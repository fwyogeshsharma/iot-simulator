package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class HistoricalDataService {

    private static final Logger logger = LoggerFactory.getLogger(HistoricalDataService.class);

    @Autowired
    private SimulatorService simulatorService;

    @Autowired
    private MedicationService medicationService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${simulator.device-ingest-url}")
    private String deviceIngestUrl;

    @Value("${simulator.device-ingest-bulk-url}")
    private String deviceIngestBulkUrl;

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    @Value("${supabase.device-data-bulk-url}")
    private String supabaseDeviceDataBulkUrl;

    @Value("${supabase.geofence-places-url}")
    private String supabaseGeofencePlacesUrl;

    @Value("${supabase.geofence-events-url}")
    private String supabaseGeofenceEventsUrl;

    // Track active jobs
    private final Map<String, HistoricalJobStatus> activeJobs = new ConcurrentHashMap<>();

    // Track geofence places per elderly person for historical data generation
    private final Map<String, List<GeofencePlace>> elderlyPersonGeofences = new ConcurrentHashMap<>();

    // Thread pool for batch sending (1 thread for sequential processing to avoid overwhelming Supabase)
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(1);

    // Thread pool for day processing (1 thread for fully sequential processing - no parallelism)
    private final ExecutorService dayExecutor = Executors.newFixedThreadPool(1);

    // Random instance for anomaly generation
    private final Random random = new Random();

    /**
     * Helper method to round double values to specified decimal places
     * Uses BigDecimal to avoid floating-point precision errors
     * @param value The value to round
     * @param decimalPlaces Number of decimal places (default: 1)
     * @return Precisely rounded double value
     */
    private double roundToDecimalPlaces(double value, int decimalPlaces) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(decimalPlaces, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    // Time periods for pattern adjustments
    enum TimePeriod {
        NIGHT(22, 6),      // 22:00-06:00
        MORNING(6, 12),    // 06:00-12:00
        AFTERNOON(12, 18), // 12:00-18:00
        EVENING(18, 22);   // 18:00-22:00

        final int startHour;
        final int endHour;

        TimePeriod(int startHour, int endHour) {
            this.startHour = startHour;
            this.endHour = endHour;
        }
    }

    // Day type for weekly patterns
    enum DayType {
        WEEKDAY,
        WEEKEND
    }

    // Anomaly types
    enum AnomalyType {
        NONE,
        FALL,
        FEVER,
        HIGH_BP
    }

    // Result class to hold generation results and futures
    private static class GenerationResult {
        int pointsGenerated;
        List<Future<?>> futures;

        GenerationResult(int pointsGenerated, List<Future<?>> futures) {
            this.pointsGenerated = pointsGenerated;
            this.futures = futures;
        }
    }

    // Result class to hold results from processing a single day
    private static class DayResult {
        int totalPoints;
        Map<String, Integer> deviceCounts;
        List<Map<String, Object>> dataPoints;  // Actual data points to send later

        DayResult(int totalPoints, Map<String, Integer> deviceCounts, List<Map<String, Object>> dataPoints) {
            this.totalPoints = totalPoints;
            this.deviceCounts = deviceCounts;
            this.dataPoints = dataPoints;
        }
    }

    /**
     * Main method to generate historical data asynchronously
     * Returns the jobId immediately while generation happens in background
     */
    public String generateHistoricalData(HistoricalDataRequest request) {
        String jobId = UUID.randomUUID().toString();
        logger.info("Starting historical data generation - JobID: {}, Request: {}", jobId, request);

        // Initialize job status immediately
        HistoricalJobStatus initialStatus = new HistoricalJobStatus(jobId, "processing");
        activeJobs.put(jobId, initialStatus);

        // Run in background thread
        CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try {
                // Get the job status we just created
                HistoricalJobStatus status = activeJobs.get(jobId);

                LocalDate startDate = LocalDate.parse(request.getStartDate());
                LocalDate endDate = LocalDate.parse(request.getEndDate());

                // Get devices
                List<Device> devices = getDevicesForRequest(request);
                if (devices.isEmpty()) {
                    throw new RuntimeException("No devices found for the provided device IDs");
                }

                logger.info("Generating data for {} devices from {} to {}",
                           devices.size(), startDate, endDate);

                // Fetch and cache all device configs ONCE (avoid repeated Supabase queries for 184 days)
                Map<String, List<DataTypeConfig>> deviceConfigsCache = new HashMap<>();
                for (Device device : devices) {
                    try {
                        List<DataTypeConfig> configs = simulatorService.getDataTypesByDeviceId(device.getId());
                        deviceConfigsCache.put(device.getId(), configs);
                        logger.debug("Cached {} configs for device {}", configs.size(), device.getDeviceId());
                    } catch (Exception e) {
                        logger.error("Error fetching configs for device {}: {}", device.getDeviceId(), e.getMessage());
                        deviceConfigsCache.put(device.getId(), new ArrayList<>()); // Empty list to avoid NPE
                    }
                }
                logger.info("Cached configs for {} devices", deviceConfigsCache.size());

                // Calculate total days
                long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
                status.setTotalDays((int) totalDays);

                // Thread-safe counters for parallel day processing
                AtomicInteger totalDataPoints = new AtomicInteger(0);
                Map<String, Integer> deviceCounts = new ConcurrentHashMap<>();
                List<Future<?>> allBatchFutures = new CopyOnWriteArrayList<>();  // Thread-safe list

                // Global batch accumulator for 500-row batches with throttling
                List<Map<String, Object>> globalBatch = Collections.synchronizedList(new ArrayList<>());

                // Determine day skip interval based on frequency
                // high: every day (no skip) → ~5,000 data points
                // medium: every 2nd day → ~2,500 data points
                // low: every 7 days (weekly) → ~700 data points
                String frequency = request.getFrequency() != null ? request.getFrequency().toLowerCase() : "high";
                logger.info("Frequency setting: {}", frequency);

                int daySkipInterval;
                switch (frequency) {
                    case "low":
                        daySkipInterval = 7;  // Weekly
                        break;
                    case "medium":
                        daySkipInterval = 2;  // Every other day
                        break;
                    case "high":
                    default:
                        daySkipInterval = 1;  // Every day
                        break;
                }
                logger.info("Day skip interval: {} (will process every {} day(s))", daySkipInterval, daySkipInterval);

                // Submit each day as a separate task for parallel processing
                List<Future<DayResult>> dayFutures = new ArrayList<>();
                int dayCounter = 0;
                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                    // Skip days based on frequency
                    if (dayCounter % daySkipInterval != 0) {
                        dayCounter++;
                        continue;
                    }
                    dayCounter++;

                    final LocalDate currentDate = date;
                    final DayType dayType = getDayType(date);

                    Future<DayResult> dayFuture = dayExecutor.submit(() -> {
                        return processSingleDay(currentDate, dayType, devices, deviceConfigsCache, request.isIncludeAnomalies(), request.getFrequency());
                    });

                    dayFutures.add(dayFuture);
                }

                logger.info("Submitted {} days for parallel processing", dayFutures.size());

                // PHASE 1: Generate all data points (don't send yet)
                logger.info("Phase 1: Generating all data points for {} days...", totalDays);
                List<Map<String, Object>> allDataPoints = new ArrayList<>();
                int daysCompleted = 0;

                for (Future<DayResult> dayFuture : dayFutures) {
                    try {
                        DayResult dayResult = dayFuture.get(5, TimeUnit.MINUTES);

                        // Collect all data points
                        allDataPoints.addAll(dayResult.dataPoints);
                        totalDataPoints.addAndGet(dayResult.totalPoints);

                        // Merge device counts
                        dayResult.deviceCounts.forEach((deviceId, count) ->
                            deviceCounts.merge(deviceId, count, Integer::sum)
                        );

                        daysCompleted++;
                        // Don't update days counter during generation - will update during sending
                        status.setDataPointsGenerated(allDataPoints.size());  // Update data points incrementally
                        status.setProgress((int) ((daysCompleted * 10.0) / totalDays));  // 0-10% for device data generation

                        // Log progress every 40 days
                        if (daysCompleted % 40 == 0) {
                            logger.info("Generated: {}/{} days, {} data points collected",
                                       daysCompleted, totalDays, allDataPoints.size());
                        }
                    } catch (TimeoutException e) {
                        logger.error("Day generation timed out after 5 minutes, skipping to next day");
                        dayFuture.cancel(true);
                    } catch (Exception e) {
                        logger.error("Error generating day: {}", e.getMessage());
                    }
                }

                logger.info("Phase 1 complete: Generated {} data points across {} days", allDataPoints.size(), daysCompleted);

                // Check if there are GPS tracker devices and generate geofence data
                boolean hasGPSTracker = devices.stream()
                    .anyMatch(device -> "gps_tracker".equalsIgnoreCase(device.getDeviceType()));

                if (hasGPSTracker) {
                    logger.info("GPS tracker device detected - generating geofence places and events...");
                    status.setProgress(10); // Start geofence generation phase

                    // Clear cache to ensure we check database for existing places
                    elderlyPersonGeofences.remove(request.getElderlyPersonId());

                    // Ensure geofence places exist for this elderly person
                    List<GeofencePlace> geofencePlaces = ensureGeofencePlacesExist(request.getElderlyPersonId());

                    if (!geofencePlaces.isEmpty()) {
                        // Generate GPS data and geofence events for each GPS tracker device
                        int gpsDeviceCount = 0;
                        int totalGpsDevices = (int) devices.stream()
                            .filter(d -> "gps_tracker".equalsIgnoreCase(d.getDeviceType())).count();

                        for (Device device : devices) {
                            if ("gps_tracker".equalsIgnoreCase(device.getDeviceType())) {
                                List<Map<String, Object>> geofenceData = generateHistoricalGeofenceData(
                                    device, startDate, endDate, geofencePlaces, daySkipInterval);
                                allDataPoints.addAll(geofenceData);
                                logger.info("Added {} GPS/geofence data points for device {}",
                                    geofenceData.size(), device.getDeviceName());

                                // Update total data points count for UI
                                status.setDataPointsGenerated(allDataPoints.size());

                                gpsDeviceCount++;
                                // Progress 10-15% during geofence generation
                                status.setProgress(10 + (int) ((gpsDeviceCount * 5.0) / totalGpsDevices));
                            }
                        }
                    }
                    status.setProgress(15); // Geofence generation complete
                }

                // Update status with total data points generated
                status.setDataPointsGenerated(allDataPoints.size());

                // Separate device_data and geofence_events (events have "_table" marker)
                List<Map<String, Object>> deviceDataPoints = new ArrayList<>();
                List<Map<String, Object>> geofenceEvents = new ArrayList<>();

                for (Map<String, Object> dataPoint : allDataPoints) {
                    if ("geofence_events".equals(dataPoint.get("_table"))) {
                        dataPoint.remove("_table"); // Remove marker before inserting
                        geofenceEvents.add(dataPoint);
                    } else {
                        deviceDataPoints.add(dataPoint);
                    }
                }

                logger.info("Separated data: {} device data points, {} geofence events",
                    deviceDataPoints.size(), geofenceEvents.size());

                // PHASE 2: Send all data in optimized 500-row batches with throttling
                logger.info("Phase 2: Sending {} device data points in 500-row batches with throttling...", deviceDataPoints.size());
                sendDataInOptimizedBatches(deviceDataPoints, status, (int) totalDays);
                logger.info("Phase 2 complete: All device data sent successfully!");

                // Send geofence events if any
                if (!geofenceEvents.isEmpty()) {
                    logger.info("Sending {} geofence events...", geofenceEvents.size());
                    sendGeofenceEventsInBatches(geofenceEvents, status);
                    logger.info("Geofence events sent successfully!");
                }
                status.setProgress(95); // Geofence events sent

                // Only create medication schedules and logs if a medication device is selected
                boolean hasMedicationDevice = devices.stream()
                    .anyMatch(device -> "medication".equalsIgnoreCase(device.getDeviceType()));

                int schedulesCreated = 0;
                int medicationLogsCreated = 0;

                if (hasMedicationDevice) {
                    logger.info("Medication device detected - creating schedules and adherence logs for {} days...", totalDays);
                    status.setProgress(96);
                    schedulesCreated = createMedicationSchedulesIfNeeded(request.getElderlyPersonId(), startDate);
                    logger.info("Created {} medication schedules", schedulesCreated);

                    status.setProgress(97);
                    medicationLogsCreated = generateMedicationAdherenceLogs(
                        request.getElderlyPersonId(), startDate, endDate, daySkipInterval);
                    logger.info("Created {} medication adherence logs", medicationLogsCreated);
                    status.setProgress(99);
                } else {
                    logger.info("No medication device selected - skipping medication schedules and adherence logs");
                }

                long elapsedMs = System.currentTimeMillis() - startTime;
                status.setStatus("completed");
                status.setProgress(100);
                status.setDaysProcessed((int) totalDays);  // Update day counter to show final total
                status.setCompletionTime(System.currentTimeMillis());  // Track completion time for cleanup

                logger.info("Historical data generation completed - JobID: {}, Total points: {}, Time: {}ms",
                           jobId, totalDataPoints.get(), elapsedMs);

                // Cleanup old jobs after successful completion
                cleanupOldJobs();

                HistoricalDataResponse response = new HistoricalDataResponse(
                        jobId, "completed",
                        "Successfully generated " + totalDataPoints.get() + " historical data points",
                        totalDataPoints.get(), (int) totalDays, deviceCounts
                );
                response.setElapsedMs(elapsedMs);

                return response;

            } catch (Exception e) {
                logger.error("Historical data generation failed - JobID: " + jobId, e);
                HistoricalJobStatus status = activeJobs.get(jobId);
                if (status != null) {
                    status.setStatus("failed");
                    status.setErrorMessage(e.getMessage());
                    status.setCompletionTime(System.currentTimeMillis());  // Track failure time for cleanup
                }

                // Cleanup old jobs even on failure to prevent memory buildup
                cleanupOldJobs();

                throw new RuntimeException("Historical data generation failed: " + e.getMessage(), e);
            }
        });

        // Return jobId immediately while generation runs in background
        return jobId;
    }

    /**
     * Process all devices for a single day (called in parallel for each day)
     * Returns DayResult with aggregated results for the day
     */
    private DayResult processSingleDay(LocalDate date, DayType dayType, List<Device> devices,
                                        Map<String, List<DataTypeConfig>> deviceConfigsCache, boolean includeAnomalies, String frequency) {
        int totalPoints = 0;
        Map<String, Integer> deviceCounts = new HashMap<>();
        List<Map<String, Object>> allDataPoints = new ArrayList<>();  // Collect all data points

        // Process all devices for this day
        for (Device device : devices) {
            try {
                // Use cached configs instead of fetching from Supabase every time
                List<DataTypeConfig> configs = deviceConfigsCache.getOrDefault(device.getId(), new ArrayList<>());

                // Process each data type for this device
                for (DataTypeConfig config : configs) {
                    List<Map<String, Object>> dataPoints = generateDataPointsForDay(
                            device, config, date, dayType, includeAnomalies, frequency
                    );

                    totalPoints += dataPoints.size();
                    allDataPoints.addAll(dataPoints);  // Collect instead of sending
                    deviceCounts.merge(device.getDeviceId(), dataPoints.size(), Integer::sum);
                }
            } catch (Exception e) {
                logger.error("Error generating data for device {} on {}: {}",
                           device.getDeviceId(), date, e.getMessage());
            }
        }

        return new DayResult(totalPoints, deviceCounts, allDataPoints);
    }

    /**
     * Generate data points for a single day
     * Returns list of data points (NOT sent yet - will be batched later)
     */
    private List<Map<String, Object>> generateDataPointsForDay(Device device, DataTypeConfig config,
                                                       LocalDate date, DayType dayType,
                                                       boolean includeAnomalies, String frequency) {
        List<Map<String, Object>> dataPoints = new ArrayList<>();

        // Always generate 1 reading per day at 2pm (to stay within free tier)
        // Frequency controls which DAYS get data, not readings per day
        int[] hoursToGenerate = {14};  // 2pm only

        for (int hour : hoursToGenerate) {
            LocalTime time = LocalTime.of(hour, 0);
            LocalDateTime timestamp = LocalDateTime.of(date, time);

            // Skip future timestamps
            if (timestamp.isAfter(LocalDateTime.now())) {
                continue;
            }

            // Determine time period
            TimePeriod period = getTimePeriod(time);

            // Skip if data type shouldn't generate during this period
            if (shouldSkipPeriod(config.getDataType(), period)) {
                continue;
            }

            // Generate base value
            Object value = generateBaseValue(config);
            if (value == null) {
                continue;
            }

            // Apply patterns
            value = applyTimeOfDayPattern(config.getDataType(), value, period);
            value = applyWeeklyPattern(config.getDataType(), value, dayType);

            // Apply anomalies
            if (includeAnomalies) {
                AnomalyType anomaly = checkForAnomaly(date, config.getDataType());
                if (anomaly != AnomalyType.NONE) {
                    value = applyAnomaly(value, config.getDataType(), anomaly);
                    logger.debug("Generated anomaly {} for {} on {}", anomaly, config.getDataType(), date);
                }
            }

            // Create payload
            Map<String, Object> payload = createPayload(device, config, value, timestamp);
            dataPoints.add(payload);
        }

        return dataPoints;  // Return data points without sending
    }

    /**
     * Generate base value using existing logic from SimulationManager
     */
    private Object generateBaseValue(DataTypeConfig config) {
        try {
            String configType = config.getConfigType();
            Map<String, Object> configData = config.getConfig();

            // Handle special data types that don't have numeric configs
            String dataType = config.getDataType();

            // Timestamp-based data types (e.g., last_opened, next_dose_time)
            if ("last_opened".equals(dataType) || "next_dose_time".equals(dataType)) {
                // Return current timestamp as ISO string
                return LocalDateTime.now().toString();
            }

            // Location data type (latitude, longitude)
            if ("location".equals(dataType)) {
                Map<String, Object> location = new LinkedHashMap<>();
                // Default location (somewhere in US for elderly care)
                location.put("latitude", roundToDecimalPlaces(40.7128 + (random.nextDouble() * 0.1 - 0.05), 6));  // NYC area with small variance
                location.put("longitude", roundToDecimalPlaces(-74.0060 + (random.nextDouble() * 0.1 - 0.05), 6));
                return location;
            }

            // Special handling for blood_pressure (supports both flat and nested formats)
            if ("blood_pressure".equals(config.getDataType())) {
                Map<String, Object> bpValue = new LinkedHashMap<>();

                // Try flat format first (systolic_min, systolic_max, diastolic_min, diastolic_max)
                if (configData.containsKey("systolic_min")) {
                    int systolicMin = ((Number) configData.getOrDefault("systolic_min", 110)).intValue();
                    int systolicMax = ((Number) configData.getOrDefault("systolic_max", 130)).intValue();
                    int diastolicMin = ((Number) configData.getOrDefault("diastolic_min", 70)).intValue();
                    int diastolicMax = ((Number) configData.getOrDefault("diastolic_max", 85)).intValue();

                    bpValue.put("systolic", systolicMin + random.nextInt(systolicMax - systolicMin + 1));
                    bpValue.put("diastolic", diastolicMin + random.nextInt(diastolicMax - diastolicMin + 1));
                }
                // Try nested format (systolic: {min, max}, diastolic: {min, max})
                else if (configData.containsKey("systolic")) {
                    Map<String, Object> systolicConfig = (Map<String, Object>) configData.get("systolic");
                    int systolicMin = ((Number) systolicConfig.get("min")).intValue();
                    int systolicMax = ((Number) systolicConfig.get("max")).intValue();
                    bpValue.put("systolic", systolicMin + random.nextInt(systolicMax - systolicMin + 1));

                    if (configData.containsKey("diastolic")) {
                        Map<String, Object> diastolicConfig = (Map<String, Object>) configData.get("diastolic");
                        int diastolicMin = ((Number) diastolicConfig.get("min")).intValue();
                        int diastolicMax = ((Number) diastolicConfig.get("max")).intValue();
                        bpValue.put("diastolic", diastolicMin + random.nextInt(diastolicMax - diastolicMin + 1));
                    }
                }

                return bpValue;
            }

            if ("range".equals(configType)) {
                // Null-safe config access
                if (configData == null || !configData.containsKey("min") || !configData.containsKey("max") ||
                    configData.get("min") == null || configData.get("max") == null) {
                    logger.warn("Range config missing min/max for {}, skipping", config.getDataType());
                    return null;
                }

                double min = ((Number) configData.get("min")).doubleValue();
                double max = ((Number) configData.get("max")).doubleValue();
                int precision = configData.containsKey("precision") ?
                               ((Number) configData.get("precision")).intValue() : 2;

                double value = min + (random.nextDouble() * (max - min));
                return roundToDecimalPlaces(value, precision);
            } else if ("enum".equals(configType)) {
                List<Object> values = (List<Object>) configData.get("values");
                if (values != null && !values.isEmpty()) {
                    return values.get(random.nextInt(values.size()));
                }
            }
        } catch (Exception e) {
            logger.error("Error generating base value for {}: {}", config.getDataType(), e.getMessage());
        }
        return null;
    }

    /**
     * Apply time-of-day patterns to values
     */
    private Object applyTimeOfDayPattern(String dataType, Object baseValue, TimePeriod period) {
        if (baseValue == null) return null;

        try {
            switch (dataType) {
                case "heart_rate":
                    double hr = ((Number) baseValue).doubleValue();
                    switch (period) {
                        case NIGHT: return roundToDecimalPlaces(hr * 0.90, 1);      // -10%
                        case MORNING: return roundToDecimalPlaces(hr * 1.05, 1);    // +5%
                        case AFTERNOON: return roundToDecimalPlaces(hr * 1.10, 1);  // +10%
                        case EVENING: return roundToDecimalPlaces(hr, 1);           // 0%
                    }
                    break;

                case "temperature":
                case "body_temperature":
                    double temp = ((Number) baseValue).doubleValue();
                    switch (period) {
                        case NIGHT: return roundToDecimalPlaces(temp - 0.5, 1);     // -0.5°F
                        case MORNING: return roundToDecimalPlaces(temp + 0.2, 1);   // +0.2°F
                        case AFTERNOON: return roundToDecimalPlaces(temp + 0.3, 1); // +0.3°F
                        case EVENING: return roundToDecimalPlaces(temp, 1);         // 0°F
                    }
                    break;

                case "steps":
                case "activity_level":
                    // Zero at night, normal during day
                    if (period == TimePeriod.NIGHT) {
                        return 0;
                    }
                    break;

                case "blood_pressure":
                    if (baseValue instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> bp = new LinkedHashMap<>((Map<String, Object>) baseValue);
                        if (period == TimePeriod.MORNING && bp.containsKey("systolic")) {
                            // Morning: +5mmHg systolic
                            int systolic = ((Number) bp.get("systolic")).intValue();
                            bp.put("systolic", systolic + 5);
                        }
                        return bp;
                    }
                    break;
            }
        } catch (Exception e) {
            logger.error("Error applying time-of-day pattern: {}", e.getMessage());
        }

        return baseValue;
    }

    /**
     * Apply weekly patterns (weekend vs weekday)
     */
    private Object applyWeeklyPattern(String dataType, Object value, DayType dayType) {
        if (value == null || dayType != DayType.WEEKEND) {
            return value;
        }

        try {
            switch (dataType) {
                case "steps":
                case "activity_level":
                    // 20% less activity on weekends
                    double activityValue = ((Number) value).doubleValue();
                    return roundToDecimalPlaces(activityValue * 0.80, 1);

                case "sleep_duration":
                    // Sleep 30 minutes more on weekends
                    double sleepValue = ((Number) value).doubleValue();
                    return roundToDecimalPlaces(sleepValue + 0.5, 1);
            }
        } catch (Exception e) {
            logger.error("Error applying weekly pattern: {}", e.getMessage());
        }

        return value;
    }

    /**
     * Check if an anomaly should be generated
     */
    private AnomalyType checkForAnomaly(LocalDate date, String dataType) {
        // Falls: 1-2 times per month (probability: ~1.5/30 = 5% per day)
        if ("fall_detected".equals(dataType) && random.nextDouble() < 0.05) {
            return AnomalyType.FALL;
        }

        // High fever: 1-2 times in 6 months (probability: 1.5/180 = 0.83% per day)
        if (("temperature".equals(dataType) || "body_temperature".equals(dataType))
            && random.nextDouble() < 0.0083) {
            return AnomalyType.FEVER;
        }

        // Blood pressure spike: 2-3% of readings
        if ("blood_pressure".equals(dataType) && random.nextDouble() < 0.025) {
            return AnomalyType.HIGH_BP;
        }

        return AnomalyType.NONE;
    }

    /**
     * Apply anomaly modifications to value
     */
    private Object applyAnomaly(Object baseValue, String dataType, AnomalyType anomaly) {
        try {
            switch (anomaly) {
                case FALL:
                    return true;  // fall_detected = true

                case FEVER:
                    // Temperature > 102°F (instead of normal ~98.6°F)
                    return roundToDecimalPlaces(102.0 + (random.nextDouble() * 2.0), 1);  // 102-104°F

                case HIGH_BP:
                    // Systolic > 160, Diastolic > 100
                    if (baseValue instanceof Map) {
                        Map<String, Object> bp = new LinkedHashMap<>();
                        bp.put("systolic", 160 + random.nextInt(20));  // 160-180
                        bp.put("diastolic", 100 + random.nextInt(15)); // 100-115
                        return bp;
                    }
                    break;
            }
        } catch (Exception e) {
            logger.error("Error applying anomaly: {}", e.getMessage());
        }

        return baseValue;
    }

    /**
     * Create payload for Supabase REST API (uses database IDs)
     */
    private Map<String, Object> createPayload(Device device, DataTypeConfig config,
                                               Object value, LocalDateTime timestamp) {
        Map<String, Object> payload = new LinkedHashMap<>();  // Use LinkedHashMap for consistent ordering
        payload.put("device_id", device.getId());  // Database UUID
        payload.put("elderly_person_id", device.getElderlyPersonId());  // Required by schema
        payload.put("data_type", config.getDataType());
        payload.put("value", value);

        // ALWAYS include unit field (null if not present) - ensures all records have same keys for batch insert
        payload.put("unit", (config.getUnit() != null && !config.getUnit().isEmpty()) ? config.getUnit() : null);

        // Add timestamp (convert to ISO format)
        payload.put("recorded_at", timestamp.atZone(ZoneId.systemDefault()).toInstant().toString());

        return payload;
    }

    /**
     * Send all data points in optimized batches with throttling and retry logic
     * This is the key optimization for Supabase free tier performance
     */
    private void sendDataInOptimizedBatches(List<Map<String, Object>> allDataPoints,
                                             HistoricalJobStatus status, int totalDays) {
        final int BATCH_SIZE = 250;  // Reduced from 500 to avoid timeouts on Supabase free tier
        final int THROTTLE_MS = 300;  // 300ms delay between batches to avoid CPU spikes
        final int MAX_RETRIES = 3;  // Maximum retry attempts per batch

        int totalBatches = (int) Math.ceil((double) allDataPoints.size() / BATCH_SIZE);
        int batchesCompleted = 0;
        int batchesFailed = 0;
        List<Integer> failedBatchNumbers = new ArrayList<>();

        logger.info("Sending {} data points in {} batches of {} (with up to {} retries per batch)",
                   allDataPoints.size(), totalBatches, BATCH_SIZE, MAX_RETRIES);

        for (int i = 0; i < allDataPoints.size(); i += BATCH_SIZE) {
            int batchNumber = (i / BATCH_SIZE) + 1;
            int endIndex = Math.min(i + BATCH_SIZE, allDataPoints.size());
            List<Map<String, Object>> batch = allDataPoints.subList(i, endIndex);

            // Send this batch with retry logic
            boolean success = sendBatchWithRetry(batch, batchNumber, MAX_RETRIES);

            if (success) {
                batchesCompleted++;
            } else {
                batchesFailed++;
                failedBatchNumbers.add(batchNumber);
                logger.error("❌ Batch #{} FAILED after {} retries - {} records NOT inserted",
                           batchNumber, MAX_RETRIES, batch.size());
            }

            // Update progress (15-85% range for device data sending)
            // Use total batches processed (successful + failed) to keep progress moving
            int batchesProcessed = batchesCompleted + batchesFailed;
            int overallProgress = 15 + (int) ((batchesProcessed * 70.0) / totalBatches);
            status.setProgress(overallProgress);

            // Update days counter to align with OVERALL progress (0-100%), not just this phase
            // Device data is 15-85% (70% of total), so scale accordingly
            // Formula: (currentProgress / 100) * totalDays
            int daysProcessed = (int) ((overallProgress / 100.0) * totalDays);
            status.setDaysProcessed(daysProcessed);

            // Log progress every 5 batches
            if ((batchesCompleted + batchesFailed) % 5 == 0 || (batchesCompleted + batchesFailed) == totalBatches) {
                logger.info("Progress: {}/{} batches ({} succeeded, {} failed) - {} / {} data points",
                           batchesCompleted + batchesFailed, totalBatches, batchesCompleted, batchesFailed,
                           endIndex, allDataPoints.size());
            }

            // Throttle between batches (except for the last one)
            if ((batchesCompleted + batchesFailed) < totalBatches) {
                try {
                    Thread.sleep(THROTTLE_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Throttling interrupted");
                }
            }
        }

        // Report final results
        if (batchesFailed == 0) {
            logger.info("✅ Successfully sent all {} data points in {} batches", allDataPoints.size(), batchesCompleted);
        } else {
            int recordsFailed = batchesFailed * BATCH_SIZE;
            logger.error("⚠️  Batch insertion completed with failures:");
            logger.error("   ✅ Succeeded: {}/{} batches ({} data points)",
                       batchesCompleted, totalBatches, batchesCompleted * BATCH_SIZE);
            logger.error("   ❌ Failed: {}/{} batches (~{} data points)",
                       batchesFailed, totalBatches, recordsFailed);
            logger.error("   Failed batch numbers: {}", failedBatchNumbers);
            throw new RuntimeException(String.format(
                "Failed to insert %d batches (~%d records). Failed batches: %s. " +
                "This will result in incomplete historical data. Please check Supabase logs and retry.",
                batchesFailed, recordsFailed, failedBatchNumbers));
        }
    }

    /**
     * Send a batch with retry logic and exponential backoff
     * Retries up to maxRetries times with increasing delays between attempts
     */
    private boolean sendBatchWithRetry(List<Map<String, Object>> batch, int batchNumber, int maxRetries) {
        int attempt = 1;

        while (attempt <= maxRetries) {
            try {
                boolean success = sendBatchToSupabase(batch);

                if (success) {
                    if (attempt > 1) {
                        logger.info("✅ Batch #{} succeeded on attempt {}/{}", batchNumber, attempt, maxRetries);
                    }
                    return true;
                } else {
                    // Failed, but may retry
                    if (attempt < maxRetries) {
                        int delayMs = (int) (1000 * Math.pow(2, attempt - 1));  // Exponential backoff: 1s, 2s, 4s
                        logger.warn("⚠️  Batch #{} failed on attempt {}/{}. Retrying in {}ms...",
                                  batchNumber, attempt, maxRetries, delayMs);
                        Thread.sleep(delayMs);
                    } else {
                        logger.error("❌ Batch #{} failed on final attempt {}/{}", batchNumber, attempt, maxRetries);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Retry interrupted for batch #{}", batchNumber);
                return false;
            } catch (Exception e) {
                logger.error("Unexpected error in batch #{} attempt {}/{}: {}",
                           batchNumber, attempt, maxRetries, e.getMessage());
                if (attempt >= maxRetries) {
                    return false;
                }
            }

            attempt++;
        }

        return false;  // All retries exhausted
    }

    /**
     * Send a single batch to Supabase REST API with retry support
     * Returns true if successful, false otherwise
     */
    private boolean sendBatchToSupabase(List<Map<String, Object>> batch) {
        if (batch.isEmpty()) {
            return true;  // Empty batch is considered success
        }

        // Validate records
        List<Map<String, Object>> validBatch = new ArrayList<>();
        for (Map<String, Object> record : batch) {
            if (record.containsKey("device_id") && record.get("device_id") != null &&
                record.containsKey("elderly_person_id") && record.get("elderly_person_id") != null &&
                record.containsKey("data_type") && record.get("data_type") != null &&
                record.containsKey("value") && record.get("value") != null) {
                validBatch.add(record);
            } else {
                logger.warn("Skipping invalid record: {}", record);
            }
        }

        if (validBatch.isEmpty()) {
            logger.warn("All {} records in batch were invalid, skipping", batch.size());
            return false;  // All invalid records is a failure
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);  // Supabase service role key
            headers.set("Authorization", "Bearer " + supabaseApiKey);
            headers.set("Prefer", "return=minimal");  // Don't return inserted data (faster)
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Send entire batch as JSON array to Supabase REST API
            String jsonBody = objectMapper.writeValueAsString(validBatch);

            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    supabaseDeviceDataBulkUrl, request, String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("✅ Inserted {} records", validBatch.size());
                return true;
            } else {
                logger.warn("Batch insert failed with status {}: {}", response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            logger.warn("Error inserting {} records: {}", validBatch.size(), e.getMessage());
            return false;  // Return false instead of swallowing exception
        }
    }

    /**
     * Get devices for the request
     */
    private List<Device> getDevicesForRequest(HistoricalDataRequest request) {
        List<Device> allDevices = simulatorService.getDevicesByElderlyPersonId(request.getElderlyPersonId());

        if (request.getDeviceIds() == null || request.getDeviceIds().isEmpty()) {
            return allDevices;
        }

        // Filter by requested device IDs
        List<Device> filteredDevices = new ArrayList<>();
        for (Device device : allDevices) {
            if (request.getDeviceIds().contains(device.getId())) {
                filteredDevices.add(device);
            }
        }

        return filteredDevices;
    }

    /**
     * Get time period for a given time
     */
    private TimePeriod getTimePeriod(LocalTime time) {
        int hour = time.getHour();

        if (hour >= 22 || hour < 6) {
            return TimePeriod.NIGHT;
        } else if (hour >= 6 && hour < 12) {
            return TimePeriod.MORNING;
        } else if (hour >= 12 && hour < 18) {
            return TimePeriod.AFTERNOON;
        } else {
            return TimePeriod.EVENING;
        }
    }

    /**
     * Determine if data type should skip this time period
     */
    private boolean shouldSkipPeriod(String dataType, TimePeriod period) {
        // Sleep data only during night
        if (("sleep_stage".equals(dataType) || "sleep_quality".equals(dataType))
            && period != TimePeriod.NIGHT) {
            return true;
        }

        return false;
    }

    /**
     * Get day type (weekday or weekend)
     */
    private DayType getDayType(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)
               ? DayType.WEEKEND : DayType.WEEKDAY;
    }

    /**
     * Create medication schedules if they don't exist
     * Uses common medications prescribed to elderly people in America
     */
    private int createMedicationSchedulesIfNeeded(String elderlyPersonId, LocalDate startDate) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            headers.set("Authorization", "Bearer " + supabaseApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Check if schedules already exist
            String medicationSchedulesUrl = "https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/medication_schedules";
            String url = medicationSchedulesUrl + "?elderly_person_id=eq." + elderlyPersonId;
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            com.fasterxml.jackson.databind.JsonNode jsonArray = objectMapper.readTree(response.getBody());

            if (jsonArray.size() > 0) {
                logger.info("Medication schedules already exist for elderly person: {}", elderlyPersonId);
                return 0;
            }

            // Define 4 most common medications for elderly in America (reduced from 7 for performance)
            List<Map<String, Object>> medications = new ArrayList<>();

            // Blood Pressure
            medications.add(createMedicationSchedule(elderlyPersonId, "Lisinopril", 10, "mg",
                "daily", startDate, Arrays.asList("08:00"), "Once daily in the morning"));

            // Cholesterol
            medications.add(createMedicationSchedule(elderlyPersonId, "Atorvastatin", 20, "mg",
                "daily", startDate, Arrays.asList("20:00"), "Once daily in the evening"));

            // Diabetes
            medications.add(createMedicationSchedule(elderlyPersonId, "Metformin", 500, "mg",
                "twice_daily", startDate, Arrays.asList("08:00", "20:00"), "Twice daily with meals"));

            // Heart Health
            medications.add(createMedicationSchedule(elderlyPersonId, "Aspirin", 81, "mg",
                "daily", startDate, Arrays.asList("08:00"), "Once daily (low dose)"));

            // Insert each medication schedule
            int created = 0;
            for (Map<String, Object> medication : medications) {
                if (insertMedicationSchedule(medication, headers)) {
                    created++;
                }
            }

            return created;
        } catch (Exception e) {
            logger.error("Error creating medication schedules: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, Object> createMedicationSchedule(String elderlyPersonId, String medicationName,
                                                          int dosageMg, String dosageUnit, String frequency,
                                                          LocalDate startDate, List<String> times, String instructions) {
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("id", UUID.randomUUID().toString());
        schedule.put("elderly_person_id", elderlyPersonId);
        schedule.put("medication_name", medicationName);
        schedule.put("dosage_mg", dosageMg);
        schedule.put("dosage_unit", dosageUnit);
        schedule.put("frequency", frequency);
        schedule.put("start_date", startDate.toString());
        schedule.put("end_date", null); // Ongoing medication
        schedule.put("times", times);
        schedule.put("instructions", instructions);
        schedule.put("is_active", true);
        return schedule;
    }

    private boolean insertMedicationSchedule(Map<String, Object> schedule, HttpHeaders headers) {
        try {
            String medicationSchedulesUrl = "https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/medication_schedules";
            String jsonBody = objectMapper.writeValueAsString(schedule);
            HttpEntity<String> postEntity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                medicationSchedulesUrl,
                HttpMethod.POST,
                postEntity,
                String.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            if (success) {
                logger.info("Created medication schedule: {} ({})", schedule.get("medication_name"), schedule.get("dosage"));
            }
            return success;
        } catch (Exception e) {
            logger.error("Error inserting medication schedule: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate medication adherence logs for a date range
     */
    private int generateMedicationAdherenceLogs(String elderlyPersonId, LocalDate startDate, LocalDate endDate, int daySkipInterval) {
        try {
            // Get all active medication schedules for this elderly person
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            headers.set("Authorization", "Bearer " + supabaseApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String medicationSchedulesUrl = "https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/medication_schedules";
            String url = medicationSchedulesUrl + "?elderly_person_id=eq." + elderlyPersonId + "&is_active=eq.true";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            com.fasterxml.jackson.databind.JsonNode jsonArray = objectMapper.readTree(response.getBody());

            if (jsonArray.size() == 0) {
                logger.info("No active medication schedules found for elderly person: {}", elderlyPersonId);
                return 0;
            }

            logger.info("Found {} active medication schedules", jsonArray.size());
            int totalLogsCreated = 0;

            // For each medication schedule, generate logs for the entire date range
            for (com.fasterxml.jackson.databind.JsonNode scheduleNode : jsonArray) {
                String scheduleId = scheduleNode.get("id").asText();
                String medicationName = scheduleNode.get("medication_name").asText();

                // Extract times
                List<String> times = new ArrayList<>();
                if (scheduleNode.has("times") && !scheduleNode.get("times").isNull()) {
                    com.fasterxml.jackson.databind.JsonNode timesNode = scheduleNode.get("times");
                    if (timesNode.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode timeNode : timesNode) {
                            times.add(timeNode.asText());
                        }
                    }
                }

                LocalDate scheduleStartDate = scheduleNode.has("start_date") ?
                    LocalDate.parse(scheduleNode.get("start_date").asText().substring(0, 10)) : startDate;
                LocalDate scheduleEndDate = scheduleNode.has("end_date") && !scheduleNode.get("end_date").isNull() ?
                    LocalDate.parse(scheduleNode.get("end_date").asText().substring(0, 10)) : endDate;

                // Adjust date range to schedule dates
                LocalDate effectiveStartDate = startDate.isBefore(scheduleStartDate) ? scheduleStartDate : startDate;
                LocalDate effectiveEndDate = endDate.isAfter(scheduleEndDate) ? scheduleEndDate : endDate;

                logger.info("Generating logs for medication '{}' from {} to {}",
                    medicationName, effectiveStartDate, effectiveEndDate);

                // Generate logs for each day and each time - batch for performance
                List<Map<String, Object>> logBatch = new ArrayList<>();
                int batchSize = 500;  // Insert 500 logs at a time (optimized for Supabase free tier)

                int dayCounter = 0;
                for (LocalDate date = effectiveStartDate; !date.isAfter(effectiveEndDate); date = date.plusDays(1)) {
                    // Skip days based on frequency
                    if (dayCounter % daySkipInterval != 0) {
                        dayCounter++;
                        continue;
                    }
                    dayCounter++;

                    for (String scheduledTime : times) {
                        // Create log (skip existence check for historical data - much faster)
                        Map<String, Object> log = createMedicationLog(scheduleId, elderlyPersonId,
                            scheduledTime, date);
                        logBatch.add(log);

                        // Insert batch when it reaches batchSize
                        if (logBatch.size() >= batchSize) {
                            int inserted = insertMedicationLogsBatch(logBatch, headers);
                            totalLogsCreated += inserted;
                            logBatch.clear();
                        }
                    }
                }

                // Insert remaining logs in the batch
                if (!logBatch.isEmpty()) {
                    int inserted = insertMedicationLogsBatch(logBatch, headers);
                    totalLogsCreated += inserted;
                    logBatch.clear();
                }
            }

            return totalLogsCreated;
        } catch (Exception e) {
            logger.error("Error generating medication adherence logs: {}", e.getMessage());
            return 0;
        }
    }

    private boolean medicationLogExists(String scheduleId, String elderlyPersonId, LocalDate date,
                                       String scheduledTime, HttpEntity<String> entity) {
        try {
            String medicationAdherenceLogsUrl = "https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/medication_adherence_logs";
            String url = medicationAdherenceLogsUrl +
                "?schedule_id=eq." + scheduleId +
                "&elderly_person_id=eq." + elderlyPersonId +
                "&scheduled_time=eq." + scheduledTime;

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            com.fasterxml.jackson.databind.JsonNode jsonArray = objectMapper.readTree(response.getBody());

            for (com.fasterxml.jackson.databind.JsonNode logNode : jsonArray) {
                if (logNode.has("timestamp") && !logNode.get("timestamp").isNull()) {
                    String timestampStr = logNode.get("timestamp").asText();
                    String logDateStr = timestampStr.substring(0, 10);
                    if (logDateStr.equals(date.toString())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("Error checking medication log existence: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> createMedicationLog(String scheduleId, String elderlyPersonId,
                                                     String scheduledTime, LocalDate date) {
        String[] parts = scheduledTime.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        LocalDateTime scheduledDateTime = date.atTime(hour, minute);

        Map<String, Object> log = new HashMap<>();
        log.put("id", UUID.randomUUID().toString());
        log.put("schedule_id", scheduleId);
        log.put("elderly_person_id", elderlyPersonId);
        log.put("scheduled_time", scheduledTime);

        // Determine status (70% taken, 20% late, 10% missed)
        double rand = random.nextDouble();
        String status;
        LocalDateTime actualTimestamp;
        boolean dispenserConfirmed;
        boolean caregiverAlerted;

        if (rand < 0.70) {
            status = "taken";
            int minutesVariance = random.nextInt(11);
            actualTimestamp = scheduledDateTime.plusMinutes(minutesVariance);
            dispenserConfirmed = random.nextDouble() < 0.85;
            caregiverAlerted = false;
        } else if (rand < 0.90) {
            status = "late";
            int minutesLate = 30 + random.nextInt(61);
            actualTimestamp = scheduledDateTime.plusMinutes(minutesLate);
            dispenserConfirmed = false;
            caregiverAlerted = false;
        } else {
            status = "missed";
            int minutesMissed = 120 + random.nextInt(61);
            actualTimestamp = scheduledDateTime.plusMinutes(minutesMissed);
            dispenserConfirmed = false;
            caregiverAlerted = true;
        }

        log.put("status", status);
        log.put("timestamp", actualTimestamp.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "+00");
        log.put("dispenser_confirmed", dispenserConfirmed);
        log.put("caregiver_alerted", caregiverAlerted);

        return log;
    }

    private boolean insertMedicationLog(Map<String, Object> log, HttpHeaders headers) {
        try {
            String medicationAdherenceLogsUrl = "https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/medication_adherence_logs";
            String jsonBody = objectMapper.writeValueAsString(log);
            HttpEntity<String> postEntity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                medicationAdherenceLogsUrl,
                HttpMethod.POST,
                postEntity,
                String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.error("Error inserting medication log: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Batch insert medication logs for better performance
     * Supabase REST API supports bulk inserts by sending an array
     */
    private int insertMedicationLogsBatch(List<Map<String, Object>> logs, HttpHeaders headers) {
        try {
            String medicationAdherenceLogsUrl = "https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/medication_adherence_logs";
            String jsonBody = objectMapper.writeValueAsString(logs);
            HttpEntity<String> postEntity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                medicationAdherenceLogsUrl,
                HttpMethod.POST,
                postEntity,
                String.class
            );

            // Throttle to avoid CPU spikes on Supabase free tier (300ms between batches)
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (response.getStatusCode().is2xxSuccessful()) {
                return logs.size();
            }
            return 0;
        } catch (Exception e) {
            logger.error("Error batch inserting {} medication logs: {}", logs.size(), e.getMessage());
            return 0;
        }
    }

    /**
     * Get job status
     */
    public HistoricalJobStatus getJobStatus(String jobId) {
        return activeJobs.get(jobId);
    }

    /**
     * Cleanup old completed jobs (call this periodically or after each job completion)
     * Prevents memory leak from accumulating job statuses
     */
    public void cleanupOldJobs() {
        // Keep completed/failed jobs for 15 minutes so frontend can poll final status
        // This prevents the bug where UI gets stuck on "Generating..." because job was removed too quickly
        // After 15 minutes, cleanup to prevent memory leak
        long cutoffTime = System.currentTimeMillis() - (15 * 60 * 1000); // 15 minutes ago

        Iterator<Map.Entry<String, HistoricalJobStatus>> iterator = activeJobs.entrySet().iterator();
        int removedCount = 0;

        while (iterator.hasNext()) {
            Map.Entry<String, HistoricalJobStatus> entry = iterator.next();
            HistoricalJobStatus status = entry.getValue();

            // Remove completed/failed jobs older than 15 minutes
            if (("completed".equals(status.getStatus()) || "failed".equals(status.getStatus()))
                && status.getCompletionTime() > 0
                && status.getCompletionTime() < cutoffTime) {
                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.info("Cleaned up {} old job statuses from memory", removedCount);
        }
    }

    /**
     * Cancel a running job
     * Attempts to stop the generation gracefully
     */
    public boolean cancelJob(String jobId) {
        HistoricalJobStatus status = activeJobs.get(jobId);
        if (status == null) {
            logger.warn("Cannot cancel job {}: not found", jobId);
            return false;
        }

        if ("completed".equals(status.getStatus()) || "failed".equals(status.getStatus())) {
            logger.info("Job {} already finished with status: {}", jobId, status.getStatus());
            return false;
        }

        logger.info("Cancelling job {}", jobId);
        status.setStatus("cancelled");
        status.setErrorMessage("Job cancelled by user");

        return true;
    }

    /**
     * Graceful shutdown - called when Spring container is destroyed
     * Prevents memory leaks and zombie threads
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down HistoricalDataService - terminating thread pools...");

        // Shutdown executors gracefully
        shutdownExecutor(dayExecutor, "dayExecutor");
        shutdownExecutor(batchExecutor, "batchExecutor");

        // Clear job statuses
        int jobCount = activeJobs.size();
        activeJobs.clear();
        logger.info("Cleared {} job statuses from memory", jobCount);

        logger.info("HistoricalDataService shutdown complete");
    }

    /**
     * Helper method to shutdown an executor service gracefully
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        try {
            logger.info("Shutting down {}...", name);

            // Disable new tasks from being submitted
            executor.shutdown();

            // Wait a while for existing tasks to terminate
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("{} did not terminate gracefully, forcing shutdown...", name);

                // Cancel currently executing tasks
                List<Runnable> droppedTasks = executor.shutdownNow();
                logger.warn("Dropped {} tasks from {}", droppedTasks.size(), name);

                // Wait a bit for tasks to respond to being cancelled
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("{} did not terminate after forced shutdown", name);
                }
            } else {
                logger.info("{} terminated gracefully", name);
            }
        } catch (InterruptedException e) {
            // (Re-)Cancel if current thread also interrupted
            executor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
            logger.error("Shutdown of {} interrupted", name, e);
        }
    }

    /**
     * Send geofence events to Supabase in batches
     */
    private void sendGeofenceEventsInBatches(List<Map<String, Object>> geofenceEvents, HistoricalJobStatus status) {
        final int BATCH_SIZE = 250;  // Reduced from 500 to match device data batch size
        final int THROTTLE_MS = 300;

        int totalBatches = (int) Math.ceil((double) geofenceEvents.size() / BATCH_SIZE);
        int batchesCompleted = 0;

        logger.info("Sending {} geofence events in {} batches of {}", geofenceEvents.size(), totalBatches, BATCH_SIZE);

        for (int i = 0; i < geofenceEvents.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, geofenceEvents.size());
            List<Map<String, Object>> batch = geofenceEvents.subList(i, endIndex);

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("apikey", supabaseApiKey);
                headers.set("Authorization", "Bearer " + supabaseApiKey);
                headers.set("Prefer", "return=minimal");

                String jsonBatch = objectMapper.writeValueAsString(batch);
                HttpEntity<String> entity = new HttpEntity<>(jsonBatch, headers);

                restTemplate.exchange(supabaseGeofenceEventsUrl, HttpMethod.POST, entity, String.class);

                batchesCompleted++;

                // Update progress (85-95% range for geofence events)
                int overallProgress = 85 + (int) ((batchesCompleted * 10.0) / totalBatches);
                status.setProgress(overallProgress);

                logger.debug("✅ Inserted {} geofence events (batch {}/{})",
                    batch.size(), batchesCompleted, totalBatches);

                // Throttle between batches
                if (batchesCompleted < totalBatches) {
                    Thread.sleep(THROTTLE_MS);
                }
            } catch (Exception e) {
                logger.error("Error sending geofence events batch: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to send geofence events batch", e);
            }
        }

        logger.info("Successfully sent all {} geofence events in {} batches", geofenceEvents.size(), batchesCompleted);
    }

    // ========== GEOFENCE-RELATED METHODS ==========

    /**
     * Auto-generate 2 geofence places (Home and Work) for an elderly person if they don't exist
     * Uses realistic coordinates for Jaipur, India
     */
    private List<GeofencePlace> ensureGeofencePlacesExist(String elderlyPersonId) {
        // Check cache first
        if (elderlyPersonGeofences.containsKey(elderlyPersonId)) {
            return elderlyPersonGeofences.get(elderlyPersonId);
        }

        try {
            // Check if places already exist in database
            String url = supabaseGeofencePlacesUrl + "?elderly_person_id=eq." + elderlyPersonId + "&is_active=eq.true";
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            headers.set("Authorization", "Bearer " + supabaseApiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<GeofencePlace[]> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, GeofencePlace[].class);

            List<GeofencePlace> existingPlaces = response.getBody() != null ?
                Arrays.asList(response.getBody()) : new ArrayList<>();

            if (!existingPlaces.isEmpty()) {
                logger.info("Found {} existing geofence places for elderly person {}", existingPlaces.size(), elderlyPersonId);
                elderlyPersonGeofences.put(elderlyPersonId, existingPlaces);
                return existingPlaces;
            }

            // Generate 2 default geofence places (Home and Work) with realistic Jaipur coordinates
            List<Map<String, Object>> placesToInsert = new ArrayList<>();
            List<GeofencePlace> newPlaces = new ArrayList<>();

            // Home - Neo Clinic area, Jaipur (realistic residential area)
            String homeId = UUID.randomUUID().toString();
            double homeLat = roundToDecimalPlaces(26.88284340 + (random.nextDouble() * 0.01 - 0.005), 6);
            double homeLon = roundToDecimalPlaces(75.75532680 + (random.nextDouble() * 0.01 - 0.005), 6);

            Map<String, Object> homeMap = new LinkedHashMap<>();
            homeMap.put("id", homeId);
            homeMap.put("elderly_person_id", elderlyPersonId);
            homeMap.put("name", "Home");
            homeMap.put("place_type", "home");
            homeMap.put("latitude", homeLat);
            homeMap.put("longitude", homeLon);
            homeMap.put("radius_meters", 100);
            homeMap.put("address", "Neo Clinic Area, Jaipur");
            homeMap.put("color", "#3b82f6");
            homeMap.put("is_active", true);
            placesToInsert.add(homeMap);

            // Create GeofencePlace object for return
            GeofencePlace home = new GeofencePlace();
            home.setId(homeId);
            home.setElderlyPersonId(elderlyPersonId);
            home.setName("Home");
            home.setPlaceType("home");
            home.setLatitude(homeLat);
            home.setLongitude(homeLon);
            home.setRadiusMeters(100);
            home.setAddress("Neo Clinic Area, Jaipur");
            home.setColor("#3b82f6");
            home.setActive(true);
            newPlaces.add(home);

            // Work - Metropolis area, Jaipur (realistic commercial area, ~6.5 km from home)
            String workId = UUID.randomUUID().toString();
            double workLat = roundToDecimalPlaces(26.84029530 + (random.nextDouble() * 0.01 - 0.005), 6);
            double workLon = roundToDecimalPlaces(75.81860370 + (random.nextDouble() * 0.01 - 0.005), 6);

            Map<String, Object> workMap = new LinkedHashMap<>();
            workMap.put("id", workId);
            workMap.put("elderly_person_id", elderlyPersonId);
            workMap.put("name", "Work");
            workMap.put("place_type", "work");
            workMap.put("latitude", workLat);
            workMap.put("longitude", workLon);
            workMap.put("radius_meters", 100);
            workMap.put("address", "Metropolis Area, Jaipur");
            workMap.put("color", "#10b981");
            workMap.put("is_active", true);
            placesToInsert.add(workMap);

            // Create GeofencePlace object for return
            GeofencePlace work = new GeofencePlace();
            work.setId(workId);
            work.setElderlyPersonId(elderlyPersonId);
            work.setName("Work");
            work.setPlaceType("work");
            work.setLatitude(workLat);
            work.setLongitude(workLon);
            work.setRadiusMeters(100);
            work.setAddress("Metropolis Area, Jaipur");
            work.setColor("#10b981");
            work.setActive(true);
            newPlaces.add(work);

            // Insert places into database using Map with correct field names
            headers.setContentType(MediaType.APPLICATION_JSON);
            String jsonPlaces = objectMapper.writeValueAsString(placesToInsert);
            HttpEntity<String> insertEntity = new HttpEntity<>(jsonPlaces, headers);

            restTemplate.exchange(supabaseGeofencePlacesUrl, HttpMethod.POST, insertEntity, String.class);

            logger.info("Auto-generated {} geofence places for elderly person {}: Home and Work",
                newPlaces.size(), elderlyPersonId);

            elderlyPersonGeofences.put(elderlyPersonId, newPlaces);
            return newPlaces;

        } catch (Exception e) {
            logger.error("Error ensuring geofence places exist for elderly person {}: {}", elderlyPersonId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Generate historical GPS coordinates and geofence events for a GPS tracker device
     * Simulates realistic movement patterns between geofence places over multiple days
     */
    private List<Map<String, Object>> generateHistoricalGeofenceData(
            Device device, LocalDate startDate, LocalDate endDate, List<GeofencePlace> places, int daySkipInterval) {

        List<Map<String, Object>> allDataPoints = new ArrayList<>();

        if (places.isEmpty() || places.size() < 2) {
            logger.warn("Need at least 2 geofence places to generate movement patterns. Skipping GPS data for device {}", device.getId());
            return allDataPoints;
        }

        GeofencePlace home = places.get(0); // First place is Home
        GeofencePlace work = places.get(1); // Second place is Work

        logger.info("Generating historical GPS data for device {} from {} to {}", device.getId(), startDate, endDate);
        logger.info("Movement pattern: {} <-> {}", home.getName(), work.getName());

        LocalDate currentDate = startDate;
        GeofencePlace currentPlace = home;
        String currentGeofenceId = null; // Track which geofence we're currently in
        LocalDateTime lastEntryTime = null;
        int dayCounter = 0;

        while (!currentDate.isAfter(endDate)) {
            // Skip days based on frequency
            if (dayCounter % daySkipInterval != 0) {
                currentDate = currentDate.plusDays(1);
                dayCounter++;
                continue;
            }
            dayCounter++;
            DayType dayType = getDayType(currentDate);

            // Generate daily movement pattern
            // Morning: At home (6:00 AM)
            LocalDateTime morningTime = currentDate.atTime(6, 0);
            if (currentPlace != home || currentGeofenceId == null) {
                // Generate entry event for home
                allDataPoints.addAll(createGeofenceEvent(device, home, "entry", morningTime));
                currentGeofenceId = home.getId();
                lastEntryTime = morningTime;
            }
            // Add GPS reading at home
            allDataPoints.add(createGPSReading(device, home, morningTime));

            if (dayType == DayType.WEEKDAY) {
                // Weekday pattern: Go to work

                // Exit home (8:00 AM)
                LocalDateTime exitHomeTime = currentDate.atTime(8, 0);
                allDataPoints.addAll(createGeofenceEvent(device, home, "exit", exitHomeTime,
                    lastEntryTime != null ? (int) ChronoUnit.MINUTES.between(lastEntryTime, exitHomeTime) : null));
                currentGeofenceId = null;

                // Travel time: Add in-transit GPS reading (8:15 AM)
                LocalDateTime transitTime = currentDate.atTime(8, 15);
                allDataPoints.add(createInTransitGPSReading(device, home, work, transitTime));

                // Arrive at work (8:30 AM)
                LocalDateTime arriveWorkTime = currentDate.atTime(8, 30);
                allDataPoints.addAll(createGeofenceEvent(device, work, "entry", arriveWorkTime));
                currentGeofenceId = work.getId();
                lastEntryTime = arriveWorkTime;
                currentPlace = work;

                // Afternoon: At work (2:00 PM)
                LocalDateTime afternoonTime = currentDate.atTime(14, 0);
                allDataPoints.add(createGPSReading(device, work, afternoonTime));

                // Exit work (5:00 PM)
                LocalDateTime exitWorkTime = currentDate.atTime(17, 0);
                allDataPoints.addAll(createGeofenceEvent(device, work, "exit", exitWorkTime,
                    lastEntryTime != null ? (int) ChronoUnit.MINUTES.between(lastEntryTime, exitWorkTime) : null));
                currentGeofenceId = null;

                // Travel back: Add in-transit GPS reading (5:15 PM)
                LocalDateTime transitBackTime = currentDate.atTime(17, 15);
                allDataPoints.add(createInTransitGPSReading(device, work, home, transitBackTime));

                // Arrive home (5:30 PM)
                LocalDateTime arriveHomeTime = currentDate.atTime(17, 30);
                allDataPoints.addAll(createGeofenceEvent(device, home, "entry", arriveHomeTime));
                currentGeofenceId = home.getId();
                lastEntryTime = arriveHomeTime;
                currentPlace = home;

                // Evening: At home (8:00 PM)
                LocalDateTime eveningTime = currentDate.atTime(20, 0);
                allDataPoints.add(createGPSReading(device, home, eveningTime));

            } else {
                // Weekend pattern: Mostly at home with occasional short trip

                // Stay at home during morning
                LocalDateTime afternoonTime = currentDate.atTime(14, 0);
                allDataPoints.add(createGPSReading(device, home, afternoonTime));

                // Evening: At home
                LocalDateTime eveningTime = currentDate.atTime(20, 0);
                allDataPoints.add(createGPSReading(device, home, eveningTime));
            }

            currentDate = currentDate.plusDays(1);
        }

        logger.info("Generated {} GPS data points and geofence events for device {}",
            allDataPoints.size(), device.getId());

        return allDataPoints;
    }

    /**
     * Create a GPS reading at a specific geofence location with small random variation
     */
    private Map<String, Object> createGPSReading(Device device, GeofencePlace place, LocalDateTime timestamp) {
        // Add small random variation within 30% of radius
        double maxVariationMeters = place.getRadiusMeters() * 0.3;
        double randomDistance = random.nextDouble() * maxVariationMeters;
        double randomBearing = random.nextDouble() * 360;

        double[] coords = moveByBearing(place.getLatitude(), place.getLongitude(), randomBearing, randomDistance);

        Map<String, Object> gpsData = new LinkedHashMap<>();
        gpsData.put("device_id", device.getId());
        gpsData.put("elderly_person_id", device.getElderlyPersonId());
        gpsData.put("data_type", "position");

        // Store as JSON object with lat/lon
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("latitude", roundToDecimalPlaces(coords[0], 6));
        position.put("longitude", roundToDecimalPlaces(coords[1], 6));
        position.put("accuracy", roundToDecimalPlaces(5 + random.nextDouble() * 20, 1)); // 5-25 meters
        try {
            gpsData.put("value", objectMapper.writeValueAsString(position));
        } catch (Exception e) {
            logger.error("Error serializing GPS position: {}", e.getMessage());
            gpsData.put("value", String.format("{\"latitude\":%.6f,\"longitude\":%.6f,\"accuracy\":%.2f}",
                coords[0], coords[1], 5 + random.nextDouble() * 20));
        }

        gpsData.put("unit", null);
        gpsData.put("recorded_at", timestamp.atZone(ZoneId.systemDefault()).toInstant().toString());

        return gpsData;
    }

    /**
     * Create a GPS reading in transit between two places
     */
    private Map<String, Object> createInTransitGPSReading(Device device, GeofencePlace from, GeofencePlace to, LocalDateTime timestamp) {
        // Calculate midpoint between two locations
        double midLat = (from.getLatitude() + to.getLatitude()) / 2;
        double midLon = (from.getLongitude() + to.getLongitude()) / 2;

        // Add some random variation
        double randomDistance = random.nextDouble() * 100; // Within 100 meters of midpoint
        double randomBearing = random.nextDouble() * 360;
        double[] coords = moveByBearing(midLat, midLon, randomBearing, randomDistance);

        Map<String, Object> gpsData = new LinkedHashMap<>();
        gpsData.put("device_id", device.getId());
        gpsData.put("elderly_person_id", device.getElderlyPersonId());
        gpsData.put("data_type", "position");

        Map<String, Object> position = new LinkedHashMap<>();
        position.put("latitude", roundToDecimalPlaces(coords[0], 6));
        position.put("longitude", roundToDecimalPlaces(coords[1], 6));
        position.put("accuracy", roundToDecimalPlaces(10 + random.nextDouble() * 30, 1)); // 10-40 meters (less accurate during movement)
        try {
            gpsData.put("value", objectMapper.writeValueAsString(position));
        } catch (Exception e) {
            logger.error("Error serializing GPS position: {}", e.getMessage());
            gpsData.put("value", String.format("{\"latitude\":%.6f,\"longitude\":%.6f,\"accuracy\":%.2f}",
                coords[0], coords[1], 10 + random.nextDouble() * 30));
        }

        gpsData.put("unit", null);
        gpsData.put("recorded_at", timestamp.atZone(ZoneId.systemDefault()).toInstant().toString());

        return gpsData;
    }

    /**
     * Create geofence entry or exit events
     * Returns a list containing both the event record and optionally a GPS data point
     */
    private List<Map<String, Object>> createGeofenceEvent(Device device, GeofencePlace place,
                                                           String eventType, LocalDateTime timestamp) {
        return createGeofenceEvent(device, place, eventType, timestamp, null);
    }

    private List<Map<String, Object>> createGeofenceEvent(Device device, GeofencePlace place,
                                                           String eventType, LocalDateTime timestamp,
                                                           Integer durationMinutes) {
        List<Map<String, Object>> results = new ArrayList<>();

        // Create geofence event record for geofence_events table
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("elderly_person_id", device.getElderlyPersonId());
        event.put("place_id", place.getId());
        event.put("device_id", device.getId());
        event.put("event_type", eventType);
        event.put("latitude", place.getLatitude());
        event.put("longitude", place.getLongitude());
        event.put("timestamp", timestamp.atZone(ZoneId.systemDefault()).toInstant().toString());
        event.put("duration_minutes", durationMinutes);

        // Mark this as a geofence event (special marker for batch processing)
        event.put("_table", "geofence_events");
        results.add(event);

        logger.debug("Created {} event for {} at {}", eventType, place.getName(), timestamp);

        return results;
    }

    /**
     * Calculate distance between two GPS coordinates in meters (Haversine formula)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_METERS = 6371e3;

        double φ1 = Math.toRadians(lat1);
        double φ2 = Math.toRadians(lat2);
        double Δφ = Math.toRadians(lat2 - lat1);
        double Δλ = Math.toRadians(lon2 - lon1);

        double a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
                Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Move from a point by bearing and distance
     * Returns new [latitude, longitude]
     */
    private double[] moveByBearing(double lat, double lon, double bearing, double meters) {
        final double EARTH_RADIUS_METERS = 6371e3;

        double φ1 = Math.toRadians(lat);
        double λ1 = Math.toRadians(lon);
        double θ = Math.toRadians(bearing);
        double δ = meters / EARTH_RADIUS_METERS;

        double φ2 = Math.asin(Math.sin(φ1) * Math.cos(δ) +
                Math.cos(φ1) * Math.sin(δ) * Math.cos(θ));
        double λ2 = λ1 + Math.atan2(Math.sin(θ) * Math.sin(δ) * Math.cos(φ1),
                Math.cos(δ) - Math.sin(φ1) * Math.sin(φ2));

        return new double[]{Math.toDegrees(φ2), Math.toDegrees(λ2)};
    }
}

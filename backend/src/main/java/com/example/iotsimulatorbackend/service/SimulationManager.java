package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.DataTypeConfig;
import com.example.iotsimulatorbackend.model.GeofencePlace;
import com.example.iotsimulatorbackend.model.SimulationStatistics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

@Service
public class SimulationManager {
    private static final Logger logger = LoggerFactory.getLogger(SimulationManager.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Map<String, SimulationTask> activeSimulations = new ConcurrentHashMap<>();
    private final Map<String, SimulationStatistics> simulationStats = new ConcurrentHashMap<>();
    private final Map<String, String> elderlyPersonToSimulation = new ConcurrentHashMap<>(); // Track which elderly person has which simulation

    @Autowired
    private SimulatorService simulatorService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${simulator.device-ingest-url}")
    private String deviceIngestUrl;

    /**
     * Start a new simulation for an elderly person
     * If a simulation is already running for this elderly person, it will be stopped first
     */
    public String startSimulation(String elderlyPersonId, List<String> specificDeviceIds) {
        // Check if there's already a running simulation for this elderly person
        String existingSimulationId = elderlyPersonToSimulation.get(elderlyPersonId);
        if (existingSimulationId != null && activeSimulations.containsKey(existingSimulationId)) {
            logger.warn("⚠️  An existing simulation is already running for elderly person ID: {}. Stopping it...", elderlyPersonId);
            logger.warn("    Existing Simulation ID: {}", existingSimulationId);
            stopSimulation(existingSimulationId);
            logger.info("✓ Previous simulation stopped successfully");
        }

        String simulationId = UUID.randomUUID().toString();

        // Get all devices for this elderly person
        List<com.example.iotsimulatorbackend.model.Device> devicesToSimulate = new ArrayList<>();
        try {
            List<com.example.iotsimulatorbackend.model.Device> allDevices =
                simulatorService.getDevicesByElderlyPersonId(elderlyPersonId);

            if (specificDeviceIds != null && !specificDeviceIds.isEmpty()) {
                // Use only specified devices
                for (com.example.iotsimulatorbackend.model.Device device : allDevices) {
                    if (specificDeviceIds.contains(device.getId())) {
                        devicesToSimulate.add(device);
                    }
                }
            } else {
                // Use all devices
                devicesToSimulate.addAll(allDevices);
            }

            if (devicesToSimulate.isEmpty()) {
                logger.warn("No devices found for elderly person ID: {}", elderlyPersonId);
                return null;
            }

            // Create statistics tracking for this simulation
            SimulationStatistics statistics = new SimulationStatistics(simulationId);
            simulationStats.put(simulationId, statistics);

            // Create and start simulation task
            SimulationTask task = new SimulationTask(simulationId, elderlyPersonId, devicesToSimulate,
                simulatorService, restTemplate, objectMapper, deviceIngestUrl, statistics);
            activeSimulations.put(simulationId, task);
            elderlyPersonToSimulation.put(elderlyPersonId, simulationId); // Track this simulation
            task.start();

            logger.info("═══════════════════════════════════════════════════════════════════════════════════════");
            logger.info("🚀 SIMULATION STARTED");
            logger.info("   Simulation ID: {}", simulationId);
            logger.info("   Elderly Person ID: {}", elderlyPersonId);
            logger.info("   Total Devices: {}", devicesToSimulate.size());
            devicesToSimulate.forEach(d ->
                logger.info("   ├─ Device: {} ({})", d.getDeviceName(), d.getDeviceId())
            );
            logger.info("═══════════════════════════════════════════════════════════════════════════════════════");
            return simulationId;
        } catch (Exception e) {
            logger.error("❌ ERROR starting simulation for elderly person: {}", elderlyPersonId, e);
            return null;
        }
    }

    /**
     * Stop a simulation
     */
    public boolean stopSimulation(String simulationId) {
        SimulationTask task = activeSimulations.get(simulationId);
        if (task != null) {
            task.stop();
            activeSimulations.remove(simulationId);

            // Remove elderly person to simulation mapping
            String elderlyPersonId = task.getElderlyPersonId();
            if (elderlyPersonId != null) {
                elderlyPersonToSimulation.remove(elderlyPersonId);
            }

            // Log statistics summary
            SimulationStatistics stats = simulationStats.get(simulationId);
            if (stats != null) {
                logger.info("═══════════════════════════════════════════════════════════════════════════════════════");
                logger.info("⏹️  SIMULATION STOPPED");
                logger.info("   Simulation ID: {}", simulationId);
                logger.info("   Total Duration: {} seconds", stats.getElapsedTimeSeconds());
                logger.info("   Total Data Points: {}", stats.getTotalDataPointsGenerated());
                logger.info("   ✓ Successful: {} ({:.1f}%)", stats.getTotalDataPointsSuccessful(), stats.getSuccessRate());
                logger.info("   ✗ Failed: {}", stats.getTotalDataPointsFailed());
                logger.info("   Data Points/Minute: {:.2f}", stats.getDataPointsPerMinute());
                logger.info("═══════════════════════════════════════════════════════════════════════════════════════");
            }
            return true;
        }
        return false;
    }

    /**
     * Get simulation status
     */
    public boolean isSimulationRunning(String simulationId) {
        return activeSimulations.containsKey(simulationId);
    }

    /**
     * Get simulation statistics
     */
    public SimulationStatistics getSimulationStatistics(String simulationId) {
        return simulationStats.get(simulationId);
    }

    /**
     * Inner class to handle individual simulation tasks
     */
    private class SimulationTask {
        private final String simulationId;
        private final String elderlyPersonId;
        private final List<com.example.iotsimulatorbackend.model.Device> devices;
        private final SimulatorService simulatorService;
        private final RestTemplate restTemplate;
        private final ObjectMapper objectMapper;
        private final String deviceIngestUrl;
        private final SimulationStatistics statistics;
        private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
        private final Map<String, LocationGenerator> locationGenerators;
        private volatile boolean isRunning = false;
        private List<GeofencePlace> geofencePlaces = new ArrayList<>();

        public SimulationTask(String simulationId, String elderlyPersonId,
                            List<com.example.iotsimulatorbackend.model.Device> devices,
                            SimulatorService simulatorService, RestTemplate restTemplate,
                            ObjectMapper objectMapper, String deviceIngestUrl, SimulationStatistics statistics) {
            this.simulationId = simulationId;
            this.elderlyPersonId = elderlyPersonId;
            this.devices = devices;
            this.simulatorService = simulatorService;
            this.restTemplate = restTemplate;
            this.objectMapper = objectMapper;
            this.deviceIngestUrl = deviceIngestUrl;
            this.statistics = statistics;
            this.locationGenerators = new ConcurrentHashMap<>();
        }

        public void start() {
            isRunning = true;

            // Fetch geofence places for location-based simulation
            logger.info("📍 Attempting to load geofence places for elderly person: {}", elderlyPersonId);
            geofencePlaces = simulatorService.getGeofencePlacesByElderlyPersonId(elderlyPersonId);
            logger.info("📍 Geofence places loaded: {} places", geofencePlaces.size());
            if (!geofencePlaces.isEmpty()) {
                logger.info("📍 Loaded {} geofence places for location-based simulation", geofencePlaces.size());
                for (GeofencePlace place : geofencePlaces) {
                    logger.info("   ├─ {} ({}) - Lat: {}, Lon: {}, Radius: {}m",
                            place.getName(), place.getPlaceType(),
                            place.getLatitude(), place.getLongitude(), place.getRadiusMeters());
                }
            } else {
                logger.warn("⚠️  NO GEOFENCE PLACES FOUND! Location simulation will use random values.");
                logger.warn("    Please ensure geofence places are created for elderly person ID: {}", elderlyPersonId);
            }

            // For each device, get its data type configs and start scheduling
            int totalScheduled = 0;
            for (com.example.iotsimulatorbackend.model.Device device : devices) {
                try {
                    List<DataTypeConfig> configs = simulatorService.getDataTypesByDeviceId(device.getId());

                    if (configs.isEmpty()) {
                        logger.warn("⚠️  No data type configs found for device: {} ({})", device.getDeviceName(), device.getDeviceId());
                        continue;
                    }

                    // For each data type config, schedule generation
                    for (DataTypeConfig config : configs) {
                        logger.debug("   Data type: {} - ConfigType: {}", config.getDataType(), config.getConfigType());

                        // Initialize LocationGenerator for GPS/location devices
                        if (("gps".equals(config.getDataType()) || "location".equals(config.getDataType()))) {
                            String generatorKey = device.getId() + "_" + config.getDataType();
                            if (!geofencePlaces.isEmpty()) {
                                locationGenerators.put(generatorKey, new LocationGenerator(geofencePlaces));
                                logger.info("✅ Initialized LocationGenerator for device {} ({}) - will use {} geofence places",
                                        device.getDeviceName(), device.getDeviceId(), geofencePlaces.size());
                            } else {
                                logger.warn("⚠️  Not initializing LocationGenerator - no geofence places loaded");
                            }
                        }

                        scheduleDataGeneration(device, config);
                        totalScheduled++;
                    }
                } catch (Exception e) {
                    logger.error("❌ Error setting up simulation for device {} ({})", device.getDeviceId(), device.getDeviceName(), e);
                }
            }
            logger.info("📊 Scheduled {} data type generators across {} devices", totalScheduled, devices.size());
        }

        private void scheduleDataGeneration(com.example.iotsimulatorbackend.model.Device device, DataTypeConfig config) {
            // Calculate interval based on frequencyPerDay from device_types table
            // frequencyPerDay represents how many times per day this data should be generated
            // Formula: interval_seconds = (24 hours * 60 minutes * 60 seconds) / frequencyPerDay

            int frequencyPerDay = config.getFrequencyPerDay(); // Dynamically from device_types.data_frequency_per_day
            long intervalSeconds = (24 * 60 * 60) / frequencyPerDay; // 24 hours / frequency

            // Format interval nicely for display
            String intervalDisplay;
            if (intervalSeconds < 60) {
                intervalDisplay = intervalSeconds + "s";
            } else if (intervalSeconds < 3600) {
                intervalDisplay = (intervalSeconds / 60) + "m";
            } else {
                intervalDisplay = (intervalSeconds / 3600) + "h";
            }

            // Calculate next execution times for logging
            long nowMillis = System.currentTimeMillis();
            long nextExecutionMillis = nowMillis; // First execution is immediate (initialDelay=0)
            long subsequentExecutionMillis = nowMillis + (intervalSeconds * 1000);

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
            String nextExecutionTime = sdf.format(new java.util.Date(nextExecutionMillis));
            String subsequentExecutionTime = sdf.format(new java.util.Date(subsequentExecutionMillis));

            logger.info("⏱️  Scheduling {} for device {} ({}) - Frequency: {}/day",
                    config.getDisplayName(), device.getDeviceName(), device.getDeviceId(),
                    frequencyPerDay);
            logger.info("    Interval: {} | First execution: {} (now) | Next: {} | Then every {}",
                    intervalDisplay, nextExecutionTime, subsequentExecutionTime, intervalDisplay);

            // Create a task key for tracking
            String taskKey = device.getId() + "_" + config.getDataType();

            // Schedule the task to run at fixed rate
            // Initial delay = 0 means first execution happens immediately
            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
                if (isRunning) {
                    generateAndSendData(device, config);
                }
            }, 0, intervalSeconds, java.util.concurrent.TimeUnit.SECONDS);

            scheduledTasks.put(taskKey, future);
        }

        private void generateAndSendData(com.example.iotsimulatorbackend.model.Device device, DataTypeConfig config) {
            try {
                // Generate value - use LocationGenerator for GPS/location data
                Object generatedValue;
                if ("gps".equals(config.getDataType()) || "location".equals(config.getDataType())) {
                    String generatorKey = device.getId() + "_" + config.getDataType();
                    LocationGenerator generator = locationGenerators.get(generatorKey);
                    if (generator != null) {
                        com.example.iotsimulatorbackend.model.LocationData locationData = generator.generateNextLocation();
                        generatedValue = locationData.toMap();

                        // Log movement info
                        if (generator.getCurrentPlace() != null) {
                            logger.debug("📍 {} at {} ({}) - Lat: {}, Lon: {}",
                                    device.getDeviceId(),
                                    generator.getCurrentPlace().getName(),
                                    generator.getCurrentPlace().getPlaceType(),
                                    String.format("%.6f", generator.getCurrentLat()),
                                    String.format("%.6f", generator.getCurrentLon()));
                        }
                    } else {
                        // Fallback if no generator
                        generatedValue = generateValue(config);
                    }
                } else {
                    // Use standard value generation
                    generatedValue = generateValue(config);
                }

                // Create payload
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("device_id", device.getDeviceId()); // Use actual hardware device_id
                payload.put("data_type", config.getDataType());
                payload.put("value", generatedValue);

                // Only include unit if it's not empty (some data types like sleep_stage have no unit)
                String unit = config.getUnit();
                if (unit != null && !unit.isEmpty() && !unit.trim().isEmpty()) {
                    payload.put("unit", unit);
                }

                // Send to device-ingest endpoint
                HttpHeaders headers = new HttpHeaders();
                // Use the device's API key in the Authorization header (device-ingest validates this)
                headers.set("Authorization", "Bearer " + device.getApiKey());
                headers.set("Content-Type", "application/json");

                String payloadJson = objectMapper.writeValueAsString(payload);
                logger.debug("📤 Sending payload to device-ingest: {}", payloadJson);

                HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                    deviceIngestUrl, request, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    // Record success in statistics
                    statistics.recordSuccess(device.getId(), device.getDeviceName(),
                                           config.getDataType(), config.getDisplayName());
                    if (!("gps".equals(config.getDataType()) || "location".equals(config.getDataType()))) {
                        logger.debug("✓ {} [{}] = {} {} (device: {})",
                                config.getDisplayName(), config.getDataType(),
                                generatedValue, config.getUnit(), device.getDeviceId());
                    }
                } else {
                    statistics.recordFailure(device.getId(), device.getDeviceName(),
                                           config.getDataType(), config.getDisplayName());
                    logger.warn("⚠️  Data send failed for {} on {} - Status: {}",
                            config.getDisplayName(), device.getDeviceId(), response.getStatusCode());
                }
            } catch (Exception e) {
                statistics.recordFailure(device.getId(), device.getDeviceName(),
                                       config.getDataType(), config.getDisplayName());
                logger.warn("❌ Error generating/sending {} for device {} ({}): {}",
                        config.getDisplayName(), device.getDeviceName(), device.getDeviceId(), e.getMessage());
            }
        }

        private Object generateValue(DataTypeConfig config) {
            if ("enum".equals(config.getConfigType())) {
                // Return random enum value
                List<?> values = (List<?>) config.getConfig().get("values");
                if (values != null && !values.isEmpty()) {
                    return values.get(new Random().nextInt(values.size()));
                }
                return "unknown";
            } else {
                // Generate random value within range
                Map<String, Object> conf = config.getConfig();

                if ("blood_pressure".equals(config.getDataType())) {
                    // Special handling for blood pressure
                    int systolicMin = ((Number) conf.getOrDefault("systolic_min", 110)).intValue();
                    int systolicMax = ((Number) conf.getOrDefault("systolic_max", 130)).intValue();
                    int diastolicMin = ((Number) conf.getOrDefault("diastolic_min", 70)).intValue();
                    int diastolicMax = ((Number) conf.getOrDefault("diastolic_max", 85)).intValue();

                    Map<String, Integer> result = new LinkedHashMap<>();
                    result.put("systolic", systolicMin + new Random().nextInt(systolicMax - systolicMin + 1));
                    result.put("diastolic", diastolicMin + new Random().nextInt(diastolicMax - diastolicMin + 1));
                    return result;
                } else if ("gps".equals(config.getDataType()) || "location".equals(config.getDataType())) {
                    // Special handling for GPS/location data - generate random coordinates
                    // Default to India coordinates if no bounds specified
                    double latMin = ((Number) conf.getOrDefault("lat_min", 28.0)).doubleValue();
                    double latMax = ((Number) conf.getOrDefault("lat_max", 29.0)).doubleValue();
                    double lonMin = ((Number) conf.getOrDefault("lon_min", 77.0)).doubleValue();
                    double lonMax = ((Number) conf.getOrDefault("lon_max", 78.0)).doubleValue();

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("latitude", latMin + (Math.random() * (latMax - latMin)));
                    result.put("longitude", lonMin + (Math.random() * (lonMax - lonMin)));
                    result.put("accuracy", 10 + (Math.random() * 20)); // 10-30 meters
                    return result;
                } else {
                    double min = ((Number) conf.getOrDefault("min", 0)).doubleValue();
                    double max = ((Number) conf.getOrDefault("max", 100)).doubleValue();
                    int precision = ((Number) conf.getOrDefault("precision", 0)).intValue();

                    double value = min + (Math.random() * (max - min));

                    // Apply precision
                    if (precision > 0) {
                        double factor = Math.pow(10, precision);
                        value = Math.round(value * factor) / factor;
                    } else {
                        value = Math.round(value);
                    }

                    return value;
                }
            }
        }

        public void stop() {
            isRunning = false;
            // Cancel all scheduled tasks
            for (ScheduledFuture<?> future : scheduledTasks.values()) {
                future.cancel(true);
            }
            scheduledTasks.clear();
        }

        public String getElderlyPersonId() {
            return elderlyPersonId;
        }
    }
}

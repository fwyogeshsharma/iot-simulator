package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.DataTypeConfig;
import com.example.iotsimulatorbackend.model.GeofencePlace;
import com.example.iotsimulatorbackend.model.SimulationStatistics;
import com.fasterxml.jackson.databind.JsonNode;
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

    /**
     * Generate and send data for a single sensor/device
     */

    public Map<String, Object> generateAndSendSensorData(String deviceId, String dataType, String location) throws Exception {
        // Step 1: Get the device information directly from Supabase by device ID
        com.example.iotsimulatorbackend.model.Device targetDevice = getDeviceById(deviceId);

        if (targetDevice == null) {
            throw new Exception("Device not found with ID: " + deviceId);
        }

        // Step 2: Get the data type configuration
        List<DataTypeConfig> configs = simulatorService.getDataTypesByDeviceId(deviceId);
        DataTypeConfig targetConfig = null;

        for (DataTypeConfig config : configs) {
            if (config.getDataType().equals(dataType)) {
                targetConfig = config;
                break;
            }
        }

        if (targetConfig == null) {
            throw new Exception("Data type not found: " + dataType + " for device: " + deviceId);
        }

        String unit = targetConfig.getUnit();
        if (unit != null && unit.trim().isEmpty()) {
            unit = null;
        }

        // Step 3: Special handling for location data with geofences
        // Generate GPS data for ONE random geofence to trigger entry/exit event
        if ("location".equals(dataType) && targetDevice.getElderlyPersonId() != null) {
            List<GeofencePlace> geofences = simulatorService.getGeofencePlacesByElderlyPersonId(targetDevice.getElderlyPersonId());

            if (geofences != null && !geofences.isEmpty()) {
                // Pick a random geofence
                GeofencePlace selectedGeofence = geofences.get(new Random().nextInt(geofences.size()));
                logger.info("🎯 Generating GPS data for random geofence '{}' for elderly person: {}",
                    selectedGeofence.getName(), targetDevice.getElderlyPersonId());

                // Generate GPS coordinates within the selected geofence
                Map<String, Double> gpsCoords = generateGpsWithinGeofence(selectedGeofence);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("device_id", targetDevice.getDeviceId());
                payload.put("data_type", targetConfig.getDataType());
                payload.put("value", gpsCoords);

                if (unit != null) {
                    payload.put("unit", unit);
                }

                // Include location if provided
                if (location != null && !location.trim().isEmpty()) {
                    payload.put("location", location);
                }

                // Send to device-ingest endpoint
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + targetDevice.getApiKey());
                headers.set("Content-Type", "application/json");

                String payloadJson = objectMapper.writeValueAsString(payload);
                if (location != null && !location.trim().isEmpty()) {
                    logger.debug("📤 Sending GPS for geofence '{}' at location '{}': {}", selectedGeofence.getName(), location, payloadJson);
                } else {
                    logger.debug("📤 Sending GPS for geofence '{}': {}", selectedGeofence.getName(), payloadJson);
                }

                HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(
                    deviceIngestUrl, request, String.class);

                Map<String, Object> result = new LinkedHashMap<>();
                if (response.getStatusCode().is2xxSuccessful()) {
                    Map<String, Object> locationInfo = new LinkedHashMap<>();
                    locationInfo.put("geofenceName", selectedGeofence.getName());
                    locationInfo.put("latitude", gpsCoords.get("latitude"));
                    locationInfo.put("longitude", gpsCoords.get("longitude"));
                    locationInfo.put("radius", selectedGeofence.getRadiusMeters());

                    List<Map<String, Object>> generatedLocations = new ArrayList<>();
                    generatedLocations.add(locationInfo);

                    result.put("success", true);
                    result.put("message", String.format("Generated GPS data for geofence: %s", selectedGeofence.getName()));
                    result.put("deviceId", targetDevice.getDeviceId());
                    result.put("dataType", targetConfig.getDataType());
                    result.put("displayName", targetConfig.getDisplayName());
                    result.put("generatedLocations", generatedLocations);

                    logger.info("✓ Generated and sent GPS data for geofence '{}' at ({}, {})",
                        selectedGeofence.getName(), gpsCoords.get("latitude"), gpsCoords.get("longitude"));
                } else {
                    result.put("success", false);
                    result.put("message", "Failed to send GPS data - Status: " + response.getStatusCode());
                    result.put("error", response.getBody());
                    logger.warn("✗ Failed to send GPS data for geofence '{}': {}",
                        selectedGeofence.getName(), response.getStatusCode());
                }

                return result;
            }
        }

        // Step 4: For non-location data, generate single value as before
        Object generatedValue = generateValue(targetConfig);

        // Step 5: Create payload
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", targetDevice.getDeviceId());
        payload.put("data_type", targetConfig.getDataType());
        payload.put("value", generatedValue);

        if (unit != null) {
            payload.put("unit", unit);
        }

        // Include location if provided
        if (location != null && !location.trim().isEmpty()) {
            payload.put("location", location);
        }

        // Step 6: Send to device-ingest endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + targetDevice.getApiKey());
        headers.set("Content-Type", "application/json");

        String payloadJson = objectMapper.writeValueAsString(payload);
        if (location != null && !location.trim().isEmpty()) {
            logger.debug("📤 Sending individual sensor payload for location '{}' to device-ingest: {}", location, payloadJson);
        } else {
            logger.debug("📤 Sending individual sensor payload to device-ingest: {}", payloadJson);
        }

        HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
            deviceIngestUrl, request, String.class);

        Map<String, Object> result = new LinkedHashMap<>();
        if (response.getStatusCode().is2xxSuccessful()) {
            result.put("success", true);
            result.put("message", "Data generated and sent successfully");
            result.put("deviceId", targetDevice.getDeviceId());
            result.put("dataType", targetConfig.getDataType());
            result.put("displayName", targetConfig.getDisplayName());
            result.put("value", generatedValue);
            result.put("unit", unit);
            if (location != null && !location.trim().isEmpty()) {
                logger.info("✓ Generated and sent {} [{}] = {} {} at location '{}'",
                        targetConfig.getDisplayName(), targetConfig.getDataType(),
                        generatedValue, unit != null ? unit : "", location);
            } else {
                logger.info("✓ Generated and sent {} [{}] = {} {}",
                        targetConfig.getDisplayName(), targetConfig.getDataType(),
                        generatedValue, unit != null ? unit : "");
            }
        } else {
            result.put("success", false);
            result.put("message", "Failed to send data - Status: " + response.getStatusCode());
            result.put("error", response.getBody());
        }

        return result;
    }

    /**
     * Get a device by its ID directly from Supabase
     */
    private com.example.iotsimulatorbackend.model.Device getDeviceById(String deviceId) throws Exception {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", simulatorService.getSupabaseApiKey());
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String devicesUrl = simulatorService.getDevicesUrl() + "?id=eq." + deviceId;
            ResponseEntity<String> response = restTemplate.exchange(devicesUrl, HttpMethod.GET, entity, String.class);
            JsonNode jsonArray = objectMapper.readTree(response.getBody());

            if (jsonArray.size() == 0) {
                return null;
            }

            JsonNode deviceNode = jsonArray.get(0);
            com.example.iotsimulatorbackend.model.Device device = new com.example.iotsimulatorbackend.model.Device(
                deviceNode.get("id").asText(),
                deviceNode.get("elderly_person_id").asText(),
                deviceNode.get("device_name").asText(),
                deviceNode.get("device_id").asText(),
                deviceNode.get("api_key").asText(),
                deviceNode.has("device_type") && !deviceNode.get("device_type").isNull()
                    ? deviceNode.get("device_type").asText() : "",
                deviceNode.has("description") && !deviceNode.get("description").isNull()
                    ? deviceNode.get("description").asText() : ""
            );

            // Set location if available
            if (deviceNode.has("location") && !deviceNode.get("location").isNull()) {
                device.setLocation(deviceNode.get("location").asText());
            }

            return device;
        } catch (Exception e) {
            logger.error("Error fetching device by ID: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Generate GPS coordinates within a specific geofence boundary
     */
    private Map<String, Double> generateGpsWithinGeofence(GeofencePlace geofence) {
        double centerLat = geofence.getLatitude();
        double centerLon = geofence.getLongitude();
        int radiusMeters = geofence.getRadiusMeters();

        // Generate a random point within the circle defined by geofence
        double randomDistance = Math.random() * radiusMeters;
        double randomBearing = Math.random() * 360;

        double[] newCoords = moveByBearing(centerLat, centerLon, randomBearing, randomDistance);
        double latitude = newCoords[0];
        double longitude = newCoords[1];

        // Round to 6 decimal places for realistic GPS coordinates
        double factor = Math.pow(10, 6);
        latitude = Math.round(latitude * factor) / factor;
        longitude = Math.round(longitude * factor) / factor;

        Map<String, Double> result = new LinkedHashMap<>();
        result.put("latitude", latitude);
        result.put("longitude", longitude);
        return result;
    }

    /**
     * Generate a value based on data type configuration
     */
    private Object generateValue(DataTypeConfig config) {
        logger.debug("🔧 Generating value for data type: {}, configType: {}, config: {}",
            config.getDataType(), config.getConfigType(), config.getConfig());

        if ("enum".equals(config.getConfigType())) {
            List<?> values = (List<?>) config.getConfig().get("values");
            if (values != null && !values.isEmpty()) {
                return values.get(new Random().nextInt(values.size()));
            }
            return "unknown";
        } else {
            Map<String, Object> conf = config.getConfig();

            if ("blood_pressure".equals(config.getDataType())) {
                int systolicMin = ((Number) conf.getOrDefault("systolic_min", 110)).intValue();
                int systolicMax = ((Number) conf.getOrDefault("systolic_max", 130)).intValue();
                int diastolicMin = ((Number) conf.getOrDefault("diastolic_min", 70)).intValue();
                int diastolicMax = ((Number) conf.getOrDefault("diastolic_max", 85)).intValue();

                Map<String, Integer> result = new LinkedHashMap<>();
                result.put("systolic", systolicMin + new Random().nextInt(systolicMax - systolicMin + 1));
                result.put("diastolic", diastolicMin + new Random().nextInt(diastolicMax - diastolicMin + 1));
                return result;
            } else if ("location".equals(config.getDataType())) {
                // Special handling for GPS location data (check data type, not config)
                Map<String, Double> latRange = (Map<String, Double>) conf.get("latitude");
                Map<String, Double> lonRange = (Map<String, Double>) conf.get("longitude");

                double latMin = -90.0;
                double latMax = 90.0;
                double lonMin = -180.0;
                double lonMax = 180.0;

                if (latRange != null) {
                    latMin = latRange.getOrDefault("min", -90.0);
                    latMax = latRange.getOrDefault("max", 90.0);
                }
                if (lonRange != null) {
                    lonMin = lonRange.getOrDefault("min", -180.0);
                    lonMax = lonRange.getOrDefault("max", 180.0);
                }

                double latitude = latMin + (Math.random() * (latMax - latMin));
                double longitude = lonMin + (Math.random() * (lonMax - lonMin));

                // Round to 6 decimal places for realistic GPS coordinates
                double factor = Math.pow(10, 6);
                latitude = Math.round(latitude * factor) / factor;
                longitude = Math.round(longitude * factor) / factor;

                logger.debug("📍 Generated GPS location: latitude={}, longitude={}", latitude, longitude);

                Map<String, Double> result = new LinkedHashMap<>();
                result.put("latitude", latitude);
                result.put("longitude", longitude);
                return result;
            } else {
                double min = ((Number) conf.getOrDefault("min", 0)).doubleValue();
                double max = ((Number) conf.getOrDefault("max", 100)).doubleValue();
                int precision = ((Number) conf.getOrDefault("precision", 0)).intValue();

                logger.debug("📊 Generating random number - min: {}, max: {}, precision: {}", min, max, precision);

                double value = min + (Math.random() * (max - min));

                if (precision > 0) {
                    double factor = Math.pow(10, precision);
                    value = Math.round(value * factor) / factor;
                } else {
                    value = Math.round(value);
                }

                logger.debug("✓ Generated value: {}", value);
                return value;
            }
        }
    }

    /**
     * Move from a point by bearing and distance
     * Returns new [latitude, longitude]
     * Uses haversine formula for accurate distance calculations on earth's surface
     */
    private double[] moveByBearing(double lat, double lon, double bearing, double meters) {
        double EARTH_RADIUS_METERS = 6371e3;
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

                // Include location if available
                if (device.getLocation() != null && !device.getLocation().trim().isEmpty()) {
                    payload.put("location", device.getLocation());
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

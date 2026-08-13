package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.Device;
import com.example.iotsimulatorbackend.model.DataTypeConfig;
import com.example.iotsimulatorbackend.model.FloorPlan;
import com.example.iotsimulatorbackend.model.GeofencePlace;
import com.example.iotsimulatorbackend.model.DeviceCompany;
import com.example.iotsimulatorbackend.model.DeviceModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

@Service
public class SimulatorService {
    @Autowired
    private RestTemplate restTemplate;

    /**
     * Reference tables - device types, their data configs, companies, models - are the
     * same for every caller and change about as often as a migration runs. Fetching them
     * on each request made a single device lookup six sequential Supabase round trips
     * (~2s), and gave it six chances to hit a stalled connection. Caching them briefly
     * cuts that to two.
     */
    private static final long REFERENCE_CACHE_TTL_MS = 5 * 60 * 1000L;

    private final Map<String, CachedJson> referenceCache = new ConcurrentHashMap<>();

    private static final class CachedJson {
        final JsonNode body;
        final long expiresAt;

        CachedJson(JsonNode body, long expiresAt) {
            this.body = body;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * GET a reference table, serving a cached copy while it is fresh.
     *
     * If the refresh fails and a stale copy exists, the stale copy is returned rather
     * than propagating the failure: reference data going a few minutes out of date is a
     * far better outcome than the device list coming back empty because a lookup of
     * company names timed out.
     */
    private JsonNode getReferenceJson(String url, HttpEntity<String> entity) throws Exception {
        long now = System.currentTimeMillis();
        CachedJson cached = referenceCache.get(url);
        if (cached != null && cached.expiresAt > now) {
            return cached.body;
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode body = objectMapper.readTree(response.getBody());
            referenceCache.put(url, new CachedJson(body, now + REFERENCE_CACHE_TTL_MS));
            return body;
        } catch (Exception e) {
            if (cached != null) {
                System.err.println("Reference fetch failed for " + url + " (" + e.getMessage()
                        + ") - serving cached copy");
                return cached.body;
            }
            throw e;
        }
    }

    @Value("${supabase.devices-url}")
    private String devicesUrl;

    @Value("${supabase.device-data-url}")
    private String deviceDataUrl;

    @Value("${supabase.device-type-data-configs-url}")
    private String deviceTypeDataConfigsUrl;

    @Value("${supabase.device-types-url:https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/device_types}")
    private String deviceTypesUrl;

    @Value("${supabase.elderly-persons-url:https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/elderly_persons}")
    private String elderlyPersonsUrl;

    @Value("${supabase.geofence-places-url:https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/geofence_places}")
    private String geofencePlacesUrl;

    @Value("${supabase.floor-plans-url:https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/floor_plans}")
    private String floorPlansUrl;

    @Value("${supabase.device-companies-url:https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/device_companies}")
    private String deviceCompaniesUrl;

    @Value("${supabase.device-models-url:https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/device_models}")
    private String deviceModelsUrl;

    @Value("${supabase.disease-profiles-url:https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/disease_profiles}")
    private String diseaseProfilesUrl;

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    @Autowired
    private ObjectMapper objectMapper;

    public List<Device> getDevicesByElderlyPersonId(String profileId) {
        try {
            // The profileId parameter is the user's auth ID (profiles.id = auth.users.id)
            // Two-step lookup process:
            // Step 1: Try to find the elderly_person_id from elderly_persons table
            //         where elderly_persons.user_id = profileId
            // Step 2: If that fails (no elderly_persons data), fall back to using profileId directly
            //         as elderly_person_id (for systems that don't use elderly_persons table)

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Step 1: Try to query elderly_persons table to get the elderly_person_id
            String elderlyPersonUrl = elderlyPersonsUrl + "?user_id=eq." + profileId;
            System.out.println("Attempting to find elderly person for user ID: " + profileId);

            ResponseEntity<String> elderlyPersonResponse = restTemplate.exchange(elderlyPersonUrl, HttpMethod.GET, entity, String.class);
            JsonNode elderlyPersonArray = objectMapper.readTree(elderlyPersonResponse.getBody());

            String elderlyPersonId;
            if (elderlyPersonArray.size() > 0) {
                // Extract the elderly_person_id from the first (and should be only) result
                elderlyPersonId = elderlyPersonArray.get(0).get("id").asText();
                System.out.println("Found elderly person ID: " + elderlyPersonId + " for user ID: " + profileId);
            } else {
                // Fallback: Use profileId directly as elderly_person_id
                // (This handles systems where profiles are used as elderly persons directly)
                elderlyPersonId = profileId;
                System.out.println("No elderly person record found. Using profile ID directly as elderly_person_id: " + profileId);
            }

            // Step 2: Now query devices using the elderly_person_id
            String devicesQueryUrl = devicesUrl + "?elderly_person_id=eq." + elderlyPersonId;
            System.out.println("Querying devices with URL: " + devicesQueryUrl);

            ResponseEntity<String> devicesResponse = restTemplate.exchange(devicesQueryUrl, HttpMethod.GET, entity, String.class);
            JsonNode jsonArray = objectMapper.readTree(devicesResponse.getBody());

            // Fetch device types for description mapping
            String allDeviceTypesUrl = deviceTypesUrl + "?select=id,code,name,description,supports_position_tracking";
            JsonNode deviceTypesArray = getReferenceJson(allDeviceTypesUrl, entity);

            Map<String, String> deviceTypeDescriptions = new HashMap<>();
            Map<String, Boolean> deviceTypeSupportsPosition = new HashMap<>();
            Map<String, String> deviceTypeIdByCode = new HashMap<>();
            for (JsonNode typeNode : deviceTypesArray) {
                String code = typeNode.get("code").asText();
                if (typeNode.has("id") && !typeNode.get("id").isNull()) {
                    deviceTypeIdByCode.put(code, typeNode.get("id").asText());
                }
                String description = typeNode.has("description") && !typeNode.get("description").isNull()
                    ? typeNode.get("description").asText()
                    : "";
                deviceTypeDescriptions.put(code, description);
                Boolean supportsPosition = typeNode.has("supports_position_tracking") && !typeNode.get("supports_position_tracking").isNull()
                    ? typeNode.get("supports_position_tracking").asBoolean()
                    : false;
                deviceTypeSupportsPosition.put(code, supportsPosition);

            }
            // Data types each device TYPE declares. Devices with no model assigned
            // (most wearables here) have no model-level supported_data_types, so this
            // is the only signal the UI has for what a device can report. Mirrors the
            // config-first ordering of isSleepCapable() on the generation side.
            Map<String, List<String>> deviceTypeDataTypes = new HashMap<>();
            try {
                String configsUrl = deviceTypeDataConfigsUrl + "?select=device_type_id,data_type";
                JsonNode configsArray = getReferenceJson(configsUrl, entity);

                Map<String, List<String>> byTypeId = new HashMap<>();
                for (JsonNode cfg : configsArray) {
                    if (cfg.get("device_type_id").isNull()) continue;
                    byTypeId.computeIfAbsent(cfg.get("device_type_id").asText(), k -> new ArrayList<>())
                            .add(cfg.get("data_type").asText());
                }
                for (Map.Entry<String, String> e : deviceTypeIdByCode.entrySet()) {
                    List<String> types = byTypeId.get(e.getValue());
                    if (types != null) {
                        deviceTypeDataTypes.put(e.getKey(), types);
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: could not fetch device type data configs: " + e.getMessage());
            }

            // Fetch company and model data for enrichment
            Map<String, String> companyNames = new HashMap<>();
            Map<String, String> modelNames = new HashMap<>();
            Map<String, Map<String, Object>> modelSpecifications = new HashMap<>();
            Map<String, List<String>> modelSupportedDataTypes = new HashMap<>();

            try {
                // Fetch all companies
                String allCompaniesUrl = deviceCompaniesUrl + "?select=id,name&is_active=eq.true";
                JsonNode companiesArray = getReferenceJson(allCompaniesUrl, entity);
                for (JsonNode companyNode : companiesArray) {
                    companyNames.put(companyNode.get("id").asText(), companyNode.get("name").asText());
                }

                // Fetch all models
                String allModelsUrl = deviceModelsUrl + "?select=id,name,specifications,supported_data_types&is_active=eq.true";
                JsonNode modelsArray = getReferenceJson(allModelsUrl, entity);
                for (JsonNode modelNode : modelsArray) {
                    String modelId = modelNode.get("id").asText();
                    modelNames.put(modelId, modelNode.get("name").asText());
                    if (modelNode.has("specifications") && !modelNode.get("specifications").isNull()) {
                        JsonNode specsNode = modelNode.get("specifications");
                        Map<String, Object> specs = objectMapper.convertValue(specsNode, Map.class);
                        modelSpecifications.put(modelId, specs);
                    }
                    // Carried through so the UI can tell which devices support which
                    // data types (e.g. only offering condition profiles for sleep-capable
                    // devices). Previously only getDeviceById populated this.
                    if (modelNode.has("supported_data_types") && modelNode.get("supported_data_types").isArray()) {
                        List<String> types = new ArrayList<>();
                        for (JsonNode t : modelNode.get("supported_data_types")) {
                            types.add(t.asText());
                        }
                        modelSupportedDataTypes.put(modelId, types);
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not fetch company/model data: " + e.getMessage());
            }

            List<Device> devices = new ArrayList<>();
            for (JsonNode deviceNode : jsonArray) {
                String deviceTypeCode = deviceNode.has("device_type") && !deviceNode.get("device_type").isNull()
                    ? deviceNode.get("device_type").asText()
                    : "";
                String description = deviceTypeDescriptions.getOrDefault(deviceTypeCode, "");

                Device device = new Device(
                    deviceNode.get("id").asText(),
                    deviceNode.get("elderly_person_id").asText(),
                    deviceNode.get("device_name").asText(),
                    deviceNode.get("device_id").asText(),
                    deviceNode.get("api_key").asText(),
                    deviceTypeCode,
                    description
                );

                // Set location if available
                if (deviceNode.has("location") && !deviceNode.get("location").isNull()) {
                    device.setLocation(deviceNode.get("location").asText());
                }

                // Set company_id and model_id if available
                if (deviceNode.has("company_id") && !deviceNode.get("company_id").isNull()) {
                    String companyId = deviceNode.get("company_id").asText();
                    device.setCompanyId(companyId);
                    device.setCompanyName(companyNames.getOrDefault(companyId, ""));
                }

                if (deviceNode.has("model_id") && !deviceNode.get("model_id").isNull()) {
                    String modelId = deviceNode.get("model_id").asText();
                    device.setModelId(modelId);
                    device.setModelName(modelNames.getOrDefault(modelId, ""));
                    device.setModelSpecifications(modelSpecifications.getOrDefault(modelId, null));
                    device.setSupportedDataTypes(modelSupportedDataTypes.getOrDefault(modelId, null));
                }
                // Fall back to the device type's declared data types when no model is
                // assigned, so the UI can still tell what this device reports.
                if (device.getSupportedDataTypes() == null || device.getSupportedDataTypes().isEmpty()) {
                    device.setSupportedDataTypes(deviceTypeDataTypes.get(deviceTypeCode));
                }

                // Set supports_position_tracking from device type
                device.setSupportsPositionTracking(deviceTypeSupportsPosition.getOrDefault(deviceTypeCode, false));

                devices.add(device);
            }

            // Log results for debugging
            if (devices.isEmpty()) {
                System.out.println("No devices found for elderly person ID: " + elderlyPersonId);
            } else {
                System.out.println("Found " + devices.size() + " devices for elderly person ID: " + elderlyPersonId);
            }

            return devices;
        } catch (Exception e) {
            // Deliberately not returning an empty list here. Doing so made a failed
            // lookup indistinguishable from a person who genuinely has no devices, and
            // the UI reported "No devices found for this elderly person" while the real
            // problem was that Supabase never answered.
            System.err.println("Error fetching devices from Supabase: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Could not load devices: " + e.getMessage(), e);
        }
    }

    public List<DataTypeConfig> getDataTypesByDeviceId(String deviceId) {
        try {
            // Step 1: Get device to find its device_type code
            String deviceUrl = devicesUrl + "?id=eq." + deviceId + "&select=device_type";
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> deviceResponse = restTemplate.exchange(deviceUrl, HttpMethod.GET, entity, String.class);
            JsonNode deviceArray = objectMapper.readTree(deviceResponse.getBody());

            if (deviceArray.size() == 0) {
                System.out.println("Device not found with ID: " + deviceId);
                return new ArrayList<>();
            }

            // Extract device_type code from the device
            String deviceTypeCode = deviceArray.get(0).get("device_type").asText();
            System.out.println("Device type code for device " + deviceId + ": " + deviceTypeCode);

            // Step 2: Query device_types table to get the UUID id and data_frequency_per_day
            String deviceTypeUrl = deviceTypesUrl + "?code=eq." + deviceTypeCode + "&select=id,data_frequency_per_day";
            ResponseEntity<String> deviceTypeResponse = restTemplate.exchange(deviceTypeUrl, HttpMethod.GET, entity, String.class);
            JsonNode deviceTypeArray = objectMapper.readTree(deviceTypeResponse.getBody());

            if (deviceTypeArray.size() == 0) {
                System.out.println("Device type not found with code: " + deviceTypeCode);
                return new ArrayList<>();
            }

            // Extract device_type_id and data_frequency_per_day
            String deviceTypeId = deviceTypeArray.get(0).get("id").asText();
            int frequencyPerDay = deviceTypeArray.get(0).has("data_frequency_per_day") && !deviceTypeArray.get(0).get("data_frequency_per_day").isNull()
                    ? deviceTypeArray.get(0).get("data_frequency_per_day").asInt()
                    : 4; // Default fallback value if not found
            System.out.println("Device type ID for code " + deviceTypeCode + ": " + deviceTypeId + ", Frequency: " + frequencyPerDay + " per day");

            // Step 3: Query device_type_data_configs for this device_type_id
            String configUrl = deviceTypeDataConfigsUrl + "?device_type_id=eq." + deviceTypeId + "&order=sort_order";
            ResponseEntity<String> configResponse = restTemplate.exchange(configUrl, HttpMethod.GET, entity, String.class);
            JsonNode configArray = objectMapper.readTree(configResponse.getBody());

            List<DataTypeConfig> dataTypeConfigs = new ArrayList<>();
            for (JsonNode configNode : configArray) {
                String dataType = configNode.get("data_type").asText();
                String displayName = configNode.get("display_name").asText();
                String unit = configNode.has("unit") && !configNode.get("unit").isNull() ? configNode.get("unit").asText() : "";
                String valueType = configNode.get("value_type").asText();

                // Parse sample_data_config to determine config type (range or enum)
                JsonNode sampleConfig = configNode.get("sample_data_config");
                String configType = "range";
                Map<String, Object> config = new HashMap<>();

                if (sampleConfig != null && !sampleConfig.isNull()) {
                    // Check if it's already a JSON object or a string
                    JsonNode parsedSample;
                    if (sampleConfig.isTextual()) {
                        // It's a string, parse it
                        String sampleStr = sampleConfig.asText();
                        parsedSample = objectMapper.readTree(sampleStr);
                    } else {
                        // It's already a JSON object
                        parsedSample = sampleConfig;
                    }

                    // Check if this is a multi-field config (like model specifications)
                    // A multi-field config has multiple top-level keys, each with its own config
                    boolean isMultiField = false;
                    if (!parsedSample.has("type") && parsedSample.size() > 0) {
                        // Check if all fields have nested config objects
                        boolean allFieldsHaveConfig = true;
                        Iterator<String> fieldNames = parsedSample.fieldNames();
                        while (fieldNames.hasNext()) {
                            String fieldName = fieldNames.next();
                            JsonNode fieldValue = parsedSample.get(fieldName);
                            if (!fieldValue.isObject()) {
                                allFieldsHaveConfig = false;
                                break;
                            }
                        }
                        isMultiField = allFieldsHaveConfig && parsedSample.size() > 0;
                    }

                    if (isMultiField) {
                        // Multi-field configuration (like model specifications)
                        configType = "combined";
                        config.put("type", "combined");
                        config.put("fields", objectMapper.convertValue(parsedSample, Map.class));
                    } else if (parsedSample.has("type")) {
                        String type = parsedSample.get("type").asText();
                        if ("enum".equals(type)) {
                            configType = "enum";
                            if (parsedSample.has("values")) {
                                config.put("values", objectMapper.convertValue(parsedSample.get("values"), List.class));
                            }
                        } else if ("boolean".equals(type)) {
                            configType = "enum";
                            config.put("values", List.of(true, false));
                        } else if ("random_number".equals(type)) {
                            configType = "range";
                            if (parsedSample.has("min")) config.put("min", parsedSample.get("min").asDouble());
                            if (parsedSample.has("max")) config.put("max", parsedSample.get("max").asDouble());
                            if (parsedSample.has("precision")) config.put("precision", parsedSample.get("precision").asInt());
                        } else if ("blood_pressure".equals(type)) {
                            configType = "range";
                            if (parsedSample.has("systolic")) {
                                config.put("systolic_min", parsedSample.get("systolic").get("min").asInt());
                                config.put("systolic_max", parsedSample.get("systolic").get("max").asInt());
                            }
                            if (parsedSample.has("diastolic")) {
                                config.put("diastolic_min", parsedSample.get("diastolic").get("min").asInt());
                                config.put("diastolic_max", parsedSample.get("diastolic").get("max").asInt());
                            }
                        } else if ("gps".equals(type)) {
                            // Special handling for GPS location data
                            configType = "range";
                            config.put("type", "gps");
                            if (parsedSample.has("latitude")) {
                                config.put("latitude", objectMapper.convertValue(parsedSample.get("latitude"), Map.class));
                            }
                            if (parsedSample.has("longitude")) {
                                config.put("longitude", objectMapper.convertValue(parsedSample.get("longitude"), Map.class));
                            }
                        }
                    }
                }

                DataTypeConfig dtConfig = new DataTypeConfig(dataType, displayName, unit, valueType, configType, config, frequencyPerDay);
                dataTypeConfigs.add(dtConfig);
            }

            System.out.println("Found " + dataTypeConfigs.size() + " data type configs for device type " + deviceTypeCode);
            return dataTypeConfigs;
        } catch (Exception e) {
            System.err.println("Error fetching data type configs from Supabase: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // Getter methods for use by other services
    public String getDevicesUrl() {
        return devicesUrl;
    }

    public String getSupabaseApiKey() {
        return supabaseApiKey;
    }

    /**
     * Fetch geofence places for an elderly person
     * The profileId parameter might be a user_id, so we need to look up the actual elderly_person_id
     */
    public List<GeofencePlace> getGeofencePlacesByElderlyPersonId(String profileId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Step 1: Try to find the elderly_person_id from elderly_persons table
            //         where elderly_persons.user_id = profileId
            String elderlyPersonUrl = elderlyPersonsUrl + "?user_id=eq." + profileId;
            System.out.println("Looking up elderly person for user ID: " + profileId);

            ResponseEntity<String> elderlyPersonResponse = restTemplate.exchange(elderlyPersonUrl, HttpMethod.GET, entity, String.class);
            JsonNode elderlyPersonArray = objectMapper.readTree(elderlyPersonResponse.getBody());

            String elderlyPersonId;
            if (elderlyPersonArray.size() > 0) {
                // Found the elderly person record - use the actual elderly_person_id
                elderlyPersonId = elderlyPersonArray.get(0).get("id").asText();
                System.out.println("✓ Found elderly person ID: " + elderlyPersonId + " for user ID: " + profileId);
            } else {
                // No elderly_persons record found - fallback to using profileId directly
                elderlyPersonId = profileId;
                System.out.println("⚠️  No elderly person record found. Using profile ID directly: " + profileId);
            }

            // Step 2: Now query geofence_places using the elderly_person_id
            String placesQueryUrl = geofencePlacesUrl + "?elderly_person_id=eq." + elderlyPersonId + "&is_active=eq.true";
            System.out.println("Fetching geofence places from: " + placesQueryUrl);

            ResponseEntity<String> response = restTemplate.exchange(placesQueryUrl, HttpMethod.GET, entity, String.class);
            JsonNode jsonArray = objectMapper.readTree(response.getBody());

            List<GeofencePlace> places = new ArrayList<>();
            for (JsonNode placeNode : jsonArray) {
                GeofencePlace place = new GeofencePlace(
                    placeNode.get("id").asText(),
                    placeNode.get("elderly_person_id").asText(),
                    placeNode.get("name").asText(),
                    placeNode.get("place_type").asText(),
                    placeNode.get("latitude").asDouble(),
                    placeNode.get("longitude").asDouble(),
                    placeNode.get("radius_meters").asInt()
                );

                if (placeNode.has("address") && !placeNode.get("address").isNull()) {
                    place.setAddress(placeNode.get("address").asText());
                }
                if (placeNode.has("color") && !placeNode.get("color").isNull()) {
                    place.setColor(placeNode.get("color").asText());
                }

                places.add(place);
            }

            System.out.println("✓ Found " + places.size() + " geofence places for elderly person: " + elderlyPersonId);
            return places;
        } catch (Exception e) {
            System.err.println("Error fetching geofence places from Supabase: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Fetch floor plans for an elderly person
     */
    public List<FloorPlan> getFloorPlansByElderlyPersonId(String elderlyPersonId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = floorPlansUrl + "?elderly_person_id=eq." + elderlyPersonId;
            System.out.println("Fetching floor plans from URL: " + url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            System.out.println("Floor plans response: " + response.getBody());
            JsonNode jsonArray = objectMapper.readTree(response.getBody());

            List<FloorPlan> floorPlans = new ArrayList<>();
            for (JsonNode planNode : jsonArray) {
                FloorPlan plan = new FloorPlan();
                plan.setId(planNode.get("id").asText());
                plan.setElderlyPersonId(planNode.get("elderly_person_id").asText());
                plan.setName(planNode.get("name").asText());
                plan.setWidth(planNode.get("width").asDouble());
                plan.setHeight(planNode.get("height").asDouble());

                if (planNode.has("grid_size") && !planNode.get("grid_size").isNull()) {
                    plan.setGridSize(planNode.get("grid_size").asDouble());
                } else {
                    plan.setGridSize(1.0);
                }

                // Parse zones from JSON
                if (planNode.has("zones") && !planNode.get("zones").isNull()) {
                    JsonNode zonesNode = planNode.get("zones");
                    List<FloorPlan.Zone> zones = new ArrayList<>();

                    if (zonesNode.isArray()) {
                        for (JsonNode zoneNode : zonesNode) {
                            FloorPlan.Zone zone = new FloorPlan.Zone();
                            zone.setName(zoneNode.get("name").asText());

                            if (zoneNode.has("color")) {
                                zone.setColor(zoneNode.get("color").asText());
                            }

                            // Parse coordinates
                            if (zoneNode.has("coordinates") && zoneNode.get("coordinates").isArray()) {
                                List<FloorPlan.Coordinate> coordinates = new ArrayList<>();
                                for (JsonNode coordNode : zoneNode.get("coordinates")) {
                                    FloorPlan.Coordinate coord = new FloorPlan.Coordinate(
                                        coordNode.get("x").asDouble(),
                                        coordNode.get("y").asDouble()
                                    );
                                    coordinates.add(coord);
                                }
                                zone.setCoordinates(coordinates);

                                // Only add zone if it has valid coordinates
                                if (!coordinates.isEmpty()) {
                                    zones.add(zone);
                                } else {
                                    System.err.println("⚠️ WARNING: Skipping zone '" + zone.getName() + "' with empty coordinates");
                                }
                            } else {
                                System.err.println("⚠️ WARNING: Skipping zone '" + zone.getName() + "' with no coordinates field");
                            }
                        }
                    }

                    plan.setZones(zones);
                }

                floorPlans.add(plan);
            }

            System.out.println("Successfully parsed " + floorPlans.size() + " floor plan(s) for elderly person " + elderlyPersonId);
            if (!floorPlans.isEmpty()) {
                floorPlans.forEach(fp -> {
                    int zoneCount = fp.getZones() != null ? fp.getZones().size() : 0;
                    System.out.println("  -> Floor Plan: " + fp.getName() + " (ID: " + fp.getId() + ", " + fp.getWidth() + "m × " + fp.getHeight() + "m, " + zoneCount + " zones)");
                    if (fp.getZones() != null) {
                        fp.getZones().forEach(zone -> {
                            int coordCount = zone.getCoordinates() != null ? zone.getCoordinates().size() : 0;
                            System.out.println("     └─ Zone: " + zone.getName() + " (" + coordCount + " coordinates)");
                        });
                    }
                });
            }
            return floorPlans;

        } catch (Exception e) {
            System.err.println("❌ ERROR fetching floor plans from Supabase for elderly person " + elderlyPersonId);
            System.err.println("   Error message: " + e.getMessage());
            System.err.println("   Error type: " + e.getClass().getName());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Fetch all active device companies
     */
    /**
     * All active disease simulation profiles, ordered for display.
     * Backs the simulator UI's condition picker.
     */
    public List<com.example.iotsimulatorbackend.model.DiseaseProfile> getDiseaseProfiles() {
        try {
            // disease_profiles is RLS-protected with a policy scoped to `authenticated`.
            // PostgREST derives the role from the Authorization bearer token, not from
            // apikey alone, so both headers are required for the service role to apply.
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            headers.set("Authorization", "Bearer " + supabaseApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = diseaseProfilesUrl + "?is_active=eq.true&order=sort_order";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonArray = objectMapper.readTree(response.getBody());

            List<com.example.iotsimulatorbackend.model.DiseaseProfile> profiles = new ArrayList<>();
            for (JsonNode node : jsonArray) {
                profiles.add(toDiseaseProfile(node));
            }
            System.out.println("✓ Found " + profiles.size() + " disease profiles");
            return profiles;
        } catch (Exception e) {
            System.err.println("Error fetching disease profiles from Supabase: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** One profile by code, or null when the code is unknown or the table is missing. */
    public com.example.iotsimulatorbackend.model.DiseaseProfile getDiseaseProfileByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            headers.set("Authorization", "Bearer " + supabaseApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = diseaseProfilesUrl + "?code=eq." + code.trim() + "&limit=1";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonArray = objectMapper.readTree(response.getBody());

            if (!jsonArray.isArray() || jsonArray.size() == 0) {
                System.err.println("No disease profile found for code: " + code);
                return null;
            }
            return toDiseaseProfile(jsonArray.get(0));
        } catch (Exception e) {
            System.err.println("Error fetching disease profile '" + code + "': " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private com.example.iotsimulatorbackend.model.DiseaseProfile toDiseaseProfile(JsonNode node) {
        com.example.iotsimulatorbackend.model.DiseaseProfile p =
                new com.example.iotsimulatorbackend.model.DiseaseProfile();
        p.setCode(node.get("code").asText());
        p.setName(node.get("name").asText());
        p.setCategory(node.has("category") && !node.get("category").isNull() ? node.get("category").asText() : "");
        p.setMinDays(node.has("min_days") ? node.get("min_days").asInt() : 0);
        p.setRecommendedDays(node.has("recommended_days") ? node.get("recommended_days").asInt() : 0);
        p.setConfidence(node.has("confidence") ? node.get("confidence").asInt() : 0);
        p.setDescription(node.has("description") && !node.get("description").isNull()
                ? node.get("description").asText() : "");

        if (node.has("required_signals") && node.get("required_signals").isArray()) {
            List<String> signals = new ArrayList<>();
            for (JsonNode s : node.get("required_signals")) signals.add(s.asText());
            p.setRequiredSignals(signals);
        }
        if (node.has("profile") && !node.get("profile").isNull()) {
            p.setProfile(objectMapper.convertValue(node.get("profile"), Map.class));
        }
        return p;
    }

    public List<DeviceCompany> getAllCompanies() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String companiesQueryUrl = deviceCompaniesUrl + "?is_active=eq.true&order=name";
            System.out.println("Fetching companies from: " + companiesQueryUrl);

            ResponseEntity<String> response = restTemplate.exchange(companiesQueryUrl, HttpMethod.GET, entity, String.class);
            JsonNode jsonArray = objectMapper.readTree(response.getBody());

            List<DeviceCompany> companies = new ArrayList<>();
            for (JsonNode companyNode : jsonArray) {
                DeviceCompany company = new DeviceCompany(
                    companyNode.get("id").asText(),
                    companyNode.has("code") && !companyNode.get("code").isNull() ? companyNode.get("code").asText() : "",
                    companyNode.get("name").asText(),
                    companyNode.has("description") && !companyNode.get("description").isNull() ? companyNode.get("description").asText() : ""
                );
                if (companyNode.has("logo_url") && !companyNode.get("logo_url").isNull()) {
                    company.setLogoUrl(companyNode.get("logo_url").asText());
                }
                if (companyNode.has("website") && !companyNode.get("website").isNull()) {
                    company.setWebsite(companyNode.get("website").asText());
                }
                companies.add(company);
            }

            System.out.println("✓ Found " + companies.size() + " companies");
            return companies;
        } catch (Exception e) {
            System.err.println("Error fetching companies from Supabase: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Fetch device models by company ID
     */
    public List<DeviceModel> getModelsByCompanyId(String companyId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String modelsQueryUrl = deviceModelsUrl + "?company_id=eq." + companyId + "&is_active=eq.true&order=name";
            System.out.println("Fetching models for company " + companyId + " from: " + modelsQueryUrl);

            ResponseEntity<String> response = restTemplate.exchange(modelsQueryUrl, HttpMethod.GET, entity, String.class);
            JsonNode jsonArray = objectMapper.readTree(response.getBody());

            List<DeviceModel> models = new ArrayList<>();
            for (JsonNode modelNode : jsonArray) {
                DeviceModel model = new DeviceModel(
                    modelNode.get("id").asText(),
                    modelNode.has("company_id") && !modelNode.get("company_id").isNull() ? modelNode.get("company_id").asText() : "",
                    modelNode.has("code") && !modelNode.get("code").isNull() ? modelNode.get("code").asText() : "",
                    modelNode.get("name").asText(),
                    modelNode.has("model_number") && !modelNode.get("model_number").isNull() ? modelNode.get("model_number").asText() : ""
                );

                if (modelNode.has("device_type_id") && !modelNode.get("device_type_id").isNull()) {
                    model.setDeviceTypeId(modelNode.get("device_type_id").asText());
                }
                if (modelNode.has("manufacturer") && !modelNode.get("manufacturer").isNull()) {
                    model.setManufacturer(modelNode.get("manufacturer").asText());
                }
                if (modelNode.has("description") && !modelNode.get("description").isNull()) {
                    model.setDescription(modelNode.get("description").asText());
                }
                if (modelNode.has("image_url") && !modelNode.get("image_url").isNull()) {
                    model.setImageUrl(modelNode.get("image_url").asText());
                }
                if (modelNode.has("specifications") && !modelNode.get("specifications").isNull()) {
                    JsonNode specsNode = modelNode.get("specifications");
                    Map<String, Object> specs = objectMapper.convertValue(specsNode, Map.class);
                    model.setSpecifications(specs);
                }

                models.add(model);
            }

            System.out.println("✓ Found " + models.size() + " models for company: " + companyId);
            return models;
        } catch (Exception e) {
            System.err.println("Error fetching models from Supabase: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Get a device model by its ID
     */
    public DeviceModel getModelById(String modelId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String modelQueryUrl = deviceModelsUrl + "?id=eq." + modelId;
            ResponseEntity<String> response = restTemplate.exchange(modelQueryUrl, HttpMethod.GET, entity, String.class);
            JsonNode jsonArray = objectMapper.readTree(response.getBody());

            if (jsonArray.size() == 0) {
                return null;
            }

            JsonNode modelNode = jsonArray.get(0);
            DeviceModel model = new DeviceModel(
                modelNode.get("id").asText(),
                modelNode.has("company_id") && !modelNode.get("company_id").isNull() ? modelNode.get("company_id").asText() : "",
                modelNode.has("code") && !modelNode.get("code").isNull() ? modelNode.get("code").asText() : "",
                modelNode.get("name").asText(),
                modelNode.has("model_number") && !modelNode.get("model_number").isNull() ? modelNode.get("model_number").asText() : ""
            );

            if (modelNode.has("specifications") && !modelNode.get("specifications").isNull()) {
                JsonNode specsNode = modelNode.get("specifications");
                Map<String, Object> specs = objectMapper.convertValue(specsNode, Map.class);
                model.setSpecifications(specs);
            }

            // Parse supported_data_types array
            if (modelNode.has("supported_data_types") && !modelNode.get("supported_data_types").isNull()) {
                JsonNode dataTypesNode = modelNode.get("supported_data_types");
                List<String> supportedDataTypes = objectMapper.convertValue(dataTypesNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                model.setSupportedDataTypes(supportedDataTypes);
            }

            return model;
        } catch (Exception e) {
            System.err.println("Error fetching model by ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get a device company by its ID
     */
    public DeviceCompany getCompanyById(String companyId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String companyQueryUrl = deviceCompaniesUrl + "?id=eq." + companyId;
            ResponseEntity<String> response = restTemplate.exchange(companyQueryUrl, HttpMethod.GET, entity, String.class);
            JsonNode jsonArray = objectMapper.readTree(response.getBody());

            if (jsonArray.size() == 0) {
                return null;
            }

            JsonNode companyNode = jsonArray.get(0);
            DeviceCompany company = new DeviceCompany(
                companyNode.get("id").asText(),
                companyNode.has("code") && !companyNode.get("code").isNull() ? companyNode.get("code").asText() : "",
                companyNode.get("name").asText(),
                companyNode.has("description") && !companyNode.get("description").isNull() ? companyNode.get("description").asText() : ""
            );

            return company;
        } catch (Exception e) {
            System.err.println("Error fetching company by ID: " + e.getMessage());
            return null;
        }
    }
}

package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.Device;
import com.example.iotsimulatorbackend.model.DataTypeInfo;
import com.example.iotsimulatorbackend.model.DataTypeConfig;
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

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimulatorService {
    private Map<String, DataTypeInfo> dataTypeRanges = new HashMap<>();

    @Autowired
    private RestTemplate restTemplate;

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

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        // Initialize data type ranges
        initializeDataTypeRanges();
    }

    private void initializeDataTypeRanges() {
        // Hardcode ranges based on data type definitions
        dataTypeRanges.put("heart_rate", new DataTypeInfo("heart_rate", "range", Map.of("min", 60, "max", 100)));
        dataTypeRanges.put("temperature", new DataTypeInfo("temperature", "range", Map.of("min", 36, "max", 42))); // Body temp
        dataTypeRanges.put("oxygen_saturation", new DataTypeInfo("oxygen_saturation", "range", Map.of("min", 90, "max", 100)));
        dataTypeRanges.put("movement", new DataTypeInfo("movement", "range", Map.of("min", 0, "max", 100)));
        dataTypeRanges.put("duration", new DataTypeInfo("duration", "range", Map.of("min", 0, "max", 1440))); // Minutes in day
        dataTypeRanges.put("bmi", new DataTypeInfo("bmi", "range", Map.of("min", 18.5, "max", 30)));
        dataTypeRanges.put("door_status", new DataTypeInfo("door_status", "enum", Map.of("values", List.of("open", "closed"))));
        dataTypeRanges.put("movement_detected", new DataTypeInfo("movement_detected", "enum", Map.of("values", List.of(true, false))));
        dataTypeRanges.put("presence", new DataTypeInfo("presence", "enum", Map.of("values", List.of(true, false))));
        dataTypeRanges.put("bed_occupancy", new DataTypeInfo("bed_occupancy", "enum", Map.of("values", List.of("occupied", "vacant"))));
        dataTypeRanges.put("seat_occupancy", new DataTypeInfo("seat_occupancy", "enum", Map.of("values", List.of("occupied", "vacant"))));
        dataTypeRanges.put("orientation", new DataTypeInfo("orientation", "range", Map.of("min", 0, "max", 360)));
        dataTypeRanges.put("activity", new DataTypeInfo("activity", "range", Map.of("min", 0, "max", 100)));
        dataTypeRanges.put("blood_pressure", new DataTypeInfo("blood_pressure", "range", Map.of("systolic_min", 90, "systolic_max", 140, "diastolic_min", 60, "diastolic_max", 90)));
    }

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

            List<Device> devices = new ArrayList<>();
            for (JsonNode deviceNode : jsonArray) {
                Device device = new Device(
                    deviceNode.get("id").asText(),
                    deviceNode.get("elderly_person_id").asText(),
                    deviceNode.get("device_name").asText(),
                    deviceNode.get("device_id").asText(),
                    deviceNode.get("api_key").asText()
                );
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
            System.err.println("Error fetching devices from Supabase: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
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
                    String sampleStr = sampleConfig.asText();
                    JsonNode parsedSample = objectMapper.readTree(sampleStr);

                    // Determine if it's range or enum based on sample_data_config content
                    if (parsedSample.has("type")) {
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

    public DataTypeInfo getRangeForDataType(String dataType) {
        return dataTypeRanges.get(dataType);
    }

    public String getUnitForDataType(String dataType) {
        // Hardcoded units
        switch (dataType) {
            case "heart_rate":
                return "bpm";
            case "temperature":
                return "°C";
            case "oxygen_saturation":
                return "%";
            case "movement":
                return "%";
            case "duration":
                return "minutes";
            case "bmi":
                return "kg/m²";
            case "door_status":
            case "movement_detected":
            case "presence":
            case "bed_occupancy":
            case "seat_occupancy":
                return "status";
            case "blood_pressure":
                return "mmHg";
            default:
                return "";
        }
    }
}

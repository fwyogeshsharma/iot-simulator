package com.example.iotsimulatorbackend.model;

import java.util.List;
import java.util.Map;

public class Device {
    private String id;
    private String elderlyPersonId;
    private String deviceName;
    private String deviceId; // e.g., "KT001"
    private String apiKey;
    private String deviceType;
    private String description;
    private String location;
    private String companyId;
    private String modelId;
    private String companyName;
    private String modelName;
    private Map<String, Object> modelSpecifications;
    private List<String> supportedDataTypes;

    // Constructors, getters, setters
    public Device() {}
    public Device(String id, String elderlyPersonId, String deviceName, String deviceId, String apiKey) {
        this.id = id;
        this.elderlyPersonId = elderlyPersonId;
        this.deviceName = deviceName;
        this.deviceId = deviceId;
        this.apiKey = apiKey;
    }

    public Device(String id, String elderlyPersonId, String deviceName, String deviceId, String apiKey, String deviceType, String description) {
        this.id = id;
        this.elderlyPersonId = elderlyPersonId;
        this.deviceName = deviceName;
        this.deviceId = deviceId;
        this.apiKey = apiKey;
        this.deviceType = deviceType;
        this.description = description;
    }

    // Getters and setters...
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getElderlyPersonId() { return elderlyPersonId; }
    public void setElderlyPersonId(String elderlyPersonId) { this.elderlyPersonId = elderlyPersonId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Map<String, Object> getModelSpecifications() { return modelSpecifications; }
    public void setModelSpecifications(Map<String, Object> modelSpecifications) { this.modelSpecifications = modelSpecifications; }
    public List<String> getSupportedDataTypes() { return supportedDataTypes; }
    public void setSupportedDataTypes(List<String> supportedDataTypes) { this.supportedDataTypes = supportedDataTypes; }
}

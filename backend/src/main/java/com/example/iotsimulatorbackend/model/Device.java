package com.example.iotsimulatorbackend.model;

public class Device {
    private String id;
    private String elderlyPersonId;
    private String deviceName;
    private String deviceId; // e.g., "KT001"
    private String apiKey;

    // Constructors, getters, setters
    public Device() {}
    public Device(String id, String elderlyPersonId, String deviceName, String deviceId, String apiKey) {
        this.id = id;
        this.elderlyPersonId = elderlyPersonId;
        this.deviceName = deviceName;
        this.deviceId = deviceId;
        this.apiKey = apiKey;
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
}

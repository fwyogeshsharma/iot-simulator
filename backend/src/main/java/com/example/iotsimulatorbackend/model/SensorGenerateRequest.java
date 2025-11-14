package com.example.iotsimulatorbackend.model;

public class SensorGenerateRequest {
    private String deviceId;
    private String dataType;
    private String location;  // Optional: location for the device

    // Constructors
    public SensorGenerateRequest() {}

    public SensorGenerateRequest(String deviceId, String dataType, String location) {
        this.deviceId = deviceId;
        this.dataType = dataType;
        this.location = location;
    }

    // Getters and setters
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}

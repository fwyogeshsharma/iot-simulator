package com.example.iotsimulatorbackend.model;

import java.util.List;

public class SimulationRequest {
    private String elderlyPersonId;      // Required: which elderly person's devices
    private List<String> deviceIds;      // Optional: specific devices. If empty, simulate all

    // Constructors
    public SimulationRequest() {}

    public SimulationRequest(String elderlyPersonId, List<String> deviceIds) {
        this.elderlyPersonId = elderlyPersonId;
        this.deviceIds = deviceIds;
    }

    // Getters and setters
    public String getElderlyPersonId() { return elderlyPersonId; }
    public void setElderlyPersonId(String elderlyPersonId) { this.elderlyPersonId = elderlyPersonId; }

    public List<String> getDeviceIds() { return deviceIds; }
    public void setDeviceIds(List<String> deviceIds) { this.deviceIds = deviceIds; }
}

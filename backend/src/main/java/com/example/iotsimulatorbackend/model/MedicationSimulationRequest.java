package com.example.iotsimulatorbackend.model;

public class MedicationSimulationRequest {
    private String elderlyPersonId;
    private String deviceId; // medication dispenser device id

    public MedicationSimulationRequest() {}

    public MedicationSimulationRequest(String elderlyPersonId, String deviceId) {
        this.elderlyPersonId = elderlyPersonId;
        this.deviceId = deviceId;
    }

    public String getElderlyPersonId() {
        return elderlyPersonId;
    }

    public void setElderlyPersonId(String elderlyPersonId) {
        this.elderlyPersonId = elderlyPersonId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}

package com.example.iotsimulatorbackend.model;

import java.util.List;

public class SimulationResponse {
    private String simulationId;        // Unique ID for this simulation session
    private String status;              // "running" or "stopped"
    private String elderlyPersonId;
    private int deviceCount;            // Number of devices being simulated
    private int dataTypeCount;          // Total number of data type configs
    private String message;
    private List<String> deviceIds;     // List of device IDs being simulated

    // Constructors
    public SimulationResponse() {}

    public SimulationResponse(String simulationId, String status, String elderlyPersonId,
                             int deviceCount, int dataTypeCount, String message) {
        this.simulationId = simulationId;
        this.status = status;
        this.elderlyPersonId = elderlyPersonId;
        this.deviceCount = deviceCount;
        this.dataTypeCount = dataTypeCount;
        this.message = message;
    }

    public SimulationResponse(String simulationId, String status, String elderlyPersonId,
                             int deviceCount, int dataTypeCount, String message, List<String> deviceIds) {
        this(simulationId, status, elderlyPersonId, deviceCount, dataTypeCount, message);
        this.deviceIds = deviceIds;
    }

    // Getters and setters
    public String getSimulationId() { return simulationId; }
    public void setSimulationId(String simulationId) { this.simulationId = simulationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getElderlyPersonId() { return elderlyPersonId; }
    public void setElderlyPersonId(String elderlyPersonId) { this.elderlyPersonId = elderlyPersonId; }

    public int getDeviceCount() { return deviceCount; }
    public void setDeviceCount(int deviceCount) { this.deviceCount = deviceCount; }

    public int getDataTypeCount() { return dataTypeCount; }
    public void setDataTypeCount(int dataTypeCount) { this.dataTypeCount = dataTypeCount; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getDeviceIds() { return deviceIds; }
    public void setDeviceIds(List<String> deviceIds) { this.deviceIds = deviceIds; }
}

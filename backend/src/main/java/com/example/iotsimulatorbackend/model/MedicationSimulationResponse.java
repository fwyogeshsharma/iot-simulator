package com.example.iotsimulatorbackend.model;

import java.util.List;
import java.util.Map;

public class MedicationSimulationResponse {
    private boolean success;
    private String message;
    private int totalLogsCreated;
    private int takenCount;
    private int lateCount;
    private int missedCount;
    private List<Map<String, Object>> logsCreated;

    public MedicationSimulationResponse() {}

    public MedicationSimulationResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getTotalLogsCreated() {
        return totalLogsCreated;
    }

    public void setTotalLogsCreated(int totalLogsCreated) {
        this.totalLogsCreated = totalLogsCreated;
    }

    public int getTakenCount() {
        return takenCount;
    }

    public void setTakenCount(int takenCount) {
        this.takenCount = takenCount;
    }

    public int getLateCount() {
        return lateCount;
    }

    public void setLateCount(int lateCount) {
        this.lateCount = lateCount;
    }

    public int getMissedCount() {
        return missedCount;
    }

    public void setMissedCount(int missedCount) {
        this.missedCount = missedCount;
    }

    public List<Map<String, Object>> getLogsCreated() {
        return logsCreated;
    }

    public void setLogsCreated(List<Map<String, Object>> logsCreated) {
        this.logsCreated = logsCreated;
    }
}

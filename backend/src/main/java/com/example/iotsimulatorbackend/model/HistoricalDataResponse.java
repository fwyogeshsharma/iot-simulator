package com.example.iotsimulatorbackend.model;

import java.util.Map;

public class HistoricalDataResponse {
    private String jobId;
    private String status;  // "processing", "completed", "failed"
    private String message;
    private int totalDataPointsGenerated;
    private int daysProcessed;
    private long elapsedMs;
    private Map<String, Integer> deviceDataCounts;  // device_id -> count
    private java.util.List<String> skippedDevices;  // List of skipped device names

    // Constructors
    public HistoricalDataResponse() {
    }

    public HistoricalDataResponse(String jobId, String status, String message) {
        this.jobId = jobId;
        this.status = status;
        this.message = message;
    }

    public HistoricalDataResponse(String jobId, String status, String message,
                                   int totalDataPointsGenerated, int daysProcessed,
                                   Map<String, Integer> deviceDataCounts) {
        this.jobId = jobId;
        this.status = status;
        this.message = message;
        this.totalDataPointsGenerated = totalDataPointsGenerated;
        this.daysProcessed = daysProcessed;
        this.deviceDataCounts = deviceDataCounts;
    }

    // Getters and Setters
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getTotalDataPointsGenerated() {
        return totalDataPointsGenerated;
    }

    public void setTotalDataPointsGenerated(int totalDataPointsGenerated) {
        this.totalDataPointsGenerated = totalDataPointsGenerated;
    }

    public int getDaysProcessed() {
        return daysProcessed;
    }

    public void setDaysProcessed(int daysProcessed) {
        this.daysProcessed = daysProcessed;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public Map<String, Integer> getDeviceDataCounts() {
        return deviceDataCounts;
    }

    public void setDeviceDataCounts(Map<String, Integer> deviceDataCounts) {
        this.deviceDataCounts = deviceDataCounts;
    }

    public java.util.List<String> getSkippedDevices() {
        return skippedDevices;
    }

    public void setSkippedDevices(java.util.List<String> skippedDevices) {
        this.skippedDevices = skippedDevices;
    }

    @Override
    public String toString() {
        return "HistoricalDataResponse{" +
                "jobId='" + jobId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", totalDataPointsGenerated=" + totalDataPointsGenerated +
                ", daysProcessed=" + daysProcessed +
                ", elapsedMs=" + elapsedMs +
                ", deviceDataCounts=" + deviceDataCounts +
                '}';
    }
}

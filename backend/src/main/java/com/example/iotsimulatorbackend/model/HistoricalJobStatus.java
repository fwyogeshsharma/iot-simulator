package com.example.iotsimulatorbackend.model;

public class HistoricalJobStatus {
    private String jobId;
    private String status;  // "processing", "completed", "failed"
    private int progress;   // 0-100%
    private int totalDays;
    private int daysProcessed;
    private int dataPointsGenerated;
    private String currentDate;  // Currently processing date
    private String errorMessage;
    private String completionMessage;  // Success message with details (including skipped devices)
    private long completionTime;  // Timestamp when job completed/failed (for cleanup)

    // Constructors
    public HistoricalJobStatus() {
    }

    public HistoricalJobStatus(String jobId, String status) {
        this.jobId = jobId;
        this.status = status;
        this.progress = 0;
        this.totalDays = 0;
        this.daysProcessed = 0;
        this.dataPointsGenerated = 0;
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

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(int totalDays) {
        this.totalDays = totalDays;
    }

    public int getDaysProcessed() {
        return daysProcessed;
    }

    public void setDaysProcessed(int daysProcessed) {
        this.daysProcessed = daysProcessed;
    }

    public int getDataPointsGenerated() {
        return dataPointsGenerated;
    }

    public void setDataPointsGenerated(int dataPointsGenerated) {
        this.dataPointsGenerated = dataPointsGenerated;
    }

    public String getCurrentDate() {
        return currentDate;
    }

    public void setCurrentDate(String currentDate) {
        this.currentDate = currentDate;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCompletionMessage() {
        return completionMessage;
    }

    public void setCompletionMessage(String completionMessage) {
        this.completionMessage = completionMessage;
    }

    public long getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(long completionTime) {
        this.completionTime = completionTime;
    }

    @Override
    public String toString() {
        return "HistoricalJobStatus{" +
                "jobId='" + jobId + '\'' +
                ", status='" + status + '\'' +
                ", progress=" + progress +
                ", totalDays=" + totalDays +
                ", daysProcessed=" + daysProcessed +
                ", dataPointsGenerated=" + dataPointsGenerated +
                ", currentDate='" + currentDate + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}

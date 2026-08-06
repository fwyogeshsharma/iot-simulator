package com.example.iotsimulatorbackend.model;

import java.util.List;

public class HistoricalDataRequest {
    private String elderlyPersonId;
    private List<String> deviceIds;
    private String startDate;  // ISO format: "2024-06-12"
    private String endDate;    // ISO format: "2024-12-12"
    private boolean includeAnomalies = true;
    private String frequency = "high";  // "low", "medium", "high" - controls data generation frequency

    // Disease-driven generation. When diseaseCode is set, sleep-capable devices are
    // generated from the matching public.disease_profiles row instead of the generic
    // per-data-type path. `days` is a convenience alternative to startDate/endDate:
    // when > 0 the range becomes [today - days, today].
    private String diseaseCode;
    private int days = 0;

    // Constructors
    public HistoricalDataRequest() {
    }

    public HistoricalDataRequest(String elderlyPersonId, List<String> deviceIds,
                                  String startDate, String endDate, boolean includeAnomalies) {
        this.elderlyPersonId = elderlyPersonId;
        this.deviceIds = deviceIds;
        this.startDate = startDate;
        this.endDate = endDate;
        this.includeAnomalies = includeAnomalies;
    }

    // Getters and Setters
    public String getElderlyPersonId() {
        return elderlyPersonId;
    }

    public void setElderlyPersonId(String elderlyPersonId) {
        this.elderlyPersonId = elderlyPersonId;
    }

    public List<String> getDeviceIds() {
        return deviceIds;
    }

    public void setDeviceIds(List<String> deviceIds) {
        this.deviceIds = deviceIds;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public boolean isIncludeAnomalies() {
        return includeAnomalies;
    }

    public void setIncludeAnomalies(boolean includeAnomalies) {
        this.includeAnomalies = includeAnomalies;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public String getDiseaseCode() {
        return diseaseCode;
    }

    public void setDiseaseCode(String diseaseCode) {
        this.diseaseCode = diseaseCode;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    @Override
    public String toString() {
        return "HistoricalDataRequest{" +
                "elderlyPersonId='" + elderlyPersonId + '\'' +
                ", deviceIds=" + deviceIds +
                ", startDate='" + startDate + '\'' +
                ", endDate='" + endDate + '\'' +
                ", includeAnomalies=" + includeAnomalies +
                ", frequency='" + frequency + '\'' +
                '}';
    }
}

package com.example.iotsimulatorbackend.model;

import java.util.Map;

public class DataTypeConfig {
    private String dataType;           // e.g., "heart_rate"
    private String displayName;        // e.g., "Heart Rate"
    private String unit;               // e.g., "bpm"
    private String valueType;          // e.g., "number", "string", "boolean", "object"
    private String configType;         // "range" or "enum"
    private Map<String, Object> config; // Range or enum values from sample_data_config
    private int frequencyPerDay;       // How many times per day to generate data (from device_types table)

    // Constructors
    public DataTypeConfig() {}

    public DataTypeConfig(String dataType, String displayName, String unit, String valueType,
                         String configType, Map<String, Object> config) {
        this.dataType = dataType;
        this.displayName = displayName;
        this.unit = unit;
        this.valueType = valueType;
        this.configType = configType;
        this.config = config;
        this.frequencyPerDay = 4; // Default fallback value
    }

    public DataTypeConfig(String dataType, String displayName, String unit, String valueType,
                         String configType, Map<String, Object> config, int frequencyPerDay) {
        this.dataType = dataType;
        this.displayName = displayName;
        this.unit = unit;
        this.valueType = valueType;
        this.configType = configType;
        this.config = config;
        this.frequencyPerDay = frequencyPerDay;
    }

    // Getters and Setters
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }

    public String getConfigType() { return configType; }
    public void setConfigType(String configType) { this.configType = configType; }

    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }

    public int getFrequencyPerDay() { return frequencyPerDay; }
    public void setFrequencyPerDay(int frequencyPerDay) { this.frequencyPerDay = frequencyPerDay; }
}

package com.example.iotsimulatorbackend.model;

import java.util.List;
import java.util.Map;

public class DataTypeInfo {
    private String dataType;
    private String type; // "range" or "enum"
    private Map<String, Object> config; // e.g., {"min": 60, "max": 100} or {"values": ["open", "closed"]}

    // Constructors, getters, setters
    public DataTypeInfo() {}
    public DataTypeInfo(String dataType, String type, Map<String, Object> config) {
        this.dataType = dataType;
        this.type = type;
        this.config = config;
    }

    // Getters and setters...
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
}

package com.example.iotsimulatorbackend.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LocationData model for GPS coordinates
 */
public class LocationData {
    private double latitude;
    private double longitude;
    private double accuracy;
    private String timestamp;

    // Constructors
    public LocationData() {}

    public LocationData(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = 10.0; // Default accuracy in meters
        this.timestamp = java.time.Instant.now().toString();
    }

    public LocationData(double latitude, double longitude, double accuracy) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.timestamp = java.time.Instant.now().toString();
    }

    // Getters and Setters
    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Convert to Map for JSON serialization
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("accuracy", accuracy);
        return map;
    }
}

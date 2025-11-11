package com.example.iotsimulatorbackend.model;

/**
 * GeofencePlace model representing a geofence area where elderly person visits
 * (e.g., home, hospital, son's house)
 */
public class GeofencePlace {
    private String id;
    private String elderlyPersonId;
    private String name;
    private String placeType; // home, work, hospital, park, relative, other
    private double latitude;
    private double longitude;
    private int radiusMeters;
    private String address;
    private String color;
    private boolean isActive;

    // Constructors
    public GeofencePlace() {}

    public GeofencePlace(String id, String elderlyPersonId, String name, String placeType,
                        double latitude, double longitude, int radiusMeters) {
        this.id = id;
        this.elderlyPersonId = elderlyPersonId;
        this.name = name;
        this.placeType = placeType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
        this.isActive = true;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getElderlyPersonId() {
        return elderlyPersonId;
    }

    public void setElderlyPersonId(String elderlyPersonId) {
        this.elderlyPersonId = elderlyPersonId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlaceType() {
        return placeType;
    }

    public void setPlaceType(String placeType) {
        this.placeType = placeType;
    }

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

    public int getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(int radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    @Override
    public String toString() {
        return "GeofencePlace{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", placeType='" + placeType + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", radiusMeters=" + radiusMeters +
                '}';
    }
}

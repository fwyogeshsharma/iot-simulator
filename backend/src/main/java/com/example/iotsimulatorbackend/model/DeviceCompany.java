package com.example.iotsimulatorbackend.model;

public class DeviceCompany {
    private String id;
    private String code;
    private String name;
    private String description;
    private String logoUrl;
    private String website;
    private boolean isActive;

    public DeviceCompany() {}

    public DeviceCompany(String id, String code, String name, String description) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.isActive = true;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}

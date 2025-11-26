package com.example.iotsimulatorbackend.model;

import java.util.List;
import java.util.Map;

public class DeviceModel {
    private String id;
    private String deviceTypeId;
    private String companyId;
    private String manufacturer;
    private String code;
    private String name;
    private String description;
    private String modelNumber;
    private String imageUrl;
    private Map<String, Object> specifications;
    private List<String> supportedDataTypes;
    private boolean isActive;

    public DeviceModel() {}

    public DeviceModel(String id, String companyId, String code, String name, String modelNumber) {
        this.id = id;
        this.companyId = companyId;
        this.code = code;
        this.name = name;
        this.modelNumber = modelNumber;
        this.isActive = true;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDeviceTypeId() { return deviceTypeId; }
    public void setDeviceTypeId(String deviceTypeId) { this.deviceTypeId = deviceTypeId; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getModelNumber() { return modelNumber; }
    public void setModelNumber(String modelNumber) { this.modelNumber = modelNumber; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Map<String, Object> getSpecifications() { return specifications; }
    public void setSpecifications(Map<String, Object> specifications) { this.specifications = specifications; }

    public List<String> getSupportedDataTypes() { return supportedDataTypes; }
    public void setSupportedDataTypes(List<String> supportedDataTypes) { this.supportedDataTypes = supportedDataTypes; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}

package com.example.iotsimulatorbackend.model;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks statistics for an active simulation
 */
public class SimulationStatistics {
    private final String simulationId;
    private final long startTime;
    private volatile long lastUpdatedTime;

    // Overall counters
    private final AtomicInteger totalDataPointsGenerated = new AtomicInteger(0);
    private final AtomicInteger totalDataPointsSuccessful = new AtomicInteger(0);
    private final AtomicInteger totalDataPointsFailed = new AtomicInteger(0);

    // Per-device tracking
    private final Map<String, DeviceStatistics> deviceStats = new HashMap<>();

    // Per-data-type tracking
    private final Map<String, DataTypeStatistics> dataTypeStats = new HashMap<>();

    public SimulationStatistics(String simulationId) {
        this.simulationId = simulationId;
        this.startTime = System.currentTimeMillis();
        this.lastUpdatedTime = this.startTime;
    }

    /**
     * Record a successful data point generation
     */
    public void recordSuccess(String deviceId, String deviceName, String dataType, String displayName) {
        totalDataPointsGenerated.incrementAndGet();
        totalDataPointsSuccessful.incrementAndGet();
        lastUpdatedTime = System.currentTimeMillis();

        // Update device stats
        deviceStats.computeIfAbsent(deviceId, k -> new DeviceStatistics(deviceId, deviceName))
                   .recordSuccess();

        // Update data type stats
        dataTypeStats.computeIfAbsent(dataType, k -> new DataTypeStatistics(dataType, displayName))
                     .recordSuccess();
    }

    /**
     * Record a failed data point generation
     */
    public void recordFailure(String deviceId, String deviceName, String dataType, String displayName) {
        totalDataPointsGenerated.incrementAndGet();
        totalDataPointsFailed.incrementAndGet();
        lastUpdatedTime = System.currentTimeMillis();

        // Update device stats
        deviceStats.computeIfAbsent(deviceId, k -> new DeviceStatistics(deviceId, deviceName))
                   .recordFailure();

        // Update data type stats
        dataTypeStats.computeIfAbsent(dataType, k -> new DataTypeStatistics(dataType, displayName))
                     .recordFailure();
    }

    // Getters
    public String getSimulationId() { return simulationId; }
    public long getStartTime() { return startTime; }
    public long getLastUpdatedTime() { return lastUpdatedTime; }
    public long getElapsedTimeSeconds() { return (System.currentTimeMillis() - startTime) / 1000; }
    public int getTotalDataPointsGenerated() { return totalDataPointsGenerated.get(); }
    public int getTotalDataPointsSuccessful() { return totalDataPointsSuccessful.get(); }
    public int getTotalDataPointsFailed() { return totalDataPointsFailed.get(); }
    public double getSuccessRate() {
        int total = totalDataPointsGenerated.get();
        return total == 0 ? 0 : (totalDataPointsSuccessful.get() * 100.0) / total;
    }
    public double getDataPointsPerMinute() {
        long elapsedSeconds = getElapsedTimeSeconds();
        if (elapsedSeconds == 0) return 0;
        return (totalDataPointsSuccessful.get() * 60.0) / elapsedSeconds;
    }
    public Map<String, DeviceStatistics> getDeviceStats() { return deviceStats; }
    public Map<String, DataTypeStatistics> getDataTypeStats() { return dataTypeStats; }

    /**
     * Inner class for per-device statistics
     */
    public static class DeviceStatistics {
        private final String deviceId;
        private final String deviceName;
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);

        public DeviceStatistics(String deviceId, String deviceName) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
        }

        public void recordSuccess() { successCount.incrementAndGet(); }
        public void recordFailure() { failureCount.incrementAndGet(); }

        public String getDeviceId() { return deviceId; }
        public String getDeviceName() { return deviceName; }
        public int getSuccessCount() { return successCount.get(); }
        public int getFailureCount() { return failureCount.get(); }
        public int getTotalCount() { return successCount.get() + failureCount.get(); }
    }

    /**
     * Inner class for per-data-type statistics
     */
    public static class DataTypeStatistics {
        private final String dataType;
        private final String displayName;
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);

        public DataTypeStatistics(String dataType, String displayName) {
            this.dataType = dataType;
            this.displayName = displayName;
        }

        public void recordSuccess() { successCount.incrementAndGet(); }
        public void recordFailure() { failureCount.incrementAndGet(); }

        public String getDataType() { return dataType; }
        public String getDisplayName() { return displayName; }
        public int getSuccessCount() { return successCount.get(); }
        public int getFailureCount() { return failureCount.get(); }
        public int getTotalCount() { return successCount.get() + failureCount.get(); }
    }
}

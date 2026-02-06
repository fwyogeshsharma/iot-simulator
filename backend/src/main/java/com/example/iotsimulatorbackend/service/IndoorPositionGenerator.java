package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.FloorPlan;
import com.example.iotsimulatorbackend.model.FloorPlan.Zone;
import com.example.iotsimulatorbackend.model.FloorPlan.Coordinate;

import java.util.*;

/**
 * Generates realistic indoor movement positions within floor plan zones
 * Similar to the main symbIoT app's generateIndoorMovementPath function
 */
public class IndoorPositionGenerator {
    private final Random random = new Random();
    private final FloorPlan floorPlan;

    // Movement parameters
    private double currentX;
    private double currentY;
    private Zone currentZone;
    private double velocityX = 0;
    private double velocityY = 0;
    private final double maxSpeed = 1.5; // meters per second (walking speed)
    private final double momentum = 0.7; // How much previous velocity affects next move

    // Zone transition tracking
    private long currentZoneEntryTime;  // When entered current zone (epoch milliseconds)
    private Long previousZoneExitTime;   // When exited previous zone (null if no previous zone)
    private boolean isFirstPosition = true;  // Track if this is the first position generated

    public IndoorPositionGenerator(FloorPlan floorPlan) {
        this.floorPlan = floorPlan;

        // Check if floor plan has zones, if not create a default zone covering entire floor plan
        if (floorPlan.getZones() == null || floorPlan.getZones().isEmpty()) {
            System.out.println("⚠️  Floor plan '" + floorPlan.getName() + "' has no zones. Creating default zone.");
            Zone defaultZone = new Zone("Default Area", List.of(
                new Coordinate(1, 1),
                new Coordinate(floorPlan.getWidth() - 1, 1),
                new Coordinate(floorPlan.getWidth() - 1, floorPlan.getHeight() - 1),
                new Coordinate(1, floorPlan.getHeight() - 1)
            ), "#3b82f6");
            floorPlan.setZones(List.of(defaultZone));
        }

        // Start in a random zone
        this.currentZone = floorPlan.getZones().get(random.nextInt(floorPlan.getZones().size()));
        Coordinate startPos = getRandomPointInZone(currentZone);
        this.currentX = startPos.getX();
        this.currentY = startPos.getY();

        // Initialize entry time to current time
        this.currentZoneEntryTime = System.currentTimeMillis();
        this.previousZoneExitTime = null;  // No previous zone at start
    }

    /**
     * Generate next position using random walk algorithm
     * Always ensures movement for smooth Movement Playback visualization
     */
    public IndoorPosition generateNextPosition(int intervalSeconds) {
        // Apply momentum and add random direction change
        // Ensure continuous movement (no stationary periods) for smooth playback
        velocityX = velocityX * momentum + (random.nextDouble() - 0.5) * 0.8;
        velocityY = velocityY * momentum + (random.nextDouble() - 0.5) * 0.8;

        // Ensure minimum movement to avoid duplicate consecutive positions
        double currentSpeed = Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        if (currentSpeed < 0.05) {
            // Add small boost if moving too slowly
            double angle = random.nextDouble() * 2 * Math.PI;
            velocityX += Math.cos(angle) * 0.1;
            velocityY += Math.sin(angle) * 0.1;
        }

        // Limit maximum speed
        double speed = Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        if (speed > maxSpeed) {
            velocityX = (velocityX / speed) * maxSpeed;
            velocityY = (velocityY / speed) * maxSpeed;
        }

        // Calculate new position with guaranteed movement
        double newX = currentX + velocityX * intervalSeconds;
        double newY = currentY + velocityY * intervalSeconds;

        // Check boundaries and reverse velocity when hitting walls (bounce effect)
        if (newX < 0.5) {
            newX = 0.5;
            velocityX = -velocityX * 0.8;  // Reverse and dampen
        } else if (newX > floorPlan.getWidth() - 0.5) {
            newX = floorPlan.getWidth() - 0.5;
            velocityX = -velocityX * 0.8;  // Reverse and dampen
        }

        if (newY < 0.5) {
            newY = 0.5;
            velocityY = -velocityY * 0.8;  // Reverse and dampen
        } else if (newY > floorPlan.getHeight() - 0.5) {
            newY = floorPlan.getHeight() - 0.5;
            velocityY = -velocityY * 0.8;  // Reverse and dampen
        }

        // Check if we're in a zone
        Zone newZone = findZoneAtPosition(newX, newY);

        if (newZone != null) {
            // Check if we changed zones
            if (!newZone.getName().equals(currentZone.getName())) {
                // Zone transition occurred
                long currentTime = System.currentTimeMillis();
                // Only set previousZoneExitTime if this is not the first position
                if (!isFirstPosition) {
                    previousZoneExitTime = currentTime;  // Exit previous zone
                }
                currentZoneEntryTime = currentTime;   // Enter new zone
            }

            // Inside a zone - update position and zone normally
            currentZone = newZone;
            currentX = newX;
            currentY = newY;
        } else {
            // Outside all zones - find nearest zone and move toward its center
            // This ensures positions always stay within zones and prevents wandering outside
            Zone nearestZone = getNearestZone(newX, newY);
            Coordinate zoneCenter = getZoneCenter(nearestZone);

            // Calculate direction toward zone center
            double dx = zoneCenter.getX() - currentX;
            double dy = zoneCenter.getY() - currentY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist > 0) {
                // Move a small step toward zone center
                double step = Math.min(0.5, dist); // Move up to 0.5 meters
                currentX += (dx / dist) * step;
                currentY += (dy / dist) * step;

                // Update velocity to continue moving toward zone
                velocityX = (dx / dist) * 0.3;
                velocityY = (dy / dist) * 0.3;

                // Check if we're now in the zone
                Zone checkZone = findZoneAtPosition(currentX, currentY);
                if (checkZone != null) {
                    // Check if we changed zones
                    if (!checkZone.getName().equals(currentZone.getName())) {
                        // Zone transition occurred
                        long currentTime = System.currentTimeMillis();
                        // Only set previousZoneExitTime if this is not the first position
                        if (!isFirstPosition) {
                            previousZoneExitTime = currentTime;
                        }
                        currentZoneEntryTime = currentTime;
                    }
                    currentZone = checkZone;
                }
            }
        }

        // Calculate actual speed from velocity
        double actualSpeed = Math.sqrt(velocityX * velocityX + velocityY * velocityY);

        // Mark that we've generated the first position
        isFirstPosition = false;

        return new IndoorPosition(
            currentX, currentY, currentZone.getName(),
            0.3 + random.nextDouble() * 0.7,  // accuracy: 0.3-1.0 meters
            actualSpeed,  // Always > 0 now
            currentZoneEntryTime,
            previousZoneExitTime
        );
    }

    /**
     * Get random point within a zone polygon
     */
    private Coordinate getRandomPointInZone(Zone zone) {
        if (zone.getCoordinates() == null || zone.getCoordinates().isEmpty()) {
            System.err.println("⚠️ WARNING: Zone '" + zone.getName() + "' has no coordinates! Using fallback (5, 5).");
            return new Coordinate(5, 5);
        }

        // Get bounding box
        double minX = zone.getCoordinates().stream().mapToDouble(Coordinate::getX).min().orElse(0);
        double maxX = zone.getCoordinates().stream().mapToDouble(Coordinate::getX).max().orElse(10);
        double minY = zone.getCoordinates().stream().mapToDouble(Coordinate::getY).min().orElse(0);
        double maxY = zone.getCoordinates().stream().mapToDouble(Coordinate::getY).max().orElse(10);

        // Try random points until one is inside
        for (int i = 0; i < 100; i++) {
            double x = minX + random.nextDouble() * (maxX - minX);
            double y = minY + random.nextDouble() * (maxY - minY);
            if (isPointInPolygon(x, y, zone.getCoordinates())) {
                return new Coordinate(x, y);
            }
        }

        // Fallback to center of bounding box
        return new Coordinate((minX + maxX) / 2, (minY + maxY) / 2);
    }

    /**
     * Check if point is inside polygon using ray casting algorithm
     */
    private boolean isPointInPolygon(double x, double y, List<Coordinate> polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double xi = polygon.get(i).getX();
            double yi = polygon.get(i).getY();
            double xj = polygon.get(j).getX();
            double yj = polygon.get(j).getY();

            boolean intersect = ((yi > y) != (yj > y))
                && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    /**
     * Find which zone contains a position
     */
    private Zone findZoneAtPosition(double x, double y) {
        for (Zone zone : floorPlan.getZones()) {
            // Skip zones with no coordinates
            if (zone.getCoordinates() == null || zone.getCoordinates().isEmpty()) {
                continue;
            }
            if (isPointInPolygon(x, y, zone.getCoordinates())) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Get nearest zone to a position
     */
    private Zone getNearestZone(double x, double y) {
        double minDist = Double.MAX_VALUE;
        Zone nearest = floorPlan.getZones().get(0);

        for (Zone zone : floorPlan.getZones()) {
            // Skip zones with no coordinates
            if (zone.getCoordinates() == null || zone.getCoordinates().isEmpty()) {
                System.err.println("⚠️ WARNING: Skipping zone '" + zone.getName() + "' with no coordinates in getNearestZone");
                continue;
            }

            Coordinate center = getZoneCenter(zone);
            double dist = Math.sqrt(Math.pow(center.getX() - x, 2) + Math.pow(center.getY() - y, 2));
            if (dist < minDist) {
                minDist = dist;
                nearest = zone;
            }
        }

        return nearest;
    }

    /**
     * Get center of zone
     */
    private Coordinate getZoneCenter(Zone zone) {
        // Handle zones with no coordinates
        if (zone.getCoordinates() == null || zone.getCoordinates().isEmpty()) {
            System.err.println("⚠️ WARNING: Zone '" + zone.getName() + "' has no coordinates! Using floor plan center.");
            return new Coordinate(floorPlan.getWidth() / 2, floorPlan.getHeight() / 2);
        }

        double sumX = 0, sumY = 0;
        for (Coordinate coord : zone.getCoordinates()) {
            sumX += coord.getX();
            sumY += coord.getY();
        }
        return new Coordinate(
            sumX / zone.getCoordinates().size(),
            sumY / zone.getCoordinates().size()
        );
    }

    /**
     * Indoor position result with zone transition timestamps
     */
    public static class IndoorPosition {
        private final double x;
        private final double y;
        private final String zone;
        private final double accuracy;
        private final double speed;
        private final long zoneEntryTime;  // When entered current zone (epoch milliseconds)
        private final Long previousZoneExitTime;  // When exited previous zone (null if no previous zone)

        public IndoorPosition(double x, double y, String zone, double accuracy, double speed,
                            long zoneEntryTime, Long previousZoneExitTime) {
            this.x = x;
            this.y = y;
            this.zone = zone;
            this.accuracy = accuracy;
            this.speed = speed;
            this.zoneEntryTime = zoneEntryTime;
            this.previousZoneExitTime = previousZoneExitTime;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public String getZone() { return zone; }
        public double getAccuracy() { return accuracy; }
        public double getSpeed() { return speed; }
        public long getZoneEntryTime() { return zoneEntryTime; }
        public Long getPreviousZoneExitTime() { return previousZoneExitTime; }
    }
}

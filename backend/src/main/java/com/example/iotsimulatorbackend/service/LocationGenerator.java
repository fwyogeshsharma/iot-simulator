package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.GeofencePlace;
import com.example.iotsimulatorbackend.model.LocationData;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LocationGenerator generates realistic GPS coordinates that simulate movement
 * between geofence places (home, hospital, son's house, etc.)
 */
public class LocationGenerator {
    private static final double EARTH_RADIUS_METERS = 6371e3;
    private static final double MOVEMENT_SPEED_METERS_PER_MINUTE = 250; // Assume 15 km/h walking speed
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private Random random = new Random();

    private GeofencePlace currentPlace;
    private GeofencePlace nextPlace;
    private double currentLat;
    private double currentLon;
    private List<GeofencePlace> places;
    private long lastGenerationTime;
    private long cycleStartTime;
    private int minutesSinceLocationChange = 0;
    private int minutesAtCurrentPlace = 0; // Minutes to stay at current place
    private boolean justArrived = false;

    public LocationGenerator(List<GeofencePlace> places) {
        this.places = new ArrayList<>(places);
        if (!places.isEmpty()) {
            // Start at first place
            this.currentPlace = places.get(0);
            this.currentLat = currentPlace.getLatitude();
            this.currentLon = currentPlace.getLongitude();
            // Stay at starting place for 2-5 minutes
            this.minutesAtCurrentPlace = 2 + random.nextInt(4);
            this.cycleStartTime = System.currentTimeMillis();
            this.justArrived = true;

            // Log initial state
            logCycleStart(currentPlace, minutesAtCurrentPlace);
        }
        this.lastGenerationTime = System.currentTimeMillis();
    }

    /**
     * Generate next GPS coordinate
     * Simulates movement: stay at current place, then move to another place
     */
    public LocationData generateNextLocation() {
        long currentTime = System.currentTimeMillis();
        long timeDiffMillis = currentTime - lastGenerationTime;
        int minutesElapsed = (int) (timeDiffMillis / (60 * 1000)); // Convert to minutes

        if (minutesElapsed == 0) {
            minutesElapsed = 1; // At least 1 minute
        }

        lastGenerationTime = currentTime;
        minutesSinceLocationChange += minutesElapsed;

        // If we've been at current place long enough, move to another place
        if (minutesSinceLocationChange >= minutesAtCurrentPlace && places.size() > 1) {
            // Pick a random different place
            if (nextPlace == null) {
                do {
                    nextPlace = places.get(random.nextInt(places.size()));
                } while (nextPlace.getId().equals(currentPlace.getId()));

                // Log when starting to move
                double distanceToNext = calculateDistance(currentLat, currentLon, nextPlace.getLatitude(), nextPlace.getLongitude());
                int minutesToArrival = (int) Math.ceil(distanceToNext / MOVEMENT_SPEED_METERS_PER_MINUTE);
                logTransitionStart(currentPlace, nextPlace, distanceToNext, minutesToArrival);
            }

            // Simulate movement from current place to next place
            moveTowardPlace(nextPlace, minutesElapsed);

            // If reached destination, update current place
            double distanceToDestination = calculateDistance(currentLat, currentLon,
                    nextPlace.getLatitude(), nextPlace.getLongitude());
            if (distanceToDestination < 50) { // Within 50 meters, consider arrived
                currentPlace = nextPlace;
                currentLat = nextPlace.getLatitude();
                currentLon = nextPlace.getLongitude();
                minutesSinceLocationChange = 0;
                // Stay at new place for 5-20 minutes
                minutesAtCurrentPlace = 2 + random.nextInt(5);
                cycleStartTime = currentTime;
                nextPlace = null;
                justArrived = true;

                // Log arrival
                logCycleStart(currentPlace, minutesAtCurrentPlace);
            }
        } else {
            // Stay at current place, add small random variation within the geofence
            addRandomVariation();
        }

        // Add small accuracy jitter (5-25 meters)
        double accuracy = 5 + random.nextDouble() * 20;

        return new LocationData(currentLat, currentLon, accuracy);
    }

    /**
     * Move location toward a destination place
     */
    private void moveTowardPlace(GeofencePlace destination, int minutesElapsed) {
        double destLat = destination.getLatitude();
        double destLon = destination.getLongitude();

        // Calculate distance to destination
        double distance = calculateDistance(currentLat, currentLon, destLat, destLon);

        // Calculate how far we can move in the elapsed minutes
        double maxMovementMeters = MOVEMENT_SPEED_METERS_PER_MINUTE * minutesElapsed;

        if (distance > maxMovementMeters) {
            // Move toward destination by the max movement amount
            double bearing = calculateBearing(currentLat, currentLon, destLat, destLon);
            double[] newCoords = moveByBearing(currentLat, currentLon, bearing, maxMovementMeters);
            currentLat = newCoords[0];
            currentLon = newCoords[1];
        } else {
            // We've reached the destination
            currentLat = destLat;
            currentLon = destLon;
        }
    }

    /**
     * Add random variation within geofence radius
     */
    private void addRandomVariation() {
        if (currentPlace == null) return;

        // Add small random movement within geofence (within 30% of radius)
        int maxVariationMeters = (int) (currentPlace.getRadiusMeters() * 0.3);
        if (maxVariationMeters < 10) maxVariationMeters = 10;

        double randomDistance = random.nextDouble() * maxVariationMeters;
        double randomBearing = random.nextDouble() * 360;

        double[] newCoords = moveByBearing(currentLat, currentLon, randomBearing, randomDistance);
        currentLat = newCoords[0];
        currentLon = newCoords[1];
    }

    /**
     * Calculate distance between two GPS coordinates in meters (Haversine formula)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double φ1 = Math.toRadians(lat1);
        double φ2 = Math.toRadians(lat2);
        double Δφ = Math.toRadians(lat2 - lat1);
        double Δλ = Math.toRadians(lon2 - lon1);

        double a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
                Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Calculate bearing between two GPS coordinates in degrees (0-360)
     */
    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double φ1 = Math.toRadians(lat1);
        double φ2 = Math.toRadians(lat2);
        double Δλ = Math.toRadians(lon2 - lon1);

        double y = Math.sin(Δλ) * Math.cos(φ2);
        double x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);
        double bearing = Math.toDegrees(Math.atan2(y, x));

        return (bearing + 360) % 360;
    }

    /**
     * Move from a point by bearing and distance
     * Returns new [latitude, longitude]
     */
    private double[] moveByBearing(double lat, double lon, double bearing, double meters) {
        double φ1 = Math.toRadians(lat);
        double λ1 = Math.toRadians(lon);
        double θ = Math.toRadians(bearing);
        double δ = meters / EARTH_RADIUS_METERS;

        double φ2 = Math.asin(Math.sin(φ1) * Math.cos(δ) +
                Math.cos(φ1) * Math.sin(δ) * Math.cos(θ));
        double λ2 = λ1 + Math.atan2(Math.sin(θ) * Math.sin(δ) * Math.cos(φ1),
                Math.cos(δ) - Math.sin(φ1) * Math.sin(φ2));

        return new double[]{Math.toDegrees(φ2), Math.toDegrees(λ2)};
    }

    /**
     * Log when arriving at a new location (entry event happened)
     * Shows actual timestamp and exit time range
     */
    private void logCycleStart(GeofencePlace place, int dwellMinutes) {
        // Entry timestamp (when person arrived)
        String entryTimeStr = formatTime(cycleStartTime);

        // Exit range: dwell time ± GPS interval (5 minutes)
        long exitTimeMin = cycleStartTime + (dwellMinutes * 60 * 1000);
        long exitTimeMax = exitTimeMin + (5 * 60 * 1000); // +5 min uncertainty

        String exitTimeMinStr = formatTime(exitTimeMin);
        String exitTimeMaxStr = formatTime(exitTimeMax);

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ ✓ GEOFENCE EVENT: ENTRY DETECTED                               ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Location: " + String.format("%-55s║", place.getName()));
        System.out.println("║ Coordinates: " + String.format("%.4f, %.4f", place.getLatitude(), place.getLongitude()));
        System.out.println("║ Dwell time: " + String.format("%-51d minutes║", dwellMinutes));
        System.out.println("║                                                                ║");
        System.out.println("║ 📌 Entry event: " + String.format("%-48s║", entryTimeStr + " ✓"));
        System.out.println("║ 🚪 Exit event range: " + String.format("%-40s║", exitTimeMinStr + " - " + exitTimeMaxStr));
        System.out.println("║    (±5 min uncertainty due to 5-min GPS sampling)              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * Log when starting transition to next location
     * Shows actual exit time and arrival range with ±5 min uncertainty due to GPS sampling
     */
    private void logTransitionStart(GeofencePlace fromPlace, GeofencePlace toPlace, double distanceMeters, int minutesToArrival) {
        // Exit time (now)
        String exitTimeStr = formatTime(System.currentTimeMillis());

        // Arrival time range: travel time ± GPS interval
        long arrivalTimeMin = System.currentTimeMillis() + (minutesToArrival * 60 * 1000);
        long arrivalTimeMax = arrivalTimeMin + (5 * 60 * 1000); // +5 min uncertainty

        String arrivalTimeMinStr = formatTime(arrivalTimeMin);
        String arrivalTimeMaxStr = formatTime(arrivalTimeMax);

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ 🚶 GEOFENCE TRANSITION: IN PROGRESS                            ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ From: " + String.format("%-57s║", fromPlace.getName()));
        System.out.println("║ To:   " + String.format("%-57s║", toPlace.getName()));
        System.out.println("║ Distance: " + String.format("%-55.0f m║", distanceMeters));
        System.out.println("║ Travel time: " + String.format("%-51d minutes║", minutesToArrival));
        System.out.println("║                                                                ║");
        System.out.println("║ 📌 Exit from " + String.format("%-45s║", fromPlace.getName() + ": " + exitTimeStr + " ✓"));
        System.out.println("║ ✈️  Entry to " + String.format("%-51s║", toPlace.getName() + ": " + arrivalTimeMinStr));
        System.out.println("║              " + String.format("%-51s║", "to " + arrivalTimeMaxStr));
        System.out.println("║              (±5 min uncertainty due to GPS sampling)           ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * Format timestamp to HH:mm:ss
     */
    private String formatTime(long timeMillis) {
        LocalDateTime dateTime = LocalDateTime.now().plusSeconds((timeMillis - System.currentTimeMillis()) / 1000);
        return dateTime.format(timeFormatter);
    }

    public GeofencePlace getCurrentPlace() {
        return currentPlace;
    }

    public double getCurrentLat() {
        return currentLat;
    }

    public double getCurrentLon() {
        return currentLon;
    }
}

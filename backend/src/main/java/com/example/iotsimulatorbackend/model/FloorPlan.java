package com.example.iotsimulatorbackend.model;

import java.util.List;

public class FloorPlan {
    private String id;
    private String elderlyPersonId;
    private String name;
    private double width;   // in meters
    private double height;  // in meters
    private double gridSize;
    private List<Zone> zones;

    public static class Zone {
        private String name;
        private List<Coordinate> coordinates;
        private String color;

        public Zone() {}

        public Zone(String name, List<Coordinate> coordinates, String color) {
            this.name = name;
            this.coordinates = coordinates;
            this.color = color;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<Coordinate> getCoordinates() { return coordinates; }
        public void setCoordinates(List<Coordinate> coordinates) { this.coordinates = coordinates; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
    }

    public static class Coordinate {
        private double x;
        private double y;

        public Coordinate() {}

        public Coordinate(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
    }

    public FloorPlan() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getElderlyPersonId() { return elderlyPersonId; }
    public void setElderlyPersonId(String elderlyPersonId) { this.elderlyPersonId = elderlyPersonId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public double getGridSize() { return gridSize; }
    public void setGridSize(double gridSize) { this.gridSize = gridSize; }
    public List<Zone> getZones() { return zones; }
    public void setZones(List<Zone> zones) { this.zones = zones; }

    /**
     * Get default floor plan for Main House
     */
    public static FloorPlan getDefaultFloorPlan(String elderlyPersonId) {
        FloorPlan floorPlan = new FloorPlan();
        floorPlan.setId("default-floor-plan");
        floorPlan.setElderlyPersonId(elderlyPersonId);
        floorPlan.setName("Main House Floor Plan");
        floorPlan.setWidth(15.0);
        floorPlan.setHeight(12.0);
        floorPlan.setGridSize(1.0);

        List<Zone> zones = List.of(
            new Zone("Living Room", List.of(
                new Coordinate(1, 1),
                new Coordinate(7, 1),
                new Coordinate(7, 6),
                new Coordinate(1, 6)
            ), "#3b82f6"),
            new Zone("Kitchen", List.of(
                new Coordinate(7, 1),
                new Coordinate(14, 1),
                new Coordinate(14, 5),
                new Coordinate(7, 5)
            ), "#10b981"),
            new Zone("Bedroom", List.of(
                new Coordinate(1, 6),
                new Coordinate(6, 6),
                new Coordinate(6, 11),
                new Coordinate(1, 11)
            ), "#8b5cf6"),
            new Zone("Bathroom", List.of(
                new Coordinate(6, 6),
                new Coordinate(9, 6),
                new Coordinate(9, 9),
                new Coordinate(6, 9)
            ), "#06b6d4"),
            new Zone("Hallway", List.of(
                new Coordinate(9, 5),
                new Coordinate(14, 5),
                new Coordinate(14, 11),
                new Coordinate(9, 11)
            ), "#64748b")
        );

        floorPlan.setZones(zones);
        return floorPlan;
    }
}

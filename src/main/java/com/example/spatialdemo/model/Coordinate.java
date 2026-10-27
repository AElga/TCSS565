package com.example.spatialdemo.model;

public record Coordinate(double x, double y) {
    public double distanceTo(Coordinate other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
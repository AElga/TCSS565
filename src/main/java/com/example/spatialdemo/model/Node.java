package com.example.spatialdemo.model;

public record Node(String id, double x, double y) {
    public double distanceTo(Node other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
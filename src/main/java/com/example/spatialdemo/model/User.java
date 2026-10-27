package com.example.spatialdemo.model;

public record User(
        String id,
        String name,
        Node originNode,
        Node destinationNode,
        double departureTime,
        double arrivalDeadline
) {
    public Coordinate origin() {
        return new Coordinate(originNode.x(), originNode.y());
    }

    public Coordinate destination() {
        return new Coordinate(destinationNode.x(), destinationNode.y());
    }
}
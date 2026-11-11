package com.example.spatialdemo.model;

import java.util.List;

public record User(
        String id,
        String name,
        Node originNode,
        double departureTime,
        List<Stop> itinerary,
        boolean isDriver,
        int vehicleCapacity
) {
    public Coordinate origin() {
        return new Coordinate(originNode.x(), originNode.y());
    }

    public Stop currentStop() {
        return itinerary.isEmpty() ? null : itinerary.get(0);
    }

    public Node currentDestination() {
        Stop stop = currentStop();
        return stop != null ? stop.node() : originNode;
    }

    public Coordinate currentDestinationCoordinate() {
        Node dest = currentDestination();
        return new Coordinate(dest.x(), dest.y());
    }
}
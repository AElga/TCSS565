package com.example.spatialdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulation")
public record SimulationProperties(
        int userCount,
        int nodeCount,
        double mapSize,
        double spatialThreshold,
        double timeThreshold,
        double rtreeBuffer,
        double maxDepartureTime,
        double minDeadlineOffset,
        double maxDeadlineOffset,
        double driverRatio,
        int vehicleCapacity,
        double simulationHorizon,
        double cycleDuration,
        int maxUserStops,
        long randomSeed
) {
    public SimulationProperties {
        if (nodeCount < 2) {
            throw new IllegalArgumentException("Node count must be at least 2");
        }
        if (vehicleCapacity < 1) {
            throw new IllegalArgumentException("Vehicle capacity must be at least 1");
        }
        if (driverRatio < 0.0 || driverRatio > 1.0) {
            throw new IllegalArgumentException("Driver ratio must be between 0.0 and 1.0");
        }
    }
}
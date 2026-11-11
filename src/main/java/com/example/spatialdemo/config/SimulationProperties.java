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
        int minVehicleCapacity,
        int maxVehicleCapacity,
        double simulationHorizon,
        double cycleDuration,
        int maxUserStops,
        long randomSeed
) {
    public SimulationProperties {
        if (minVehicleCapacity < 0 || maxVehicleCapacity < minVehicleCapacity) {
            throw new IllegalArgumentException("Vehicle capacity range must be non-negative and ordered");
        }
        if (driverRatio < 0.0 || driverRatio > 1.0) {
            throw new IllegalArgumentException("Driver ratio must be between 0.0 and 1.0");
        }
    }
}
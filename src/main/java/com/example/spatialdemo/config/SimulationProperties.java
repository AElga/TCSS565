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
        // Inclusive bounds for generated participant vehicle capacities.
        int minVehicleCapacity,
        int maxVehicleCapacity,
        long randomSeed
) {
    public SimulationProperties {
        if (minVehicleCapacity < 0 || maxVehicleCapacity < minVehicleCapacity) {
            throw new IllegalArgumentException("Vehicle capacity range must be non-negative and ordered");
        }
    }
}
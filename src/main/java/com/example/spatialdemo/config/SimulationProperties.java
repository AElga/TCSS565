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
        long randomSeed
) {}
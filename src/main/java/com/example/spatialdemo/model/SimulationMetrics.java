package com.example.spatialdemo.model;

import java.util.List;

public record SimulationMetrics(
        String algorithmName,
        int totalUsers,
        int totalDrivers,
        int totalPassengers,
        int matchedCount,
        int unmatchedCount,
        double matchPercentage,
        double averageMatchScore,
        long executionTimeMs,
        List<MatchResult> matches
) {}
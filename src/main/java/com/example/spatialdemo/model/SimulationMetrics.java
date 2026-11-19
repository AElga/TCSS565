package com.example.spatialdemo.model;

import java.util.List;

public record SimulationMetrics(
        String algorithmName,
        int totalUsers,
        int totalDrivers,
        int totalPassengers,
        int matchedCount,
        int unmatchedCount,
        int strandedCount,
        double matchPercentage,
        double averageUserPreferenceScore,
        double averageCompletedPreferenceScore,
        double averageCarpoolPreferenceScore,
        int totalVehiclesUsed,
        double averageVehicleOccupancy,
        double resourceSavingsPercentage,
        int independentCompletionCount,
        int carpoolCompletionCount,
        int totalLegsAttempted,
        int totalLegsCompleted,
        double legCompletionPercentage,
        double averageDetourDistance,
        long executionTimeMs,
        List<MatchResult> matches
) {}
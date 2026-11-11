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
        double averageSimulationScore,
        double averageMatchedScoreNoFails,
        int independentCompletionCount,
        int carpoolCompletionCount,
        double averageIndependentScore,
        double averageCarpoolScore,
        int totalLegsAttempted,
        int totalLegsCompleted,
        double legCompletionPercentage,
        double averageDetourDistance,
        long executionTimeMs,
        List<MatchResult> matches
) {}
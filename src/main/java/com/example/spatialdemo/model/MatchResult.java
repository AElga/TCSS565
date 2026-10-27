package com.example.spatialdemo.model;

public record MatchResult(
        String driverId,
        String passengerId,
        double matchScore,
        double detourDistance,
        String algorithmName
) {}
package com.example.spatialdemo.strategy;

import com.example.spatialdemo.model.MatchResult;
import com.example.spatialdemo.model.User;

import java.util.ArrayList;
import java.util.List;

public record BaselineMatchmakingStrategy(double spatialThreshold,
                                          double timeThreshold) implements MatchmakingStrategy {

    @Override
    public String getAlgorithmName() {
        return "Exhaustive Driver-Passenger Scan";
    }

    @Override
    public List<MatchResult> findMatches(List<User> potentialRiders) {
        List<MatchResult> results = new ArrayList<>();
        List<String> matchedPassengerIds = new ArrayList<>();

        List<User> drivers = potentialRiders.stream().filter(u -> u.isDriver() && u.vehicleCapacity() > 0).toList();

        for (User driver : drivers) {
            if (matchedPassengerIds.contains(driver.id())) continue;

            int currentCapacity = driver.vehicleCapacity();

            for (User rider : potentialRiders) {
                if (rider.id().equals(driver.id())) continue; // Can't ride with yourself
                if (matchedPassengerIds.contains(rider.id())) continue;
                if (currentCapacity <= 0) break;

                double destDist = driver.currentDestinationCoordinate().distanceTo(rider.currentDestinationCoordinate());
                double origDist = driver.origin().distanceTo(rider.origin());
                double timeDiff = Math.abs(driver.departureTime() - rider.departureTime());

                if (destDist <= spatialThreshold && origDist <= spatialThreshold && timeDiff <= timeThreshold) {
                    double score = 1.0 / (1.0 + origDist + destDist + timeDiff);
                    results.add(new MatchResult(driver.id(), rider.id(), score, origDist, getAlgorithmName()));
                    matchedPassengerIds.add(rider.id());
                    currentCapacity--;
                }
            }
        }
        return results;
    }
}
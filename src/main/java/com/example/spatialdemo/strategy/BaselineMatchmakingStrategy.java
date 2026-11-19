package com.example.spatialdemo.strategy;

import com.example.spatialdemo.model.MatchResult;
import com.example.spatialdemo.model.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record BaselineMatchmakingStrategy(double spatialThreshold,
                                          double timeThreshold,
                                          int vehicleCapacity) implements MatchmakingStrategy {

    @Override
    public String getAlgorithmName() {
        return "Exhaustive Driver-Passenger Scan";
    }

    @Override
    public List<MatchResult> findMatches(List<User> potentialRiders) {
        List<MatchResult> results = new ArrayList<>();
        int maxPassengerSeats = Math.max(0, vehicleCapacity - 1);
        if (maxPassengerSeats == 0) {
            return results;
        }

        Set<String> matchedIds = new HashSet<>();
        List<User> drivers = potentialRiders.stream().filter(User::isDriver).toList();

        for (User driver : drivers) {
            if (matchedIds.contains(driver.id())) continue;

            int currentCapacity = maxPassengerSeats;
            boolean driverMatched = false;

            for (User rider : potentialRiders) {
                if (rider.id().equals(driver.id())) continue;
                if (matchedIds.contains(rider.id())) continue;
                if (currentCapacity <= 0) break;

                double destDist = driver.currentDestinationCoordinate().distanceTo(rider.currentDestinationCoordinate());
                double origDist = driver.origin().distanceTo(rider.origin());
                double timeDiff = Math.abs(driver.departureTime() - rider.departureTime());

                if (destDist <= spatialThreshold && origDist <= spatialThreshold && timeDiff <= timeThreshold) {
                    double score = 1.0 / (1.0 + origDist + destDist + timeDiff);
                    results.add(new MatchResult(driver.id(), rider.id(), score, origDist, getAlgorithmName()));
                    matchedIds.add(rider.id());
                    currentCapacity--;
                    driverMatched = true;
                }
            }

            if (driverMatched) {
                matchedIds.add(driver.id());
            }
        }
        return results;
    }
}
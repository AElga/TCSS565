package com.example.spatialdemo.strategy;

import com.example.spatialdemo.model.MatchResult;
import com.example.spatialdemo.model.User;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Rectangle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record RTreeSimilarityMatchmakingStrategy(double spatialThreshold,
                                                 double timeThreshold,
                                                 double rtreeBuffer,
                                                 int vehicleCapacity) implements MatchmakingStrategy {

    @Override
    public String getAlgorithmName() {
        return "R-Tree Peer Index";
    }

    @Override
    public List<MatchResult> findMatches(List<User> users) {
        List<MatchResult> results = new ArrayList<>();
        int maxPassengerSeats = Math.max(0, vehicleCapacity - 1);
        if (maxPassengerSeats == 0) {
            return results;
        }

        Set<String> matchedIds = new HashSet<>();
        List<User> drivers = users.stream().filter(User::isDriver).toList();

        RTree<User, Rectangle> tree = RTree.create();
        for (User rider : users) {
            double curDestX = rider.currentDestination().x();
            double curDestY = rider.currentDestination().y();
            double minX = Math.min(rider.origin().x(), curDestX);
            double minY = Math.min(rider.origin().y(), curDestY);
            double maxX = Math.max(rider.origin().x(), curDestX);
            double maxY = Math.max(rider.origin().y(), curDestY);
            Rectangle rect = Geometries.rectangle(minX - rtreeBuffer, minY - rtreeBuffer, maxX + rtreeBuffer, maxY + rtreeBuffer);
            tree = tree.add(rider, rect);
        }

        for (User driver : drivers) {
            if (matchedIds.contains(driver.id())) continue;

            Rectangle searchRect = Geometries.rectangle(
                    driver.origin().x() - spatialThreshold,
                    driver.origin().y() - spatialThreshold,
                    driver.origin().x() + spatialThreshold,
                    driver.origin().y() + spatialThreshold
            );

            Iterable<Entry<User, Rectangle>> candidateEntries = tree.search(searchRect).toBlocking().toIterable();

            record RiderCandidate(User rider, double totalDist, double timeDiff, double score, double origDist) {}
            List<RiderCandidate> validCandidates = new ArrayList<>();

            for (Entry<User, Rectangle> entry : candidateEntries) {
                User candidate = entry.value();
                if (candidate.id().equals(driver.id())) continue;
                if (matchedIds.contains(candidate.id())) continue;

                double destDist = driver.currentDestinationCoordinate().distanceTo(candidate.currentDestinationCoordinate());
                double origDist = driver.origin().distanceTo(candidate.origin());
                double timeDiff = Math.abs(driver.departureTime() - candidate.departureTime());

                if (destDist <= spatialThreshold && origDist <= spatialThreshold && timeDiff <= timeThreshold) {
                    double totalDist = origDist + destDist;
                    double score = 1.0 / (1.0 + totalDist + timeDiff);
                    validCandidates.add(new RiderCandidate(candidate, totalDist, timeDiff, score, origDist));
                }
            }

            // Pick the closest spatial match (tie-breaking by smallest time difference)
            validCandidates.sort(
                    Comparator.comparingDouble(RiderCandidate::totalDist)
                            .thenComparingDouble(RiderCandidate::timeDiff)
            );

            int seatsFilled = 0;
            for (RiderCandidate rc : validCandidates) {
                if (seatsFilled >= maxPassengerSeats) break;

                results.add(new MatchResult(driver.id(), rc.rider.id(), rc.score, rc.origDist, getAlgorithmName()));
                matchedIds.add(rc.rider.id());
                seatsFilled++;
            }

            if (seatsFilled > 0) {
                matchedIds.add(driver.id());
            }
        }
        return results;
    }
}
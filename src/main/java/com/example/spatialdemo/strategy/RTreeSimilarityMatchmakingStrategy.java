package com.example.spatialdemo.strategy;

import com.example.spatialdemo.model.MatchResult;
import com.example.spatialdemo.model.User;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Rectangle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record RTreeSimilarityMatchmakingStrategy(double spatialThreshold, double timeThreshold,
                                                 double rtreeBuffer) implements MatchmakingStrategy {

    @Override
    public String getAlgorithmName() {
        return "R-Tree Peer Index";
    }

    @Override
    public List<MatchResult> findMatches(List<User> users) {
        List<MatchResult> results = new ArrayList<>();
        List<String> matchedPassengerIds = new ArrayList<>();

        List<User> drivers = users.stream().filter(u -> u.isDriver() && u.vehicleCapacity() > 0).toList();

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
            if (matchedPassengerIds.contains(driver.id())) continue;

            Rectangle searchRect = Geometries.rectangle(
                    driver.origin().x() - spatialThreshold,
                    driver.origin().y() - spatialThreshold,
                    driver.origin().x() + spatialThreshold,
                    driver.origin().y() + spatialThreshold
            );

            Iterable<Entry<User, Rectangle>> candidateEntries = tree.search(searchRect).toBlocking().toIterable();

            record RiderCandidate(User rider, double score, double origDist) {}
            List<RiderCandidate> validCandidates = new ArrayList<>();

            for (Entry<User, Rectangle> entry : candidateEntries) {
                User candidate = entry.value();
                if (candidate.id().equals(driver.id())) continue;
                if (matchedPassengerIds.contains(candidate.id())) continue;

                double destDist = driver.currentDestinationCoordinate().distanceTo(candidate.currentDestinationCoordinate());
                double origDist = driver.origin().distanceTo(candidate.origin());
                double timeDiff = Math.abs(driver.departureTime() - candidate.departureTime());

                if (destDist <= spatialThreshold && origDist <= spatialThreshold && timeDiff <= timeThreshold) {
                    double score = 1.0 / (1.0 + origDist + destDist + timeDiff);
                    validCandidates.add(new RiderCandidate(candidate, score, origDist));
                }
            }

            validCandidates.sort(Comparator.comparingDouble(RiderCandidate::score).reversed());

            int seatsFilled = 0;
            int capacity = driver.vehicleCapacity();
            for (RiderCandidate rc : validCandidates) {
                if (seatsFilled >= capacity) break;

                results.add(new MatchResult(driver.id(), rc.rider.id(), rc.score, rc.origDist, getAlgorithmName()));
                matchedPassengerIds.add(rc.rider.id());
                seatsFilled++;
            }
        }
        return results;
    }
}
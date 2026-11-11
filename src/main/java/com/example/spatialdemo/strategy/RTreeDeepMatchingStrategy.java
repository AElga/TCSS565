package com.example.spatialdemo.strategy;

import com.example.spatialdemo.model.MatchResult;
import com.example.spatialdemo.model.Stop;
import com.example.spatialdemo.model.User;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Rectangle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record RTreeDeepMatchingStrategy(double spatialThreshold, double timeThreshold,
                                        double rtreeBuffer) implements MatchmakingStrategy {

    @Override
    public String getAlgorithmName() {
        return "R-Tree Multi-Leg Trajectory Index (Optimized)";
    }

    @Override
    public List<MatchResult> findMatches(List<User> users) {
        List<MatchResult> results = new ArrayList<>();
        List<String> matchedPassengerIds = new ArrayList<>();

        List<User> drivers = users.stream().filter(u -> u.isDriver() && u.vehicleCapacity() > 0).toList();

        RTree<User, Rectangle> tree = RTree.create();
        for (User rider : users) {
            double minX = rider.originNode().x();
            double minY = rider.originNode().y();
            double maxX = minX;
            double maxY = minY;

            for (Stop stop : rider.itinerary()) {
                minX = Math.min(minX, stop.node().x());
                minY = Math.min(minY, stop.node().y());
                maxX = Math.max(maxX, stop.node().x());
                maxY = Math.max(maxY, stop.node().y());
            }

            Rectangle rect = Geometries.rectangle(minX - rtreeBuffer, minY - rtreeBuffer, maxX + rtreeBuffer, maxY + rtreeBuffer);
            tree = tree.add(rider, rect);
        }

        for (User driver : drivers) {
            if (matchedPassengerIds.contains(driver.id())) continue;

            double minX = driver.originNode().x();
            double minY = driver.originNode().y();
            double maxX = minX;
            double maxY = minY;

            for (Stop stop : driver.itinerary()) {
                minX = Math.min(minX, stop.node().x());
                minY = Math.min(minY, stop.node().y());
                maxX = Math.max(maxX, stop.node().x());
                maxY = Math.max(maxY, stop.node().y());
            }

            Rectangle searchRect = Geometries.rectangle(
                    minX - spatialThreshold,
                    minY - spatialThreshold,
                    maxX + spatialThreshold,
                    maxY + spatialThreshold
            );

            Iterable<Entry<User, Rectangle>> candidateEntries = tree.search(searchRect).toBlocking().toIterable();

            record RiderCandidate(User rider, double score, double origDist) {}
            List<RiderCandidate> validCandidates = new ArrayList();

            for (Entry<User, Rectangle> entry : candidateEntries) {
                User candidate = entry.value();
                if (candidate.id().equals(driver.id())) continue;
                if (matchedPassengerIds.contains(candidate.id())) continue;

                double origDist = driver.originNode().distanceTo(candidate.originNode());
                double timeDiff = Math.abs(driver.departureTime() - candidate.departureTime());

                if (origDist > spatialThreshold || timeDiff > timeThreshold) continue;

                int passengerStopsCount = candidate.itinerary().size();
                if (passengerStopsCount == 0) continue;

                double stopDistSum = 0;
                boolean viablePath = true;
                double currentDriverTime = driver.departureTime();
                com.example.spatialdemo.model.Node lastDriverNode = driver.originNode();

                for (int k = 0; k < passengerStopsCount; k++) {
                    if (k >= driver.itinerary().size()) {
                        viablePath = false;
                        break;
                    }

                    Stop dStop = driver.itinerary().get(k);
                    Stop cStop = candidate.itinerary().get(k);

                    double legDist = lastDriverNode.distanceTo(dStop.node());
                    currentDriverTime += legDist * 1.5; // matching SimulationService travel time factor (dist * 1.5)
                    lastDriverNode = dStop.node();

                    double stopDist = dStop.node().distanceTo(cStop.node());
                    if (stopDist > spatialThreshold) {
                        viablePath = false;
                        break;
                    }

                    if (currentDriverTime > cStop.latestDeadline()) {
                        viablePath = false;
                        break;
                    }

                    stopDistSum += stopDist;
                }

                if (!viablePath) continue;

                double avgStopDist = stopDistSum / passengerStopsCount;

                double score = 1.0 / (1.0 + origDist + avgStopDist + timeDiff);
                validCandidates.add(new RiderCandidate(candidate, score, origDist));
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
package com.example.spatialdemo.strategy;

import com.example.spatialdemo.model.MatchResult;
import com.example.spatialdemo.model.Node;
import com.example.spatialdemo.model.Stop;
import com.example.spatialdemo.model.User;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Rectangle;

import java.util.*;

public record RTreeDeepMatchingStrategy(double spatialThreshold,
                                        double timeThreshold,
                                        double rtreeBuffer,
                                        int vehicleCapacity) implements MatchmakingStrategy {

    @Override
    public String getAlgorithmName() {
        return "R-Tree Multi-Leg Trajectory Index (Optimized)";
    }

    private record CandidateEdge(String driverId, String riderId, int sharedLegs, double score, double origDist) {}

    @Override
    public List<MatchResult> findMatches(List<User> users) {
        List<MatchResult> results = new ArrayList<>();
        int maxPassengerSeats = Math.max(0, vehicleCapacity - 1);
        if (maxPassengerSeats == 0) {
            return results;
        }

        List<User> drivers = users.stream().filter(User::isDriver).toList();
        if (drivers.isEmpty()) {
            return results;
        }

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

            Rectangle rect = Geometries.rectangle(
                    minX - rtreeBuffer, minY - rtreeBuffer,
                    maxX + rtreeBuffer, maxY + rtreeBuffer
            );
            tree = tree.add(rider, rect);
        }

        Map<String, List<CandidateEdge>> passengerPrefsByDriver = new HashMap<>();
        Map<String, List<CandidateEdge>> driverRiderPrefsByDriver = new HashMap<>();

        for (User driver : drivers) {
            if (driver.itinerary().isEmpty()) continue;

            Rectangle searchRect = Geometries.rectangle(
                    driver.originNode().x() - spatialThreshold,
                    driver.originNode().y() - spatialThreshold,
                    driver.originNode().x() + spatialThreshold,
                    driver.originNode().y() + spatialThreshold
            );

            Iterable<Entry<User, Rectangle>> candidateEntries = tree.search(searchRect).toBlocking().toIterable();

            List<CandidateEdge> nonDriverPrefs = new ArrayList<>();
            List<CandidateEdge> driverPrefs = new ArrayList<>();

            for (Entry<User, Rectangle> entry : candidateEntries) {
                User candidate = entry.value();
                if (candidate.id().equals(driver.id())) continue;
                if (candidate.itinerary().isEmpty()) continue;

                double origDist = driver.originNode().distanceTo(candidate.originNode());
                double timeDiff = Math.abs(driver.departureTime() - candidate.departureTime());

                if (origDist > spatialThreshold || timeDiff > timeThreshold) continue;

                int maxLookahead = Math.min(driver.itinerary().size(), candidate.itinerary().size());
                int sharedLegs = 0;
                double stopDistSum = 0;
                double currentDriverTime = driver.departureTime();
                Node lastDriverNode = driver.originNode();

                for (int k = 0; k < maxLookahead; k++) {
                    Stop dStop = driver.itinerary().get(k);
                    Stop cStop = candidate.itinerary().get(k);

                    double legDist = lastDriverNode.distanceTo(dStop.node());
                    currentDriverTime += legDist * 1.2;
                    lastDriverNode = dStop.node();

                    double stopDist = dStop.node().distanceTo(cStop.node());
                    if (stopDist > spatialThreshold || currentDriverTime > cStop.latestDeadline()) {
                        break;
                    }

                    stopDistSum += stopDist;
                    sharedLegs++;
                }

                if (sharedLegs == 0) continue;

                double avgStopDist = stopDistSum / sharedLegs;
                double score = 1.0 / (1.0 + origDist + avgStopDist + timeDiff);
                CandidateEdge edge = new CandidateEdge(driver.id(), candidate.id(), sharedLegs, score, origDist);

                if (!candidate.isDriver()) {
                    nonDriverPrefs.add(edge);
                } else {
                    driverPrefs.add(edge);
                }
            }

            Comparator<CandidateEdge> prefOrder = Comparator
                    .comparingDouble(CandidateEdge::score).reversed()
                    .thenComparing(Comparator.comparingInt(CandidateEdge::sharedLegs).reversed());

            nonDriverPrefs.sort(prefOrder);
            driverPrefs.sort(prefOrder);

            if (!nonDriverPrefs.isEmpty()) {
                passengerPrefsByDriver.put(driver.id(), nonDriverPrefs);
            }
            if (!driverPrefs.isEmpty()) {
                driverRiderPrefsByDriver.put(driver.id(), driverPrefs);
            }
        }

        Map<String, List<CandidateEdge>> assignedByDriver = new HashMap<>();
        Map<String, CandidateEdge> riderMatch = new HashMap<>();
        runStableMatching(drivers, passengerPrefsByDriver, assignedByDriver, riderMatch, maxPassengerSeats, new HashSet<>());

        Set<String> busyDrivers = new HashSet<>();
        for (Map.Entry<String, List<CandidateEdge>> entry : assignedByDriver.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                busyDrivers.add(entry.getKey());
            }
        }
        runStableMatching(drivers, driverRiderPrefsByDriver, assignedByDriver, riderMatch, maxPassengerSeats, busyDrivers);

        for (CandidateEdge edge : riderMatch.values()) {
            results.add(new MatchResult(edge.driverId(), edge.riderId(), edge.score(), edge.origDist(), getAlgorithmName()));
        }

        return results;
    }

    private void runStableMatching(List<User> drivers,
                                   Map<String, List<CandidateEdge>> prefsByDriver,
                                   Map<String, List<CandidateEdge>> assignedByDriver,
                                   Map<String, CandidateEdge> riderMatch,
                                   int maxPassengerSeats,
                                   Set<String> excludedRiders) {
        Map<String, Integer> nextProposalIdx = new HashMap<>();
        ArrayDeque<String> freeDrivers = new ArrayDeque<>();

        for (User d : drivers) {
            if (riderMatch.containsKey(d.id())) continue;
            int currentCount = assignedByDriver.getOrDefault(d.id(), List.of()).size();
            if (currentCount < maxPassengerSeats && prefsByDriver.containsKey(d.id())) {
                freeDrivers.add(d.id());
                nextProposalIdx.put(d.id(), 0);
            }
        }

        while (!freeDrivers.isEmpty()) {
            String driverId = freeDrivers.poll();
            if (riderMatch.containsKey(driverId)) {
                continue;
            }

            List<CandidateEdge> prefs = prefsByDriver.get(driverId);
            List<CandidateEdge> currentAssigned = assignedByDriver.computeIfAbsent(driverId, k -> new ArrayList<>());

            while (currentAssigned.size() < maxPassengerSeats) {
                int idx = nextProposalIdx.getOrDefault(driverId, 0);
                if (idx >= prefs.size()) {
                    break;
                }
                nextProposalIdx.put(driverId, idx + 1);

                CandidateEdge proposal = prefs.get(idx);
                String riderId = proposal.riderId();
                if (excludedRiders.contains(riderId)) {
                    continue;
                }

                CandidateEdge existing = riderMatch.get(riderId);
                if (existing == null) {
                    riderMatch.put(riderId, proposal);
                    currentAssigned.add(proposal);
                    excludedRiders.add(driverId);
                } else if (isBetterForRider(proposal, existing)) {
                    riderMatch.put(riderId, proposal);
                    currentAssigned.add(proposal);
                    excludedRiders.add(driverId);

                    List<CandidateEdge> prevDriverList = assignedByDriver.get(existing.driverId());
                    if (prevDriverList != null) {
                        prevDriverList.remove(existing);
                        if (prevDriverList.isEmpty()) {
                            excludedRiders.remove(existing.driverId());
                        }
                        if (prevDriverList.size() < maxPassengerSeats && !riderMatch.containsKey(existing.driverId())) {
                            freeDrivers.add(existing.driverId());
                        }
                    }
                }
            }
        }
    }

    private boolean isBetterForRider(CandidateEdge candidate, CandidateEdge current) {
        if (candidate.score() != current.score()) {
            return candidate.score() > current.score();
        }
        return candidate.sharedLegs() > current.sharedLegs();
    }
}
package com.example.spatialdemo.service;

import com.example.spatialdemo.config.SimulationProperties;
import com.example.spatialdemo.model.*;
import com.example.spatialdemo.strategy.MatchmakingStrategy;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SimulationService {

    private final SimulationProperties properties;
    private final VirtualMap virtualMap;

    public SimulationService(SimulationProperties properties) {
        this.properties = properties;
        this.virtualMap = generateVirtualMap();
    }

    private VirtualMap generateVirtualMap() {
        List<Node> nodes = new ArrayList<>();
        Random random = new Random(properties.randomSeed());
        double mapSize = properties.mapSize();

        for (int i = 0; i < properties.nodeCount(); i++) {
            nodes.add(new Node("NODE-" + (i + 1), random.nextDouble() * mapSize, random.nextDouble() * mapSize));
        }

        return new VirtualMap(nodes);
    }

    public VirtualMap getVirtualMap() {
        return virtualMap;
    }

    public List<User> generateSyntheticUsers() {
        return generateSyntheticUsers(properties.userCount());
    }

    public List<User> generateSyntheticUsers(int count) {
        List<User> users = new ArrayList<>();
        Random random = new Random(properties.randomSeed() + 1);
        List<Node> nodes = virtualMap.nodes();

        for (int i = 0; i < count; i++) {
            Node originNode = nodes.get(random.nextInt(nodes.size()));
            double departureTime = random.nextDouble() * (properties.simulationHorizon() * 0.5);

            int stopCount = 1 + random.nextInt(Math.max(1, properties.maxUserStops()));
            List<Stop> itinerary = new ArrayList<>();
            Node previousNode = originNode;
            double cumulativeTime = departureTime;

            for (int s = 0; s < stopCount; s++) {
                Node stopNode;
                do {
                    stopNode = nodes.get(random.nextInt(nodes.size()));
                } while (stopNode.id().equals(previousNode.id()));

                double dist = previousNode.distanceTo(stopNode);
                cumulativeTime += dist * 1.2;

                double earliestArrival = cumulativeTime;
                double latestDeadline = earliestArrival + properties.minDeadlineOffset() + (random.nextDouble() * properties.maxDeadlineOffset() * 2.0);

                itinerary.add(new Stop(stopNode, earliestArrival, latestDeadline));
                previousNode = stopNode;
            }

            boolean isDriver = random.nextDouble() < properties.driverRatio();
            int capacity = isDriver ? random.nextInt(properties.minVehicleCapacity(), properties.maxVehicleCapacity() + 1) : 0;

            users.add(new User(
                    "USER-" + (i + 1),
                    "Participant " + (i + 1),
                    originNode,
                    departureTime,
                    itinerary,
                    isDriver,
                    capacity
            ));
        }
        return users;
    }

    private static class ParticipantState {
        User baseUser;
        Node currentLocation;
        double currentEffectiveDepartureTime;
        final List<Stop> remainingItinerary;
        final int totalLegs;
        int completedLegs = 0;
        boolean hasCompleted = false;
        final List<Double> legScores = new ArrayList<>();
        boolean wasCarpooled = false;

        int currentPassengersCount = 0;
        boolean isRidingAsPassenger = false;
        ParticipantState driverHost = null;
        final List<ParticipantState> passengersOnBoard = new ArrayList<>();

        ParticipantState(User user) {
            this.baseUser = user;
            this.currentLocation = user.originNode();
            this.currentEffectiveDepartureTime = user.departureTime();
            this.remainingItinerary = new ArrayList<>(user.itinerary());
            this.totalLegs = user.itinerary().size();
        }

        void advanceLeg(double legScore, boolean carpooled) {
            if (!remainingItinerary.isEmpty()) {
                Stop completedStop = remainingItinerary.remove(0);
                currentLocation = completedStop.node();
                currentEffectiveDepartureTime = completedStop.latestDeadline();
                completedLegs++;
                legScores.add(legScore);
                if (carpooled) {
                    this.wasCarpooled = true;
                }
                if (remainingItinerary.isEmpty()) {
                    hasCompleted = true;
                    passengersOnBoard.clear();
                }

                for (ParticipantState passenger : new ArrayList<>(passengersOnBoard)) {
                    passenger.currentLocation = this.currentLocation;
                    passenger.currentEffectiveDepartureTime = this.currentEffectiveDepartureTime;
                    passenger.advanceLeg(legScore, true);
                    if (passenger.hasCompleted || passenger.remainingItinerary.isEmpty()) {
                        passengersOnBoard.remove(passenger);
                        currentPassengersCount = Math.max(0, currentPassengersCount - 1);
                    }
                }
            }
        }

        void demoteToPassenger() {
            this.baseUser = new User(
                    baseUser.id(),
                    baseUser.name(),
                    baseUser.originNode(),
                    baseUser.departureTime(),
                    baseUser.itinerary(),
                    false,
                    0
            );
        }

        User toLegSnapshot() {
            int maxPassengerSeats = Math.max(0, baseUser.vehicleCapacity() - 1);
            int availableCapacity = baseUser.isDriver() ? Math.max(0, maxPassengerSeats - currentPassengersCount) : 0;

            return new User(
                    baseUser.id(),
                    baseUser.name(),
                    currentLocation,
                    currentEffectiveDepartureTime,
                    new ArrayList<>(remainingItinerary),
                    baseUser.isDriver(),
                    availableCapacity
            );
        }
    }

    public SimulationMetrics runSimulation(MatchmakingStrategy strategy, List<User> users) {
        long startTime = System.currentTimeMillis();

        double dispatchHorizon = properties.simulationHorizon();
        double maxTravelHorizon = dispatchHorizon * 5;
        double cycleDuration = properties.cycleDuration();

        List<ParticipantState> states = users.stream().map(ParticipantState::new).toList();
        List<MatchResult> allMatches = new ArrayList<>();

        for (double currentTime = 0.0; currentTime < maxTravelHorizon; currentTime += cycleDuration) {
            if (states.stream().allMatch(s -> s.hasCompleted)) {
                break;
            }

            double windowEnd = currentTime + cycleDuration;
            double finalCurrentTime = currentTime;

            List<ParticipantState> activeStates = states.stream()
                    .filter(s -> !s.hasCompleted)
                    .filter(s -> !s.isRidingAsPassenger)
                    .filter(s -> s.currentEffectiveDepartureTime >= finalCurrentTime && s.currentEffectiveDepartureTime < windowEnd)
                    .toList();

            if (!activeStates.isEmpty()) {
                List<ParticipantState> eligibleStates = activeStates.stream()
                        .filter(s -> s.baseUser.departureTime() <= dispatchHorizon)
                        .toList();

                if (!eligibleStates.isEmpty()) {
                    List<User> legUsers = eligibleStates.stream().map(ParticipantState::toLegSnapshot).toList();
                    List<MatchResult> cycleMatches = strategy.findMatches(legUsers);

                    Set<String> matchedIdsThisCycle = new HashSet<>();
                    for (MatchResult match : cycleMatches) {
                        allMatches.add(match);
                        matchedIdsThisCycle.add(match.driverId());
                        matchedIdsThisCycle.add(match.passengerId());

                        double carpoolScore = match.matchScore() * 2.0;

                        ParticipantState driverState = states.stream().filter(s -> s.baseUser.id().equals(match.driverId())).findFirst().orElse(null);
                        ParticipantState passengerState = states.stream().filter(s -> s.baseUser.id().equals(match.passengerId())).findFirst().orElse(null);

                        if (driverState != null && passengerState != null) {
                            driverState.passengersOnBoard.add(passengerState);
                            driverState.currentPassengersCount++;
                            passengerState.isRidingAsPassenger = true;
                            passengerState.driverHost = driverState;

                            driverState.advanceLeg(carpoolScore, true);
                            passengerState.demoteToPassenger();
                            passengerState.advanceLeg(carpoolScore, true);
                        }
                    }

                    for (ParticipantState state : eligibleStates) {
                        if (!matchedIdsThisCycle.contains(state.baseUser.id())) {
                            if (state.baseUser.isDriver() && state.baseUser.vehicleCapacity() > 0) {
                                double soloScore = 0.2;
                                state.advanceLeg(soloScore, false);
                            }
                        }
                    }
                }
            }
        }

        for (ParticipantState state : states) {
            while (state.legScores.size() < state.totalLegs) {
                state.legScores.add(0.0);
            }
        }

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        int totalUsers = users.size();
        int totalDrivers = (int) users.stream().filter(User::isDriver).count();
        int totalPassengers = totalUsers - totalDrivers;

        int completedCount = (int) states.stream().filter(s -> s.hasCompleted).count();
        int strandedCount = (int) states.stream()
                .filter(s -> !s.hasCompleted && !s.baseUser.isDriver())
                .count();

        double matchPercentage = totalUsers > 0 ? ((double) completedCount / totalUsers) * 100.0 : 0.0;

        double averageSimulationScore = states.stream()
                .flatMapToDouble(s -> s.legScores.stream().mapToDouble(Double::doubleValue))
                .average()
                .orElse(0.0);

        double averageMatchedScoreNoFails = states.stream()
                .filter(s -> s.hasCompleted)
                .flatMapToDouble(s -> s.legScores.stream().mapToDouble(Double::doubleValue))
                .average()
                .orElse(0.0);

        int independentCompletionCount = (int) states.stream().filter(s -> s.hasCompleted && !s.wasCarpooled).count();
        int carpoolCompletionCount = (int) states.stream().filter(s -> s.hasCompleted && s.wasCarpooled).count();

        double averageIndependentScore = states.stream()
                .filter(s -> !s.wasCarpooled)
                .flatMapToDouble(s -> s.legScores.stream().mapToDouble(Double::doubleValue))
                .average()
                .orElse(0.0);

        double averageCarpoolScore = states.stream()
                .filter(s -> s.wasCarpooled)
                .flatMapToDouble(s -> s.legScores.stream().mapToDouble(Double::doubleValue))
                .average()
                .orElse(0.0);

        int totalLegsAttempted = states.stream().mapToInt(s -> s.totalLegs).sum();
        int totalLegsCompleted = states.stream().mapToInt(s -> s.completedLegs).sum();
        double legCompletionPercentage = totalLegsAttempted > 0 ? ((double) totalLegsCompleted / totalLegsAttempted) * 100.0 : 0.0;

        double averageDetourDistance = allMatches.isEmpty() ? 0.0 : allMatches.stream()
                .mapToDouble(MatchResult::detourDistance)
                .average()
                .orElse(0.0);

        return new SimulationMetrics(
                strategy.getAlgorithmName(),
                totalUsers,
                totalDrivers,
                totalPassengers,
                completedCount,
                totalUsers - completedCount,
                strandedCount,
                matchPercentage,
                averageSimulationScore,
                averageMatchedScoreNoFails,
                independentCompletionCount,
                carpoolCompletionCount,
                averageIndependentScore,
                averageCarpoolScore,
                totalLegsAttempted,
                totalLegsCompleted,
                legCompletionPercentage,
                averageDetourDistance,
                executionTime,
                allMatches
        );
    }

    public SimulationProperties getProperties() {
        return properties;
    }
}
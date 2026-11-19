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

        if (nodes.size() < 2) {
            throw new IllegalStateException("At least 2 nodes are required to generate distinct sequential stops");
        }

        for (int i = 0; i < count; i++) {
            Node originNode = nodes.get(random.nextInt(nodes.size()));
            double departureTime = random.nextDouble() * properties.maxDepartureTime();

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
                double latestDeadline = earliestArrival + properties.minDeadlineOffset()
                        + (random.nextDouble() * properties.maxDeadlineOffset() * 2.0);

                itinerary.add(new Stop(stopNode, earliestArrival, latestDeadline));
                previousNode = stopNode;
            }

            boolean isDriver = random.nextDouble() < properties.driverRatio();

            users.add(new User(
                    "USER-" + (i + 1),
                    "Participant " + (i + 1),
                    originNode,
                    departureTime,
                    itinerary,
                    isDriver
            ));
        }
        return users;
    }

    private static class ParticipantState {
        final User baseUser;
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

        void advanceLeg(double legScore, boolean carpooled, double cycleDuration) {
            if (!remainingItinerary.isEmpty()) {
                Stop completedStop = remainingItinerary.removeFirst();
                currentLocation = completedStop.node();
                currentEffectiveDepartureTime = Math.max(
                        currentEffectiveDepartureTime + cycleDuration,
                        completedStop.latestDeadline()
                );
                completedLegs++;
                legScores.add(legScore);
                if (carpooled) {
                    this.wasCarpooled = true;
                }

                boolean driverFinished = remainingItinerary.isEmpty();
                if (driverFinished) {
                    hasCompleted = true;
                }

                for (ParticipantState passenger : new ArrayList<>(passengersOnBoard)) {
                    passenger.currentLocation = this.currentLocation;
                    passenger.advanceLeg(legScore, true, cycleDuration);
                    passenger.currentEffectiveDepartureTime = this.currentEffectiveDepartureTime;

                    if (driverFinished || passenger.hasCompleted || passenger.remainingItinerary.isEmpty()) {
                        passenger.isRidingAsPassenger = false;
                        passenger.driverHost = null;
                        passengersOnBoard.remove(passenger);
                        currentPassengersCount = Math.max(0, currentPassengersCount - 1);
                    }
                }

                if (driverFinished) {
                    passengersOnBoard.clear();
                    currentPassengersCount = 0;
                }
            }
        }

        User toLegSnapshot() {
            return new User(
                    baseUser.id(),
                    baseUser.name(),
                    currentLocation,
                    currentEffectiveDepartureTime,
                    new ArrayList<>(remainingItinerary),
                    baseUser.isDriver()
            );
        }
    }

    public SimulationMetrics runSimulation(MatchmakingStrategy strategy, List<User> users) {
        long startTime = System.currentTimeMillis();

        double dispatchHorizon = properties.simulationHorizon();
        double maxTravelHorizon = dispatchHorizon * 5;
        double cycleDuration = properties.cycleDuration();
        double timeThreshold = properties.timeThreshold();
        int maxPassengerSeats = Math.max(0, properties.vehicleCapacity() - 1);

        List<ParticipantState> states = users.stream().map(ParticipantState::new).toList();
        Map<String, ParticipantState> stateById = new HashMap<>();
        for (ParticipantState s : states) {
            stateById.put(s.baseUser.id(), s);
        }

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
                    .filter(s -> s.currentEffectiveDepartureTime < windowEnd)
                    .filter(s -> s.baseUser.isDriver()
                            ? s.currentEffectiveDepartureTime >= finalCurrentTime
                            : (finalCurrentTime - s.currentEffectiveDepartureTime) <= timeThreshold)
                    .toList();

            if (!activeStates.isEmpty()) {
                List<ParticipantState> eligibleStates = activeStates.stream()
                        .filter(s -> s.baseUser.departureTime() <= dispatchHorizon)
                        .toList();

                if (!eligibleStates.isEmpty()) {
                    List<User> legUsers = eligibleStates.stream()
                            .filter(s -> !s.baseUser.isDriver() || s.currentPassengersCount < maxPassengerSeats)
                            .map(ParticipantState::toLegSnapshot)
                            .toList();

                    List<MatchResult> cycleMatches = strategy.findMatches(legUsers);

                    Set<String> matchedIdsThisCycle = new HashSet<>();
                    Map<ParticipantState, Double> matchedDriverScores = new LinkedHashMap<>();

                    for (MatchResult match : cycleMatches) {
                        ParticipantState driverState = stateById.get(match.driverId());
                        ParticipantState passengerState = stateById.get(match.passengerId());

                        if (driverState != null && passengerState != null
                                && !driverState.baseUser.id().equals(passengerState.baseUser.id())
                                && driverState.baseUser.isDriver()
                                && !driverState.isRidingAsPassenger
                                && !passengerState.isRidingAsPassenger
                                && !matchedIdsThisCycle.contains(passengerState.baseUser.id())
                                && !matchedDriverScores.containsKey(passengerState)
                                && driverState.currentPassengersCount < maxPassengerSeats) {

                            allMatches.add(match);
                            matchedIdsThisCycle.add(match.driverId());
                            matchedIdsThisCycle.add(match.passengerId());

                            double carpoolScore = match.matchScore() * 2.0;

                            driverState.passengersOnBoard.add(passengerState);
                            driverState.currentPassengersCount++;
                            passengerState.isRidingAsPassenger = true;
                            passengerState.driverHost = driverState;

                            matchedDriverScores.merge(driverState, carpoolScore, Math::max);
                        }
                    }

                    for (Map.Entry<ParticipantState, Double> entry : matchedDriverScores.entrySet()) {
                        entry.getKey().advanceLeg(entry.getValue(), true, cycleDuration);
                    }

                    for (ParticipantState state : eligibleStates) {
                        if (!matchedIdsThisCycle.contains(state.baseUser.id()) && state.baseUser.isDriver()) {
                            double soloScore = 0.2;
                            state.advanceLeg(soloScore, false, cycleDuration);
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
                .filter(s -> s.hasCompleted && !s.wasCarpooled)
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
package com.example.spatialdemo.service;

import com.example.spatialdemo.config.SimulationProperties;
import com.example.spatialdemo.model.*;
import com.example.spatialdemo.strategy.MatchmakingStrategy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
        List<RoutePath> routePaths = new ArrayList<>();
        Random random = new Random(properties.randomSeed());
        double mapSize = properties.mapSize();

        for (int i = 0; i < properties.nodeCount(); i++) {
            nodes.add(new Node("NODE-" + (i + 1), random.nextDouble() * mapSize, random.nextDouble() * mapSize));
        }

        int roadIdCounter = 1;
        for (int i = 0; i < nodes.size(); i++) {
            Node n1 = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                Node n2 = nodes.get(j);
                double dist = n1.distanceTo(n2);
                if (dist <= (mapSize / 3.0)) {
                    double travelTime = dist * 1.5;
                    routePaths.add(new RoutePath("ROAD-" + roadIdCounter++, n1, n2, travelTime));
                }
            }
        }

        return new VirtualMap(nodes, routePaths);
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
            Node destinationNode;
            do {
                destinationNode = nodes.get(random.nextInt(nodes.size()));
            } while (destinationNode.id().equals(originNode.id()));

            double departureTime = random.nextDouble() * properties.maxDepartureTime();
            double deadline = departureTime + properties.minDeadlineOffset() + random.nextDouble() * properties.maxDeadlineOffset();

            users.add(new User(
                    "USER-" + (i + 1),
                    "Participant " + (i + 1),
                    originNode,
                    destinationNode,
                    departureTime,
                    deadline
            ));
        }
        return users;
    }

    public SimulationMetrics runSimulation(MatchmakingStrategy strategy, List<User> users) {
        int totalUsers = users.size();

        long startTime = System.currentTimeMillis();
        List<MatchResult> matches = strategy.findMatches(users);
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        int matchedUsersCount = matches.size() * 2;
        int unmatchedCount = Math.max(0, totalUsers - matchedUsersCount);
        double matchPercentage = totalUsers > 0 ? ((double) matchedUsersCount / totalUsers) * 100.0 : 0.0;

        double averageMatchScore = matches.isEmpty() ? 0.0 : matches.stream()
                .mapToDouble(MatchResult::matchScore)
                .average()
                .orElse(0.0);

        return new SimulationMetrics(
                strategy.getAlgorithmName(),
                totalUsers,
                0,
                totalUsers,
                matchedUsersCount,
                unmatchedCount,
                matchPercentage,
                averageMatchScore,
                executionTime,
                matches
        );
    }

    public SimulationProperties getProperties() {
        return properties;
    }
}
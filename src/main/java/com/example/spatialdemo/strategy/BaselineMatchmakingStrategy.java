package com.example.spatialdemo.strategy;

import com.example.spatialdemo.model.MatchResult;
import com.example.spatialdemo.model.User;

import java.util.ArrayList;
import java.util.List;

public record BaselineMatchmakingStrategy(double spatialThreshold,
                                          double timeThreshold) implements MatchmakingStrategy {

    @Override
    public String getAlgorithmName() {
        return "Exhaustive Peer Scan";
    }

    @Override
    public List<MatchResult> findMatches(List<User> users) {
        List<MatchResult> results = new ArrayList<>();
        List<String> matchedIds = new ArrayList<>();

        for (int i = 0; i < users.size(); i++) {
            User u1 = users.get(i);
            if (matchedIds.contains(u1.id())) continue;

            for (int j = i + 1; j < users.size(); j++) {
                User u2 = users.get(j);
                if (matchedIds.contains(u2.id())) continue;

                double destDist = u1.destination().distanceTo(u2.destination());
                double origDist = u1.origin().distanceTo(u2.origin());
                double timeDiff = Math.abs(u1.departureTime() - u2.departureTime());

                if (destDist <= spatialThreshold && origDist <= spatialThreshold && timeDiff <= timeThreshold) {
                    double score = 1.0 / (1.0 + origDist + destDist + timeDiff);
                    results.add(new MatchResult(u1.id(), u2.id(), score, origDist, getAlgorithmName()));
                    matchedIds.add(u1.id());
                    matchedIds.add(u2.id());
                    break;
                }
            }
        }
        return results;
    }
}
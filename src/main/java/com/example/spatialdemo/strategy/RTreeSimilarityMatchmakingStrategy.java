package com.example.spatialdemo.strategy;

import com.example.spatialdemo.model.MatchResult;
import com.example.spatialdemo.model.User;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Rectangle;

import java.util.ArrayList;
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

        RTree<User, Rectangle> tree = RTree.create();
        for (User user : users) {
            double minX = Math.min(user.origin().x(), user.destination().x());
            double minY = Math.min(user.origin().y(), user.destination().y());
            double maxX = Math.max(user.origin().x(), user.destination().x());
            double maxY = Math.max(user.origin().y(), user.destination().y());
            Rectangle rect = Geometries.rectangle(minX - rtreeBuffer, minY - rtreeBuffer, maxX + rtreeBuffer, maxY + rtreeBuffer);
            tree = tree.add(user, rect);
        }

        List<String> matchedIds = new ArrayList<>();

        for (User user : users) {
            if (matchedIds.contains(user.id())) continue;

            Rectangle searchRect = Geometries.rectangle(
                    user.origin().x() - spatialThreshold,
                    user.origin().y() - spatialThreshold,
                    user.origin().x() + spatialThreshold,
                    user.origin().y() + spatialThreshold
            );

            Iterable<Entry<User, Rectangle>> candidateEntries = tree.search(searchRect).toBlocking().toIterable();

            User bestPartner = null;
            double bestScore = -1.0;
            double bestOrigDist = Double.MAX_VALUE;

            for (Entry<User, Rectangle> entry : candidateEntries) {
                User candidate = entry.value();
                if (candidate.id().equals(user.id()) || matchedIds.contains(candidate.id())) continue;

                double destDist = user.destination().distanceTo(candidate.destination());
                double origDist = user.origin().distanceTo(candidate.origin());
                double timeDiff = Math.abs(user.departureTime() - candidate.departureTime());

                if (destDist <= spatialThreshold && origDist <= spatialThreshold && timeDiff <= timeThreshold) {
                    double score = 1.0 / (1.0 + origDist + destDist + timeDiff);
                    if (score > bestScore) {
                        bestScore = score;
                        bestPartner = candidate;
                        bestOrigDist = origDist;
                    }
                }
            }

            if (bestPartner != null) {
                results.add(new MatchResult(user.id(), bestPartner.id(), bestScore, bestOrigDist, getAlgorithmName()));
                matchedIds.add(user.id());
                matchedIds.add(bestPartner.id());
            }
        }
        return results;
    }
}
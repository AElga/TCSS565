package com.example.spatialdemo.strategy;

import com.example.spatialdemo.model.MatchResult;
import com.example.spatialdemo.model.User;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.kdtree.KdNode;
import org.locationtech.jts.index.kdtree.KdTree;

import java.util.ArrayList;
import java.util.List;

public record KdTreeSimilarityMatchmakingStrategy(double spatialThreshold,
                                                  double timeThreshold) implements MatchmakingStrategy {

    private record IndexedUser(User user, int inputOrder) {}

    private static final class UserGroup {
        private final List<IndexedUser> users = new ArrayList<>();
    }

    @Override
    public String getAlgorithmName() {
        return "K-D Tree Peer Index";
    }

    @Override
    public List<MatchResult> findMatches(List<User> users) {
        KdTree tree = new KdTree();
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            KdNode node = tree.insert(
                    new Coordinate(user.origin().x(), user.origin().y()),
                    new UserGroup()
            );
            if (!(node.getData() instanceof UserGroup group)) {
                throw new IllegalStateException("Unexpected data stored in k-d tree node");
            }
            group.users.add(new IndexedUser(user, i));
        }

        List<MatchResult> results = new ArrayList<>();
        List<String> matchedIds = new ArrayList<>();

        for (User user : users) {
            if (matchedIds.contains(user.id())) continue;

            List<IndexedUser> candidates = new ArrayList<>();
            Envelope searchEnvelope = new Envelope(
                    user.origin().x() - spatialThreshold,
                    user.origin().x() + spatialThreshold,
                    user.origin().y() - spatialThreshold,
                    user.origin().y() + spatialThreshold
            );
            for (Object result : tree.query(searchEnvelope)) {
                if (!(result instanceof KdNode node) || !(node.getData() instanceof UserGroup group)) {
                    throw new IllegalStateException("Unexpected result returned by k-d tree query");
                }
                candidates.addAll(group.users);
            }
            candidates.sort(java.util.Comparator.comparingInt(IndexedUser::inputOrder));

            User bestPartner = null;
            double bestScore = -1.0;
            double bestOrigDist = Double.MAX_VALUE;

            for (IndexedUser indexedCandidate : candidates) {
                User candidate = indexedCandidate.user();
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

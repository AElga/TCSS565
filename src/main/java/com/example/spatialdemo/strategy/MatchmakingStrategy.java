package com.example.spatialdemo.strategy;

import com.example.spatialdemo.model.MatchResult;
import com.example.spatialdemo.model.User;
import java.util.List;

public interface MatchmakingStrategy {
    String getAlgorithmName();
    List<MatchResult> findMatches(List<User> users);
}
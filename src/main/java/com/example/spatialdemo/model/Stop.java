package com.example.spatialdemo.model;

public record Stop(
        Node node,
        double earliestArrival,
        double latestDeadline
) {}
package com.example.spatialdemo.model;

public record RoutePath(String id, Node source, Node target, double travelTime) {}
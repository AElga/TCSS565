package com.example.spatialdemo.model;

import java.util.List;

public record VirtualMap(List<Node> nodes, List<RoutePath> routePaths) {}
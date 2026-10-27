package com.example.spatialdemo;

import com.example.spatialdemo.model.SimulationMetrics;
import com.example.spatialdemo.model.User;
import com.example.spatialdemo.model.VirtualMap;
import com.example.spatialdemo.service.SimulationService;
import com.example.spatialdemo.strategy.BaselineMatchmakingStrategy;
import com.example.spatialdemo.strategy.RTreeSimilarityMatchmakingStrategy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.List;

@SpringBootApplication
@EnableConfigurationProperties(com.example.spatialdemo.config.SimulationProperties.class)
public class SpatialDemoApplication implements CommandLineRunner {

    private final SimulationService simulationService;

    public SpatialDemoApplication(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpatialDemoApplication.class, args);
    }

    @Override
    public void run(String... args) {
        var props = simulationService.getProperties();
        VirtualMap map = simulationService.getVirtualMap();

        System.out.printf("Nodes: %d | Routes: %d%n", map.nodes().size(), map.routePaths().size());
        System.out.printf("Users: %d | Spatial Threshold: %.1f | Time Threshold: %.1f%n",
                props.userCount(), props.spatialThreshold(), props.timeThreshold());

        List<User> users = simulationService.generateSyntheticUsers();
        System.out.printf("Population Initialized: [%d Participants spawning at graph nodes]%n", users.size());
        System.out.println("This is where the fun begins! For nerds, anyway.");
        System.out.println();


        BaselineMatchmakingStrategy baseline = new BaselineMatchmakingStrategy(props.spatialThreshold(), props.timeThreshold());
        SimulationMetrics baselineMetrics = simulationService.runSimulation(baseline, users);
        printMetrics(baselineMetrics);

        System.out.println();

        RTreeSimilarityMatchmakingStrategy rtree = new RTreeSimilarityMatchmakingStrategy(props.spatialThreshold(), props.timeThreshold(), props.rtreeBuffer());
        SimulationMetrics rtreeMetrics = simulationService.runSimulation(rtree, users);
        printMetrics(rtreeMetrics);

        System.out.println();
        System.out.println("Simulation complete. You may end the program.");
    }

    private void printMetrics(SimulationMetrics m) {
        System.out.printf("ALGORITHM: %s%n", m.algorithmName());
        System.out.printf("  Total Participants  : %d%n", m.totalUsers());
        System.out.printf("  Matched Participants: %d / %d (%d Carpool Pairs)%n", m.matchedCount(), m.totalUsers(), m.matches().size());
        System.out.printf("  Match Percentage    : %.2f%%\n", m.matchPercentage());
        System.out.printf("  Average Match Score : %.4f%n", m.averageMatchScore());
        System.out.printf("  Unmatched Count     : %d%n", m.unmatchedCount());
        System.out.printf("  Execution Time      : %d ms%n", m.executionTimeMs());
    }
}
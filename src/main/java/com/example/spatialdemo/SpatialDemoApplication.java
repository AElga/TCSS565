package com.example.spatialdemo;

import com.example.spatialdemo.model.SimulationMetrics;
import com.example.spatialdemo.model.User;
import com.example.spatialdemo.model.VirtualMap;
import com.example.spatialdemo.service.SimulationService;
import com.example.spatialdemo.strategy.BaselineMatchmakingStrategy;
import com.example.spatialdemo.strategy.RTreeDeepMatchingStrategy;
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

        System.out.printf("Nodes: %d (Virtual Map active)%n", map.nodes().size());
        System.out.printf("Users: %d | Horizon: %.1f | Cycle: %.1f | Max Stops: %d%n",
                props.userCount(), props.simulationHorizon(), props.cycleDuration(), props.maxUserStops());

        List<User> users = simulationService.generateSyntheticUsers();
        System.out.printf("Population Initialized: [%d Participants with multi-stop itineraries]%n", users.size());

        BaselineMatchmakingStrategy baseline = new BaselineMatchmakingStrategy(props.spatialThreshold(), props.timeThreshold());
        SimulationMetrics baselineMetrics = simulationService.runSimulation(baseline, users);
        printMetrics(baselineMetrics);

        System.out.println();

        RTreeSimilarityMatchmakingStrategy rtree = new RTreeSimilarityMatchmakingStrategy(props.spatialThreshold(), props.timeThreshold(), props.rtreeBuffer());
        SimulationMetrics rtreeMetrics = simulationService.runSimulation(rtree, users);
        printMetrics(rtreeMetrics);

        System.out.println();

        RTreeDeepMatchingStrategy deepRtree = new RTreeDeepMatchingStrategy(props.spatialThreshold(), props.timeThreshold(), props.rtreeBuffer());
        SimulationMetrics deepMetrics = simulationService.runSimulation(deepRtree, users);
        printMetrics(deepMetrics);

        System.out.println();
        System.out.println("Simulation complete. You may end the program.");
    }

    private void printMetrics(SimulationMetrics m) {
        System.out.printf("ALGORITHM: %s%n", m.algorithmName());
        System.out.printf("  Total Participants       : %d%n", m.totalUsers());
        System.out.printf("  Matched Participants     : %d / %d (%d Carpool Pairs)%n", m.matchedCount(), m.totalUsers(), m.matches().size());
        System.out.printf("    - Independent Solo     : %d participants%n", m.independentCompletionCount());
        System.out.printf("    - Carpool Participants : %d participants%n", m.carpoolCompletionCount());
        System.out.printf("  Match Percentage         : %.2f%%\n", m.matchPercentage());
        System.out.printf("  Leg Completion Rate      : %d / %d legs (%.2f%%)%n", m.totalLegsCompleted(), m.totalLegsAttempted(), m.legCompletionPercentage());
        System.out.printf("  Average Simulation Score : %.4f%n", m.averageSimulationScore());
        System.out.printf("  Avg Score (No Fails)     : %.4f%n", m.averageMatchedScoreNoFails());
        System.out.printf("    - Independent Score    : %.4f%n", m.averageIndependentScore());
        System.out.printf("    - Carpool Score        : %.4f%n", m.averageCarpoolScore());
        System.out.printf("  Average Detour Dist      : %.2f%n", m.averageDetourDistance());
        System.out.printf("  Unmatched Count          : %d%n", m.unmatchedCount());
        System.out.printf("  Stranded Count           : %d (Including Initially Isolated Passengers)%n", m.strandedCount());
        System.out.printf("  Execution Time           : %d ms%n", m.executionTimeMs());
    }
}
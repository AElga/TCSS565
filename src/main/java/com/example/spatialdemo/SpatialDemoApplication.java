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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SpringBootApplication
@EnableConfigurationProperties(com.example.spatialdemo.config.SimulationProperties.class)
public class SpatialDemoApplication implements CommandLineRunner {

    private record SimulationResult(int participantCount, SimulationMetrics metrics) {}

    private final SimulationService simulationService;

    public SpatialDemoApplication(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpatialDemoApplication.class, args);
    }

    @Override
    public void run(String... args) throws IOException {
        var props = simulationService.getProperties();

        VirtualMap map = simulationService.getVirtualMap();

        System.out.printf("Nodes: %d | Routes: %d%n", map.nodes().size(), map.routePaths().size());
        System.out.printf("Participant range: 50 to 500 | Spatial Threshold: %.1f | Time Threshold: %.1f%n",
                props.spatialThreshold(), props.timeThreshold());
        System.out.println("This is where the fun begins! For nerds, anyway.");
        System.out.println();

        BaselineMatchmakingStrategy baseline = new BaselineMatchmakingStrategy(props.spatialThreshold(), props.timeThreshold());
        RTreeSimilarityMatchmakingStrategy rtree = new RTreeSimilarityMatchmakingStrategy(props.spatialThreshold(), props.timeThreshold(), props.rtreeBuffer());
        List<SimulationResult> results = new ArrayList<>(20);
        for (int participantCount = 50; participantCount <= 500; participantCount += 50) {
            List<User> users = simulationService.generateSyntheticUsers(participantCount);
            System.out.printf("Running algorithms for %d participants...%n", participantCount);
            SimulationMetrics baselineMetrics = simulationService.runSimulation(baseline, users);
            results.add(new SimulationResult(participantCount, baselineMetrics));

            SimulationMetrics rtreeMetrics = simulationService.runSimulation(rtree, users);
            results.add(new SimulationResult(participantCount, rtreeMetrics));
        }

        Path outputDirectory = Path.of("src", "main", "java", "com", "example", "spatialdemo", "output");
        Files.createDirectories(outputDirectory);
        Path combinedCsvPath = outputDirectory.resolve("simulation-results.csv");
        writeCombinedResultsCsv(combinedCsvPath, results);
        writeAlgorithmResultsCsvs(outputDirectory, results);

        SimulationMetrics exampleMetrics = results.stream()
                .filter(result -> result.participantCount() == 500)
                .map(SimulationResult::metrics)
                .filter(metrics -> metrics.algorithmName().equals(rtree.getAlgorithmName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing R-Tree results for 500 participants"));
        System.out.println();
        System.out.println("Example result (R-Tree, 500 participants):");
        printMetrics(exampleMetrics);

        String baselineFileName = toFileName(baseline.getAlgorithmName());
        String rtreeFileName = toFileName(rtree.getAlgorithmName());
        System.out.printf("%nCSV output directory: %s%n", outputDirectory.toAbsolutePath());
        System.out.printf("  Combined: %s%n", combinedCsvPath.toAbsolutePath());
        System.out.printf("  Execution time: %s-execution-time.csv, %s-execution-time.csv%n",
                baselineFileName, rtreeFileName);
        System.out.printf("  Match percentage: %s-match-percentage.csv, %s-match-percentage.csv%n",
                baselineFileName, rtreeFileName);
        System.out.println("  Chart columns: x_axis_participants vs. the corresponding y_axis value");
        System.out.println("Simulation complete. You may end the program.");
    }

    private void writeCombinedResultsCsv(Path csvPath, List<SimulationResult> results) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            writer.write("algorithm,x_axis_participants,y_axis_execution_time_ms,y_axis_match_percentage");
            writer.newLine();
            for (SimulationResult result : results) {
                SimulationMetrics metrics = result.metrics();
                writer.write(escapeCsv(metrics.algorithmName()) + "," + result.participantCount() + ","
                        + metrics.executionTimeMs() + "," + metrics.matchPercentage());
                writer.newLine();
            }
        }
    }

    private void writeAlgorithmResultsCsvs(Path outputDirectory, List<SimulationResult> results) throws IOException {
        Map<String, List<SimulationResult>> resultsByAlgorithm = new LinkedHashMap<>();
        for (SimulationResult result : results) {
            resultsByAlgorithm.computeIfAbsent(result.metrics().algorithmName(), name -> new ArrayList<>()).add(result);
        }

        for (Map.Entry<String, List<SimulationResult>> entry : resultsByAlgorithm.entrySet()) {
            String baseName = toFileName(entry.getKey());
            Path executionTimeCsv = outputDirectory.resolve(baseName + "-execution-time.csv");
            Path matchPercentageCsv = outputDirectory.resolve(baseName + "-match-percentage.csv");
            try (BufferedWriter timeWriter = Files.newBufferedWriter(executionTimeCsv, StandardCharsets.UTF_8);
                 BufferedWriter percentageWriter = Files.newBufferedWriter(matchPercentageCsv, StandardCharsets.UTF_8)) {
                timeWriter.write("x_axis_participants,y_axis_execution_time_ms");
                timeWriter.newLine();
                percentageWriter.write("x_axis_participants,y_axis_match_percentage");
                percentageWriter.newLine();
                for (SimulationResult result : entry.getValue()) {
                    SimulationMetrics metrics = result.metrics();
                    timeWriter.write(result.participantCount() + "," + metrics.executionTimeMs());
                    timeWriter.newLine();
                    percentageWriter.write(result.participantCount() + "," + metrics.matchPercentage());
                    percentageWriter.newLine();
                }
            }
        }
    }

    private String toFileName(String algorithmName) {
        return algorithmName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private String escapeCsv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
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
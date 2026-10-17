package com.example.spatialdemo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class SpatialDemoApplication implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public SpatialDemoApplication(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpatialDemoApplication.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            String selectSql = "SELECT Name, Location.STAsText() AS WKTLocation FROM Places";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql);

            System.out.println("\n--- Stored Spatial Locations ---");
            for (Map<String, Object> row : rows) {
                System.out.printf("Place: %-22s | Location (WKT): %s%n",
                        row.get("Name"),
                        row.get("WKTLocation"));
            }

            String distanceSql = "SELECT Name, " +
                    "Location.STDistance(geography::STGeomFromText('POINT(-73.9851 40.7589)', 4326)) AS DistanceMeters " +
                    "FROM Places";

            List<Map<String, Object>> distanceRows = jdbcTemplate.queryForList(distanceSql);

            System.out.println("\n--- Distance from Times Square (-73.9851, 40.7589) ---");
            for (Map<String, Object> row : distanceRows) {
                System.out.printf("Place: %-22s | Distance: %.2f meters%n",
                        row.get("Name"),
                        ((Number) row.get("DistanceMeters")).doubleValue());
            }
            System.out.println();
            System.out.println();

        } catch (Exception e) {
            System.err.println("Database Connection Failed! Check your TCP/IP settings or credentials.");
            e.printStackTrace();
        }
    }
}

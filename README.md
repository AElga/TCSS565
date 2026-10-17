# Spatial Demo

This project is a Spring Boot application that connects to a Microsoft SQL Server database and queries spatial data stored in the `Places` table. It demonstrates how to store location data in a SQL Server spatial column, retrieve it as WKT, and calculate distances from a point such as Times Square in New York City.

## What the app does

The application:

- starts as a Spring Boot console app
- connects to SQL Server using the JDBC driver
- reads rows from the `Places` table
- prints each location in Well-Known Text (WKT) format
- calculates the distance from a reference point to each stored location using SQL Server geography functions
- prints a simple result summary to the console

This is useful for learning how SQL Server spatial data types work in a Java application, and how to combine Java, Spring Boot, and SQL Server for geospatial queries.

## Dependencies

Before running the app, make sure the following are installed and available:

- Java 25 or later
- Maven 3.9+
- Microsoft SQL Server 2022 or SQL Server Express / LocalDB instance
- SQL Server TCP/IP enabled
- A database named `SpatialDemo`
- A table named `Places` with a `Location` column of type `geography`
- The SQL Server JDBC driver included by Maven

## Database setup

1. Open SQL Server Management Studio (SSMS) or another SQL client.
2. Connect to your local SQL Server instance.
3. Run the SQL script in `database/SpatialDemo.sql`.
4. Confirm the `SpatialDemo` database exists and contains the `Places` table.
5. Verify that the table includes rows with geospatial coordinates similar to the example locations.

## Configuration

Update the database connection values in `src/main/resources/application.properties` if needed:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=SpatialDemo;encrypt=true;trustServerCertificate=true;
spring.datasource.username=sa
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false
logging.level.root=ERROR
logging.level.org.springframework=ERROR
```

If you are using a different SQL Server instance or port, update the URL accordingly.

## Step-by-step: how to run the app

1. Open a terminal in the project root folder:

   ```bash
   cd C:\TCSS565\TCSS565
   ```

2. Set Java and Maven on the PATH if needed:

   ```powershell
   $env:JAVA_HOME = 'C:\tools\jdk-25\jdk-25.0.2'
   $env:PATH = "$env:JAVA_HOME\bin;C:\tools\maven-3.9.15\maven-3.9.15\bin;$env:PATH"
   ```

3. Make sure the SQL Server database is running and reachable.
4. Confirm that the `SpatialDemo` database is available and the `Places` table exists.
5. Run the application:

   ```bash
   mvn spring-boot:run
   ```

6. The app will print the stored locations and the distance from Times Square to each location.

## Example output

```text
--- Stored Spatial Locations ---
Place: Empire State Building  | Location (WKT): POINT (-73.9857 40.7484)
Place: Central Park           | Location (WKT): POINT (-73.9654 40.7829)

--- Distance from Times Square (-73.9851, 40.7589) ---
Place: Empire State Building  | Distance: 1167.12 meters
Place: Central Park           | Distance: 3141.56 meters
```

## Troubleshooting

- If the app cannot connect to SQL Server, check that TCP/IP is enabled for the SQL Server instance.
- If the database cannot be found, create it using the script in `database/SpatialDemo.sql`.
- If login credentials fail, update the `spring.datasource.username` and `spring.datasource.password` values in `application.properties`.
- If the app cannot resolve the SQL Server instance, use the direct server port and instance configuration in the JDBC URL.

## Project structure

- `src/main/java/com/example/spatialdemo/SpatialDemoApplication.java` — entry point and query logic
- `src/main/resources/application.properties` — database configuration
- `database/SpatialDemo.sql` — database and table creation script
- `pom.xml` — Maven configuration and dependencies

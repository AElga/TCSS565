CREATE DATABASE SpatialDemo;
GO

USE SpatialDemo;
GO

CREATE TABLE Places (
    ID INT IDENTITY(1,1) PRIMARY KEY,
    Name NVARCHAR(100),
    Location GEOGRAPHY
);
GO

INSERT INTO Places (Name, Location)
VALUES 
    ('Empire State Building', GEOGRAPHY::STGeomFromText('POINT(-73.9857 40.7484)', 4326)),
    ('Central Park', GEOGRAPHY::STGeomFromText('POINT(-73.9654 40.7829)', 4326));
GO

# ------------------------------------------------------------------------------
# Multi-stage Dockerfile for Media Buying Dashboard
# Stage 1: Build with Maven + Eclipse Temurin 11
# Stage 2: Runtime with Eclipse Temurin 11 JRE
# ------------------------------------------------------------------------------

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-11 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:11-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE ${PORT:-6800}

# Shell form CMD — Docker wraps with /bin/sh -c and evaluates ${PORT} directly.
# This reads Railway's PORT env var and passes it as a command-line argument.
# Command-line args have the highest Spring Boot property precedence.
CMD java -jar app.jar --server.port=${PORT:-6800} --server.address=0.0.0.0

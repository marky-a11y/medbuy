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

RUN mvn clean package -DskipTests -P railway

# Stage 2: Runtime
FROM eclipse-temurin:11-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# EXPOSE is documentation-only; Railway routes traffic to the PORT env var,
# which application.yml consumes via server.port: ${PORT:8080}.
EXPOSE 8080

# Exec form — no shell wrapper needed. application.yml resolves the PORT env
# var through Spring Boot's property mechanism (server.port: ${PORT:8080}).
# -XX:+ExitOnOutOfMemoryError: On OOM the JVM calls System.exit(1) instead of
# silently dying, which triggers our shutdown hook so we can distinguish OOM
# from other external kills in the logs.
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

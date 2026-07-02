# ------------------------------------------------------------------------------
# Multi-stage Dockerfile for Media Buying Dashboard
# Stage 1: Build with Maven + Eclipse Temurin 8
# Stage 2: Runtime with Eclipse Temurin 8 JRE
# ------------------------------------------------------------------------------

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-8 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:8-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

CMD ["java","-jar","app.jar"]

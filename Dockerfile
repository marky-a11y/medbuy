# ------------------------------------------------------------------------------
# Multi-stage Dockerfile for Media Buying Dashboard
# Stage 1: Build with Maven + Java 8
# Stage 2: Runtime with OpenJDK 8 JRE
# ------------------------------------------------------------------------------

# Stage 1: Build
FROM maven:3.8-openjdk-8 AS build

WORKDIR /app

# Copy pom.xml and download dependencies (layer caching)
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM openjdk:8-jre-alpine

# Create non-root user for security
RUN addgroup -S mediabuyer && \
    adduser -S -G mediabuyer -h /app -s /sbin/nologin mediabuyer

# Create necessary directories
RUN mkdir -p /app /var/log/mediabuying && \
    chown -R mediabuyer:mediabuyer /app /var/log/mediabuying

# Copy the built JAR from the build stage
COPY --from=build /app/target/media-buying-dashboard.jar /app/app.jar

# Set working directory
WORKDIR /app

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run as non-root user
USER mediabuyer

# Entry point - bind to 0.0.0.0 as required by java_web_app.md
ENTRYPOINT ["java", \
    "-jar", "/app/app.jar", \
    "--server.address=0.0.0.0", \
    "-Djava.security.egd=file:/dev/./urandom"]

# Build stage
FROM maven:3.9.0-eclipse-temurin-17 AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first for better layer caching
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src

# Build application
RUN mvn -B -DskipTests clean package

# Runtime stage - Use JRE instead of JDK for smaller image
FROM eclipse-temurin:17-jre-jammy

# Add metadata labels
LABEL maintainer="samuel@example.com"
LABEL description="Inventory Service - Product Stock Management Microservice"
LABEL version="1.0.0"

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Copy JAR from build stage with flexible naming
ARG JAR_FILE=target/*.jar
COPY --from=build /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8081

# Add health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

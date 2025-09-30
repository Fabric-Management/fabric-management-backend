# =============================================================================
# COMPANY SERVICE DOCKERFILE
# =============================================================================
# Multi-stage build for optimized production image

# Build stage
FROM maven:3.9.6-openjdk-21-slim AS build

# Set working directory
WORKDIR /app

# Copy parent POM and shared modules
COPY pom.xml .
COPY shared/ ./shared/

# Copy service POM
COPY services/company-service/pom.xml ./services/company-service/

# Download dependencies (this layer will be cached)
RUN mvn dependency:go-offline -f services/company-service/pom.xml

# Copy service source code
COPY services/company-service/src ./services/company-service/src

# Build the application
RUN mvn clean package -f services/company-service/pom.xml -DskipTests

# Runtime stage
FROM openjdk:21-jre-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r fabricuser && useradd -r -g fabricuser fabricuser

# Set working directory
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/services/company-service/target/company-service-*.jar app.jar

# Change ownership
RUN chown -R fabricuser:fabricuser /app
USER fabricuser

# Expose port
EXPOSE 8083

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8083/api/v1/companies/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

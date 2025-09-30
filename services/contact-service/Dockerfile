# =============================================================================
# CONTACT SERVICE DOCKERFILE
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
COPY services/contact-service/pom.xml ./services/contact-service/

# Download dependencies (this layer will be cached)
RUN mvn dependency:go-offline -f services/contact-service/pom.xml

# Copy service source code
COPY services/contact-service/src ./services/contact-service/src

# Build the application
RUN mvn clean package -f services/contact-service/pom.xml -DskipTests

# Runtime stage
FROM openjdk:21-jre-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r fabricuser && useradd -r -g fabricuser fabricuser

# Set working directory
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/services/contact-service/target/contact-service-*.jar app.jar

# Change ownership
RUN chown -R fabricuser:fabricuser /app
USER fabricuser

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8082/api/v1/contacts/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

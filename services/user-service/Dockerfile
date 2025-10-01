# =============================================================================
# USER SERVICE DOCKERFILE - SIMPLIFIED & FAST
# =============================================================================
# Simple two-stage build for faster deployment

# =============================================================================
# Stage 1: Build (Simplified)
# =============================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Copy everything needed for build
COPY pom.xml .
COPY shared/ shared/
COPY services/user-service/ services/user-service/

# Build the service (Maven reactor will handle shared modules automatically)
RUN mvn clean package -pl services/user-service -am -DskipTests -B

# =============================================================================
# Stage 2: Runtime Image (Minimal)
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

# Install required tools
RUN apk add --no-cache curl netcat-openbsd bash && rm -rf /var/cache/apk/*

# Create non-root user
RUN addgroup -g 1001 -S fabricuser && \
    adduser -u 1001 -S fabricuser -G fabricuser

# Set working directory
WORKDIR /app

# Copy the JAR from build stage
COPY --from=build /build/services/user-service/target/user-service-1.0.0-SNAPSHOT.jar app.jar

# Copy entrypoint script
COPY scripts/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Change ownership
RUN chown -R fabricuser:fabricuser /app

# Switch to non-root user
USER fabricuser

# Expose application port and JMX port
EXPOSE 8081 9010

# Health check with liveness probe
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/api/v1/users/actuator/health/liveness || exit 1

# Set environment variables
ENV SPRING_APPLICATION_NAME=user-service
ENV SERVER_PORT=8081

# Use custom entrypoint
ENTRYPOINT ["/docker-entrypoint.sh"]

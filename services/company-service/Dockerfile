# Multi-stage build for company-service
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy parent POM and common modules first for better caching
COPY pom.xml .
COPY common/ common/
COPY services/company-service/pom.xml services/company-service/

# Download dependencies
RUN mvn dependency:go-offline -pl services/company-service -am

# Copy source code and build
COPY services/company-service/src/ services/company-service/src/
RUN mvn clean package -pl services/company-service -am -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy JAR file
COPY --from=builder --chown=appuser:appgroup /app/services/company-service/target/*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown appuser:appgroup /app/logs

USER appuser

EXPOSE 8083 9083

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:9083/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:docker}", \
    "-Dmanagement.server.port=9083", \
    "-Dserver.port=8083", \
    "-jar", "app.jar"]

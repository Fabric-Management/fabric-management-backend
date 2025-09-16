# Multi-stage build for auth-service
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy parent POM and common modules first for better caching
COPY pom.xml .
COPY common/ common/
COPY services/auth-service/pom.xml services/auth-service/

# Download dependencies
RUN mvn dependency:go-offline -pl services/auth-service -am

# Copy source code and build
COPY services/auth-service/src/ services/auth-service/src/
RUN mvn clean package -pl services/auth-service -am -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /app/services/auth-service/target/*.jar app.jar

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

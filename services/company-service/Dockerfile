# Multi-stage build for company-service
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Configure Maven for better network reliability
ENV MAVEN_OPTS="-Dmaven.repo.local=/root/.m2/repository \
    -Dhttp.socketTimeout=60000 \
    -Dhttp.connectionTimeout=60000 \
    -Dmaven.wagon.http.retryHandler.count=3"

# Create Maven settings using printf to avoid parsing issues
RUN mkdir -p /root/.m2 && \
    printf '%s\n' \
    '<?xml version="1.0" encoding="UTF-8"?>' \
    '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
    '    <profiles>' \
    '        <profile>' \
    '            <id>default</id>' \
    '            <properties>' \
    '                <maven.wagon.http.connectionTimeout>60000</maven.wagon.http.connectionTimeout>' \
    '                <maven.wagon.http.readTimeout>60000</maven.wagon.http.readTimeout>' \
    '            </properties>' \
    '        </profile>' \
    '    </profiles>' \
    '    <activeProfiles>' \
    '        <activeProfile>default</activeProfile>' \
    '    </activeProfiles>' \
    '</settings>' > /root/.m2/settings.xml

# Copy all POMs first for better caching
COPY pom.xml .
COPY common/common-core/pom.xml common/common-core/
COPY common/common-security/pom.xml common/common-security/
COPY services/company-service/pom.xml services/company-service/

# Build parent POM with retry logic
RUN mvn clean install -N -DskipTests || \
    (sleep 10 && mvn clean install -N -DskipTests) || \
    (sleep 20 && mvn clean install -N -DskipTests)

# Copy and build common modules source
COPY common/common-core/src common/common-core/src
RUN mvn clean install -f common/common-core/pom.xml -DskipTests

COPY common/common-security/src common/common-security/src
RUN mvn clean install -f common/common-security/pom.xml -DskipTests

# Download service dependencies
WORKDIR /app/services/company-service
RUN mvn dependency:go-offline -B

# Copy and build service
COPY services/company-service/src src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install wget for healthcheck
RUN apk add --no-cache wget

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy JAR file
COPY --from=builder --chown=appuser:appgroup /app/services/company-service/target/*.jar app.jar

# Create necessary directories
RUN mkdir -p /app/logs /tmp && \
    chown -R appuser:appgroup /app/logs /tmp

# Set JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m \
    -XX:MaxMetaspaceSize=128m \
    -XX:+UseG1GC \
    -Djava.io.tmpdir=/tmp \
    -Djava.security.egd=file:/dev/./urandom"

USER appuser

EXPOSE 8083

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Multi-stage build for user-service
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Configure Maven for better network reliability
ENV MAVEN_OPTS="-Dmaven.repo.local=/root/.m2/repository \
    -Dhttp.socketTimeout=60000 \
    -Dhttp.connectionTimeout=60000 \
    -Dmaven.wagon.http.retryHandler.count=3"

# Create Maven settings
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' >> /root/.m2/settings.xml && \
    echo '    <profiles>' >> /root/.m2/settings.xml && \
    echo '        <profile>' >> /root/.m2/settings.xml && \
    echo '            <id>default</id>' >> /root/.m2/settings.xml && \
    echo '            <properties>' >> /root/.m2/settings.xml && \
    echo '                <maven.wagon.http.connectionTimeout>60000</maven.wagon.http.connectionTimeout>' >> /root/.m2/settings.xml && \
    echo '                <maven.wagon.http.readTimeout>60000</maven.wagon.http.readTimeout>' >> /root/.m2/settings.xml && \
    echo '            </properties>' >> /root/.m2/settings.xml && \
    echo '        </profile>' >> /root/.m2/settings.xml && \
    echo '    </profiles>' >> /root/.m2/settings.xml && \
    echo '    <activeProfiles>' >> /root/.m2/settings.xml && \
    echo '        <activeProfile>default</activeProfile>' >> /root/.m2/settings.xml && \
    echo '    </activeProfiles>' >> /root/.m2/settings.xml && \
    echo '</settings>' >> /root/.m2/settings.xml

# Copy all POMs first for better caching
COPY pom.xml .
COPY common/common-core/pom.xml common/common-core/
COPY common/common-security/pom.xml common/common-security/
COPY services/user-service/pom.xml services/user-service/

# Build parent POM
RUN mvn clean install -N -DskipTests || \
    (sleep 10 && mvn clean install -N -DskipTests)

# Copy and build common modules source
COPY common/common-core/src common/common-core/src
RUN mvn clean install -f common/common-core/pom.xml -DskipTests

COPY common/common-security/src common/common-security/src
RUN mvn clean install -f common/common-security/pom.xml -DskipTests

# Download service dependencies
WORKDIR /app/services/user-service
RUN mvn dependency:go-offline -B

# Copy and build service
COPY services/user-service/src src
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
COPY --from=builder --chown=appuser:appgroup /app/services/user-service/target/*.jar app.jar

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

EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Multi-stage build for company-service
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Configure Maven for better network reliability
ENV MAVEN_OPTS="-Dmaven.repo.local=/root/.m2/repository -Dhttp.socketTimeout=60000 -Dhttp.connectionTimeout=60000 -Dmaven.wagon.http.retryHandler.count=3"

# Copy parent POM and common modules first for better caching
COPY pom.xml .
COPY common/ common/

# Create Maven settings.xml for timeout configuration
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' >> /root/.m2/settings.xml && \
    echo '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' >> /root/.m2/settings.xml && \
    echo '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0' >> /root/.m2/settings.xml && \
    echo '          http://maven.apache.org/xsd/settings-1.0.0.xsd">' >> /root/.m2/settings.xml && \
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

# Build parent POM first with retry logic
RUN mvn clean install -N -DskipTests || \
    (sleep 10 && mvn clean install -N -DskipTests) || \
    (sleep 20 && mvn clean install -N -DskipTests)

# Build common modules with retry logic
RUN mvn clean install -f common/common-core/pom.xml -DskipTests || \
    (sleep 10 && mvn clean install -f common/common-core/pom.xml -DskipTests) || \
    (sleep 20 && mvn clean install -f common/common-core/pom.xml -DskipTests)

RUN mvn clean install -f common/common-security/pom.xml -DskipTests || \
    (sleep 10 && mvn clean install -f common/common-security/pom.xml -DskipTests) || \
    (sleep 20 && mvn clean install -f common/common-security/pom.xml -DskipTests)

# Copy service POM and download dependencies with retry
COPY services/company-service/pom.xml services/company-service/
WORKDIR /app/services/company-service
RUN mvn dependency:go-offline || \
    (sleep 10 && mvn dependency:go-offline) || \
    (sleep 20 && mvn dependency:go-offline)

# Copy source code and build the service independently
COPY services/company-service/src/ src/
RUN mvn clean package -DskipTests

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

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]

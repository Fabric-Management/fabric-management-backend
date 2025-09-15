# 🔧 Fabric Management System - Configuration Guide

## 📋 Overview

Bu dokümantasyon Fabric Management System'in konfigürasyon yapısını ve kullanımını açıklar. Sistem modern mikroservis mimarisine uygun, environment-aware ve güvenlik odaklı bir yapıya sahiptir.

## 🏗️ Configuration Architecture

### Environment-Based Configuration
```
.env                    # Development environment
.env.example            # Template for all environments
.env.prod               # Production environment
.env.staging            # Staging environment (optional)
```

### Service-Specific Configuration
```
services/
├── user-service/src/main/resources/application.yml
├── contact-service/src/main/resources/application.yml
└── company-service/src/main/resources/application.yml
```

## 🚀 Quick Start

### 1. Environment Setup
```bash
# Copy the example environment file
cp .env.example .env

# Edit with your actual values
nano .env
```

### 2. Required Environment Variables
```bash
# Database
POSTGRES_HOST=localhost
POSTGRES_PORT=5433
POSTGRES_DB=fabric_management
POSTGRES_USER=your_db_user
POSTGRES_PASSWORD=your_secure_password

# Security
JWT_SECRET=your_jwt_secret_key_here
GOOGLE_MAPS_API_KEY=your_google_maps_api_key
```

### 3. Start Services
```bash
# Development mode
docker-compose up -d

# Production mode
SPRING_PROFILES_ACTIVE=prod docker-compose -f docker-compose.yml up -d
```

## 📊 Configuration Profiles

### Local Development Profile (`local`)
- H2 in-memory database
- Mock geocoding service
- Debug logging enabled
- H2 console accessible
- Swagger UI enabled

### Test Profile (`test`)
- H2 test database
- Minimal logging
- Fast startup configuration
- Mock external services

### Production Profile (`prod`)
- PostgreSQL database
- Google Maps geocoding
- Optimized logging
- Security hardened
- Monitoring enabled

## 🔒 Security Configuration

### JWT Configuration
```yaml
spring:
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration: ${JWT_EXPIRATION:86400000}  # 24 hours
      refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}  # 7 days
```

### Database Security
```yaml
spring:
  datasource:
    hikari:
      data-source-properties:
        useSSL: true
        requireSSL: true
        verifyServerCertificate: true
```

## 📍 Geocoding Configuration

### Google Maps Setup
```yaml
geocoding:
  provider: google
  google:
    api-key: ${GOOGLE_MAPS_API_KEY}
    language: en
    region: GB
    timeout: 10000
    max-retries: 3
```

### Cache Configuration
```yaml
geocoding:
  cache:
    enabled: true
    ttl: 86400  # 24 hours
    max-size: 10000
    
  rate-limiting:
    enabled: true
    requests-per-second: 50
    daily-limit: 25000
```

## 🗄️ Database Configuration

### PostgreSQL Setup
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}_servicename
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    
    hikari:
      maximum-pool-size: ${DB_POOL_MAX_SIZE:20}
      minimum-idle: ${DB_POOL_MIN_SIZE:5}
      connection-timeout: ${DB_CONNECTION_TIMEOUT:20000}
```

### Flyway Migration
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    validate-on-migrate: true
    clean-disabled: true
```

## 📡 External Services

### Service Discovery
```yaml
external:
  services:
    user-service:
      url: http://localhost:${USER_SERVICE_PORT:8081}
      timeout: 5000
      retry:
        max-attempts: 3
        delay: 1000
```

### Message Queue (RabbitMQ)
```yaml
messaging:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
```

## 📊 Monitoring & Observability

### Actuator Configuration
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: ${PROMETHEUS_ENABLED:true}
```

### Logging Configuration
```yaml
logging:
  level:
    root: ${LOG_LEVEL:INFO}
    com.fabricmanagement: ${DEBUG_MODE:false} ? DEBUG : INFO
  pattern:
    console: ${LOG_PATTERN:%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n}
```

## 🐳 Docker Configuration

### Environment Variables in Docker
```yaml
services:
  contact-service:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-docker}
      POSTGRES_HOST: postgres-db
      REDIS_HOST: redis
      RABBITMQ_HOST: rabbitmq
      GOOGLE_MAPS_API_KEY: ${GOOGLE_MAPS_API_KEY}
```

### Health Checks
```yaml
healthcheck:
  test: ["CMD", "wget", "--spider", "http://localhost:8082/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

## 🔧 Troubleshooting

### Common Issues

1. **Database Connection Failed**
   ```bash
   # Check if PostgreSQL is running
   docker-compose ps postgres-db
   
   # Check connection
   docker-compose exec postgres-db psql -U fabric_admin -d fabric_management
   ```

2. **Service Discovery Issues**
   ```bash
   # Check service health
   curl http://localhost:8081/actuator/health
   curl http://localhost:8082/actuator/health
   ```

3. **Google Maps API Errors**
   ```bash
   # Verify API key
   curl "https://maps.googleapis.com/maps/api/geocode/json?address=London&key=${GOOGLE_MAPS_API_KEY}"
   ```

### Debug Mode
```bash
# Enable debug logging
export DEBUG_MODE=true
export LOG_LEVEL=DEBUG

# Restart services
docker-compose restart
```

## 📈 Performance Tuning

### Database Pool Optimization
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # Production
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
```

### JVM Tuning (Production)
```dockerfile
ENV JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC -XX:G1HeapRegionSize=16m"
```

## 🔐 Production Security Checklist

- [ ] Change all default passwords
- [ ] Use strong JWT secrets (min 256 bits)
- [ ] Enable SSL/TLS
- [ ] Configure firewall rules
- [ ] Set up monitoring and alerting
- [ ] Enable audit logging
- [ ] Regular security updates
- [ ] Backup strategy implemented

## 📚 Additional Resources

- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html)
- [Docker Compose Reference](https://docs.docker.com/compose/compose-file/)
- [Microservices Patterns](https://microservices.io/patterns/)
- [Google Maps Geocoding API](https://developers.google.com/maps/documentation/geocoding)

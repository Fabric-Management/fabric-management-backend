# 🚀 Yeni Mikroservis/Modül Entegrasyon Kılavuzu

## 📋 İçindekiler

1. [Genel Bakış](#genel-bakış)
2. [Önkoşullar](#önkoşullar)
3. [Adım Adım Entegrasyon](#adım-adım-entegrasyon)
4. [Dockerfile Yapılandırması](#dockerfile-yapılandırması)
5. [Docker Compose Entegrasyonu](#docker-compose-entegrasyonu)
6. [Ortam Değişkenleri](#ortam-değişkenleri)
7. [API Gateway Rotası Ekleme](#api-gateway-rotası-ekleme)
8. [Doğrulama ve Test](#doğrulama-ve-test)
9. [Checklist](#checklist)

---

## 🎯 Genel Bakış

Bu kılavuz, Fabric Management System'e yeni bir mikroservis veya modül eklerken izlenmesi gereken standart prosedürleri içerir. **DRY (Don't Repeat Yourself)** ve **KISS (Keep It Simple, Stupid)** prensiplerine uygun olarak tasarlanmıştır.

### Temel Prensipler

- ✅ **Tek Dockerfile**: Tüm servisler için `Dockerfile.service` kullanılır
- ✅ **Tutarlı Konfigürasyon**: ENV değişkenleri `.env` ve `.env.example` dosyalarında yönetilir
- ✅ **Standart Port Yapısı**: Her servis için belirli port aralıkları
- ✅ **Docker Compose DRY**: Shared configuration blocks kullanımı
- ✅ **API Gateway Routing**: Merkezi yönlendirme

---

## ✅ Önkoşullar

### Gerekli Bilgiler

Başlamadan önce aşağıdaki bilgileri belirleyin:

```yaml
Servis Adı: order-service          # Kebab-case
Port: 8084                          # 8080-8099 arası
JMX Port: 9014                      # 9010-9029 arası
Context Path: /api/v1/orders        # REST API base path
Dependencies:                       # Diğer servislere bağımlılıklar
  - user-service
  - company-service
Database: order_db                  # Gerekiyorsa
Kafka Topics:                       # Event-driven iletişim
  - order-events
  - payment-events
```

### Proje Yapısı Bilgisi

```
services/
  └── order-service/               # Yeni servis klasörü
      ├── pom.xml                  # Maven konfigürasyonu
      └── src/
          └── main/
              ├── java/
              │   └── com/fabricmanagement/order/
              │       ├── OrderServiceApplication.java
              │       ├── api/            # Controllers
              │       ├── application/    # Services, DTOs
              │       ├── domain/         # Entities, Events
              │       └── infrastructure/ # Repositories, Clients
              └── resources/
                  ├── application.yml
                  └── application-docker.yml
```

---

## 📝 Adım Adım Entegrasyon

### 1. Maven Modül Oluşturma

#### a) Root `pom.xml` Güncelleme

```xml
<!-- fabric-management-backend/pom.xml -->
<modules>
    <!-- Existing modules -->
    <module>services/api-gateway</module>
    <module>services/user-service</module>
    <module>services/contact-service</module>
    <module>services/company-service</module>

    <!-- ✅ YENİ: Order Service -->
    <module>services/order-service</module>

    <!-- Shared modules -->
    <module>shared/shared-domain</module>
    <module>shared/shared-application</module>
    <module>shared/shared-infrastructure</module>
    <module>shared/shared-security</module>
</modules>
```

#### b) Servis `pom.xml` Oluşturma

```xml
<!-- services/order-service/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.fabricmanagement</groupId>
        <artifactId>fabric-management-backend</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>order-service</artifactId>
    <name>Order Service</name>
    <description>Order management microservice</description>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Shared Modules -->
        <dependency>
            <groupId>com.fabricmanagement</groupId>
            <artifactId>shared-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fabricmanagement</groupId>
            <artifactId>shared-infrastructure</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>

        <!-- Other dependencies as needed -->
    </dependencies>
</project>
```

### 2. Application Configuration

#### a) `application.yml` Oluşturma

```yaml
# services/order-service/src/main/resources/application.yml
# =============================================================================
# FABRIC MANAGEMENT SYSTEM - ORDER SERVICE CONFIGURATION
# =============================================================================

spring:
  application:
    name: order-service
  profiles:
    active: local

  # Database Configuration
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5433}/${POSTGRES_DB:fabric_management}
    username: ${POSTGRES_USER:fabric_user}
    password: ${POSTGRES_PASSWORD:fabric_password}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000

  jpa:
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        show_sql: false
    show-sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true

  # Redis Cache Configuration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

  cache:
    type: redis
    redis:
      time-to-live: 600000 # 10 minutes

  # Kafka Configuration
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      client-id: ${spring.application.name}-producer-${HOSTNAME:localhost}
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      enable-idempotence: true
    consumer:
      client-id: ${spring.application.name}-consumer-${HOSTNAME:localhost}
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: order-service-group
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        spring.json.trusted.packages: "com.fabricmanagement.order.domain.event"
        allow.auto.create.topics: true
        jmx.prefix: order-service

# Server Configuration
server:
  port: 8084
  servlet:
    context-path: /api/v1/orders

# External Services
user-service:
  url: ${USER_SERVICE_URL:http://localhost:8081}

company-service:
  url: ${COMPANY_SERVICE_URL:http://localhost:8083}

# Resilience4j
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        slidingWindowSize: 10
      company-service:
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
  retry:
    instances:
      user-service:
        maxAttempts: 3
        waitDuration: 1s
      company-service:
        maxAttempts: 3

# Management & Monitoring
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true

# OpenAPI/Swagger
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    operationsSorter: method

# Logging
logging:
  level:
    com.fabricmanagement.order: INFO
    org.springframework.security: INFO
    org.springframework.web: INFO
    org.hibernate.SQL: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/order-service.log
```

#### b) `application-docker.yml` Oluşturma

```yaml
# services/order-service/src/main/resources/application-docker.yml
# =============================================================================
# FABRIC MANAGEMENT SYSTEM - ORDER SERVICE DOCKER CONFIGURATION
# =============================================================================

spring:
  application:
    name: order-service

  # Database Configuration (Docker overrides)
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:postgres}:${POSTGRES_PORT:5432}/${POSTGRES_DB:fabric_management}
    username: ${POSTGRES_USER:fabric_user}
    password: ${POSTGRES_PASSWORD:fabric_password}

  flyway:
    clean-disabled: true # Security: Prevent data loss
    repair-on-migrate: false

  # Redis Configuration (Docker overrides)
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      connect-timeout: 3000ms

  # Kafka Configuration (Docker overrides)
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9093}
    producer:
      client-id: order-service
    consumer:
      client-id: order-service

# Logging (Docker overrides)
logging:
  level:
    com.fabricmanagement.order: INFO
    org.springframework.security: WARN
    org.springframework.web: WARN
    org.hibernate.SQL: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  # File logging disabled in Docker - logs go to stdout/stderr
```

---

## 🐳 Dockerfile Yapılandırması

### Önemli: Universal Dockerfile Kullanımı

**❌ YAPMAYIN**: Her servis için ayrı Dockerfile oluşturmayın

**✅ YAPIN**: Mevcut `Dockerfile.service` dosyasını kullanın

```dockerfile
# ❌ YANLIŞ: services/order-service/Dockerfile oluşturmayın
# Bu DRY prensibine aykırıdır ve bakım maliyetini artırır

# ✅ DOĞRU: Root'taki Dockerfile.service zaten tüm servisleri destekliyor
# /fabric-management-backend/Dockerfile.service
```

### Dockerfile.service Nasıl Çalışır?

Universal `Dockerfile.service` build argumentları ile çalışır:

```dockerfile
# Dockerfile.service (zaten mevcut)
ARG SERVICE_NAME    # Örnek: order-service
ARG SERVICE_PORT    # Örnek: 8084

# Build sadece belirtilen servisi compile eder
RUN mvn clean package -pl services/${SERVICE_NAME} -am -DskipTests -B

# Runtime için JAR'ı kopyalar
COPY --from=build /build/services/${SERVICE_NAME}/target/${SERVICE_NAME}-1.0.0-SNAPSHOT.jar app.jar
```

### Manuel Build (Gerekirse)

```bash
# Yeni servis için Docker image build
docker build -f Dockerfile.service \
  --build-arg SERVICE_NAME=order-service \
  --build-arg SERVICE_PORT=8084 \
  -t fabric-order-service:latest .
```

---

## 🔧 Docker Compose Entegrasyonu

### 1. docker-compose-complete.yml Güncelleme

```yaml
# docker-compose-complete.yml
services:
  # ... existing services ...

  # ===========================================================================
  # ORDER SERVICE
  # ===========================================================================
  order-service:
    build:
      context: .
      dockerfile: Dockerfile.service
      args:
        SERVICE_NAME: order-service
        SERVICE_PORT: 8084
    image: fabric-order-service:latest
    container_name: fabric-order-service
    hostname: order-service-instance
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy
      user-service:
        condition: service_healthy
      company-service:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      POSTGRES_HOST: postgres
      POSTGRES_PORT: 5432
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:-}
      KAFKA_BOOTSTRAP_SERVERS: kafka:9093
      JWT_SECRET: ${JWT_SECRET}
      JMX_PORT: 9014
      USER_SERVICE_URL: http://user-service:8081
      COMPANY_SERVICE_URL: http://company-service:8083
      SERVER_PORT: 8084
    ports:
      - "${ORDER_SERVICE_PORT:-8084}:8084"
      - "${ORDER_SERVICE_JMX_PORT:-9014}:9014"
    networks:
      - fabric-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8084/api/v1/orders/actuator/health"]
      <<: *healthcheck-defaults
      start_period: 90s
    logging: *default-logging
    deploy:
      resources:
        limits:
          memory: 1024M
          cpus: "1.0"
        reservations:
          memory: 512M
          cpus: "0.5"
```

### 2. DRY Prensipleri

Docker Compose'da kod tekrarını önlemek için shared configuration blocks kullanın:

```yaml
# docker-compose-complete.yml üst kısmında
x-logging: &default-logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "3"

x-healthcheck-defaults: &healthcheck-defaults
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s

# Sonra servislerde kullanın:
services:
  order-service:
    logging: *default-logging
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8084/actuator/health"]
      <<: *healthcheck-defaults
```

---

## 🔐 Ortam Değişkenleri

### 1. `.env.example` Güncelleme

```bash
# .env.example dosyasına ekleyin

# =============================================================================
# MICROSERVICES - PORTS
# =============================================================================
USER_SERVICE_PORT=8081
CONTACT_SERVICE_PORT=8082
COMPANY_SERVICE_PORT=8083
ORDER_SERVICE_PORT=8084          # ✅ YENİ

# =============================================================================
# MICROSERVICES - INTER-SERVICE COMMUNICATION
# =============================================================================
USER_SERVICE_URL=http://localhost:8081
CONTACT_SERVICE_URL=http://localhost:8082
COMPANY_SERVICE_URL=http://localhost:8083
ORDER_SERVICE_URL=http://localhost:8084    # ✅ YENİ

# =============================================================================
# MONITORING - JMX PORTS
# =============================================================================
USER_SERVICE_JMX_PORT=9011
CONTACT_SERVICE_JMX_PORT=9012
COMPANY_SERVICE_JMX_PORT=9013
ORDER_SERVICE_JMX_PORT=9014      # ✅ YENİ
```

### 2. `.env` Güncelleme

```bash
# .env dosyanıza aynı değişkenleri ekleyin
# NOT: Bu dosya .gitignore'dadır, gerçek değerler içerir

ORDER_SERVICE_PORT=8084
ORDER_SERVICE_URL=http://localhost:8084
ORDER_SERVICE_JMX_PORT=9014
```

### 3. Ortam Değişkenleri Standardı

| Kategori | Format | Örnek |
|----------|--------|-------|
| Port | `{SERVICE}_PORT` | `ORDER_SERVICE_PORT=8084` |
| URL | `{SERVICE}_URL` | `ORDER_SERVICE_URL=http://localhost:8084` |
| JMX | `{SERVICE}_JMX_PORT` | `ORDER_SERVICE_JMX_PORT=9014` |
| Host | `{SERVICE}_HOST` | `ORDER_SERVICE_HOST=localhost` |

---

## 🌐 API Gateway Rotası Ekleme

### 1. Gateway application.yml Güncelleme

```yaml
# services/api-gateway/src/main/resources/application.yml
spring:
  cloud:
    gateway:
      routes:
        # ... existing routes ...

        # ✅ YENİ: Order Service Routes
        - id: order-service
          uri: ${ORDER_SERVICE_URL:http://localhost:8084}
          predicates:
            - Path=/api/v1/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderServiceCircuitBreaker
                fallbackUri: forward:/fallback/order-service
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100
```

### 2. Gateway application-docker.yml Güncelleme

```yaml
# services/api-gateway/src/main/resources/application-docker.yml
spring:
  cloud:
    gateway:
      routes:
        # ... existing routes ...

        # ✅ YENİ: Order Service Routes (Docker)
        - id: order-service
          uri: ${ORDER_SERVICE_URL:http://order-service:8084}
          predicates:
            - Path=/api/v1/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderServiceCircuitBreaker
                fallbackUri: forward:/fallback/order-service
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100
```

### 3. Resilience4j Circuit Breaker Konfigürasyonu

```yaml
# services/api-gateway/src/main/resources/application-docker.yml
resilience4j:
  circuitbreaker:
    configs:
      default:
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        slidingWindowSize: 100
    instances:
      userServiceCircuitBreaker:
        baseConfig: default
      companyServiceCircuitBreaker:
        baseConfig: default
      contactServiceCircuitBreaker:
        baseConfig: default
      orderServiceCircuitBreaker:    # ✅ YENİ
        baseConfig: default

  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 1s
```

### 4. Fallback Controller (Opsiyonel)

```java
// services/api-gateway/src/main/java/com/fabricmanagement/gateway/controller/FallbackController.java
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/order-service")
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SERVICE_UNAVAILABLE");
        response.put("message", "Order Service is temporarily unavailable");
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
```

---

## ✅ Doğrulama ve Test

### 1. Derleme Testi

```bash
# Root dizinden Maven build
mvn clean install -DskipTests

# Sadece yeni servis
mvn clean install -pl services/order-service -am -DskipTests
```

### 2. Docker Build Testi

```bash
# Docker image oluşturma
docker build -f Dockerfile.service \
  --build-arg SERVICE_NAME=order-service \
  --build-arg SERVICE_PORT=8084 \
  -t fabric-order-service:latest .
```

### 3. Docker Compose Testi

```bash
# Sadece yeni servisi başlat
docker-compose -f docker-compose-complete.yml up -d order-service

# Logları kontrol et
docker logs fabric-order-service -f

# Health check
curl http://localhost:8084/api/v1/orders/actuator/health
```

### 4. API Gateway Üzerinden Test

```bash
# Gateway üzerinden erişim
curl http://localhost:8080/api/v1/orders/actuator/health

# Gateway route kontrolü
curl http://localhost:8080/actuator/gateway/routes | jq '.[] | select(.route_id=="order-service")'
```

### 5. Inter-Service Communication Test

```bash
# Feign client testi (eğer varsa)
curl -X GET http://localhost:8084/api/v1/orders/test-connection
```

### 6. Kafka Integration Test (Eğer kullanılıyorsa)

```bash
# Kafka consumer group kontrolü
docker exec fabric-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --list | grep order-service-group
```

---

## 📋 Entegrasyon Checklist

### Geliştirme Aşaması

- [ ] Maven modül oluşturuldu (`pom.xml`)
- [ ] Root `pom.xml`'e modül eklendi
- [ ] Servis ana sınıfı oluşturuldu (`OrderServiceApplication.java`)
- [ ] Clean Architecture katmanları oluşturuldu (api, application, domain, infrastructure)
- [ ] `application.yml` oluşturuldu
- [ ] `application-docker.yml` oluşturuldu
- [ ] Flyway migration scripts eklendi (gerekirse)
- [ ] Unit testler yazıldı

### Docker Yapılandırması

- [ ] **Dockerfile oluşturulmadı** (Universal `Dockerfile.service` kullanılıyor)
- [ ] `docker-compose-complete.yml`'e servis eklendi
- [ ] Shared configuration blocks kullanıldı (`*default-logging`, `*healthcheck-defaults`)
- [ ] Health check endpoint doğru yapılandırıldı
- [ ] Resource limits (memory, CPU) ayarlandı
- [ ] Network yapılandırması doğru
- [ ] Dependencies (depends_on) tanımlandı

### Ortam Değişkenleri

- [ ] `.env.example`'a port eklendi
- [ ] `.env.example`'a URL eklendi
- [ ] `.env.example`'a JMX port eklendi
- [ ] `.env` dosyası güncellendi (local development)
- [ ] Naming convention'a uyuldu (`{SERVICE}_PORT`, `{SERVICE}_URL`)
- [ ] Default değerler ayarlandı (`:` ile fallback)

### API Gateway

- [ ] Gateway `application.yml`'e route eklendi
- [ ] Gateway `application-docker.yml`'e route eklendi
- [ ] Circuit breaker konfigürasyonu eklendi
- [ ] Rate limiter ayarları yapıldı
- [ ] Fallback endpoint oluşturuldu (opsiyonel)
- [ ] Gateway environment variables güncellendi

### Dokümantasyon

- [ ] Servis README.md oluşturuldu
- [ ] API endpoint dokümantasyonu yazıldı
- [ ] Swagger/OpenAPI yapılandırıldı
- [ ] Inter-service communication dokümante edildi
- [ ] Event/Kafka topic'leri dokümante edildi

### Test ve Doğrulama

- [ ] Maven build başarılı
- [ ] Docker image build başarılı
- [ ] Docker Compose ile servis ayağa kalkıyor
- [ ] Health check endpoint çalışıyor
- [ ] Gateway üzerinden erişim sağlanıyor
- [ ] Database bağlantısı çalışıyor
- [ ] Redis cache çalışıyor
- [ ] Kafka integration çalışıyor (gerekirse)
- [ ] Feign client'lar çalışıyor (gerekirse)
- [ ] JMX monitoring erişilebilir

### Production Hazırlık

- [ ] Logging seviyeleri production için ayarlandı
- [ ] Security yapılandırması tamamlandı
- [ ] Performance tuning yapıldı
- [ ] Backup stratejisi belirlendi
- [ ] Monitoring dashboards eklendi
- [ ] Alert rules tanımlandı

---

## 🎯 Best Practices

### DO's ✅

1. **Universal Dockerfile Kullan**
   - Tek `Dockerfile.service` tüm servisleri destekler
   - DRY prensibine uygun

2. **ENV Değişkenlerini Merkezi Yönet**
   - `.env.example` template olarak
   - `.env` local development için

3. **Shared Configuration Blocks Kullan**
   ```yaml
   x-logging: &default-logging
   x-healthcheck: &healthcheck-defaults
   ```

4. **Tutarlı Naming Convention**
   ```bash
   {SERVICE}_PORT
   {SERVICE}_URL
   {SERVICE}_JMX_PORT
   ```

5. **Circuit Breaker ve Retry Ekle**
   - Resilience4j ile fault tolerance
   - API Gateway level'da

6. **Health Checks Ekle**
   - Spring Actuator `/actuator/health`
   - Docker healthcheck ile otomatik restart

### DON'Ts ❌

1. **❌ Her Servis İçin Ayrı Dockerfile Oluşturma**
   - Boilerplate kod tekrarı
   - Bakım maliyeti artar

2. **❌ Hardcoded Değerler**
   ```yaml
   # ❌ YANLIŞ
   uri: http://localhost:8084

   # ✅ DOĞRU
   uri: ${ORDER_SERVICE_URL:http://localhost:8084}
   ```

3. **❌ Port Çakışmaları**
   - Her servis unique port kullanmalı
   - 8080-8099: Servisler
   - 9010-9029: JMX

4. **❌ Dokümantasyon Eksikliği**
   - Her servis README.md içermeli
   - API endpoints dokümante edilmeli

5. **❌ Test Etmeden Production**
   - Local test
   - Docker test
   - Integration test

---

## 🔍 Troubleshooting

### Sorun: Servis Başlamıyor

```bash
# 1. Container loglarını kontrol et
docker logs fabric-order-service --tail 100

# 2. Health check durumunu kontrol et
docker inspect fabric-order-service | jq '.[0].State.Health'

# 3. Port çakışması kontrolü
netstat -tulpn | grep 8084

# 4. Environment variables kontrolü
docker exec fabric-order-service env | grep ORDER
```

### Sorun: Database Bağlantısı Başarısız

```bash
# 1. PostgreSQL hazır mı?
docker exec fabric-postgres pg_isready

# 2. Database var mı?
docker exec fabric-postgres psql -U fabric_user -d fabric_management -c "SELECT 1"

# 3. Network bağlantısı
docker exec fabric-order-service ping -c 3 postgres
```

### Sorun: Gateway Route Çalışmıyor

```bash
# 1. Gateway routes kontrolü
curl http://localhost:8080/actuator/gateway/routes | jq

# 2. Order service erişilebilir mi?
docker exec fabric-api-gateway ping -c 3 order-service

# 3. Gateway logs
docker logs fabric-api-gateway | grep order-service
```

### Sorun: Kafka Bağlantısı Yok

```bash
# 1. Kafka broker erişilebilir mi?
docker exec fabric-order-service nc -zv kafka 9093

# 2. Consumer group var mı?
docker exec fabric-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --list | grep order

# 3. Topic oluşturuldu mu?
docker exec fabric-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list | grep order
```

---

## 📚 İlgili Dokümantasyon

- [PRINCIPLES.md](../development/PRINCIPLES.md) - Geliştirme prensipleri
- [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Deployment rehberi
- [PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md) - Proje yapısı
- [API_GATEWAY_SETUP.md](./API_GATEWAY_SETUP.md) - API Gateway konfigürasyonu

---

## 🎓 Örnek Servis Entegrasyonları

Referans olarak mevcut servislere bakabilirsiniz:

- **User Service**: `services/user-service/`
- **Contact Service**: `services/contact-service/`
- **Company Service**: `services/company-service/`
- **API Gateway**: `services/api-gateway/`

Her servis bu kılavuza uygun şekilde yapılandırılmıştır.

---

**Son Güncelleme:** 2025-10-03
**Versiyon:** 1.0.0
**Hazırlayan:** DevOps & Architecture Team

> **Not**: Bu kılavuz, projedeki tüm mevcut servislerin analizi sonucu hazırlanmıştır ve DRY, KISS prensipleriyle tam uyumludur. Yeni bir servis eklerken bu kılavuzu takip ederek tutarlılığı koruyabilirsiniz.

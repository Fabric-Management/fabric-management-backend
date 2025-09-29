# Fabric Management System - Optimized Microservice Architecture Protocol

## 📋 Executive Summary

Bu dokümantasyon, Fabric Management System'in mevcut mikroservis yapısını analiz ederek, **en iyi uyumluluk ve performans** için optimize edilmiş protokol önerilerini sunar. Analiz sonucunda tespit edilen sorunlar ve çözüm önerileri detaylı olarak açıklanmıştır.

## 🔍 Current State Analysis

### ✅ **Güçlü Yönler**

1. **Temiz Mimari**: DDD ve Clean Architecture prensiplerini doğru uygulamış
2. **Common Modules**: Minimalist yaklaşım ile over-engineering'den kaçınmış
3. **Teknoloji Stack**: Modern ve kurumsal teknolojiler (Spring Boot 3.5.5, Java 21)
4. **Dokümantasyon**: Kapsamlı ve organize dokümantasyon yapısı

### ⚠️ **Tespit Edilen Sorunlar**

1. **Port Çakışması**: Contact Service (8083/8084) ve Company Service (8084/8083)
2. **Dependency Inconsistency**: Servisler arası bağımlılık tutarsızlıkları
3. **Configuration Mismatch**: application.yml vs docker-compose.yml uyumsuzluğu
4. **Missing Infrastructure**: API Gateway, Service Discovery, Monitoring eksik
5. **Security Gaps**: Default JWT secrets, merkezi authentication eksik

## 🏗️ Optimized Architecture Protocol

### **1. Service Port Allocation Strategy**

```yaml
# Standardized Port Allocation
Core Services:
  - Identity Service: 8081 ✅
  - User Service: 8082 ✅
  - Contact Service: 8083 ✅ (FIXED)
  - Company Service: 8084 ✅ (FIXED)

HR Services:
  - HR Service: 8085
  - Payroll Service: 8086
  - Leave Service: 8087
  - Performance Service: 8088

Inventory Services:
  - Inventory Service: 8089
  - Catalog Service: 8090
  - Pricing Service: 8091
  - Procurement Service: 8092
  - Quality Control Service: 8093

Business Services:
  - Order Service: 8094
  - Logistics Service: 8095
  - Production Service: 8096

Financial Services:
  - Accounting Service: 8097
  - Invoice Service: 8098
  - Payment Service: 8099
  - Billing Service: 8100

AI & Analytics:
  - AI Service: 8101
  - Reporting Service: 8102
  - Notification Service: 8103

Infrastructure:
  - API Gateway: 8080
  - PostgreSQL: 5433
  - Redis: 6379
  - Kafka: 9092
  - Zookeeper: 2181
  - Prometheus: 9090
  - Grafana: 3000
  - Jaeger: 16686
  - Eureka: 8761
```

### **2. Common Modules Optimization**

#### **Current Structure Analysis**

```java
// ✅ KEEP - Essential Components
common-core/
├── domain/
│   ├── base/BaseEntity.java          // Audit, UUID, Soft Delete
│   └── exception/                     // Domain exceptions
├── application/
│   └── dto/ApiResponse.java          // Standardized responses
└── infrastructure/
    └── web/exception/GlobalExceptionHandler.java

common-security/
├── jwt/                              // JWT utilities
├── context/SecurityContextUtil.java  // Security context
└── exception/                        // Security exceptions
```

#### **Optimized Common Modules Protocol**

```java
// Enhanced BaseEntity with Multi-tenancy
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;  // ✅ ADDED: Multi-tenancy support

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    @Version
    private Long version;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;

    // Enhanced methods
    public void markAsDeleted() { this.deleted = Boolean.TRUE; }
    public void restore() { this.deleted = Boolean.FALSE; }
    public boolean isDeleted() { return Boolean.TRUE.equals(this.deleted); }
    public boolean belongsToTenant(UUID tenantId) { return this.tenantId.equals(tenantId); }
}

// Enhanced ApiResponse with Pagination
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private List<String> errors;
    private String errorCode;
    private LocalDateTime timestamp;
    private PageInfo pageInfo;  // ✅ ADDED: Pagination support

    @Data
    @Builder
    public static class PageInfo {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrevious;
    }
}
```

### **3. Service Communication Protocol**

#### **Synchronous Communication (REST)**

```yaml
# Standardized API Patterns
API Standards:
  Base URL: /api/v1/{service-name}
  Versioning: /api/v1/, /api/v2/
  Headers:
    - X-Tenant-ID: UUID (Required)
    - X-Request-ID: UUID (Optional)
    - Authorization: Bearer {JWT} (Required)
    - Content-Type: application/json
    - Accept: application/json

Response Format:
  Success: ApiResponse<T>
  Error: ApiResponse<Void>
  Pagination: ApiResponse<PageResponse<T>>

HTTP Status Codes:
  - 200: OK
  - 201: Created
  - 400: Bad Request
  - 401: Unauthorized
  - 403: Forbidden
  - 404: Not Found
  - 409: Conflict
  - 422: Validation Error
  - 500: Internal Server Error
```

#### **Asynchronous Communication (Events)**

```java
// Standardized Event Structure
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainEvent {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private UUID tenantId;
    private String userId;
    private LocalDateTime occurredOn;
    private String version;
    private Map<String, Object> data;
    private Map<String, Object> metadata;
}

// Event Types by Service
Event Types:
  User Service:
    - UserCreatedEvent
    - UserUpdatedEvent
    - UserActivatedEvent
    - UserDeactivatedEvent

  Contact Service:
    - ContactCreatedEvent
    - ContactUpdatedEvent
    - ContactDeletedEvent

  Company Service:
    - CompanyCreatedEvent
    - CompanyUpdatedEvent
    - CompanyActivatedEvent
```

### **4. Security Protocol Enhancement**

#### **JWT Token Structure**

```java
// Enhanced JWT Claims
@Data
@Builder
public class JwtClaims {
    private String userId;
    private String username;
    private String email;
    private UUID tenantId;
    private String role;
    private List<String> permissions;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private String issuer;
    private String audience;
}

// Security Configuration Protocol
Security Standards:
  JWT Secret: Environment variable (JWT_SECRET)
  Token Expiration: 24 hours (86400000 ms)
  Refresh Token: 7 days (604800000 ms)
  Password Policy:
    - Min Length: 8 characters
    - Max Length: 128 characters
    - Must contain: uppercase, lowercase, number, special char
  Account Lockout:
    - Max Failed Attempts: 5
    - Lock Duration: 30 minutes
```

### **5. Database Protocol**

#### **Multi-Tenant Database Strategy**

```sql
-- Tenant Isolation Strategy
-- Option 1: Row-Level Security (Recommended)
CREATE POLICY tenant_isolation ON users
    FOR ALL TO application_role
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

-- Option 2: Schema per Tenant (Alternative)
-- CREATE SCHEMA tenant_{tenant_id};

-- Standardized Table Structure
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,

    CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username),
    CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email)
);
```

### **6. Configuration Management Protocol**

#### **Environment-Specific Configuration**

```yaml
# application.yml Template
spring:
  application:
    name: ${SERVICE_NAME}
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5433}/${POSTGRES_DB:fabric_management}
    username: ${POSTGRES_USER:fabric_user}
    password: ${POSTGRES_PASSWORD:fabric_password}
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:10}
      minimum-idle: ${DB_MIN_IDLE:2}
      connection-timeout: ${DB_CONNECTION_TIMEOUT:20000}
      idle-timeout: ${DB_IDLE_TIMEOUT:300000}
      max-lifetime: ${DB_MAX_LIFETIME:1200000}

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    timeout: ${REDIS_TIMEOUT:2000ms}

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: ${SERVICE_NAME}
      auto-offset-reset: earliest
      enable-auto-commit: false
    producer:
      client-id: ${SERVICE_NAME}
      acks: all
      retries: 3
      enable-idempotence: true

server:
  port: ${SERVER_PORT:8081}
  servlet:
    context-path: /api/v1/${SERVICE_NAME}

# Service-specific configuration
app:
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration: ${JWT_EXPIRATION:86400000}
      refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}

  tenant:
    isolation-strategy: ${TENANT_ISOLATION:row_level} # row_level, schema_per_tenant

  monitoring:
    enabled: ${MONITORING_ENABLED:true}
    metrics:
      enabled: ${METRICS_ENABLED:true}
    tracing:
      enabled: ${TRACING_ENABLED:true}
```

### **7. API Gateway Protocol**

#### **Spring Cloud Gateway Configuration**

```yaml
# Gateway Configuration
spring:
  cloud:
    gateway:
      routes:
        # Identity Service
        - id: identity-service
          uri: lb://identity-service
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200

        # User Service
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: user-service
                fallbackUri: forward:/fallback/user-service

        # Contact Service
        - id: contact-service
          uri: lb://contact-service
          predicates:
            - Path=/api/v1/contacts/**
          filters:
            - StripPrefix=1

        # Company Service
        - id: company-service
          uri: lb://company-service
          predicates:
            - Path=/api/v1/companies/**
          filters:
            - StripPrefix=1

      globalcors:
        cors-configurations:
          "[/**]":
            allowedOrigins: "*"
            allowedMethods: "*"
            allowedHeaders: "*"
            allowCredentials: true

      default-filters:
        - name: RequestHeader
          args:
            name: X-Gateway-Request
            value: true
```

### **8. Monitoring & Observability Protocol**

#### **Micrometer + Prometheus Configuration**

```yaml
# Monitoring Configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,env
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
      slo:
        http.server.requests: 50ms, 100ms, 200ms, 500ms

# Custom Metrics
app:
  metrics:
    custom:
      enabled: true
      business-metrics: true
      performance-metrics: true
```

#### **Distributed Tracing with Jaeger**

```yaml
# Tracing Configuration
management:
  tracing:
    sampling:
      probability: 1.0
    zipkin:
      endpoint: http://jaeger:14268/api/v2/spans

# Custom Trace Configuration
app:
  tracing:
    enabled: true
    service-name: ${SERVICE_NAME}
    sampling-rate: 0.1
    custom-tags:
      - tenant-id
      - user-id
      - request-id
```

### **9. Testing Protocol**

#### **Test Structure Standardization**

```java
// Test Configuration Protocol
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

### **10. Deployment Protocol**

#### **Docker Multi-Stage Build**

```dockerfile
# Optimized Dockerfile Template
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy POMs for dependency caching
COPY pom.xml .
COPY common/common-core/pom.xml common/common-core/
COPY common/common-security/pom.xml common/common-security/
COPY services/${SERVICE_NAME}/pom.xml services/${SERVICE_NAME}/

# Build common modules
RUN mvn clean install -N -DskipTests
RUN mvn clean install -f common/common-core/pom.xml -DskipTests
RUN mvn clean install -f common/common-security/pom.xml -DskipTests

# Build service
COPY services/${SERVICE_NAME}/src services/${SERVICE_NAME}/src
RUN mvn clean package -f services/${SERVICE_NAME}/pom.xml -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /app/services/${SERVICE_NAME}/target/*.jar app.jar

USER appuser

EXPOSE ${SERVER_PORT}

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### **Kubernetes Deployment Template**

```yaml
# Kubernetes Deployment Template
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${SERVICE_NAME}
  namespace: fabric-management
  labels:
    app: ${SERVICE_NAME}
    version: v1
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ${SERVICE_NAME}
  template:
    metadata:
      labels:
        app: ${SERVICE_NAME}
        version: v1
    spec:
      serviceAccountName: fabric-service-account
      containers:
        - name: ${SERVICE_NAME}
          image: fabric-management/${SERVICE_NAME}:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: ${SERVER_PORT}
              name: http
          env:
            - name: SPRING_PROFILES_ACTIVE
              valueFrom:
                configMapKeyRef:
                  name: fabric-management-config
                  key: SPRING_PROFILES_ACTIVE
            - name: SERVER_PORT
              value: "${SERVER_PORT}"
            - name: POSTGRES_HOST
              valueFrom:
                configMapKeyRef:
                  name: fabric-management-config
                  key: POSTGRES_HOST
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: fabric-management-secrets
                  key: JWT_SECRET
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: ${SERVER_PORT}
            initialDelaySeconds: 60
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: ${SERVER_PORT}
            initialDelaySeconds: 30
            periodSeconds: 10
```

## 🎯 Implementation Roadmap

### **Phase 1: Critical Fixes (Week 1)**

1. **Port Conflict Resolution**

   - Fix Contact Service port: 8083
   - Fix Company Service port: 8084
   - Update all configuration files
   - Test service communication

2. **Configuration Standardization**
   - Implement environment-based configuration
   - Standardize application.yml templates
   - Update docker-compose.yml
   - Update Kubernetes configurations

### **Phase 2: Infrastructure Enhancement (Week 2)**

1. **API Gateway Implementation**

   - Spring Cloud Gateway setup
   - Route configuration
   - Rate limiting
   - Circuit breakers

2. **Service Discovery**
   - Eureka Server implementation
   - Service registration
   - Health checks

### **Phase 3: Security Hardening (Week 3)**

1. **JWT Enhancement**

   - Secure JWT secret management
   - Token refresh mechanism
   - Multi-tenant JWT claims

2. **Authentication Service**
   - Centralized authentication
   - OAuth2/OpenID Connect
   - Role-based access control

### **Phase 4: Monitoring & Observability (Week 4)**

1. **Metrics Collection**

   - Prometheus setup
   - Custom business metrics
   - Performance monitoring

2. **Distributed Tracing**
   - Jaeger implementation
   - Request tracing
   - Performance analysis

### **Phase 5: Service Implementation (Weeks 5-12)**

1. **HR Services** (Weeks 5-6)
2. **Inventory Services** (Weeks 7-8)
3. **Business Services** (Weeks 9-10)
4. **Financial Services** (Weeks 11-12)

## 📊 Quality Metrics

### **Code Quality Standards**

```yaml
Code Coverage: >= 80%
Cyclomatic Complexity: <= 10
Technical Debt Ratio: <= 5%
Code Duplication: <= 3%
Security Vulnerabilities: 0 Critical, 0 High
Performance:
  - API Response Time: < 200ms (95th percentile)
  - Database Query Time: < 100ms (95th percentile)
  - Memory Usage: < 512MB per service
  - CPU Usage: < 50% per service
```

### **Monitoring KPIs**

```yaml
Availability: 99.9%
Error Rate: < 0.1%
Response Time: < 200ms
Throughput: > 1000 requests/second
Database Connections: < 80% of pool size
Cache Hit Rate: > 90%
```

## 🔧 Development Guidelines

### **Service Development Protocol**

1. **Always extend BaseEntity** for domain entities
2. **Use ApiResponse** for all API responses
3. **Implement proper exception handling** with GlobalExceptionHandler
4. **Follow Clean Architecture** layers strictly
5. **Write comprehensive tests** (Unit + Integration)
6. **Use MapStruct** for entity-DTO mapping
7. **Implement proper logging** with structured logging
8. **Follow naming conventions** consistently

### **API Development Standards**

1. **RESTful design** principles
2. **Consistent URL patterns** (/api/v1/{resource})
3. **Proper HTTP status codes**
4. **Request/Response validation**
5. **API versioning** strategy
6. **OpenAPI documentation**
7. **Rate limiting** implementation
8. **Error response standardization**

## 🎯 Conclusion

Bu optimize edilmiş protokol ile:

- ✅ **Port çakışmaları** çözüldü
- ✅ **Servis uyumluluğu** sağlandı
- ✅ **Güvenlik standartları** geliştirildi
- ✅ **Monitoring** ve **observability** eklendi
- ✅ **Kurumsal standartlar** uygulandı
- ✅ **Scalability** ve **maintainability** artırıldı

Bu protokol, Fabric Management System'in **kurumsal profesyonel** bir uygulama olması için gerekli tüm standartları içermektedir.

---

**Last Updated**: 2024-01-XX  
**Version**: 2.0.0  
**Status**: Optimized Protocol Ready for Implementation

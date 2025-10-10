# 🚀 New Microservice Creation Guide

**Purpose:** Complete guide for creating new microservices in Fabric Management System  
**Target Audience:** AI Assistant & Development Team  
**Priority:** 🔴 CRITICAL - Read BEFORE creating any new service  
**Last Updated:** 2025-10-10  
**Version:** 1.0

---

## 📚 İlk Talimat (ZORUNLU)

**Önce şu dokümantasyonları DİKKATLE oku:**

### 🔴 ZORUNLU Dokümantasyon

1. **docs/AI_ASSISTANT_LEARNINGS.md** - Kodlama prensipleri ve kurallar
2. **docs/SECURITY.md** - Security standartları
3. **docs/development/PRINCIPLES.md** - Kodlama prensipleri
4. **docs/development/CODE_STRUCTURE_GUIDE.md** - Klasör yapısı
5. **docs/development/DATA_TYPES_STANDARDS.md** - UUID ve type standartları
6. **docs/development/MICROSERVICES_API_STANDARDS.md** - API standartları
7. **docs/database/DATABASE_GUIDE.md** - Database standartları

### 🟡 REFERANS Dokümantasyon

1. **COMPANY_SERVICE_REFACTORING_COMPLETE.md** - Başarılı microservice örneği
2. **docs/services/user-service.md** - User Service architecture
3. **docs/services/company-service.md** - Company Service architecture
4. **docs/services/api-gateway.md** - API Gateway integration
5. **POLICY_USAGE_ANALYSIS_AND_RECOMMENDATIONS.md** - Policy kullanımı

**Bu dosyaları okumadan hiçbir kod yazma!**

---

## 🎯 Microservice Creation Checklist

### Phase 1: Planning & Design (1-2 hours)

- [ ] **Domain Analysis**

  - [ ] Bounded context belirlendi mi?
  - [ ] Entity'ler tanımlandı mı?
  - [ ] Value objects belirlendi mi?
  - [ ] Business rules açık mı?
  - [ ] External dependencies belirlendi mi?

- [ ] **Architecture Decisions**

  - [ ] Domain model: Anemic vs Rich? (Default: Anemic)
  - [ ] Policy integration gerekli mi?
  - [ ] Cross-service communication var mı?
  - [ ] Event publishing gerekli mi?
  - [ ] File storage gerekli mi?

- [ ] **Database Design**
  - [ ] Table'lar tasarlandı mı?
  - [ ] Foreign key'ler belirlendi mi?
  - [ ] Index'ler planlandı mı?
  - [ ] Migration strategy belirlendi mi?

### Phase 2: Project Setup (30 minutes)

- [ ] **Maven Module Creation**

  - [ ] `services/{service-name}/pom.xml` oluşturuldu mu?
  - [ ] Parent POM'a eklendi mi?
  - [ ] Dependencies doğru mu?
  - [ ] Shared modules import edildi mi?

- [ ] **Application Configuration**

  - [ ] `application.yml` oluşturuldu mu?
  - [ ] `application-docker.yml` oluşturuldu mu?
  - [ ] Port numarası belirlendi mi? (unique!)
  - [ ] Database connection yapılandırıldı mı?

- [ ] **Docker Configuration**
  - [ ] `Dockerfile` oluşturuldu mu?
  - [ ] `docker-compose.yml`'e eklendi mi?
  - [ ] Health check yapılandırıldı mı?
  - [ ] Environment variables tanımlandı mı?

### Phase 3: Core Implementation (4-6 hours)

- [ ] **Klasör Yapısı**

  - [ ] Clean Architecture structure oluşturuldu mu?
  - [ ] `api/`, `application/`, `domain/`, `infrastructure/` layerlar
  - [ ] DTO klasörleri: `api/dto/request/`, `api/dto/response/`
  - [ ] Mapper klasörü: `application/mapper/`

- [ ] **Domain Layer**

  - [ ] Entity'ler oluşturuldu mu? (extends BaseEntity)
  - [ ] Value objects oluşturuldu mu?
  - [ ] Domain events tanımlandı mı?
  - [ ] Domain exceptions oluşturuldu mu?

- [ ] **Application Layer**

  - [ ] Service sınıfları oluşturuldu mu?
  - [ ] Mapper'lar oluşturuldu mu?
  - [ ] Business logic implement edildi mi?
  - [ ] Transaction management eklendi mi?

- [ ] **API Layer**

  - [ ] Controller'lar oluşturuldu mu?
  - [ ] Request DTO'lar oluşturuldu mu?
  - [ ] Response DTO'lar oluşturuldu mu?
  - [ ] Validation annotations eklendi mi?

- [ ] **Infrastructure Layer**
  - [ ] Repository'ler oluşturuldu mu?
  - [ ] Event publisher oluşturuldu mu?
  - [ ] External client'lar oluşturuldu mu?
  - [ ] Config sınıfları oluşturuldu mu?

### Phase 4: Security & Integration (2-3 hours)

- [ ] **Security Integration**

  - [ ] `@EnableWebSecurity` yapılandırıldı mı?
  - [ ] JWT token validation eklendi mi?
  - [ ] `shared.security` package scan edildi mi?
  - [ ] `@PreAuthorize` annotations eklendi mi?
  - [ ] SecurityContext kullanılıyor mu?

- [ ] **API Gateway Integration**

  - [ ] Gateway routing yapılandırıldı mı?
  - [ ] Health endpoint expose edildi mi?
  - [ ] Rate limiting tanımlandı mı?
  - [ ] Circuit breaker yapılandırıldı mı?

- [ ] **Policy Integration** (Opsiyonel)
  - [ ] Policy field'ları entity'de var mı?
  - [ ] PolicyEngine inject edildi mi?
  - [ ] Policy check'ler eklendi mi?
  - [ ] Policy audit yapılandırıldı mı?

### Phase 5: Database Migration (1 hour)

- [ ] **Flyway Migrations**
  - [ ] `V1__create_{entity}_tables.sql` oluşturuldu mu?
  - [ ] BaseEntity columns eklendi mi? (created_at, updated_at, version, deleted)
  - [ ] Foreign key constraints eklendi mi?
  - [ ] Index'ler oluşturuldu mu?
  - [ ] Seed data migration'ı eklendi mi? (opsiyonel)

### Phase 6: Testing (2-3 hours)

- [ ] **Unit Tests**

  - [ ] Service tests yazıldı mı?
  - [ ] Mapper tests yazıldı mı?
  - [ ] Coverage >80% mi?

- [ ] **Integration Tests**
  - [ ] Controller tests yazıldı mı?
  - [ ] Repository tests yazıldı mı?
  - [ ] E2E flow test edildi mi?

### Phase 7: Documentation (1 hour)

- [ ] **Service Documentation**

  - [ ] `services/{service-name}/README.md` oluşturuldu mu?
  - [ ] `docs/services/{service-name}.md` oluşturuldu mu?
  - [ ] API endpoints dokümante edildi mi?
  - [ ] Architecture diagram eklendi mi?

- [ ] **Update System Docs**
  - [ ] `docs/ARCHITECTURE.md` güncellendi mi?
  - [ ] `docker-compose.yml` dokümante edildi mi?
  - [ ] `README.md` güncellendi mi?

### Phase 8: Final Checks (30 minutes)

- [ ] **Code Quality**

  - [ ] Lint errors yok mu?
  - [ ] Hardcoded string yok mu?
  - [ ] Comment noise temizlendi mi?
  - [ ] Import'lar organize mi?

- [ ] **Deployment Ready**
  - [ ] Docker build başarılı mı?
  - [ ] Docker compose çalışıyor mu?
  - [ ] Health check çalışıyor mu?
  - [ ] Logs düzgün mü?

---

## 📂 Standard Klasör Yapısı

```
services/{service-name}/
├── pom.xml
├── Dockerfile
├── README.md
│
└── src/
    ├── main/
    │   ├── java/com/fabricmanagement/{service}/
    │   │   │
    │   │   ├── {Service}Application.java
    │   │   │
    │   │   ├── api/                              # 🌐 HTTP Layer
    │   │   │   ├── {Entity}Controller.java
    │   │   │   └── dto/
    │   │   │       ├── request/
    │   │   │       │   ├── Create{Entity}Request.java
    │   │   │       │   └── Update{Entity}Request.java
    │   │   │       └── response/
    │   │   │           └── {Entity}Response.java
    │   │   │
    │   │   ├── application/                      # 🔧 Business Layer
    │   │   │   ├── mapper/
    │   │   │   │   ├── {Entity}Mapper.java
    │   │   │   │   └── {Entity}EventMapper.java
    │   │   │   └── service/
    │   │   │       └── {Entity}Service.java
    │   │   │
    │   │   ├── domain/                           # 🎯 Domain Layer
    │   │   │   ├── aggregate/
    │   │   │   │   └── {Entity}.java
    │   │   │   ├── event/
    │   │   │   │   ├── {Entity}CreatedEvent.java
    │   │   │   │   ├── {Entity}UpdatedEvent.java
    │   │   │   │   └── {Entity}DeletedEvent.java
    │   │   │   ├── valueobject/
    │   │   │   │   └── {Status}.java
    │   │   │   └── exception/
    │   │   │       └── {Entity}NotFoundException.java
    │   │   │
    │   │   └── infrastructure/                   # 🏗️ Infrastructure
    │   │       ├── repository/
    │   │       │   └── {Entity}Repository.java
    │   │       ├── client/
    │   │       │   └── {External}ServiceClient.java
    │   │       ├── messaging/
    │   │       │   ├── {Entity}EventPublisher.java
    │   │       │   └── {External}EventListener.java
    │   │       └── config/
    │   │           ├── FeignClientConfig.java
    │   │           └── KafkaConfig.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-docker.yml
    │       └── db/migration/
    │           ├── V1__create_{entity}_tables.sql
    │           └── V2__add_indexes.sql
    │
    └── test/
        └── java/com/fabricmanagement/{service}/
            ├── api/
            │   └── {Entity}ControllerTest.java
            └── application/
                └── service/
                    └── {Entity}ServiceTest.java
```

---

## 🎯 Code Templates

### 1. Entity (Anemic Domain Model)

```java
package com.fabricmanagement.{service}.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * {Entity} Entity
 *
 * Domain model for {description}.
 * Pattern: Anemic Domain (Pure data holder)
 */
@Entity
@Table(name = "{entities}")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class {Entity} extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private {Entity}Status status;

    // NO BUSINESS METHODS!
    // Lombok provides @Getter/@Setter
    // Business logic → Service layer
}
```

### 2. Service

```java
package com.fabricmanagement.{service}.application.service;

import com.fabricmanagement.{service}.api.dto.request.Create{Entity}Request;
import com.fabricmanagement.{service}.api.dto.response.{Entity}Response;
import com.fabricmanagement.{service}.application.mapper.{Entity}Mapper;
import com.fabricmanagement.{service}.application.mapper.{Entity}EventMapper;
import com.fabricmanagement.{service}.domain.aggregate.{Entity};
import com.fabricmanagement.{service}.infrastructure.messaging.{Entity}EventPublisher;
import com.fabricmanagement.{service}.infrastructure.repository.{Entity}Repository;
import com.fabricmanagement.shared.application.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * {Entity} Service
 *
 * Business logic for {entity} management.
 * Pattern: Orchestration only (no mapping, no event building)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class {Entity}Service {

    private final {Entity}Repository repository;
    private final {Entity}Mapper mapper;
    private final {Entity}EventMapper eventMapper;
    private final {Entity}EventPublisher eventPublisher;

    @Transactional
    public UUID create{Entity}(Create{Entity}Request request, UUID tenantId, String createdBy) {
        log.info("Creating {entity} for tenant: {}", tenantId);

        // Mapping → Mapper's job
        {Entity} entity = mapper.fromCreateRequest(request, tenantId, createdBy);
        entity = repository.save(entity);

        log.info("{Entity} created successfully: {}", entity.getId());

        // Event building → EventMapper's job
        eventPublisher.publish{Entity}Created(
            eventMapper.toCreatedEvent(entity)
        );

        return entity.getId();
    }

    @Transactional(readOnly = true)
    public {Entity}Response get{Entity}(UUID id, UUID tenantId) {
        {Entity} entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new {Entity}NotFoundException(id));

        return mapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PagedResponse<{Entity}Response> list{Entities}(UUID tenantId, Pageable pageable) {
        Page<{Entity}> page = repository.findByTenantId(tenantId, pageable);
        return PagedResponse.of(page, mapper::toResponse);
    }
}
```

### 3. Controller

```java
package com.fabricmanagement.{service}.api;

import com.fabricmanagement.{service}.api.dto.request.Create{Entity}Request;
import com.fabricmanagement.{service}.api.dto.response.{Entity}Response;
import com.fabricmanagement.{service}.application.service.{Entity}Service;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * {Entity} Controller
 *
 * HTTP endpoints for {entity} management.
 * Pattern: HTTP handling only (no business logic)
 */
@RestController
@RequestMapping("/api/v1/{entities}")
@RequiredArgsConstructor
@Slf4j
public class {Entity}Controller {

    private final {Entity}Service service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UUID>> create{Entity}(
            @Valid @RequestBody Create{Entity}Request request,
            @AuthenticationPrincipal SecurityContext ctx) {

        UUID id = service.create{Entity}(request, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(id, "{Entity} created successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<{Entity}Response>> get{Entity}(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityContext ctx) {

        {Entity}Response response = service.get{Entity}(id, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<{Entity}Response>> list{Entities}(
            Pageable pageable,
            @AuthenticationPrincipal SecurityContext ctx) {

        PagedResponse<{Entity}Response> response = service.list{Entities}(
            ctx.getTenantId(), pageable
        );
        return ResponseEntity.ok(response);
    }
}
```

### 4. Mapper

```java
package com.fabricmanagement.{service}.application.mapper;

import com.fabricmanagement.{service}.api.dto.request.Create{Entity}Request;
import com.fabricmanagement.{service}.api.dto.response.{Entity}Response;
import com.fabricmanagement.{service}.domain.aggregate.{Entity};
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {Entity} Mapper
 *
 * DTO ↔ Entity mapping.
 * Pattern: Mapping only (no business logic)
 */
@Component
public class {Entity}Mapper {

    public {Entity} fromCreateRequest(Create{Entity}Request request, UUID tenantId, String createdBy) {
        return {Entity}.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name(request.getName())
                .status({Entity}Status.ACTIVE)
                .createdBy(createdBy)
                .build();
    }

    public {Entity}Response toResponse({Entity} entity) {
        return {Entity}Response.builder()
                .id(entity.getId())
                .name(entity.getName())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
```

### 5. Request DTO

```java
package com.fabricmanagement.{service}.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

/**
 * Create {Entity} Request DTO
 */
@Data
@Builder
public class Create{Entity}Request {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    // Add other fields with validation
}
```

### 6. Response DTO

```java
package com.fabricmanagement.{service}.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {Entity} Response DTO
 */
@Data
@Builder
public class {Entity}Response {

    private UUID id;
    private String name;
    private String status;
    private LocalDateTime createdAt;

    // Add other fields as needed
}
```

### 7. Repository

```java
package com.fabricmanagement.{service}.infrastructure.repository;

import com.fabricmanagement.{service}.domain.aggregate.{Entity};
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {Entity} Repository
 */
@Repository
public interface {Entity}Repository extends JpaRepository<{Entity}, UUID> {

    Optional<{Entity}> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<{Entity}> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByNameAndTenantId(String name, UUID tenantId);
}
```

### 8. Migration SQL

```sql
-- V1__create_{entity}_tables.sql

-- {Entity} Table
CREATE TABLE {entities} (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- Tenant Isolation
    tenant_id UUID NOT NULL,

    -- Business Fields
    name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,

    -- BaseEntity Audit Fields (MANDATORY!)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Indexes
CREATE INDEX idx_{entities}_tenant_id ON {entities}(tenant_id);
CREATE INDEX idx_{entities}_status ON {entities}(status);
CREATE INDEX idx_{entities}_created_at ON {entities}(created_at);

-- Unique Constraints
CREATE UNIQUE INDEX idx_{entities}_name_tenant
    ON {entities}(name, tenant_id)
    WHERE deleted = FALSE;

-- Comments
COMMENT ON TABLE {entities} IS '{Entity} management table';
COMMENT ON COLUMN {entities}.tenant_id IS 'Tenant isolation - all queries must filter by this';
```

---

## 🔒 Security Integration

### Application Main Class

```java
package com.fabricmanagement.{service};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * {Service} Application
 */
@SpringBootApplication(scanBasePackages = {
    "com.fabricmanagement.{service}",
    "com.fabricmanagement.shared.security"  // ← MANDATORY for security
})
@EntityScan(basePackages = {
    "com.fabricmanagement.{service}.domain",
    "com.fabricmanagement.shared.domain"     // ← Full package scan
})
@EnableJpaRepositories("com.fabricmanagement.{service}.infrastructure.repository")
@EnableFeignClients
@EnableKafka
public class {Service}Application {

    public static void main(String[] args) {
        SpringApplication.run({Service}Application.class, args);
    }
}
```

### Security Configuration

```java
package com.fabricmanagement.{service}.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security Configuration
 *
 * JWT validation handled by shared-security module.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
```

---

## 📊 Configuration Files

### pom.xml

```xml
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

    <artifactId>{service}-service</artifactId>
    <name>{Service} Service</name>
    <description>{Service} microservice for Fabric Management System</description>

    <dependencies>
        <!-- Shared Modules -->
        <dependency>
            <groupId>com.fabricmanagement</groupId>
            <artifactId>shared-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fabricmanagement</groupId>
            <artifactId>shared-application</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fabricmanagement</groupId>
            <artifactId>shared-infrastructure</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fabricmanagement</groupId>
            <artifactId>shared-security</artifactId>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Feign Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>

        <!-- Kafka -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>

        <!-- Utilities -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### application.yml

```yaml
# {Service} Service Configuration

server:
  port: {PORT}  # Choose unique port (8084+)

spring:
  application:
    name: {service}-service

  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/{service}_db
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: {service}-service-group
      properties:
        spring.json.trusted.packages: "*"

# JWT Configuration
jwt:
  secret: ${JWT_SECRET:your-secret-key}

# Logging
logging:
  level:
    com.fabricmanagement.{service}: INFO
    org.springframework: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: always
```

### Dockerfile

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy jar file
COPY target/{service}-service-1.0.0-SNAPSHOT.jar app.jar

# Expose port
EXPOSE {PORT}

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:{PORT}/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml entry

```yaml
  {service}-service:
    build:
      context: .
      dockerfile: services/{service}-service/Dockerfile
    ports:
      - "{PORT}:{PORT}"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_USER=postgres
      - DB_PASSWORD=postgres
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      - postgres
      - kafka
    networks:
      - fabric-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:{PORT}/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 40s
```

---

## ⚠️ YAPMAMANLAR (Anti-Patterns)

### ❌ YAPMA:

1. ❌ Entity'de business method ekleme (Anemic domain kullan!)
2. ❌ Service'de mapping logic (Mapper'a delege et!)
3. ❌ Controller'da business logic (Service'e delege et!)
4. ❌ Hardcoded string/number kullanma (Constants kullan!)
5. ❌ Magic number kullanma (Named constants!)
6. ❌ Over-engineering (KISS prensibi!)
7. ❌ Code duplication (DRY prensibi!)
8. ❌ String ID kullanma (UUID kullan!)
9. ❌ username field ekleme (userID kullan!)
10. ❌ Policy gereksiz yere ekleme (İhtiyaç varsa ekle!)

### ✅ YAP:

1. ✅ Dokümantasyonları oku
2. ✅ Clean Architecture kullan
3. ✅ Anemic Domain Model kullan
4. ✅ Mapper pattern kullan
5. ✅ SRP, DRY, KISS, YAGNI prensipleri
6. ✅ UUID type safety (her yerde UUID!)
7. ✅ SecurityContext kullan (@AuthenticationPrincipal)
8. ✅ BaseEntity extend et
9. ✅ Shared modules kullan
10. ✅ Test yaz (coverage >80%)

---

## 🎯 Başarı Kriterleri

### Kod Kalitesi

- [ ] Zero lint errors
- [ ] Zero hardcoded strings
- [ ] Zero magic numbers
- [ ] Entity <200 lines (Anemic domain)
- [ ] Service <300 lines
- [ ] Controller <200 lines
- [ ] Test coverage >80%

### Architecture

- [ ] Clean Architecture structure
- [ ] SRP applied (each class one job)
- [ ] DRY applied (no duplication)
- [ ] KISS applied (simple solutions)
- [ ] YAGNI applied (no over-engineering)

### Security

- [ ] JWT authentication integrated
- [ ] SecurityContext kullanılıyor
- [ ] @PreAuthorize annotations var
- [ ] Tenant isolation uygulanmış
- [ ] UUID type safety (all IDs)

### Database

- [ ] Flyway migrations created
- [ ] BaseEntity columns added
- [ ] Indexes created
- [ ] Foreign keys defined
- [ ] Unique constraints added

### Integration

- [ ] API Gateway routing configured
- [ ] Health endpoint working
- [ ] Docker image builds
- [ ] Docker compose integration
- [ ] Kafka events (if needed)

### Documentation

- [ ] README.md created
- [ ] Service docs created
- [ ] API endpoints documented
- [ ] Architecture diagram included
- [ ] Timestamps added

---

## 📝 Port Allocation

| Service          | Port | Status  |
| ---------------- | ---- | ------- |
| API Gateway      | 8080 | ✅ Used |
| User Service     | 8081 | ✅ Used |
| Contact Service  | 8082 | ✅ Used |
| Company Service  | 8083 | ✅ Used |
| **YOUR SERVICE** | 8084 | 🆕 Next |
| **Future**       | 8085 | 🔜 Free |
| **Future**       | 8086 | 🔜 Free |

---

## 🚀 Quick Start Command

```bash
# Create service directory
mkdir -p services/{service-name}/src/main/java/com/fabricmanagement/{service}
mkdir -p services/{service-name}/src/main/resources/db/migration
mkdir -p services/{service-name}/src/test/java/com/fabricmanagement/{service}

# Create basic files
touch services/{service-name}/pom.xml
touch services/{service-name}/Dockerfile
touch services/{service-name}/README.md
touch services/{service-name}/src/main/resources/application.yml
touch services/{service-name}/src/main/resources/application-docker.yml

# Create package structure
cd services/{service-name}/src/main/java/com/fabricmanagement/{service}
mkdir -p api/dto/{request,response}
mkdir -p application/{mapper,service}
mkdir -p domain/{aggregate,event,valueobject,exception}
mkdir -p infrastructure/{repository,client,messaging,config}
```

---

## 📚 Related Documentation

### Core Documents

- [docs/AI_ASSISTANT_LEARNINGS.md](docs/AI_ASSISTANT_LEARNINGS.md)
- [docs/development/PRINCIPLES.md](docs/development/PRINCIPLES.md)
- [docs/development/CODE_STRUCTURE_GUIDE.md](docs/development/CODE_STRUCTURE_GUIDE.md)
- [docs/development/DATA_TYPES_STANDARDS.md](docs/development/DATA_TYPES_STANDARDS.md)

### Service Examples

- [docs/services/user-service.md](docs/services/user-service.md)
- [docs/services/company-service.md](docs/services/company-service.md)
- [docs/services/contact-service.md](docs/services/contact-service.md)

### Refactoring Guides

- [COMPANY_SERVICE_REFACTORING_COMPLETE.md](COMPANY_SERVICE_REFACTORING_COMPLETE.md)
- [API_GATEWAY_REFACTORING_PROMPT.md](API_GATEWAY_REFACTORING_PROMPT.md)

---

## 💡 Pro Tips

### Tip 1: Start Simple

Önce basit CRUD işlemleri ile başla. Complex business logic'i sonra ekle.

### Tip 2: Copy from Existing

User-Service veya Company-Service'den copy-paste yap, sonra customize et.

### Tip 3: Test as You Go

Her feature'ı implement ettikten sonra test et. Son ana bırakma!

### Tip 4: Document Immediately

Kod yazarken dokümante et. Sonra unutursun!

### Tip 5: Follow Patterns

Mevcut pattern'leri koru. Consistency çok önemli!

### Tip 6: Use Shared Modules

shared-domain, shared-infrastructure'ı maksimum kullan. Don't reinvent!

### Tip 7: Security First

Security integration'ı baştan ekle. Sonra eklemek zor!

### Tip 8: Policy Optional

Policy her service'de gerekli değil! İhtiyaç varsa ekle, yoksa ekleme.

---

## 🎊 Final Checklist

### Before Committing

- [ ] All files created
- [ ] No lint errors
- [ ] Tests passing
- [ ] Docker builds
- [ ] Health check works
- [ ] Documentation complete

### Git Commit Message Template

```
feat({service}-service): create new {service} microservice

- Add Clean Architecture structure
- Implement CRUD operations
- Add security integration
- Add API Gateway routing
- Add database migrations
- Add comprehensive documentation

Endpoints:
- POST /api/v1/{entities} - Create {entity}
- GET /api/v1/{entities}/{id} - Get {entity}
- GET /api/v1/{entities} - List {entities}
- PUT /api/v1/{entities}/{id} - Update {entity}
- DELETE /api/v1/{entities}/{id} - Delete {entity}

Technical:
- Port: {PORT}
- Database: {service}_db
- Kafka topics: {entity}-events
- Test coverage: >80%

Status: Production Ready
```

---

**Created By:** Development Team  
**Last Updated:** 2025-10-10  
**Version:** 1.0  
**Status:** ✅ Ready to Use - Complete Microservice Creation Guide  
**Next Review:** 2026-01-10

---

## 🎯 Remember

> "This is our baby. We must take care of it properly."
>
> - No temporary solutions
> - No workarounds
> - Production-grade from start
> - Clean Architecture always
> - Best practices mandatory
>
> **Quality is NOT optional. It's ESSENTIAL!**

**Haydi şimdi harika bir microservice oluştur! 🚀✨**

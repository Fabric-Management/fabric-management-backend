# shared-infrastructure

**Version**: 1.0.0  
**Last Updated**: 2025-10-20

## Purpose

Infrastructure layer shared across all microservices - Contains base configs, constants, utilities, policy engine, and cross-cutting concerns.

**Principle**: Reusable infrastructure code, NOT business logic!

---

## Key Components

### 1. Constants (8 files - ZERO HARDCODED!)

**Usage**: Import and use instead of literals

```java
import static com.fabricmanagement.shared.infrastructure.constants.ServiceConstants.*;

// Use constant instead of "http://user-service:8081"
String url = USER_SERVICE_URL;
```

**Files**:

- `ServiceConstants` - Service URLs, ports, timeouts
- `SecurityConstants` - Security headers, prefixes
- `SecurityRoles` - Role names (PLATFORM_SUPER_ADMIN, etc.)
- `KafkaTopics` - Event topic names
- `ValidationConstants` - Regex patterns, min/max lengths
- `TokenConstants` - Token expiry, headers
- `InternalApiConstants` - Internal API keys, paths
- `NotificationConstants` - Channels, templates

### 2. Base Configurations

#### BaseFeignClientConfig

**Purpose**: Standard Feign client setup with correlation ID propagation

**Usage**:

```java
@Configuration
public class CompanyFeignConfig extends BaseFeignClientConfig {
    // Automatically gets:
    // - Request/Response logging
    // - Correlation ID interceptor
    // - Error decoder
    // - Retry logic
}
```

#### BaseKafkaErrorConfig

**Purpose**: Kafka error handling with DLT support

**Usage**:

```java
@Configuration
public class CompanyKafkaConfig extends BaseKafkaErrorConfig {
    // Automatically gets:
    // - ErrorHandlingDeserializer
    // - DLT publishing
    // - Retry backoff
}
```

### 3. Global Exception Handler

**Purpose**: Consistent error responses across all services

**Handles**:

- Domain exceptions → HTTP status mapping
- Validation errors → 400 Bad Request
- Authentication errors → 401 Unauthorized
- Authorization errors → 403 Forbidden
- Not found errors → 404 Not Found
- Business rule violations → 422 Unprocessable Entity

**Auto-configured**: Just include dependency, works automatically!

### 4. Policy Engine

**Components**:

- `PolicyEngine` - RBAC decision engine
- `PolicyCache` - In-memory policy cache (ConcurrentHashMap)
- `PolicyAuditService` - Audit policy decisions
- `PlatformPolicyGuard` - Platform-level guards
- `CompanyTypeGuard` - Company-type-based guards
- `ScopeResolver` - Resolve permission scopes
- `UserGrantResolver` - Resolve user permissions

**Usage**:

```java
@Autowired
private PolicyEngine policyEngine;

PolicyDecision decision = policyEngine.evaluate(
    PolicyContext.builder()
        .userId(userId)
        .tenantId(tenantId)
        .resourceType("COMPANY")
        .operation(OperationType.WRITE)
        .build()
);

if (!decision.isAllowed()) {
    throw new ForbiddenException(decision.getReason());
}
```

**Performance**: PolicyCache reduces DB hits by 90%+

### 5. Outbox Pattern

**Components**:

- `OutboxEventPublisher` - Publish events transactionally
- `OutboxEventRepository` - JPA repository for outbox table

**Usage**:

```java
@Autowired
private OutboxEventPublisher outboxPublisher;

@Transactional
public void createCompany(Company company) {
    companyRepository.save(company);

    // Publish event (saved in outbox table)
    outboxPublisher.publish(new CompanyCreatedEvent(...));

    // Scheduler publishes to Kafka asynchronously
}
```

### 6. Utilities

#### TextSimilarityUtil

**Purpose**: String similarity for duplicate detection

```java
double similarity = TextSimilarityUtil.jaroWinkler("ABC Corp", "ABC Corporation");
// Returns: 0.85 (85% similar)
```

#### StringNormalizationUtil

**Purpose**: Normalize company names (international support)

```java
String normalized = StringNormalizationUtil.normalize("Şirket A.Ş.");
// Returns: "sirket"
```

#### DataMaskingUtil

**Purpose**: Mask sensitive data (email, phone)

```java
String masked = DataMaskingUtil.maskEmail("test@example.com");
// Returns: "te**@ex******.com"
```

#### EmailValidationUtil

**Purpose**: Validate email format

```java
boolean valid = EmailValidationUtil.isValid("test@example.com");
```

#### MaskingUtil

**Purpose**: Generic masking utility

```java
String masked = MaskingUtil.mask("1234567890", 4);
// Returns: "******7890"
```

---

## Configuration Classes

### MessageSourceConfig

**Purpose**: I18n message resolution

```java
@Autowired
private MessageResolver messageResolver;

String message = messageResolver.getMessage(
    AuthMessageKeys.INVALID_PASSWORD,
    Locale.forLanguageTag("tr")
);
```

### TextProcessingConfig

**Purpose**: Text processing configuration (normalization, similarity)

---

## Design Principles

### 1. Base Config Pattern

- All services extend base configs (`BaseFeignClientConfig`, `BaseKafkaErrorConfig`)
- Consistency across services
- DRY - configure once, use everywhere

### 2. Constants First

- NEVER hardcode values
- Constants in `shared-infrastructure/constants/`
- Environment variable overrides supported

### 3. Utilities are Stateless

- All util methods are `static`
- No instance state
- Thread-safe by design

---

## Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
</dependencies>
```

---

## Testing

```bash
# Run tests
mvn -pl shared/shared-infrastructure test

# Coverage
mvn -pl shared/shared-infrastructure clean test jacoco:report
```

**Current Coverage**: ~50% (target: 85%+)

**Priority Tests Needed**:

- `PolicyEngineTest` ✅ (exists)
- `PolicyCacheTest` ✅ (exists)
- `BaseFeignClientConfigTest` ❌ (add)
- `GlobalExceptionHandlerTest` ❌ (add)
- Utility tests (TextSimilarity, Normalization) ❌ (add)

---

**Owner**: Fabric Management Team  
**Module Type**: Foundation  
**Stability**: Stable (v1.0.0)

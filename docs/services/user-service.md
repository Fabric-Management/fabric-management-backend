# 👤 User Service Documentation

**Version:** 2.0  
**Last Updated:** 2025-10-10  
**Port:** 8081  
**Database:** fabric_management (user_schema)  
**Status:** ✅ Production Ready

---

## 📋 Overview

User Service manages user authentication, authorization, and profile management. Implements Clean Architecture with Anemic Domain Model pattern.

### Core Responsibilities

- ✅ User authentication (login, password management)
- ✅ User profile management (CRUD)
- ✅ JWT token generation
- ✅ Login attempt tracking (brute force protection)
- ✅ Security audit logging
- ✅ Integration with Contact Service (email/phone)
- ✅ Policy-based authorization (UserContext)

---

## 🏗️ Architecture

### Current Architecture (Post-Refactoring - Oct 2025)

```
user-service/
├── api/
│   ├── UserController.java [186 satır]
│   ├── AuthController.java
│   └── dto/
│       ├── request/
│       │   ├── CreateUserRequest.java
│       │   ├── UpdateUserRequest.java
│       │   ├── LoginRequest.java
│       │   └── SetupPasswordRequest.java
│       └── response/
│           ├── UserResponse.java
│           ├── LoginResponse.java
│           └── CheckContactResponse.java
│
├── application/
│   ├── mapper/
│   │   ├── UserMapper.java [221 satır]
│   │   ├── UserEventMapper.java [47 satır]
│   │   └── AuthMapper.java [74 satır]
│   └── service/
│       ├── UserService.java [169 satır]
│       └── AuthService.java [211 satır]
│
├── domain/
│   ├── aggregate/
│   │   └── User.java [99 satır] ← Pure data holder!
│   ├── event/
│   │   ├── UserCreatedEvent.java
│   │   ├── UserUpdatedEvent.java
│   │   └── UserDeletedEvent.java
│   └── valueobject/
│       ├── UserStatus.java
│       └── RegistrationType.java
│
└── infrastructure/
    ├── repository/
    │   └── UserRepository.java
    ├── client/
    │   └── ContactServiceClient.java
    ├── messaging/
    │   └── UserEventPublisher.java
    ├── security/
    │   └── LoginAttemptTracker.java [Redis-based]
    └── audit/
        └── SecurityAuditLogger.java
```

### Key Patterns

- ✅ **Anemic Domain Model**: Entity = Pure data holder
- ✅ **Mapper Separation**: 3 focused mappers (User, Event, Auth)
- ✅ **Clean Architecture**: Clear layer separation
- ✅ **10 Golden Rules**: SRP, DRY, KISS, YAGNI applied

---

## 📦 Domain Model

### User Aggregate (99 lines - Anemic Domain)

```java
@Entity
@Table(name = "users")
@Getter
@Setter
@SuperBuilder
public class User extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;  // ← UUID type safety!

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false)
    private RegistrationType registrationType;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "role")
    private String role;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    @Type(JsonBinaryType.class)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private Map<String, Object> preferences;

    @Type(JsonBinaryType.class)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;

    // ========== COMPANY RELATIONS ==========
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "station_id")
    private UUID stationId;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    // ========== POLICY FIELD ==========
    @Enumerated(EnumType.STRING)
    @Column(name = "user_context", nullable = false)
    @lombok.Builder.Default
    private UserContext userContext = UserContext.INTERNAL;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "functions", columnDefinition = "text[]")
    private List<String> functions;

    // NO BUSINESS METHODS! (Anemic Domain)
    // Business logic → UserService
    // Mapping logic → UserMapper
}
```

**Key Changes (Oct 2025 Refactoring):**

- ✅ 408 lines → 99 lines (-76%)
- ✅ Removed 20+ business methods
- ✅ Pure @Getter/@Setter (Lombok)
- ✅ Policy field added (userContext)

---

## 🔐 Policy Integration

### Policy Field - UserContext

```java
@Column(name = "user_context", nullable = false)
private UserContext userContext = UserContext.INTERNAL;
```

**UserContext Values:**

- `INTERNAL` - İç kullanıcılar (employees)
- `CUSTOMER` - Müşteri kullanıcıları
- `SUPPLIER` - Tedarikçi kullanıcıları
- `EXTERNAL` - Dış kullanıcılar

### Current Status

**Database:** ✅ Field var  
**Service:** ⚠️ **Policy enforcement YOK** (TODO)  
**PolicyEngine:** ⚠️ **Integration YOK** (TODO)

### Recommended Integration

```java
@Service
public class UserService {
    private final PolicyEngine policyEngine;  // ADD!

    @Transactional
    public UUID createUser(CreateUserRequest request, UUID tenantId, String createdBy) {
        // TODO: Policy check ekle
        // Sadece INTERNAL company user create edebilir

        User user = userMapper.fromCreateRequest(request, tenantId, createdBy);
        user = userRepository.save(user);

        eventPublisher.publishUserCreated(eventMapper.toCreatedEvent(user, email));

        return user.getId();
    }
}
```

**📖 Detaylı policy analiz:** [POLICY_USAGE_ANALYSIS_AND_RECOMMENDATIONS.md](../../POLICY_USAGE_ANALYSIS_AND_RECOMMENDATIONS.md)

---

## 🔒 Security Features

### 1. Authentication Flow

**Step 1: Check Contact**

```
POST /api/v1/users/auth/check-contact
→ Returns: exists, hasPassword, userId
→ Security: Response time masking (200ms min)
→ Rate limit: 10 req/min
```

**Step 2: Setup Password (First Time)**

```
POST /api/v1/users/auth/setup-password
→ Validation: Contact must be verified
→ Password requirements enforced
→ Rate limit: 3 req/min
```

**Step 3: Login**

```
POST /api/v1/users/auth/login
→ Brute force protection (5 attempts → 15 min lockout)
→ Security audit logging
→ Rate limit: 5 req/min
→ Returns: JWT access + refresh token
```

### 2. Brute Force Protection

```java
// Redis-based (LoginAttemptTracker)
- Max attempts: 5
- Lockout: 15 minutes
- Auto-unlock after timeout
- Distributed tracking
```

### 3. Security Audit Logging

```
[SECURITY_AUDIT] event=LOGIN_SUCCESS contactValue=use*** userId=uuid
[SECURITY_AUDIT] event=LOGIN_FAILED contactValue=use*** reason=Invalid password
[SECURITY_AUDIT] event=ACCOUNT_LOCKED contactValue=use*** attempts=5
```

---

## 📊 API Endpoints

### User Management

| Method | Endpoint               | Auth          | Description  |
| ------ | ---------------------- | ------------- | ------------ |
| POST   | `/api/v1/users`        | ADMIN         | Create user  |
| GET    | `/api/v1/users/{id}`   | Authenticated | Get user     |
| GET    | `/api/v1/users`        | Authenticated | List users   |
| PUT    | `/api/v1/users/{id}`   | Owner/ADMIN   | Update user  |
| DELETE | `/api/v1/users/{id}`   | ADMIN         | Delete user  |
| GET    | `/api/v1/users/search` | Authenticated | Search users |

### Authentication (Public)

| Method | Endpoint                            | Rate Limit | Description            |
| ------ | ----------------------------------- | ---------- | ---------------------- |
| POST   | `/api/v1/users/auth/check-contact`  | 10/min     | Check contact exists   |
| POST   | `/api/v1/users/auth/setup-password` | 3/min      | Setup initial password |
| POST   | `/api/v1/users/auth/login`          | 5/min      | Login with credentials |

---

## 🗄️ Database Schema

### Users Table

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,                    -- ✅ UUID type!
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    status VARCHAR(50) NOT NULL,
    registration_type VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255),
    role VARCHAR(100),
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45),

    -- Company relations
    company_id UUID,                             -- ✅ UUID type!
    department_id UUID,
    station_id UUID,
    job_title VARCHAR(100),

    -- Policy field
    user_context VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',  -- ✅ Policy!
    functions TEXT[],

    -- JSONB fields
    preferences JSONB,
    settings JSONB,

    -- BaseEntity fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN DEFAULT FALSE,
    version INTEGER DEFAULT 0
);
```

**Key Points:**

- ✅ UUID type columns (NOT VARCHAR!)
- ✅ Policy field (user_context)
- ✅ JSONB for flexible data
- ✅ BaseEntity auditing fields

---

## 🤝 Integration Points

### Contact Service Integration

```java
@FeignClient(
    name = "contact-service",
    url = "${contact-service.url}",
    configuration = FeignClientConfig.class,
    fallback = ContactServiceClientFallback.class
)
public interface ContactServiceClient {
    @GetMapping("/api/v1/contacts/find-by-value")
    ApiResponse<ContactDto> findByValue(@RequestParam String value);
}
```

**Features:**

- ✅ JWT token propagation
- ✅ Circuit breaker
- ✅ Fallback mechanism
- ✅ Resilience4j integration

### Events Published

- `UserCreatedEvent` - New user created
- `UserUpdatedEvent` - User profile updated
- `UserDeletedEvent` - User soft deleted

### Events Consumed

- `CompanyCreatedEvent` - Company created
- `ContactVerifiedEvent` - Contact verified

---

## 📈 Metrics & Monitoring

### Key Metrics

- User creation rate
- Login success/failure rate
- Account lockout rate
- JWT token validation rate
- Contact verification rate

### Health Checks

```
GET /actuator/health
→ Database connectivity
→ Redis connectivity
→ Contact Service connectivity
```

---

## 🔧 Configuration

```yaml
# application.yml
server:
  port: 8081

# Security
security:
  login-attempt:
    max-attempts: 5
    lockout-duration-minutes: 15
  response-time-masking:
    min-response-time-ms: 200

# JWT
jwt:
  secret: ${JWT_SECRET}
  expiration: 3600000 # 1 hour
  refresh-expiration: 86400000 # 24 hours

# Contact Service
contact-service:
  url: http://localhost:8082
```

---

## 🎯 Policy Integration Status

### ✅ Implemented

- ✅ UserContext field in User entity
- ✅ Company relation fields (companyId, departmentId)
- ✅ Database migration complete

### ⚠️ TODO (High Priority)

- ⚠️ PolicyEngine integration
- ⚠️ Business rule enforcement:
  - CUSTOMER company CANNOT create users
  - SUPPLIER company CANNOT create users
  - Cross-company user access control
- ⚠️ Data scope validation (SELF, COMPANY, CROSS_COMPANY, GLOBAL)

**📖 Detaylı analiz:** [POLICY_USAGE_ANALYSIS_AND_RECOMMENDATIONS.md](../../POLICY_USAGE_ANALYSIS_AND_RECOMMENDATIONS.md)

---

## 🔗 Related Documentation

- [Security Guide](../SECURITY.md) - Complete security documentation
- [Policy Authorization](../development/POLICY_AUTHORIZATION.md) - Policy system
- [Code Structure](../development/code_structure_guide.md) - Coding standards
- [User Service Refactoring](../reports/USER_SERVICE_FINAL_REFACTORING_SUMMARY.md) - Refactoring report

---

**Last Updated:** 2025-10-10  
**Version:** 2.0 (Post-Refactoring)  
**Status:** ✅ Production Ready  
**LOC:** 567 lines (Entity: 99, Service: 169, Mappers: 342)

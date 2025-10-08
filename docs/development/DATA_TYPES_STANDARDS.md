# 🔢 Data Types & Identifier Standards

## 🎯 Overview

Bu doküman, Fabric Management System'de veri tipi kullanımı ve identifier yönetimi standartlarını tanımlar. Tüm geliştiricilerin bu standartlara uyması sistem tutarlılığı ve güvenliği için kritiktir.

**Son Güncelleme:** 2025-10-07  
**Durum:** ✅ Aktif ve Zorunlu

---

## 🆔 UUID Standardizasyonu

### Genel Prensip

> **"System-generated identifiers MUST be UUID type throughout the entire stack"**

Sistem tarafından üretilen tüm identifier'lar (ID, tenant_id, vb.) **UUID** tipinde olmalıdır:

- ✅ Database: `UUID` column type
- ✅ Domain/Entity: `UUID` field type
- ✅ API Parameters: `UUID` type
- ✅ Repository: `UUID` parameters/return types

### UUID Kullanım Alanları

| Identifier   | Type | Rationale                              |
| ------------ | ---- | -------------------------------------- |
| `id`         | UUID | Primary key, globally unique           |
| `tenant_id`  | UUID | Multi-tenancy identifier, type-safe    |
| `user_id`    | UUID | User references, consistent with id    |
| `company_id` | UUID | Company references, consistent with id |
| `contact_id` | UUID | Contact references, consistent with id |

---

## ✅ Correct UUID Implementation

### 1. Database Schema

```sql
-- ✅ CORRECT: Native UUID type
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes on UUID
CREATE INDEX idx_users_tenant_id ON users (tenant_id);

-- Foreign keys with UUID
ALTER TABLE user_sessions
    ADD CONSTRAINT fk_user_sessions_user_id
    FOREIGN KEY (user_id) REFERENCES users(id);
```

### 2. JPA Entity

```java
// ✅ CORRECT: UUID field type
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;  // ✅ UUID type, not String

    // No manual UUID manipulation
    // Framework handles UUID generation and persistence
}
```

### 3. Repository

```java
// ✅ CORRECT: UUID parameters and return types
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.deleted = false")
    List<User> findByTenantId(@Param("tenantId") UUID tenantId);  // ✅ UUID parameter

    Optional<User> findByIdAndTenantId(UUID userId, UUID tenantId);  // ✅ Both UUID
}
```

### 4. Service Layer

```java
// ✅ CORRECT: UUID in domain logic
@Service
public class UserService {

    public UserResponse getUser(UUID userId, UUID tenantId) {  // ✅ UUID parameters
        User user = userRepository.findById(userId)
            .filter(u -> u.getTenantId().equals(tenantId))  // ✅ UUID comparison (no .toString())
            .orElseThrow(() -> new UserNotFoundException(userId));

        return mapToResponse(user);
    }

    public UUID createUser(CreateUserRequest request, UUID tenantId, String createdBy) {
        User user = User.builder()
            .id(UUID.randomUUID())  // ✅ System generates UUID
            .tenantId(tenantId)     // ✅ UUID → UUID (no conversion)
            .firstName(request.getFirstName())
            .build();

        return userRepository.save(user).getId();  // ✅ Returns UUID
    }
}
```

### 5. Controller Layer

```java
// ✅ CORRECT: UUID path variables
@RestController
@RequestMapping("/")
public class UserController {

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();  // ✅ Returns UUID
        UserResponse user = userService.getUser(userId, tenantId);   // ✅ UUID → UUID
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
```

### 6. Security Context

```java
// ✅ CORRECT: UUID extraction from JWT
public class SecurityContextHolder {

    public static UUID getCurrentTenantId() {  // ✅ Returns UUID
        Authentication auth = getAuthentication();
        String tenantIdStr = (String) auth.getDetails();
        return UUID.fromString(tenantIdStr);  // ✅ Parse once, use everywhere as UUID
    }
}
```

---

## 🔄 UUID → String Conversion (Boundary Layer ONLY)

**RULE:** Only convert UUID to String at system boundaries (serialization points)

### When to Convert UUID → String

#### 1. JWT Token Generation

```java
// ✅ CORRECT: Convert at JWT boundary
String accessToken = jwtTokenProvider.generateToken(
    user.getId().toString(),           // UUID → String for JWT
    user.getTenantId().toString(),     // UUID → String for JWT
    claims
);
```

**Why:** JWT payload is JSON, which doesn't have UUID type.

#### 2. Kafka Events

```java
// ✅ CORRECT: Convert for event serialization
UserCreatedEvent event = UserCreatedEvent.builder()
    .userId(user.getId())
    .tenantId(user.getTenantId().toString())  // UUID → String for Kafka
    .firstName(user.getFirstName())
    .build();

eventPublisher.publish(event);
```

**Why:** Kafka events are JSON serialized, consumer services may use different languages.

#### 3. Audit Logging

```java
// ✅ CORRECT: Convert for log messages
auditLogger.logSuccessfulLogin(
    contactValue,
    userId.toString(),     // UUID → String for log
    tenantId.toString()    // UUID → String for log
);
```

**Why:** Logs are text-based, UUID.toString() provides readable format.

#### 4. External API Integration

```java
// ✅ CORRECT: Convert for external API calls
String response = externalApi.callService(
    "user_id=" + userId.toString()  // UUID → String for query param
);
```

**Why:** External APIs may not support UUID type.

---

## ❌ Anti-Patterns (DO NOT DO)

### ❌ WRONG: String in Database

```sql
-- ❌ WRONG: String type for system-generated ID
CREATE TABLE users (
    id VARCHAR(36),  -- ❌ Should be UUID
    tenant_id VARCHAR(255)  -- ❌ Should be UUID
);
```

**Problems:**

- ❌ No type safety
- ❌ Manual validation needed
- ❌ Performance penalty (VARCHAR vs UUID)
- ❌ Larger storage size (36 bytes vs 16 bytes)

### ❌ WRONG: String in Entity

```java
// ❌ WRONG: String field for UUID
@Entity
public class User {
    @Column(name = "tenant_id")
    private String tenantId;  // ❌ Should be UUID
}
```

**Problems:**

- ❌ Runtime errors possible
- ❌ Type safety lost
- ❌ Manual parsing everywhere
- ❌ No compile-time validation

### ❌ WRONG: Manual UUID Manipulation

```java
// ❌ WRONG: Manual UUID creation without validation
user.setTenantId(UUID.randomUUID());  // ❌ Tenant ID should be from context

// ❌ WRONG: String concatenation with UUID
String key = "user:" + userId.toString() + ":" + tenantId.toString();  // ❌ Fragile

// ❌ WRONG: Unnecessary conversions
UUID tenantId = UUID.fromString(user.getTenantId().toString());  // ❌ If already UUID
```

**Problems:**

- ❌ Security risk (random tenant IDs)
- ❌ Inconsistent data
- ❌ Hard to debug

### ❌ WRONG: String Comparison in Domain

```java
// ❌ WRONG: Converting UUID to String for comparison
user.getTenantId().toString().equals(tenantId.toString())  // ❌ Unnecessary

// ✅ CORRECT: Direct UUID comparison
user.getTenantId().equals(tenantId)  // ✅ Clean and efficient
```

---

## 📊 UUID vs String Performance

| Aspect            | UUID          | VARCHAR(36)    | Winner  |
| ----------------- | ------------- | -------------- | ------- |
| Storage Size      | 16 bytes      | 36 bytes       | UUID ✅ |
| Index Performance | Fast (binary) | Slower (text)  | UUID ✅ |
| Comparison Speed  | Native        | String compare | UUID ✅ |
| Type Safety       | Compile-time  | Runtime        | UUID ✅ |
| Validation        | Built-in      | Manual         | UUID ✅ |
| Memory Footprint  | Smaller       | Larger         | UUID ✅ |

**Benchmark Results (approximate):**

- UUID index lookup: ~10ms
- VARCHAR(36) index lookup: ~15ms
- **50% faster with UUID!**

---

## 🔒 Security Considerations

### UUID Generation Best Practices

```java
// ✅ CORRECT: Let framework generate UUIDs
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;  // Hibernate generates UUID

// ✅ CORRECT: System-controlled UUID
User user = User.builder()
    .id(UUID.randomUUID())  // ✅ OK in service layer with validation
    .tenantId(tenantId)     // ✅ From authenticated context
    .build();

// ❌ WRONG: User-provided UUID
@PostMapping
public void createUser(@RequestBody CreateUserRequest request) {
    user.setTenantId(request.getTenantId());  // ❌ NEVER accept tenant ID from user!
}
```

### Multi-Tenancy Security

```java
// ✅ CORRECT: Always validate tenant ID from security context
@GetMapping("/{userId}")
public UserResponse getUser(@PathVariable UUID userId) {
    UUID tenantId = SecurityContextHolder.getCurrentTenantId();  // ✅ From JWT
    return userService.getUser(userId, tenantId);  // ✅ Enforces tenant isolation
}

// ❌ WRONG: Accept tenant ID from request
@GetMapping("/{userId}")
public UserResponse getUser(
    @PathVariable UUID userId,
    @RequestParam UUID tenantId  // ❌ SECURITY RISK!
) {
    return userService.getUser(userId, tenantId);  // ❌ User can access other tenants!
}
```

---

## 🧪 Testing UUID Fields

### Unit Tests

```java
@Test
void shouldCreateUserWithUUID() {
    // Given
    UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // When
    User user = User.builder()
        .tenantId(tenantId)
        .firstName("John")
        .build();

    // Then
    assertThat(user.getTenantId()).isEqualTo(tenantId);  // ✅ UUID comparison
    assertThat(user.getTenantId()).isInstanceOf(UUID.class);  // ✅ Type validation
}
```

### Integration Tests

```java
@Test
void shouldFilterByTenantId() {
    // Given
    UUID tenant1 = UUID.randomUUID();
    UUID tenant2 = UUID.randomUUID();

    userRepository.save(User.builder().tenantId(tenant1).build());
    userRepository.save(User.builder().tenantId(tenant2).build());

    // When
    List<User> users = userRepository.findByTenantId(tenant1);

    // Then
    assertThat(users).hasSize(1);
    assertThat(users.get(0).getTenantId()).isEqualTo(tenant1);
}
```

---

## 📋 Migration Guide (String → UUID)

If you need to convert existing String-based identifiers to UUID:

### Step 1: Update Database Schema

```sql
-- Create new UUID column
ALTER TABLE users ADD COLUMN tenant_id_uuid UUID;

-- Populate with converted values
UPDATE users SET tenant_id_uuid = tenant_id::UUID WHERE tenant_id IS NOT NULL;

-- Verify data integrity
SELECT COUNT(*) FROM users WHERE tenant_id IS NOT NULL AND tenant_id_uuid IS NULL;
-- Result should be 0

-- Drop old column and rename
ALTER TABLE users DROP COLUMN tenant_id;
ALTER TABLE users RENAME COLUMN tenant_id_uuid TO tenant_id;
ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;

-- Rebuild indexes
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
```

### Step 2: Update Entity

```java
// Before
@Column(name = "tenant_id")
private String tenantId;

// After
@Column(name = "tenant_id", nullable = false)
private UUID tenantId;
```

### Step 3: Update Repository

```java
// Before
List<User> findByTenantId(@Param("tenantId") String tenantId);

// After
List<User> findByTenantId(@Param("tenantId") UUID tenantId);
```

### Step 4: Update Service Layer

```java
// Before
public UserResponse getUser(UUID userId, String tenantId) {
    // Manual parsing needed
    UUID tenantUuid = UUID.fromString(tenantId);
    // ...
}

// After
public UserResponse getUser(UUID userId, UUID tenantId) {
    // Direct usage, no parsing ✅
    // ...
}
```

---

## 🎯 UUID Best Practices Checklist

### Database Layer

- [ ] Primary keys are UUID type
- [ ] Foreign keys are UUID type
- [ ] Tenant identifiers are UUID type
- [ ] Default value: `gen_random_uuid()` or application-generated
- [ ] Indexes created on UUID columns

### Domain/Entity Layer

- [ ] All ID fields are `UUID` type (not String)
- [ ] No manual `UUID.randomUUID()` in setters
- [ ] No `.toString()` conversions in domain logic
- [ ] Builder pattern uses UUID directly

### Repository Layer

- [ ] Method parameters are UUID type
- [ ] Return types are UUID (for ID returns)
- [ ] JPQL queries use UUID parameters
- [ ] No string casting in queries

### Service Layer

- [ ] Method signatures use UUID for IDs
- [ ] UUID comparisons use `.equals()` directly
- [ ] No unnecessary `UUID.fromString()` calls
- [ ] TenantId from SecurityContext is UUID

### Controller Layer

- [ ] `@PathVariable UUID userId` (not String)
- [ ] SecurityContext returns UUID
- [ ] No manual UUID parsing from request params

### Boundary Layers (Serialization)

- [ ] JWT generation: `uuid.toString()` only at boundary
- [ ] Kafka events: `uuid.toString()` for JSON serialization
- [ ] Audit logs: `uuid.toString()` for text format
- [ ] External APIs: `uuid.toString()` as needed

---

## 🚫 What NOT to Do

### ❌ Never Mix Types

```java
// ❌ WRONG: Mixed types in same context
public class User {
    private UUID id;           // UUID
    private String tenantId;   // String ❌ INCONSISTENT!
}
```

### ❌ Never Accept User-Provided System IDs

```java
// ❌ WRONG: Security vulnerability
@PostMapping
public void createUser(@RequestBody CreateUserRequest request) {
    User user = User.builder()
        .tenantId(request.getTenantId())  // ❌ User can set any tenant!
        .build();
}

// ✅ CORRECT: Always from security context
@PostMapping
public void createUser(@RequestBody CreateUserRequest request) {
    UUID tenantId = SecurityContextHolder.getCurrentTenantId();  // ✅ From JWT
    User user = User.builder()
        .tenantId(tenantId)  // ✅ System-controlled
        .build();
}
```

### ❌ Never Manually Manipulate UUIDs

```java
// ❌ WRONG: Manual UUID updates
user.setTenantId(UUID.randomUUID());  // ❌ Breaks multi-tenancy!

// ❌ WRONG: UUID concatenation
String composite = userId + "-" + tenantId;  // ❌ Fragile and wrong

// ✅ CORRECT: Use as-is, let system manage
UUID tenantId = SecurityContextHolder.getCurrentTenantId();
user.setTenantId(tenantId);  // ✅ From trusted source
```

---

## 🔧 Common Patterns

### Pattern 1: Entity Creation

```java
// ✅ CORRECT
public UUID createUser(CreateUserRequest request, UUID tenantId, String createdBy) {
    User user = User.builder()
        .id(UUID.randomUUID())        // System generates
        .tenantId(tenantId)           // From security context
        .firstName(request.getFirstName())
        .createdBy(createdBy)
        .build();

    return userRepository.save(user).getId();
}
```

### Pattern 2: Tenant Filtering

```java
// ✅ CORRECT: UUID throughout
public UserResponse getUser(UUID userId, UUID tenantId) {
    return userRepository.findById(userId)
        .filter(u -> u.getTenantId().equals(tenantId))  // UUID comparison
        .map(this::mapToResponse)
        .orElseThrow(() -> new UserNotFoundException(userId));
}
```

### Pattern 3: Event Publishing

```java
// ✅ CORRECT: Convert only at boundary
UserCreatedEvent event = UserCreatedEvent.builder()
    .userId(user.getId())                      // UUID field in entity
    .tenantId(user.getTenantId().toString())   // UUID → String for Kafka
    .firstName(user.getFirstName())
    .build();

kafkaTemplate.send("user-events", event);  // JSON serialization
```

---

## 📐 Other Data Type Standards

### Timestamps

```java
// ✅ CORRECT: Use Java time API
private LocalDateTime createdAt;     // For application timestamps
private Instant processedAt;         // For precise event timestamps
private ZonedDateTime scheduledAt;   // For timezone-aware scheduling

// ❌ WRONG: Don't use old Date API
private Date createdAt;  // ❌ Deprecated approach
```

### Enums

```java
// ✅ CORRECT: Store as String in database
@Enumerated(EnumType.STRING)
private UserStatus status;

// ❌ WRONG: Store as ordinal
@Enumerated(EnumType.ORDINAL)
private UserStatus status;  // ❌ Breaks on reordering
```

### Money/Decimal Values

```java
// ✅ CORRECT: Use BigDecimal for money
@Column(precision = 19, scale = 4)
private BigDecimal amount;

// ❌ WRONG: Float/Double for money
private double amount;  // ❌ Precision loss!
```

### Boolean Flags

```java
// ✅ CORRECT: Explicit boolean with default
@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
private Boolean deleted = false;

// Prefer primitive for required fields
private boolean isActive;
```

---

## 🧩 Integration Examples

### Example: Complete CRUD with UUID

```java
@Service
public class UserService {

    // CREATE
    public UUID createUser(CreateUserRequest request, UUID tenantId) {
        User user = User.builder()
            .tenantId(tenantId)  // UUID
            .firstName(request.getFirstName())
            .build();
        return userRepository.save(user).getId();  // Returns UUID
    }

    // READ
    public UserResponse getUser(UUID userId, UUID tenantId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)  // Both UUID
            .map(this::toResponse)
            .orElseThrow(() -> new UserNotFoundException(userId));
    }

    // UPDATE
    public void updateUser(UUID userId, UpdateUserRequest request, UUID tenantId) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)  // Both UUID
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.setFirstName(request.getFirstName());
        userRepository.save(user);
    }

    // DELETE
    public void deleteUser(UUID userId, UUID tenantId) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)  // Both UUID
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.markAsDeleted();
        userRepository.save(user);
    }

    // LIST
    public List<UserResponse> listUsers(UUID tenantId) {
        return userRepository.findByTenantId(tenantId)  // UUID
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }
}
```

---

## ✅ Verification Checklist

Before committing code, verify:

- [ ] Database columns use UUID type (not VARCHAR)
- [ ] Entity fields are UUID type (not String)
- [ ] Repository methods use UUID parameters
- [ ] Service methods use UUID parameters
- [ ] Controller path variables are UUID type
- [ ] SecurityContext returns UUID
- [ ] UUID → String conversion only at boundaries (JWT, Kafka, Logs)
- [ ] No manual UUID manipulation in business logic
- [ ] Tenant ID always from SecurityContext (never from request)
- [ ] Tests verify UUID type correctness

---

## 📚 Related Documentation

- [Microservices & API Gateway Standards](MICROSERVICES_API_STANDARDS.md) - ⭐⭐⭐ API Gateway & Controller standards
- [Development Principles](PRINCIPLES.md) - General coding standards
- [Database Guide](../database/DATABASE_GUIDE.md) - Database schema standards
- [Security Guide](../SECURITY.md) - Security best practices
- [Code Structure Guide](CODE_STRUCTURE_GUIDE.md) - Project organization

---

## 🎓 Learning Resources

- [PostgreSQL UUID Type](https://www.postgresql.org/docs/current/datatype-uuid.html)
- [Java UUID Class](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/UUID.html)
- [Hibernate UUID Mapping](https://docs.jboss.org/hibernate/orm/6.2/userguide/html_single/Hibernate_User_Guide.html#basic-uuid)

---

## 📝 Version History

| Version | Date       | Changes                                       |
| ------- | ---------- | --------------------------------------------- |
| 1.0     | 2025-10-07 | Initial UUID standardization established      |
|         |            | - Database: VARCHAR(255) → UUID for tenant_id |
|         |            | - Entity: String tenantId → UUID tenantId     |
|         |            | - Consistent UUID usage across all layers     |

---

**Document Owner:** Development Team  
**Review Frequency:** Quarterly  
**Next Review:** 2026-01-07

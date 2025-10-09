# 🛡️ Policy-Based Authorization - Principles & Standards

**Purpose:** Kod kalitesini korumak, mimari bütünlüğü sağlamak  
**Scope:** Policy Authorization implementation için özel kurallar  
**Mandatory:** ✅ Bu kurallara UYULMALIDIR  
**Review:** Her PR bu kurallara göre kontrol edilecek  
**Status:** ✅ ACTIVE & ENFORCED  
**Last Updated:** 2025-10-09 19:20 UTC+1

---

## 🎯 Core Principles (MANDATORY)

### 1. ⭐ UUID Type Safety (CRITICAL)

```java
// ✅ DOĞRU
@Entity
public class User extends BaseEntity {
    @Column(name = "company_id")
    private UUID companyId;  // UUID type
}

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdAndCompanyId(UUID id, UUID companyId);  // UUID parameters
}

// ❌ YANLIŞ
private String companyId;  // ASLA String kullanma!
Optional<User> findById(String id);  // ASLA String parameter!
```

**Rules:**

- Database columns: `UUID` type (not `VARCHAR`)
- Entity fields: `UUID` type (not `String`)
- Repository methods: `UUID` parameters
- Service methods: `UUID` parameters
- Controller `@PathVariable`: `UUID` type
- DTO → Entity conversion: Handle String → UUID at boundary
- **Exception:** Kafka events, logs, external APIs use String

**Why?** Type safety, prevents ID manipulation, compile-time validation

---

### 2. 🏗️ Clean Architecture Layers

```
Presentation → Application → Domain → Infrastructure
     (API)       (Service)   (Entity)   (Repository)
```

**Rules:**

- **API Layer:** HTTP concerns only, no business logic
- **Application Layer:** Orchestration, transaction boundaries
- **Domain Layer:** Business rules, invariants, domain events
- **Infrastructure Layer:** External dependencies, DB, messaging

```java
// ✅ DOĞRU
@RestController
public class PolicyController {
    private final PolicyEngine policyEngine;  // Delegate

    @GetMapping("/check")
    public ApiResponse<PolicyDecision> check(@AuthenticationPrincipal SecurityContext ctx) {
        return ApiResponse.success(policyEngine.evaluate(ctx, request));
    }
}

// ❌ YANLIŞ
@RestController
public class PolicyController {
    @GetMapping("/check")
    public ApiResponse<PolicyDecision> check() {
        // Business logic in controller!
        if (companyType == INTERNAL) {
            return ALLOW;
        }
    }
}
```

---

### 3. 📏 Single Responsibility Principle (SOLID-S)

**File Size Limits:**

- **Service:** ~150 lines (max 200)
- **Mapper:** ~120 lines (max 150)
- **Validator:** ~60 lines (max 80)
- **Controller:** ~120 lines (max 150)
- **Entity (Aggregate):** ~250 lines (max 300)

**Split When Exceeded:**

```java
// ❌ YANLIŞ: UserService.java (400 lines)
public class UserService {
    public void createUser() { }
    public UserDTO toDTO(User user) { }  // Mapping logic!
    public void validateUser() { }       // Validation logic!
    public List<User> searchUsers() { }  // Search logic!
}

// ✅ DOĞRU: Split into multiple classes
public class UserService {  // 150 lines
    private final UserMapper mapper;
    private final UserValidator validator;
    private final UserSearchService searchService;

    public void createUser() {
        validator.validate(request);  // Delegate
        User user = mapper.toEntity(request);  // Delegate
        // ... business logic only
    }
}

public class UserMapper { }  // 120 lines
public class UserValidator { }  // 60 lines
public class UserSearchService { }  // 80 lines
```

---

### 4. 🚫 DRY (Don't Repeat Yourself)

```java
// ❌ YANLIŞ: Manual extraction (boilerplate everywhere)
@GetMapping
public Response getUsers(Authentication auth) {
    String userId = (String) auth.getPrincipal();
    UUID tenantId = UUID.fromString((String) auth.getDetails());
    // Repetitive boilerplate code!
}

// ✅ DOĞRU: Spring Security native @AuthenticationPrincipal
@GetMapping
public Response getUsers(@AuthenticationPrincipal SecurityContext ctx) {
    ctx.getTenantId();  // Clean!
    ctx.getUserId();    // Clean!
}
```

**No Code Duplication:**

- Use `shared` modules for common logic
- Use Spring Security's `@AuthenticationPrincipal` for security context
- Use base classes (`BaseEntity`, `BaseException`)
- Use message keys instead of hard-coded strings

---

### 5. 🌍 Centralized Constants (MANDATORY)

**Policy Constants Example:**

```java
// ✅ DOĞRU: PolicyConstants class
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PolicyConstants {

    // Decision types
    public static final String DECISION_ALLOW = "ALLOW";
    public static final String DECISION_DENY = "DENY";

    // Policy version
    public static final String POLICY_VERSION_DEFAULT = "1.0";

    // Cache configuration
    public static final int CACHE_TTL_MINUTES = 5;
    public static final String CACHE_KEY_SEPARATOR = "::";

    // Permission status
    public static final String PERMISSION_STATUS_ACTIVE = "ACTIVE";

    // Denial reasons
    public static final String REASON_GUARDRAIL = "company_type_guardrail_";
    public static final String REASON_PLATFORM = "platform_policy_";
    public static final String REASON_USER_GRANT = "user_grant_";
    public static final String REASON_SCOPE = "scope_violation_";
    public static final String REASON_ROLE = "role_access_denied";
    public static final String REASON_ERROR = "policy_evaluation_error";
}

// Usage
String decision = PolicyConstants.DECISION_ALLOW;
String version = PolicyConstants.POLICY_VERSION_DEFAULT;
int ttl = PolicyConstants.CACHE_TTL_MINUTES;
```

**Rules:**

- All magic strings → Constants
- All magic numbers → Constants
- Constants class in infrastructure package
- `private` constructor (utility class)
- `public static final` fields
- Grouped by category

**Why?**

- Easy to change
- Type-safe
- IDE autocomplete
- Prevents typos
- Single source of truth

---

### 6. 🌍 Centralized Error Messages (MANDATORY)

```java
// ❌ YANLIŞ: Hard-coded
throw new Exception("User not found: " + userId);
throw new Exception("Company type must be INTERNAL");

// ✅ DOĞRU: Message keys
throw new ResourceNotFoundException(
    ErrorMessageKeys.USER_NOT_FOUND,  // Key
    userId.toString()                 // Parameter
);

throw new ValidationException(
    ErrorMessageKeys.INVALID_COMPANY_TYPE,
    companyType.name()
);
```

**Structure:**

```
shared-domain/message/
  ├── ErrorMessageKeys.java           // Constants
  └── PolicyErrorMessageKeys.java     // Policy-specific

shared-infrastructure/resources/messages/
  ├── errors_en.properties            // English
  ├── errors_tr.properties            // Turkish
  ├── policy_errors_en.properties     // Policy-specific EN
  └── policy_errors_tr.properties     // Policy-specific TR
```

**Benefits:**

- i18n support (EN/TR automatic)
- Consistency %100
- Easy to change
- Test-friendly (key-based assertions)

---

### 7. 🎭 Separation of Concerns

| Concern            | Belongs To       | Example                         |
| ------------------ | ---------------- | ------------------------------- |
| **HTTP handling**  | Controller       | `@GetMapping`, `ResponseEntity` |
| **Business logic** | Service          | Transaction, orchestration      |
| **Mapping**        | Mapper           | DTO ↔ Entity                    |
| **Validation**     | Validator        | Business rules                  |
| **Data access**    | Repository       | Queries                         |
| **Domain logic**   | Entity/Aggregate | Invariants, state transitions   |

```java
// ✅ DOĞRU: Each class has ONE job

@RestController  // HTTP only
public class UserController { }

@Service  // Business orchestration
public class UserService { }

@Component  // Mapping only
public class UserMapper { }

@Component  // Validation only
public class UserValidator { }

@Repository  // Data access only
public interface UserRepository { }

@Entity  // Domain logic only
public class User extends BaseEntity { }
```

---

## 🔐 Policy-Specific Rules

### 8. PDP Must Be Stateless

```java
// ✅ DOĞRU: Stateless PolicyEngine
@Component
public class PolicyEngine {
    public PolicyDecision evaluate(PolicyContext context) {
        // No instance state!
        // All inputs from context
        // Results from evaluation logic
    }
}

// ❌ YANLIŞ: Stateful
@Component
public class PolicyEngine {
    private PolicyContext currentContext;  // BAD!
    private List<PolicyDecision> cache;    // BAD!
}
```

**Rules:**

- No instance variables (except injected dependencies)
- All data from method parameters
- Cache in Redis (external state)
- Thread-safe by design

---

### 9. Policy Decision Immutability

```java
// ✅ DOĞRU: Immutable decision
@Value  // Lombok @Value = immutable
@Builder(toBuilder = true)  // ⚠️ Lombok 1.18.24+ için toBuilder açık olmalı
public class PolicyDecision {
    private final boolean allowed;
    private final String reason;
    private final String policyVersion;
    private final LocalDateTime decidedAt;
    private final String correlationId;  // 🔥 Gateway → Service → Audit izlenebilirlik
    // No setters!
}

// ❌ YANLIŞ: Mutable
@Data  // Has setters!
public class PolicyDecision {
    private boolean allowed;
    // Can be changed after creation!
}
```

**Why?** Audit trail integrity, thread safety, prevents tampering

---

### 10. First DENY Wins (Security)

```java
// ✅ DOĞRU: Check order
public PolicyDecision evaluate(PolicyContext ctx) {
    // 1. Company Type guardrail
    if (!isCompanyTypeAllowed(ctx)) {
        return PolicyDecision.deny("company_type_guardrail");  // STOP!
    }

    // 2. Platform policy
    if (hasPlatformDeny(ctx)) {
        return PolicyDecision.deny("platform_policy");  // STOP!
    }

    // 3. User-specific DENY
    if (hasUserDeny(ctx)) {
        return PolicyDecision.deny("user_override_deny");  // STOP!
    }

    // 4. Role default ALLOW
    if (hasRoleAllow(ctx)) {
        // Continue to next check
    }

    // 5. Data scope check
    if (!isScopeValid(ctx)) {
        return PolicyDecision.deny("scope_invalid");  // STOP!
    }

    return PolicyDecision.allow("role_default + scope_valid");
}
```

**Rule:** Deny is stronger than allow. Fail-safe by default.

---

### 11. Double Validation (Defense in Depth)

```java
// 1️⃣ Gateway (PEP) - First check
@Component
public class PolicyEnforcementFilter {
    public void filter(ServerWebExchange exchange) {
        PolicyDecision decision = pdpClient.evaluate(context);
        if (!decision.isAllowed()) {
            return unauthorizedResponse();
        }
        // Add decision to headers
        addPolicyHeaders(exchange, decision);
    }
}

// 2️⃣ Service - Second check
@Service
public class UserService {
    private final ScopeValidator scopeValidator;

    public User getUser(UUID userId, SecurityContext ctx) {
        // Validate again!
        if (!scopeValidator.canAccess(userId, ctx)) {
            throw new ForbiddenException("Scope validation failed");
        }
        return userRepository.findById(userId);
    }
}
```

**Why?** Gateway bypass protection, manipulation detection

---

### 12. Scope Validation Pattern

```java
// ✅ DOĞRU: Explicit scope validation
@Component
public class ScopeValidator {

    public boolean canAccess(UUID resourceOwnerId, SecurityContext ctx) {
        return switch (ctx.getDefaultScope()) {
            case SELF -> resourceOwnerId.equals(UUID.fromString(ctx.getUserId()));
            case COMPANY -> isSameCompany(resourceOwnerId, ctx.getCompanyId());
            case CROSS_COMPANY -> hasRelationship(resourceOwnerId, ctx);
            case GLOBAL -> ctx.hasAnyRole("SUPER_ADMIN");
        };
    }

    private boolean isSameCompany(UUID resourceOwnerId, UUID userCompanyId) {
        UUID resourceCompanyId = getUserCompanyId(resourceOwnerId);
        return resourceCompanyId.equals(userCompanyId);
    }
}

// ❌ YANLIŞ: Implicit assumption
public User getUser(UUID userId) {
    return userRepository.findById(userId);  // No scope check!
}
```

---

### 13. Audit Everything

```java
// ✅ DOĞRU: Comprehensive audit
@Service
public class PolicyAuditService {

    public void logDecision(PolicyContext ctx, PolicyDecision decision, long latencyMs) {
        PolicyDecisionAudit audit = PolicyDecisionAudit.builder()
            .userId(ctx.getUserId())
            .companyId(ctx.getCompanyId())
            .companyType(ctx.getCompanyType())
            .endpoint(ctx.getEndpoint())
            .operation(ctx.getOperation())
            .scope(ctx.getScope())
            .decision(decision.isAllowed() ? "ALLOW" : "DENY")
            .reason(decision.getReason())  // ⭐ WHY?
            .policyVersion(decision.getPolicyVersion())
            .requestIp(ctx.getRequestIp())
            .requestId(ctx.getRequestId())
            .latencyMs(latencyMs)
            .createdAt(LocalDateTime.now())
            .build();

        auditRepository.save(audit);  // Async preferred
    }
}
```

**Requirements:**

- Log BOTH ALLOW and DENY
- Include reason (explainability)
- Include policy version (traceability)
- Include latency (performance monitoring)
- **Async logging (MANDATORY)** - Use Kafka event queue, never block main thread
- Include correlationId for distributed tracing

**Implementation:**

```java
// MANDATORY: Async audit with Kafka
@Service
public class PolicyAuditService {

    @Autowired
    private KafkaTemplate<String, PolicyAuditEvent> kafkaTemplate;

    public void logDecisionAsync(PolicyContext ctx, PolicyDecision decision, long latencyMs) {
        PolicyAuditEvent event = PolicyAuditEvent.builder()
            .userId(ctx.getUserId())
            .decision(decision.isAllowed() ? "ALLOW" : "DENY")
            .reason(decision.getReason())
            .correlationId(decision.getCorrelationId())  // 🔥 Trace across services
            .latencyMs(latencyMs)
            .build();

        // Non-blocking send
        kafkaTemplate.send("policy.audit", event);
    }
}
```

---

## 📦 Shared vs Service-Specific

### Decision Tree

```
Is this used by ALL microservices?
├─ YES → Put in shared/
│  └─ Example: PolicyEngine, CompanyType enum, ErrorMessageKeys
│
└─ NO → Is this used by 2+ microservices?
   ├─ YES → Put in shared/ (future-proof)
   │  └─ Example: SecurityContext, ScopeValidator
   │
   └─ NO → Put in specific service/
      └─ Example: DepartmentService (only Company Service needs it)
```

### Examples

| Component               | Shared ✅ | Service ❌ | Reason                                        |
| ----------------------- | --------- | ---------- | --------------------------------------------- |
| `PolicyEngine` (PDP)    | ✅        | -          | All services need to check policies           |
| `CompanyType` enum      | ✅        | -          | Used in User, Company, Policy contexts        |
| `SecurityContext`       | ✅        | -          | Every request needs this                      |
| `ScopeValidator`        | ✅        | -          | All services validate scope                   |
| `ErrorMessageKeys`      | ✅        | -          | Centralized message management                |
| `Department` entity     | -         | ✅ Company | Only Company Service manages departments      |
| `PolicyRegistry` entity | ✅        | -          | PDP needs this, may be separate service later |
| `UserPermission` entity | ✅        | -          | Policy system, not User-specific              |

---

## 🧪 Testing Standards

### Unit Test Rules

```java
// ✅ DOĞRU: Isolated unit test
@ExtendWith(MockitoExtension.class)
class PolicyEngineTest {

    @Mock
    private CompanyTypeGuard companyTypeGuard;

    @Mock
    private ScopeResolver scopeResolver;

    @InjectMocks
    private PolicyEngine policyEngine;

    @Test
    @DisplayName("Should DENY when company type is CUSTOMER")
    void shouldDenyCustomerWrite() {
        // Given
        PolicyContext ctx = createContext(CompanyType.CUSTOMER, OperationType.WRITE);
        when(companyTypeGuard.isAllowed(any())).thenReturn(false);

        // When
        PolicyDecision decision = policyEngine.evaluate(ctx);

        // Then
        assertFalse(decision.isAllowed());
        assertEquals("company_type_guardrail", decision.getReason());
        verify(companyTypeGuard).isAllowed(ctx);
    }
}
```

**Rules:**

- Use `@ExtendWith(MockitoExtension.class)`
- Mock external dependencies
- Test ONE thing per test
- Use `@DisplayName` for clarity
- Follow Given-When-Then pattern
- Assert on reason (not just boolean)

---

### Integration Test Rules

```java
// ✅ DOĞRU: Integration test with real DB
@SpringBootTest
@Transactional
@TestPropertySource(locations = "classpath:application-test.yml")
class PolicyEngineIntegrationTest {

    @Autowired
    private PolicyEngine policyEngine;

    @Autowired
    private PolicyRegistryRepository policyRegistry;

    @Test
    void shouldAllowInternalUserWithRolePermission() {
        // Given: Policy in DB
        PolicyRegistry policy = createPolicy("WRITE:USER/COMPANY", List.of("ADMIN"));
        policyRegistry.save(policy);

        PolicyContext ctx = createContext(
            CompanyType.INTERNAL,
            UserRole.ADMIN,
            OperationType.WRITE
        );

        // When
        PolicyDecision decision = policyEngine.evaluate(ctx);

        // Then
        assertTrue(decision.isAllowed());
        assertThat(decision.getReason()).contains("role_default");
    }
}
```

---

## 🚨 Common Pitfalls (AVOID!)

### ❌ Pitfall 1: Anemic Domain Model

```java
// ❌ WRONG: Getters/setters only
@Entity
public class PolicyDecision {
    private boolean allowed;
    private String reason;
    // Only getters/setters, no behavior!
}

// ✅ RIGHT: Rich domain model
@Entity
public class PolicyDecision {
    public boolean isExpired() {
        return decidedAt.plusMinutes(5).isBefore(LocalDateTime.now());
    }

    public boolean isDeny() {
        return !allowed;
    }

    public String getAuditMessage() {
        return String.format("[%s] %s - %s",
            decidedAt, allowed ? "ALLOW" : "DENY", reason);
    }
}
```

---

### ❌ Pitfall 2: Service God Class

```java
// ❌ WRONG: 500-line service
public class PolicyService {
    public PolicyDecision evaluate() { }
    public void audit() { }
    public void cache() { }
    public PolicyDTO toDTO() { }
    public void validate() { }
    // ... 50 more methods
}

// ✅ RIGHT: Split responsibilities
public class PolicyEngine { }  // Evaluation only
public class PolicyAuditService { }  // Audit only
public class PolicyCacheService { }  // Cache only
public class PolicyMapper { }  // Mapping only
public class PolicyValidator { }  // Validation only
```

---

### ❌ Pitfall 3: String-based IDs

```java
// ❌ WRONG
public PolicyDecision evaluate(String userId, String companyId) {
    // Can pass wrong format, null, etc.
}

// ✅ RIGHT
public PolicyDecision evaluate(UUID userId, UUID companyId) {
    // Type-safe, compile-time check
}
```

---

### ❌ Pitfall 4: Ignoring Scope

```java
// ❌ WRONG: No scope check
public List<User> listUsers(UUID companyId) {
    return userRepository.findByCompanyId(companyId);  // What if user is CUSTOMER?
}

// ✅ RIGHT: Scope-aware
public List<User> listUsers(UUID companyId, SecurityContext ctx) {
    scopeValidator.validateCompanyAccess(companyId, ctx);  // Throws if not allowed
    return userRepository.findByCompanyId(companyId);
}
```

---

## ✅ Pre-Commit Checklist

Before committing code, verify:

- [ ] **UUID type safety:** No String IDs in entity/repository/service
- [ ] **File size limits:** Service ~150, Mapper ~120, Entity ~250 lines
- [ ] **No hard-coded strings:** Use message keys
- [ ] **No code duplication:** Use shared modules
- [ ] **Proper layer separation:** No business logic in controllers
- [ ] **All public methods have JavaDoc**
- [ ] **Unit tests written:** Coverage > 80%
- [ ] **Integration tests for critical paths**
- [ ] **No `System.out.println` or `e.printStackTrace()`**
- [ ] **Proper exception handling:** Use custom exceptions
- [ ] **Lombok annotations correct:** `@Data` vs `@Value`
- [ ] **Null-safe code:** Use `Optional` where appropriate
- [ ] **Transaction boundaries correct:** `@Transactional` on service methods
- [ ] **Audit logging for critical operations**
- [ ] **Code formatted (IntelliJ default)**
- [ ] **No warnings or errors in IDE**

---

## 📚 Reference Documents

- [Development Principles](PRINCIPLES.md) - General coding standards
- [Code Structure Guide](CODE_STRUCTURE_GUIDE.md) - Where to put code
- [Architecture Document](../ARCHITECTURE.md) - System architecture
- [Data Types Standards](DATA_TYPES_STANDARDS.md) - UUID guidelines
- [Policy TODO](POLICY_AUTHORIZATION_IMPLEMENTATION_TODO.md) - Implementation tasks

---

## 🎯 Summary

**Remember:**

1. ⭐ **UUID type safety** is MANDATORY
2. 📏 **Single Responsibility** - Keep files small
3. 🚫 **DRY** - No code duplication
4. 🌍 **Centralized messages** - i18n support
5. 🔐 **First DENY wins** - Security first
6. 🛡️ **Double validation** - Gateway + Service
7. 📊 **Audit everything** - WHY? is important
8. 🧪 **Test thoroughly** - Unit + Integration

**When in doubt, ask:** "Is this following Clean Architecture and SOLID principles?"

---

**Document Owner:** Tech Lead  
**Reviewers:** All Developers  
**Status:** ✅ Active & Enforced  
**Last Updated:** 2025-10-09 19:20 UTC+1  
**Version:** 2.0 (Added PolicyConstants principle)

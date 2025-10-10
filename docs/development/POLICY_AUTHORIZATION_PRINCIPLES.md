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

### 11. Defense-in-Depth Pattern ⭐ UPDATED (Phase 3)

**Architecture:** 2-Layer Security

```
Layer 1: API Gateway (Primary Enforcement)
    ↓
Layer 2: Microservices (Secondary Enforcement)
```

#### Implementation Pattern

**1️⃣ Gateway Layer (PolicyEnforcementFilter)**

```java
// api-gateway/filter/PolicyEnforcementFilter.java
@Component
@RequiredArgsConstructor
public class PolicyEnforcementFilter implements GlobalFilter, Ordered {

    private final PolicyEngine policyEngine;
    private final ReactivePolicyAuditPublisher auditPublisher;  // ✅ Phase 3

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Build policy context
        PolicyContext context = buildPolicyContext(request, userId, tenantId);
        long startTime = System.currentTimeMillis();

        // Evaluate policy (async)
        return evaluatePolicyAsync(context)
            .flatMap(decision -> {
                long latencyMs = System.currentTimeMillis() - startTime;

                // Publish audit event (fire-and-forget) ✅ Phase 3
                auditPublisher.publishDecision(context, decision, latencyMs)
                    .subscribe(null, error -> log.error("Audit failed"));

                if (decision.isDenied()) {
                    return responseHelper.forbidden(exchange, decision.getReason());
                }

                return chain.filter(exchange);
            });
    }

    @Override
    public int getOrder() {
        return FilterOrder.POLICY_FILTER;  // -50 (after JWT)
    }
}
```

**2️⃣ Service Layer (PolicyValidationFilter)** ⭐ NEW

```java
// user-service/infrastructure/security/PolicyValidationFilter.java
@Component
@Order(2)  // After JwtAuthenticationFilter
@RequiredArgsConstructor
public class PolicyValidationFilter implements Filter {

    private final PolicyEngine policyEngine;

    private static final List<String> PUBLIC_PATHS = List.of(
        "/actuator",
        "/api/public",
        "/api/v1/auth/login"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Skip public paths
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Get SecurityContext from Spring Security
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof SecurityContext)) {
            chain.doFilter(request, response);
            return;
        }

        SecurityContext securityContext = (SecurityContext) authentication.getPrincipal();

        // Build PolicyContext and evaluate (secondary check)
        PolicyContext policyContext = buildPolicyContext(httpRequest, securityContext);
        PolicyDecision decision = policyEngine.evaluate(policyContext);

        if (decision.isDenied()) {
            log.warn("Policy DENIED (secondary check) - User: {}, Path: {}, Reason: {}",
                securityContext.getUserId(), path, decision.getReason());
            throw new ForbiddenException(decision.getReason());
        }

        log.debug("Policy ALLOWED (secondary check) - User: {}, Path: {}",
            securityContext.getUserId(), path);

        // Continue filter chain
        chain.doFilter(request, response);
    }

    private PolicyContext buildPolicyContext(HttpServletRequest request, SecurityContext secCtx) {
        UUID userId = UUID.fromString(secCtx.getUserId());

        return PolicyContext.builder()
            .userId(userId)
            .companyId(secCtx.getTenantId())
            .companyType(secCtx.getCompanyType())
            .endpoint(request.getRequestURI())
            .httpMethod(request.getMethod())
            .operation(mapOperation(request.getMethod()))
            .scope(inferScope(request.getRequestURI()))
            .roles(extractRoles(secCtx))
            .correlationId(request.getHeader("X-Correlation-ID"))
            .requestIp(request.getRemoteAddr())
            .build();
    }
}
```

**Why Defense-in-Depth?**

| Scenario                       | Gateway Only    | Defense-in-Depth  |
| ------------------------------ | --------------- | ----------------- |
| Normal request                 | ✅ Protected    | ✅ Protected      |
| Gateway bypass (internal call) | ❌ Vulnerable   | ✅ Protected      |
| Gateway compromise             | ❌ Total breach | 🟡 Limited breach |
| Policy mismatch                | ❌ Undetected   | ✅ Detected       |

**Benefits:**

- ✅ **2-layer protection** (redundancy)
- ✅ **Gateway bypass immunity** (internal calls protected)
- ✅ **Fail-safe architecture** (deny on error)
- ✅ **Consistent enforcement** (same PolicyEngine)

**Performance Impact:**

- Gateway: ~40ms (policy + audit)
- Service: ~10ms (cached evaluation)
- **Total:** +50ms (acceptable for security gain)

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

## 🆕 Phase 3 Implementation Patterns (Oct 2025)

### Pattern 1: Optional Dependency Injection ⭐

**Problem:** PolicyEngine needs PolicyRegistryRepository, but Gateway doesn't have database access.

**Solution:** Optional dependency with `@Autowired(required = false)`

```java
@Component
public class PolicyEngine {

    private final CompanyTypeGuard companyTypeGuard;  // Required
    private final ScopeResolver scopeResolver;  // Required
    private final PolicyRegistryRepository policyRegistryRepository;  // Optional

    public PolicyEngine(
            CompanyTypeGuard companyTypeGuard,
            ScopeResolver scopeResolver,
            @Autowired(required = false) PolicyRegistryRepository policyRegistryRepository) {
        this.companyTypeGuard = companyTypeGuard;
        this.scopeResolver = scopeResolver;
        this.policyRegistryRepository = policyRegistryRepository;
    }

    private boolean checkRoleDefaultAccess(PolicyContext context) {
        // Try PolicyRegistry lookup (if available)
        if (policyRegistryRepository != null) {
            Optional<PolicyRegistry> policy = policyRegistryRepository
                .findByEndpointAndOperationAndActiveTrue(endpoint, operation);

            if (policy.isPresent()) {
                return policy.get().hasRoleAccess(userRole);
            }
        }

        // Fallback to hardcoded logic (backward compatible)
        return checkFallbackRoleAccess(context);
    }
}
```

**Benefits:**

- ✅ Works in both Gateway (no DB) and Services (with DB)
- ✅ Graceful degradation (fallback logic)
- ✅ Single PolicyEngine implementation for all contexts
- ✅ Database-driven when available

**When to Use:**

- Component used in multiple contexts (Gateway + Services)
- Dependency not available in all contexts
- Need graceful degradation strategy

---

### Pattern 2: Reactive Audit Publisher ⭐

**Problem:** Gateway is reactive (WebFlux), but PolicyAuditService uses blocking I/O (JPA).

**Solution:** Separate reactive publisher for Gateway.

```java
// Gateway-specific (Kafka-only, no database)
@Component
@RequiredArgsConstructor
public class ReactivePolicyAuditPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public Mono<Void> publishDecision(PolicyContext context, PolicyDecision decision, long latencyMs) {
        return Mono.fromRunnable(() -> publishSync(context, decision, latencyMs))
            .subscribeOn(Schedulers.boundedElastic())  // Offload to separate thread
            .onErrorResume(error -> {
                log.error("Audit failed: {}", error.getMessage());
                return Mono.empty();  // Fail-safe
            })
            .then();
    }

    private void publishSync(PolicyContext context, PolicyDecision decision, long latencyMs) {
        PolicyAuditEvent event = buildAuditEvent(context, decision, latencyMs);
        String eventJson = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("policy.audit", context.getCorrelationId(), eventJson);
    }
}

// Service-specific (DB + Kafka)
@Service
public class PolicyAuditService {

    private final PolicyDecisionAuditRepository auditRepository;  // JPA
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Async
    public void logDecision(PolicyContext context, PolicyDecision decision, long latencyMs) {
        // 1. Save to database (blocking)
        auditRepository.save(audit);

        // 2. Publish to Kafka (fire-and-forget)
        publishToKafka(audit);
    }
}
```

**Comparison:**

| Aspect          | PolicyAuditService | ReactivePolicyAuditPublisher |
| --------------- | ------------------ | ---------------------------- |
| **I/O Model**   | Blocking (JPA)     | Reactive (Non-blocking)      |
| **Database**    | PostgreSQL         | None (Kafka-only)            |
| **Context**     | Microservices      | API Gateway                  |
| **Pattern**     | DB + Kafka         | Kafka-only                   |
| **When to Use** | Services with DB   | Reactive Gateway             |

**Benefits:**

- ✅ Reactive-compatible (no blocking in Gateway)
- ✅ Decoupled architecture (Gateway doesn't need DB)
- ✅ Event-driven (Kafka for persistence)
- ✅ Fail-safe (audit error doesn't block request)

**When to Use:**

- Reactive context (WebFlux, Spring Cloud Gateway)
- No database access in component
- Need async audit without blocking
- Fire-and-forget pattern suitable

---

### Pattern 3: PolicyRegistry Lookup with Fallback ⭐

**Problem:** Need database-driven policies but must work without database.

**Solution:** Lookup + Fallback pattern

```java
private boolean checkRoleDefaultAccess(PolicyContext context) {
    // Step 1: Try PolicyRegistry lookup (database-driven)
    if (policyRegistryRepository != null && context.getEndpoint() != null) {
        try {
            Optional<PolicyRegistry> policyOpt = policyRegistryRepository
                .findByEndpointAndOperationAndActiveTrue(
                    context.getEndpoint(),
                    context.getOperation()
                );

            if (policyOpt.isPresent()) {
                PolicyRegistry policy = policyOpt.get();

                // Check default roles from database
                if (policy.getDefaultRoles() != null && !policy.getDefaultRoles().isEmpty()) {
                    return context.getRoles().stream()
                        .anyMatch(policy::hasRoleAccess);
                }

                // Policy exists but no role restrictions
                return true;
            }

        } catch (Exception e) {
            log.error("PolicyRegistry lookup failed. Falling back.", e);
            // Fall through to fallback logic
        }
    }

    // Step 2: Fallback to hardcoded logic (backward compatible)
    return checkFallbackRoleAccess(context);
}

private boolean checkFallbackRoleAccess(PolicyContext context) {
    // Hardcoded fallback rules
    if (context.hasAnyRole("ADMIN", "SUPER_ADMIN")) return true;
    if (context.hasAnyRole("MANAGER")) return true;
    if (context.hasAnyRole("USER")) return context.getOperation().isReadOnly();
    return false;
}
```

**Benefits:**

- ✅ Database-driven (flexible, runtime configurable)
- ✅ Fallback (works without database)
- ✅ Fail-safe (error doesn't break system)
- ✅ Backward compatible

**When to Use:**

- Need runtime configuration
- Component used in multiple contexts
- Database not always available
- Need zero-downtime policy updates

---

### Pattern 4: Filter Order Management ⭐

**Problem:** Filters must execute in correct order for security.

**Solution:** Centralized order constants + @Order annotation

```java
// constants/FilterOrder.java
public final class FilterOrder {
    public static final int JWT_FILTER = -100;      // FIRST
    public static final int POLICY_FILTER = -50;    // SECOND
    public static final int LOGGING_FILTER = 0;     // THIRD

    private FilterOrder() {}
}

// Gateway filter (Reactive)
@Component
public class PolicyEnforcementFilter implements GlobalFilter, Ordered {
    @Override
    public int getOrder() {
        return FilterOrder.POLICY_FILTER;  // -50
    }
}

// Service filter (Servlet)
@Component
@Order(2)  // After JwtAuthenticationFilter (Order 1)
public class PolicyValidationFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        // Secondary policy check
    }
}
```

**Execution Order:**

```
-100: JWT Authentication    → Validate token, extract claims
 -50: Policy Enforcement    → Check authorization
   0: Request Logging       → Log request/response
   1: JwtAuthenticationFilter (Service) → Re-validate
   2: PolicyValidationFilter (Service) → Secondary check
```

**Benefits:**

- ✅ Explicit execution order
- ✅ No magic numbers
- ✅ Easy to understand flow
- ✅ Centralized configuration

**When to Use:**

- Multiple filters in pipeline
- Order-dependent processing
- Need clear execution sequence

---

### Pattern 5: Fire-and-Forget Audit ⭐

**Problem:** Audit logging shouldn't block main request flow.

**Solution:** Fire-and-forget with error swallowing

```java
// Gateway (Reactive)
auditPublisher.publishDecision(context, decision, latencyMs)
    .subscribe(
        null,  // No success handler needed
        error -> log.error("Audit failed (non-blocking): {}", error.getMessage())
    );

// Service (Async)
@Async
public void logDecision(PolicyContext context, PolicyDecision decision, long latencyMs) {
    try {
        auditRepository.save(audit);
        publishToKafka(audit);
    } catch (Exception e) {
        // Fail-safe: Log error but don't throw
        log.error("Audit failed: {}", e.getMessage());
    }
}
```

**Principles:**

- ✅ **Non-blocking** (async execution)
- ✅ **Fail-safe** (error doesn't affect main flow)
- ✅ **Fire-and-forget** (no waiting for result)
- ✅ **Logged** (error tracking for monitoring)

**When to Use:**

- Audit logging
- Event publishing
- Non-critical side effects
- Performance-sensitive paths

---

### Pattern 6: Correlation ID Propagation ⭐

**Problem:** Need to trace request across multiple services.

**Solution:** Generate once, propagate everywhere

```java
// 1. Gateway generates (or accepts from client)
String correlationId = request.getHeader("X-Correlation-ID");
if (correlationId == null) {
    correlationId = UUID.randomUUID().toString();
}

// 2. Add to PolicyContext
PolicyContext context = PolicyContext.builder()
    .correlationId(correlationId)
    .build();

// 3. Use in audit event
PolicyAuditEvent event = PolicyAuditEvent.builder()
    .correlationId(correlationId)
    .build();

// 4. Use as Kafka message key (for ordering)
kafkaTemplate.send("policy.audit", correlationId, eventJson);

// 5. Propagate to downstream services
request.mutate()
    .header("X-Correlation-ID", correlationId)
    .build();
```

**Benefits:**

- ✅ **Distributed tracing** (track request across services)
- ✅ **Kafka ordering** (same correlation = same partition)
- ✅ **Log correlation** (group related logs)
- ✅ **Debug friendly** (trace entire request flow)

**When to Use:**

- Microservices architecture
- Distributed tracing needed
- Event ordering required
- Log aggregation systems

---

### Pattern 7: Build Context from SecurityContext ⭐

**Problem:** Need to convert SecurityContext to PolicyContext in filters.

**Solution:** Private helper method with consistent mapping

```java
@Component
public class PolicyValidationFilter implements Filter {

    private PolicyContext buildPolicyContext(HttpServletRequest request, SecurityContext secCtx) {
        // Parse userId (String → UUID)
        UUID userId = secCtx.getUserId() != null ?
            UUID.fromString(secCtx.getUserId()) : null;

        return PolicyContext.builder()
            .userId(userId)
            .companyId(secCtx.getTenantId())
            .companyType(secCtx.getCompanyType())
            .endpoint(request.getRequestURI())
            .httpMethod(request.getMethod())
            .operation(mapOperation(request.getMethod()))
            .scope(inferScope(request.getRequestURI()))
            .roles(extractRoles(secCtx))
            .correlationId(request.getHeader("X-Correlation-ID"))
            .requestId(request.getHeader("X-Request-ID"))
            .requestIp(request.getRemoteAddr())
            .build();
    }

    private OperationType mapOperation(String method) {
        return switch (method.toUpperCase()) {
            case "GET", "HEAD" -> OperationType.READ;
            case "POST", "PUT", "PATCH" -> OperationType.WRITE;
            case "DELETE" -> OperationType.DELETE;
            default -> OperationType.READ;
        };
    }

    private DataScope inferScope(String path) {
        if (path.contains("/me") || path.contains("/profile")) {
            return DataScope.SELF;
        }
        if (path.contains("/admin") || path.contains("/system")) {
            return DataScope.GLOBAL;
        }
        return DataScope.COMPANY;
    }

    private List<String> extractRoles(SecurityContext secCtx) {
        if (secCtx.getRoles() == null) return List.of();

        return Arrays.stream(secCtx.getRoles())
            .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
            .collect(Collectors.toList());
    }
}
```

**Benefits:**

- ✅ **Reusable** (same pattern in all services)
- ✅ **Type-safe** (String → UUID conversion)
- ✅ **Consistent** (same mapping logic everywhere)
- ✅ **Testable** (private methods can be tested)

**When to Use:**

- Filter implementations
- AOP aspects
- Interceptors
- Any place converting SecurityContext → PolicyContext

---

### Pattern 8: Kafka Event Factory Method ⭐

**Problem:** Creating Kafka events from domain objects is repetitive.

**Solution:** Static factory method on event class

```java
@Builder
public class PolicyAuditEvent extends DomainEvent {
    private UUID userId;
    private String decision;
    private String reason;
    // ... more fields

    /**
     * Factory method to create event from audit entity
     */
    public static PolicyAuditEvent fromAudit(PolicyDecisionAudit audit) {
        return PolicyAuditEvent.builder()
            .userId(audit.getUserId())
            .companyId(audit.getCompanyId())
            .decision(audit.getDecision())
            .reason(audit.getReason())
            .latencyMs(audit.getLatencyMs())
            .correlationId(audit.getCorrelationId())
            .timestamp(audit.getCreatedAt())
            .build();
    }
}

// Usage
PolicyAuditEvent event = PolicyAuditEvent.fromAudit(audit);
String eventJson = objectMapper.writeValueAsString(event);
kafkaTemplate.send("policy.audit", correlationId, eventJson);
```

**Benefits:**

- ✅ **DRY** (one place for conversion)
- ✅ **Type-safe** (compile-time check)
- ✅ **Self-documenting** (clear factory method)
- ✅ **Testable** (easy to unit test)

**When to Use:**

- Creating Kafka events from entities
- Converting between domain models
- Event-driven architecture
- Need consistent event creation

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

**Core Principles (Always):**

1. ⭐ **UUID type safety** is MANDATORY
2. 📏 **Single Responsibility** - Keep files small
3. 🚫 **DRY** - No code duplication
4. 🌍 **Centralized messages** - i18n support
5. 🔐 **First DENY wins** - Security first
6. 🛡️ **Double validation** - Gateway + Service
7. 📊 **Audit everything** - WHY? is important
8. 🧪 **Test thoroughly** - Unit + Integration

**Phase 3 Additions (New):**

9. 🛡️ **Defense-in-Depth** - PolicyValidationFilter in all services
10. 🔄 **Optional Dependencies** - `@Autowired(required = false)` pattern
11. ⚡ **Reactive Audit** - Non-blocking audit for Gateway
12. 🗄️ **Registry Lookup** - Database-driven + Fallback
13. 📡 **Correlation ID** - Distributed tracing everywhere
14. 🔥 **Fire-and-Forget** - Async audit without blocking

**When in doubt, ask:** "Is this following Clean Architecture and SOLID principles?"

---

## 🆕 Phase 3 Pattern Quick Reference

| Pattern                      | File Location                                                   | Lines   | Use When                         |
| ---------------------------- | --------------------------------------------------------------- | ------- | -------------------------------- |
| **Defense-in-Depth Filter**  | `{service}/infrastructure/security/PolicyValidationFilter.java` | ~160    | All services (secondary check)   |
| **Reactive Audit Publisher** | `api-gateway/audit/ReactivePolicyAuditPublisher.java`           | ~90     | Reactive contexts (Gateway)      |
| **Optional Dependency**      | `shared-infrastructure/policy/engine/PolicyEngine.java`         | Pattern | Component used in mixed contexts |
| **Registry Lookup**          | `PolicyEngine.checkRoleDefaultAccess()`                         | Pattern | Need database-driven config      |
| **Context Builder**          | `PolicyValidationFilter.buildPolicyContext()`                   | Helper  | Converting SecurityContext       |
| **Factory Method**           | `PolicyAuditEvent.fromAudit()`                                  | Static  | Creating Kafka events            |

**📖 Complete examples:** See Pattern 1-8 sections above

---

**Document Owner:** Tech Lead  
**Reviewers:** All Developers  
**Status:** ✅ Active & Enforced  
**Last Updated:** 2025-10-10 14:45 UTC+1  
**Version:** 3.0 (Phase 3 Implementation Patterns Added)

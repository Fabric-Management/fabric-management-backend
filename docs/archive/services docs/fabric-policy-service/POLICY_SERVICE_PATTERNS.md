# 🎨 FABRIC POLICY SERVICE - DESIGN PATTERNS

**Version:** 1.0  
**Status:** 🧩 Design Phase  
**Scope:** Design patterns and implementation strategies for Policy Service  
**Last Updated:** 2025-01-27

---

## 🎯 PURPOSE

This document defines the **design patterns** and **implementation strategies** for the Fabric Policy Service. It complements the Protocol document by providing concrete patterns for developers to follow.

### Key Patterns Covered:

- **Policy Evaluation Pattern** - How to evaluate authorization requests
- **Subscription Management Pattern** - Service access based on subscriptions
- **Department Access Pattern** - Granular permissions within organizations
- **Cross-service Coordination Pattern** - Policy consistency across microservices
- **Cache-aside Pattern** - High-performance policy caching
- **Event-driven Policy Updates** - Real-time policy synchronization

---

## 🏗️ CORE DESIGN PATTERNS

### 1. Policy Evaluation Pattern

**Intent:** Centralize authorization logic with consistent evaluation flow

**Structure:**

```java
@Component
public class PolicyEvaluationEngine {

    private final List<PolicyEvaluator> evaluators;
    private final PolicyCache policyCache;
    private final AuditService auditService;

    public PolicyDecision evaluate(PolicyContext context) {
        // 1. Cache check
        PolicyDecision cached = policyCache.get(context.getCacheKey());
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        // 2. Sequential evaluation
        for (PolicyEvaluator evaluator : evaluators) {
            PolicyDecision decision = evaluator.evaluate(context);
            if (decision.isDenied()) {
                return decision; // Fail-fast
            }
        }

        // 3. Cache and audit
        PolicyDecision finalDecision = PolicyDecision.allow("All checks passed");
        policyCache.put(context.getCacheKey(), finalDecision);
        auditService.logEvaluation(context, finalDecision);

        return finalDecision;
    }
}
```

**Policy Evaluator Chain:**

```java
// 1. Subscription Evaluator
@Component
public class SubscriptionEvaluator implements PolicyEvaluator {
    public PolicyDecision evaluate(PolicyContext context) {
        if (!hasRequiredSubscription(context)) {
            return PolicyDecision.deny("Insufficient subscription");
        }
        return PolicyDecision.allow("Subscription valid");
    }
}

// 2. Department Evaluator
@Component
public class DepartmentEvaluator implements PolicyEvaluator {
    public PolicyDecision evaluate(PolicyContext context) {
        if (!hasDepartmentAccess(context)) {
            return PolicyDecision.deny("Department access denied");
        }
        return PolicyDecision.allow("Department access granted");
    }
}

// 3. Individual Permission Evaluator
@Component
public class IndividualPermissionEvaluator implements PolicyEvaluator {
    public PolicyDecision evaluate(PolicyContext context) {
        if (hasIndividualPermission(context)) {
            return PolicyDecision.allow("Individual permission granted");
        }
        return PolicyDecision.allow("No individual permission required");
    }
}
```

### 2. Subscription Management Pattern

**Intent:** Manage service access based on subscription tiers

**Structure:**

```java
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PolicyCache policyCache;

    public SubscriptionTier getUserSubscription(UUID userId, UUID tenantId) {
        String cacheKey = "subscription:" + userId + ":" + tenantId;

        return policyCache.get(cacheKey, () ->
            subscriptionRepository.findByUserIdAndTenantId(userId, tenantId)
                .map(Subscription::getTier)
                .orElse(SubscriptionTier.FREE)
        );
    }

    public boolean hasServiceAccess(UUID userId, UUID tenantId, String serviceName) {
        SubscriptionTier tier = getUserSubscription(userId, tenantId);
        return tier.getAccessibleServices().contains(serviceName);
    }

    public List<String> getAccessibleEndpoints(UUID userId, UUID tenantId, String serviceName) {
        SubscriptionTier tier = getUserSubscription(userId, tenantId);
        return tier.getServiceEndpoints(serviceName);
    }
}
```

**Subscription Tier Definition:**

```java
public enum SubscriptionTier {
    FREE(Set.of("user-service", "company-service")),
    BASIC(Set.of("user-service", "company-service", "fabric-fiber-service")),
    PREMIUM(Set.of("user-service", "company-service", "fabric-fiber-service", "fabric-yarn-service")),
    ENTERPRISE(Set.of()); // All services

    private final Set<String> accessibleServices;

    public Set<String> getAccessibleServices() {
        return accessibleServices;
    }

    public List<String> getServiceEndpoints(String serviceName) {
        return switch (this) {
            case FREE -> List.of("read");
            case BASIC -> List.of("read", "create");
            case PREMIUM -> List.of("read", "create", "update");
            case ENTERPRISE -> List.of("read", "create", "update", "delete", "admin");
        };
    }
}
```

### 3. Department Access Pattern

**Intent:** Provide granular permissions based on organizational departments

**Structure:**

```java
@Service
public class DepartmentAccessService {

    private final UserDepartmentRepository userDepartmentRepository;
    private final DepartmentPermissionRepository permissionRepository;

    public boolean hasDepartmentAccess(UUID userId, String department, String operation) {
        // 1. Check user's department
        UserDepartment userDept = userDepartmentRepository.findByUserId(userId);
        if (!userDept.getDepartment().equals(department)) {
            return false;
        }

        // 2. Check department permissions
        DepartmentPermission permission = permissionRepository
            .findByDepartmentAndOperation(department, operation);

        return permission != null && permission.isActive();
    }

    public boolean hasCrossDepartmentAccess(UUID userId, String targetDepartment, String operation) {
        // Check if user has special cross-department permissions
        return permissionRepository
            .findByUserIdAndDepartmentAndOperation(userId, targetDepartment, operation)
            .isPresent();
    }
}
```

**Department Permission Matrix:**

```java
@Entity
public class DepartmentPermission {
    @Id
    private UUID id;

    private String department;
    private String operation;
    private String resourceType;
    private boolean active;

    // Examples:
    // PLANNING + READ + CUSTOMER_FINANCIAL_DATA
    // WAREHOUSE + WRITE + INVENTORY_DATA
    // SALES + READ + CUSTOMER_CONTACT_DATA
}
```

### 4. Individual Permission Pattern

**Intent:** Grant specific permissions to individual users beyond their role/department

**Structure:**

```java
@Service
public class IndividualPermissionService {

    private final IndividualPermissionRepository permissionRepository;
    private final AuditService auditService;

    @Transactional
    public IndividualPermission grantPermission(
            UUID userId,
            String endpoint,
            String reason,
            UUID grantedBy,
            LocalDateTime expiresAt) {

        IndividualPermission permission = IndividualPermission.builder()
            .userId(userId)
            .endpoint(endpoint)
            .reason(reason)
            .grantedBy(grantedBy)
            .grantedAt(LocalDateTime.now())
            .expiresAt(expiresAt)
            .active(true)
            .build();

        IndividualPermission saved = permissionRepository.save(permission);

        // Audit the grant
        auditService.logPermissionGrant(saved);

        // Invalidate user cache
        policyCache.evictUser(userId.toString());

        return saved;
    }

    public boolean hasIndividualPermission(UUID userId, String endpoint) {
        return permissionRepository
            .findByUserIdAndEndpointAndActiveTrue(userId, endpoint)
            .stream()
            .anyMatch(permission ->
                permission.getExpiresAt() == null ||
                permission.getExpiresAt().isAfter(LocalDateTime.now())
            );
    }
}
```

**Individual Permission Entity:**

```java
@Entity
public class IndividualPermission {
    @Id
    private UUID id;

    private UUID userId;
    private String endpoint;
    private String reason;
    private UUID grantedBy;
    private LocalDateTime grantedAt;
    private LocalDateTime expiresAt;
    private boolean active;

    // Example usage:
    // userId: Ayşe (Planning Specialist)
    // endpoint: /api/v1/customers/{id}/financial
    // reason: "Planning optimization requirements"
    // expiresAt: 3 months from now
}
```

### 5. Cache-aside Pattern

**Intent:** High-performance policy caching with intelligent invalidation

**Structure:**

```java
@Service
public class PolicyCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PolicyRepository policyRepository;

    public PolicyDecision getPolicyDecision(String cacheKey) {
        // 1. Try cache first
        PolicyDecision cached = (PolicyDecision) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        // 2. Cache miss - evaluate and cache
        PolicyDecision decision = evaluatePolicy(cacheKey);
        redisTemplate.opsForValue().set(cacheKey, decision, Duration.ofMinutes(5));

        return decision;
    }

    public void invalidateUserCache(UUID userId) {
        // Remove all cache entries for user
        Set<String> keys = redisTemplate.keys("policy:*:" + userId + ":*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void invalidatePolicyCache(UUID policyId) {
        // Remove all cache entries for policy
        Set<String> keys = redisTemplate.keys("policy:" + policyId + ":*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
```

**Cache Key Strategy:**

```java
public class CacheKeyBuilder {

    public static String buildPolicyKey(UUID userId, UUID tenantId, String endpoint, String method) {
        return String.format("policy:%s:%s:%s:%s",
            userId, tenantId, endpoint, method);
    }

    public static String buildSubscriptionKey(UUID userId, UUID tenantId) {
        return String.format("subscription:%s:%s", userId, tenantId);
    }

    public static String buildDepartmentKey(UUID userId, String department) {
        return String.format("department:%s:%s", userId, department);
    }
}
```

### 6. Event-driven Policy Updates Pattern

**Intent:** Real-time policy synchronization across microservices

**Structure:**

```java
@Component
public class PolicyEventProcessor {

    private final PolicyCacheService cacheService;
    private final PolicyRepository policyRepository;

    @KafkaListener(topics = "policy-events")
    public void handlePolicyUpdate(PolicyUpdateEvent event) {
        switch (event.getEventType()) {
            case POLICY_UPDATED:
                handlePolicyUpdated(event);
                break;
            case POLICY_DELETED:
                handlePolicyDeleted(event);
                break;
            case SUBSCRIPTION_CHANGED:
                handleSubscriptionChanged(event);
                break;
            case PERMISSION_GRANTED:
                handlePermissionGranted(event);
                break;
        }
    }

    private void handlePolicyUpdated(PolicyUpdateEvent event) {
        // 1. Update policy in database
        Policy policy = policyRepository.findById(event.getPolicyId())
            .orElseThrow(() -> new PolicyNotFoundException(event.getPolicyId()));

        policy.updateFromEvent(event);
        policyRepository.save(policy);

        // 2. Invalidate related cache entries
        cacheService.invalidatePolicyCache(event.getPolicyId());

        // 3. Notify affected services
        notificationService.notifyPolicyUpdate(event);
    }

    private void handleSubscriptionChanged(PolicyUpdateEvent event) {
        // 1. Update user subscription
        subscriptionService.updateUserSubscription(
            event.getUserId(),
            event.getNewSubscription()
        );

        // 2. Invalidate user cache
        cacheService.invalidateUserCache(event.getUserId());

        // 3. Send notification to user
        notificationService.notifySubscriptionChange(event);
    }
}
```

**Policy Update Event:**

```java
public class PolicyUpdateEvent {
    private String eventType;
    private UUID eventId;
    private LocalDateTime occurredAt;
    private UUID tenantId;
    private UUID policyId;
    private UUID userId;
    private String oldSubscription;
    private String newSubscription;
    private Map<String, Object> changes;
    private String traceId;
}
```

---

## 🔄 INTEGRATION PATTERNS

### 1. Service Integration Pattern

**Intent:** How other services integrate with Policy Service

**Structure:**

```java
@Service
public class PolicyClientService {

    private final PolicyServiceClient policyServiceClient;
    private final CircuitBreaker circuitBreaker;

    public boolean isAuthorized(PolicyCheckRequest request) {
        return circuitBreaker.executeSupplier(() -> {
            PolicyCheckResponse response = policyServiceClient.checkPolicy(request);
            return response.isAllowed();
        });
    }

    @Retryable(value = {Exception.class}, maxAttempts = 3)
    public PolicyCheckResponse checkPolicyWithRetry(PolicyCheckRequest request) {
        return policyServiceClient.checkPolicy(request);
    }
}
```

**Feign Client:**

```java
@FeignClient(
    name = "policy-service",
    url = "${services.policy-service.url}",
    configuration = BaseFeignClientConfig.class
)
public interface PolicyServiceClient {

    @PostMapping("/api/v1/policy/check")
    PolicyCheckResponse checkPolicy(@RequestBody PolicyCheckRequest request);

    @GetMapping("/api/v1/policy/user/{userId}/permissions")
    List<Permission> getUserPermissions(@PathVariable UUID userId);
}
```

### 2. Gateway Integration Pattern

**Intent:** How API Gateway integrates with Policy Service

**Structure:**

```java
@Component
public class PolicyGatewayFilter implements GatewayFilter {

    private final PolicyClientService policyClientService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. Extract user context
        PolicyContext context = extractPolicyContext(exchange);

        // 2. Check policy
        return policyClientService.isAuthorizedAsync(context)
            .flatMap(authorized -> {
                if (authorized) {
                    return chain.filter(exchange);
                } else {
                    return unauthorizedResponse(exchange);
                }
            });
    }

    private PolicyContext extractPolicyContext(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        return PolicyContext.builder()
            .userId(extractUserId(request))
            .tenantId(extractTenantId(request))
            .endpoint(request.getPath().value())
            .method(request.getMethod().name())
            .ipAddress(extractIpAddress(request))
            .build();
    }
}
```

---

## 📊 MONITORING PATTERNS

### 1. Policy Evaluation Metrics Pattern

**Intent:** Comprehensive monitoring of policy evaluation performance

**Structure:**

```java
@Component
public class PolicyMetricsCollector {

    private final MeterRegistry meterRegistry;
    private final Timer.Sample evaluationTimer;

    public PolicyDecision evaluateWithMetrics(PolicyContext context) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            PolicyDecision decision = policyEngine.evaluate(context);

            // Record metrics
            recordEvaluationMetrics(context, decision, sample);

            return decision;
        } catch (Exception e) {
            recordErrorMetrics(context, e);
            throw e;
        }
    }

    private void recordEvaluationMetrics(PolicyContext context, PolicyDecision decision, Timer.Sample sample) {
        sample.stop(Timer.builder("policy.evaluation.duration")
            .tag("service", context.getServiceName())
            .tag("endpoint", context.getEndpoint())
            .tag("decision", decision.isAllowed() ? "allowed" : "denied")
            .register(meterRegistry));

        Counter.builder("policy.evaluation.count")
            .tag("service", context.getServiceName())
            .tag("decision", decision.isAllowed() ? "allowed" : "denied")
            .register(meterRegistry)
            .increment();
    }
}
```

### 2. Audit Trail Pattern

**Intent:** Comprehensive audit logging for compliance and security

**Structure:**

```java
@Service
public class PolicyAuditService {

    private final AuditRepository auditRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Async
    public void logPolicyEvaluation(PolicyContext context, PolicyDecision decision) {
        PolicyAuditLog auditLog = PolicyAuditLog.builder()
            .timestamp(LocalDateTime.now())
            .eventType("POLICY_EVALUATION")
            .userId(context.getUserId())
            .tenantId(context.getTenantId())
            .endpoint(context.getEndpoint())
            .method(context.getMethod())
            .decision(decision.isAllowed())
            .policyName(decision.getPolicyName())
            .evaluationTimeMs(decision.getEvaluationTimeMs())
            .traceId(context.getTraceId())
            .ipAddress(context.getIpAddress())
            .userAgent(context.getUserAgent())
            .build();

        // Save to database
        auditRepository.save(auditLog);

        // Send to Kafka for real-time processing
        kafkaTemplate.send("policy-audit-events", auditLog);
    }
}
```

---

## 🧪 TESTING PATTERNS

### 1. Policy Testing Pattern

**Intent:** Comprehensive testing of policy evaluation logic

**Structure:**

```java
@SpringBootTest
class PolicyEvaluationTest {

    @Autowired
    private PolicyEvaluationEngine policyEngine;

    @Test
    void shouldAllowFiberCreationForPremiumUser() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .endpoint("/api/v1/fiber/fibers")
            .method("POST")
            .subscription("FIBER_PREMIUM")
            .department("PLANNING")
            .role("SPECIALIST")
            .build();

        // When
        PolicyDecision decision = policyEngine.evaluate(context);

        // Then
        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getPolicyName()).isEqualTo("FIBER_CREATE_POLICY");
    }

    @Test
    void shouldDenyFiberCreationForBasicUser() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .endpoint("/api/v1/fiber/fibers")
            .method("POST")
            .subscription("FIBER_BASIC")
            .department("PLANNING")
            .role("SPECIALIST")
            .build();

        // When
        PolicyDecision decision = policyEngine.evaluate(context);

        // Then
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReason()).contains("Insufficient subscription");
    }
}
```

### 2. Integration Testing Pattern

**Intent:** End-to-end testing of policy service integration

**Structure:**

```java
@SpringBootTest
@Testcontainers
class PolicyServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("policy_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @Autowired
    private PolicyServiceClient policyServiceClient;

    @Test
    void shouldIntegrateWithOtherServices() {
        // Given
        PolicyCheckRequest request = PolicyCheckRequest.builder()
            .userId(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .endpoint("/api/v1/fiber/fibers")
            .method("POST")
            .build();

        // When
        PolicyCheckResponse response = policyServiceClient.checkPolicy(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
    }
}
```

---

## ✅ PATTERN COMPLIANCE CHECKLIST

### Core Patterns

- ✅ **Policy Evaluation Pattern** - Centralized authorization logic
- ✅ **Subscription Management Pattern** - Service access based on tiers
- ✅ **Department Access Pattern** - Granular organizational permissions
- ✅ **Individual Permission Pattern** - User-specific access grants
- ✅ **Cache-aside Pattern** - High-performance policy caching
- ✅ **Event-driven Updates** - Real-time policy synchronization

### Integration Patterns

- ✅ **Service Integration Pattern** - Clean service-to-service communication
- ✅ **Gateway Integration Pattern** - API Gateway policy enforcement
- ✅ **Monitoring Patterns** - Comprehensive metrics and audit
- ✅ **Testing Patterns** - Thorough test coverage

### Quality Standards

- ✅ **Performance** - Sub-50ms policy evaluation
- ✅ **Reliability** - Circuit breaker and retry patterns
- ✅ **Scalability** - Horizontal scaling support
- ✅ **Security** - Multi-layer security model
- ✅ **Observability** - Full monitoring and alerting

---

**Pattern Version:** 1.0  
**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

---

**Next Steps:**

1. Review and approve pattern designs
2. Create implementation checkpoints
3. Begin core pattern implementation
4. Integrate patterns with existing services

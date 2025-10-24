# 📋 SUBSCRIPTION MANAGEMENT

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/platform/company`  
**Reference:** [OS_SUBSCRIPTION_MODEL.md](../../../OS_SUBSCRIPTION_MODEL.md)

---

## 🎯 OVERVIEW

Subscription Management, tenant'ların **OS (Operating Subscription)** aboneliklerini yönetir.

### **Core Responsibilities**

- ✅ **Subscription CRUD** - Create, Read, Update, Delete
- ✅ **Subscription Lifecycle** - TRIAL → ACTIVE → EXPIRED
- ✅ **OS Access Control** - Which OS tenant has access to?
- ✅ **Feature Toggles** - OS-specific feature management
- ✅ **Trial Management** - Trial period tracking
- ✅ **Auto-Renewal** - Automatic subscription renewal

---

## 🧱 SUBSCRIPTION LIFECYCLE

### **States**

```
┌─────────────┐
│   TRIAL     │ Start trial (14-30 days)
└──────┬──────┘
       │
       ├─ Trial Expires → EXPIRED
       │
       └─ Payment → ACTIVE
                    │
                    ├─ Expiry Date → EXPIRED
                    │
                    ├─ Cancel → CANCELLED
                    │
                    └─ Payment Issue → SUSPENDED
                                       │
                                       └─ Payment Resolved → ACTIVE
```

| State         | Description                 | Access                  |
| ------------- | --------------------------- | ----------------------- |
| **TRIAL**     | Trial period active         | ✅ Full (until expiry)  |
| **ACTIVE**    | Paid subscription           | ✅ Full                 |
| **EXPIRED**   | Trial or subscription ended | ❌ Read-only or blocked |
| **CANCELLED** | Manually cancelled          | ❌ Blocked              |
| **SUSPENDED** | Payment issue               | ❌ Blocked              |

---

## 📋 DOMAIN MODELS

### **Subscription Entity**

```java
@Entity
@Table(name = "common_subscription", schema = "common_company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseEntity {

    // Identity (from BaseEntity)
    // - UUID id
    // - UUID tenantId
    // - String uid (e.g., "ACME-001-SUB-001")

    @Column(nullable = false)
    private String osCode; // YarnOS, LoomOS, PlanOS, etc.

    @Column(nullable = false)
    private String osName; // Display name

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status; // TRIAL, ACTIVE, EXPIRED, CANCELLED, SUSPENDED

    @Column(nullable = false)
    private Instant startDate;

    @Column
    private Instant expiryDate;

    @Column
    private Instant trialEndsAt;

    @Column(columnDefinition = "JSONB")
    private Map<String, Boolean> features; // OS-specific feature toggles

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PricingTier pricingTier; // FREE, BASIC, PROFESSIONAL, ENTERPRISE

    // Business methods
    public boolean isActive() {
        if (this.status == SubscriptionStatus.ACTIVE) {
            if (this.expiryDate == null) {
                return true; // No expiry
            }
            return this.expiryDate.isAfter(Instant.now());
        }

        if (this.status == SubscriptionStatus.TRIAL) {
            return this.trialEndsAt.isAfter(Instant.now());
        }

        return false;
    }

    public void activate() {
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
    }

    public void suspend() {
        this.status = SubscriptionStatus.SUSPENDED;
    }
}
```

### **OSDefinition Entity**

```java
@Entity
@Table(name = "common_os_definition", schema = "common_company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OSDefinition extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String osCode; // YarnOS, LoomOS, etc.

    @Column(nullable = false)
    private String osName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OSType osType; // BASE, LITE, FULL, PREMIUM

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "JSONB", nullable = false)
    private List<String> includedModules; // ["production.fiber", "production.yarn"]

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PricingTier pricingTier; // FREE, BASIC, PROFESSIONAL, ENTERPRISE

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
```

---

## 🔗 ENDPOINTS

| Endpoint                                                     | Method | Purpose                    | Auth Required  |
| ------------------------------------------------------------ | ------ | -------------------------- | -------------- |
| `/api/common/companies/{id}/subscriptions`                   | GET    | List company subscriptions | ✅ Yes         |
| `/api/common/companies/{id}/subscriptions/{osCode}`          | GET    | Get specific subscription  | ✅ Yes         |
| `/api/common/companies/{id}/subscriptions`                   | POST   | Add subscription           | ✅ Yes (ADMIN) |
| `/api/common/companies/{id}/subscriptions/{osCode}`          | PUT    | Update subscription        | ✅ Yes (ADMIN) |
| `/api/common/companies/{id}/subscriptions/{osCode}/activate` | POST   | Activate subscription      | ✅ Yes (ADMIN) |
| `/api/common/companies/{id}/subscriptions/{osCode}/cancel`   | POST   | Cancel subscription        | ✅ Yes (ADMIN) |
| `/api/common/subscriptions/os-definitions`                   | GET    | List available OS          | ✅ Yes         |

---

## 🔄 EVENTS

| Event                           | Trigger           | Listeners                               |
| ------------------------------- | ----------------- | --------------------------------------- |
| `SubscriptionCreatedEvent`      | New subscription  | Analytics, Billing, Notification        |
| `SubscriptionActivatedEvent`    | Trial → Active    | Analytics, Notification                 |
| `SubscriptionTrialStartedEvent` | Trial begins      | Notification, Monitoring                |
| `SubscriptionTrialEndingEvent`  | 7 days before end | Notification, Sales                     |
| `SubscriptionExpiredEvent`      | Expiry reached    | Analytics, Notification, Access Control |
| `SubscriptionRenewedEvent`      | Auto-renewal      | Billing, Analytics                      |
| `SubscriptionCancelledEvent`    | User cancels      | Analytics, Billing, Notification        |
| `SubscriptionSuspendedEvent`    | Payment issue     | Access Control, Notification            |

---

## 🔐 SUBSCRIPTION CHECK

### **SubscriptionChecker Service**

```java
@Component
@RequiredArgsConstructor
public class SubscriptionChecker {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCache subscriptionCache;

    public boolean hasActiveSubscription(UUID tenantId, String osCode) {
        // 1. Check cache
        Optional<Subscription> cached = subscriptionCache.get(tenantId, osCode);
        if (cached.isPresent()) {
            return cached.get().isActive();
        }

        // 2. Check database
        Optional<Subscription> subscription = subscriptionRepository
            .findByTenantIdAndOsCode(tenantId, osCode);

        if (subscription.isEmpty()) {
            return false;
        }

        // 3. Check if active
        boolean isActive = subscription.get().isActive();

        // 4. Cache result
        subscriptionCache.put(tenantId, osCode, subscription.get());

        return isActive;
    }

    public boolean hasFeature(UUID tenantId, String osCode, String featureCode) {
        Optional<Subscription> subscription = subscriptionRepository
            .findByTenantIdAndOsCode(tenantId, osCode);

        if (subscription.isEmpty() || !subscription.get().isActive()) {
            return false;
        }

        Map<String, Boolean> features = subscription.get().getFeatures();
        return features != null && features.getOrDefault(featureCode, false);
    }
}
```

---

## 📊 DATABASE SCHEMA

### **Subscription Table**

```sql
CREATE SCHEMA IF NOT EXISTS common_company;

CREATE TABLE common_company.common_subscription (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(50) NOT NULL UNIQUE,
    os_code VARCHAR(50) NOT NULL,
    os_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    trial_ends_at TIMESTAMP,
    features JSONB,
    pricing_tier VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, os_code)
);

CREATE INDEX idx_subscription_tenant_os ON common_company.common_subscription(tenant_id, os_code);
CREATE INDEX idx_subscription_status ON common_company.common_subscription(status);
CREATE INDEX idx_subscription_expiry ON common_company.common_subscription(expiry_date) WHERE expiry_date IS NOT NULL;
```

### **OS Definition Table**

```sql
CREATE TABLE common_company.common_os_definition (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(50) NOT NULL UNIQUE,
    os_code VARCHAR(50) NOT NULL UNIQUE,
    os_name VARCHAR(100) NOT NULL,
    os_type VARCHAR(20) NOT NULL,
    description TEXT,
    included_modules JSONB NOT NULL,
    pricing_tier VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 🎯 SUBSCRIPTION EXAMPLES

### **Example 1: FabricOS (Base)**

```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "tenantId": "789e4567-e89b-12d3-a456-426614174000",
  "uid": "ACME-001-SUB-001",
  "osCode": "FabricOS",
  "osName": "Fabric Management Base Platform",
  "status": "ACTIVE",
  "startDate": "2025-01-01T00:00:00Z",
  "expiryDate": null,
  "trialEndsAt": null,
  "features": {
    "core": true,
    "inventory": true,
    "shipment": true,
    "finance_basic": true,
    "yarn_lite": false
  },
  "pricingTier": "FREE"
}
```

### **Example 2: YarnOS (Professional)**

```json
{
  "id": "456e7890-e89b-12d3-a456-426614174000",
  "tenantId": "789e4567-e89b-12d3-a456-426614174000",
  "uid": "ACME-001-SUB-002",
  "osCode": "YarnOS",
  "osName": "Yarn Production OS",
  "status": "ACTIVE",
  "startDate": "2025-01-01T00:00:00Z",
  "expiryDate": "2025-12-31T23:59:59Z",
  "trialEndsAt": null,
  "features": {
    "fiber_production": true,
    "yarn_production": true,
    "quality_control": true,
    "inventory_advanced": true,
    "planning_advanced": true
  },
  "pricingTier": "PROFESSIONAL"
}
```

---

## ⏱️ AUTO-RENEWAL SCHEDULER

### **Expiry Check Scheduler**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;
    private final DomainEventPublisher eventPublisher;

    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2 AM
    @Transactional
    public void checkSubscriptionExpiry() {
        log.info("Checking subscription expiry...");

        // 1. Check expiring soon (7 days)
        List<Subscription> expiringSubscriptions = subscriptionRepository
            .findByExpiryDateBetween(
                Instant.now(),
                Instant.now().plus(7, ChronoUnit.DAYS)
            );

        for (Subscription subscription : expiringSubscriptions) {
            // Notify tenant
            notificationService.sendSubscriptionExpiryWarning(
                subscription.getTenantId(),
                subscription.getOsCode(),
                subscription.getExpiryDate()
            );

            // Publish event
            eventPublisher.publish(new SubscriptionTrialEndingEvent(
                subscription.getTenantId(),
                subscription.getId(),
                subscription.getOsCode(),
                subscription.getExpiryDate()
            ));
        }

        // 2. Mark expired subscriptions
        List<Subscription> expired = subscriptionRepository
            .findByExpiryDateBeforeAndStatus(
                Instant.now(),
                SubscriptionStatus.ACTIVE
            );

        for (Subscription subscription : expired) {
            log.warn("Subscription expired: tenantId={}, osCode={}",
                subscription.getTenantId(), subscription.getOsCode());

            subscription.expire();
            subscriptionRepository.save(subscription);

            // Publish event
            eventPublisher.publish(new SubscriptionExpiredEvent(
                subscription.getTenantId(),
                subscription.getId(),
                subscription.getOsCode(),
                SubscriptionStatus.ACTIVE
            ));
        }

        log.info("Subscription expiry check completed: {} expiring, {} expired",
            expiringSubscriptions.size(), expired.size());
    }
}
```

---

## 🔐 OS DEPENDENCY VALIDATION

### **Dependency Rules**

Bazı OS'lar diğer OS'lara bağımlıdır:

| OS            | Required OS         | Optional OS         | Reason                          |
| ------------- | ------------------- | ------------------- | ------------------------------- |
| **YarnOS**    | FabricOS            | PlanOS, AnalyticsOS | Base platform required          |
| **LoomOS**    | FabricOS            | YarnOS, PlanOS      | Weaving needs yarn integration  |
| **AccountOS** | FabricOS, FinanceOS | none                | Accounting needs finance module |

### **Dependency Validator**

```java
@Service
@RequiredArgsConstructor
public class SubscriptionValidator {

    private final OSDependencyRepository dependencyRepository;
    private final SubscriptionRepository subscriptionRepository;

    public ValidationResult validateSubscription(UUID tenantId, String osCode) {
        // Get required dependencies
        List<OSDependency> dependencies = dependencyRepository
            .findByOsCodeAndIsOptionalFalse(osCode);

        List<String> missingDependencies = new ArrayList<>();

        for (OSDependency dependency : dependencies) {
            boolean hasRequiredOS = subscriptionRepository
                .existsByTenantIdAndOsCodeAndStatusIn(
                    tenantId,
                    dependency.getRequiredOsCode(),
                    List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL)
                );

            if (!hasRequiredOS) {
                missingDependencies.add(dependency.getRequiredOsCode());
            }
        }

        if (!missingDependencies.isEmpty()) {
            return ValidationResult.invalid(
                "Missing required OS: " + String.join(", ", missingDependencies)
            );
        }

        return ValidationResult.valid();
    }
}
```

---

## 📊 USAGE IN POLICY ENGINE

### **Integration with @PolicyCheck**

```java
@RestController
@RequestMapping("/api/production/yarn")
public class YarnController {

    @PolicyCheck(
        os = "YarnOS",                      // Required OS
        resource = "fabric.yarn.create",    // Resource
        action = "POST",                    // Action
        fallbackOs = "FabricOS.yarn_lite"   // Fallback to lite version
    )
    @PostMapping
    public ResponseEntity<?> createYarn(@RequestBody CreateYarnRequest request) {
        // Implementation
    }
}
```

### **Policy Evaluation with OS**

```java
@Aspect
@Component
@RequiredArgsConstructor
public class PolicyCheckAspect {

    private final SubscriptionChecker subscriptionChecker;
    private final PolicyEvaluationEngine policyEngine;

    @Around("@annotation(policyCheck)")
    public Object checkPolicy(ProceedingJoinPoint joinPoint, PolicyCheck policyCheck) throws Throwable {
        UUID tenantId = TenantContext.getCurrentTenantId();

        // 1. Check OS subscription
        if (!subscriptionChecker.hasActiveSubscription(tenantId, policyCheck.os())) {
            // Try fallback OS
            if (policyCheck.fallbackOs() != null) {
                String[] parts = policyCheck.fallbackOs().split("\\.");
                String fallbackOs = parts[0];
                String fallbackFeature = parts.length > 1 ? parts[1] : null;

                if (subscriptionChecker.hasActiveSubscription(tenantId, fallbackOs)) {
                    if (fallbackFeature == null ||
                        subscriptionChecker.hasFeature(tenantId, fallbackOs, fallbackFeature)) {
                        // Fallback allowed
                        return joinPoint.proceed();
                    }
                }
            }

            throw new SubscriptionRequiredException(policyCheck.os());
        }

        // 2. Continue with full policy evaluation
        PolicyDecision decision = policyEngine.evaluate(buildPolicyRequest(policyCheck));

        if (!decision.isAllowed()) {
            throw new AccessDeniedException(decision.getReason());
        }

        return joinPoint.proceed();
    }
}
```

---

## 📈 STATISTICS & ANALYTICS

### **Subscription Metrics**

```java
@Service
@RequiredArgsConstructor
public class SubscriptionAnalyticsService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionMetrics getMetrics() {
        long totalSubscriptions = subscriptionRepository.count();
        long activeSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);
        long trialSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.TRIAL);
        long expiredSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.EXPIRED);

        Map<String, Long> subscriptionsByOS = subscriptionRepository
            .findAll()
            .stream()
            .collect(Collectors.groupingBy(Subscription::getOsCode, Collectors.counting()));

        return SubscriptionMetrics.builder()
            .totalSubscriptions(totalSubscriptions)
            .activeSubscriptions(activeSubscriptions)
            .trialSubscriptions(trialSubscriptions)
            .expiredSubscriptions(expiredSubscriptions)
            .subscriptionsByOS(subscriptionsByOS)
            .build();
    }
}
```

---

## ✅ BEST PRACTICES

### **1. Always Validate Dependencies**

```java
// ✅ Good: Validate before creating
ValidationResult validation = subscriptionValidator.validateSubscription(tenantId, "YarnOS");
if (!validation.isValid()) {
    throw new SubscriptionValidationException(validation.getErrors());
}

// ❌ Bad: No validation
subscriptionRepository.save(subscription); // May break dependencies
```

### **2. Use SubscriptionChecker**

```java
// ✅ Good: Use checker (with cache)
if (subscriptionChecker.hasActiveSubscription(tenantId, "YarnOS")) {
    // Allow access
}

// ❌ Bad: Direct query (no cache)
Optional<Subscription> sub = subscriptionRepository.findByTenantIdAndOsCode(tenantId, "YarnOS");
```

### **3. Publish Events**

```java
// ✅ Good: Publish events
subscription.activate();
subscriptionRepository.save(subscription);
eventPublisher.publish(new SubscriptionActivatedEvent(...));

// ❌ Bad: No events
subscription.setStatus(SubscriptionStatus.ACTIVE);
subscriptionRepository.save(subscription);
```

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

# 💳 SUBSCRIPTION MANAGEMENT - IMPLEMENTATION GUIDE

**Module:** common/platform/company  
**Version:** 1.0  
**Last Updated:** 2025-10-25  
**Status:** ✅ Implementation Complete

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Domain Model](#domain-model)
3. [Repositories](#repositories)
4. [Services](#services)
5. [Exceptions](#exceptions)
6. [API Endpoints](#api-endpoints) (Pending)
7. [Database Schema](#database-schema)
8. [Usage Examples](#usage-examples)

---

## 🎯 OVERVIEW

Bu doküman, **Composable Feature-Based Subscription** modelinin teknik implementasyon detaylarını içerir.

### **Key Concepts**

- **String-Based Tiers** - Her OS'un kendi tier isimleri (ENUM yok!)
- **JSONB Feature Storage** - Esnek feature entitlement
- **Usage Quotas** - API, storage, entity limitleri
- **4-Layer Validation** - OS → Feature → Quota → Policy

---

## 🏗️ DOMAIN MODEL

### **Core Entities**

```
src/main/java/com/fabricmanagement/common/platform/company/domain/
├── Subscription.java               # OS subscription entity
├── SubscriptionStatus.java         # TRIAL, ACTIVE, EXPIRED, etc.
├── SubscriptionQuota.java          # Usage quota tracking
├── FeatureCatalog.java             # Feature entitlement rules
├── OSDefinition.java               # OS definitions
├── PricingTierValidator.java       # Tier validation utility
└── exception/
    ├── SubscriptionRequiredException.java
    ├── FeatureNotAvailableException.java
    └── QuotaExceededException.java
```

### **Subscription Entity**

**File:** `Subscription.java`

**Fields:**

- `osCode` (String) - "YarnOS", "LoomOS", etc.
- `osName` (String) - Human-readable name
- `status` (SubscriptionStatus) - TRIAL, ACTIVE, EXPIRED, CANCELLED, SUSPENDED
- `pricingTier` (String) - "Starter", "Professional", "Enterprise", "Standard", "Advanced"
- `features` (Map<String, Boolean>) - JSONB feature override
- `startDate` (Instant)
- `expiryDate` (Instant)
- `trialEndsAt` (Instant)

**Key Methods:**

```java
boolean isActive()
boolean isExpired()
boolean hasFeature(String featureName)
void activate()
void expire()
void cancel()
void suspend()
void resume()
void enableFeature(String featureName)
void disableFeature(String featureName)
```

**Validation:**

- `@PrePersist/@PreUpdate` - Validates pricing tier for OS using `PricingTierValidator`
- Auto-sets default tier if not specified

### **SubscriptionQuota Entity**

**File:** `SubscriptionQuota.java`

**Fields:**

- `subscriptionId` (UUID)
- `quotaType` (String) - "users", "api_calls", "fiber_entities", etc.
- `quotaLimit` (Long)
- `quotaUsed` (Long)
- `resetPeriod` (String) - "MONTHLY", "DAILY", "NONE"
- `lastResetAt` (Instant)

**Key Methods:**

```java
boolean isExceeded()
boolean isUnlimited()
long remaining()
double usagePercentage()
void increment(long amount)
void decrement(long amount)
void reset()
boolean needsReset()
```

### **FeatureCatalog Entity**

**File:** `FeatureCatalog.java`

**Fields:**

- `featureId` (String) - "yarn.blend.management"
- `osCode` (String) - "YarnOS"
- `featureName` (String)
- `description` (String)
- `availableInTiers` (List<String>) - ["Professional", "Enterprise"]
- `category` (String)
- `isActive` (Boolean)
- `requiresOs` (String) - Dependency on another OS

**Key Methods:**

```java
boolean isAvailableInTier(String tierName)
boolean hasOsDependency()
String getMinimumTier()
String getOsPrefix()
String getModuleName()
```

### **PricingTierValidator Utility**

**File:** `PricingTierValidator.java`

**Static Methods:**

```java
boolean isValidTier(String osCode, String tierName)
Set<String> getValidTiers(String osCode)
String getDefaultTier(String osCode)
int getTierLevel(String tierName)
boolean meetsMinimumTier(String currentTier, String minimumTier)
String getTierDisplayName(String tierName)
```

**OS-Tier Mapping:**

```java
"YarnOS" → ["Starter", "Professional", "Enterprise"]
"AnalyticsOS" → ["Standard", "Advanced", "Enterprise"]
"IntelligenceOS" → ["Professional", "Enterprise"]
"FabricOS" → ["Base"]
```

---

## 📦 REPOSITORIES

### **SubscriptionRepository**

**File:** `SubscriptionRepository.java` (already exists)

**Methods:**

```java
Optional<Subscription> findByTenantIdAndId(UUID tenantId, UUID id)
List<Subscription> findByTenantId(UUID tenantId)
Optional<Subscription> findActiveSubscriptionByOsCode(UUID tenantId, String osCode, Instant now)
List<Subscription> findActiveSubscriptions(UUID tenantId, Instant now)
```

### **SubscriptionQuotaRepository**

**File:** `SubscriptionQuotaRepository.java`

**Methods:**

```java
Optional<SubscriptionQuota> findByTenantIdAndQuotaType(UUID tenantId, String quotaType)
List<SubscriptionQuota> findByTenantId(UUID tenantId)
List<SubscriptionQuota> findBySubscriptionId(UUID subscriptionId)
List<SubscriptionQuota> findByResetPeriod(String resetPeriod)
List<SubscriptionQuota> findExceededQuotas(UUID tenantId)
boolean isQuotaExceeded(UUID tenantId, String quotaType)
Optional<Long> getRemainingQuota(UUID tenantId, String quotaType)
```

### **FeatureCatalogRepository**

**File:** `FeatureCatalogRepository.java`

**Methods:**

```java
Optional<FeatureCatalog> findByFeatureId(String featureId)
List<FeatureCatalog> findByOsCode(String osCode)
List<FeatureCatalog> findByCategory(String category)
List<FeatureCatalog> findByIsActiveTrue()
List<FeatureCatalog> findByTier(String tier)
List<FeatureCatalog> findByOsCodeAndTier(String osCode, String tier)
List<FeatureCatalog> findByRequiresOs(String requiresOs)
```

---

## 🛠️ SERVICES

### **SubscriptionService** (Existing - Enhanced)

**File:** `SubscriptionService.java`

**Methods:**

```java
boolean hasActiveSubscription(UUID tenantId, String osCode)
List<SubscriptionDto> getCompanySubscriptions(UUID companyId)
List<SubscriptionDto> getActiveSubscriptions()
SubscriptionDto activateSubscription(UUID subscriptionId)
void expireSubscription(UUID subscriptionId)
void processExpiringSubscriptions()  // Scheduled job
```

### **EnhancedSubscriptionService** (To Be Created)

**Responsibilities:**

- Feature entitlement checks
- Usage quota validation
- Combined OS + Feature + Quota enforcement

**Methods:**

```java
boolean hasFeature(UUID tenantId, String featureId)
boolean hasQuota(UUID tenantId, String quotaType)
void enforceEntitlement(UUID tenantId, String featureId, String quotaType)
```

**Implementation:**

```java
@Service
@RequiredArgsConstructor
public class EnhancedSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final FeatureCatalogRepository featureCatalogRepository;
    private final SubscriptionQuotaRepository quotaRepository;

    public void enforceEntitlement(UUID tenantId, String featureId, String quotaType) {
        // Layer 1: OS Subscription Check
        String osCode = extractOsCode(featureId);
        if (!hasActiveSubscription(tenantId, osCode)) {
            throw new SubscriptionRequiredException(osCode + " subscription required");
        }

        // Layer 2: Feature Entitlement Check
        if (!hasFeature(tenantId, featureId)) {
            throw new FeatureNotAvailableException(
                "Feature '" + featureId + "' not available in your plan"
            );
        }

        // Layer 3: Usage Quota Check
        if (quotaType != null && !hasQuota(tenantId, quotaType)) {
            throw new QuotaExceededException("Quota exceeded for " + quotaType);
        }
    }
}
```

### **QuotaService** (To Be Created)

**Responsibilities:**

- Quota enforcement
- Quota increment/decrement
- Monthly/daily quota resets

**Methods:**

```java
void enforceQuota(UUID tenantId, String quotaType)
void incrementQuota(UUID tenantId, String quotaType, long increment)
void decrementQuota(UUID tenantId, String quotaType, long decrement)
void resetMonthlyQuotas()  // Scheduled job
void resetDailyQuotas()    // Scheduled job
```

---

## ❌ EXCEPTIONS

### **SubscriptionRequiredException**

**HTTP Status:** 402 Payment Required (or 403 Forbidden)

**Fields:**

- `requiredOs` (String)

**Usage:**

```java
throw new SubscriptionRequiredException("YarnOS subscription required", "YarnOS");
```

### **FeatureNotAvailableException**

**HTTP Status:** 402 Payment Required

**Fields:**

- `featureId` (String)
- `minimumTier` (String)

**Usage:**

```java
throw new FeatureNotAvailableException(
    "Blend management requires Professional tier",
    "yarn.blend.management",
    "Professional"
);
```

### **QuotaExceededException**

**HTTP Status:** 429 Too Many Requests

**Fields:**

- `quotaType` (String)
- `limit` (Long)
- `used` (Long)

**Usage:**

```java
throw new QuotaExceededException(
    "API call limit exceeded",
    "api_calls",
    100_000L,
    100_000L
);
```

---

## 📡 API ENDPOINTS (Pending Implementation)

### **Get Active Subscriptions**

```http
GET /api/v1/subscriptions/active

Response:
[
  {
    "id": "uuid",
    "osCode": "YarnOS",
    "pricingTier": "Professional",
    "status": "ACTIVE",
    "features": {
      "yarn.fiber.create": true,
      "yarn.blend.management": true
    }
  }
]
```

### **Get Usage Quotas**

```http
GET /api/v1/subscriptions/quotas

Response:
[
  {
    "quotaType": "users",
    "limit": 20,
    "used": 15,
    "remaining": 5
  },
  {
    "quotaType": "api_calls",
    "limit": 100000,
    "used": 45230,
    "remaining": 54770,
    "resetPeriod": "MONTHLY"
  }
]
```

---

## 🗄️ DATABASE SCHEMA

### **Tables Created**

```sql
-- Subscription table
common_company.common_subscription
  - pricing_tier: VARCHAR(50)  -- ⭐ Changed from ENUM to String
  - features: JSONB

-- Quota table (NEW)
common_company.common_subscription_quota
  - quota_type, quota_limit, quota_used
  - reset_period, last_reset_at

-- Feature catalog (NEW)
common_company.common_feature_catalog
  - feature_id, os_code
  - available_in_tiers: JSONB

-- OS definition (UPDATED)
common_company.common_os_definition
  - available_tiers: JSONB  -- ⭐ NEW
  - default_tier: VARCHAR(50)  -- ⭐ NEW
```

### **Indexes**

```sql
-- Subscription
idx_subscription_tenant_os (tenant_id, os_code)
idx_subscription_status (status)

-- Quota
idx_quota_tenant_type (tenant_id, quota_type)
idx_quota_subscription (subscription_id)
uk_tenant_subscription_quota (tenant_id, subscription_id, quota_type)

-- Feature Catalog
idx_feature_os_code (os_code)
idx_feature_id (feature_id) UNIQUE
```

---

## 💡 USAGE EXAMPLES

### **Example 1: Create Subscription**

```java
Subscription yarnOS = Subscription.builder()
    .osCode("YarnOS")
    .osName("Yarn Production OS")
    .status(SubscriptionStatus.ACTIVE)
    .pricingTier("Professional")  // String!
    .startDate(Instant.now())
    .expiryDate(Instant.now().plus(365, ChronoUnit.DAYS))
    .features(Map.of(
        "yarn.fiber.create", true,
        "yarn.blend.management", true
    ))
    .build();

subscriptionRepository.save(yarnOS);
```

### **Example 2: Feature Gating in Controller**

```java
@RestController
@RequestMapping("/api/v1/yarn/fibers")
public class FiberController {

    @Autowired
    private EnhancedSubscriptionService subscriptionService;

    @PostMapping("/{fiberId}/blend")
    public ResponseEntity<FiberBlendDto> createBlend(
        @PathVariable UUID fiberId,
        @RequestBody CreateBlendRequest request
    ) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        // Feature gating
        subscriptionService.enforceEntitlement(
            tenantId,
            "yarn.blend.management",
            null
        );

        FiberBlendDto blend = fiberService.createBlend(fiberId, request);
        return ResponseEntity.ok(blend);
    }
}
```

### **Example 3: Quota Enforcement**

```java
@PostMapping
public ResponseEntity<FiberDto> createFiber(@RequestBody CreateFiberRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // Check entitlement + quota
    subscriptionService.enforceEntitlement(
        tenantId,
        "yarn.fiber.create",
        "fiber_entities"  // Check quota
    );

    FiberDto fiber = fiberService.createFiber(request);

    // Increment quota
    quotaService.incrementQuota(tenantId, "fiber_entities", 1);

    return ResponseEntity.ok(fiber);
}
```

---

## 📚 RELATED DOCUMENTATION

- [../../SUBSCRIPTION_MODEL.md](../../SUBSCRIPTION_MODEL.md) - Kapsamlı subscription model dokümantasyonu
- [../../SUBSCRIPTION_QUICK_START.md](../../SUBSCRIPTION_QUICK_START.md) - Hızlı başlangıç kılavuzu
- [../../ARCHITECTURE.md](../../ARCHITECTURE.md) - Subscription model architecture

---

## ✅ IMPLEMENTATION STATUS

| Component                | Status       | File                             |
| ------------------------ | ------------ | -------------------------------- |
| **Subscription Entity**  | ✅ Completed | Subscription.java                |
| **SubscriptionQuota**    | ✅ Completed | SubscriptionQuota.java           |
| **FeatureCatalog**       | ✅ Completed | FeatureCatalog.java              |
| **OSDefinition**         | ✅ Completed | OSDefinition.java                |
| **PricingTierValidator** | ✅ Completed | PricingTierValidator.java        |
| **Repositories**         | ✅ Completed | \*Repository.java files          |
| **Exceptions**           | ✅ Completed | exception/\*.java files          |
| **SubscriptionDto**      | ✅ Completed | dto/SubscriptionDto.java         |
| **EnhancedService**      | ⏳ Pending   | EnhancedSubscriptionService.java |
| **QuotaService**         | ⏳ Pending   | QuotaService.java                |
| **API Endpoints**        | ⏳ Pending   | api/SubscriptionController.java  |
| **Database Migrations**  | ⏳ Pending   | db/migration/V\*.sql             |
| **Feature Seeding**      | ⏳ Pending   | FeatureCatalogSeeder.java        |
| **Integration Tests**    | ⏳ Pending   | \*Test.java files                |

---

**Last Updated:** 2025-10-25  
**Maintained By:** Fabric Management Team

# 🧭 OS (OPERATING SUBSCRIPTION) MODEL

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Status:** ✅ Active Development

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Core Concept](#core-concept)
3. [FabricOS - Base Platform](#fabricos---base-platform)
4. [Independent OS Subscriptions](#independent-os-subscriptions)
5. [Subscription Model](#subscription-model)
6. [OS Access Control](#os-access-control)
7. [Database Schema](#database-schema)
8. [Examples](#examples)

---

## 🎯 OVERVIEW

**OS (Operating Subscription)** = Abonelik ürünü (Subscription Plan)

Her tenant (şirket) bir veya birden fazla OS'e abone olarak platformun ilgili domain'lerine erişim kazanır.

### **Temel Mantık**

- **FabricOS** = Platformun çekirdeği → Diğer OS'ların koordinasyon merkezi
- **Lite OS'lar** = FabricOS içinde temel seviyede endpoint erişimi (örn: YarnOS Lite)
- **Full OS'lar** = Bağımsız, tam özellikli abonelik paketleri
- **Her OS** = Bir veya birden fazla business domain

---

## 🧩 FABRICOS - BASE PLATFORM

### **Amaç**

Fabrika işletmesi olsun ya da sadece fasonla çalışan tenant olsun, herkesin temel olarak sahip olacağı ana sistem.

### **FabricOS İçeriği**

| Alt OS                          | Domain/Module                                | Açıklama                                                               |
| ------------------------------- | -------------------------------------------- | ---------------------------------------------------------------------- |
| **CoreOS**                      | common/platform                              | Platform kimliği, kullanıcı yönetimi, güvenlik, multi-tenant altyapısı |
| **InventoryOS**                 | logistics/inventory                          | Stok yönetimi, ham madde & mamul takibi                                |
| **ShipmentOS (LogiOS)**         | logistics/shipment, logistics/customs        | Sevkiyat, kargo, gümrük süreçleri                                      |
| **FinanceOS**                   | finance                                      | Finansal işlemler, ödemeler, faturalar                                 |
| **HumanOS**                     | human                                        | Personel, bordro, izin, performans                                     |
| **ProcureOS**                   | procurement                                  | Satın alma, tedarikçi, teklif isteme (RFQ)                             |
| **PlanOS**                      | production/planning                          | Üretim planlama, kapasite yönetimi                                     |
| **FlowOS**                      | common/infrastructure/cqrs + production/task | İş emri akışları, süreç otomasyonu                                     |
| **AnalyticsOS (ReportOS Lite)** | insight/analytics                            | Raporlama ve basit BI ekranları                                        |
| **LoomOS (Lite)**               | production/execution/loom                    | Dokuma işletmeleri için temel erişim                                   |
| **KnitOS (Lite)**               | production/execution/knit                    | Örme işletmeleri için temel erişim                                     |
| **YarnOS (Lite)**               | production/execution/fiber + yarn            | İplik üretimiyle ilgili temel erişim                                   |
| **DyeOS (Lite)**                | production/execution/dye                     | Boya & apre işletmeleri için temel erişim                              |

### **FabricOS Features**

```json
{
  "osCode": "FabricOS",
  "osName": "Fabric Management Base Platform",
  "osType": "BASE",
  "pricingTier": "FREE",
  "includedModules": [
    "common.platform.auth",
    "common.platform.user",
    "common.platform.company",
    "common.platform.policy",
    "common.platform.audit",
    "common.platform.config",
    "common.platform.monitoring",
    "common.platform.communication",
    "logistics.inventory",
    "logistics.shipment",
    "finance.basic",
    "human.basic",
    "procurement.basic",
    "production.planning.basic",
    "insight.analytics.basic"
  ],
  "liteFeatures": {
    "loom_lite": false,
    "knit_lite": false,
    "yarn_lite": false,
    "dye_lite": false
  }
}
```

---

## 🎯 INDEPENDENT OS SUBSCRIPTIONS

### **Full OS'lar**

| OS                         | Domain/Modules                    | Açıklama                                   | Pricing      |
| -------------------------- | --------------------------------- | ------------------------------------------ | ------------ |
| **YarnOS**                 | production/fiber, production/yarn | İplik işletmeleri için tam üretim yönetimi | PROFESSIONAL |
| **LoomOS**                 | production/weaving                | Dokuma üretimi & kalite kontrol süreçleri  | PROFESSIONAL |
| **KnitOS**                 | production/knitting               | Örme üretimi yapan işletmeler için         | PROFESSIONAL |
| **DyeOS**                  | production/finishing              | Boya & apre işletmeleri için tam sistem    | PROFESSIONAL |
| **AccountOS**              | finance/accounting                | Resmi muhasebe, fatura, vergi entegrasyonu | ENTERPRISE   |
| **ProcureOS**              | procurement                       | Tedarik, teklif, satın alma yönetimi       | PROFESSIONAL |
| **PlanOS**                 | production/planning               | Üretim planlama, kapasite yönetimi         | PROFESSIONAL |
| **AnalyticsOS (ReportOS)** | insight/analytics                 | BI, dashboard, raporlama                   | ENTERPRISE   |
| **IntelligenceOS (AIOS)**  | insight/intelligence              | Yapay zeka, tahminleme, optimizasyon       | ENTERPRISE   |
| **CustomOS**               | integration                       | Dış sistem entegrasyonları (ERP, IoT, EDI) | ENTERPRISE   |

### **Future OS Candidates**

| OS                           | Amaç                       | Açıklama                                             | Status     |
| ---------------------------- | -------------------------- | ---------------------------------------------------- | ---------- |
| **EdgeOS**                   | IoT & sensör entegrasyonu  | Makine verisi toplama & anlık üretim takibi          | 🔮 Planned |
| **ChainOS**                  | Blockchain izlenebilirlik  | Ham maddeden müşteriye zincir takibi                 | 🔮 Planned |
| **AIOs**                     | AI modelleri & tahminleme  | Tahmini üretim, kalite optimizasyonu                 | 🔮 Planned |
| **ReportOS (Genişletilmiş)** | BI & KPI analizleri        | Çok boyutlu raporlama & dashboard'lar                | 🔮 Planned |
| **ConfigOS**                 | Tenant bazlı ayar yönetimi | FabricOS'un yapılandırma çekirdeği (opsiyonel)       | 🔮 Planned |
| **HumanOS+**                 | HR geliştirilmiş sürüm     | Performans, eğitim, teşvik sistemleri                | 🔮 Planned |
| **ShipmentOS+**              | Gelişmiş nakliye           | Nakliye firması entegrasyonu, rota optimizasyonu     | 🔮 Planned |
| **CustomsOS**                | Gümrük yönetimi            | ShipmentOS'tan ayrılarak bağımsız abonelik (premium) | 🔮 Planned |

---

## 💎 SUBSCRIPTION MODEL

### **Subscription Structure**

```
Tenant → Company → Multiple OS Subscriptions
    │
    ├─ FabricOS (BASE - Always Active)
    ├─ YarnOS (ACTIVE)
    ├─ PlanOS (TRIAL)
    └─ AnalyticsOS (EXPIRED)
```

### **Subscription States**

| State         | Description                       | Access                  |
| ------------- | --------------------------------- | ----------------------- |
| **ACTIVE**    | Paid subscription, full access    | ✅ Full                 |
| **TRIAL**     | Trial period, limited time        | ✅ Full (until expiry)  |
| **EXPIRED**   | Trial or subscription ended       | ❌ Read-only or blocked |
| **CANCELLED** | Manually cancelled                | ❌ Blocked              |
| **SUSPENDED** | Payment issue or policy violation | ❌ Blocked              |

### **Subscription Example**

```json
{
  "tenantId": "ACME-001",
  "tenantName": "ACME Corporation",
  "subscriptions": [
    {
      "osCode": "FabricOS",
      "osName": "Fabric Management Base Platform",
      "subscriptionStatus": "ACTIVE",
      "startDate": "2025-01-01",
      "expiryDate": null,
      "trialEndsAt": null,
      "pricingTier": "FREE",
      "features": {
        "core": true,
        "inventory": true,
        "shipment": true,
        "finance_basic": true,
        "human_basic": true,
        "procure_basic": true,
        "plan_basic": true,
        "flow": true,
        "analytics_basic": true,
        "loom_lite": false,
        "knit_lite": false,
        "yarn_lite": false,
        "dye_lite": false
      }
    },
    {
      "osCode": "YarnOS",
      "osName": "Yarn Production OS",
      "subscriptionStatus": "ACTIVE",
      "startDate": "2025-01-01",
      "expiryDate": "2025-12-31",
      "trialEndsAt": null,
      "pricingTier": "PROFESSIONAL",
      "features": {
        "fiber_production": true,
        "yarn_production": true,
        "quality_control": true,
        "inventory_advanced": true,
        "planning_advanced": true,
        "analytics": true
      }
    },
    {
      "osCode": "PlanOS",
      "osName": "Production Planning OS",
      "subscriptionStatus": "TRIAL",
      "startDate": "2025-02-01",
      "expiryDate": "2025-04-30",
      "trialEndsAt": "2025-04-30",
      "pricingTier": "PROFESSIONAL",
      "features": {
        "capacity_planning": true,
        "scheduling": true,
        "workcenter": true,
        "optimization": false
      }
    }
  ]
}
```

---

## 🔐 OS ACCESS CONTROL

### **Access Control Flow**

```
1. HTTP Request arrives
   └─ Extract tenant_id from JWT

2. Check OS Subscription
   ├─ Does tenant have required OS?
   ├─ Is subscription ACTIVE or TRIAL?
   └─ Is trial expired?

3. Check Feature Access
   ├─ Does OS include this module?
   ├─ Is feature enabled?
   └─ Is feature available in pricing tier?

4. Check Company Structure
   ├─ Which departments can access?
   ├─ Commercial relationships?
   └─ Parent-child company hierarchy

5. Check User Permissions
   ├─ User role (ADMIN, PLANNER, VIEWER)
   ├─ User department
   └─ User-specific permissions (advanced)

6. Check Additional Conditions
   ├─ Time range
   ├─ Field conditions
   └─ Business rules

7. Decision: ALLOW or DENY
```

### **@PolicyCheck with OS**

```java
@RestController
@RequestMapping("/api/production/yarn")
@RequiredArgsConstructor
public class YarnController {

    @PolicyCheck(
        os = "YarnOS",                    // Required OS
        resource = "fabric.yarn.create",  // Resource
        action = "POST"                   // Action
    )
    @PostMapping
    public ResponseEntity<?> createYarn(@RequestBody CreateYarnRequest request) {
        // Implementation
    }

    @PolicyCheck(
        os = "YarnOS",
        resource = "fabric.yarn.read",
        action = "GET",
        fallbackOs = "FabricOS.yarn_lite"  // Fallback to FabricOS lite version
    )
    @GetMapping("/{id}")
    public ResponseEntity<?> getYarn(@PathVariable UUID id) {
        // Implementation
    }
}
```

### **OS Subscription Check (Code Logic)**

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
            return isSubscriptionActive(cached.get());
        }

        // 2. Check database
        Optional<Subscription> subscription = subscriptionRepository
            .findByTenantIdAndOsCode(tenantId, osCode);

        if (subscription.isEmpty()) {
            return false;
        }

        // 3. Check status
        boolean isActive = isSubscriptionActive(subscription.get());

        // 4. Cache result
        subscriptionCache.put(tenantId, osCode, subscription.get());

        return isActive;
    }

    private boolean isSubscriptionActive(Subscription subscription) {
        if (subscription.getSubscriptionStatus() == SubscriptionStatus.ACTIVE) {
            return true;
        }

        if (subscription.getSubscriptionStatus() == SubscriptionStatus.TRIAL) {
            return subscription.getTrialEndsAt().isAfter(Instant.now());
        }

        return false;
    }
}
```

---

## 🗄️ DATABASE SCHEMA

### **1. OS Definition Table**

```sql
CREATE TABLE common_os_definition (
    id UUID PRIMARY KEY,
    os_code VARCHAR(50) NOT NULL UNIQUE,
    os_name VARCHAR(100) NOT NULL,
    os_type VARCHAR(20) NOT NULL, -- BASE, LITE, FULL, PREMIUM
    description TEXT,
    included_modules JSONB NOT NULL,
    pricing_tier VARCHAR(20) NOT NULL, -- FREE, BASIC, PROFESSIONAL, ENTERPRISE
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Example data
INSERT INTO common_os_definition (id, os_code, os_name, os_type, included_modules, pricing_tier) VALUES
(
    gen_random_uuid(),
    'FabricOS',
    'Fabric Management Base Platform',
    'BASE',
    '["common.platform", "logistics.inventory", "logistics.shipment", "finance.basic", "human.basic", "procurement.basic", "production.planning.basic", "insight.analytics.basic"]'::jsonb,
    'FREE'
),
(
    gen_random_uuid(),
    'YarnOS',
    'Yarn Production OS',
    'FULL',
    '["production.fiber", "production.yarn", "production.quality", "logistics.inventory.advanced", "production.planning.advanced"]'::jsonb,
    'PROFESSIONAL'
);
```

### **2. Tenant Subscription Table**

```sql
CREATE TABLE common_subscription (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(50) NOT NULL,
    os_code VARCHAR(50) NOT NULL,
    subscription_status VARCHAR(20) NOT NULL, -- ACTIVE, TRIAL, EXPIRED, CANCELLED, SUSPENDED
    start_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    trial_ends_at TIMESTAMP,
    features JSONB, -- OS-specific feature toggles
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, os_code),
    FOREIGN KEY (os_code) REFERENCES common_os_definition(os_code)
);

-- Indexes
CREATE INDEX idx_subscription_tenant_os ON common_subscription(tenant_id, os_code);
CREATE INDEX idx_subscription_status ON common_subscription(subscription_status);
CREATE INDEX idx_subscription_expiry ON common_subscription(expiry_date) WHERE expiry_date IS NOT NULL;

-- Example data
INSERT INTO common_subscription (id, tenant_id, uid, os_code, subscription_status, start_date) VALUES
(
    gen_random_uuid(),
    'ACME-001-TENANT-ID',
    'ACME-001-SUB-001',
    'FabricOS',
    'ACTIVE',
    '2025-01-01'
),
(
    gen_random_uuid(),
    'ACME-001-TENANT-ID',
    'ACME-001-SUB-002',
    'YarnOS',
    'ACTIVE',
    '2025-01-01'
);
```

### **3. OS Feature Table**

```sql
CREATE TABLE common_os_feature (
    id UUID PRIMARY KEY,
    os_code VARCHAR(50) NOT NULL,
    feature_code VARCHAR(100) NOT NULL,
    feature_name VARCHAR(255) NOT NULL,
    description TEXT,
    endpoint_pattern VARCHAR(255), -- /api/production/yarn/**
    is_default_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    required_tier VARCHAR(20), -- BASIC, PROFESSIONAL, ENTERPRISE
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(os_code, feature_code),
    FOREIGN KEY (os_code) REFERENCES common_os_definition(os_code)
);

-- Example data
INSERT INTO common_os_feature (id, os_code, feature_code, feature_name, endpoint_pattern, required_tier) VALUES
(
    gen_random_uuid(),
    'YarnOS',
    'fiber_production',
    'Fiber Production Management',
    '/api/production/fiber/**',
    'PROFESSIONAL'
),
(
    gen_random_uuid(),
    'YarnOS',
    'yarn_production',
    'Yarn Production Management',
    '/api/production/yarn/**',
    'PROFESSIONAL'
);
```

---

## 📊 EXAMPLES

### **Example 1: Tenant with Multiple OS**

```json
{
  "tenantId": "ACME-001",
  "tenantName": "ACME Corporation",
  "subscriptions": [
    {
      "osCode": "FabricOS",
      "status": "ACTIVE",
      "modules": ["core", "inventory", "shipment", "finance_basic"]
    },
    {
      "osCode": "YarnOS",
      "status": "ACTIVE",
      "modules": ["fiber", "yarn", "quality"]
    },
    {
      "osCode": "PlanOS",
      "status": "TRIAL",
      "trialEndsAt": "2025-04-30",
      "modules": ["capacity", "scheduling", "workcenter"]
    }
  ],
  "accessMatrix": {
    "/api/production/fiber/**": "ALLOWED (YarnOS)",
    "/api/production/yarn/**": "ALLOWED (YarnOS)",
    "/api/production/loom/**": "DENIED (No LoomOS)",
    "/api/production/planning/**": "ALLOWED (PlanOS - Trial)",
    "/api/logistics/inventory/**": "ALLOWED (FabricOS)",
    "/api/finance/accounting/**": "DENIED (No AccountOS)"
  }
}
```

### **Example 2: Subscription Upgrade Flow**

```
1. Tenant starts with FabricOS (FREE)
   └─ Has: Core, Inventory, Shipment, Finance Basic

2. Tenant subscribes to YarnOS (PROFESSIONAL)
   └─ Gains: Fiber Production, Yarn Production, Quality Control

3. Tenant activates YarnOS Lite in FabricOS
   └─ FabricOS.yarn_lite = true
   └─ Limited yarn access within FabricOS

4. Tenant upgrades to full YarnOS
   └─ yarn_lite disabled
   └─ Full YarnOS activated
```

### **Example 3: Policy Check with OS**

```
Request: POST /api/production/yarn/create

Policy Evaluation:
  1. Tenant Check:
     ✅ tenant_id = ACME-001
     ✅ Has YarnOS subscription
     ✅ Subscription status = ACTIVE
     ✅ Expiry date = 2025-12-31 (future)

  2. Company Check:
     ✅ Department = Production
     ✅ Allowed departments for YarnOS = [production, planning]

  3. User Check:
     ✅ User role = PLANNER
     ✅ Allowed roles for yarn.create = [ADMIN, PLANNER]
     ✅ User department = Production

  4. Condition Check:
     ✅ Time = 10:00 (within 08:00-18:00)
     ✅ quantity = 500 (< 1000)

  Decision: ALLOW
  Reason: "All policy checks passed"
```

---

## 🔄 OS SUBSCRIPTION LIFECYCLE

### **Lifecycle States**

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

### **Auto-Renewal Logic**

```java
@Scheduled(cron = "0 0 2 * * ?") // Every day at 2 AM
@Transactional
public void checkSubscriptionExpiry() {
    List<Subscription> expiringSubscriptions = subscriptionRepository
        .findByExpiryDateBetween(Instant.now(), Instant.now().plus(7, ChronoUnit.DAYS));

    for (Subscription subscription : expiringSubscriptions) {
        // Notify tenant
        notificationService.sendSubscriptionExpiryWarning(
            subscription.getTenantId(),
            subscription.getOsCode(),
            subscription.getExpiryDate()
        );
    }

    // Mark expired subscriptions
    List<Subscription> expired = subscriptionRepository
        .findByExpiryDateBeforeAndStatus(Instant.now(), SubscriptionStatus.ACTIVE);

    for (Subscription subscription : expired) {
        subscription.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
        subscriptionRepository.save(subscription);

        // Publish event
        eventPublisher.publish(new SubscriptionExpiredEvent(
            subscription.getTenantId(),
            subscription.getOsCode()
        ));
    }
}
```

---

## 📋 OS TO MODULE MAPPING

### **Mapping Table**

| OS Code            | Modules                                                  | Endpoints                                         |
| ------------------ | -------------------------------------------------------- | ------------------------------------------------- |
| **FabricOS**       | common/platform, logistics/inventory, logistics/shipment | /api/common/**, /api/logistics/**                 |
| **YarnOS**         | production/fiber, production/yarn                        | /api/production/fiber/**, /api/production/yarn/** |
| **LoomOS**         | production/loom                                          | /api/production/loom/\*\*                         |
| **KnitOS**         | production/knit                                          | /api/production/knit/\*\*                         |
| **DyeOS**          | production/dye                                           | /api/production/dye/\*\*                          |
| **AccountOS**      | finance/accounting                                       | /api/finance/accounting/\*\*                      |
| **PlanOS**         | production/planning                                      | /api/production/planning/\*\*                     |
| **AnalyticsOS**    | insight/analytics                                        | /api/insight/analytics/\*\*                       |
| **IntelligenceOS** | insight/intelligence                                     | /api/insight/intelligence/\*\*                    |
| **CustomOS**       | integration                                              | /api/integration/\*\*                             |

---

## 🔗 OS DEPENDENCY MATRIX

### **OS Dependencies**

Bazı OS'lar diğer OS'lara bağımlıdır. Örneğin, YarnOS için PlanOS'un olması üretim planlamasını kolaylaştırır.

| OS Code            | Required OS           | Optional OS            | Reason                                                   |
| ------------------ | --------------------- | ---------------------- | -------------------------------------------------------- |
| **YarnOS**         | FabricOS              | PlanOS, AnalyticsOS    | Base platform gerekli, planlama ve analitik isteğe bağlı |
| **LoomOS**         | FabricOS              | YarnOS, PlanOS         | Dokuma için iplik ve planlama entegrasyonu               |
| **KnitOS**         | FabricOS              | YarnOS, PlanOS         | Örme için iplik ve planlama entegrasyonu                 |
| **DyeOS**          | FabricOS              | YarnOS, LoomOS, KnitOS | Boyama öncesi üretim entegrasyonu                        |
| **AccountOS**      | FabricOS, FinanceOS   | none                   | Muhasebe için temel finans gerekli                       |
| **PlanOS**         | FabricOS              | YarnOS, LoomOS         | Planlama için üretim modülü entegrasyonu                 |
| **AnalyticsOS**    | FabricOS              | All production OS      | Analitik için veri kaynakları                            |
| **IntelligenceOS** | FabricOS, AnalyticsOS | All production OS      | AI için veri ve analitik altyapısı                       |
| **CustomOS**       | FabricOS              | Any business OS        | Entegrasyon için bağlı modüller                          |

### **Dependency Schema**

```sql
CREATE TABLE common_os_dependency (
    id UUID PRIMARY KEY,
    os_code VARCHAR(50) NOT NULL,
    required_os_code VARCHAR(50) NOT NULL,
    is_optional BOOLEAN NOT NULL DEFAULT FALSE,
    dependency_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (os_code) REFERENCES common_os_definition(os_code),
    FOREIGN KEY (required_os_code) REFERENCES common_os_definition(os_code),
    UNIQUE(os_code, required_os_code)
);

-- Example data
INSERT INTO common_os_dependency (id, os_code, required_os_code, is_optional, dependency_reason) VALUES
(gen_random_uuid(), 'YarnOS', 'FabricOS', FALSE, 'Base platform required for all OS'),
(gen_random_uuid(), 'YarnOS', 'PlanOS', TRUE, 'Planning integration for production scheduling'),
(gen_random_uuid(), 'LoomOS', 'FabricOS', FALSE, 'Base platform required'),
(gen_random_uuid(), 'LoomOS', 'YarnOS', TRUE, 'Yarn integration for loom production'),
(gen_random_uuid(), 'AccountOS', 'FabricOS', FALSE, 'Base platform required'),
(gen_random_uuid(), 'AccountOS', 'FinanceOS', FALSE, 'Finance module required for accounting');
```

### **Dependency Validation**

```java
@Service
@RequiredArgsConstructor
public class SubscriptionValidator {

    private final OSDependencyRepository dependencyRepository;
    private final SubscriptionRepository subscriptionRepository;

    public ValidationResult validateSubscription(UUID tenantId, String osCode) {
        // Get required dependencies
        List<OSDependency> dependencies = dependencyRepository.findByOsCode(osCode);

        List<String> missingDependencies = new ArrayList<>();

        for (OSDependency dependency : dependencies) {
            if (!dependency.isOptional()) {
                // Check if tenant has required OS
                boolean hasRequiredOS = subscriptionRepository
                    .existsByTenantIdAndOsCodeAndStatus(
                        tenantId,
                        dependency.getRequiredOsCode(),
                        SubscriptionStatus.ACTIVE
                    );

                if (!hasRequiredOS) {
                    missingDependencies.add(dependency.getRequiredOsCode());
                }
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

## 🔔 SUBSCRIPTION LIFECYCLE EVENTS

### **Event Types**

| Event                             | Trigger                 | Listeners                               |
| --------------------------------- | ----------------------- | --------------------------------------- |
| **SubscriptionCreatedEvent**      | New subscription        | Analytics, Billing, Notification        |
| **SubscriptionActivatedEvent**    | Trial → Active          | Analytics, Notification                 |
| **SubscriptionTrialStartedEvent** | Trial begins            | Notification, Monitoring                |
| **SubscriptionTrialEndingEvent**  | 7 days before trial end | Notification, Sales                     |
| **SubscriptionExpiredEvent**      | Expiry date reached     | Analytics, Notification, Access Control |
| **SubscriptionRenewedEvent**      | Auto-renewal            | Billing, Analytics                      |
| **SubscriptionCancelledEvent**    | User cancels            | Analytics, Billing, Notification        |
| **SubscriptionSuspendedEvent**    | Payment issue           | Access Control, Notification            |
| **SubscriptionUpgradedEvent**     | Plan upgrade            | Billing, Analytics, Notification        |
| **SubscriptionDowngradedEvent**   | Plan downgrade          | Access Control, Analytics               |

### **Event Schema**

```java
@Getter
public class SubscriptionActivatedEvent extends DomainEvent {

    private final UUID subscriptionId;
    private final String osCode;
    private final String osName;
    private final String pricingTier;
    private final Instant activatedAt;

    public SubscriptionActivatedEvent(UUID tenantId, UUID subscriptionId, String osCode, String osName, String pricingTier) {
        super(tenantId, "SUBSCRIPTION_ACTIVATED");
        this.subscriptionId = subscriptionId;
        this.osCode = osCode;
        this.osName = osName;
        this.pricingTier = pricingTier;
        this.activatedAt = Instant.now();
    }
}

@Getter
public class SubscriptionTrialEndingEvent extends DomainEvent {

    private final UUID subscriptionId;
    private final String osCode;
    private final Instant trialEndsAt;
    private final Integer daysRemaining;

    public SubscriptionTrialEndingEvent(UUID tenantId, UUID subscriptionId, String osCode, Instant trialEndsAt) {
        super(tenantId, "SUBSCRIPTION_TRIAL_ENDING");
        this.subscriptionId = subscriptionId;
        this.osCode = osCode;
        this.trialEndsAt = trialEndsAt;
        this.daysRemaining = (int) ChronoUnit.DAYS.between(Instant.now(), trialEndsAt);
    }
}

@Getter
public class SubscriptionExpiredEvent extends DomainEvent {

    private final UUID subscriptionId;
    private final String osCode;
    private final Instant expiredAt;
    private final SubscriptionStatus previousStatus;

    public SubscriptionExpiredEvent(UUID tenantId, UUID subscriptionId, String osCode, SubscriptionStatus previousStatus) {
        super(tenantId, "SUBSCRIPTION_EXPIRED");
        this.subscriptionId = subscriptionId;
        this.osCode = osCode;
        this.expiredAt = Instant.now();
        this.previousStatus = previousStatus;
    }
}
```

### **Event Listeners**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEventListener {

    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final PolicyCache policyCache;

    @EventListener
    @Transactional
    public void handleSubscriptionActivated(SubscriptionActivatedEvent event) {
        log.info("Subscription activated: tenantId={}, osCode={}", event.getTenantId(), event.getOsCode());

        // Send welcome email
        notificationService.sendSubscriptionWelcome(event.getTenantId(), event.getOsCode());

        // Track analytics
        analyticsService.trackSubscriptionActivation(event);

        // Invalidate policy cache (new OS means new permissions)
        policyCache.invalidateTenant(event.getTenantId());
    }

    @EventListener
    @Transactional
    public void handleSubscriptionTrialEnding(SubscriptionTrialEndingEvent event) {
        log.warn("Trial ending soon: tenantId={}, osCode={}, daysRemaining={}",
            event.getTenantId(), event.getOsCode(), event.getDaysRemaining());

        // Send reminder email
        notificationService.sendTrialEndingReminder(
            event.getTenantId(),
            event.getOsCode(),
            event.getDaysRemaining()
        );
    }

    @EventListener
    @Transactional
    public void handleSubscriptionExpired(SubscriptionExpiredEvent event) {
        log.warn("Subscription expired: tenantId={}, osCode={}", event.getTenantId(), event.getOsCode());

        // Send expiry notification
        notificationService.sendSubscriptionExpired(event.getTenantId(), event.getOsCode());

        // Track analytics
        analyticsService.trackSubscriptionExpiry(event);

        // Invalidate policy cache (lost OS means lost permissions)
        policyCache.invalidateTenant(event.getTenantId());

        // Disable OS-specific features
        featureToggleService.disableOSFeatures(event.getTenantId(), event.getOsCode());
    }
}
```

---

## ⏱️ FEATURE-LEVEL RATE LIMITING

### **Per-Tenant Rate Limits**

Bazı OS'lar (özellikle AnalyticsOS, IntelligenceOS) için tenant bazlı rate limiting uygulanır.

```sql
CREATE TABLE common_os_rate_limit (
    id UUID PRIMARY KEY,
    os_code VARCHAR(50) NOT NULL,
    feature_code VARCHAR(100) NOT NULL,
    endpoint_pattern VARCHAR(255) NOT NULL,
    requests_per_minute INTEGER NOT NULL,
    requests_per_hour INTEGER NOT NULL,
    requests_per_day INTEGER NOT NULL,
    pricing_tier VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (os_code) REFERENCES common_os_definition(os_code),
    UNIQUE(os_code, feature_code, pricing_tier)
);

-- Example data
INSERT INTO common_os_rate_limit (id, os_code, feature_code, endpoint_pattern, requests_per_minute, requests_per_hour, requests_per_day, pricing_tier) VALUES
(gen_random_uuid(), 'AnalyticsOS', 'report_generation', '/api/insight/analytics/reports/**', 10, 100, 1000, 'PROFESSIONAL'),
(gen_random_uuid(), 'AnalyticsOS', 'report_generation', '/api/insight/analytics/reports/**', 50, 500, 5000, 'ENTERPRISE'),
(gen_random_uuid(), 'IntelligenceOS', 'ai_prediction', '/api/insight/intelligence/predict/**', 5, 50, 500, 'PROFESSIONAL'),
(gen_random_uuid(), 'IntelligenceOS', 'ai_prediction', '/api/insight/intelligence/predict/**', 20, 200, 2000, 'ENTERPRISE');
```

### **Rate Limiting Check**

```java
@Component
@RequiredArgsConstructor
public class OSRateLimiter {

    private final OSRateLimitRepository rateLimitRepository;
    private final RedisTemplate<String, Integer> redisTemplate;

    public boolean checkRateLimit(UUID tenantId, String osCode, String featureCode, String pricingTier) {
        // Get rate limit config
        OSRateLimit rateLimit = rateLimitRepository
            .findByOsCodeAndFeatureCodeAndPricingTier(osCode, featureCode, pricingTier)
            .orElseThrow(() -> new RateLimitNotFoundException(osCode, featureCode));

        // Check Redis counters
        String minuteKey = String.format("ratelimit:%s:%s:%s:minute", tenantId, osCode, featureCode);
        String hourKey = String.format("ratelimit:%s:%s:%s:hour", tenantId, osCode, featureCode);
        String dayKey = String.format("ratelimit:%s:%s:%s:day", tenantId, osCode, featureCode);

        Integer minuteCount = redisTemplate.opsForValue().get(minuteKey);
        Integer hourCount = redisTemplate.opsForValue().get(hourKey);
        Integer dayCount = redisTemplate.opsForValue().get(dayKey);

        // Check limits
        if (minuteCount != null && minuteCount >= rateLimit.getRequestsPerMinute()) {
            return false; // Limit exceeded
        }

        if (hourCount != null && hourCount >= rateLimit.getRequestsPerHour()) {
            return false;
        }

        if (dayCount != null && dayCount >= rateLimit.getRequestsPerDay()) {
            return false;
        }

        // Increment counters
        redisTemplate.opsForValue().increment(minuteKey);
        redisTemplate.expire(minuteKey, Duration.ofMinutes(1));

        redisTemplate.opsForValue().increment(hourKey);
        redisTemplate.expire(hourKey, Duration.ofHours(1));

        redisTemplate.opsForValue().increment(dayKey);
        redisTemplate.expire(dayKey, Duration.ofDays(1));

        return true; // Within limits
    }
}
```

### **Rate Limiting Aspect**

```java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OSRateLimitAspect {

    private final OSRateLimiter rateLimiter;

    @Around("@annotation(osRateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, OSRateLimit osRateLimit) throws Throwable {
        // Get tenant context
        UUID tenantId = TenantContext.getCurrentTenantId();

        // Get subscription
        Subscription subscription = subscriptionRepository
            .findByTenantIdAndOsCode(tenantId, osRateLimit.osCode())
            .orElseThrow(() -> new SubscriptionNotFoundException(tenantId, osRateLimit.osCode()));

        // Check rate limit
        boolean allowed = rateLimiter.checkRateLimit(
            tenantId,
            osRateLimit.osCode(),
            osRateLimit.featureCode(),
            subscription.getPricingTier()
        );

        if (!allowed) {
            log.warn("Rate limit exceeded: tenantId={}, osCode={}, feature={}",
                tenantId, osRateLimit.osCode(), osRateLimit.featureCode());
            throw new RateLimitExceededException(osRateLimit.osCode(), osRateLimit.featureCode());
        }

        return joinPoint.proceed();
    }
}
```

### **Usage**

```java
@RestController
@RequestMapping("/api/insight/analytics/reports")
@RequiredArgsConstructor
public class ReportController {

    @OSRateLimit(osCode = "AnalyticsOS", featureCode = "report_generation")
    @PostMapping("/generate")
    public ResponseEntity<?> generateReport(@RequestBody ReportRequest request) {
        // Implementation
    }

    @OSRateLimit(osCode = "IntelligenceOS", featureCode = "ai_prediction")
    @PostMapping("/predict")
    public ResponseEntity<?> predict(@RequestBody PredictionRequest request) {
        // Implementation
    }
}
```

---

## 📊 UID OPTIMIZATION

### **UID Best Practices**

UID sadece **human-readable reference** olarak kullanılır, mantıksal primary key değildir.

```java
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;  // Primary key (machine-level)

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;  // Tenant isolation

    @Column(name = "uid", nullable = false, unique = true, length = 50)
    private String uid;  // Human-readable reference (NOT primary key)

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

### **UID Generation Pattern**

```
Pattern: {TENANT_UID}-{MODULE}-{ENTITY}-{SEQUENCE}

Examples:
  - ACME-001-USER-00042
  - ACME-001-MAT-05123
  - ACME-001-INV-00891
  - XYZ-002-ORD-00156
```

### **UID Generator**

```java
@Component
@RequiredArgsConstructor
public class UIDGenerator {

    private final SequenceRepository sequenceRepository;

    public String generate(String tenantUid, String module, String entity) {
        // Get next sequence
        Long sequence = sequenceRepository.getNextSequence(tenantUid, module, entity);

        // Format: TENANT-MODULE-ENTITY-SEQUENCE
        return String.format("%s-%s-%s-%05d",
            tenantUid,      // ACME-001
            module,         // USER, MAT, INV
            entity,         // (optional, can be same as module)
            sequence        // 00042
        );
    }
}
```

### **UID Usage**

```
Purpose: Human-readable reference for:
  ✅ Audit logs
  ✅ Admin dashboards
  ✅ Support tickets
  ✅ Customer communication
  ✅ Debugging

NOT for:
  ❌ Primary key
  ❌ Foreign key relationships
  ❌ Database joins
  ❌ API responses (use UUID)
```

---

## 💡 BENEFITS

### **For Tenants**

- ✅ **Pay for what you use** - Sadece ihtiyaç duyulan OS'lara ödeme
- ✅ **Try before buy** - Trial period ile deneme imkanı
- ✅ **Flexible scaling** - İhtiyaç arttıkça ek OS eklenebilir
- ✅ **Cost effective** - Gereksiz feature'lar için ödeme yok

### **For Platform**

- ✅ **Revenue streams** - Multiple subscription tiers
- ✅ **Feature control** - Fine-grained access control
- ✅ **Easy upselling** - Lite → Full upgrade path
- ✅ **Usage analytics** - Which OS is popular?

### **For Developers**

- ✅ **Clear boundaries** - OS = Module mapping
- ✅ **Easy testing** - Enable/disable OS for testing
- ✅ **Flexible deployment** - Disable unused OS to save resources
- ✅ **Modular development** - Develop OS independently

---

## 📝 CHANGELOG

### **Version 1.0 - 2025-01-27**

- ✅ Initial OS Subscription Model
- ✅ FabricOS base platform defined
- ✅ Independent OS'lar tanımlandı
- ✅ Subscription lifecycle states
- ✅ Database schema
- ✅ **OS Dependency Matrix** eklendi
- ✅ **Subscription Lifecycle Events** eklendi
- ✅ **Feature-Level Rate Limiting** eklendi
- ✅ **UID Optimization** best practices

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

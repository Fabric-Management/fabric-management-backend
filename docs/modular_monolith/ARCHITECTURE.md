# 🏗️ MODULAR MONOLITH ARCHITECTURE

**Version:** 2.0  
**Last Updated:** 2025-10-25  
**Status:** ✅ Active Development

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Core Principles](#core-principles)
3. [Module Structure](#module-structure)
4. [Common Module](#common-module)
5. [Business Modules](#business-modules)
6. [Module Dependencies](#module-dependencies)
7. [Communication Patterns](#communication-patterns)
8. [Data Management](#data-management)
9. [Event-Driven Architecture](#event-driven-architecture)
10. [Technology Stack](#technology-stack)
11. [Subscription Model Architecture](#subscription-model-architecture) ⭐ NEW
12. [Performance Characteristics](#performance-characteristics)
13. [Future Evolution](#future-evolution)

---

## 🎯 OVERVIEW

Fabric Management platformu, **Modular Monolith** mimarisi ile geliştirilmiştir. Bu mimari, mikroservis mimarisinin avantajlarını (modülerlik, bağımsız geliştirme, net sınırlar) tek bir deploy edilebilir uygulamanın basitliği ile birleştirir.

### **Neden Modular Monolith?**

| Kriter                      | Mikroservisler | Modular Monolith      | Karar               |
| --------------------------- | -------------- | --------------------- | ------------------- |
| **Operasyonel Karmaşıklık** | Yüksek         | Düşük                 | ✅ Modular Monolith |
| **Network Overhead**        | Var            | Yok                   | ✅ Modular Monolith |
| **Development Hızı**        | Yavaş          | Hızlı                 | ✅ Modular Monolith |
| **Debugging**               | Zor            | Kolay                 | ✅ Modular Monolith |
| **Transaction Management**  | Dağıtık        | Basit                 | ✅ Modular Monolith |
| **Maliyet**                 | Yüksek         | Düşük                 | ✅ Modular Monolith |
| **Modülerlik**              | Var            | Var                   | ✅ Eşit             |
| **Ölçeklenebilirlik**       | Esnek          | Kısıtlı (ama yeterli) | ⚖️ Kabul edilebilir |

---

## 💎 CORE PRINCIPLES

### **1. Clean Domain Boundaries**

Her modül kendi sorumluluklarıyla sınırlıdır ve diğer modüllere doğrudan bağımlı değildir.

```java
@ApplicationModule(
    allowedDependencies = {"common"} // Sadece common'a bağımlı
)
public class ProductionModule {
    // Production domain logic
}
```

### **2. In-Process Communication**

Modüller arası çağrılar Java method çağrısıdır, network overhead yoktur.

```java
// ❌ Mikroservisler: HTTP call (yavaş, network overhead)
MaterialDto material = restTemplate.getForObject(
    "http://material-service/api/materials/123",
    MaterialDto.class
);

// ✅ Modular Monolith: Direct method call (hızlı, in-process)
MaterialDto material = materialFacade.getMaterial(materialId);
```

### **3. Event-Driven Interaction**

Domain değişiklikleri event olarak publish edilir, eventual consistency sağlanır.

```java
// 1. Domain event publish
Material material = Material.create(name, type);
eventPublisher.publish(new MaterialCreatedEvent(material.getId()));

// 2. Başka modül event'i dinler
@EventListener
public void handle(MaterialCreatedEvent event) {
    // React to material creation
}
```

### **4. Multi-Tenant Aware**

Her tablo tenant_id içerir, Row-Level Security (RLS) aktiftir.

```sql
CREATE TABLE prod_material (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    -- RLS policy ile tenant_id otomatik filter
);
```

### **5. Centralized Policy Control**

Tüm endpoint'ler `@PolicyCheck` anotasyonu ile korunur.

```java
@PolicyCheck(resource="fabric.material.create", action="POST")
@PostMapping("/api/production/materials")
public ResponseEntity<?> createMaterial(@RequestBody MaterialDto dto) {
    // Implementation
}
```

### **6. Self-Healing & Degraded Mode**

Kafka veya Redis down olsa bile sistem çalışmaya devam eder.

```java
@Autowired(required = false) // Optional dependency
private KafkaTemplate<String, Object> kafkaTemplate;

public void publishEvent(DomainEvent event) {
    if (kafkaTemplate != null) {
        kafkaTemplate.send("events", event);
    } else {
        // Fallback: Save to outbox table
        outboxRepository.save(OutboxEvent.from(event));
    }
}
```

---

## 🧱 MODULE STRUCTURE

### **Vertical Slice Architecture**

Her modül kendi katmanlarına sahiptir:

```
material/
├─ api/
│  ├─ controller/          # External REST API
│  │  └─ MaterialController.java
│  └─ facade/              # Internal API
│     └─ MaterialFacade.java
├─ app/
│  ├─ command/             # CQRS Commands
│  │  └─ CreateMaterialCommand.java
│  ├─ query/               # CQRS Queries
│  │  └─ GetMaterialQuery.java
│  └─ MaterialService.java # Business Logic
├─ domain/
│  ├─ Material.java        # Domain Entity
│  ├─ MaterialType.java    # Value Object
│  └─ event/
│     └─ MaterialCreatedEvent.java # Domain Event
├─ infra/
│  ├─ repository/
│  │  └─ MaterialRepository.java # Data Access
│  └─ client/
│     ├─ interface/
│     │  └─ LogisticsClient.java # Interface
│     └─ impl/
│        └─ LogisticsRestClient.java # Implementation
└─ dto/
   ├─ MaterialDto.java     # Data Transfer Object
   ├─ CreateMaterialRequest.java
   └─ UpdateMaterialRequest.java
```

### **Katman Sorumlulukları**

| Katman                | Sorumluluk                             | Örnek                                              |
| --------------------- | -------------------------------------- | -------------------------------------------------- |
| **api/controller/**   | External REST endpoints                | `@RestController`, `@PostMapping`                  |
| **api/facade/**       | Internal API for other modules         | `public interface MaterialFacade`                  |
| **app/**              | Business logic, command/query handlers | `MaterialService`, `CreateMaterialCommand`         |
| **domain/**           | Domain entities, value objects, events | `Material`, `MaterialType`, `MaterialCreatedEvent` |
| **infra/repository/** | Data access                            | `MaterialRepository extends JpaRepository`         |
| **infra/client/**     | External module communication          | `LogisticsClient`, `LogisticsRestClient`           |
| **dto/**              | Data transfer objects                  | `MaterialDto`, `CreateMaterialRequest`             |

---

## 🔧 COMMON MODULE

Common module tüm cross-cutting concerns'leri içerir.

### **Platform (İşletim Altyapısı)**

```
common/platform/
├─ auth/              # Authentication & Authorization
├─ user/              # User Management
├─ company/           # Company/Tenant Management
├─ policy/            # Policy Engine
├─ audit/             # Audit Logging
├─ config/            # Configuration
├─ monitoring/        # Health & Metrics
└─ communication/     # Notifications
```

### **Infrastructure (Teknik Altyapı)**

```
common/infrastructure/
├─ persistence/       # BaseEntity, AuditableEntity
├─ events/            # DomainEvent, OutboxEvent
├─ mapping/           # MapStruct Config
├─ cqrs/              # Command, Query, Handler
├─ web/               # ApiResponse, PagedResponse
└─ security/          # TenantFilter, JwtUtils
```

### **Util (Yardımcı Sınıflar)**

```
common/util/
├─ Money.java         # Money value object
├─ Unit.java          # Unit value object
└─ TimeHelper.java    # Time utilities
```

---

## 🏢 BUSINESS MODULES

### **Production Module**

```
production/
├─ masterdata/        # Material, Recipe
├─ planning/          # Capacity, Scheduling, Workcenter
├─ execution/         # Fiber, Yarn, Loom, Knit, Dye
└─ quality/           # Inspections, Results
```

### **Logistics Module**

```
logistics/
├─ inventory/         # Item, Lot, Movement, Location
├─ shipment/          # Order, Carrier, Tracking
└─ customs/           # Customs Declaration
```

### **Finance Module**

```
finance/
├─ ar/                # Accounts Receivable
├─ ap/                # Accounts Payable
├─ cashbank/          # Cash & Bank Management
├─ invoice/           # Invoicing
└─ costing/           # Cost Accounting
```

### **Human Module**

```
human/
├─ employee/          # Employee Management
├─ org/               # Organization Structure
├─ leave/             # Leave Management
├─ payroll/           # Payroll Processing
└─ performance/       # Performance Management
```

### **Procurement Module**

```
procurement/
├─ supplier/          # Supplier Management
├─ requisition/       # Purchase Requisition
├─ rfq/               # Request for Quotation
├─ po/                # Purchase Order
└─ grn/               # Goods Receipt Note
```

### **Integration Module**

```
integration/
├─ adapters/          # ERP, E-Invoice, Carriers
├─ webhooks/          # Webhook Management
├─ transforms/        # Data Transformation
└─ notifications/     # Notification Providers
```

### **Insight Module**

```
insight/
├─ analytics/         # Read Models, Dashboards
└─ intelligence/      # AI, Forecasts, Optimization
```

---

## 🔗 MODULE DEPENDENCIES

### **Dependency Rules**

```java
// common/CommonModule.java
@ApplicationModule(
    allowedDependencies = {} // common başka module'e bağımlı değil
)
class CommonModule {}

// production/ProductionModule.java
@ApplicationModule(
    allowedDependencies = {"common"} // sadece common'a bağımlı
)
class ProductionModule {}

// logistics/LogisticsModule.java
@ApplicationModule(
    allowedDependencies = {"common", "production"} // production event'lerini dinler
)
class LogisticsModule {}
```

### **Dependency Matrix**

| Module        | Allowed Dependencies                   |
| ------------- | -------------------------------------- |
| `common`      | none                                   |
| `production`  | common                                 |
| `logistics`   | common, production                     |
| `finance`     | common, logistics, production          |
| `human`       | common                                 |
| `procurement` | common, finance                        |
| `integration` | common, production, logistics, finance |
| `insight`     | common, production, logistics, finance |

**Enforcement:** Spring Modulith veya ArchUnit ile compile-time kontrolü.

---

## 💬 COMMUNICATION PATTERNS

### **1. Direct Call (Facade)**

**Kullanım:** Senkron, read-only, low-latency

```java
// Logistics modülü, Production modülünün facade'ini kullanır
@RequiredArgsConstructor
public class InventoryService {
    private final MaterialFacade materialFacade; // In-process call

    public void checkMaterialAvailability(UUID materialId) {
        MaterialDto material = materialFacade.getMaterial(materialId);
        // Use material data
    }
}
```

### **2. Domain Event**

**Kullanım:** Asenkron, eventual consistency, loose coupling

```java
// Production modülü event publish eder
eventPublisher.publish(new MaterialCreatedEvent(tenantId, materialId));

// Logistics modülü event'i dinler
@EventListener
public void handle(MaterialCreatedEvent event) {
    // Create inventory item for new material
}
```

### **3. Outbox Pattern**

**Kullanım:** Reliable event publishing, transaction safety

```java
@Transactional
public void createMaterial(CreateMaterialRequest request) {
    // 1. Save material
    Material material = materialRepository.save(Material.create(request));

    // 2. Save event to outbox (same transaction)
    outboxRepository.save(OutboxEvent.from(new MaterialCreatedEvent(material)));

    // 3. OutboxScheduler will publish event to Kafka
}
```

---

## 🗃️ DATA MANAGEMENT

### **Database Schema**

Her domain kendi schema prefix'ine sahiptir:

```sql
-- Common schemas
CREATE SCHEMA IF NOT EXISTS common_auth;
CREATE SCHEMA IF NOT EXISTS common_user;
CREATE SCHEMA IF NOT EXISTS common_policy;
CREATE SCHEMA IF NOT EXISTS common_audit;

-- Business schemas
CREATE SCHEMA IF NOT EXISTS prod_masterdata;
CREATE SCHEMA IF NOT EXISTS prod_planning;
CREATE SCHEMA IF NOT EXISTS prod_execution;
CREATE SCHEMA IF NOT EXISTS logi_inventory;
CREATE SCHEMA IF NOT EXISTS logi_shipment;
CREATE SCHEMA IF NOT EXISTS fin_ar;
CREATE SCHEMA IF NOT EXISTS fin_ap;
```

### **Migration Strategy**

Flyway migration'lar domain bazlı organize edilir:

```
src/main/resources/db/migration/
├─ V1__common_auth_init.sql
├─ V2__common_user_init.sql
├─ V3__common_policy_init.sql
├─ V4__common_audit_init.sql
├─ V10__prod_masterdata_init.sql
├─ V11__prod_planning_init.sql
├─ V12__prod_execution_init.sql
├─ V20__logi_inventory_init.sql
├─ V21__logi_shipment_init.sql
├─ V30__fin_ar_init.sql
└─ V31__fin_ap_init.sql
```

### **Multi-Tenancy**

Her tablo `tenant_id` içerir, RLS policy'leri ile tenant izolasyonu sağlanır:

```sql
CREATE TABLE prod_material (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    -- other fields
);

-- Row-Level Security Policy
ALTER TABLE prod_material ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON prod_material
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);
```

---

## 🔄 EVENT-DRIVEN ARCHITECTURE

### **Event Flow**

```
┌─────────────────┐
│  MaterialService│
│  (Production)   │
└────────┬────────┘
         │
         │ 1. Save Material
         ▼
┌─────────────────┐
│  MaterialRepo   │
│  (Database)     │
└────────┬────────┘
         │
         │ 2. Save OutboxEvent
         ▼
┌─────────────────┐
│  OutboxRepo     │
│  (Database)     │
└────────┬────────┘
         │
         │ 3. OutboxScheduler
         ▼
┌─────────────────┐
│  Kafka          │
│  (Event Bus)    │
└────────┬────────┘
         │
         │ 4. Event Listeners
         ▼
┌─────────────────────────────────────┐
│  InventoryService (Logistics)       │
│  CostingService (Finance)           │
│  AnalyticsService (Insight)         │
└─────────────────────────────────────┘
```

### **Event Types**

| Event                  | Publisher      | Listeners                   |
| ---------------------- | -------------- | --------------------------- |
| `MaterialCreatedEvent` | Production     | Logistics, Finance, Insight |
| `MaterialUpdatedEvent` | Production     | Logistics, Finance, Insight |
| `InventoryMovedEvent`  | Logistics      | Finance, Insight            |
| `InvoiceCreatedEvent`  | Finance        | Insight                     |
| `UserCreatedEvent`     | Common/User    | All modules                 |
| `CompanyCreatedEvent`  | Common/Company | All modules                 |

---

## 🛠️ TECHNOLOGY STACK

### **Core**

- **Java 17** - Programming language
- **Spring Boot 3.x** - Application framework
- **Spring Modulith** - Module boundary enforcement
- **Maven** - Build tool

### **Data**

- **PostgreSQL 15+** - Primary database
- **Flyway** - Database migration
- **Spring Data JPA** - ORM
- **Hibernate** - JPA implementation

### **Messaging**

- **Kafka** - Event streaming (optional)
- **Spring Events** - In-process events
- **Outbox Pattern** - Reliable event publishing

### **Caching**

- **Redis** - Distributed cache
- **Caffeine** - Local cache

### **Security**

- **Spring Security** - Security framework
- **JWT** - Authentication
- **OAuth2** - Authorization

### **Observability**

- **Spring Actuator** - Health checks, metrics
- **Micrometer** - Metrics collection
- **OpenTelemetry** - Distributed tracing
- **Prometheus** - Metrics storage
- **Grafana** - Metrics visualization

### **Testing**

- **JUnit 5** - Unit testing
- **Testcontainers** - Integration testing
- **ArchUnit** - Architecture testing
- **Spring Modulith Test** - Module boundary testing

---

## 💳 SUBSCRIPTION MODEL ARCHITECTURE

### **⭐ Composable Feature-Based Subscription**

FabricOS, **String-based, flexible** pricing tier sistemi kullanır. Her OS'un kendi tier isimlendirmesi vardır.

### **Architecture Layers**

```
┌────────────────────────────────────────────────────┐
│  1. OS Subscription Layer                          │
│  ─────────────────────────────                     │
│  • Entity: Subscription                            │
│  • Check: Tenant YarnOS'a abone mi?                │
│  • Database: common_subscription                   │
│  • Field: os_code, pricing_tier (String)           │
├────────────────────────────────────────────────────┤
│  2. Feature Entitlement Layer                      │
│  ─────────────────────────────                     │
│  • Entity: FeatureCatalog                          │
│  • Check: YarnOS'ta "yarn.blend" feature var mı?   │
│  • Database: common_feature_catalog                │
│  • Field: feature_id, available_in_tiers (JSONB)   │
├────────────────────────────────────────────────────┤
│  3. Usage Quota Layer                              │
│  ─────────────────────────────                     │
│  • Entity: SubscriptionQuota                       │
│  • Check: Fiber entity limiti aşıldı mı?           │
│  • Database: common_subscription_quota             │
│  • Field: quota_type, quota_limit, quota_used      │
├────────────────────────────────────────────────────┤
│  4. Policy Engine Layer (RBAC/ABAC)                │
│  ─────────────────────────────                     │
│  • Entity: PolicyRule                              │
│  • Check: User yetkisi var mı? Context uygun mu?   │
│  • Database: common_policy_rule                    │
└────────────────────────────────────────────────────┘
```

### **Domain Model**

```java
// Subscription Entity - String-based pricing tier
@Entity
@Table(name = "common_subscription", schema = "common_company")
public class Subscription extends BaseEntity {
    private String osCode;              // "YarnOS", "LoomOS", etc.
    private String osName;
    private SubscriptionStatus status;  // TRIAL, ACTIVE, EXPIRED
    private String pricingTier;         // "Starter", "Professional", "Enterprise"
                                        // OR "Standard", "Advanced"
    private Map<String, Boolean> features;  // JSONB override
    private Instant startDate;
    private Instant expiryDate;
}

// FeatureCatalog Entity - Feature entitlement rules
@Entity
@Table(name = "common_feature_catalog", schema = "common_company")
public class FeatureCatalog extends BaseEntity {
    private String featureId;                  // "yarn.blend.management"
    private String osCode;                      // "YarnOS"
    private String featureName;
    private List<String> availableInTiers;      // ["Professional", "Enterprise"]
    private Boolean isActive;
}

// SubscriptionQuota Entity - Usage limits
@Entity
@Table(name = "common_subscription_quota", schema = "common_company")
public class SubscriptionQuota extends BaseEntity {
    private UUID subscriptionId;
    private String quotaType;       // "users", "api_calls", "fiber_entities"
    private Long quotaLimit;
    private Long quotaUsed;
    private String resetPeriod;     // "MONTHLY", "DAILY", "NONE"
}

// PricingTierValidator - Tier validation utility
public class PricingTierValidator {
    private static final Map<String, Set<String>> OS_VALID_TIERS = Map.of(
        "YarnOS", Set.of("Starter", "Professional", "Enterprise"),
        "AnalyticsOS", Set.of("Standard", "Advanced", "Enterprise"),
        "IntelligenceOS", Set.of("Professional", "Enterprise"),
        "FabricOS", Set.of("Base")
    );

    public static boolean isValidTier(String osCode, String tierName);
    public static String getDefaultTier(String osCode);
}
```

### **OS Catalog**

| OS                 | Tier'lar                         | Entry Price | Modules                |
| ------------------ | -------------------------------- | ----------- | ---------------------- |
| **FabricOS**       | Base (zorunlu)                   | $199/mo     | auth, user, policy     |
| **YarnOS**         | Starter/Professional/Enterprise  | $99/mo      | fiber, yarn            |
| **LoomOS**         | Starter/Professional/Enterprise  | $149/mo     | weaving                |
| **KnitOS**         | Starter/Professional/Enterprise  | $129/mo     | knitting               |
| **DyeOS**          | Starter/Professional/Enterprise  | $119/mo     | finishing              |
| **AnalyticsOS**    | Standard/Advanced/Enterprise     | $149/mo     | analytics              |
| **IntelligenceOS** | Professional/Enterprise          | $299/mo     | intelligence (AI/ML)   |
| **EdgeOS**         | Starter/Professional/Enterprise  | $199/mo     | IoT integration        |
| **AccountOS**      | Standard/Professional/Enterprise | $79/mo      | accounting             |
| **CustomOS**       | Standard/Professional/Enterprise | $399/mo     | integration (ERP, EDI) |

### **Feature Gating Example**

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

        // ⭐ SUBSCRIPTION CHECK
        subscriptionService.enforceEntitlement(
            tenantId,
            "yarn.blend.management",  // Feature ID
            null                       // No quota check
        );

        FiberBlendDto blend = fiberService.createBlend(fiberId, request);
        return ResponseEntity.ok(blend);
    }
}

// EnhancedSubscriptionService implementation
public void enforceEntitlement(UUID tenantId, String featureId, String quotaType) {
    // Layer 1: OS Subscription Check
    String osCode = extractOsCode(featureId);  // "yarn.blend.management" → "YarnOS"
    if (!hasActiveSubscription(tenantId, osCode)) {
        throw new SubscriptionRequiredException(osCode + " subscription required");
    }

    // Layer 2: Feature Entitlement Check
    if (!hasFeature(tenantId, featureId)) {
        throw new FeatureNotAvailableException(
            "Feature requires Professional tier or higher"
        );
    }

    // Layer 3: Usage Quota Check (if applicable)
    if (quotaType != null && !hasQuota(tenantId, quotaType)) {
        throw new QuotaExceededException("Quota exceeded for " + quotaType);
    }
}
```

### **Database Schema**

```sql
-- Subscription table
CREATE TABLE common_company.common_subscription (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    os_code VARCHAR(50) NOT NULL,
    os_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    pricing_tier VARCHAR(50) NOT NULL,  -- ⭐ String, not ENUM!
    start_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    features JSONB,                      -- ⭐ Feature override
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(tenant_id, os_code)
);

-- Feature catalog table
CREATE TABLE common_company.common_feature_catalog (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    feature_id VARCHAR(100) UNIQUE NOT NULL,
    os_code VARCHAR(50) NOT NULL,
    feature_name VARCHAR(255) NOT NULL,
    available_in_tiers JSONB,           -- ⭐ ["Professional", "Enterprise"]
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Usage quota table
CREATE TABLE common_company.common_subscription_quota (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subscription_id UUID REFERENCES common_subscription(id),
    quota_type VARCHAR(50) NOT NULL,
    quota_limit BIGINT NOT NULL,
    quota_used BIGINT DEFAULT 0,
    reset_period VARCHAR(20),          -- "MONTHLY", "DAILY", "NONE"
    last_reset_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(tenant_id, subscription_id, quota_type)
);

-- OS definition table
CREATE TABLE common_company.common_os_definition (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    os_code VARCHAR(50) UNIQUE NOT NULL,
    os_name VARCHAR(255) NOT NULL,
    description TEXT,
    included_modules JSONB,             -- ["production.fiber", "production.yarn"]
    available_tiers JSONB,              -- ⭐ ["Starter", "Professional", "Enterprise"]
    default_tier VARCHAR(50),           -- ⭐ "Starter"
    created_at TIMESTAMP DEFAULT NOW()
);
```

### **Key Features**

✅ **String-Based Tiers** - Her OS'un kendi tier isimleri (enum yok!)  
✅ **JSONB Storage** - Esnek feature ve tier yapısı  
✅ **Composable** - Sadece ihtiyaç duyulan OS'lar alınır  
✅ **Granular Control** - Feature-level entitlement  
✅ **Usage Quotas** - API, storage, entity limitleri  
✅ **Multi-Tier Support** - OS bazlı farklı tier isimlendirme  
✅ **Database-Driven** - Feature catalog database'de saklanır

### **Documentation**

**Detaylı bilgi için:**

- [SUBSCRIPTION_MODEL.md](./SUBSCRIPTION_MODEL.md) - Kapsamlı dokümantasyon (1167 satır)
- [SUBSCRIPTION_QUICK_START.md](./SUBSCRIPTION_QUICK_START.md) - Hızlı başlangıç kılavuzu

---

## 📊 PERFORMANCE CHARACTERISTICS

| Metric               | Target       | Measurement      |
| -------------------- | ------------ | ---------------- |
| **Endpoint Latency** | < 50ms       | In-process calls |
| **Throughput**       | > 1000 req/s | Single instance  |
| **Memory**           | < 2GB        | Typical load     |
| **Startup Time**     | < 30s        | Cold start       |
| **Transaction Time** | < 100ms      | Single domain    |

---

## 🔮 FUTURE EVOLUTION

### **Microservice Migration Path**

Eğer gelecekte bir modül mikroservise dönüştürülmek istenirse:

1. **Module Extraction:** Modülü ayrı bir repo'ya taşı
2. **API Gateway:** REST API ekle
3. **Event Bus:** Kafka ile event publishing
4. **Database Split:** Ayrı database instance
5. **Deployment:** Ayrı container/pod

### **Hybrid Architecture**

Bazı modüller monolith içinde, bazıları mikroservis olarak çalışabilir:

```
┌─────────────────────┐
│  Monolith           │
│  ├─ common          │
│  ├─ production      │
│  ├─ logistics       │
│  └─ finance         │
└─────────┬───────────┘
          │
          │ Events
          ▼
┌─────────────────────┐
│  Insight Service    │ (Microservice)
│  (Heavy analytics)  │
└─────────────────────┘
```

---

**Version:** 2.0  
**Last Updated:** 2025-10-25  
**Maintained By:** Fabric Management Team  
**Latest Addition:** ⭐ Composable Feature-Based Subscription Model

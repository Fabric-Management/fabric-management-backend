# 🏗️ MODULAR MONOLITH ARCHITECTURE

**Version:** 1.0  
**Last Updated:** 2025-01-27  
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

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

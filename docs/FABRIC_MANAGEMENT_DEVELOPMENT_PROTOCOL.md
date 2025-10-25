# 🧭 FABRIC MANAGEMENT PLATFORM – DEVELOPMENT PROTOCOL

**Version:** 4.0  
**Status:** ✅ Approved – Ready for Implementation  
**Scope:** Development standards, architectural principles, and operational patterns for the Fabric Management System  
**Last Updated:** 2025-10-25  
**Latest Addition:** ⭐ Composable Feature-Based Subscription Model

---

## 📋 QUICK NAVIGATION

| Section                                                                   | Description                       | Key Topics                                                    |
| ------------------------------------------------------------------------- | --------------------------------- | ------------------------------------------------------------- |
| [Primary Goals](#primary-goals)                                           | Platform objectives               | Performance, modularity, security                             |
| [Target Architecture](#target-architecture-overview)                      | System overview                   | Modular monolith structure                                    |
| [Architectural Principles](#architectural-principles)                     | Core principles                   | Domain boundaries, events, multi-tenancy, **⭐ subscription** |
| [Enterprise Flow Chain](#enterprise-flow-chain)                           | Request flow                      | Controller → Service → Domain → Event                         |
| [Directory Structure](#directory-structure-final)                         | Code organization                 | Module layout, **⭐ subscription/**                           |
| [Strategic Domains](#new-strategic-domains)                               | Governance & Operations           | Policy governance, job orchestration                          |
| [Security & Policy](#security--policy-protocol)                           | Security layers                   | JWT, RBAC, ABAC                                               |
| [**⭐ Subscription Model**](#composable-feature-based-subscription-model) | **⭐ Feature-based subscription** | **OS catalog, tiers, quotas**                                 |
| [Quality Checklist](#quality-checklist)                                   | Quality standards                 | Test coverage, security, performance                          |
| [Summary](#summary)                                                       | Platform summary                  | Key achievements                                              |
| [Next Steps](#next-steps)                                                 | Implementation roadmap            | Migration steps                                               |

---

## 🎯 PRIMARY GOALS

| Amaç                                       | Açıklama                                                                                 |
| ------------------------------------------ | ---------------------------------------------------------------------------------------- |
| ⚙️ **Minimum Maliyet & Düşük Karmaşıklık** | Gereksiz mikroservis ayrımlarını önle, tek codebase içinde domain sınırlarını koru.      |
| ⚡ **Yüksek Performans (in-process)**      | Domainler arası çağrılar in-process, düşük latency, tek transaction boundary.            |
| 🧱 **Net Domain Sınırları**                | Her domain bağımsız geliştirilebilir, test edilebilir, ileride mikroservise dönüşebilir. |
| 🔒 **Güvenli & İzlenebilir**               | Core modül tarafından kimlik, politika, audit ve tenant yönetimi sağlanır.               |
| 🧩 **Modüler Evrim (Modular Monolith)**    | Başlangıçta tek deploy, gelecekte yatay bölünebilir yapı.                                |
| 🔁 **Kısmi Çöküşte Dayanıklılık**          | Event-driven yapılar ve cache'ler sayesinde sistemin tamamı çökmez.                      |

---

## 🏗️ TARGET ARCHITECTURE OVERVIEW

```
┌──────────────────────────────────────────────────────────────────────┐
│                   FABRIC MANAGEMENT PLATFORM                         │
│                    (Modular Monolith Core)                          │
├──────────────────────────────────────────────────────────────────────┤
│  common/           → Platform + Infrastructure + Utilities           │
│                      (auth, user, company, policy, audit, events)    │
├──────────────────────────────────────────────────────────────────────┤
│  governance/       → ⭐ Access & Policy Governance + Compliance      │
│                      (policy engine, access review, anomaly)         │
├──────────────────────────────────────────────────────────────────────┤
│  operations/       → ⭐ CENTRAL ORCHESTRATOR (Job, Workflow, SLA)   │
│                      (job management, assignment, tracking)          │
├──────────────────────────────────────────────────────────────────────┤
│  BUSINESS DOMAINS:                                                   │
│  ├─ production/    → MasterData, Planning, Execution, Quality        │
│  ├─ logistics/     → Inventory, Shipment, Customs                    │
│  ├─ finance/       → Accounting, Costing, Billing                    │
│  ├─ human/         → Employee, Payroll, Performance, Leave           │
│  ├─ procurement/   → Supplier, Purchase, GRN, RFQ                    │
│  ├─ integration/   → Adapters, Webhooks, Schedulers, Outbox          │
│  └─ insight/       → Analytics, Intelligence (AI, Forecasts)         │
└──────────────────────────────────────────────────────────────────────┘
```

**Tek deploy (monolith), ama her domain modül olarak izole.**  
**Gerektiğinde bağımsız servise dönüştürülebilir.**

---

## ⚙️ ARCHITECTURAL PRINCIPLES

### 1. Clean Domain Boundaries

- Her domain, kendi `ApplicationModule` tanımıyla gelir
- Bağımlılıklar compile time'da doğrulanır (Spring Modulith / ArchUnit)
- Cross-domain çağrılar sadece `api/Facade` üzerinden yapılabilir

### 2. In-Process Communication

- Domainler arası çağrılar için Feign, REST yok
- Doğrudan Java method çağrısı (Facade arayüzü)
- Bu sayede latency ≈ 0, network overhead yok

### 3. Event-Driven Interaction (Async!)

- Her domain `DomainEventPublisher` ile olay fırlatır
- **Spring Modulith Events** kullanarak reliable async event processing
- Event'ler `event_publication` tablosuna yazılır (transaction-safe!)
- @EventListener methodları **@Async** ile çalışabilir (non-blocking)
- Kafka **optional** - development'ta Spring Events yeterli, production'da Kafka eklenebilir

**Async Architecture:**

```java
// Publisher (main thread)
eventPublisher.publish(new UserCreatedEvent(...));
// ↓ Returns immediately!

// Listener (separate thread!)
@EventListener
@Async  // ← Async execution!
@Transactional
public void onUserCreated(UserCreatedEvent event) {
    // Runs in background thread
    // Doesn't block main operation!
}
```

**Benefits:**

- ✅ Non-blocking operations
- ✅ Transaction-safe (Spring Modulith)
- ✅ Reliable delivery (event_publication table)
- ✅ No Kafka needed for development
- ✅ Can add Kafka later without code changes

### 4. Multi-Tenant Aware

- Her domain tablosunda `tenant_id`
- RLS (Row-Level Security) PostgreSQL'de aktif
- `TenantFilter` (common/security) request'ten tenant'ı alır ve DB context'e aktarır

### 5. Centralized Policy & Subscription Control

**4-Layer Access Control Architecture:**

```
┌────────────────────────────────────────────┐
│  Layer 1: OS Subscription                  │  ← ⭐ NEW
│  ─────────────────────────────             │
│  Tenant YarnOS'a abone mi?                 │
├────────────────────────────────────────────┤
│  Layer 2: Feature Entitlement              │  ← ⭐ NEW
│  ─────────────────────────────             │
│  YarnOS "yarn.blend" feature'ı var mı?     │
├────────────────────────────────────────────┤
│  Layer 3: Usage Quota                      │  ← ⭐ NEW
│  ─────────────────────────────             │
│  Fiber entity limiti aşıldı mı?            │
├────────────────────────────────────────────┤
│  Layer 4: Policy Engine (RBAC/ABAC)        │
│  ─────────────────────────────             │
│  User role & permission check              │
└────────────────────────────────────────────┘
```

- **Subscription Layer** (`common/platform/subscription`) - OS-based subscription validation
- **Feature Entitlement** - Granular feature-level access control
- **Usage Quotas** - API, storage, entity limit enforcement
- **Policy Engine** (`common/platform/policy`) - Role & permission based access
- Tüm domain endpoint'leri `@PolicyCheck` ve subscription check ile korunur

**Detaylı bilgi:** [SUBSCRIPTION_MODEL.md](./modular_monolith/SUBSCRIPTION_MODEL.md)

### 6. Self-Healing & Degraded Mode

- Redis geçici olarak erişilemezse sistem çalışmaya devam eder (cache bypass)
- Spring Modulith event_publication table güvenilir event delivery sağlar
- @Async operations retry mechanism ile desteklenir
- Kafka **optional** - yoksa in-process events kullanılır
- Sistem critical dependencies olmadan çalışabilir (graceful degradation)

---

## 🎯 ENTERPRISE FLOW CHAIN

### **Request → Response Akışı**

```
HTTP Request → Controller → DTO → Service → Domain → Repository → Event → Audit
```

### **Katman Sorumlulukları**

| Katman             | Sorumluluk                 | Örnek                              |
| ------------------ | -------------------------- | ---------------------------------- |
| **API**            | REST Endpoint              | `MaterialController`               |
| **Application**    | İş mantığı + event publish | `MaterialService`                  |
| **Domain**         | Entity, ValueObject, Event | `Material`, `MaterialCreatedEvent` |
| **Infrastructure** | Repository implementasyonu | `MaterialRepository`               |
| **Common**         | Cross-cutting concerns     | `AuditService`, `PolicyEngine`     |

### **Flow Implementation Example**

#### **1. Controller Layer (API)**

```java
@RestController
@RequestMapping("/api/production/masterdata/material")
@RequiredArgsConstructor
@Slf4j
public class MaterialController {

    private final MaterialService materialService;
    private final EnhancedSubscriptionService subscriptionService;  // ⭐ NEW

    @PolicyCheck(resource="fabric.material.create", action="POST")
    @AuditLog(action="MATERIAL_CREATE", resource="material")
    @PostMapping
    public ResponseEntity<ApiResponse<MaterialDto>> createMaterial(@Valid @RequestBody CreateMaterialRequest request) {
        log.info("Creating material: {}", request.getName());

        // ⭐ SUBSCRIPTION CHECK (4-Layer Control)
        UUID tenantId = TenantContext.getCurrentTenantId();
        subscriptionService.enforceEntitlement(
            tenantId,
            "production.material.create",  // Feature ID
            "material_entities"             // Quota type (optional)
        );

        MaterialDto created = materialService.createMaterial(request);

        return ResponseEntity.ok(ApiResponse.success(created, "Material created successfully"));
    }
}
```

#### **2. Service Layer (Application)**

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditService auditService;

    public MaterialDto createMaterial(CreateMaterialRequest request) {
        // 1. Domain Logic
        Material material = Material.create(
            request.getName(),
            request.getDescription(),
            request.getType(),
            request.getCategory(),
            request.getSupplier(),
            request.getUnitCost(),
            request.getUnit()
        );

        // 2. Repository Save
        Material saved = materialRepository.save(material);

        // 3. Event Publishing
        eventPublisher.publishEvent(new MaterialCreatedEvent(
            TenantContext.getCurrentTenantId(),
            saved.getId(),
            saved.getName(),
            saved.getType().toString()
        ));

        // 4. Audit Logging
        auditService.logAction("MATERIAL_CREATE", "material", saved.getId().toString(),
            "Material created: " + saved.getName());

        return MaterialDto.from(saved);
    }
}
```

#### **3. Domain Layer**

```java
@Entity
@Table(name = "prod_material")
@Getter
@Setter
@Builder
public class Material extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialType type;

    // Business methods
    public static Material create(String name, String description, MaterialType type,
                                 String category, String supplier, BigDecimal unitCost,
                                 String unit) {
        Material material = Material.builder()
            .name(name)
            .description(description)
            .type(type)
            .category(category)
            .supplier(supplier)
            .unitCost(unitCost)
            .unit(unit)
            .isActive(true)
            .isAvailable(true)
            .build();

        material.addDomainEvent(new MaterialCreatedEvent(material.getId(), material.getName(), material.getType()));
        return material;
    }
}
```

#### **4. Infrastructure Layer**

```java
@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    List<Material> findByTenantIdAndIsActiveTrue(UUID tenantId);

    Optional<Material> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT m FROM Material m WHERE m.tenantId = :tenantId AND m.type = :type")
    List<Material> findByTenantIdAndType(@Param("tenantId") UUID tenantId, @Param("type") MaterialType type);
}
```

#### **5. Event Layer**

```java
@Getter
public class MaterialCreatedEvent extends DomainEvent {

    private final UUID materialId;
    private final String materialName;
    private final String materialType;

    public MaterialCreatedEvent(UUID tenantId, UUID materialId, String materialName, String materialType) {
        super(tenantId, "MATERIAL_CREATED");
        this.materialId = materialId;
        this.materialName = materialName;
        this.materialType = materialType;
    }
}
```

---

## 🧱 DIRECTORY STRUCTURE (FINAL)

### Proje Kök Yapısı (Modular Monolith - Tek Uygulama)

```
fabric-management-backend/  (ROOT - Tek Modular Monolith)
├─ pom.xml                  # Ana uygulama POM (parent değil, tek uygulama)
├─ docker-compose.yml       # PostgreSQL, Redis, Kafka, Monitoring
├─ Dockerfile.service       # Production deployment
├─ .env, .env.example       # Environment configuration
├─ src/main/java/com/fabricmanagement/
│  ├─ FabricManagementApplication.java  # Main Spring Boot class
│  │
│  ├─ common/                 # 🧱 CROSS-CUTTING & CORE INFRASTRUCTURE
│  │  ├─ platform/            # Platform Services (application-level cores)
│  │  │  ├─ auth/             # Authentication & Authorization
│  │  │  ├─ user/             # User Management (identity, profile, roles)
│  │  │  ├─ company/          # Company/Tenant Management (hierarchy, departments)
│  │  │  ├─ policy/           # Policy Engine (runtime access evaluation)
│  │  │  ├─ audit/            # Audit Logging & Compliance
│  │  │  ├─ config/           # Centralized Configuration
│  │  │  ├─ monitoring/       # Metrics, Health, Tracing, Observability
│  │  │  ├─ communication/    # Notifications (Email, SMS, WhatsApp, in-app)
│  │  │  └─ subscription/     # ⭐ Composable Feature-Based Subscription
│  │  │     ├─ domain/        # Subscription, SubscriptionQuota, FeatureCatalog
│  │  │     ├─ app/           # SubscriptionService, QuotaService
│  │  │     ├─ infra/         # Repositories (Subscription, Quota, Feature)
│  │  │     └─ api/           # Subscription management endpoints
│  │  │
│  │  ├─ infrastructure/      # Shared Infrastructure Layer
│  │  │  ├─ persistence/      # BaseEntity, AuditableEntity, Repository base
│  │  │  ├─ events/           # DomainEvent, OutboxEvent, Event Bus
│  │  │  ├─ mapping/          # DTO-Entity Mappers (MapStruct)
│  │  │  ├─ cqrs/             # CommandBus, QueryBus, Handler interfaces
│  │  │  ├─ web/              # ApiResponse, PagedResponse, GlobalExceptionHandler
│  │  │  ├─ security/         # JWT, TenantFilter, Security Context
│  │  │  └─ cache/            # Redis cache utilities, invalidation manager
│  │  │
│  │  └─ util/                # Common Utilities
│  │     ├─ Money.java        # Currency-aware value object
│  │     ├─ Unit.java         # Measurement unit value object
│  │     └─ TimeHelper.java   # Time & date utilities
│  │
│  ├─ governance/             # 🛡️ ACCESS & POLICY GOVERNANCE LAYER
│  │  ├─ access/              # Access Governance Protocol (AGP)
│  │  │  ├─ policy/           # Policy Registry, Evaluation Engine
│  │  │  │  ├─ registry/      # Policy definitions & storage
│  │  │  │  ├─ engine/        # Policy evaluation logic
│  │  │  │  ├─ cache/         # Policy decision caching
│  │  │  │  └─ api/           # Policy management REST API
│  │  │  ├─ review/           # Dual approval & version control for policies
│  │  │  ├─ audit/            # Policy decision audit trail
│  │  │  └─ sync/             # Cache invalidation & policy propagation
│  │  │
│  │  └─ compliance/          # Compliance & Risk Monitoring
│  │     ├─ review/           # Periodic access reviews
│  │     ├─ anomaly/          # Suspicious access pattern detection
│  │     └─ report/           # Audit & compliance reports
│  │
│  ├─ operations/             # 🎯 OPERATIONS ORCHESTRATION DOMAIN
│  │  ├─ job/                 # Job & Work Order Management
│  │  │  ├─ domain/           # Job, WorkOrder, WorkOrderStage entities
│  │  │  ├─ app/              # JobService, WorkOrderService
│  │  │  │  ├─ command/       # CreateJobCommand, AdvanceStageCommand
│  │  │  │  └─ query/         # GetJobQuery, ListActiveJobsQuery
│  │  │  ├─ infra/repository/ # JobRepository, WorkOrderRepository
│  │  │  └─ api/              # REST & Facade interfaces
│  │  │
│  │  ├─ assignment/          # Personnel & Team Assignment
│  │  │  ├─ domain/           # Assignment, SkillTag, RoleRequirement
│  │  │  ├─ app/              # Assignment matching, capacity check
│  │  │  └─ policy/           # Assignment rules & governance integration
│  │  │
│  │  ├─ workflow/            # Workflow Engine & Templates
│  │  │  ├─ template/         # WorkflowTemplate definitions
│  │  │  ├─ engine/           # Stage transitions, rules, automation
│  │  │  └─ sla/              # SLA monitoring, performance metrics
│  │  │
│  │  └─ tracking/            # Traceability & Event Tracking
│  │     ├─ timeline/         # Activity timeline for visualization
│  │     └─ event/            # JobCreatedEvent, StageAdvancedEvent, etc.
│  │
│  ├─ production/             # 🏭 PRODUCTION DOMAIN
│  │  ├─ masterdata/          # Material, Recipe master data
│  │  │  ├─ material/         # Fiber, Yarn, Fabric materials
│  │  │  └─ recipe/           # Weave, Dye, Finish recipes
│  │  ├─ planning/            # Production planning
│  │  │  ├─ capacity/         # Capacity management
│  │  │  ├─ scheduling/       # Production scheduling
│  │  │  └─ workcenter/       # Work center management
│  │  ├─ execution/           # Production execution
│  │  │  ├─ fiber/            # Fiber processing
│  │  │  ├─ yarn/             # Yarn spinning
│  │  │  ├─ loom/             # Weaving (loom operations)
│  │  │  ├─ knit/             # Knitting
│  │  │  └─ dye/              # Dyeing & finishing
│  │  └─ quality/             # Quality control
│  │     ├─ inspection/       # Quality inspections
│  │     └─ result/           # Test results
│  │
│  ├─ logistics/              # 📦 LOGISTICS DOMAIN
│  │  ├─ inventory/           # Inventory management
│  │  │  ├─ item/             # Inventory items
│  │  │  ├─ lot/              # Lot tracking
│  │  │  ├─ movement/         # Stock movements
│  │  │  └─ location/         # Warehouse locations
│  │  ├─ shipment/            # Shipment management
│  │  │  ├─ order/            # Shipment orders
│  │  │  ├─ carrier/          # Carrier management
│  │  │  └─ tracking/         # Shipment tracking
│  │  └─ customs/             # Customs declarations
│  │
│  ├─ finance/                # 💰 FINANCE DOMAIN
│  │  ├─ ar/                  # Accounts Receivable
│  │  ├─ ap/                  # Accounts Payable
│  │  ├─ cashbank/            # Cash & Bank Management
│  │  ├─ invoice/             # Invoicing
│  │  └─ costing/             # Cost Accounting
│  │
│  ├─ human/                  # 👥 HUMAN RESOURCES DOMAIN
│  │  ├─ employee/            # Employee Management
│  │  ├─ org/                 # Organization Structure
│  │  ├─ leave/               # Leave Management
│  │  ├─ payroll/             # Payroll Processing
│  │  └─ performance/         # Performance Management
│  │
│  ├─ procurement/            # 🛒 PROCUREMENT DOMAIN
│  │  ├─ supplier/            # Supplier Management
│  │  ├─ requisition/         # Purchase Requisition
│  │  ├─ rfq/                 # Request for Quotation
│  │  ├─ po/                  # Purchase Order
│  │  └─ grn/                 # Goods Receipt Note
│  │
│  ├─ integration/            # 🔗 INTEGRATION SERVICES
│  │  ├─ adapters/            # ERP, E-Invoice, Carriers
│  │  ├─ webhooks/            # Webhook Management
│  │  ├─ transforms/          # Data Transformation
│  │  └─ notifications/       # Notification Providers
│  │
│  └─ insight/                # 🧠 INSIGHT & ANALYTICS
│     ├─ analytics/           # Read Models, Dashboards
│     └─ intelligence/        # AI, Forecasts, Optimization
│
├─ src/main/resources/
│  ├─ application.yml         # Spring Boot configuration
│  ├─ application-local.yml   # Local environment
│  ├─ application-prod.yml    # Production environment
│  ├─ db/migration/           # Flyway: V1__*.sql (domain-schema'lar)
│  └─ policies/               # Static policy templates (bootstrap)
│
├─ src/test/java/com/fabricmanagement/
│  └─ ...                     # Test structure mirrors main
│
├─ monitoring/                # Prometheus, Grafana, AlertManager configs
├─ scripts/                   # Deployment & utility scripts
└─ postman/                   # API collections
```

**✅ Tek JAR, tek deploy, ama her domain kendi paketi.**  
**✅ Domainler arası doğrudan çağrı yok; facade + domain event kullanılır.**  
**✅ Parent POM yok - direkt Spring Boot application olarak yapılandırılır.**

---

## 🎯 NEW STRATEGIC DOMAINS

### **Governance Domain** 🛡️

**Purpose:** Centralized access control, policy management, and compliance monitoring.

**Key Features:**

- **Access Governance Protocol (AGP):** Policy registry, evaluation engine, decision caching
- **Policy Review:** Dual approval & version control for critical policies
- **Policy Audit:** Complete decision trail for compliance
- **Policy Sync:** Real-time cache invalidation across instances
- **Compliance:** Access reviews, anomaly detection, audit reports

**Integration with common/platform/policy:**

- common/platform/policy: **Runtime** policy evaluation (Layer 1-5 checks)
- governance/access/policy: **Management** policy definitions, registry, audit

### **Operations Domain** 🎯

**Purpose:** Central orchestration layer for all operational activities.

**Key Features:**

- **Job Management:** High-level work definition (e.g., "Dyeing for Order #123")
- **WorkOrder:** Executable operational units with stage tracking
- **Assignment:** Personnel & team assignment based on skills & capacity
- **Workflow Templates:** Predefined stage sequences (spinning, weaving, dyeing)
- **SLA Monitoring:** Deadline tracking, escalation, notifications
- **Timeline Tracking:** Complete traceability of all actions

**Cross-Domain Orchestration:**

```
Operations coordinates:
├─ Production → Generates jobs for manufacturing processes
├─ Logistics → Creates jobs for shipment & warehousing
├─ Procurement → Triggers jobs for PO follow-up & inbound
├─ Human → Assigns personnel based on skills & workload
└─ Finance → Links cost data to jobs for performance analysis
```

**Workflow Example:**

```
Job: "Weave 1000m Denim Fabric for Order #456"
├─ Stage 1: Yarn Preparation (Production/Yarn) → Assigned to Team A
├─ Stage 2: Loom Setup (Production/Loom) → Assigned to Technician B
├─ Stage 3: Weaving (Production/Loom) → Assigned to Operator C
├─ Stage 4: Quality Check (Production/Quality) → Assigned to QC Team
└─ Stage 5: Warehouse Transfer (Logistics/Inventory) → Assigned to Logistics

Each stage:
✅ Has SLA deadline
✅ Sends notifications on delay
✅ Records timeline events
✅ Requires policy approval for transitions
```

---

### Common Module Yapısı (Platform + Infrastructure)

```
common/
├─ platform/                  # İşletim altyapısı
│  ├─ auth/                   # Authentication & Authorization
│  │  ├─ api/
│  │  │  ├─ controller/
│  │  │  │  └─ AuthController.java
│  │  │  └─ facade/
│  │  │      └─ AuthFacade.java
│  │  ├─ app/
│  │  │  └─ AuthService.java
│  │  ├─ domain/
│  │  │  ├─ AuthUser.java
│  │  │  ├─ RefreshToken.java
│  │  │  └─ event/
│  │  │      └─ UserLoginEvent.java
│  │  ├─ infra/
│  │  │  └─ repository/
│  │  │      └─ AuthUserRepository.java
│  │  └─ dto/
│  │      ├─ LoginRequest.java
│  │      └─ LoginResponse.java
│  ├─ user/                   # User Management
│  │  ├─ api/
│  │  │  ├─ controller/
│  │  │  │  └─ UserController.java
│  │  │  └─ facade/
│  │  │      └─ UserFacade.java
│  │  ├─ app/
│  │  │  └─ UserService.java
│  │  ├─ domain/
│  │  │  ├─ User.java
│  │  │  └─ event/
│  │  │      └─ UserCreatedEvent.java
│  │  ├─ infra/
│  │  │  └─ repository/
│  │  │      └─ UserRepository.java
│  │  └─ dto/
│  │      ├─ UserDto.java
│  │      └─ CreateUserRequest.java
│  ├─ company/                # Company/Tenant Management
│  │  ├─ api/
│  │  │  ├─ controller/
│  │  │  │  └─ CompanyController.java
│  │  │  └─ facade/
│  │  │      └─ CompanyFacade.java
│  │  ├─ app/
│  │  │  └─ CompanyService.java
│  │  ├─ domain/
│  │  │  ├─ Company.java
│  │  │  ├─ Department.java
│  │  │  └─ event/
│  │  │      └─ CompanyCreatedEvent.java
│  │  ├─ infra/
│  │  │  └─ repository/
│  │  │      └─ CompanyRepository.java
│  │  └─ dto/
│  │      ├─ CompanyDto.java
│  │      └─ CreateCompanyRequest.java
│  ├─ policy/                 # Policy Engine
│  │  ├─ api/
│  │  │  ├─ controller/
│  │  │  │  └─ PolicyController.java
│  │  │  └─ facade/
│  │  │      └─ PolicyFacade.java
│  │  ├─ app/
│  │  │  └─ PolicyService.java
│  │  ├─ domain/
│  │  │  ├─ Policy.java
│  │  │  └─ event/
│  │  │      └─ PolicyUpdatedEvent.java
│  │  ├─ infra/
│  │  │  └─ repository/
│  │  │      └─ PolicyRepository.java
│  │  └─ dto/
│  │      ├─ PolicyDto.java
│  │      └─ CreatePolicyRequest.java
│  ├─ audit/                  # Audit Logging
│  │  ├─ api/
│  │  │  ├─ controller/
│  │  │  │  └─ AuditController.java
│  │  │  └─ facade/
│  │  │      └─ AuditFacade.java
│  │  ├─ app/
│  │  │  └─ AuditService.java
│  │  ├─ domain/
│  │  │  ├─ AuditLog.java
│  │  │  └─ event/
│  │  │      └─ AuditEvent.java
│  │  ├─ infra/
│  │  │  └─ repository/
│  │  │      └─ AuditLogRepository.java
│  │  └─ dto/
│  │      ├─ AuditLogDto.java
│  │      └─ CreateAuditLogRequest.java
│  ├─ config/                 # Configuration
│  │  ├─ DatabaseConfig.java
│  │  ├─ CacheConfig.java
│  │  └─ MonitoringConfig.java
│  ├─ monitoring/             # Health & Metrics
│  │  ├─ api/
│  │  │  ├─ controller/
│  │  │  │  └─ HealthController.java
│  │  │  └─ facade/
│  │  │      └─ HealthFacade.java
│  │  ├─ app/
│  │  │  └─ HealthService.java
│  │  ├─ domain/
│  │  │  ├─ HealthStatus.java
│  │  │  └─ event/
│  │  │      └─ HealthCheckEvent.java
│  │  ├─ infra/
│  │  │  └─ repository/
│  │  │      └─ HealthRepository.java
│  │  └─ dto/
│  │      ├─ HealthStatusDto.java
│  │      └─ HealthCheckRequest.java
│  └─ communication/          # Notifications
│     ├─ api/
│     │  ├─ controller/
│     │  │  └─ NotificationController.java
│     │  └─ facade/
│     │      └─ NotificationFacade.java
│     ├─ app/
│     │  └─ NotificationService.java
│     ├─ domain/
│     │  ├─ Notification.java
│     │  └─ event/
│     │      └─ NotificationSentEvent.java
│     ├─ infra/
│     │  └─ repository/
│     │      └─ NotificationRepository.java
│     └─ dto/
│         ├─ NotificationDto.java
│         └─ SendNotificationRequest.java
├─ infrastructure/            # Teknik altyapı
│  ├─ persistence/
│  │  ├─ BaseEntity.java
│  │  ├─ AuditableEntity.java
│  │  └─ SpecificationUtils.java
│  ├─ events/
│  │  ├─ DomainEvent.java
│  │  ├─ DomainEventPublisher.java
│  │  └─ OutboxEvent.java
│  ├─ mapping/
│  │  └─ MapStructConfig.java
│  ├─ cqrs/
│  │  ├─ Command.java
│  │  ├─ Query.java
│  │  ├─ CommandHandler.java
│  │  └─ QueryHandler.java
│  ├─ web/
│  │  ├─ GlobalExceptionHandler.java
│  │  ├─ ProblemDetails.java
│  │  ├─ ResponseWrapper.java
│  │  ├─ ApiResponse.java
│  │  └─ PagedResponse.java
│  └─ security/
│     ├─ TenantFilter.java
│     ├─ JwtUtils.java
│     ├─ PolicyCheck.java
│     └─ SecurityConfig.java
└─ util/                      # Yardımcı sınıflar
   ├─ Money.java
   ├─ Unit.java
   └─ TimeHelper.java
```

### Modül İç Yapısı (Vertical Slice + DDD)

Her domain paketi kendi "feature modüllerini" barındırır ve içinde klasik controller/service/repository katmanları vardır.

**Örnek: production/ (tamamı tek modül değil; alt modüller)**

```
production/
├─ ProductionModule.java              # @ApplicationModule tanımı
├─ masterdata/
│  ├─ material/                       # fiber, yarn, fabric türleri
│  │  ├─ api/
│  │  │  ├─ controller/               # External REST API
│  │  │  │  └─ MaterialController.java
│  │  │  └─ facade/                   # Internal API
│  │  │      └─ MaterialFacade.java
│  │  ├─ app/
│  │  │  ├─ command/
│  │  │  │  └─ CreateMaterialCommand.java
│  │  │  ├─ query/
│  │  │  │  └─ GetMaterialQuery.java
│  │  │  └─ MaterialService.java
│  │  ├─ domain/
│  │  │  ├─ Material.java
│  │  │  ├─ MaterialType.java
│  │  │  └─ event/
│  │  │      └─ MaterialCreatedEvent.java
│  │  ├─ infra/
│  │  │  ├─ repository/
│  │  │  │  └─ MaterialRepository.java
│  │  │  └─ client/
│  │  │      ├─ interface/
│  │  │      │  └─ LogisticsClient.java
│  │  │      └─ impl/
│  │  │          └─ LogisticsRestClient.java
│  │  └─ dto/
│  │      ├─ MaterialDto.java
│  │      ├─ CreateMaterialRequest.java
│  │      └─ UpdateMaterialRequest.java
│  └─ recipe/                         # weave, dye, finish reçeteleri
│     ├─ api/
│     │  ├─ controller/
│     │  │  └─ RecipeController.java
│     │  └─ facade/
│     │      └─ RecipeFacade.java
│     ├─ app/
│     │  └─ RecipeService.java
│     ├─ domain/
│     │  ├─ Recipe.java
│     │  └─ event/
│     │      └─ RecipeCreatedEvent.java
│     ├─ infra/
│     │  └─ repository/
│     │      └─ RecipeRepository.java
│     └─ dto/
│         └─ RecipeDto.java
├─ planning/
│  ├─ capacity/ ...
│  ├─ scheduling/ ...
│  └─ workcenter/ ...
├─ execution/
│  ├─ fiber/ ...
│  ├─ yarn/ ...
│  ├─ loom/ ...
│  ├─ knit/ ...
│  └─ dye/ ...
└─ quality/
   ├─ inspections/ ...
   └─ results/ ...
```

**Kural:** Her feature alt klasörü şu mini katmanlara sahip: api/ (public facade), web/, app/, domain/, infra/, dto/.  
Böylece hem domain-first görünürlük var hem de Spring alışkanlıkları korunuyor.

### Common Module Structure

```
common/
├─ platform/                  # İşletim altyapısı
│  ├─ auth/                   # Authentication & Authorization
│  ├─ user/                   # User Management
│  ├─ policy/                 # Policy Engine
│  ├─ audit/                  # Audit Logging
│  ├─ config/                 # Configuration
│  ├─ monitoring/             # Health & Metrics
│  └─ communication/          # Notifications
├─ infrastructure/            # Teknik altyapı
│  ├─ persistence/
│  │  ├─ BaseEntity.java
│  │  ├─ AuditableEntity.java
│  │  └─ SpecificationUtils.java
│  ├─ events/
│  │  ├─ DomainEvent.java
│  │  ├─ DomainEventPublisher.java
│  │  └─ OutboxEvent.java
│  ├─ mapping/
│  │  └─ MapStructConfig.java
│  ├─ cqrs/
│  │  ├─ Command.java
│  │  ├─ Query.java
│  │  ├─ CommandHandler.java
│  │  └─ QueryHandler.java
│  ├─ web/
│  │  ├─ GlobalExceptionHandler.java
│  │  ├─ ProblemDetails.java
│  │  ├─ ResponseWrapper.java
│  │  ├─ ApiResponse.java
│  │  └─ PagedResponse.java
│  └─ security/
│     ├─ TenantFilter.java
│     ├─ JwtUtils.java
│     ├─ PolicyCheck.java
│     └─ SecurityConfig.java
└─ util/                      # Yardımcı sınıflar
   ├─ Money.java
   ├─ Unit.java
   └─ TimeHelper.java
```

---

## 🔒 SECURITY & POLICY PROTOCOL

### 🔑 Authentication

- JWT / OAuth2 based (`common/auth`)
- Tenant + Role claims JWT içinde taşınır
- Internal modules communicate via in-process call — token verification unnecessary

### 🧩 Authorization

- `@PolicyCheck` annotation → `PolicyEvaluationEngine` çağrısı
- Policy'ler: JSON veya DB tabanlı (`policy_registry` tablosu)
- Decision cache: Redis → 5m TTL
- Default policy: deny-all (whitelist mantığı)

### 📜 Example Annotation

```java
@PolicyCheck(resource="fabric.yarn.create", action="POST")
@PostMapping("/api/production/yarn")
public ResponseEntity<?> createYarn(@RequestBody YarnDto dto) {
    // Implementation
}
```

---

## 🗃️ DATA MANAGEMENT

### Veri Katmanı (PostgreSQL)

- **PostgreSQL** + Flyway migrations (per-domain SQL dosyaları)
- **Schema prefix** per domain: `core_*`, `prod_*`, `logi_*`, `fin_*`, `hr_*`, `proc_*`, `integ_*`, `ins_*`
- **EntityBase**: `id`, `createdAt`, `updatedAt`, `tenantId`, `version`

**Multi-tenant:** her tabloda tenant_id, RLS aktif (DB güvenliği)

**Flyway:** domain klasörlerine ayrılmış migration'lar (okunabilirlik için isim ön eki)

```
resources/db/migration/
├─ V1__core_init.sql
├─ V2__prod_masterdata.sql
├─ V3__prod_execution.sql
├─ V4__logi_inventory.sql
...
```

---

## 🧠 DEVELOPMENT GUIDELINES

### common/ (Platform + Infrastructure)

**Platform:** İşletim altyapısı (auth, user, policy, audit, config, monitoring, communication)
**Infrastructure:** Teknik altyapı (persistence, events, mapping, cqrs, web, security, util)

```
common/
├─ platform/         # İşletim altyapısı
│  ├─ auth/          # Authentication & Authorization
│  ├─ user/          # User Management
│  ├─ company/       # Company/Tenant Management
│  ├─ policy/        # Policy Engine
│  ├─ audit/         # Audit Logging
│  ├─ config/        # Configuration
│  ├─ monitoring/    # Health & Metrics
│  └─ communication/ # Notifications
├─ infrastructure/   # Teknik altyapı
│  ├─ persistence/   # BaseEntity, auditing, jpa converters
│  ├─ events/        # base event, outbox modeli, event publisher
│  ├─ mapping/       # MapStruct config
│  ├─ cqrs/          # Command, Query, Handler interfaces
│  ├─ web/           # global exception, problem+json, pagination utils
│  └─ security/      # TenantFilter, JwtUtils
└─ util/             # money, unit, time helpers
```

**Kural:** Domain'ler common'a bağımlı olabilir; common hiçbir domain'e bağımlı olamaz.

### Outbox & Event Akışı (Basit ve sağlam)

Her modül yan etki doğuracak değişiklikte Outbox tablosuna yazar (aynı transaction).

integration modülündeki OutboxPublisher job'ı bu kayıtları Kafka'ya iter (veya direkt internal event dinleyicilerine dağıtır).

Broker down ise birikiyor; sistem çalışmaya devam eder.

### Test Stratejisi

- **Module slice tests:** Her feature klasöründe web + app + infra için Spring slice testleri.
- **Contract tests (Facade):** api/ arayüzleri için consumer-driven kontratlar.
- **Integration tests:** Testcontainers (Postgres/Redis/Kafka).
- **ArchUnit / Modulith verifications:** Modül sınırı ihlallerinde test fail.

### API Yol Kuralları (tutarlı ve sade)

```
/api/core/users ...
/api/production/materials ...
/api/production/yarn/batches ...
/api/production/quality/inspections ...
/api/logistics/inventory/movements ...
/api/logistics/shipments ...
/api/finance/ar/invoices ...
/api/human/employees ...
/api/procurement/po ...
/api/insight/analytics/dashboards ...
```

**Kural:** /{domain}/{feature}/... formatı; domain sınırı URL'den de anlaşılır.

### Modül Kısa Özetleri (sınır ve içerik)

- **common/:**

  - platform: auth, user, company, policy, audit, config, monitoring, communication, **subscription** ⭐
    - **subscription:** OS catalog (FabricOS, YarnOS, LoomOS, etc.), feature entitlement, usage quotas
    - Supports: String-based pricing tiers, JSONB feature storage, composable OS model
    - See: [SUBSCRIPTION_MODEL.md](./modular_monolith/SUBSCRIPTION_MODEL.md)
  - infrastructure: persistence, events, mapping, cqrs, web, security, cache
  - util: Money, Unit, TimeHelper

- **governance/:**

  - access: policy registry, evaluation engine, review, audit, sync, dashboard
  - compliance: access review, anomaly detection, compliance reports

- **operations/:** ⭐ **MERKEZI ORCHESTRATOR**

  - job: Job, WorkOrder, WorkOrderStage management
  - assignment: Personnel & team assignment, capacity matching
  - workflow: Workflow templates, stage transitions, SLA monitoring
  - tracking: Timeline, traceability, event logging

- **production/:** masterdata(material, recipe), planning(capacity, scheduling, workcenter), execution(fiber, yarn, loom, knit, dye), quality(inspection, result)

- **logistics/:** inventory(item, lot, movement, location), shipment(order, carrier, tracking), customs

- **finance/:** ar, ap, cashbank, invoice, costing

- **human/:** employee, org, leave, payroll, performance

- **procurement/:** supplier, requisition, rfq, po, grn

- **integration/:** adapters (erp, e-invoice, carriers), webhooks, transforms, notifications

- **insight/:** analytics (read models, dashboards), intelligence (AI forecasts/optimization)

### Kod Örneği (Facade + Service imzası)

```java
// production/execution/yarn/api/YarnFacade.java
public interface YarnFacade {
    Optional<YarnSummary> findLot(UUID tenantId, UUID lotId);
    void produceYarn(UUID tenantId, ProduceYarnCommand cmd);
}

// production/execution/yarn/app/YarnService.java
@Service
@RequiredArgsConstructor
class YarnService implements YarnFacade {
    private final YarnRepository repo;
    private final DomainEventPublisher events;

    @Transactional
    public void produceYarn(UUID tenantId, ProduceYarnCommand cmd) {
        YarnLot lot = YarnLot.produce(tenantId, cmd);
        repo.save(lot);
        events.publish(new YarnProducedEvent(tenantId, lot.getId(), lot.getQuantity()));
    }
}
```

| Alan                       | Kural                                                  |
| -------------------------- | ------------------------------------------------------ |
| **DTO – Entity Mapping**   | Sadece MapStruct kullanılacak                          |
| **Transaction Boundaries** | Transaction'lar domain bazında; cross-domain işlem yok |
| **Caching**                | Redis; domain içinde `@Cacheable`                      |
| **Events**                 | DomainEvent + Outbox; Kafka opsiyonel                  |
| **Testing**                | Slice Tests, Modulith Tests, Testcontainers            |
| **API Naming**             | `/api/{domain}/{feature}/...` formatı                  |
| **Code Reviews**           | Modül sınırına aykırı dependency kabul edilmez         |

---

## 🧩 MODULE TO MODULE RELATIONS

### Bağımlılık Kuralları (Spring Modulith ile enforce)

Her domain kökünde bir modül tanımı yapıyoruz; yalnızca izin verilen bağımlılık geçerli.

```java
// common/CommonModule.java
@ApplicationModule(
    allowedDependencies = {} // common başka domain'e bağımlı değil
)
class CommonModule {}

// production/ProductionModule.java
@ApplicationModule(
    allowedDependencies = {"common"} // sadece common'dan platform/infrastructure okur
)
class ProductionModule {}

// logistics/LogisticsModule.java
@ApplicationModule(
    allowedDependencies = {"common", "production"} // üretim eventlerini tüketebilir
)
class LogisticsModule {}
```

**Derleme zamanı ihlalde fail! Spagetti bağımlılıklarını baştan engeller.**

| Module        | Allowed Dependencies                               |
| ------------- | -------------------------------------------------- |
| `common`      | none                                               |
| `governance`  | common                                             |
| `operations`  | common, governance, production, logistics, human   |
| `production`  | common, governance                                 |
| `logistics`   | common, governance, production                     |
| `finance`     | common, governance, logistics, production          |
| `human`       | common, governance                                 |
| `procurement` | common, governance, finance                        |
| `integration` | common, governance, production, logistics, finance |
| `insight`     | common, production, logistics, finance, operations |

**Kurallar ArchUnit veya Spring Modulith ile enforce edilecektir.**

### Modüller Arası İletişim Desenleri

**Öncelik:** Domain içinde in-process (service call).

**Domainler arası:**

1. **Event-first (ASYNC!):**

   - `ApplicationEventPublisher` ile event publish
   - **Spring Modulith Events** - event_publication tablosuna yazılır (transaction-safe!)
   - **@Async @EventListener** ile async processing (non-blocking!)
   - Kafka **optional** - external systems için (YAGNI principle!)

   ```java
   // Async event pattern (RECOMMENDED!)
   @Service
   public class MaterialService {
       public MaterialDto create(CreateMaterialRequest request) {
           Material saved = materialRepository.save(material);

           // ASYNC EVENT - immediate return!
           eventPublisher.publish(new MaterialCreatedEvent(...));

           return MaterialDto.from(saved); // Returns immediately!
       }
   }

   // Listener (separate module, async!)
   @EventListener
   @Async  // ← Runs in background thread!
   @Transactional
   public void onMaterialCreated(MaterialCreatedEvent event) {
       // Inventory operations...
       // Doesn't block MaterialService!
   }
   ```

2. **Read-only façade (SYNC):** Başka domain verisini okumak için api/Facade üzerinden in-process call.

   ```java
   // Synchronous facade call
   Optional<MaterialDto> material = materialFacade.findById(tenantId, materialId);
   // Fast in-process, no network overhead
   ```

**Event vs Facade:**

- **Facade:** Synchronous read (immediate response needed)
- **Events:** Asynchronous writes (fire-and-forget, eventual consistency)

**Facade örneği (read-only):**

```java
// production/masterdata/material/api/MaterialFacade.java
public interface MaterialFacade {
    Optional<MaterialSummary> findById(UUID tenantId, UUID materialId);
}
```

**Kullanan (logistics):**

```java
@RequiredArgsConstructor
public class ReceivingService {
   private final MaterialFacade materials; // sadece API katmanına bağlanır
   ...
}
```

---

## 🔍 OBSERVABILITY

- `common/monitoring` → Spring Actuator endpoints aktif: `/actuator/health`, `/actuator/metrics`, `/actuator/trace`
- **OpenTelemetry (OTel)** + Prometheus (Micrometer)
- Request tracing ID → `X-Trace-ID`
- Audit logs (`common/audit`) tüm kritik işlemleri kaydeder

---

## 🧩 DEPLOYMENT STRATEGY

- **Tek container** (`monolith.jar`) → Docker + K8s deployment
- **Sidecar services** (optional):
  - Kafka (events)
  - Redis (cache)
  - Prometheus / Grafana (monitoring)
  - PostgreSQL (main DB)
- **CI/CD**: GitHub Actions → Docker Build → K8s Deploy (Blue-Green)

---

## 🚀 SCALING STRATEGY

| Seviye                 | Yaklaşım                                              |
| ---------------------- | ----------------------------------------------------- |
| **Low Load**           | Tek monolith container (shared JVM)                   |
| **Medium Load**        | Multi-instance monolith + shared DB                   |
| **High Load (Future)** | Domain modülleri bağımsız mikroservislere ayrılabilir |
| **Read-heavy Modules** | Insight & Analytics → ayrı servis (CQRS read replica) |

---

## 💳 COMPOSABLE FEATURE-BASED SUBSCRIPTION MODEL

### **⭐ Overview**

FabricOS, **esnek, özellik tabanlı** abonelik modeli kullanır. Kullanıcılar sadece ihtiyaç duydukları OS'ları satın alır.

### **Architecture Layers**

```
Layer 1: OS Subscription     → Tenant YarnOS'a abone mi?
Layer 2: Feature Entitlement → YarnOS "yarn.blend" var mı?
Layer 3: Usage Quota         → Fiber entity limiti aşıldı mı?
Layer 4: Policy Engine       → User permission check
```

### **OS Catalog (10 OS)**

| OS                 | Tier'lar                         | Entry Price | Açıklama                         |
| ------------------ | -------------------------------- | ----------- | -------------------------------- |
| **FabricOS**       | Base (zorunlu)                   | $199/mo     | Tüm tenantlar için base platform |
| **YarnOS**         | Starter/Professional/Enterprise  | $99/mo      | İplik üretimi (fiber + yarn)     |
| **LoomOS**         | Starter/Professional/Enterprise  | $149/mo     | Dokuma üretimi                   |
| **KnitOS**         | Starter/Professional/Enterprise  | $129/mo     | Örme üretimi                     |
| **DyeOS**          | Starter/Professional/Enterprise  | $119/mo     | Boya & Apre                      |
| **AnalyticsOS**    | Standard/Advanced/Enterprise     | $149/mo     | BI & Raporlama                   |
| **IntelligenceOS** | Professional/Enterprise          | $299/mo     | AI & Tahminleme                  |
| **EdgeOS**         | Starter/Professional/Enterprise  | $199/mo     | IoT & Sensörler                  |
| **AccountOS**      | Standard/Professional/Enterprise | $79/mo      | Resmi Muhasebe                   |
| **CustomOS**       | Standard/Professional/Enterprise | $399/mo     | Dış Entegrasyonlar               |

### **Key Features**

✅ **String-Based Tiers** - Her OS'un kendi tier isimleri (enum yok!)  
✅ **JSONB Storage** - Esnek feature ve tier yapısı  
✅ **Composable** - Sadece ihtiyaç duyulan OS'lar alınır  
✅ **Granular Control** - Feature-level entitlement  
✅ **Usage Quotas** - API, storage, entity limitleri  
✅ **Database-Driven** - Feature catalog database'de saklanır

### **Implementation Example**

```java
@Service
public class EnhancedSubscriptionService {

    public void enforceEntitlement(UUID tenantId, String featureId, String quotaType) {
        // Layer 1: OS Subscription
        String osCode = extractOsCode(featureId);
        if (!hasActiveSubscription(tenantId, osCode)) {
            throw new SubscriptionRequiredException(osCode + " required");
        }

        // Layer 2: Feature Entitlement
        if (!hasFeature(tenantId, featureId)) {
            throw new FeatureNotAvailableException("Feature not in your tier");
        }

        // Layer 3: Usage Quota
        if (quotaType != null && !hasQuota(tenantId, quotaType)) {
            throw new QuotaExceededException("Quota exceeded");
        }
    }
}
```

### **Database Tables**

- `common_subscription` - OS subscriptions (pricing_tier: String)
- `common_subscription_quota` - Usage quotas (users, API calls, entities)
- `common_feature_catalog` - Feature entitlement rules (available_in_tiers: JSONB)
- `common_os_definition` - OS definitions (available_tiers: JSONB)

### **Documentation**

**Detaylı bilgi için:**

- [SUBSCRIPTION_INDEX.md](./modular_monolith/SUBSCRIPTION_INDEX.md) - Documentation index
- [SUBSCRIPTION_MODEL.md](./modular_monolith/SUBSCRIPTION_MODEL.md) - Kapsamlı dokümantasyon (1167 satır)
- [SUBSCRIPTION_QUICK_START.md](./modular_monolith/SUBSCRIPTION_QUICK_START.md) - Hızlı başlangıç
- [common/platform/company/SUBSCRIPTION.md](./modular_monolith/common/platform/company/SUBSCRIPTION.md) - Implementation guide

---

## ✅ QUALITY CHECKLIST

| Kategori                | Gereklilik                                 |
| ----------------------- | ------------------------------------------ |
| 🔍 **Test Coverage**    | %80 minimum (module-based)                 |
| 🧠 **Code Readability** | Domain-first structure, no "god services"  |
| 🧩 **Extensibility**    | Yeni domain eklenebilir, eskiye dokunmadan |
| 🔒 **Security**         | JWT + Policy + Subscription enforcement    |
| ⚡ **Performance**      | Endpoint latency < 50ms (in-process calls) |
| 🧱 **Fault Tolerance**  | Outbox + retry + cache                     |
| 🧾 **Auditability**     | Common/Audit log records for every action  |

---

## 🏁 SUMMARY

Fabric Management Platform artık:

- 🧩 **Modular Monolith** mimarisiyle yönetilebilir
- 🔄 **Event-driven** yapıda ölçeklenebilir
- 🔐 **4-Layer Security** ile korunur (Subscription → Feature → Quota → Policy)
- 💳 **Composable Subscription Model** ile esnek fiyatlandırma sunar
- ⚙️ **Low-latency** ve **low-cost** şekilde çalışabilir
- 💡 Geliştiriciler için sade, net, esnek bir yapı sunar

---

## ✳️ NEXT STEPS

1. **Common modülü** oluştur (platform + infrastructure)
2. **@ApplicationModule** yapılarını tanımla (Spring Modulith)
3. **SubscriptionService + PolicyEngine** aktif et (4-layer control)
4. **Subscription database migrations** oluştur
5. **Outbox + Redis + Flyway** altyapısını kur
6. **Feature catalog seeding** implement et
7. **İlk modül**: Yarn Production Flow (fiber → yarn → fabric)
8. **Integration testleri** ile subscription + policy + events doğrula

---

**Protocol Version:** 4.0  
**Last Updated:** 2025-10-25  
**Maintained By:** Fabric Management Team  
**Latest Addition:** ⭐ Composable Feature-Based Subscription Model

---

**📚 Key Documentation:**

- [SUBSCRIPTION_INDEX.md](./modular_monolith/SUBSCRIPTION_INDEX.md) - Subscription documentation index
- [ARCHITECTURE.md](./modular_monolith/ARCHITECTURE.md) - Modular monolith architecture
- [PROJECT_PROGRESS.md](./modular_monolith/PROJECT_PROGRESS.md) - Implementation progress

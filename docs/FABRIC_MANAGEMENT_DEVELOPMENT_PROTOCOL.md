# 🧭 FABRIC MANAGEMENT PLATFORM – DEVELOPMENT PROTOCOL

**Version:** 3.0  
**Status:** ✅ Approved – Ready for Implementation  
**Scope:** Development standards, architectural principles, and operational patterns for the Fabric Management System  
**Last Updated:** 2025-01-27

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
┌──────────────────────────────────────────────────────────┐
│                   FABRIC MANAGEMENT                       │
│                 (Modular Monolith Core)                  │
├──────────────────────────────────────────────────────────┤
│  common/           → Platform (auth, user, company, policy, audit, config, monitoring, communication) + Infrastructure (persistence, events, mapping, cqrs, web, security, util) │
│  production/       → MasterData, Planning, Execution, Quality                     │
│  logistics/        → Inventory, Shipment, Customs                                 │
│  finance/          → Accounting, Costing, Billing                                 │
│  human/            → Employee, Payroll, Performance, Leave                        │
│  procurement/      → Supplier, Purchase, GRN, RFQ                                 │
│  integration/      → Adapters, Webhooks, Schedulers, Outbox                       │
│  insight/          → Analytics, Intelligence (AI, Forecasts)                     │
└──────────────────────────────────────────────────────────┘
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

### 3. Event-Driven Interaction

- Her domain `DomainEventPublisher` ile olay fırlatır
- Olaylar `integration` modülündeki Outbox tablosuna yazılır
- Event'ler Kafka'ya taşınır ama sistem Kafka olmadan da çalışabilir

### 4. Multi-Tenant Aware

- Her domain tablosunda `tenant_id`
- RLS (Row-Level Security) PostgreSQL'de aktif
- `TenantFilter` (common/security) request'ten tenant'ı alır ve DB context'e aktarır

### 5. Centralized Policy Control

- Endpoint bazlı erişim denetimi `common/policy` modülünde yapılır
- Policy'ler subscription, department, role, conditions üzerinden değerlendirilir
- Tüm domain endpoint'leri `@PolicyCheck` anotasyonu ile korunur

### 6. Self-Healing & Degraded Mode

- Kafka veya Redis geçici olarak erişilemezse sistem çalışmaya devam eder
- Outbox tabloları, cache'ler ve async retry mekanizması devreye girer

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

    @PolicyCheck(resource="fabric.material.create", action="POST")
    @AuditLog(action="MATERIAL_CREATE", resource="material")
    @PostMapping
    public ResponseEntity<ApiResponse<MaterialDto>> createMaterial(@Valid @RequestBody CreateMaterialRequest request) {
        log.info("Creating material: {}", request.getName());

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

### Proje Kök Yapısı (Tek App)

```
fabric-management-backend/
├─ build.gradle / pom.xml
├─ src/main/java/com/fabricmanagement/
│  ├─ common/                 # TÜM CROSS-CUTTING CONCERNS
│  │  ├─ platform/           # İşletim altyapısı (auth, user, policy, audit, config, monitoring, communication)
│  │  ├─ infrastructure/     # Teknik altyapı (persistence, events, mapping, cqrs, web, security, util)
│  │  └─ util/               # Yardımcı sınıflar
│  ├─ production/             # Business Domain: masterdata, planning, execution, quality
│  ├─ logistics/              # Business Domain: inventory, shipment, customs
│  ├─ finance/                # Business Domain: ar, ap, invoice, costing
│  ├─ human/                  # Business Domain: employee, org, leave, payroll, performance
│  ├─ procurement/            # Business Domain: supplier, requisition, rfq, po, grn
│  ├─ integration/            # Business Domain: adapters, webhooks, transforms, notifications
│  └─ insight/                # Business Domain: analytics(read models), intelligence(AI/forecasts)
└─ src/main/resources/
   ├─ application.yml
   └─ db/migration/           # Flyway: V1__*.sql (domain-schema'lar)
```

**Tek jar, tek deploy, ama her domain kendi paketi.**  
**Domainler arası doğrudan çağrı yok; olması gerekenler için açık arayüz (facade) + domain event.**

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

- **common/:** platform (auth, user, company, policy, audit, config, monitoring, communication) + infrastructure (persistence, events, mapping, cqrs, web, security, util)
- **production/:** masterdata(material, recipe), planning(capacity, scheduling, workcenter), execution(fiber, yarn, loom, knit, dye), quality(inspections, results)
- **logistics/:** inventory(item, lot, movement, location), shipment(order, carrier, tracking), customs (opsiyonel alt modül)
- **finance/:** ar, ap, cashbank, invoice, costing (+ gerekirse accounting)
- **human/:** employee, org, leave, payroll, performance
- **procurement/:** supplier, requisition, rfq, po, grn
- **integration/:** adapters (erp, e-invoice, carriers), webhooks, transforms, notifications (provider), scheduler
- **insight/:** analytics (read models, dashboards), intelligence (forecasts/optimization). Kaynak domain verisini değiştirmez.

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

**Kurallar ArchUnit veya Spring Modulith ile enforce edilecektir.**

### Modüller Arası İletişim Desenleri

**Öncelik:** Domain içinde in-process (service call).

**Domainler arası:**

1. **Event-first:** ApplicationEventPublisher (modül içi) + Outbox tablosu → Kafka/Redpanda (dış entegrasyon veya güçlü asenkron).

2. **Read-only façade:** Başka domain verisini değiştirmeden okumak için yalnızca api/Facade üzerinden çağrı.

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

## ✅ QUALITY CHECKLIST

| Kategori                | Gereklilik                                 |
| ----------------------- | ------------------------------------------ |
| 🔍 **Test Coverage**    | %80 minimum (module-based)                 |
| 🧠 **Code Readability** | Domain-first structure, no "god services"  |
| 🧩 **Extensibility**    | Yeni domain eklenebilir, eskiye dokunmadan |
| 🔒 **Security**         | JWT + Policy enforcement aktif             |
| ⚡ **Performance**      | Endpoint latency < 50ms (in-process calls) |
| 🧱 **Fault Tolerance**  | Outbox + retry + cache                     |
| 🧾 **Auditability**     | Common/Audit log records for every action  |

---

## 🏁 SUMMARY

Fabric Management Platform artık:

- 🧩 **Modular Monolith** mimarisiyle yönetilebilir
- 🔄 **Event-driven** yapıda ölçeklenebilir
- 🔐 **Common platform engine** ile güvenli
- ⚙️ **Low-latency** ve **low-cost** şekilde çalışabilir
- 💡 Geliştiriciler için sade, net, esnek bir yapı sunar

---

## ✳️ NEXT STEPS

1. **Common modülü** oluştur (platform + infrastructure)
2. **@ApplicationModule** yapılarını tanımla (Spring Modulith)
3. **PolicyEngine** ve **TenantFilter**'ı aktif et
4. **Outbox + Redis + Flyway** altyapısını kur
5. **İlk modül**: Yarn Production Flow (fiber → yarn → fabric)
6. **Integration testleri**yle domain eventlerini doğrula

---

**Protocol Version:** 3.0  
**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

# Fabric Management Backend — AI Coding Standards

Bu dosya, projede kod yazan **tüm AI ajanlarının ve geliştiricilerin** uyması gereken tek kanonik kurallar bütünüdür.

---

## 1. Tech Stack

- **Java 21** (Records, Pattern Matching, Sealed Classes aktif)
- **Spring Boot 3.2+**, Spring Modulith 1.1, Spring Security + JWT
- **PostgreSQL** + Flyway (migration), Hibernate 6
- **MapStruct** (DTO mapping), **Lombok** (boilerplate), **Caffeine** (cache)
- **Resilience4j** (circuit breaker), **OpenFeign** (external API)
- **SpringDoc OpenAPI** (API docs), **Micrometer** (observability)

---

## 2. Module Structure & Layer Rules

Her modül/alt modül bu katman yapısını takip eder. Bağımlılık yönü: `api → app → domain ← infra`

```
{module}/
├── api/controller/       # @RestController — HTTP giriş noktası
├── api/facade/           # Birden fazla service'i orkestre eden ince katman (opsiyonel)
├── app/                  # @Service — business orchestration, event listener, scheduler
├── domain/               # Entity, Value Object, Enum, domain event, exception
│   ├── event/            # Domain event'ler (SADECE burada, app/event/ yasak)
│   └── exception/        # Domain-specific exception'lar
├── dto/                  # Request/Response DTO'ları (api/dto/ yasak, kök dto/ kullan)
├── infra/repository/     # @Repository — Spring Data JPA
├── infra/client/         # External API client'lar (opsiyonel)
└── mapper/               # MapStruct mapper'lar
```

**Katman Kuralları:**
- `domain` katmanı hiçbir dış katmanı import etmez (Spring annotation'ları hariç)
- `infra` sadece `domain` interface'lerini implemente eder
- `app` katmanı `domain` ve `infra`'yı kullanır
- `api` katmanı sadece `app` ve `dto`'yu bilir
- Katman ismi: `app/` kullan (`application/` yasak), `infra/` kullan (`infrastructure/` sadece `common/` için)

### 2.1 Modül Hiyerarşisi (Bounded Context'ler)

Proje üç seviyeli bir modül hiyerarşisine sahiptir. Her üst düzey paket, bağımsız bir Bounded Context'tir:

| Modül | Sorumluluk |
|-------|------------|
| `platform` | Auth, User, Organization, Tenant, Communication, TradingPartner, Subscription, Policy, Audit, AI |
| `production` | Masterdata (Fiber, Material, Recipe), Execution (Batch, WorkOrder, GoodsReceipt, Inventory, Lineage), Quality |
| `sales` | SalesOrder, Quote, Catalog, Pricing, Sample |
| `procurement` | PurchaseOrder, SubcontractOrder, SupplierRFQ, SupplierQuote |
| `flowboard` | Board, Task, Generator, Automation, Dashboard |
| `human` | Employee, Leave, Payroll, Compliance, Localization |
| `iwm` | Location, Reservation, StockCount, Transfer, RMA, Adjustment, Rules |
| `costing` | CostCalculation, PriceList, ExchangeRate |
| `notification` | Hub, i18n |
| `finance` | Invoice |
| `logistics` | Shipment |
| `approval` | ApprovalPolicy, ApprovalRequest, UserPromotion |
| `offline` | Sync |

- **Alt Modüller:** Büyük bounded context'ler alt modüllere ayrılır. Her biri kendi `api/app/domain/dto/infra` yapısını taşır. Örn: `production` → `masterdata/fiber`, `execution/batch`, `quality/result`
- **Platform vs Domain:** Platform modülleri (auth, user, tenant, subscription) uygulama altyapısıdır — domain modülleri platform'u kullanabilir, tersi yasak
- **`common/infrastructure/`:** Yalnızca framework-level, domain-agnostic altyapı: `BaseEntity`, `SecurityConfig`, `TenantContext`, `GlobalExceptionHandler`. İş mantığı burada bulunmaz

---

## 3. BaseEntity & Multi-Tenancy

**Her JPA entity `BaseEntity` veya `BaseJunctionEntity`'den türetilmelidir.** Manuel `id`, `tenantId`, `createdAt`, `updatedAt` ekleme.

```java
@Entity
@Table(name = "fiber_batches", indexes = {
  @Index(name = "idx_fiber_batch_tenant", columnList = "tenantId"),
  @Index(name = "idx_fiber_batch_uid", columnList = "uid")
})
public class FiberBatch extends BaseEntity {
    @Enumerated(EnumType.STRING)
    private FiberType fiberType;

    private BigDecimal quantity; // BigDecimal ZORUNLU — double/float yasak

    @Version
    private Long version; // Optimistic locking
}
```

**Multi-Tenancy:**
- Her query tenant-scoped olmalı (RLS + `TenantContext`)
- Global veri erişimi sadece `@InternalEndpoint` ile
- Tenant isolation testi zorunlu: Tenant A ile oluştur, Tenant B ile eriş → boş veya 404

**Security — AccessService Pattern:**
- Her modül kendi `XxxAccessService`'ini `security/` paketi altında tanımlar
- `BaseAccessService`'i extend eder, modüle özel yetkilendirme kurallarını içerir
- JWT token yönetimi `platform/auth/` sorumluluğunda — diğer modüller `AuthenticatedUserContext` üzerinden erişir

---

## 4. Coding Standards

### DO ✅
- **Records** → DTO ve Value Object için `record` kullan (immutable, compact)
- **Stream API** → Koleksiyon işlemlerinde `stream().filter().map().toList()` kullan, imperative döngü yerine
- **Optional chain** → `repository.findById(id).orElseThrow(...)` kullan, null check yerine
- **Pattern Matching** → `switch` expression + sealed class/record destructuring (Java 21)
- **Factory method** → Entity oluşturmada `create()` / `of()` kullan, public constructor yerine
- **Rich Domain Model** → İş kuralları ve state transition'lar entity içinde yaşar
- **Status Enum + Transition** → Her stateful entity bir status enum'a sahip; geçiş kuralları enum'da tanımlı
- **`@Version`** → Optimistic locking — concurrent update koruması
- **`@Transactional(readOnly = true)`** → Read-only sorgularda kullan
- **`@TransactionalEventListener(phase = AFTER_COMMIT)`** → Event listener'larda; commit sonrası dinle
- **Domain Exception hiyerarşisi** → Modül-bazlı exception'lar; generic `RuntimeException` yasak
- **Adapter pattern** → External API entegrasyonlarında (accounting, ERP) strategy/adapter kullan
- **Circuit breaker + Retry** → External çağrılarda `@CircuitBreaker` + `@Retry` kullan
- **Batch operations** → Bulk insert/update'lerde `flush()` + `clear()` ile batch yap
- **Composite index** → `tenantId` + sık filtrelenen alanlar için composite index oluştur
- **Hesapla, input'a güvenme** → Toplam ağırlık, net tutar gibi aggregated alanları backend'de hesapla

### DON'T ❌
- Entity'yi REST response olarak expose etme → DTO kullan
- `application/` paket ismi → `app/` kullan
- `api/dto/` → Kök `dto/` paketi kullan
- `app/event/` → `domain/event/` kullan
- Hard delete → Soft delete (`isActive`)
- `ddl-auto` (validate hariç) → Flyway kullan
- Tenant bypass eden query
- `double`/`float` para/miktar için → `BigDecimal`
- `@Transactional` Controller'da → Service'te kullan
- `catch (Exception e) {}` → Spesifik exception yakala, yutma
- Schema değişikliği migration'sız
- Cross-module'de doğrudan repository inject → QueryService/Facade/Event kullan
- Generic error mesajı → Domain-specific mesaj kullan (ör: "GSM must be 80-400")
- Boş paket / iskelet kod → Ya implemente et ya sil; Controller/Service'siz domain-only paket production kodunda bulunamaz

---

## 5. API & Error Handling

```java
// Response envelope — TÜM endpoint'ler bu formatı döner
return ApiResponse.success(batchDto);
return ApiResponse.success(batchDto, "Batch created");
return ApiResponse.error(apiError);

// Controller imzası
@PostMapping
@PreAuthorize("hasRole('PRODUCTION_MANAGER')")
public ResponseEntity<ApiResponse<BatchDto>> createBatch(
    @Valid @RequestBody CreateBatchRequest request) { ... }

// DTO — record + validation
public record CreateBatchRequest(
    @NotBlank String batchCode,
    @NotNull FiberType fiberType,
    @Positive @NotNull BigDecimal weightKg
) {}
```

- **MapStruct** ile entity↔DTO mapping; shared config `unmappedTargetPolicy = ERROR`
- **GlobalExceptionHandler** → Tüm exception'lar standart `ApiError` formatında
- **@PreAuthorize** → Her endpoint'te rol kontrolü zorunlu
- **OpenAPI** → `@Tag`, `@Operation`, `@ApiResponse` annotation'ları kullan
- **API versioning** → `/api/v1/`

### 5.1 Exception Hiyerarşisi

Her üst düzey modülün kendi `DomainException` alt sınıfı olmalıdır. Exception mesajları i18n-ready veya constants altında tutulur.

```
DomainException (abstract, common/infrastructure)
├── ProductionDomainException
│   ├── BatchDomainException
│   ├── FiberDomainException
│   └── RecipeDomainException
├── SalesDomainException
├── ProcurementDomainException
├── IwmDomainException
├── HumanDomainException
├── FinanceDomainException
└── LogisticsDomainException
```

---

## 6. Events & Cross-Module Communication

- **Domain event** → Modüller arası iletişim `ApplicationEventPublisher` ile
- Event sınıfları publish eden modülün `domain/event/` paketinde tanımlanır
- **Event isimlendirme** → `{Entity}{FiilGeçmişZaman}Event` (`BatchCreatedEvent`, `OrderConfirmedEvent`)
- **Event payload** → Minimum veri: sadece ID'ler ve kritik alanlar; full entity veya DTO event'e konmaz
- **Listener** → Dinleyen modülün `app/listener/` paketinde, `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`
- **Cross-module çağrı** → Doğrudan `@Autowired OtherService` yasak; interface (port) veya QueryService üzerinden
- **Facade** → Controller birden fazla modül service'i çağırıyorsa `api/facade/` kullan

---

## 7. Database & Flyway

- **Migration naming** → Delta: `V{yyyyMMddHHmmss}__{aciklama}.sql`; Repeatable: `R__{sira}__{aciklama}.sql`
- **Konsolide migration** → `db/migration/consolidated/` referans arşividir, Flyway tarafından çalıştırılmaz. Production'da çalışan migration'lar `db/migration/` kökündedir
- **Rollback** → Kritik migration'lar için `db/rollback/V{version}_ROLLBACK__{aciklama}.sql`
- **RLS** → `current_setting('app.current_tenant', true)::uuid` ile tenant isolation
- **Index** → `tenant_id`, FK'lar, status/state alanları, JSONB için GIN
- **CHECK constraint** → Enum ve business rule (ör: `quantity > 0`)
- **JSONB** → Flexible spec'ler için `@Type(JsonBinaryType.class)` + `columnDefinition = "jsonb"`
- **Tek modül = tek migration** → Cross-module schema değişiklikleri ayrı dosyalarda

---

## 8. Testing

| Tür | Suffix | Scope | Araçlar |
|-----|--------|-------|---------|
| Unit | `*Test.java` | Tek sınıf, mock dependency | JUnit 5, Mockito |
| Integration | `*IntegrationTest.java` / `*IT.java` | Spring context, gerçek DB | Testcontainers |
| Architecture | `*ArchTest.java` | Paket bağımlılıkları, naming | ArchUnit |

- **Co-location** → `src/test/.../batch/app/BatchServiceTest.java` mirror eder `src/main/.../batch/app/BatchService.java`
- **Tenant isolation testi** → Her modülde zorunlu: tenant A'dan oluştur, tenant B ile eriş → 404
- **Coverage** → %80+ (DTO, config, event POJO hariç)
- **ArchUnit doğrulaması** → Commit öncesi: `./mvnw test -Dtest="*ArchTest"`

---

## 9. Naming Conventions

| Katman | Pattern | Örnek |
|--------|---------|-------|
| Controller | `XxxController` | `BatchController` |
| Service | `XxxService` / `XxxEngine` / `XxxProcessor` | `BatchService` |
| Repository | `XxxRepository` | `BatchRepository` |
| Entity | `Xxx` | `FiberBatch` |
| Request DTO | `CreateXxxRequest` / `UpdateXxxRequest` | `CreateBatchRequest` |
| Response DTO | `XxxDto` / `XxxResponse` | `BatchDto` |
| Domain Event | `Xxx{PastTense}Event` | `BatchCreatedEvent` |
| Exception | `XxxDomainException` | `ProductionDomainException` |
| Listener | `XxxEventListener` | `BatchEventListener` |
| Facade | `XxxFacade` | `FiberFacade` |
| Access Service | `XxxAccessService` | `ProductionAccessService` |
| Scheduled Job | `XxxJob` | `ApprovalExpiryJob` |

**Paket isimleri** → Tümü lowercase, tek kelime/bitişik: `tradingpartner`, `goodsreceipt`

---

## 10. Performance

- **N+1 önleme** → `JOIN FETCH`, `@EntityGraph`, `@BatchSize`, DTO Projection
- **Pagination** → Liste endpoint'lerinde `Pageable` zorunlu; `findAll()` tüm tablo yasak
- **Caching** → Sık okunan, nadir değişen veri `@Cacheable` (Caffeine) ile; `@CacheEvict` ile invalidation
- **Batch insert** → `entityManager.flush()` + `clear()` ile 50'lik batch'ler
- **Index stratejisi** → `EXPLAIN ANALYZE` ile doğrula; `tenantId` + status + createdAt composite

---

## 11. Implementation Checklist

Yeni feature implement ederken bu sırayı takip et:
1. **Domain** → Entity (BaseEntity extend), Value Object (record), Enum (state machine), Domain Event
2. **Repository** → JPA Repository, custom query (JOIN FETCH, DTO projection)
3. **Service** → Business logic, event publishing, `@Transactional`
4. **DTO / Mapper** → Request/Response record, MapStruct mapper
5. **Controller** → REST endpoint, `@Valid`, `@PreAuthorize`, `ApiResponse<T>`
6. **Event Listener** → `@TransactionalEventListener(AFTER_COMMIT)`, `@Async`
7. **Config** → Security, properties
8. **Test** → Unit → Slice (`@DataJpaTest`) → Integration (`@SpringBootTest`)

---

## 12. Code Review Checklist

İncelemede bu kriterleri kontrol et (önem sırasıyla):

| # | Kriter | 🔴 Kritik Örnek |
|---|--------|-----------------|
| 1 | **Katman ihlali var mı?** | Controller'da business logic, domain'de @Service |
| 2 | **Güvenlik eksik mi?** | @PreAuthorize yok, SQL injection riski, entity response'da sızıyor |
| 3 | **Transaction tutarsızlığı?** | Event commit öncesi publish, @Version eksik, controller'da @Transactional |
| 4 | **N+1 / performans?** | Döngüde DB sorgusu, pagination yok, index eksik |
| 5 | **Tenant isolation bozuk mu?** | Query'de tenantId filtresi yok |
| 6 | **Domain model anemic mi?** | Tüm logic service'te, entity sadece getter/setter |
| 7 | **Modern Java kullanılmış mı?** | Record yerine class DTO, for loop yerine stream, null check yerine Optional |
| 8 | **Test yeterli mi?** | Unit + tenant isolation + integration |
| 9 | **İsimlendirme doğru mu?** | Suffix tablosuna uyuyor mu? |

**Severity:** 🔴 Kritik (merge blocker) → 🟡 Orta (önerilir) → 🟢 Tavsiye (iyileştirme)

---

## 13. Textile Domain

- Doğru terminoloji: fiber (COTTON, POLYESTER), yarn count (`"30/1"`), GSM (fabric), construction (WOVEN, KNIT)
- Quality grades: A (premium), B (standard), C (below), SECOND (defective)
- Para → BigDecimal, minor units (kuruş/cent), ISO 4217
- GSM: 80-400, width: 100-320 cm
- Üretim akışı: Fiber → Yarn → Fabric → Finishing → QC → Packaging

---

## 14. Karar Tablosu — "Bu sınıf nereye gider?"

| Soru | Cevap |
|------|-------|
| Framework/altyapı concern'i mi? | `common/infrastructure/` |
| Auth, tenant, user, org ile ilgili mi? | `platform/{subdomain}/` |
| Spesifik bir iş domain'ine ait mi? | İlgili domain modülü |
| Birden fazla modülün verisine erişiyor mu? | Interface/port veya QueryService üzerinden |
| Event mi publish ediyorsun? | `domain/event/` altına koy |
| Event mi dinliyorsun? | Dinleyen modülün `app/listener/` altına koy |
| Controller birden fazla service çağırıyor mu? | `api/facade/` oluştur |

---

## 15. Paket Referans Haritası

```
com.fabricmanagement/
├── common/infrastructure/          # Framework-level, domain-agnostic altyapı
│   ├── config/                     # OpenApiConfig, TimeConfig
│   ├── cqrs/                       # Command/Query marker interface'leri
│   ├── events/                     # DomainEvent, DomainEventPublisher
│   ├── mapping/                    # MapStructConfig
│   ├── persistence/                # BaseEntity, TenantContext, UIDGenerator
│   ├── security/                   # SecurityConfig, JwtFilter, BaseAccessService
│   └── web/                        # ApiResponse, GlobalExceptionHandler, PagedResponse
│
├── platform/                       # PLATFORM SERVİSLERİ
│   ├── auth/                       # JWT, MFA, Onboarding
│   ├── communication/              # Address, Email, WhatsApp, Notification
│   ├── organization/               # Organization, Department, Certification
│   ├── subscription/               # Subscription, Quota, FeatureCatalog
│   ├── tenant/                     # Tenant, TenantSettings
│   ├── tradingpartner/             # TradingPartner, PartnerUser
│   └── user/                       # User, Role, Profile
│
├── production/                     # ÜRETİM
│   ├── masterdata/{fiber,material,recipe}/
│   ├── execution/{batch,workorder,goodsreceipt,inventory,lineage}/
│   └── quality/result/
│
├── sales/                          # SATIŞ
│   └── {salesorder,quote,catalog,pricing,sample}/
│
├── procurement/                    # TEDARİK
│   └── {purchaseorder,subcontract,rfq,quote}/
│
├── human/                          # İNSAN KAYNAKLARI
│   └── {core/employee,leave,payroll,compliance}/
│
├── iwm/                            # DEPO YÖNETİMİ
│   └── {location,reservation,stockcount,transfer,rma,adjustment,rules}/
│
├── flowboard/                      # SCRUMBAN BOARD
│   └── {board,task,generator,automation,dashboard}/
│
├── costing/                        # MALİYETLENDİRME
├── notification/                   # BİLDİRİM
├── finance/                        # FİNANS
├── logistics/                      # LOJİSTİK
├── approval/                       # ONAY SİSTEMİ
└── offline/                        # OFFLINE SYNC
```

---

## 16. Automated Enforcement

Yukarıdaki kuralların büyük kısmı **ArchUnit testleriyle** otomatik olarak enforce edilir:

- `ConstitutionArchTest.java` → Katman bağımlılıkları, modül sınırları, paket isimlendirme
- `ModernJavaArchTest.java` → DDD kuralları, clean code, API tutarlılığı

```bash
# Commit öncesi doğrulama
./mvnw test -Dtest="com.fabricmanagement.architecture.*ArchTest"

# Format kontrolü
./mvnw fmt:format

# Bug detection
./mvnw spotbugs:check
```

---

## 17. Platform ↔ Human Tenant Decoupling (Employee Projection Port)

`platform/user` modülünün `human/core/employee` modülüne doğrudan binary bağımlılığını (service/entity seviyesinde) engellemek için **Port/Adapter Pattern** ve **Shared Kernel** kullanılır. 

### 17.1 Shared Kernel (`common/infrastructure/identity/`) ✅
Modüller arası veri değişiminde kullanılan ortak tipler burada tanımlanır:
- `Gender`, `Title` (Enum)
- `EmergencyContactData` (Record — immutable)

### 17.2 Port Pattern (Platform Domain) ✅
Platform modülü, HR verisine ihtiyaç duyduğunda `human` modülüne sormaz; kendi domain'inde bir interface (Port) ve veri modeli (Snapshot) tanımlar.
- `EmployeeProjectionPort`: Veri okuma (findByUserId, findByUserIds)
- `EmployeeCreationPort`: Veri oluşturma / lifecycle
- `EmployeeSnapshot`: Platform modülüne özel read-only veri modeli (Record)

### 17.3 Adapter Implementation (Human App) ✅
Human modülü, platform'un tanımladığı portları kendi `app/adapter/` klasörü altında implemente eder.
- `EmployeeProjectionAdapter`: Entity → Snapshot dönüşümünü yapar.
- `EmployeeCreationAdapter`: `EmployeeService` domain orchestration'ı ile platform taleplerini bağlar.

### 17.4 N+1 Önleme Kuralı ✅
Kullanıcı listeleri (admin, user search vb.) dönerken employee verisi eklemek için **mutlaka batch-load** (`findByUserIds`) kullanılmalıdır. Stream içinde tek tek `findByUserId` çağrılması **KESİNLİKLE YASAKTIR.**

## 18. Platform/AI ↔ Production Decoupling (AI Tool Registry Pattern)

`platform/ai` modülünün production altyapısına (repository, entity) doğrudan bağımlılığını 
engellemek için **AI Tool Registry Pattern** kullanılır.

### 18.1 Shared Infrastructure (`common/infrastructure/ai/`) ✅
- `AIToolProvider` — domain provider'ların implement ettiği interface (`getSupportedTools()`, `execute()`)
- `AIQueryNormalizer` — Türkçe→İngilizce fiber terminoloji çevirisi (pamuk→cotton, vb.)

### 18.2 Registry (`platform/ai/app/`) ✅
- `AIToolRegistry` — Tüm `AIToolProvider` bean'lerini Spring injection ile toplar.
  Sonuç cachelemesi (60 sn TTL) burada yönetilir. `create_*` araçları cache'lenmez.
- `AIFunctionCaller` — Saf entry point; yalnızca `TenantContext`'ten tenantId okur ve 
  `toolRegistry.execute()` 'e delege eder.

### 18.3 Domain Providers (Adapter) ✅
Her domain kendi AI araçlarını kendi `app/adapter/` altında implement eder:
- `FiberAIToolProvider` → `search_fibers`, `get_fiber_info`, `list_fiber_categories`, `create_fiber`
- `MaterialAIToolProvider` → `check_material_stock`, `create_material`, `search_materials`, `get_production_status`
- `SmartSearchAIToolProvider` (`platform/ai/app/adapter/`) → `smart_search` (cross-domain orkestrasyon)

### 18.4 Circular Dependency Çözümü ✅
`SmartSearchAIToolProvider` `AIToolRegistry`'ye bağımlıdır — ancak `AIToolRegistry` 
tüm provider'ları (SmartSearch dahil) toplar. Circular dependency `ObjectProvider<AIToolRegistry>` 
ile çözülür: Spring lazy proxy ile initialization sırasında circular dep oluşmaz.

### 18.5 ArchUnit Rule 11.4 ✅
`platform/ai` modülü production altyapısına (`..production..infra..`) doğrudan erişemez.
Facade (`api/facade/`) ve DTO kullanımı serbesttir.

### 18.6 Kural: Yeni AI Aracı Eklemek
1. İlgili domain modülünde `app/adapter/XxxAIToolProvider.java` oluştur (`AIToolProvider` implement et)
2. `getSupportedTools()` içinde araç adını kaydet
3. `execute()` içinde işlemi kendi facade'ı üzerinden gerçekleştir
4. Unit test yaz (`XxxAIToolProviderTest`)
5. `AIFunctionCaller`'a **kesinlikle dokunma** — araç otomatik olarak kayıt olur

---

## 19. WorkOrder Cross-Module Decoupling (Port/Adapter + ACL Pattern)

`production/execution/workorder` eksenindeki çapraz modül bağımlılıklarını izole etmek için
**Port/Adapter + Anti-Corruption Layer** uygulandı. 3 fazda tamamlandı.

### 19.1 Tespit Edilen Coupling Noktaları

| ID | Kaynak | Hedef | Tür |
|----|--------|-------|-----|
| C1 | `sales/SalesOrderRuleEngine` | `production/WorkOrderService` | Doğrudan servis çağrısı |
| C2 | `production/WorkOrderService` | `sales/SalesOrderConfirmedEvent.SalesOrderLineSnapshot` | Event tipi sızıntısı |
| C3 | `production/WorkOrderService` | `approval/ApprovalGuardService` | Doğrudan servis çağrısı |
| C4 | `approval/ApprovalGuardService` | `platform.user.infra/UserRepository` | Altyapı bypass |
| C5 | `production/WorkOrderService` | `platform/TradingPartnerCertificationService` | Kasıtlı — **dokunulmadı** |

C5 bilinçli olarak izole edilmedi: platform→domain yönü Rule 11.2 kapsamında kabul edilebilir.

### 19.2 Phase 1 — Event İzolasyonu & Altyapı Bypass Düzeltmesi ✅

**C2:** `production/execution/workorder/dto/IncomingSalesOrderLine` record tanımlandı.
`WorkOrderSalesEventListener` çeviri sınırıdır: event alır, map'ler, servise local DTO iletir.
`WorkOrderService` sıfır `sales.*` import'u ile çalışır.

**C4:** `approval/domain/port/UserTrustLevelPort` arayüzü + `platform/user/app/adapter/UserTrustLevelAdapter`.
`SystemUser.ID` kontrolü adapter içinde — `ApprovalGuardService` platform bilmez.

**Phase X (ertelendi):** `User.java`, `approval.domain.UserTrustLevel` enum'unu import ediyor.
Bu `platform/user → approval` pre-existing coupling ayrı bir refactoring konusudur.

### 19.3 Phase 2 — Cross-BC Port/Adapter (Hibrit Karar) ✅

**C1 — Bounded Port (1:1):**
- `sales/salesorder/domain/port/ProductionOrderPort` — Sales kendi dilinde konuşur ("production order", "work order" değil)
- `sales/salesorder/domain/port/DraftProductionOrderCommand` — Sales çıktı kontratı (record)
- `production/execution/workorder/app/adapter/WorkOrderCreationAdapter` — portu implement eder, command→request map'leme burada

**C3 — Universal Port (cross-cutting):**
- `common/infrastructure/approval/ApprovalPort` — approval cross-cutting olduğu için `common`'da; `String entityType` kullanır
- `approval/app/adapter/ApprovalGuardAdapter` — `String` → `ApprovalEntityType.valueOf()` dönüşümü burada
- `WorkOrderService` sıfır `approval.*` import'u; `"WORK_ORDER"` String sabitini kullanır

**Bağımlılık yönü kararı (DIP):**
- 1:1 bağımlılık → port consumer modülünde (`ProductionOrderPort` → `sales`)
- Cross-cutting → `common/infrastructure/` (`ApprovalPort` → `common`)

### 19.4 Phase 3 — ArchUnit Guardrail'ları ✅

`ConstitutionArchTest.java` — **Article 12 — WorkOrder Bounded Context Isolation:**

- **Rule 12.1:** `sales..` → `production..app..` / `production..infra..` yasak
- **Rule 12.2:** `production..` (EventListener hariç) → `approval.app..` yasak
- **Rule 12.3:** `approval..` → `platform.user.infra..` yasak

Rule 12.3 uygulanırken 3 gizli coupling daha yakalandı:

| Sınıf | Sorun | Port |
|-------|-------|------|
| `UserPromotionService` | `UserRepository.save()` + `UserService` doğrudan | `approval/domain/port/UserTrustMutationPort` |
| `ApproverRecipientResolver` | `UserRepository.findByTenantIdAndRole_RoleCodeIn()` | `approval/domain/port/ApproverRecipientPort` |

### 19.5 Tüm Port/Adapter Envanteri

**`approval/domain/port/`:**
- `UserTrustLevelPort` — trust level okuma (ApprovalGuardService)
- `UserTrustMutationPort` — trust level yazma + kullanıcı deaktivasyon (UserPromotionService)
- `ApproverRecipientPort` — role kodu → kullanıcı ID listesi (ApproverRecipientResolver)

**`platform/user/app/adapter/`:**
- `UserTrustLevelAdapter` — SystemUser.ID bypass burada
- `UserTrustMutationAdapter`
- `ApproverRecipientAdapter`

**`sales/salesorder/domain/port/`:**
- `ProductionOrderPort` + `DraftProductionOrderCommand`

**`production/execution/workorder/app/adapter/`:**
- `WorkOrderCreationAdapter`

**`common/infrastructure/approval/`:**
- `ApprovalPort` — cross-cutting; String entityType (enum coupling yok)

**`approval/app/adapter/`:**
- `ApprovalGuardAdapter`

### 19.6 Kurallar: Yeni Cross-BC Bağımlılık Eklendiğinde

1. Doğrudan servis/repository import'u **YASAK** — port tanımla
2. Port, ihtiyaç duyan modülde yaşar (consumer owns the contract — DIP)
3. 1:1 bağımlılık → port consumer modülünde; cross-cutting → `common/infrastructure/`
4. Adapter, sağlayıcı modülün `app/adapter/` içinde; infra'ya sadece adapter erişir
5. ACL: Port consumer'ın kendi dilini kullanır ("production order", "work order" değil)
6. Port/Adapter tamamlandıktan sonra `ConstitutionArchTest`'e ihlali engelleyen ArchUnit kuralı eklenir
7. `SystemUser.ID`, `ApproverRole` gibi platform sabitleri adapter'a taşınır; servis katmanı platform bilmez

---

## 20. Mimari Sağlık Raporu ve Konsolidasyon (Article 13)

Cross-Module alanında en toksik bağımlılık türü "Infrastructure Bypass" durumudur. Bir modülün, başka bir modülün `infra` katmanına (Repository, Entity vb.) doğrudan erişmesi; o modülün business kurallarını (validasyonlar, event publishing) ve app katmanındaki interceptor'ları (tenant isolation, RLS) tamamen devre dışı bırakır.

### Kural 13.1 - 13.4 (Cross-Module Infra Isolation)
- **HİÇBİR MODÜL** kendi dışındaki bir modülün `.infra..` paketine erişemez. (common/infrastructure istisnadır)
- İhtiyaç durumunda **Port/Adapter pattern** veya hedef modülün **QueryService/Facade** katmanları kullanılmalıdır.
- **Article 13 TAMAMEN TEMİZ — 13 frozen → 0 ✅** (Tüm 4 rule: 0 frozen violation)
- Frozen sınıflar `.and().doNotHaveSimpleName(...)` ile filtrelenir. ArchUnit testleri herhangi bir yeni infrastructure-bypass ihlaline izin vermez.
- Çözüm geçmişi ve kullanılan pattern'ler Section 22'de belgelenmiştir.

---

## 21. Notification Hub Cross-Module Decoupling (Port/Adapter + Event Enrichment)

Notification Hub'daki 3 listener (InventoryNotificationListener, ProcurementNotificationListener, ProductionNotificationListener) doğrudan `platform.organization.infra.repository.DepartmentRepository` kullanıyordu. Ek olarak ProcurementNotificationListener, SupplierRFQRepository üzerinden callback yaparak `rfqCreatedByUserId` çekiyordu.

### 21.1 Çözüm A — DepartmentRecipientPort (ACL Pattern)

Port, **notification modülünde** yaşar (platform'da değil) — bağımlılık grafiği tek yönlü kalır.

**Port:**
- `notification/hub/domain/port/DepartmentRecipientPort` — department bazlı kullanıcı/yönetici çözümleme
  - `findUsersByDepartmentKeyword(UUID tenantId, String... keywords)`
  - `findManagersByDepartmentKeyword(UUID tenantId, String... keywords)`

**Adapter:**
- `notification/hub/app/adapter/PlatformDepartmentAdapter` — ACL; DepartmentService + UserQueryService çağırır
  - Tüm `getDepartmentUsers()` / `getDepartmentManagers()` / `matchesAny()` helper logic'i burada konsolide

### 21.2 Çözüm B — Event Enrichment (SupplierQuoteReceivedEvent)

`ProcurementNotificationListener`, SupplierRFQRepository'den `rfqCreatedByUserId` çekiyordu (DB callback = N+1 risk). Çözüm: Event'e `rfqCreatedByUserId` alanı eklendi.

- `procurement/quote/domain/event/SupplierQuoteReceivedEvent` → yeni alan: `UUID rfqCreatedByUserId`
- Listener artık sıfır DB sorgusu yapar; tüm veri event'ten gelir

### 21.3 Refactored Listeners

3 listener'da yapılan değişiklikler:
- `DepartmentRepository` import'u kaldırıldı → `DepartmentRecipientPort` inject edildi
- `getDepartmentUsers()` / `getDepartmentManagers()` / `matchesAny()` helper method'ları silindi
- `ProcurementNotificationListener`: SupplierRFQRepository import'u kaldırıldı → event field'dan okuma

### 21.4 ArchUnit Etkisi

- **Rule 13.4** artık tamamen temiz (0 frozen violation)
- **Rule 13.1** frozen count: 7 → 4 (3 notification listener çözüldü)
- Toplam frozen violation: 12 → 8

### 21.5 Mimari Kararlar

| Karar | Gerekçe |
|-------|---------|
| Port notification'da, platform'da değil | Unidirectional dependency graph; consumer owns the contract (DIP) |
| Event Enrichment > Port/Adapter (RFQ case) | Hem coupling'i hem DB query'yi ortadan kaldırır; zero latency |
| Tek port, iki method | `findUsers` + `findManagers` — aynı bounded context, aynı aggregate (Department→User) |

---

## 22. Frozen Violation Envanteri ve Çözüm Geçmişi

### 22.1 Çözülen Gruplar

**Grup A — TenantRepository bypass ✅ (3 frozen → 0, Toplam: 8→5)**

Port/Adapter pattern — domain model sınırı geçilmediğinden uygulandı.
- Port: `common/infrastructure/tenant/TenantQueryPort` — `findAllActiveTenants()`, `findAllByIds()`, `findById()`
- Adapter: `platform/tenant/app/adapter/TenantQueryAdapter`
- DTO: `common/infrastructure/tenant/TenantReference` (id, uid, name)
- Çözülen: BatchCertificationExpiryCheckJob, FiberRequestService, InvoiceOverdueJob

**Grup B — Certification çapraz referans ✅ (3 frozen → 0, Toplam: 5→2)**

QueryService pattern — JPA @ManyToOne ilişkiler domain sınırını zaten geçtiğinden Port/Adapter yerine app-layer delegation uygulandı.
- `production/masterdata/fiber/app/FiberCertificationQueryService` — platform modüllerinin production infra bypass'ını önler
- `platform/tradingpartner/app/TradingPartnerCertificationQueryService` — production modülünün platform infra bypass'ını önler
- `platform/organization/app/OrganizationCertificationQueryService` — production modülünün platform infra bypass'ını önler
- Çözülen: OrganizationCertificationService, TradingPartnerCertificationService, BatchCertificationService

**Pattern Seçim Rehberi:**
- Domain entity sınırı geçilmiyorsa → **Port/Adapter** (minimal DTO, ACL)
- JPA @ManyToOne ilişki zaten sınır geçiyorsa → **QueryService** (app delegation, infra bypass'ı kapatır)

### 22.2 Article 13 — TAMAMEN TEMİZ ✅

**Grup C — Locale/i18n bypass ✅ (2 frozen → 0, Toplam: 2→0)**

Port/Adapter pattern — JPA @ManyToOne yoktur (sadece primitive UUID); Grup A pattern'i geçerli.
- `common/infrastructure/locale/LocaleResolutionPort` — 4 method: `findUserLocale()`, `findUserTimezone()`, `findTenantDefaultLocale()`, `findTenantTimezone()`
- `notification/i18n/app/adapter/LocaleResolutionAdapter` — port'u implement eder
- `platform/user/domain/port/UserLocaleConfigPort` — `findByUserId()`, `saveOrUpdate()`, `deleteByUserId()`
- `platform/user/domain/port/UserLocalePreferences` — record DTO (userId, locale, timezone)
- `notification/i18n/app/adapter/UserLocaleConfigAdapter` — entity create/update signature karmaşıklığını saklar; dateFormat korunur
- Normalization (`extractLanguageCode`, "tr-TR" → "TR") UserLocaleService'de kalır — porta normalize data gider
- Çözülen: LocalizationService, UserLocaleService

**Tüm Frozen Violations: 13 → 0** — Rule 13.1, 13.2, 13.3, 13.4 hepsi temiz.

### 22.3 Yeni Cross-Module Bağımlılık Oluştuğunda

Aşağıdaki karar ağacını kullan:

```
Yeni cross-module bağımlılık gerekiyor
         |
         ▼
Domain entity @ManyToOne var mı?
    |               |
   EVET            HAYIR
    |               |
    ▼               ▼
QueryService    UUID/primitive yeterli mi?
(app delegation)    |               |
                   EVET            HAYIR (zengin veri)
                    |               |
                    ▼               ▼
               Port/Adapter    Port/Adapter
               (minimal DTO)  (zengin DTO)
```

Port yeri: 1:1 → consumer modülünde; cross-cutting (2+ consumer) → `common/infrastructure/`

---

## 23. Domain Event Standardizasyonu (Rule 8.1)

**Article 8 — Rule 8.1**: `domain/event/` paketindeki tüm `*Event` sınıfları `DomainEvent` base class'ını extend etmek zorundadır.

### 23.1 Kural Amacı

`DomainEvent` base class'ı (`common.infrastructure.events.DomainEvent`) 4 standart field sağlar:
- `eventId` (UUID) — her event için benzersiz ID, otomatik üretilir
- `tenantId` (UUID) — hangi tenant'ın eventi
- `eventType` (String) — "BATCH_LINEAGE_DELETED" gibi sabit string
- `occurredAt` (Instant) — event yayın zamanı, otomatik set edilir

### 23.2 Çözülen İhlaller (7 frozen → 0) ✅

| Event | Modül | Eski Yapı | Dönüşüm |
|-------|-------|-----------|---------|
| BatchLineageDeletedEvent | production/lineage | `@Value class` | `@Getter extends DomainEvent` |
| BatchLineageCreatedEvent | production/lineage | `@Value class` | `@Getter extends DomainEvent`, `consumedAt` korundu |
| CostVarianceDetectedEvent | costing | `@Builder record` | `@Getter class extends DomainEvent`, `detectedAt` → `occurredAt` |
| InventoryTransactionCreatedEvent | production/inventory | `@Getter @Builder class` | `extends DomainEvent`, `tenantId` field kaldırıldı |
| GoodsReceiptConfirmedEvent | production/goodsreceipt | `@Builder record` | `@Getter class extends DomainEvent` |
| MinStockAlertEvent | iwm | `@Data @Builder class` | `@Getter extends DomainEvent` (production versiyonu zaten uyumluydu) |
| ReturnRateExceededEvent | iwm | `@Data @Builder class` | `@Getter extends DomainEvent` (production versiyonu zaten uyumluydu) |

### 23.3 Migration Pattern (Record / @Value → DomainEvent subclass)

Java `record` ve `@Value` class'ları abstract class extend edemez. Dönüşüm adımları:

```java
// ESKİ: @Builder record veya @Value class
@Builder
public record XxxEvent(UUID tenantId, UUID entityId, String data) {}

// YENİ: @Getter class + @Builder constructor üzerinde
@Getter
public class XxxEvent extends DomainEvent {

    private final UUID entityId;
    private final String data;
    // tenantId kaldırıldı — DomainEvent'ten miras

    @Builder  // ← constructor üzerinde; producer builder chain'i DEĞİŞMEZ
    public XxxEvent(UUID tenantId, UUID entityId, String data) {
        super(tenantId, "XXX_HAPPENED");
        this.entityId = entityId;
        this.data = data;
    }
}
```

**Neden `@Builder` constructor üzerinde?** — Producer kodlarındaki `.tenantId(x).entityId(y).build()` zinciri kırılmadan çalışmaya devam eder.

**İş semantiği olan timestamp'ler korunur** — `consumedAt` (tüketim tarihi) veya `transactionDate` (işlem tarihi) `occurredAt` (event yayın anı) ile aynı kavram değildir; bu field'lar entity'de kalır.

### 23.4 Consumer Uyum Notu

`record` → `class` dönüşümünde record accessor'ları (`.fieldName()`) Java Bean getter'larına (`.getFieldName()`) dönüşür. Consumer sınıflarda bu değişiklik yapılmalıdır. Bu refactoring'de güncellenenler:
- `ProductionNotificationListener` — 5 call site
- `GoodsReceiptEventAdapter` — 3 call site

---

## 24. Entity Standardizasyonu (Rule 4.3)

**Article 4 — Rule 4.3**: `@Entity` sınıfları `BaseEntity` veya `BaseJunctionEntity`'den türemek zorundadır.

### 24.1 Kural Amacı

`BaseEntity` (`common.infrastructure.persistence.BaseEntity`) şu sözleşmeyi zorunlu kılar:
- `id` (UUID, @Id) — primary key, otomatik üretilir
- `tenantId` (UUID) — multi-tenant izolasyon
- `uid` (String) — insan okunabilir benzersiz ID
- `createdAt/By`, `updatedAt/By` — Spring Data Auditing
- `isActive`, `deletedAt` — soft-delete
- `version` (Long) — optimistic locking
- Abstract `getModuleCode()` — UID üretimi için modül kodu

### 24.2 Sonuç: 7 → 6 (1 false positive kaldırıldı)

| Entity | Sonuç | Gerekçe |
|--------|-------|---------|
| **TaskDependency** | ✅ frozen'dan çıkarıldı | Zaten BaseEntity'yi extend ediyordu — false positive |
| **Tenant** | 🔒 Kalıcı exception | Root entity; kendi tenantId'si olamaz |
| **TradingPartnerRegistry** | 🔒 Kalıcı exception | Platform-wide singleton; tenant scope yok |
| **EmployeeNumberSequence** | 🔒 Kalıcı exception | `tenantId` IS `@Id`; UUID @Id ile uyumsuz |
| **BatchOverrideLog** | 🔒 Kalıcı exception | Append-only audit log; soft-delete/version anlamsız |
| **UserDepartment** | 🔒 Kalıcı exception | Composite `@IdClass` key; BaseEntity @Id ile uyumsuz |
| **TaskLabelAssignment** | 🔒 Kalıcı exception | NOTE [X1] — bilinçli karar; junction table, audit trail gereksiz |

### 24.3 Karar Prensibi: Zorunlu Uyum vs. Tasarım İstisnası

Rule 8.1 (domain event'ler) → saf Java davranışı, hiçbir entity bu kuraldan muaf olamaz.
Rule 4.3 (entity'ler) → şema sözleşmesi; `tenantId`, UUID @Id, soft-delete **anlamsız** olduğunda enforce etmek mimariyi kirletir.

Altı entity için CalıcıException kararını `ConstitutionArchTest`'teki inline comment'ler belgeler. Gelecekte yeni `@Entity` yazan herhangi biri ArchUnit'ten anında feedback alır.

---

## 25. Katmanlı Mimari Temizliği (Rule 2.2 & Rule 6.2)

**Rule 2.2** — Application layer sınıfları infra katmanına ait olmamalıdır.
**Rule 6.2** — Application layer sınıfları `Service`, `Component`, `Facade`, vb. son eki taşımalıdır.

### 25.1 Rule 2.2 — Çözülen İhlaller

| Sınıf | Eski Konum | Yeni Konum | Gerekçe |
|-------|-----------|-----------|---------|
| `EmployeeComplianceContext` | `human/compliance/app/` | `human/compliance/domain/` | Domain verisi tutan `record`; app katmanında olmamalıydı |
| `EmailNotificationSender` | `notification/hub/infra/email/` | `notification/hub/app/adapter/email/` | Email gönderimi bir adapter; infra değil app/adapter'a aittir |

**Kalıcı exception:**
- `WebSocketAuthInterceptor` → `notification/hub/infra/websocket/` — Spring `ChannelInterceptor`; `JwtService` gerektiren gerçek güvenlik altyapısı; app katmanına taşınamaz.

ArchUnit muafiyet kapsamı `notification.hub.infra..` → `notification.hub.infra.websocket..` olarak daraltıldı.

### 25.2 Rule 6.2 — Naming Convention Ek İstisna

`EmailNotificationSender`, `app/adapter/` altına taşındıktan sonra isimlendirme kuralını tetikledi.
`Sender` son eki, `Evaluator` ve `Dispatcher` gibi önceki istisnalar listesine eklendi.
Gerekçe: Gönderici adaptörler iş odaklı isimler taşır; `Service` son eki semantik olarak yanlış olur.

### 25.3 Rule 11.2 — Platform→Domain Exception Dokümantasyonu

6 platform sub-modülü için bilinçli exception'lar `ConstitutionArchTest` inline comment'lerinde belgelendi:

| Kod | Modül | Gerekçe |
|-----|-------|---------|
| [E1] | `platform.user` | Approval port adaptörleri + Human event listener'lar (cache invalidation) + Notification query impl |
| [E2] | `platform.admin` | Platformu taraflı admin operasyonları; gelecekteki domain erişimi için korunan guard |
| [E3] | `platform.ai` | AIToolRegistry tüm domain modüllerinden AIToolProvider toplar; cross-domain tool discovery çekirdeği |
| [E4] | `platform.tradingpartner` | TradingPartner entity OfflineMetadata embed eder; certification servisleri FiberCertification referans eder |
| [E5] | `platform.organization` | OrganizationCertification, fiber standart doğrulaması için production.masterdata.fiber'a bağlıdır |
| [E6] | `platform.auth` | Tenant-scoped izin çözümlemesi için domain erişimi gerekebilir; ileriye dönük guard |

---

## Commits

Format: `type(scope): description`
- Types: `feat`, `fix`, `docs`, `refactor`, `test`, `perf`, `chore`
- Scopes: module name (`fiberos`, `yarnos`), `common`, `ci`, `db`

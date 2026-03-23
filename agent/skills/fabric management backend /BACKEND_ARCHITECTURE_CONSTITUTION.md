# 📋 Fabric Management Backend — Mimari Anayasa v1.0

Bu belge, backend projesinin mimari bütünlüğünü korumak amacıyla hazırlanmış temel kurallar bütünüdür. Proje, **Spring Boot Modular Monolith** mimarisini takip eder. Her geliştirme süreci, kod incelemesi ve mimari karar bu belgede tanımlanan kurallara tabidir.

---

## Madde 1: Modül Hiyerarşisi ve Sınırlar

Proje üç seviyeli bir modül hiyerarşisine sahiptir.

- **Kural 1.1 (Üst Düzey Modüller):** Her üst düzey paket, bağımsız bir Bounded Context'tir. Mevcut modüller:

  | Modül | Sorumluluk |
  |-------|-----------|
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

- **Kural 1.2 (Alt Modüller):** Büyük bounded context'ler alt modüllere ayrılır. Her alt modül kendi `api/app/domain/dto/infra` yapısını taşır. Örnek: `production` → `masterdata/fiber`, `execution/batch`, `quality/result`.

- **Kural 1.3 (`common/infrastructure/` Tanımı):** `common/infrastructure/` yalnızca framework-level, domain-agnostic altyapı içerir: `BaseEntity`, `SecurityConfig`, `TenantContext`, `DomainEventPublisher`, `GlobalExceptionHandler`. Hiçbir iş mantığı bu pakette bulunmaz.

---

## Madde 2: Katman Mimarisi (Layered Architecture)

Her modül (ve alt modül) aşağıdaki katman yapısını takip eder. Katman isimleri **standartlaştırılmıştır** — alternatif isimlendirmeler yasaktır.

```
moduladi/
├── api/                    # Giriş noktası — Controller, Facade, QueryService
│   ├── controller/         # REST endpoint'leri
│   ├── facade/             # Birden fazla service'i orkestre eden ince katman (opsiyonel)
│   └── query/              # Read-only query service'leri (opsiyonel, CQRS pattern)
├── app/                    # Uygulama mantığı — Service, EventListener, Scheduler
│   ├── scheduler/          # Scheduled job'lar (opsiyonel)
│   └── listener/           # Domain event listener'ları (opsiyonel)
├── domain/                 # Saf domain modeli — Entity, ValueObject, Enum, Event
│   ├── event/              # Domain event'ler
│   ├── exception/          # Domain-specific exception'lar
│   └── value/              # Value object'ler (opsiyonel)
├── dto/                    # Request/Response DTO'ları
└── infra/                  # Altyapı implementasyonları
    ├── repository/         # Spring Data repository'leri
    ├── client/             # External API client'ları (opsiyonel)
    └── persistence/        # JPA-specific implementasyonlar (opsiyonel)
```

- **Kural 2.1 (Katman İsmi Standardı):** Uygulama katmanının ismi her yerde `app/` olmalıdır. `application/` kullanılmaz. Hexagonal port tanımları gerekiyorsa `domain/port/` altında tanımlanır, ayrı bir `application/port/` katmanı açılmaz.

- **Kural 2.2 (Bağımlılık Yönü):** Bağımlılıklar dışarıdan içeriye akar:
  ```
  api → app → domain ← infra
  ```
  `domain` katmanı hiçbir şeyi import etmez (Spring, JPA annotation'ları hariç). `infra` katmanı `domain` interface'lerini implemente eder. `app` katmanı `domain` ve `infra`'yı kullanır. `api` katmanı sadece `app` ve `dto`'yu bilir.

- **Kural 2.3 (DTO Yerleşimi):** DTO'lar modül kökündeki `dto/` paketinde bulunur. `api/dto/` veya `api/dto/request/` + `api/dto/response/` gibi alt yapılanmalar kullanılmaz. İstisnasız tüm DTO'lar tek `dto/` paketinde yaşar. İsimlendirme yeterli ayrımı sağlar: `CreateBatchRequest`, `BatchDto`, `BatchResponse`.

---

## Madde 3: Modüller Arası İletişim

Modüller birbirini doğrudan import edemez. İletişim belirli mekanizmalarla yapılır.

- **Kural 3.1 (Domain Event'ler):** Modüller arası asenkron iletişim Spring `ApplicationEventPublisher` üzerinden domain event'ler ile yapılır. Event sınıfları publish eden modülün `domain/event/` paketinde tanımlanır. Dinleyen modül bu event sınıfını import eder — bu tek izin verilen cross-module import'tur.

- **Kural 3.2 (Event Yerleşimi):** Tüm domain event'ler `domain/event/` altında tanımlanır. `app/event/` kullanılmaz — application layer'da tanımlanan event'ler `domain/event/`'e taşınır.

- **Kural 3.3 (Senkron Cross-Module Çağrı):** Bir modülün başka bir modülün service'ini çağırması gerekiyorsa, çağrılan modül bir interface (port) tanımlar ve bu interface üzerinden iletişim kurulur. Doğrudan `@Autowired SomeOtherModuleService` yasaktır — yalnızca tanımlı interface'ler inject edilir.

- **Kural 3.4 (Facade Pattern):** Bir controller birden fazla modülün service'ini orkestre etmesi gerekiyorsa, `api/facade/` altında bir Facade sınıfı oluşturulur. Controller'lar direkt birden fazla service çağırmaz.

---

## Madde 4: Domain Modeli Kuralları

Domain katmanı projenin kalbidir ve en katı kurallara tabidir.

- **Kural 4.1 (Rich Domain Model):** Entity'ler sadece getter/setter değildir. İş kuralları ve durum geçişleri entity metodları içinde uygulanır. Validasyon mantığı mümkün olduğunca entity'de yaşar, service'te değil.

- **Kural 4.2 (Status Enum + Geçiş Kontrolü):** Her stateful entity bir Status enum'a sahiptir. Geçerli durum geçişleri enum'da veya entity metodunda tanımlanır. `InvalidStatusTransitionException` ile korunur.

- **Kural 4.3 (BaseEntity Zorunluluğu):** Tüm JPA entity'leri `BaseEntity` veya `BaseJunctionEntity`'den türetilir. Manuel `id`, `createdAt`, `updatedAt`, `tenantId` alanı eklenmez.

- **Kural 4.4 (Domain Exception Hiyerarşisi):** Her üst düzey modülün kendi `DomainException` alt sınıfı vardır: `ProductionDomainException`, `SalesDomainException`, `IwmDomainException` vb. Generic `RuntimeException` throw edilmez.

---

## Madde 5: Güvenlik ve Multi-Tenancy

- **Kural 5.1 (AccessService Pattern):** Her modül kendi `XxxAccessService`'ini `security/` paketi altında tanımlar. Bu sınıf `BaseAccessService`'i extend eder ve modüle özel yetkilendirme kurallarını içerir.

- **Kural 5.2 (Tenant İzolasyonu):** Tüm veri erişimi `TenantContext` üzerinden tenant-scoped olarak yapılır. Repository query'leri `tenantId` filtresini içermek zorundadır. Global veri erişimi sadece `@InternalEndpoint` ile işaretlenmiş endpoint'lerde ve platform admin service'lerinde yapılabilir.

- **Kural 5.3 (Kimlik Doğrulama):** JWT token yönetimi `platform/auth/` modülünün sorumluluğundadır. Diğer modüller `AuthenticatedUserContext` üzerinden mevcut kullanıcı bilgisine erişir. Doğrudan JWT parsing yapılmaz.

---

## Madde 6: İsimlendirme Standartları

- **Kural 6.1 (Paket İsimleri):** Tümü `lowercase`, tek kelime veya bitişik: `tradingpartner`, `goodsreceipt`, `salesorder`. Tire veya alt çizgi kullanılmaz.

- **Kural 6.2 (Sınıf İsimleri):** Katman + domain ile tutarlı:

  | Katman | Pattern | Örnek |
  |--------|---------|-------|
  | Controller | `XxxController` | `BatchController` |
  | Service | `XxxService` | `BatchService` |
  | Repository | `XxxRepository` | `BatchRepository` |
  | Domain Entity | `Xxx` | `Batch` |
  | DTO (Request) | `CreateXxxRequest`, `UpdateXxxRequest` | `CreateBatchRequest` |
  | DTO (Response) | `XxxDto`, `XxxResponse` | `BatchDto` |
  | Domain Event | `XxxEvent` (fiil geçmiş zaman) | `BatchCreatedEvent` |
  | Domain Exception | `XxxDomainException` | `BatchDomainException` |
  | Scheduled Job | `XxxJob` | `ApprovalExpiryJob` |
  | Event Listener | `XxxEventListener` veya `XxxListener` | `BatchEventListener` |
  | Access Service | `XxxAccessService` | `ProductionAccessService` |
  | Facade | `XxxFacade` | `FiberFacade` |

- **Kural 6.3 (Katman İsmi):** `app` kullanılır, `application` kullanılmaz. `infra` kullanılır, `infrastructure` yalnızca `common/infrastructure/` için kabul edilir (tarihsel uyumluluk).

---

## Madde 7: Hata Yönetimi

- **Kural 7.1 (Exception Hiyerarşisi):**
  ```
  DomainException (abstract, common)
  ├── ProductionDomainException
  │   ├── BatchDomainException
  │   ├── FiberDomainException
  │   └── RecipeDomainException
  ├── SalesDomainException
  ├── ProcurementDomainException
  ├── IwmDomainException
  └── ...
  ```

- **Kural 7.2 (GlobalExceptionHandler):** Tüm exception'lar `GlobalExceptionHandler` tarafından yakalanır ve standart `ApiResponse` / `ApiError` formatında döndürülür. Modül-spesifik exception handler yalnızca özel HTTP status mapping gerekiyorsa oluşturulur (örn. `ApprovalExceptionHandler`).

- **Kural 7.3 (Exception Mesajları):** Exception mesajları i18n key olarak tanımlanır veya `constants/` altında sabit olarak tutulur. Hardcoded string yasaktır.

---

## Madde 8: Domain Event Standardı

- **Kural 8.1 (Event Sınıf Yapısı):** Tüm domain event'ler `DomainEvent` base class'ını extend eder. Immutable olmalıdır (Lombok `@Value` veya `@Builder` ile).

- **Kural 8.2 (Event İsimlendirme):** `{Entity}{FiilGeçmişZaman}Event` formatı: `BatchCreatedEvent`, `OrderConfirmedEvent`, `GoodsReceiptConfirmedEvent`. Şimdiki zaman kullanılmaz (`BatchCreating` yanlış).

- **Kural 8.3 (Event Payload):** Event'ler minimum veri taşır — sadece listener'ın ihtiyaç duyduğu ID'ler ve kritik alanlar. Full entity veya DTO event'e konmaz; listener gerekli veriyi kendi repository'sinden çeker.

- **Kural 8.4 (Listener Yerleşimi):** Event listener'lar dinleyen modülün `app/` veya `app/listener/` paketi altında bulunur. Publish eden modül listener içermez.

---

## Madde 9: Veritabanı ve Migration Kuralları

- **Kural 9.1 (Flyway Naming):** Konsolide migration'lar `V001` – `V010` formatında modül bazlı. Delta migration'lar `V{yyyyMMddHHmmss}__{aciklama}.sql` formatında. Repeatable migration'lar `R__{sira}__{aciklama}.sql`.

- **Kural 9.2 (Konsolide vs Delta):** `db/migration/consolidated/` klasörü referans arşividir, Flyway tarafından çalıştırılmaz. Production'da çalışan migration'lar `db/migration/` kök dizinindedir.

- **Kural 9.3 (Rollback):** Her kritik migration için `db/rollback/` altında karşılık gelen rollback scripti bulunur. Rollback dosya ismi: `V{version}_ROLLBACK__{aciklama}.sql`.

- **Kural 9.4 (Tek Modül = Tek Migration):** Bir migration dosyası birden fazla modülün tablolarını değiştiremez. Cross-module schema değişiklikleri ayrı migration dosyalarında yapılır.

---

## Madde 10: Test Kuralları

- **Kural 10.1 (Co-location):** Test dosyaları, test ettikleri sınıfın mirror package'ında bulunur:
  ```
  src/main/.../batch/app/BatchService.java
  src/test/.../batch/app/BatchServiceTest.java
  ```

- **Kural 10.2 (Test Türleri):**

  | Tür | Suffix | Scope |
  |-----|--------|-------|
  | Unit Test | `*Test.java` | Tek sınıf, mocklanmış dependency'ler |
  | Integration Test | `*IntegrationTest.java` veya `*IT.java` | Spring context, gerçek DB |

- **Kural 10.3 (Her Modül Kendi Testini Taşır):** Test coverage boşlukları modül bazında izlenir. Yeni feature merge edilmeden önce en az service katmanı test edilmiş olmalıdır.

---

## Madde 11: `platform` Modülü Özel Kuralları

`platform` (mevcut yapıda `common/platform/`) projenin en büyük ve en kritik modülüdür.

- **Kural 11.1 (Alt Modül Yapısı):** `platform` altındaki her sub-domain kendi `api/app/domain/dto/infra` yapısını taşır ve bağımsız bir bounded context gibi davranır.

- **Kural 11.2 (Platform vs Domain Modüller):** Platform modülleri (auth, user, tenant, subscription) uygulama altyapısıdır — domain modüllerinden (production, sales, procurement) bağımsızdır. Domain modülleri platform modüllerini kulllanabilir, tersi olmaz.

- **Kural 11.3 (Communication Modülü):** Address, Contact, Email, Notification, WhatsApp gibi iletişim altyapısı `platform/communication/` altında yaşar. Diğer modüller notification göndermek için `NotificationService` interface'i üzerinden iletişim kurar, doğrudan email/sms sınıflarını import etmez.

---

## Madde 12: İskelet Kod ve Boş Modül Yasağı

- **Kural 12.1 (Boş Paket Yasağı):** Controller veya Service içermeyen, sadece domain entity + enum barındıran paketler production kodunda bulunamaz. Ya implemente edilir ya da `// TODO` ile birlikte docs/todo'ya taşınır.

- **Kural 12.2 (`target/` Dışlama):** `target/` klasörü `.gitignore`'da olmalıdır ve code review kapsamı dışındadır.

- **Kural 12.3 (Arşiv Dosyalar):** `architecture_review.txt` gibi dosyalar `docs/` altına taşınır, source tree'de bulunmaz.

---

## 🎯 Altın Standart Paket Hiyerarşisi

```
com.fabricmanagement/
├── FabricManagementApplication.java
│
├── common/
│   └── infrastructure/          # Framework-level altyapı
│       ├── config/              # OpenApiConfig, TimeConfig
│       ├── cqrs/                # Command/Query marker interface'leri
│       ├── events/              # DomainEvent, DomainEventPublisher
│       ├── mapping/             # MapStructConfig
│       ├── persistence/         # BaseEntity, TenantContext, UIDGenerator
│       ├── security/            # SecurityConfig, JwtFilter, BaseAccessService
│       ├── util/                # DuplicateValidator
│       └── web/                 # ApiResponse, GlobalExceptionHandler, PagedResponse
│           ├── exception/
│           └── rate/
│
├── platform/                    # PLATFORM SERVİSLERİ
│   ├── admin/                   # Platform admin
│   ├── ai/                      # AI asistan
│   ├── audit/                   # Audit log
│   ├── auth/                    # Kimlik doğrulama, JWT, MFA, Onboarding
│   ├── communication/           # Address, Contact, Email, Notification, WhatsApp
│   ├── organization/            # Organization, Department, Certification
│   ├── policy/                  # Policy engine
│   ├── subscription/            # Subscription, Quota, FeatureCatalog
│   ├── tenant/                  # Tenant, TenantSettings
│   ├── tradingpartner/          # TradingPartner, PartnerUser, Registry
│   └── user/                    # User, Role, Profile, WorkLocation
│
├── production/                  # ÜRETİM
│   ├── common/exception/
│   ├── execution/
│   │   ├── batch/               # Batch lifecycle
│   │   ├── goodsreceipt/        # Mal kabul
│   │   ├── inventory/           # Stok hareketleri ve bakiye
│   │   ├── lineage/             # Batch soy ağacı
│   │   └── workorder/           # İş emri
│   ├── masterdata/
│   │   ├── fiber/               # Fiber tanımları ve kalite standartları
│   │   ├── material/            # Malzeme tanımları
│   │   └── recipe/              # Reçete yönetimi
│   ├── quality/
│   │   └── result/              # QC test sonuçları
│   └── security/
│
├── sales/                       # SATIŞ
│   ├── catalog/
│   ├── common/exception/
│   ├── pricing/
│   ├── quote/
│   ├── salesorder/
│   ├── sample/
│   └── security/
│
├── procurement/                 # TEDARİK
│   ├── common/exception/
│   ├── purchaseorder/
│   ├── quote/
│   ├── rfq/
│   ├── subcontract/
│   └── security/
│
├── flowboard/                   # PROJE YÖNETİMİ (Scrumban)
│   ├── automation/
│   ├── board/
│   ├── common/websocket/
│   ├── dashboard/
│   ├── generator/
│   └── task/
│
├── human/                       # İNSAN KAYNAKLARI
│   ├── common/exception/
│   ├── compliance/
│   ├── core/employee/
│   ├── leave/
│   ├── payroll/
│   ├── security/
│   └── shared/domain/           # HR alt modüllerinin ortak tipleri
│
├── iwm/                         # DEPO YÖNETİMİ
│   ├── adjustment/
│   ├── common/
│   ├── location/
│   ├── reservation/
│   ├── rma/
│   ├── rules/
│   ├── security/
│   ├── stockcount/
│   └── transfer/
│
├── costing/                     # MALİYETLENDİRME
├── notification/                # BİLDİRİM ve i18n
├── finance/                     # FİNANS
├── logistics/                   # LOJİSTİK
├── approval/                    # ONAY SİSTEMİ
└── offline/                     # OFFLINE SYNC
```

---

## 📐 Karar Tablosu

Yeni bir sınıf veya paket oluştururken bu tabloyu kullan:

| Soru | Evet | Hayır |
|------|------|-------|
| Framework/altyapı concern'i mi? | `common/infrastructure/` | — |
| Auth, tenant, user, org ile ilgili mi? | `platform/{subdomain}/` | — |
| Spesifik bir iş domain'ine ait mi? | İlgili domain modülü | — |
| Birden fazla modül kullanıyor mu? | Interface/port üzerinden iletişim | — |
| Sadece entity + enum mi var, service yok mu? | İmplemente et veya `docs/todo`'ya taşı | — |
| Event mi publish ediyorsun? | `domain/event/` altına koy | — |
| Event mi dinliyorsun? | Dinleyen modülün `app/listener/` altına koy | — |

---

## 🔄 Açık Refactoring Kararları

Anayasa kabul edildikten sonra uygulanacak temizlik adımları:

1. **`common/platform/` → `platform/`:** Paket rename. `common/` altında sadece `infrastructure/` ve `util/` kalır.
2. **`application/` → `app/`:** `human/core/employee/application/` ve `flowboard/*/application/` paketleri `app/`'e rename edilir.
3. **`app/event/` → `domain/event/`:** `flowboard/task/app/event/` altındaki event'ler `domain/event/`'e taşınır.
4. **Boş paketler temizlenir:** `production/execution/warehouse/`, yarı-implemente IWM sub-modülleri değerlendirilir.
5. **DTO yerleşimi standardize edilir:** `api/dto/`, `api/dto/request/`, `api/dto/response/` yapıları kök `dto/`'ya düzleştirilir.
6. **`consolidated/` klasörü:** Production'da kullanılmıyorsa `docs/archive/` altına taşınır.
7. **`architecture_review.txt`:** Source tree'den `docs/` altına taşınır.

🧶 FABRIC-YARN-SERVICE
Domain Architecture & Specification (Production-Ready Design)
🎯 PURPOSE

fabric-yarn-service sistemde ipliğin (yarn) teknik, ticari ve maliyet bazlı tekil tanımını yönetir.
Her “yarn” bir aggregate root’tur; fiber composition, supplier mappings ve cost history alt agregatlarıyla birlikte çalışır.

🔹 Bu servis üretim zincirinin en alt seviyesindeki “hammadde kimliği”ni temsil eder.
🔹 Her domain ondan türetilir: weaving, finishing, costing, pricing, catalog.

🧱 DOMAIN BOUNDARY
In-Scope

Yarn tanımı (technical & compositional identity)

Supplier bazlı eşleştirme (supplier SKU, fiyat, currency)

Cost tracking (valid-from/to, currency normalization)

Event publishing (YarnDefined, YarnCostChanged)

Read model caching (fast lookup for production)

Out-of-Scope

Fiber CRUD → fabric-fiber-service

Costing logic → fabric-costing-service

Pricing or approval logic → fabric-pricing-service

Inventory or inbound flows → ayrı bounded context

🧩 AGGREGATE STRUCTURE
YarnAggregate (Root)
 ├── YarnSpecification (Value Object)
 │     ├─ compositionType : CompositionType (PURE / BLEND / YARN_BASED)
 │     ├─ components : List<FiberComponent>
 │     ├─ physicalProperties : YarnProperty
 │     ├─ wasteRate : %
 │     └─ unitOfMeasure : UnitOfMeasure (KG / BOBBIN / CONE)
 │
 ├── SupplierMapping (Sub-Aggregate)
 │     ├─ supplierId
 │     ├─ supplierYarnCode
 │     ├─ supplierYarnName
 │     ├─ currency / price / validFrom / validTo
 │     └─ status : ACTIVE / INACTIVE
 │
 ├── YarnCost (Sub-Aggregate)
 │     ├─ pricePerUnit
 │     ├─ currency
 │     ├─ exchangeRate
 │     ├─ transportCost / wasteFactor / totalCost
 │     └─ effectiveDateRange
 │
 └── Audit & Version (BaseEntity)

⚙️ CORE ATTRIBUTES
Alan	Tür	Açıklama
id	UUID	System identity (immutable)
tenantId	UUID	Multi-tenant isolation
code	String	Internal yarn code (YRN-00045)
name	String	Human-readable (e.g. “Ne 30/1 Compact CO/PE 60/40”)
compositionType	Enum (PURE / BLEND / YARN_BASED)	Technical base
specification	YarnSpecification (VO)	All measurable/technical attributes
category	Enum (KNITTING / WEAVING / SEWING / GENERAL)	Usage domain
currentCost	Money (Value Object)	Snapshot of last calculated cost
currency	Enum	ISO currency
supplierCount	Integer	Derived from SupplierMapping list
status	Enum (ACTIVE / INACTIVE / ARCHIVED)	Lifecycle state
version / audit fields	inherited	From BaseEntity
🧬 COMPOSITION MODEL
CompositionType Enum
Değer	Açıklama
PURE	Tek %100 fiber (ör. Cotton)
BLEND	Birden fazla fiber oranla (ör. CO/PE 60/40)
YARN_BASED	İki farklı ipliğin malunesi (ör. melange, core-spun)
FiberComponent (Value Object)
Alan	Açıklama
fiberCode	Fiber Service’teki referans (örn. CO, PE, VI)
percentage	Karışımdaki oran (toplam = 100%)
sustainabilityType	Organic, Recycled, Bio-based (Fiber’dan miras)

🧠 Rule:

Sum(percentage) = 100

At least one component must be ACTIVE in Fiber Service

YarnProperty (Value Object)
Alan	Açıklama
countSystem	NE / NM / TEX
countValue	Örn. 30, 40, 20
ply	1, 2, 3
twistType	S / Z / Balanced
twistPerMeter	optional numeric
colorType	RawWhite / Melange / Dyed
tensileStrength, elongation, usterCVm	optional QC metrics
💰 COST SUB-AGGREGATE
Responsibilities

Track historical cost evolution per yarn and supplier.

Normalize across currencies and dates.

Publish YarnCostChanged event when valid range shifts.

Invariants

Only one “active cost” per supplier at a given time.

Cost always expressed in tenant default currency via FX normalization.

No hardcoded waste/transport multipliers → loaded from Config / Policy tables.

🧾 SUPPLIER MAPPING SUB-AGGREGATE
Alan	Açıklama
supplierId	CompanyService reference
supplierYarnCode	Tedarikçi iç kodu
supplierYarnName	Tedarikçi ürün adı
currency	ISO code
price	Decimal
validFrom, validTo	Tarih aralığı
status	ACTIVE / INACTIVE

➡ Each supplier mapping produces YarnSourcingAdded event.

🔁 EVENT MODEL (Kafka Topics)
Event	Trigger	Consumer(s)	Payload Anahtarları
YarnDefined	Yarn created	Catalog, Weaving	yarnId, spec, composition, createdAt
YarnUpdated	Technical update	Catalog	yarnId, changedFields
YarnCostChanged	Cost update	Costing, Catalog	yarnId, newCost, validFrom, currency
SupplierMappingAdded	Supplier link	Costing	yarnId, supplierId, price
YarnDeactivated	Soft delete	Catalog	yarnId

All events published via Transactional Outbox Pattern → guaranteed delivery.

🔒 VALIDATION RULES
Kural	Açıklama
composition.totalPercentage == 100	Domain invariant
fiber must exist & ACTIVE in FiberService	External reference validation
countValue > 0	Numeric rule
wasteRate ∈ [0, 100]	Config constraint
validFrom < validTo	Temporal consistency
deleted=false ⇒ status=ACTIVE	Soft-delete integrity

All numeric thresholds (e.g. tolerance, fire ratio) are configurable via Policy table —
zero hardcoded constants anywhere.

🧠 DOMAIN BEHAVIORS
Behavior	Description
defineYarn()	Creates a new YarnAggregate, validates composition, publishes YarnDefined.
updateYarnSpec()	Updates physical properties, emits YarnUpdated.
addSupplierMapping()	Links supplier SKU, triggers YarnSourcingAdded.
updateCost()	Adds new YarnCost, recalculates snapshot, triggers YarnCostChanged.
deactivate()	Soft delete, triggers YarnDeactivated.
⚡ CQRS DESIGN
Komut (Write)	Açıklama
POST /yarns	Yeni yarn tanımı
POST /yarns/{id}/suppliers	Supplier mapping ekleme
PATCH /yarns/{id}/cost	Yeni maliyet girme
PATCH /yarns/{id}	Teknik özellik güncelleme
DELETE /yarns/{id}	Soft delete
Sorgu (Read)	Açıklama
GET /yarns/{id}	Detaylı bilgi (composition + cost)
GET /yarns?filter=...	Listeleme, arama
GET /yarns/{id}/cost/current	Güncel maliyet
GET /yarns/{id}/suppliers	Aktif tedarikçiler

All queries use Redis cache (tenant-scoped, TTL 120 s) with event-driven invalidation.

🧩 DEPENDENCIES & EVENT FLOW
[Fiber Service] ── FiberDefined ─▶ [Yarn Service]
       │                            │
       ▼                            ▼
   fiber cache              publishes YarnDefined, YarnCostChanged
                                   │
                                   ▼
                    [Weaving, Finishing, Costing, Catalog]

🧱 TECHNICAL PRINCIPLES
İlke	Uygulama
ZERO HARDCODED VALUES	Tüm katsayılar, default toleranslar ve currency rates → ConfigService / DB
ZERO OVER ENGINEERING	Tek aggregate root, üç sub-aggregate yeterli
CLEAN CODE / SRP	CommandService, QueryService, Mapper, Repository ayrı
CQRS	Write ve Read API’leri fiziksel olarak ayrılmış controller katmanları
YAGNI	Henüz gerekmedikçe quality metrics veya forecasting yok
KISS	Simple, deterministic data model; no polymorphic hierarchies
DRY	Common audit & version BaseEntity’de
PRODUCTION-READY	Transactional outbox, retry policy, OpenTelemetry, Redis caching
ORCHESTRATION + CHOREOGRAPHY	Orchestration: none (autonomous); Choreography: event-driven link to Costing/Pricing
🧾 AUDITABILITY & TRACEABILITY

All actions stamped with tenantId, createdBy, correlationId.

Events carry headers: X-Tenant-Id, X-Correlation-Id, X-Request-Id.

Outbox table maintains delivery guarantees (at-least-once semantics).

OpenTelemetry tracing spans across Yarn → Costing → Pricing chain.

🧭 SERVICE STARTUP DEPENDENCY
1️⃣ fabric-fiber-service     → 2️⃣ fabric-yarn-service
3️⃣ fabric-weaving-service   → 4️⃣ fabric-finishing-service
5️⃣ fabric-costing-service   → 6️⃣ fabric-pricing-service
7️⃣ fabric-catalog-service

✅ SUMMARY
Özellik	Durum
Domain Isolation	✔︎ Fully independent bounded context
External Dependency	FiberService (reference only)
Event Publication	✔︎ YarnDefined, YarnCostChanged
CQRS	✔︎ Separate command/query surfaces
Multi-Tenant	✔︎ via tenantId propagation
Observability	✔︎ OpenTelemetry + structured logs
Cache Strategy	✔︎ Redis + event invalidation
Production Readiness	✔︎ Transactional Outbox, Config externalization
Over-engineering risk	❌ None – minimal aggregates only

🧩 Outcome:
fabric-yarn-service is a self-contained, event-driven, CQRS-compliant domain that defines the technical DNA of all textile products.
It is the authoritative source of truth for any yarn identity, cost, and supplier mapping in the fabric management ecosystem.
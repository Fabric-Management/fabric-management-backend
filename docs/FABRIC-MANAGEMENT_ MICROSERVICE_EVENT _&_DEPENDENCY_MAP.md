🧩 FABRIC MANAGEMENT – MICROSERVICE EVENT & DEPENDENCY MAP (Manifesto-Aligned)
#	Microservice	Amaç (Bounded Context)	Yayınladığı Eventler (Publish)	Dinlediği Eventler (Listen)	Veri Akışı Yönü	Notlar / İlişki Türü
1️⃣	fabric-api-gateway	Tüm giriş noktası – JWT doğrulama, rate-limit, policy enforcement	–	–	⬜	Sadece proxy; event üretmez / dinlemez
2️⃣	fabric-user-service	Tenant onboarding, kullanıcı yönetimi	UserCreated, UserActivated, TenantRegistered	CompanyCreated, ContactVerified	🔁 Company ↔ User ↔ Contact	Orchestration servisi (Saga starter)
3️⃣	fabric-company-service	Firma, müşteri, tedarikçi yönetimi	CompanyCreated, SupplierLinked, CustomerLinked	TenantRegistered	⬅️ User → Company	“Supplier” / “Customer” bağı kurar
4️⃣	fabric-contact-service	Email / telefon kimlikleri, doğrulama	ContactCreated, ContactVerified	UserCreated	⬅️ User → Contact	Auth sonrası doğrulama akışları
5️⃣	fabric-notification-service	Email, SMS, push bildirimi	NotificationDelivered	UserInvitationSent, TenantRegistered	⬅️ Event-driven	Template cache + rate-limit
6️⃣	fabric-fiber-service 🆕	Saf fiber (elyaf) tanımları, CRUD kaynağı	FiberDefined, FiberUpdated, FiberDeactivated	–	🔄 Source-of-truth	Tüm fiber composition’lar buradan gelir
7️⃣	fabric-yarn-service	Fiber’lerden iplik oluşturma (yarn aggregate)	YarnDefined, YarnUpdated, YarnCostChanged	FiberDefined, FiberUpdated, CompanyCreated	⬅️ Fiber → Yarn	Harman ve malune bileşimleri içerir
8️⃣	fabric-weaving-service	Yarn’lardan ham kumaş üretimi (greige fabric)	FabricGreigeDefined, GreigeCostChanged	YarnDefined, YarnCostChanged	⬅️ Yarn → Weaving	Loom parametreleri, reçeteler
9️⃣	fabric-finishing-service	Boya/apre – bitmiş kumaş üretimi	FinishedFabricDefined, FinishingCostChanged	FabricGreigeDefined, GreigeCostChanged, SupplierLinked	⬅️ Weaving → Finishing	Canonical proses + supplier mapping
🔟	fabric-costing-service	Tüm üretim maliyetlerinin konsolidasyonu	ProductCostCalculated, PriceFloorViolated	YarnCostChanged, GreigeCostChanged, FinishingCostChanged	⬅️ Yarn / Weaving / Finishing → Costing	Kural motoru, versiyonlu formüller
11️⃣	fabric-pricing-service	Satış fiyatı ve indirim politikaları	PriceListUpdated, SellPriceProposed	ProductCostCalculated, SellPriceApproved	⬅️ Costing → Pricing	“Maliyet altına düşemez” kontrolü
12️⃣	fabric-catalog-service	Tüm ürünlerin tek katalog görünümü	CatalogItemIndexed, CatalogItemPriceChanged	YarnDefined, FabricGreigeDefined, FinishedFabricDefined, PriceListUpdated	⬅️ Yarn / Weaving / Finishing / Pricing → Catalog	Read-only, full-text index (Elastic/Redis)
13️⃣	fabric-inbound-service	Tedarikçi irsaliye / Excel / PDF veri içe alımı	InboundDraftCreated, InboundPosted, InboundMismatchDetected	SupplierLinked, CatalogItemIndexed	⬅️ Catalog → Inbound	Parser config + event-driven validation
14️⃣	fabric-inventory-service	Lot/roll/bale bazlı stok hareketleri	InventoryIn, InventoryAdjusted, InventoryCommitted	InboundPosted, WeavingRunCompleted, FinishingRunCompleted	⬅️ Inbound / Weaving / Finishing → Inventory	TTL kısa cache, event invalidation
15️⃣	fabric-procurement-service (opsiyonel)	Satınalma talepleri ve sipariş akışı	PurchaseOrderCreated, GoodsReceiptPosted	PriceListUpdated, InboundPosted	⬅️ Pricing / Inbound → Procurement	Gereksinim doğarsa eklenir
16️⃣	fabric-supplier-mapping-service (opsiyonel)	Tedarikçi kod / canonical entity eşleştirme	SupplierMappingUpdated	CompanyCreated	⬅️ Company → Mapping	Küçük çekirdek servis, lookup amaçlı
🔁 Event Akış Zinciri (Basitleştirilmiş Görünüm)
[Fiber Service]
   │
   ▼
[Yarn Service]  ── YarnCostChanged ─▶ [Costing Service]
   │                                 │
   ▼                                 ▼
[Weaving Service] ─ GreigeCostChanged ─▶ [Costing Service]
   │                                 │
   ▼                                 ▼
[Finishing Service] ─ FinishingCostChanged ─▶ [Costing Service]
                                         │
                                         ▼
                                [Pricing Service]
                                         │
                                         ▼
                                [Catalog Service]
                                         │
                                         ▼
                                [Inbound → Inventory → Procurement]

🧱 Cache & Dependency Özeti
Servis	Cachelenen Veri	Cache TTL	Kaynak
Fiber	Fiber referansları	Uzun (24h)	DB
Yarn	Fiber composition + cost snapshot	Kısa (5m)	Redis + Event invalidation
Weaving	Recipe + last 10 production KPI	Kısa (2m)	Redis
Finishing	Canonical recipe + supplier mapping	Orta (10m)	Redis
Costing	Last computed cost + rule version	Kısa (2m)	Redis
Pricing	Active price lists + overrides	Orta (10m)	Redis
Catalog	Full-text index + hot items	Kısa (30s–2m)	Elastic + Redis
Inbound	Parser config + supplier mapping snapshot	Kısa (1m)	Redis
⚙️ Orchestration & Choreography
Tür	Nerede Kullanılır	Servisler
Orchestration (Saga)	Onboarding akışı	User ↔ Company ↔ Contact ↔ Notification
Choreography (Event Flow)	Üretim – maliyet – fiyat – katalog akışı	Fiber → Yarn → Weaving → Finishing → Costing → Pricing → Catalog
✅ Özet Değerlendirme
Alan	Durum
Zero Hardcoded Values	✔︎ (Tüm katsayılar ve kurallar DB/config/source-driven)
Zero Over Engineering	✔︎ (Yalnızca bounded context gerektiren servisler var)
Event-Driven Communication	✔︎ (Kafka topic bazlı, transactional outbox’lı)
CQRS Separation	✔︎ (Catalog read-only, diğerleri command/query ayrımıyla)
Multi-Tenant Ready	✔︎ (Tüm domain entity’lerinde tenant_id)
Observability	✔︎ (OpenTelemetry + Correlation ID + RED metrics)
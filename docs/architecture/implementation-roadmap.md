# İmplementasyon Yol Haritası

> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Amaç: 59 dökümanın hangi sırayla implemente edileceğini, bağımlılık zincirini, iş değerini ve risk analizini tanımlar.

---

## Değerlendirme Kriterleri

Her implementasyon maddesi 4 kriter üzerinden değerlendirildi:

| Kriter | Açıklama | Ağırlık |
|---|---|---|
| **Bağımlılık** | Başka ne tamamlanmış olmalı? Kaç modül bunu bekliyor? | Kritik |
| **İş Değeri** | Kullanıcıya/işletmeye ne kazandırır? | Yüksek |
| **Teknik Risk** | Ne kadar karmaşık? Mevcut koda etkisi ne? | Orta |
| **Mevcut Kod Uyumu** | Kodda altyapı ne kadar hazır? | Orta |

---

## Mevcut Kod Durumu — Başlangıç Noktası

```
TAMAMLANMIŞ (kodda çalışan):
  ✅ Temel Yapılar     : BaseEntity, User, Auth, Role, Organization, Department
  ✅ İş Ortakları      : TradingPartner, Registry, PartnerCertification
  ✅ Referans Tabloları : CertificationType, FiberCategory, FiberIsoCode, FiberAttribute
  ✅ Hammadde          : Material, Fiber, FiberQualityStandard, FiberRequest
  ✅ Parti Yönetimi    : Batch (evrensel), BatchCertification, BatchReservation, BatchLineage
  ✅ Stok (production) : InventoryTransaction, InventoryBalance (CQRS)
  ✅ Kalite Kontrol    : FiberTestResult, FiberQcAutoEvaluator
  ✅ Depo Lokasyon     : WarehouseLocation (production şemasında)
  ✅ Güvenlik          : ProductionAccessService (role+department)
  ✅ Sipariş           : SalesOrder (header only — line yok)
  ✅ Sevkiyat          : Shipment (header only — line yok)
  ✅ Fatura            : Invoice (AR/AP lifecycle)
  ✅ HR                : Employee, Compliance, Leave, Payroll
  ✅ Exception         : Production exception hiyerarşisi

EKSİK (implemente edilecek):
  ❌ 40+ entity/özellik — aşağıdaki fazlarda sıralanmış
```

---

## Bağımlılık Haritası

```
Seviye 0 — Temel (✅ TAMAMLANMIŞ)
  BaseEntity, User, Department, TradingPartner, Material, Fiber
  Batch, WarehouseLocation, ProductionAccessService

Seviye 1 — Üretim Tanım (Recipe bağımsız çalışabilir)
  Recipe ← Fiber, Material
  Batch.sourceType ← Batch (mevcut, alan ekleme)

Seviye 2 — Üretim Planlama (Recipe gerekli)
  WorkOrder ← Recipe, TradingPartner, Department
  GoodsReceipt ← Batch, WarehouseLocation

Seviye 3 — Sipariş Detay (WorkOrder gerekli)
  SalesOrderLine ← SalesOrder (mevcut), Material, WorkOrder
  
Seviye 4 — Tedarik (WorkOrder gerekli)
  PurchaseOrder ← WorkOrder, TradingPartner
  SubcontractOrder ← WorkOrder, TradingPartner

Seviye 5 — Maliyet (PriceList bağımsız, CostCalc WorkOrder/Batch gerekli)
  PriceList, CostItem ← Material
  CostCalculation ← PriceList + WorkOrder/Batch

Seviye 6 — Satış Genişletme (SalesOrderLine gerekli)
  Quote ← TradingPartner, Material, CostCalculation (ESTIMATED)
  ProductCatalog ← Material
  DiscountPolicy ← CostCalculation

Seviye 7 — Operasyon (tüm event kaynakları gerekli)
  FlowBoard ← SalesOrder, WorkOrder, Batch, GoodsReceipt event'leri
  NotificationHub ← i18n + tüm modül event'leri

Seviye 8 — Kontrol (FlowBoard/NotificationHub gerekli)
  ApprovalSystem ← FlowBoard (APPROVAL task), NotificationHub
  IWM genişletme ← GoodsReceipt, SalesOrder, Batch event'leri

Seviye 9 — İleri (tüm temel akışlar gerekli)
  Offline sync ← SalesOrder, Quote, ProductCatalog
  Cari Hesap ← Invoice, PO, SO, RMA
```

---

## FAZ 0 — Mevcut Kod İyileştirme
**Süre tahmini: 1 hafta**  
**Bağımlılık: Yok — hemen başlanabilir**  
**İş değeri: Altyapı sağlamlaştırma — ilerideki fazlar için temel**

Bu faz yeni entity eklemiyor — mevcut kodu dökümanlarla uyumlu hale getiriyor.

### 0.1 — Batch.sourceType/sourceId Alan Ekleme
> **Döküman:** `02-production/batch-production.md`  
> **İş:** Mevcut Batch entity'ye 2 alan ekleme + Flyway migration  
> **Risk:** Düşük — mevcut davranışı bozmaz, nullable alanlar

```
Batch entity'ye ekle:
  sourceType: BatchSourceType (Enum) — nullable
  sourceId: UUID — nullable

Flyway migration:
  ALTER TABLE production.production_execution_batch 
    ADD COLUMN source_type VARCHAR(50),
    ADD COLUMN source_id UUID;
```

### 0.2 — Exception Genişletme
> **Döküman:** `11-cross-cutting/exception-strategy.md`  
> **İş:** Ortak DomainException base + 4 modül base exception  
> **Risk:** Düşük — mevcut ProductionDomainException'a dokunmaz

```
Yeni dosyalar:
  common/infrastructure/exception/DomainException.java (abstract base)
  order/common/exception/OrderDomainException.java
  logistics/common/exception/LogisticsDomainException.java
  finance/common/exception/FinanceDomainException.java
  human/common/exception/HumanDomainException.java

Mevcut güncelleme:
  ProductionDomainException extends DomainException (geriye uyumlu)
  GlobalExceptionHandler'a DomainException handler ekle
```

### 0.3 — WarehouseLocation Birleştirme Hazırlığı
> **Döküman:** `05-iwm/location.md`  
> **İş:** Mevcut WarehouseLocation'a SITE seviyesi + warehouseType ekleme  
> **Risk:** Orta — mevcut location tipleri etkilenmez, yeni alanlar nullable

```
Batch entity'ye ekle:
  warehouse_type VARCHAR(20) — nullable (RAW/FINISHED/WIP/REJECT/SAMPLE)
  
SITE tipi LocationType enum'a eklenir — mevcut veriler etkilenmez.
```

---

## FAZ 1 — Üretim Çekirdeği: Recipe + WorkOrder
**Süre tahmini: 2-3 hafta**  
**Bağımlılık: Faz 0 tamamlanmış olmalı**  
**İş değeri: ÇOK YÜKSEK — üretim planlamasının temeli, şu an hiç yok**

Bu faz SalesOrder → WorkOrder → Batch üretim zincirini kuruyor.

### 1.1 — Recipe + RecipeComponent
> **Döküman:** `02-production/recipe.md`  
> **Bağımlılık:** Material ✅, Fiber ✅  
> **İş değeri:** Yüksek — blend tanımı olmadan üretim planlanamaz

```
Yeni entity'ler:
  Recipe (production.prod_recipe)
  RecipeComponent (production.prod_recipe_component)

Servisler:
  RecipeService — CRUD, versiyonlama, otomatik name/isoCode üretimi
  RecipeMatchingService — eşleşme mantığı (duplicate kontrol)

API:
  /api/production/recipes — CRUD, components, version history

Yetki: ProductionAccessService'e RECIPE modülü eklenir
```

### 1.2 — WorkOrder + WorkOrderAssignee
> **Döküman:** `02-production/work-order.md`  
> **Bağımlılık:** Recipe (1.1), TradingPartner ✅, Department ✅  
> **İş değeri:** ÇOK YÜKSEK — üretim emri olmadan üretim yönetilemez

```
Yeni entity'ler:
  WorkOrder (production.prod_work_order)
  WorkOrderAssignee (production.prod_work_order_assignee)

Servisler:
  WorkOrderService — CRUD, status geçişleri, supplier snapshot
  WorkOrderAssigneeService — atama yönetimi

API:
  /api/production/work-orders — CRUD, lifecycle, assignees

Status machine: DRAFT → PENDING_APPROVAL → APPROVED → SENT → IN_PROGRESS → COMPLETED
Event'ler: WorkOrderApproved, WorkOrderDeadlineSet

Yetki: ProductionAccessService'e WORK_ORDER modülü eklenir
```

### 1.3 — WorkOrder ↔ Batch Entegrasyonu
> **Döküman:** `02-production/work-order.md` + `02-production/batch-production.md`  
> **Bağımlılık:** WorkOrder (1.2), Batch ✅  
> **İş değeri:** Yüksek — üretim emri → batch tüketim/çıktı zinciri

```
Entegrasyon:
  WorkOrder IN_PROGRESS → Recipe'ye göre hammadde Batch'leri consume
  Üretim çıktısı → yeni Batch (sourceType=INTERNAL_PRODUCTION, sourceId=WO.id)
  BatchLineage kaydı → parent→child
  Attribute inheritance → child attributes hesaplama

Bu faz mevcut Batch servisini kullanır — yeni entity yok, entegrasyon kodu.
```

---

## FAZ 2 — Sipariş Detay: SalesOrderLine + RuleEngine
**Süre tahmini: 2 hafta**  
**Bağımlılık: Faz 1 tamamlanmış olmalı**  
**İş değeri: YÜKSEK — müşteri siparişi → üretim zinciri**

### 2.1 — SalesOrderLine
> **Döküman:** `03-sales/sales-order.md`  
> **Bağımlılık:** SalesOrder ✅ (mevcut), Material ✅, WorkOrder (Faz 1)  
> **İş değeri:** Yüksek — sipariş kalem seviyesi olmadan ne üretileceği bilinmez

```
Mevcut SalesOrder modülüne eklenen:
  SalesOrderLine entity (order.sales.domain)
  moduleType alanı SalesOrder'a eklenir
  moduleSpecs JSONB — SalesOrderLine'da

SalesOrderLine alanları:
  salesOrderId, materialId, productDesc, requestedQty, unit
  unitPrice, currency, moduleSpecs (JSONB), lineStatus

LineStatus: PENDING → RECIPE_ASSIGNED → IN_PRODUCTION → COMPLETED → IN_WAREHOUSE → SHIPPED
```

### 2.2 — RuleEngine: SalesOrder → WorkOrder Zinciri
> **Döküman:** `03-sales/sales-order.md` (RuleEngine bölümü)  
> **Bağımlılık:** SalesOrderLine (2.1), Recipe (Faz 1), WorkOrder (Faz 1)  
> **İş değeri:** Yüksek — otomatik üretim planlama

```
SalesOrderConfirmed eventi yayınlanır
    ↓
RuleEngine her SalesOrderLine için:
  1. Recipe eşleştirme (4 adımlı kaskad)
  2. WorkOrder taslağı oluştur (DRAFT)
  3. salesOrderLineId = SalesOrderLine.id
  4. plannedQty ← requestedQty

Bu faz deterministic — AI yok, kural bazlı.
```

### 2.3 — Order Exception + Yetkilendirme
> **Döküman:** `11-cross-cutting/exception-strategy.md` + `11-cross-cutting/auth-strategy.md`  
> **İş:** OrderDomainException alt sınıfları + OrderAccessService

```
Exception: InvalidOrderStateException, OrderLineValidationException
AccessService: OrderAccessService — Sales/Marketing, Procurement department'ları
```

---

## FAZ 3 — Dış Alım: GoodsReceipt + PurchaseOrder
**Süre tahmini: 2-3 hafta**  
**Bağımlılık: Faz 1 tamamlanmış olmalı (Faz 2 paralel çalışabilir)**  
**İş değeri: YÜKSEK — tedarikçiden mal alımı**

### 3.1 — GoodsReceipt + GoodsReceiptItem
> **Döküman:** `02-production/goods-receipt.md`  
> **Bağımlılık:** Batch ✅, WarehouseLocation ✅  
> **İş değeri:** Yüksek — fiziksel teslim alma kaydı olmadan stok girişi yapılamaz

```
Yeni entity'ler:
  GoodsReceipt (production.goods_receipt)
  GoodsReceiptItem (production.goods_receipt_item)

Akış: GoodsReceipt CONFIRMED → yeni Batch oluşur (sourceType=PURCHASE)
Barkod üretimi: BCH-{batchNumber}-{sıra}
IWM tetikleyici: GoodsReceiptConfirmed event
```

### 3.2 — PurchaseOrder + PurchaseOrderLine
> **Döküman:** `04-procurement/purchase-order.md`  
> **Bağımlılık:** WorkOrder (Faz 1), TradingPartner ✅, GoodsReceipt (3.1)  
> **İş değeri:** Yüksek — tedarikçi sipariş yönetimi

```
Yeni entity'ler:
  PurchaseOrder (procurement.purchase_order)
  PurchaseOrderLine (procurement.purchase_order_line)

Status: DRAFT → SENT → CONFIRMED → PARTIALLY_RECEIVED → RECEIVED → CLOSED
WorkOrder.fulfillmentType = PURCHASE → PurchaseOrder oluşturulur
GoodsReceipt CONFIRMED → PO status güncellenir
```

### 3.3 — SubcontractOrder
> **Döküman:** `04-procurement/subcontract-order.md`  
> **Bağımlılık:** WorkOrder (Faz 1), TradingPartner ✅, GoodsReceipt (3.1)  
> **İş değeri:** Orta-Yüksek — fason iş yönetimi

```
Yeni entity:
  SubcontractOrder (procurement.subcontract_order)

Hammadde çıkış: SC CONFIRMED → Batch consume (IWM ISSUE)
Geri dönüş: GoodsReceipt CONFIRMED → yeni Batch (sourceType=SUBCONTRACT)
Fire kontrolü: gönderilen vs dönen miktar farkı
```

---

## FAZ 4 — Maliyet Yönetimi
**Süre tahmini: 2 hafta**  
**Bağımlılık: Faz 1 + Faz 3 tamamlanmış olmalı**  
**İş değeri: YÜKSEK — kâr/zarar görünürlüğü**

### 4.1 — Maliyet Tanım: CostItem + CostTemplate + PriceList
> **Döküman:** `06-costing/cost-structure.md` + `06-costing/price-list.md`  
> **Bağımlılık:** Material ✅  
> **İş değeri:** Temel — hesaplama için fiyat verisi gerekli

```
Yeni entity'ler:
  CostItem (costing.cost_item) — 8 global kalem + modül eklentileri
  CostTemplate (costing.cost_template)
  PriceList (costing.price_list)
  PriceListItem (costing.price_list_item) — tradingPartnerId dahil
  VolumePriceBreak (costing.volume_price_break)
```

### 4.2 — CostCalculation: 3 Aşamalı Maliyet
> **Döküman:** `06-costing/cost-calculation.md`  
> **Bağımlılık:** PriceList (4.1), Recipe (Faz 1), WorkOrder (Faz 1), Batch ✅  
> **İş değeri:** Yüksek — tahmini/planlı/gerçek maliyet karşılaştırma

```
Yeni entity'ler:
  CostCalculation (costing.cost_calculation)
  CostCalculationLine (costing.cost_calculation_line)
  ExchangeRateSnapshot (costing.exchange_rate_snapshot)
  CostHistory (costing.cost_history)

3 aşama:
  ESTIMATED → Quote.estimatedUnitCost
  PLANNED → WorkOrder.plannedCost
  ACTUAL → Batch.actualCost

Event: CostVarianceDetected (ileride FlowBoard tetikler)
```

---

## FAZ 5 — Satış Genişletme: Quote + Katalog
**Süre tahmini: 2-3 hafta**  
**Bağımlılık: Faz 2 + Faz 4 tamamlanmış olmalı**  
**İş değeri: YÜKSEK — saha satış + fiyat teklifi**

### 5.1 — ProductCatalog
> **Döküman:** `03-sales/product-catalog.md`  
> **Bağımlılık:** Material ✅  
> **İş değeri:** Orta — katalog olmadan Quote oluşturulabilir ama UX zayıf

### 5.2 — DiscountPolicy + Fiyat Hesaplama
> **Döküman:** `03-sales/discount-policy.md`  
> **Bağımlılık:** CostCalculation (Faz 4), ProductCatalog (5.1)  
> **İş değeri:** Yüksek — kâr marjı koruması

### 5.3 — Quote + QuoteLine + QuoteApprovalToken
> **Döküman:** `03-sales/quote-approval.md`  
> **Bağımlılık:** DiscountPolicy (5.2), TradingPartner ✅, CostCalculation (Faz 4)  
> **İş değeri:** Yüksek — müşteriye fiyat teklifi

### 5.4 — SampleRequest + SampleDelivery
> **Döküman:** `03-sales/sample-management.md`  
> **Bağımlılık:** Material ✅, TradingPartner ✅  
> **İş değeri:** Orta — numune yönetimi

---

## FAZ 6 — Tedarik Genişletme: RFQ + Supplier Portal
**Süre tahmini: 2 hafta**  
**Bağımlılık: Faz 3 tamamlanmış olmalı**  
**İş değeri: ORTA — anlaşmalı tedarikçi direkt PO ile çalışabilir, RFQ opsiyonel**

### 6.1 — SupplierRFQ + SupplierRFQRecipient + SupplierRFQLine
> **Döküman:** `04-procurement/supplier-rfq.md`

### 6.2 — SupplierQuote + SupplierQuoteLine + SupplierQuoteToken
> **Döküman:** `04-procurement/supplier-quote.md`

### 6.3 — Tedarikçi Portal Akışı
> E-posta + portal üzerinden teklif alma

---

## FAZ 7 — Bildirim & i18n Altyapısı
**Süre tahmini: 2-3 hafta**  
**Bağımlılık: Faz 2 tamamlanmış olmalı (event kaynakları hazır)**  
**İş değeri: YÜKSEK — kullanıcı deneyimi, operasyonel farkındalık**

### 7.1 — i18n Altyapısı
> **Döküman:** `08-notification-i18n/i18n.md`  
> **Bağımlılık:** Yok — bağımsız altyapı  
> **Not:** i18n erken fazda da yapılabilir — ama Faz 7'ye koymamızın sebebi bildirim şablonları ile birlikte anlamlı olması

```
SupportedLocale, TranslationKey, TranslationValue
TenantLocaleConfig, UserLocaleConfig
Başlangıç dilleri: EN, TR
```

### 7.2 — NotificationHub
> **Döküman:** `08-notification-i18n/notification-hub.md`  
> **Bağımlılık:** i18n (7.1), event kaynakları (Faz 1-5'ten event'ler)

```
NotificationTemplate, NotificationQueue, NotificationLog
UserNotificationPreference
İlk kanal: IN_APP (WebSocket)
İkinci kanal: EMAIL
47 event entegrasyonu (aşamalı — önce kritik olanlar)
```

---

## FAZ 8 — FlowBoard: Operasyonel Görev Yönetimi
**Süre tahmini: 3-4 hafta**  
**Bağımlılık: Faz 7 tamamlanmış olmalı (bildirim), Faz 1-3 (event kaynakları)**  
**İş değeri: ÇOK YÜKSEK — işletmenin operasyonel nabzı**

### 8.1 — Board + Task + TaskAssignee
> **Döküman:** `07-flowboard/board-task.md`

```
Board (modül bazlı), Task (14 TaskType), TaskAssignee (3 atama yolu)
WIP limiti, PriorityScore, Kanban akışı
```

### 8.2 — SmartTaskGenerator + TaskTemplate
> **Döküman:** `07-flowboard/smart-task-generator.md`

```
Event → TaskTemplate eşleşme → Task oluştur → ata → bildirim gönder
Stok kontrol motoru (SalesOrderConfirmed için)
Otomatik bağımlılıklar (PLANNING → PRODUCTION → QUALITY → WAREHOUSE → SHIPMENT)
```

### 8.3 — Task Detayları
> **Döküman:** `07-flowboard/task-details.md` + `07-flowboard/escalation.md`

```
TaskChecklist, TaskComment, TaskActivityLog, TaskDependency
EscalationLog — 4 kural (deadline, untouched, blocked, unassigned)
```

### 8.4 — Performans + RecurringTasks
> **Döküman:** `07-flowboard/performance.md` + `07-flowboard/recurring-tasks.md`

```
UserPerformanceSnapshot (haftalık job)
RecurringTaskTemplate — "bir önceki kapanınca yenisi açılsın"
Manager Dashboard (5 panel)
```

---

## FAZ 9 — Onay Sistemi
**Süre tahmini: 1-2 hafta**  
**Bağımlılık: Faz 8 (FlowBoard APPROVAL task'ı), Faz 7 (NotificationHub)**  
**İş değeri: YÜKSEK — yeni kullanıcı denetimi, güvenlik**

### 9.1 — UserTrustLevel + ApprovalPolicy
> **Döküman:** `09-approval/trust-level-policy.md`

### 9.2 — ApprovalRequest + UserPromotionRequest
> **Döküman:** `09-approval/approval-request.md`

```
User.trustLevel eklenir
ApprovalPolicy tenant yapılandırması
ApprovalRequest → WorkOrder PENDING_APPROVAL akışı
UserPromotionRequest → eskalasyon zinciri (3 red → askıya)
```

---

## FAZ 10 — IWM Genişletme
**Süre tahmini: 2-3 hafta**  
**Bağımlılık: Faz 3 (GoodsReceipt), Faz 2 (SalesOrder events)**  
**İş değeri: ORTA-YÜKSEK — Production Inventory zaten çalışıyor, IWM ek katman**

### 10.1 — Lokasyon Birleştirme (Şema Geçişi)
> **Döküman:** `05-iwm/location.md`  
> **İş:** production → iwm şema geçişi

### 10.2 — StockReservation + Lot Seçim Motoru
> **Döküman:** `05-iwm/stock-reservation.md`

### 10.3 — MinStockRule + LotEndRule
> **Döküman:** `05-iwm/stock-rules.md`

### 10.4 — StockCount + StockTransfer
> **Döküman:** `05-iwm/stock-count.md` + `05-iwm/transfer.md`

### 10.5 — RMA + ManualAdjustment
> **Döküman:** `05-iwm/rma.md` + `05-iwm/manual-adjustment.md`

---

## FAZ 11 — Mobil & Offline
**Süre tahmini: 3-4 hafta**  
**Bağımlılık: Faz 5 (Quote, ProductCatalog), Faz 7 (NotificationHub)**  
**İş değeri: YÜKSEK — saha satış, fuar senaryosu**

### 11.1 — Offline Mimari + Sync Stratejisi
> **Döküman:** `10-mobile-offline/offline-sync.md`

### 11.2 — Mobil Uygulama (React Native / Flutter)
> Framework kararı, offline-first mimari

---

## FAZ 12 — Yetkilendirme Standardizasyonu
**Süre tahmini: 1-2 hafta**  
**Bağımlılık: İlgili modüller tamamlanmış olmalı**  
**İş değeri: GÜVENLİK — her modül korumalı olmalı**

### 12.1 — OrderAccessService, FinanceAccessService, LogisticsAccessService
> **Döküman:** `11-cross-cutting/auth-strategy.md`

> **Not:** Bu faz herhangi bir fazdan sonra, ilgili modül tamamlandığında uygulanabilir. Ama en geç production'a çıkmadan önce tamamlanmalı.

---

## Zaman Çizelgesi — Gantt Özeti

```
Hafta  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19 20
       │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │
Faz 0  ████                                                        Altyapı iyileştirme
Faz 1     ████████                                                 Recipe + WorkOrder
Faz 2           ██████                                             SalesOrderLine + RuleEngine
Faz 3           ████████                                           GoodsReceipt + PO + SC
Faz 4                    ██████                                    Maliyet
Faz 5                         ████████                             Quote + Katalog
Faz 6                              ██████                          RFQ + Supplier Portal
Faz 7                                   ████████                   Bildirim + i18n
Faz 8                                        ██████████████        FlowBoard
Faz 9                                                  ██████     Onay Sistemi
Faz 10                                                 ████████   IWM Genişletme
Faz 11                                                      ██████████ Mobil
Faz 12 ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  Yetkilendirme (sürekli)

       MVP ─────────────────┤  V1.0 ──────────────────┤  V2.0 ──┤
```

---

## MVP Tanımı (Faz 0-3 — Hafta 1-8)

MVP = Minimum Viable Product — ilk kullanılabilir versiyon.

**MVP ile yapılabilecekler:**
- Fiber hammadde kataloğu yönetimi ✅ (mevcut)
- Recipe (blend reçetesi) oluşturma
- WorkOrder oluşturma ve takibi (INTERNAL + PURCHASE + SUBCONTRACT)
- Batch üretim yönetimi ✅ (mevcut) + WorkOrder entegrasyonu
- QC test ve otomatik değerlendirme ✅ (mevcut)
- Tedarikçiden mal alımı (PO → GoodsReceipt → Batch)
- Fason iş yönetimi (SC → hammadde çıkış → dönüş)
- Müşteri siparişi (SalesOrder + SalesOrderLine)
- Sipariş → üretim otomatik zinciri (RuleEngine)
- Depo lokasyon yönetimi ✅ (mevcut)
- Stok takibi ✅ (mevcut — Production Inventory)

**MVP ile yapılamayacaklar:**
- Fiyat teklifi (Quote) — Faz 5
- Maliyet hesaplama — Faz 4
- FlowBoard görev yönetimi — Faz 8
- Bildirim sistemi — Faz 7
- Mobil/offline — Faz 11

---

## Risk Değerlendirmesi

| Faz | Risk | Açıklama | Mitigasyon |
|---|---|---|---|
| Faz 1 | Orta | Recipe versiyonlama karmaşıklığı | İlk sürümde basit tut — sadece ACTIVE/ARCHIVED |
| Faz 2 | Yüksek | RuleEngine recipe eşleştirme | 4 adımlı kaskad — Adım 4 (fallback) her zaman çalışır |
| Faz 3 | Orta | GoodsReceipt polimorfik sourceType | Production exception'lar ile güvenli geçişler |
| Faz 4 | Orta | Döviz kuru + 3 aşamalı maliyet | İlk sürümde tek para birimi (TRY), ileride multi-currency |
| Faz 5 | Yüksek | DiscountPolicy fiyat bölgeleri | Formül doğrulanmış — sıralı kontrol, toplama yok |
| Faz 7 | Orta | 47 event entegrasyonu | Aşamalı — önce CRITICAL event'ler, sonra diğerleri |
| Faz 8 | Yüksek | FlowBoard karmaşıklığı | Faz 8.1 (Board+Task) ile başla, 8.4 (perf) ileride |
| Faz 11 | Yüksek | Offline sync çakışma yönetimi | 4 çakışma tipi tanımlı — manager kararı fallback |

---

## Paralel Çalışma Fırsatları

Bazı fazlar paralel yürütülebilir:

```
Faz 2 (SalesOrderLine) ║ Faz 3 (GoodsReceipt + PO)
  → İkisi de Faz 1'e bağımlı ama birbirinden bağımsız
  → İki geliştirici paralel çalışabilir

Faz 4 (Maliyet) ║ Faz 6 (RFQ)
  → İkisi de Faz 3'e bağımlı ama birbirinden bağımsız

Faz 7 (Bildirim) ║ Faz 10 (IWM)
  → Birbirinden bağımsız
```

---

## Döküman Referansları

Her faz hangi dökümanları kullanır:

| Faz | Dökümanlar |
|---|---|
| 0 | `batch-production.md`, `exception-strategy.md`, `location.md` |
| 1 | `recipe.md`, `work-order.md`, `batch-production.md`, `batch-lineage.md` |
| 2 | `sales-order.md`, `work-order.md` |
| 3 | `goods-receipt.md`, `purchase-order.md`, `subcontract-order.md` |
| 4 | `cost-structure.md`, `price-list.md`, `cost-calculation.md`, `exchange-rate-history.md` |
| 5 | `product-catalog.md`, `discount-policy.md`, `quote-approval.md`, `sample-management.md` |
| 6 | `supplier-rfq.md`, `supplier-quote.md` |
| 7 | `i18n.md`, `notification-hub.md`, `event-catalog.md` |
| 8 | `board-task.md`, `smart-task-generator.md`, `task-details.md`, `escalation.md`, `performance.md`, `recurring-tasks.md` |
| 9 | `trust-level-policy.md`, `approval-request.md` |
| 10 | `location.md`, `stock-reservation.md`, `stock-rules.md`, `stock-count.md`, `transfer.md`, `rma.md`, `manual-adjustment.md` |
| 11 | `offline-sync.md` |
| 12 | `auth-strategy.md`, `production-security.md` |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — 13 faz, bağımlılık haritası, MVP tanımı, Gantt, risk analizi |

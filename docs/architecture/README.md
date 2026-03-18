# Fabric Management Backend — Mimari Dokümantasyon

> Versiyon: 2.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17

---

## Genel Bakış

Bu dizin, Fabric Management Backend sisteminin tüm mimari dökümanlarını içerir. Her döküman **tek bir modüle, yapıya veya akışa** odaklanır. Entity tanımları yalnızca kanonik kaynağında yapılır — diğer dökümanlar referans verir.

### Temel Kurallar

- **Tek kaynak ilkesi:** Her entity yalnızca bir dökümanında tanımlanır.
- **Referans zinciri tek yönlü:** Diğer dökümanlar FK referansı verir, tanım tekrarlamaz.
- **Gerçek kod uyumu:** Dökümanlar implementasyondaki şema, entity ve enum'larla tutarlıdır.

---

## Sistem Mimarisi — Büyük Resim

```
┌─────────────────────────────────────────────────────────────────┐
│                     01 — TEMEL YAPILAR                          │
│   BaseEntity · User · AuthUser · Role · Contact                 │
│   Organization · Department · UserDepartment                    │
│   TradingPartner · TradingPartnerRegistry · PartnerCertification│
│   CertificationType · FiberCategory · FiberIsoCode              │
├─────────────────────────────────────────────────────────────────┤
│                     02 — ÜRETİM ZİNCİRİ                        │
│   Material · Fiber · Recipe · RecipeComponent                   │
│   WorkOrder · WorkOrderAssignee                                 │
│   Batch · GoodsReceipt · GoodsReceiptItem                       │
├─────────────────────────────────────────────────────────────────┤
│                     03 — SATIŞ ZİNCİRİ                         │
│   SalesOrder · SalesOrderLine · Quote · QuoteLine               │
│   QuoteApprovalToken · SampleRequest · SampleDelivery           │
│   DiscountPolicy · ProductCatalog                               │
├─────────────────────────────────────────────────────────────────┤
│                     04 — TEDARİK ZİNCİRİ                       │
│   SupplierRFQ · SupplierRFQLine · SupplierRFQRecipient          │
│   SupplierQuote · SupplierQuoteLine · SupplierQuoteToken        │
│   PurchaseOrder · PurchaseOrderLine                             │
│   SubcontractOrder                                              │
├─────────────────────────────────────────────────────────────────┤
│                     05 — ENVANTER & DEPO (IWM)                  │
│   Location · StockTransaction · StockLedger · StockReservation  │
│   MinStockRule · LotEndRule · ReturnRateRule                    │
│   StockCount · StockCountLine · StockAdjustment                 │
│   StockTransfer · RMA · StockAdjustmentRequest                  │
├─────────────────────────────────────────────────────────────────┤
│                     06 — MALİYET YÖNETİMİ                      │
│   CostItem · CostTemplate · PriceList · PriceListItem           │
│   VolumePriceBreak · CostCalculation · CostCalculationLine      │
│   ExchangeRateSnapshot · CostHistory                            │
├─────────────────────────────────────────────────────────────────┤
│                     07 — FLOWBOARD (Görev Yönetimi) — v2.0       │
│   Board · BoardGroup · BoardView · Task · TaskAssignee            │
│   TaskLabel · TaskLabelAssignment · TaskChecklist · TaskComment    │
│   TaskActivityLog · TaskTemplate · TaskDependency · TaskRelation   │
│   TaskTimeEntry · TaskAttachment · TaskReminder · AutomationRule   │
│   RecurringTaskTemplate · EscalationLog                           │
│   DashboardConfig · DashboardWidget · UserPerformanceSnapshot     │
├─────────────────────────────────────────────────────────────────┤
│                     08 — BİLDİRİM & i18n                        │
│   SupportedLocale · TranslationKey · TranslationValue           │
│   TenantLocaleConfig · UserLocaleConfig                         │
│   NotificationTemplate · NotificationQueue · NotificationLog    │
│   UserNotificationPreference                                    │
├─────────────────────────────────────────────────────────────────┤
│                     09 — ONAY SİSTEMİ                           │
│   UserTrustLevel · ApprovalPolicy · ApprovalRequest             │
│   UserPromotionRequest                                          │
├─────────────────────────────────────────────────────────────────┤
│                     10 — MOBİL & OFFLİNE                        │
│   Offline sync stratejisi · 4 çakışma tipi                      │
├─────────────────────────────────────────────────────────────────┤
│                     11 — CROSS-CUTTİNG                          │
│   Event Kataloğu · Status Enum Kataloğu                         │
│   Polimorfik FK Kuralları · JSONB Stratejisi                    │
│   Cari Hesap İskelet                                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## Döküman Dizini

### 01-foundations/ — Temel Yapılar

| Döküman | Kapsam | Entity'ler |
|---|---|---|
| `base-entity.md` | Tüm entity'lerin ortak alanları | BaseEntity, multi-tenancy, soft delete, UID formatı |
| `reference-tables.md` | Sistem tanımlı referans verileri | CertificationType, FiberCategory, FiberIsoCode |
| `user-auth.md` | Kullanıcı ve kimlik doğrulama | User, AuthUser, Role, Contact, UserContact, UserType |
| `organization-department.md` | Organizasyon yapısı | Organization, Department, UserDepartment |
| `trading-partner.md` | İş ortakları | TradingPartner, TradingPartnerRegistry, PartnerCertification |

### 02-production/ — Üretim Zinciri

| Döküman | Kapsam | Entity'ler |
|---|---|---|
| `material-fiber.md` | Hammadde tanımları | Material, Fiber |
| `recipe.md` | Üretim reçeteleri | Recipe, RecipeComponent |
| `work-order.md` | İş emirleri | WorkOrder, WorkOrderAssignee |
| `batch-production.md` | Üretim yürütme | Batch |
| `goods-receipt.md` | Teslim alma | GoodsReceipt, GoodsReceiptItem |

### 03-sales/ — Satış Zinciri

| Döküman | Kapsam | Entity'ler |
|---|---|---|
| `sales-order.md` | Müşteri siparişleri | SalesOrder, SalesOrderLine |
| `quote-approval.md` | Fiyat teklifi | Quote, QuoteLine, QuoteApprovalToken |
| `sample-management.md` | Numune yönetimi | SampleRequest, SampleDelivery |
| `discount-policy.md` | İndirim & fiyat kuralları | DiscountPolicy |
| `product-catalog.md` | Ürün kataloğu | ProductCatalog |

### 04-procurement/ — Tedarik Zinciri

| Döküman | Kapsam | Entity'ler |
|---|---|---|
| `supplier-rfq.md` | Fiyat teklifi talebi | SupplierRFQ, SupplierRFQLine, SupplierRFQRecipient |
| `supplier-quote.md` | Tedarikçi teklifi | SupplierQuote, SupplierQuoteLine, SupplierQuoteToken |
| `purchase-order.md` | Satın alma siparişi | PurchaseOrder, PurchaseOrderLine |
| `subcontract-order.md` | Fason iş siparişi | SubcontractOrder |

### 05-iwm/ — Envanter & Depo

| Döküman | Kapsam | Entity'ler |
|---|---|---|
| `location.md` | Lokasyon hiyerarşisi | Location |
| `stock-transaction-ledger.md` | Stok hareketleri | StockTransaction, StockLedger |
| `stock-reservation.md` | Stok ayırma & lot seçimi | StockReservation, lot seçim motoru, FIFO/FEFO |
| `stock-rules.md` | Stok kuralları | MinStockRule, LotEndRule, ReturnRateRule |
| `stock-count.md` | Stok sayımı | StockCount, StockCountLine, StockCountAssignee, StockCountTolerance |
| `transfer.md` | Depolar arası transfer | StockTransfer |
| `rma.md` | İade yönetimi | RMA |
| `manual-adjustment.md` | Manuel düzeltme | StockAdjustmentRequest |

### 06-costing/ — Maliyet Yönetimi

| Döküman | Kapsam | Entity'ler |
|---|---|---|
| `cost-structure.md` | Maliyet tanımları | CostItem, CostTemplate |
| `price-list.md` | Fiyat listeleri | PriceList, PriceListItem, VolumePriceBreak |
| `cost-calculation.md` | 3 aşamalı maliyet hesabı | CostCalculation, CostCalculationLine |
| `exchange-rate-history.md` | Döviz kuru & tarihsel kayıt | ExchangeRateSnapshot, CostHistory |

### 07-flowboard/ — Görev Yönetimi (v2.0 — Monday.com İlhamlı)

| Döküman | Kapsam | Entity'ler |
|---|---|---|
| `board-task.md` | Board, grup, görünüm, etiket yapısı | Board, BoardGroup, BoardView, Task, TaskAssignee, TaskLabel, TaskLabelAssignment |
| `smart-task-generator.md` | Event → task zinciri + otomasyon | SmartTaskGenerator, TaskTemplate, AutomationRule |
| `task-details.md` | Task detayları, zaman, dosya, ilişki | TaskChecklist, TaskComment, TaskActivityLog, TaskDependency, TaskTimeEntry, TaskAttachment, TaskReminder, TaskRelation |
| `recurring-tasks.md` | Tekrarlayan görevler | RecurringTaskTemplate |
| `escalation.md` | Eskalasyon (6 kural, 3 seviye) | EscalationLog |
| `performance.md` | Performans, dashboard, workload | UserPerformanceSnapshot, DashboardConfig, DashboardWidget |

### 08-notification-i18n/ — Bildirim & Çok Dil

| Döküman | Kapsam | Entity'ler |
|---|---|---|
| `i18n.md` | Çok dil altyapısı | SupportedLocale, TranslationKey, TranslationValue, TenantLocaleConfig, UserLocaleConfig |
| `notification-hub.md` | Merkezi bildirim | NotificationTemplate, NotificationQueue, NotificationLog, UserNotificationPreference |

### 09-approval/ — Onay Sistemi

| Döküman | Kapsam | Entity'ler |
|---|---|---|
| `trust-level-policy.md` | Güven seviyesi & politika | UserTrustLevel, ApprovalPolicy |
| `approval-request.md` | Onay talepleri | ApprovalRequest, UserPromotionRequest |

### 10-mobile-offline/ — Mobil & Offline

| Döküman | Kapsam |
|---|---|
| `offline-sync.md` | Offline mimari, sync stratejisi, 4 çakışma tipi |

### 11-cross-cutting/ — Modüller Arası

| Döküman | Kapsam |
|---|---|
| `event-catalog.md` | Tüm event'lerin tek listesi — kaynak, alıcı, önem |
| `status-enum-catalog.md` | Tüm status enum'larının tek listesi — geçiş kuralları |
| `polymorphic-fk-rules.md` | Polimorfik FK kuralları ve validation |
| `jsonb-strategy.md` | JSONB kullanım kuralları ve index stratejisi |
| `cari-hesap-iskelet.md` | Cari Hesap iskelet tasarımı (placeholder) |

---

## Modüller Arası Event Akışı

```
SalesOrder CONFIRMED
      ↓
      ├──→ FlowBoard: SmartTaskGenerator → PLANNING task
      ├──→ NotificationHub: SALES_ORDER_CONFIRMED
      └──→ RuleEngine: WorkOrder taslağı oluşturur

WorkOrder APPROVED
      ↓
      ├──→ FlowBoard: SmartTaskGenerator → PRODUCTION task
      └──→ NotificationHub: WORKORDER_APPROVED

Batch QC_FAILED
      ↓
      ├──→ FlowBoard: SmartTaskGenerator → QUALITY task
      └──→ NotificationHub: BATCH_QC_FAILED (CRITICAL)

GoodsReceipt CONFIRMED
      ↓
      ├──→ IWM: StockTransaction RECEIPT
      ├──→ FlowBoard: SmartTaskGenerator → WAREHOUSE task
      └──→ NotificationHub: bildirim

SupplierQuote RECEIVED
      ↓
      └──→ NotificationHub: SUPPLIER_QUOTE_RECEIVED

MinStockRule tetiklendi
      ↓
      ├──→ FlowBoard: PROCUREMENT task
      └──→ NotificationHub: MIN_STOCK_ALERT

CostVariance tespit edildi
      ↓
      ├──→ FlowBoard: COSTING task
      └──→ NotificationHub: COST_VARIANCE_DETECTED
```

> **Tam event listesi:** `11-cross-cutting/event-catalog.md`

---

## Açık Modüller (Henüz Döküman Yok)

| Modül | Durum | Bağımlılık |
|---|---|---|
| Yarn | Planlandı | Material-Fiber modülü tamamlanacak |
| Fabric | Planlandı | Yarn modülü referans |
| Dye/Finishing | Planlandı | Fabric modülü referans |
| HR | Planlandı | User + Department tamamlandı |
| Abonelik / Tenant | Kısmen var | FlowBoard Board aktivasyonu bekliyor |
| QC (Kalite Kontrol) | Araştırma aşaması | Modül bazında ayrı tasarlanacak |
| Raporlama | Planlandı | Tüm modüller tamamlandıktan sonra |
| Cari Hesap | İskelet hazır | Tüm modüllerle entegre |

---

## Genel Açık Kararlar

Bu kararlar birden fazla dökümanı etkiliyor.

- [ ] Abonelik modülü tamamlandığında FlowBoard Board aktivasyonu bağlanacak
- [ ] QC tasarımı her modül için ayrı yapılacak
- [ ] Push notification altyapısı — Firebase mi, APNs mi?
- [ ] E-posta servisi — SendGrid mi, AWS SES mi?
- [ ] WebSocket gerçek zamanlı bildirim kapsamı
- [ ] Mobil uygulama framework kararı — React Native mi, Flutter mı?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 2.0 | 2026-03-17 | Tam yeniden yapılandırma — tek sorumluluk prensibi, 37 döküman, 11 kategori |
| 1.0 | 2026-03-16 | İlk index — 7 döküman |

---

## Ek Modüller (Gerçek Koddan Türetilmiş)

### 02-production/ — Genişletilmiş Üretim Zinciri

| Döküman | Kapsam | Durum |
|---|---|---|
| `batch-lineage.md` | **YENİ** — İzlenebilirlik, attribute inheritance | Gerçek koddan |
| `inventory.md` | **YENİ** — CQRS stok hareketleri (InventoryTransaction + InventoryBalance) | Gerçek koddan |
| `quality-fiber.md` | **YENİ** — Fiber QC test, otomatik değerlendirme, event-driven batch update | Gerçek koddan |
| `warehouse-location.md` | **YENİ** — Gerçek WarehouseLocation (kapasite, barkod, AISLE, PRODUCTION_LINE) | Gerçek koddan |
| `production-security.md` | **YENİ** — ProductionAccessService yetkilendirme matrisi | Gerçek koddan |
| `fiber-request.md` | **YENİ** — Tenant'ın platforma yeni lif talebi | Gerçek koddan |
| `batch-production.md` | **V2.0** — Evrensel Batch modeli, gerçek state machine | Yeniden yazıldı |

### 12-human/ — HR Modülü

| Döküman | Kapsam |
|---|---|
| `human-overview.md` | Employee, Compliance (ülke policy pack), Leave (izin), Payroll (bordro + ülke stratejileri) |

### 13-finance/ — Finans Modülü

| Döküman | Kapsam |
|---|---|
| `finance-invoice.md` | Invoice — AR/AP, lifecycle, vade takibi, kısmi ödeme |

### 14-logistics/ — Lojistik Modülü

| Döküman | Kapsam |
|---|---|
| `logistics-shipment.md` | Shipment — gelen/giden, kargo takibi, teslimat kanıtı |

# SalesOrder — Müşteri Siparişi

> Modül: Satış Zinciri (03-sales)
> Versiyon: 2.0 | Durum: Aktif
> Son güncelleme: 2026-06-01
> **Bu doküman mevcut implementasyonu yansıtır. Gerçeğin kaynağı koddur; çelişki halinde `SalesOrder` / `SalesOrderLine` entity'leri esastır.**

---

## Genel Bakış

SalesOrder müşteri siparişlerini yönetir. `SalesOrder → SalesOrderLine → WorkOrder` zinciri üretim planlamasının tetikleyicisidir. Hem masaüstü hem mobil platformlardan oluşturulabilir.

Tablolar: `sales_ord.sales_order`, `sales_ord.sales_order_line`. Her ikisi `BaseEntity`'den miras alır (tenant izolasyonu, soft-delete, optimistic locking `version`).

---

## 1. SalesOrder

> Tablo: `sales_ord.sales_order`

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `tradingPartnerId` | UUID | Evet | FK → TradingPartner. SALES'te müşteri, PURCHASE'te tedarikçi. |
| `orderNumber` | String | Evet | **Otomatik** — `DocumentNumberGenerator` (ör. `SO-20260202-00001`) |
| `customerReference` | String | Hayır | Müşterinin kendi PO referansı |
| `orderType` | OrderType (Enum) | Evet | SALES (varsayılan) / PURCHASE |
| `status` | OrderStatus (Enum) | Evet | Bkz. [Status Akışı](#status-akışı) |
| `orderDate` | Date | Evet | Sipariş tarihi |
| `requestedDeliveryDate` | Date | Hayır | Müşterinin talep ettiği teslim |
| `promisedDeliveryDate` | Date | Hayır | Bizim taahhüt ettiğimiz teslim |
| `actualDeliveryDate` | Date | Hayır | Gerçekleşen teslim (deliver ile set edilir) |
| `deadline` | Date | Hayır | Tüm satırlar için son teslim |
| `totals` | OrderTotals (embedded) | Evet | `total` + `tax` + `discount` + `currency`. **Satırlardan hesaplanır** (input'a güvenilmez). Varsayılan currency `TRY`. |
| `moduleType` | ModuleType (Enum) | Hayır | FIBER / YARN / FABRIC / DYE_FINISHING |
| `quoteId` | UUID | Hayır | FK → Quote — dönüşümle geldiyse |
| `sampleRequestId` | UUID | Hayır | FK → SampleRequest — numuneden geldiyse |
| `shippingAddress` / `billingAddress` / `shippingMethod` | String | Hayır | Sevkiyat/fatura bilgisi |
| `statusBeforeHold` | OrderStatus | Hayır | `hold()` öncesi status; `resume()` bununla geri yükler |
| `rejectionReason` | String | Hayır | `reject()` ile saklanır; `reviseRejected()` ile temizlenir |
| `notes` | String (TEXT) | Hayır | Genel notlar |
| `metadata` | JSONB | Hayır | Esnek veri (payment terms, incoterms, onay kanıtı vb.) |
| `offlineMetadata` | embedded | Hayır | Offline oluşturma/sync bilgisi (deviceId, syncedAt) |

> **Not:** Eski dokümandaki `customerId`, `paymentTerms`, `leadTimeDays`, `approvalMethod`, `approvedAt`, `approvedByName`, `approvalEvidence`, `attachments` ayrı kolon **değildir** — müşteri referansı `tradingPartnerId`'dir; ödeme/onay/ek detayları `metadata` (JSONB) içinde tutulur.

### Status Akışı

`OrderStatus` 10 değer içerir:

```
DRAFT, PENDING_APPROVAL, CONFIRMED, IN_PROGRESS,
PARTIALLY_SHIPPED, SHIPPED, DELIVERED, CANCELLED, REJECTED, ON_HOLD
```

> Not: `IN_PRODUCTION`, `IN_WAREHOUSE`, `CLOSED` **yoktur** (eski doküman hatası).

```
DRAFT ──confirm──▶ CONFIRMED ──startProcessing──▶ IN_PROGRESS ──▶ (PARTIALLY_)SHIPPED ──▶ DELIVERED
  │                    ▲                                  ▲
  └─onay gerekirse─▶ PENDING_APPROVAL ──sistem onayı──────┘   (sevkiyatla otomatik)
                          └─red──▶ REJECTED ──reviseRejected──▶ DRAFT

İptal: {DRAFT, PENDING_APPROVAL, CONFIRMED, IN_PROGRESS, ON_HOLD} ──cancel──▶ CANCELLED
Beklet: non-terminal ──hold──▶ ON_HOLD ──resume──▶ (statusBeforeHold)
Terminal: DELIVERED, CANCELLED, REJECTED
```

| Geçiş | Tetikleyen | Otomatik? |
|---|---|---|
| DRAFT → PENDING_APPROVAL | confirm + onay gerekiyorsa (`ApprovalPort`) | Evet |
| DRAFT → CONFIRMED | confirm + onay gerekmiyorsa | Evet |
| PENDING_APPROVAL → CONFIRMED | **yalnızca** sistem approval callback'i (`confirmFromApproval`) | Evet |
| PENDING_APPROVAL → REJECTED | approval reddi | Evet |
| REJECTED → DRAFT | `reviseRejected()` (revizyon + yeniden onay) | Manuel |
| CONFIRMED → IN_PROGRESS | `startProcessing` endpoint | **Manuel** (üretim event'i ile otomasyon henüz yok — bkz. [Açık Kararlar](#açık-kararlar)) |
| CONFIRMED/IN_PROGRESS/PARTIALLY_SHIPPED → PARTIALLY_SHIPPED veya SHIPPED | `ShipmentLineConfirmedEvent` aggregate'i (tüm aktif satırlar sevk → SHIPPED, kısmı → PARTIALLY_SHIPPED) | **Otomatik** |
| SHIPPED → DELIVERED | `deliver` endpoint | Manuel |
| → CANCELLED | `cancel` (canCancel: DRAFT/PENDING_APPROVAL/CONFIRMED/IN_PROGRESS/ON_HOLD) + WorkOrder cascade | Manuel |
| non-terminal → ON_HOLD | `hold()` (`statusBeforeHold` saklanır) | Manuel |
| ON_HOLD → önceki status | `resume()` | Manuel |

> **Onay bypass koruması:** `confirmOrder` yalnızca DRAFT'tan ilerletir; PENDING_APPROVAL → CONFIRMED **sadece** sistem callback'inden (`confirmOrderAsSystem` → `confirmFromApproval`) gelir. Kullanıcının onay bekleyen siparişi manuel confirm etmesi 409 ile reddedilir.

### Event'ler

| Event | Ne zaman | Alıcı |
|---|---|---|
| `SalesOrderConfirmedEvent` | → CONFIRMED | Production (`WorkOrderSalesEventListener` → WorkOrder DRAFT), FlowBoard, RuleEngine |
| `SalesOrderCancelledEvent` | → CANCELLED | Production (`WorkOrderSalesEventListener` → COMPLETED olmayan WorkOrder'ları CANCELLED yapar) |
| `ShipmentLineConfirmedEvent` (gelen) | logistics sevkiyat onayı | Sales (`ShipmentProgressService`): satır `shippedQty` + header PARTIALLY_SHIPPED/SHIPPED |

> Eski dokümandaki `SalesOrderInWarehouse`, `ProductStoredEvent`, `ShipmentDispatchedEvent` **kodda yoktur**. Sevkiyat sinyali satır bazlı `ShipmentLineConfirmedEvent`'tir; header status bu satır event'leri aggregate edilerek türetilir.

---

## 2. SalesOrderLine

> Tablo: `sales_ord.sales_order_line`

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `salesOrderId` | UUID | Evet | FK → SalesOrder |
| `productId` | UUID | Hayır | FK → ürün (katalogdan seçildiyse) |
| `productDesc` | String (TEXT) | Hayır | Serbest ürün tanımı (katalogda yoksa) |
| `requestedQty` | Decimal(15,3) | Evet | Talep edilen miktar. **Aktif satırda > 0 zorunlu** (domain guard) |
| `shippedQty` | Decimal(15,3) | Evet | Sevk edilen miktar (varsayılan 0; sevkiyat event'leriyle artar) |
| `unit` | String | Evet | KG / MT / PIECE vb. |
| `unitPrice` | Money (embedded) | Hayır | `amount` + `currency`. **Currency = sipariş currency'si** (zorunlu eşleşme) |
| `processedShipmentLineIds` | Set\<UUID\> | — | Idempotency anahtarı (aynı sevkiyat satırı iki kez sayılmaz) |
| `moduleType` | ModuleType | Hayır | FIBER / YARN / FABRIC / DYE_FINISHING |
| `moduleSpecs` | JSONB | Hayır | Modüle özel alanlar — bkz. [ModuleSpecs](#modulespecs-jsonb-şemaları) |
| `lineStatus` | SalesOrderLineStatus | Evet | Bkz. [LineStatus](#linestatus-akışı) |
| `recipeId` | UUID | Hayır | RuleEngine veya manuel atama sonrası |

**Validasyon:** `productId` veya `productDesc` — en az biri zorunlu. Aktif satırda `requestedQty > 0`. Satır currency'si sipariş currency'sine eşit olmalı.

> **Tasarım kararı (açık):** Eski doküman `materialId`'ye geçildiğini söylüyordu; **kod hâlâ `productId` + `productDesc` kullanıyor**. Bu çözülmemiş bir karardır — bkz. [Açık Kararlar](#açık-kararlar).

### Domain yardımcıları

- `getRemainingQty()` = `requestedQty − shippedQty` (null-safe).
- `isOverShipped()` = `shippedQty > requestedQty` (null-safe).
- `addShippedQuantity(shipmentLineId, qty)` → `boolean` (yeni=true, duplicate=false). **Hard-throw etmez**; over-shipment fiziksel gerçektir, `ShipmentProgressService` WARN loglar (async sevkiyat event'i yutulmaz).

### LineStatus Akışı

```
PENDING → RECIPE_ASSIGNED → IN_PRODUCTION → COMPLETED → IN_WAREHOUSE → SHIPPED
        ↘ CANCELLED
```

| Geçiş | Tetikleyen | Durum |
|---|---|---|
| PENDING → RECIPE_ASSIGNED | RuleEngine veya manuel recipe atama | **Bağlı (wired)** |
| RECIPE_ASSIGNED → IN_PRODUCTION → COMPLETED → IN_WAREHOUSE → SHIPPED | üretim / IWM event'leri | **Tanımlı, henüz bağlanmadı** (event'ler wire edilmemiş — bkz. [Açık Kararlar](#açık-kararlar)) |

> Dürüstlük notu: Yalnızca `PENDING → RECIPE_ASSIGNED` implement edilmiştir. Zincirin geri kalanı enum'da tanımlı ama tetikleyici event'ler bağlı değildir; "otomatik" olarak sunulmamalıdır.

---

## ModuleSpecs JSONB Şemaları

Her `moduleType` için `moduleSpecs` JSONB alan şeması (`ModuleSpecsValidator` doğrular). `certificationReq` / `originReq` RuleEngine tarafından recipe eşleştirmede kullanılır.

### FIBER
```json
{ "certificationReq": "GOTS", "originReq": "TR", "moq": 500, "leadTimeDays": 21 }
```
### YARN
```json
{ "count": "30/1 Ne", "twist": "Z", "construction": "Ring Spun", "certificationReq": "GOTS" }
```
### FABRIC
```json
{ "weight": "180 g/m²", "width": "150 cm", "weaveType": "Plain", "certificationReq": "GOTS" }
```
### DYE_FINISHING
```json
{ "color": "Pantone 19-4052 TCX", "finish": "Sanforized", "washInstruction": "30°C gentle", "certificationReq": "OEKO-TEX" }
```

> Yeni modül eklenince tabloya dokunulmaz — sadece yeni JSON şeması + backend validasyonu tanımlanır.

---

## RuleEngine — Recipe Eşleştirme

`SalesOrderConfirmed` tetiklendiğinde RuleEngine her PENDING `SalesOrderLine` için 4 adımlı kaskad çalıştırır. Üç eşleştirme adımı da satırın `moduleSpecs`'inden gelen `certificationReq` ve `originReq` ile filtrelenir.

```
Hazırlık — certificationReq / originReq moduleSpecs'ten alınır,
           Locale.ROOT ile normalize edilir (UPPER + trim).

Adım 1 — productId default recipe
  En son ACTIVE recipe (prod_recipe_component.fiber_id = productId),
  certification/origin uyuşuyorsa.

Adım 2 — Müşteri geçmişi
  Aynı (tradingPartner + product) için son kullanılan recipe,
  certification/origin filtreli.

Adım 3 — Frekans
  Bu product için en çok kullanılan ACTIVE recipe (WorkOrder sayısı),
  certification/origin filtreli.

Adım 4 — Fallback
  Eşleşme yok → WorkOrder taslağı recipe'siz açılır,
  FlowBoard'a RECIPE_ASSIGNMENT task'ı düşer.
```

> **Normalizasyon:** `prod_recipe_component.certification` / `origin` hem yazma anında (`@PrePersist`, `Locale.ROOT`) hem de mevcut veri için backfill migration'ı ile normalize edilir; sorgular düz eşitlik kullanır (indexli, case-tutarlı).
>
> **Cert/origin → WorkOrder:** Şu an cert/origin yalnızca eşleştirmede kullanılır; WorkOrder entity'sine henüz map edilmez (`FAB-1025`).

### RuleEngine → WorkOrder Zinciri

```
SalesOrder CONFIRMED
    ↓ SalesOrderConfirmedEvent
WorkOrderSalesEventListener (production), her satır için:
  - DRAFT WorkOrder oluşturur (salesOrderLineId, plannedQty ← requestedQty, deadline ← order.deadline)
  - recipe atandıysa: PLANNING task; atanamadıysa: RECIPE_ASSIGNMENT task
```

> Modül sınırı: Sales, WorkOrder'a doğrudan dokunmaz — yalnızca event yayınlar; production kendi listener'ında tepki verir. İptal cascade'i de aynı şekilde `SalesOrderCancelledEvent` üzerinden gider.

> AI katmanı yok — RuleEngine deterministik kurallara dayanır.

---

## SalesOrder → Sevkiyat / IWM Entegrasyonu

Sales, logistics/IWM iç detaylarını bilmez — yalnızca event dinler.

| Gelen Event | Sales Aksiyonu |
|---|---|
| `ShipmentLineConfirmedEvent` | Satır `shippedQty` += confirmedQty (idempotent); aggregate'e göre header → PARTIALLY_SHIPPED / SHIPPED |

> İki fazlı işlem (`ShipmentProgressService`): Faz 1 satır `shippedQty` (ayrı tx), Faz 2 header status (ayrı tx + `@Retryable` optimistic-lock). Over-shipment WARN'lanır, engellenmez (hard enforcement sevkiyat-oluşturma/logistics tarafına aittir).

---

## İlişki Özeti

```
TradingPartner (CUSTOMER) ──→ SalesOrder ──→ SalesOrderLine (1:N)
                                  │               │
                                  │               ├──→ Product (productId, opsiyonel)
                                  │               ├──→ WorkOrder (1:N, salesOrderLineId)
                                  │               └──→ StockReservation (lot ayırma)
                                  │
                                  ├──→ Quote (opsiyonel)
                                  └──→ SampleRequest (opsiyonel)
```

---

## Açık Kararlar

- [ ] **`productId` vs `materialId`:** Kod `productId` + `productDesc` kullanıyor; eski dokümanın `materialId`/Material hedefi uygulanmadı. Biri seçilip tek kaynak yapılmalı.
- [ ] **Satır-status zinciri + header üretim otomasyonu:** `RECIPE_ASSIGNED → … → SHIPPED` ve `CONFIRMED → IN_PROGRESS`, depo (`IN_WAREHOUSE` benzeri) geçişleri üretim/IWM event'lerine bağlı değil; şu an manuel. (gap)
- [ ] **Sertifika eşleştirme semantiği (any vs all):** Sorgu, recipe'nin *herhangi* bir component'i istenen sertifikaya sahipse eşleştirir. Tüm-component uyumu (ör. %100 GOTS) gerekiyorsa `NOT EXISTS` yapısına çevrilmeli — iş tarafı kararı.
- [ ] **cert/origin → WorkOrder mapping:** `FAB-1025` açık (WorkOrder gereksinimi taşımıyor; fallback manuel atama görevi de gereksinimi görmüyor).
- [ ] **`moduleType` çiftlemesi:** Hem header'da hem satırda; karışık-modüllü sipariş (bazı satır FABRIC, bazı YARN) tek header `moduleType` ile temsil edilemiyor. (gap)

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/trading-partner.md` | SalesOrder.tradingPartnerId → TradingPartner |
| `02-production/work-order.md` | WorkOrder.salesOrderLineId → SalesOrderLine |
| `02-production/recipe.md` | RuleEngine recipe eşleştirme (cert/origin) |
| `03-sales/quote-approval.md` | Quote → SalesOrder dönüşümü |
| `03-sales/sample-management.md` | SampleRequest → SalesOrder |
| `05-iwm/stock-reservation.md` | SalesOrderLine → StockReservation |
| `07-flowboard/smart-task-generator.md` | SalesOrderConfirmed → PLANNING task |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon |
| 2.0 | 2026-06-01 | Kod gerçeğine hizalama: gerçek 10-değerli OrderStatus + geçişler; onay bypass koruması; PARTIALLY_SHIPPED/SHIPPED sevkiyat otomasyonu; iptal/hold(resume)/REJECTED→DRAFT; cert/origin recipe eşleştirme; totals satırlardan hesaplama; qty guard'ları. Uydurma status/event'ler (IN_PRODUCTION, IN_WAREHOUSE, CLOSED, SalesOrderInWarehouse, ProductStoredEvent, ShipmentDispatchedEvent) ve `customerId`/`materialId` çıkarıldı. "Kanonik kaynak" iddiası kaldırıldı. |

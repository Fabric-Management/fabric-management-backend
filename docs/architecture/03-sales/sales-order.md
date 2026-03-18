# SalesOrder — Müşteri Siparişi

> Modül: Satış Zinciri (03-sales)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: SalesOrder ve SalesOrderLine entity'leri burada tanımlanır.

---

## Genel Bakış

SalesOrder müşteri siparişlerini yönetir. `SalesOrder → SalesOrderLine → WorkOrder` zinciri üretim planlamasının tetikleyicisidir. Hem masaüstü hem mobil platformlardan oluşturulabilir.

---

## 1. SalesOrder

> Tablo: `sales.sales_order`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `orderNumber` | String | Evet | **Otomatik** — `SO-2024-001` |
| `customerId` | UUID | Evet | FK → TradingPartner (partnerType = CUSTOMER/BOTH) |
| `status` | SalesOrderStatus (Enum) | Evet | Bkz. `11-cross-cutting/status-enum-catalog.md` |
| `deadline` | Date | Hayır | Teslim tarihi |
| `moduleType` | ModuleType (Enum) | Evet | FIBER / YARN / FABRIC / DYE_FINISHING |
| `quoteId` | UUID | Hayır | FK → Quote — dönüşümle geldiyse |
| `sampleRequestId` | UUID | Hayır | FK → SampleRequest — numuneden geldiyse |
| `paymentTerms` | Enum | Hayır | CASH / NET_30 / NET_60 / OPEN_ACCOUNT |
| `leadTimeDays` | Integer | Hayır | Teslim süresi |
| `approvalMethod` | Enum | Hayır | EMAIL_LINK / VERBAL / PHOTO / DIGITAL_SIGNATURE |
| `approvedAt` | Timestamp | Hayır | Onay zamanı |
| `approvedByName` | String | Hayır | Onaylayan müşteri adı |
| `approvalEvidence` | JSONB | Hayır | Onay kanıtı (fotoğraf, konum, imza) |
| `notes` | String (TEXT) | Hayır | Genel notlar |
| `attachments` | JSONB | Hayır | Referans dokümanlar |
| `offlineCreatedAt` | Timestamp | Hayır | Offline oluşturulduysa |
| `deviceId` | String | Hayır | Hangi tabletten |
| `syncedAt` | Timestamp | Hayır | Sunucuya sync zamanı |

### Status Akışı

```
DRAFT → CONFIRMED → IN_PRODUCTION → IN_WAREHOUSE → SHIPPED → DELIVERED → CLOSED
                  ↘ CANCELLED (DELIVERED ve CLOSED hariç)
```

| Geçiş | Tetikleyen |
|---|---|
| `DRAFT → CONFIRMED` | Kullanıcı onaylar |
| `CONFIRMED → IN_PRODUCTION` | İlk bağlı WorkOrder IN_PROGRESS olunca — otomatik |
| `IN_PRODUCTION → IN_WAREHOUSE` | Tüm SalesOrderLine'lar IN_WAREHOUSE olunca — otomatik |
| `IN_WAREHOUSE → SHIPPED` | IWM ShipmentDispatchedEvent |
| `SHIPPED → DELIVERED` | Teslimat onayı |
| `DELIVERED → CLOSED` | Kullanıcı kapatır |

### Event'ler

| Geçiş | Event | Alıcı |
|---|---|---|
| `→ CONFIRMED` | `SalesOrderConfirmed` | FlowBoard (PLANNING task), NotificationHub, RuleEngine |
| `→ IN_WAREHOUSE` | `SalesOrderInWarehouse` | FlowBoard (SHIPMENT task), NotificationHub |

---

## 2. SalesOrderLine

> Tablo: `sales.sales_order_line`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `salesOrderId` | UUID | Evet | FK → SalesOrder |
| `materialId` | UUID | Hayır | FK → Material — katalogdan seçildiyse |
| `productDesc` | String (TEXT) | Hayır | Serbest ürün tanımı — katalogda yoksa |
| `requestedQty` | Decimal | Evet | Talep edilen miktar |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `unitPrice` | Decimal | Hayır | Birim fiyat |
| `currency` | Enum | Hayır | USD / EUR / TRY |
| `moduleSpecs` | JSONB | Hayır | Modüle özel alanlar — bkz. ModuleSpecs |
| `lineStatus` | SalesOrderLineStatus (Enum) | Evet | Bkz. `11-cross-cutting/status-enum-catalog.md` |

**Validasyon:** `materialId` veya `productDesc` — en az biri zorunlu, ikisi de null olamaz.

> **Tasarım kararı:** Eski dökümanlardan `productId → Product` FK'sı kaldırıldı. Material entity modüller arası ortak "ürün" katmanı olarak kullanılır — Product entity'sine gerek yok.

### LineStatus Akışı

```
PENDING → RECIPE_ASSIGNED → IN_PRODUCTION → COMPLETED → IN_WAREHOUSE → SHIPPED
        ↘ CANCELLED
```

| Geçiş | Tetikleyen |
|---|---|
| `PENDING → RECIPE_ASSIGNED` | RuleEngine veya kullanıcı recipe atar |
| `RECIPE_ASSIGNED → IN_PRODUCTION` | Bağlı WorkOrder IN_PROGRESS olunca — otomatik |
| `IN_PRODUCTION → COMPLETED` | Tüm bağlı WorkOrder'lar COMPLETED olunca — otomatik |
| `COMPLETED → IN_WAREHOUSE` | IWM ProductStoredEvent |
| `IN_WAREHOUSE → SHIPPED` | IWM ShipmentDispatchedEvent |

---

## ModuleSpecs JSONB Şemaları

Her modül için `moduleSpecs` JSONB içindeki alan şeması. Backend her `moduleType` için ayrı validasyon uygular.

### FIBER

```json
{
  "certificationReq": "GOTS",
  "originReq": "TR",
  "moq": 500,
  "leadTimeDays": 21
}
```

### YARN

```json
{
  "count": "30/1 Ne",
  "twist": "Z",
  "construction": "Ring Spun",
  "certificationReq": "GOTS"
}
```

### FABRIC

```json
{
  "weight": "180 g/m²",
  "width": "150 cm",
  "weaveType": "Plain",
  "certificationReq": "GOTS"
}
```

### DYE_FINISHING

```json
{
  "color": "Pantone 19-4052 TCX",
  "finish": "Sanforized",
  "washInstruction": "30°C gentle",
  "certificationReq": "OEKO-TEX"
}
```

> **UI davranışı:** `SalesOrder.moduleType` seçilince form dinamik olarak değişir.  
> **Yeni modül eklendiğinde:** Tabloya dokunulmaz — sadece yeni JSON şeması ve backend validasyonu tanımlanır.

---

## RuleEngine — Recipe Eşleştirme

`SalesOrderConfirmed` eventi tetiklendiğinde RuleEngine her `SalesOrderLine` için 4 adımlı kaskad çalıştırır:

```
Adım 1 — Material varsayılan recipe
  materialId varsa ve bağlı Fiber'in varsayılan recipe'si doluysa → recipe atandı
  certificationReq / originReq uyuşuyor mu? → uyuşmuyorsa sonraki adım

Adım 2 — Müşteri geçmişi
  Aynı müşteri + aynı material + aynı certificationReq + originReq
  daha önce kullanılmış mı? → en son kullanılan recipe önerilir

Adım 3 — Kısıt tabanlı filtreleme
  ACTIVE recipe'ler arasında certificationReq + originReq ile filtrele
  (RecipeComponent tablosu üzerinden — JSONB değil)
  En çok kullanılan recipe önerilir

Adım 4 — Fallback
  Eşleşen recipe bulunamadı
  WorkOrder taslağı açılır, recipeId boş bırakılır
  FlowBoard'a RECIPE_ASSIGNMENT task: "Bu kalem için recipe atanması gerekiyor"
```

### RuleEngine → WorkOrder Zinciri

```
SalesOrder CONFIRMED
    ↓
RuleEngine her SalesOrderLine için:
  - Recipe eşleştirme (4 adımlı kaskad)
  - WorkOrder taslağı oluşturur (DRAFT)
    - salesOrderLineId = SalesOrderLine.id
    - plannedQty ← requestedQty
    - deadline ← SalesOrder.deadline
    ↓
FlowBoard'a task açılır:
  - Recipe atandıysa: "WorkOrder onaylanmayı bekliyor" (PLANNING)
  - Recipe atanamadıysa: "Recipe atanması gerekiyor" (RECIPE_ASSIGNMENT)
```

> **AI katmanı:** Sistem AI olmadan tam çalışır. RuleEngine deterministik kurallara dayanır.

---

## SalesOrder → IWM Entegrasyonu

SalesOrder IWM'in iç detaylarını bilmez — sadece event dinler.

| IWM Eventi | SalesOrder Aksiyonu |
|---|---|
| `ProductStoredEvent` | SalesOrderLine → IN_WAREHOUSE |
| `ShipmentDispatchedEvent` | SalesOrderLine → SHIPPED |
| `DeliveryConfirmedEvent` | SalesOrder → DELIVERED |

---

## İlişki Özeti

```
TradingPartner (CUSTOMER) ──→ SalesOrder ──→ SalesOrderLine (1:N)
                                    │               │
                                    │               ├──→ Material (FK)
                                    │               ├──→ WorkOrder (1:N via salesOrderLineId)
                                    │               └──→ StockReservation (lot ayırma)
                                    │
                                    ├──→ Quote (opsiyonel — dönüşümle geldiyse)
                                    └──→ SampleRequest (opsiyonel)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/trading-partner.md` | SalesOrder.customerId → TradingPartner |
| `02-production/material-fiber.md` | SalesOrderLine.materialId → Material |
| `02-production/work-order.md` | WorkOrder.salesOrderLineId → SalesOrderLine |
| `02-production/recipe.md` | RuleEngine recipe eşleştirme |
| `03-sales/quote-approval.md` | Quote → SalesOrder dönüşümü |
| `03-sales/sample-management.md` | SampleRequest → SalesOrder |
| `05-iwm/stock-reservation.md` | SalesOrderLine → StockReservation |
| `07-flowboard/smart-task-generator.md` | SalesOrderConfirmed → PLANNING task |

---

## Açık Kararlar

- [ ] moduleSpecs şemaları kesinleştirilecek — her modül için zorunlu alan kararları
- [ ] RuleEngine eşleştirme mantığı tekrar değerlendirilecek
- [ ] Kendi iç planlama (salesOrderLineId=null) ayrı UI mı, aynı form mu?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — productId→materialId değiştirildi, RuleEngine RecipeComponent'e yönlendirildi |

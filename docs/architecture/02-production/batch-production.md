# Batch — Evrensel Parti Modeli

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 2.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Batch, BatchAttribute, BatchCertification, BatchReservation, BatchOverrideLog burada tanımlanır.

---

## Genel Bakış

Batch tüm malzeme tipleri (Fiber, Yarn, Fabric vb.) için **tek evrensel parti modeli**dir. Bir Batch hammadde balyası olabilir, üretim çıktısı olabilir, satın alma lotu olabilir — hepsi aynı entity. Modüle özel alanlar `attributes` JSONB ile taşınır.

### Tasarım Felsefesi

- **Evrensel:** materialId + materialType ile her malzeme tipine hizmet eder
- **Tam yaşam döngüsü:** QC → stok → rezervasyon → tüketim → bitme
- **Modüle özel alanlar:** `attributes` JSONB (fiber_micronaire, yarn_count vb.)
- **Lineage destekli:** parentBatchId ile blend/üretim zinciri
- **Lokasyon bağlı:** locationId → WarehouseLocation

---

## 1. Batch

> Tablo: `production.production_execution_batch`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `batchCode` | String | Evet | Tenant içinde benzersiz — otomatik üretilir |
| `materialId` | UUID | Evet | FK → Material |
| `materialType` | MaterialType (Enum) | Evet | FIBER / YARN / FABRIC / CHEMICAL / CONSUMABLE |
| `quantity` | Decimal | Evet | Mevcut miktar |
| `reservedQuantity` | Decimal | Evet | Rezerve edilen miktar |
| `consumedQuantity` | Decimal | Evet | Tüketilen miktar |
| `wasteQuantity` | Decimal | Evet | Fire miktarı |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `status` | BatchStatus (Enum) | Evet | Bkz. State Machine |
| `attributes` | JSONB | Hayır | Modüle özel alanlar |
| `locationId` | UUID | Hayır | FK → WarehouseLocation |
| `parentBatchId` | UUID | Hayır | FK → Batch (self) — blend/lineage |
| `qualityStandardId` | UUID | Hayır | FK → FiberQualityStandard — QC profili |
| `sourceType` | BatchSourceType (Enum) | Hayır | Batch'in kaynağı — nereden geldi? |
| `sourceId` | UUID | Hayır | Kaynak kaydın id'si — polimorfik FK |

### BatchSourceType — "Bu Batch Nereden Geldi?"

| Değer | Açıklama | sourceId → |
|---|---|---|
| `INTERNAL_PRODUCTION` | Kendi fabrikamızda üretildi | WorkOrder.id |
| `PURCHASE` | Tedarikçiden satın alındı | PurchaseOrder.id |
| `SUBCONTRACT` | Fason firmadan döndü | SubcontractOrder.id |
| `ADJUSTMENT` | Stok sayımı düzeltmesi | StockCount.id |
| `RETURN` | Müşteriden iade | RMA.id |
| `INITIAL_STOCK` | Sistem kurulumu / ilk stok girişi | null |

> **Tasarım kararı:** Batch doğrudan WorkOrder'a FK ile bağlı değildir — evrensel kalır. `sourceType + sourceId` ile "nereden geldiği" opsiyonel olarak izlenir. Lineage ise "neye dönüştüğünü" izler (parent→child tüketim zinciri).
>
> **Neden böyle?**
> - Tedarikçiden gelen Batch: WorkOrder yok, PurchaseOrder var
> - Stok sayımı fazlası: Hiçbir order yok
> - Fason dönüş: WorkOrder var ama üretimi fason firma yaptı
> - İlk stok girişi: Sistem kurulumunda, hiçbir kaynak yok
>
> `sourceType/sourceId` → "Bu batch nereden geldi?" (giriş kaynağı)  
> `BatchLineage` → "Bu batch neye dönüştü?" (çıkış/tüketim zinciri)  
> İkisi birbirini tamamlar.

---

## State Machine — BatchStatus

```
PENDING_QC → AVAILABLE → RESERVED → IN_PROGRESS → DEPLETED
                ↑
          QUARANTINE (conditional accept sonrası yeniden değerlendirme)

Yan dallar: QC_REJECTED, ON_HOLD, RETURNED, DESTROYED
```

| Değer | Açıklama |
|---|---|
| `PENDING_QC` | Yeni oluşturulmuş — kalite kontrol bekliyor |
| `AVAILABLE` | QC geçti — stokta, kullanılabilir |
| `RESERVED` | Sipariş/üretim için ayrıldı |
| `IN_PROGRESS` | Üretime alındı — tüketim devam ediyor |
| `DEPLETED` | Tamamen tüketildi |
| `QUARANTINE` | Karantina — şüpheli kalite |
| `ON_HOLD` | Beklemede — yönetici kararı bekleniyor |
| `QC_REJECTED` | QC reddetti — kullanılamaz |
| `RETURNED` | İade edildi |
| `DESTROYED` | İmha edildi |

### QC Event Akışı

```
FiberTestResult oluşturulur → FiberQcAutoEvaluator çalışır
    ↓
FiberTestResultApprovedEvent yayınlanır
    ↓
BatchQcEventListener dinler:
  APPROVED            → Batch.status = AVAILABLE
  CONDITIONAL_ACCEPT  → Batch.status = QUARANTINE
  REJECTED            → Batch.status = QC_REJECTED
```

> **Detay:** `02-production/quality-fiber.md`

---

## 2. BatchCertification

| Alan | Tip | Açıklama |
|---|---|---|
| `batchId` | UUID | FK → Batch |
| `certificationTypeId` | UUID | FK → CertificationType |
| `licenseNo` | String | Sertifika numarası |
| `validFrom` | LocalDate | Geçerlilik başlangıcı |
| `validUntil` | LocalDate | Geçerlilik bitişi |
| `scope` | Enum | BATCH / PARTNER / FACILITY |
| `changeReason` | Enum | INITIAL / RENEWAL / CORRECTION / REVOCATION |
| `isAutoFilled` | Boolean | Otomatik mı dolduruldu |

Auto-fill: Batch oluşturulurken TradingPartner'ın aktif sertifikaları otomatik kopyalanabilir. Periyodik kontrol: `BatchCertificationExpiryCheckJob`.

---

## 3. BatchReservation

| Alan | Tip | Açıklama |
|---|---|---|
| `batchId` | UUID | FK → Batch |
| `reservedQuantity` | Decimal | Ayrılan miktar |
| `status` | Enum | ACTIVE / RELEASED / CONSUMED / EXPIRED |
| `referenceType` | String | SALES_ORDER, WORK_ORDER vb. |
| `referenceId` | UUID | İlgili kaydın id'si |
| `expiresAt` | Timestamp | Süre dolunca otomatik release |

---

## 4. BatchOverrideLog

Status zorla değiştirildiğinde denetim kaydı.

| Alan | Tip | Açıklama |
|---|---|---|
| `batchId` | UUID | FK → Batch |
| `fromStatus` | BatchStatus | Önceki durum |
| `toStatus` | BatchStatus | Yeni durum |
| `reason` | String (TEXT) | Gerekçe — zorunlu |
| `overriddenBy` | UUID | FK → User |

---

## Batch Operasyonları

| Operasyon | Açıklama | Miktar Etkisi |
|---|---|---|
| `reserve` | Sipariş/üretim için ayır | reservedQuantity += |
| `release` | Rezervasyonu serbest bırak | reservedQuantity -= |
| `consume` | Üretime kullan | quantity -=, consumedQuantity += |
| `waste` | Fire kaydet | quantity -=, wasteQuantity += |
| `adjust` | Manuel düzeltme | quantity ± |
| `split` | Batch'i böl | İki ayrı Batch oluşur |
| `transfer` | Lokasyon değiştir | locationId güncellenir |
| `startProduction` | Üretime al | status → IN_PROGRESS |
| `overrideStatus` | Status zorla değiştir | BatchOverrideLog yazılır |

---

## Batch Attributes — Modüle Özel Alanlar

### Fiber
```json
{ "fiber_micronaire": 4.2, "fiber_length_mm": 28.5, "fiber_strength": 30.1,
  "fiber_elongation": 6.5, "fiber_moisture": 7.8, "fiber_trash_content": 2.1,
  "fiber_grade": "A", "origin_country": "TR", "certification": "GOTS" }
```

### Yarn
```json
{ "yarn_count": "30/1 Ne", "yarn_twist": "Z", "yarn_construction": "Ring Spun", "yarn_ply": 1 }
```

### Fabric
```json
{ "fabric_weight_gsm": 180, "fabric_width_cm": 150, "fabric_weave": "Plain" }
```

> **Attribute Inheritance:** Child batch oluşturulduğunda parent'lardan attribute'lar otomatik hesaplanır. Detay: `02-production/batch-lineage.md`

---

## Domain Event'ler

| Event | Tetikleyen |
|---|---|
| `BatchCreatedEvent` | Yeni batch |
| `BatchReservedEvent` | Rezervasyon |
| `BatchConsumedEvent` | Tüketim |
| `BatchCompletedEvent` | DEPLETED |
| `BatchSplitEvent` | Bölme |
| `BatchTransferredEvent` | Lokasyon değişikliği |
| `BatchWasteRecordedEvent` | Fire kaydı |
| `BlendedBatchCreatedEvent` | Blend batch |

---

## API

Base path: `/api/production/batches`

Yetkilendirme: `ProductionAccessService` — BATCH READ/WRITE.

CRUD: create, getAll, getById, getByMaterialId. Operasyonlar: reserve, release, consume, record-waste, adjust, split, partial-acceptance-split, start-production, transfer, override-status. Sertifika: certification-autofill, certifications. Detay: reservations, attributes.

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/material-fiber.md` | Batch.materialId → Material |
| `02-production/batch-lineage.md` | Parent/child izlenebilirlik |
| `02-production/inventory.md` | InventoryTransaction + InventoryBalance |
| `02-production/quality-fiber.md` | FiberTestResult → QC event akışı |
| `02-production/warehouse-location.md` | Batch.locationId |
| `01-foundations/reference-tables.md` | BatchCertification → CertificationType |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 2.0 | 2026-03-17 | Tamamen yeniden yazıldı — evrensel Batch, gerçek state machine, certification, reservation, override log, domain events |
| 1.0 | 2026-03-17 | İlk versiyon (basit model) |

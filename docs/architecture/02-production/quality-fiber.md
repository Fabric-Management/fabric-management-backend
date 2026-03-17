# Fiber Kalite Kontrol — QC Test & Otomatik Değerlendirme

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: FiberTestResult, FiberQcAutoEvaluator, FiberQualityStandard burada tanımlanır.

---

## Genel Bakış

Fiber parti test sonuçları ve kalite kapısı. Test kaydı oluşturulduğunda `FiberQualityStandard` ile otomatik karşılaştırma yapılır, onay kararı verilir ve Batch durumu event ile güncellenir. Modül sadece test ve karar tutar — Batch status güncellemesi execution tarafında event-driven yapılır.

---

## 1. FiberTestResult

> Tablo: `production.production_quality_fiber_test_result`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `batchId` | UUID | Evet | FK → Batch — hangi parti için test |
| `testDate` | LocalDate | Evet | Test tarihi |
| `testType` | Enum | Evet | LABORATORY / PRODUCTION / INCOMING |
| `fineness` | Decimal | Hayır | Micronaire / dtex |
| `lengthMm` | Decimal | Hayır | Stapl uzunluğu (mm) |
| `strengthCnDTex` | Decimal | Hayır | Mukavemet (cN/dtex) |
| `elongationPercent` | Decimal | Hayır | Uzama (%) |
| `moisturePercent` | Decimal | Hayır | Nem (%) |
| `trashContentPercent` | Decimal | Hayır | Çöp oranı (%) |
| `approvalStatus` | TestApprovalStatus (Enum) | Evet | PENDING / APPROVED / REJECTED / CONDITIONAL_ACCEPT |
| `testLab` | String | Hayır | Test laboratuvarı |
| `testStandard` | String | Hayır | Test standardı referansı |
| `remarks` | String (TEXT) | Hayır | Notlar |

### TestApprovalStatus

| Değer | Açıklama |
|---|---|
| `PENDING` | Henüz değerlendirilmedi (standart yoksa) |
| `APPROVED` | Tüm ölçümler min–max içinde, hedefe yakın |
| `CONDITIONAL_ACCEPT` | Min–max içinde ama hedeften sapma var |
| `REJECTED` | En az bir ölçüm min/max dışında |

---

## 2. FiberQualityStandard

> Tablo: `production.prod_fiber_quality_standard`  
> `BaseEntity`'den miras alır.  
> Tanım: `production.masterdata.fiber` paketinde.

ISO kodu bazlı kalite profilleri. Her parametre için LSL (alt limit), Target (hedef), USL (üst limit).

| Alan | Tip | Açıklama |
|---|---|---|
| `fiberIsoCodeId` | UUID | FK → FiberIsoCode — hangi lif tipi |
| `standardName` | String | Standart adı |
| `finenesLsl` / `finenesTarget` / `finenesUsl` | Decimal | Micronaire limitleri |
| `lengthMmLsl` / `lengthMmTarget` / `lengthMmUsl` | Decimal | Uzunluk limitleri |
| `strengthLsl` / `strengthTarget` / `strengthUsl` | Decimal | Mukavemet limitleri |
| `elongationLsl` / `elongationTarget` / `elongationUsl` | Decimal | Uzama limitleri |
| `moistureLsl` / `moistureTarget` / `moistureUsl` | Decimal | Nem limitleri |
| `trashContentLsl` / `trashContentTarget` / `trashContentUsl` | Decimal | Çöp oranı limitleri |
| `isDefault` | Boolean | Bu ISO kodu için varsayılan profil mi |

---

## 3. FiberQcAutoEvaluator — Otomatik Değerlendirme

Sadece `FIBER` tipi batch'ler için çalışır.

### Standart Bulma Sırası

```
1. Batch.qualityStandardId doluysa → o standart kullanılır
2. Yoksa → Batch → Material → Fiber → FiberIsoCode → default FiberQualityStandard
3. Hiçbiri yoksa → hasStandard = false, approval PENDING kalır
```

### Karar Mantığı

```
Her ölçüm (fineness, length, strength, elongation, moisture, trash) için:
  LSL ≤ ölçüm ≤ USL ?

Tüm ölçümler aralık içinde:
  Hepsi hedefe yakın → APPROVED
  Biri hedeften sapık → CONDITIONAL_ACCEPT

En az bir ölçüm aralık dışında:
  → REJECTED
```

### Standart Yoksa

```
FiberQcAutoEvaluator → hasStandard = false
    ↓
InAppNotificationService → BATCH_NO_QUALITY_STANDARD bildirimi
    ↓
Batch manuel incelemeye kalır (PENDING_QC'de bekler)
    ↓
Kullanıcı manuel onay/red/conditional accept yapar (PATCH endpoint)
```

---

## 4. Event-Driven QC → Batch Akışı

```
Test kaydı oluşturulur (POST /api/production/quality/fiber-tests)
    ↓
FiberTestResultService.create()
    ↓
FiberQcAutoEvaluator çalışır
    ↓
Standart varsa → approvalStatus set edilir
    ↓
FiberTestResultApprovedEvent yayınlanır
  { tenantId, batchId, approvalStatus, actorId }
    ↓
BatchQcEventListener (production.execution.batch.app) dinler:
  APPROVED            → Batch.status = AVAILABLE
  CONDITIONAL_ACCEPT  → Batch.status = QUARANTINE
  REJECTED            → Batch.status = QC_REJECTED
    ↓
Sadece PENDING_QC veya QUARANTINE batch'ler güncellenir
Diğer durumlarda skip (idempotent)
```

> **Loose coupling:** Quality modülü Batch entity'sini doğrudan değiştirmez — event yayınlar, execution modülü dinler ve günceller.

---

## API

Base path: `/api/production/quality/fiber-tests`

| Endpoint | Metod | Açıklama |
|---|---|---|
| `/` | POST | Yeni test sonucu (auto-eval tetiklenir) |
| `/{id}` | GET | Tek sonuç |
| `/` | GET | Tüm sonuçlar |
| `/batch/{batchId}` | GET | Batch'e ait sonuçlar |
| `/status/{status}` | GET | Approval status'e göre |
| `/{id}/approval` | PATCH | Manuel onay güncelleme |

**Yetkilendirme:** `ProductionAccessService` — QUALITY_TEST READ/WRITE.

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/batch-production.md` | Batch state machine — QC event akışı |
| `02-production/material-fiber.md` | Fiber → FiberIsoCode → QualityStandard |
| `01-foundations/reference-tables.md` | FiberIsoCode — standart bulma |
| `08-notification-i18n/notification-hub.md` | BATCH_NO_QUALITY_STANDARD bildirimi |

---

## İleride Eklenecek QC Modülleri

| Modül | Durum | Not |
|---|---|---|
| Yarn QC | Planlandı | İplik kalite parametreleri farklı (count, twist uniformity) |
| Fabric QC | Planlandı | Makine entegrasyonu gerekebilir |
| Dye/Finishing QC | Planlandı | Renk farkı (Delta E), haslık testleri |

Her modül aynı pattern'ı izleyecek: TestResult entity + AutoEvaluator + QualityStandard + Event → BatchQcEventListener.

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek koddan, placeholder QC'den tam döküman'a |

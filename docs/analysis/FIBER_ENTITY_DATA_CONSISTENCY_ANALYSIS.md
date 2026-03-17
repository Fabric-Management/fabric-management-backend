# Fiber Entity & Fiber Data Tutarlılık Analizi

Bu doküman Fiber entity, DTO'lar, Batch attributes ve frontend tipleri arasındaki tutarlılığı analiz eder.

---

## 1. Veri Akışı Özeti

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ MASTER DATA (Katalog)                                                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Fiber Entity (prod_fiber)          FiberDto (API response)                       │
│ ├─ material_id                     ├─ materialId                                 │
│ ├─ fiber_category_id                ├─ fiberCategoryId                            │
│ ├─ fiber_iso_code_id               ├─ fiberIsoCodeId, isoCode (enriched)           │
│ ├─ fiber_name                      ├─ fiberName                                   │
│ ├─ fiber_grade                     ├─ fiberGrade                                  │
│ ├─ composition (JSONB)             ├─ composition                                 │
│ ├─ status                          ├─ status                                      │
│ └─ remarks                         ├─ attributes[] (enriched via links)           │
│                                    └─ certifications[] (enriched via links)      │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│ BATCH / LOT (Fiziksel Parti)                                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Batch.attributes (JSONB)            FiberAttributes (typed record)               │
│ ├─ fiber_micronaire                 ├─ micronaire                                 │
│ ├─ fiber_staple_length              ├─ stapleLength                                │
│ ├─ fiber_grade                      ├─ grade     ← LOT seviyesi                   │
│ ├─ fiber_shade                      ├─ shade                                       │
│ ├─ fiber_organic_cert_no            ├─ organicCertNo                               │
│ └─ bale_moisture                    └─ baleMoisture                               │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Tutarlı Olan Alanlar

| Alan | Entity | DTO | Frontend | Not |
|------|--------|-----|----------|-----|
| materialId | ✓ | ✓ | ✓ | UUID → string (JSON) |
| fiberCategoryId | ✓ | ✓ | ✓ | |
| fiberIsoCodeId | ✓ | ✓ | ✓ | |
| fiberName | ✓ | ✓ | ✓ | |
| status | ✓ | ✓ | ✓ | FiberStatus |
| remarks | ✓ | ✓ | ✓ | |
| version | ✓ | ✓ | ✓ | Optimistic locking |
| composition | ✓ | ✓ | ✓ | Map<UUID,BigDecimal> ↔ Record<string,number> |

### CreateFiberRequest
- CreateFiberModal attributeIds, certificationIds gönderiyor ✓
- Backend CreateFiberRequest bu alanları destekliyor ✓

### Batch Attributes (snake_case)
- Backend: `fiber_grade`, `fiber_micronaire`, vb. ✓
- Frontend batch-attributes.ts: snake_case interface ✓
- CreateBatchModal: fiber alanlarını top-level request'e map ediyor ✓

---

## 3. Tutarsızlıklar ve Öneriler

### 3.1 UpdateFiberRequest — attributeIds / certificationIds Eksik

| Katman | attributeIds | certificationIds |
|--------|--------------|-------------------|
| Backend UpdateFiberRequest | ✓ Var | ✓ Var |
| Backend FiberService.updateFiber | ✓ İşleniyor | ✓ İşleniyor |
| Frontend UpdateFiberRequest (fiber-masterdata.ts) | ❌ Yok | ❌ Yok |
| UpdateFiberModal | ❌ Göndermiyor | ❌ Göndermiyor |

**Etki:** Fiber güncelleme sırasında attributes ve certifications değiştirilemez. Create'te atanabiliyor, Update'te güncellenemiyor.

**Öneri:** 
- Frontend `UpdateFiberRequest` interface'ine `attributeIds?: string[]` ve `certificationIds?: string[]` eklenmeli.
- UpdateFiberModal'da CreateFiberModal'daki gibi Step 4 "Properties & Certs" adımı eklenmeli (veya mevcut formda attributes/certifications seçimi).

---

### 3.2 fiber_grade — İki Farklı Kavram (Semantik Karışıklık)

| Konum | Anlam | Örnek |
|-------|-------|-------|
| **Fiber.fiberGrade** (prod_fiber) | Katalog tanımı seviyesi — "Bu fiber tipi genelde hangi grade?" | "Standard", "Premium" |
| **Batch.attributes.fiber_grade** | Lot seviyesi — "Bu spesifik parti teslimatta hangi grade?" | "Grade A", "A+" |

**Mevcut durum:** Her ikisi de string, farklı amaçlara hizmet ediyor.

**Tasarım kararı (onaylı):** FiberGrade Batch'e taşınacak veya FiberGradeEnum yapılacak. Bu durumda:
- `Fiber.fiberGrade` kaldırılacak
- Tüm grade bilgisi Batch.attributes içinde veya ayrı tabloda tutulacak

**Geçiş öncesi:** Şu an iki kavram birbirine karışabilir. Dokümantasyonda netleştirilmeli:
- Fiber.fiberGrade = "Varsayılan / katalog grade" (opsiyonel)
- Batch.fiber_grade = "Lot bazlı ölçüm / tedarikçe bildirilen grade"

---

### 3.3 FiberDto.attributes / certifications — Enrichment vs Entity

| Alan | Entity'de | DTO'da | Kaynak |
|------|-----------|--------|--------|
| attributes | ❌ (FiberAttributeLink) | ✓ | enrichFiberDto |
| certifications | ❌ (FiberCertificationLink) | ✓ | enrichFiberDto |

**Tutarlı:** Entity doğrudan bu alanları tutmuyor; link tabloları üzerinden enrich ediliyor. Frontend FiberDto bu alanları bekliyor ve alıyor ✓

---

### 3.4 Composition — Blended vs Pure

| | Create | Update |
|---|--------|--------|
| Pure fiber | composition: undefined veya {} | composition: undefined (değişmez) |
| Blended | composition: { baseId: pct } | composition: { baseId: pct } |

**Tutarlı:** Backend ve frontend aynı semantiği kullanıyor. Update'te `composition` null/undefined ise mevcut değer korunuyor.

---

### 3.5 Batch Create — Fiber Attributes

CreateBatchRequest'te fiber-specific alanlar:
- Backend: `micronaire`, `stapleLength`, `fiberGrade`, `fiberShade`, `organicCertNo` ✓
- Frontend CreateBatchRequest: Aynı alanlar tanımlı ✓
- CreateBatchModal: `attributes["fiber_grade"]` vb. → top-level `fiberGrade` vb. map ediyor ✓

**Tutarlı.**

---

## 4. Özet Tablo

| Konu | Tutarlı mı? | Aksiyon |
|------|-------------|---------|
| Fiber entity ↔ FiberDto | ✓ | — |
| CreateFiberRequest (entity, DTO, frontend) | ✓ | — |
| UpdateFiberRequest attributeIds/certificationIds | ❌ | Frontend'e ekle, UpdateFiberModal'da UI |
| fiber_grade (Fiber vs Batch) | ⚠️ | Tasarım kararı: Batch'e taşı, Fiber'dan kaldır |
| Batch.attributes (FiberAttributes) | ✓ | snake_case uyumlu |
| Composition type (UUID/string, BigDecimal/number) | ✓ | JSON serialization uyumlu |

---

## 5. Önerilen Düzeltmeler (Öncelik Sırasıyla)

1. **P1 — UpdateFiberRequest:** Frontend `UpdateFiberRequest`'e `attributeIds` ve `certificationIds` ekle; UpdateFiberModal'da attributes/certifications seçim adımı ekle (veya mevcut tek adımda).

2. **P2 — fiber_grade dokümantasyonu:** Geçiş öncesi iki kavramın farkını dokümante et; Fiber.fiberGrade kaldırma işinde Batch.attributes.fiber_grade ile ilişkiyi netleştir.

3. **P3 — Composition Batch'e taşıma:** Tasarım kararına göre Composition Fiber'dan Batch'e taşınacak. Bu büyük refactor; ayrı migration planı gerekir.

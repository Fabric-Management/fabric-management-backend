# Fiber Backend — Özet Analiz Raporu

FabricOS **Fiber** modülünün backend tarafına ait özet rapor: entity'ler, tablolar, ilişkiler ve DTO'lar.

---

## 1. Genel Bakış

| Öğe | Açıklama |
|-----|----------|
| **Modül** | Production → Masterdata (Fiber) + Execution (Fiber Batch) + Quality + Lineage + Inventory |
| **Schema** | `production` |
| **Ana entity** | `Fiber` (lif katalog tanımı); fiziksel stok için `FiberBatch` |

---

## 2. Material vs Fiber Paket İlişkisi

| Paket | Konum | Rol |
|-------|--------|-----|
| **material** | `...production.masterdata.material` | **Ortak masterdata:** Tüm malzeme tipleri (FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE) için tek entity. Birim (`unit`), `material_type` ve audit alanlarını taşır. |
| **fiber** | `...production.masterdata.fiber` | **Lif özel masterdata:** Sadece lif katalog verisi. Her kayıt **mutlaka bir Material’a** bağlıdır (1:1). |

**İlişki:**

- **Material** → Tipi `FIBER` olan kayıtlar, **Fiber** tarafında genişletilir.
- **Fiber** entity’si `material_id` (FK) ile **tek bir Material**’a bağlıdır; veritabanında `uk_fiber_material` ile bir Material’a en fazla bir Fiber eşlenir.
- Yani: **Material** “ne tür malzeme + birim”; **Fiber** “o malzemenin lif tarafındaki ek bilgiler” (fiberName, fiberGrade, category, iso, composition, specification vb.). Yarn/Fabric için benzer şekilde ayrı modüller (yarn, fabric) kendi entity’leriyle aynı Material tablosuna referans verir.

**Özet:** `material` paketi **paylaşılan**, tip-bağımsız malzeme tanımı; `fiber` paketi bu tanımın **lif tipi için uzantısı** ve her Fiber bir Material’a 1:1 bağlıdır.

---

## 3. Entity'ler ve Tablolar (Özet)

### 3.1 Ana Entity'ler

| Entity | Tablo | Açıklama |
|--------|-------|----------|
| **Fiber** | `production.prod_fiber` | Lif master verisi (katalog). Material ile 1:1, kategori/ISO/özellik/sertifika ile ilişkili. |
| **Material** | `production.prod_material` | Birim, material_type (FIBER, YARN, FABRIC, …). Fiber her zaman bir Material’a bağlı. |
| **FiberCategory** | `production.prod_fiber_category` | Referans tablosu — lif kategorisi (N:1). |
| **FiberIsoCode** | `production.prod_fiber_iso_code` | Referans — ISO 2076 kodları (N:1). |
| **FiberAttribute** | `production.prod_fiber_attribute` | Referans — özellikler (ORGANIC, RECYCLED vb.). N:M (link üzerinden). |
| **FiberCertification** | `production.prod_fiber_certification` | Referans — sertifikalar (GOTS, OEKO-TEX vb.). N:M (link üzerinden). |
| **FiberAttributeLink** | `production.prod_fiber_attribute_link` | Fiber ↔ FiberAttribute N:M birleşim tablosu. |
| **FiberCertificationLink** | `production.prod_fiber_certification_link` | Fiber ↔ FiberCertification N:M; cert_number, valid_from/valid_until. |
| **FiberSpecification** | `production.prod_fiber_specification` | Lif için hedef kalite/tolerans (min/target/max). Fiber’a N:1 (fiber_id). |

### 3.2 Execution (Fiziksel Stok / Parti)

| Entity | Tablo | Açıklama |
|--------|-------|----------|
| **FiberBatch** | `production.production_execution_fiber_batch` | Lif partisi/lot; fiber_id → Fiber. Miktar, rezerve/tüketilen/fire, status (AVAILABLE, RESERVED, IN_PROGRESS, DEPLETED). |

### 3.3 Kalite ve Stok Hareketi

| Entity | Tablo | Açıklama |
|--------|-------|----------|
| **FiberTestResult** | `production.production_quality_fiber_test_result` | Parti bazlı test sonuçları; fiber_batch_id → FiberBatch. |
| **InventoryTransaction** | `production.production_execution_inventory_transaction` | Stok hareketi (giriş, tüketim, fire, düzeltme, transfer); batch_id ile batch’e bağlı. |
| **BatchLineage** | `production.production_execution_batch_lineage` | Parti soy ağacı (parent_batch_id → child_batch_id); izlenebilirlik. |

---

## 4. İlişki Özeti (Mantıksal)

```
Material (1) ───────────────── (1) Fiber
                                    ├── (N:1) FiberCategory, FiberIsoCode
                                    ├── (N:M) FiberAttribute   [FiberAttributeLink]
                                    ├── (N:M) FiberCertification [FiberCertificationLink]
                                    └── (1:N) FiberSpecification

Fiber (1) ─────────────────── (N) FiberBatch
                                    ├── (N) FiberTestResult
                                    ├── (N) InventoryTransaction
                                    └── (N:M) BatchLineage (parent ↔ child)
```

---

## 5. DTO'lar (Backend → API)

Dokümantasyon ve TODO’lara göre kullanılan/planlanan DTO’lar:

### 5.1 Fiber (Masterdata)

| DTO | Yön | Açıklama |
|-----|-----|----------|
| **FiberDto** | Response | Fiber entity → API. (Not: `version` alanı optimistic locking için eklenmesi planlanıyor.) |
| **CreateFiberRequest** | Request | Fiber oluşturma; aynı DTO update için de kullanılıyor. Update’te `version` eklenmesi planlanıyor. |
| **UpdateFiberRequest** | Request | (Alternatif) Sadece güncelleme alanları + `version` için ayrı DTO. |

### 5.2 Fiber Batch (Execution)

| DTO | Yön | Açıklama |
|-----|-----|----------|
| **FiberBatchDto** | Response | Parti bilgisi (batchCode, quantity, availableQuantity, status, vb.). `version` taşınması planlanıyor. |
| **QuantityRequest** | Request | Rezerve / serbest bırakma / tüketim gibi miktar işlemleri; `version` eklenecek. |

### 5.3 Referans ve Ortak

| DTO | Yön | Açıklama |
|-----|-----|----------|
| **MaterialDto** | Response | Material bilgisi; Fiber ekranlarında kullanılır. `version` taşınması planlanıyor. |

### 5.4 Gelecek (Batch Generalization)

Evrensel Batch modeline geçişte:

- **BatchDto** — `materialId`, `materialType`, `batchCode`, `quantity`, `availableQuantity`, `status`, `attributes` (JSONB) ile tüm parti tipleri (Fiber, Yarn, Fabric) için tek DTO.
- **FiberBatchDto** bu yapıya evrilecek; modüle özel alanlar `attributes` içinde taşınacak.

---

## 6. Servis ve API Katmanları (Konum)

| Bileşen | Paket / konum |
|---------|----------------|
| Fiber, FiberSpecification, Link entity’leri | `production.masterdata.fiber.domain` |
| Referans entity’ler (Category, IsoCode, Attribute, Certification) | `production.masterdata.fiber.domain.reference` |
| FiberBatch | `production.execution.fiber.domain` |
| FiberTestResult | `production.quality.result.domain` |
| Material | `production.masterdata.material.domain` |
| FiberService, FiberFacade, FiberController | `production.masterdata.fiber` (app / api) |
| FiberBatchService, FiberBatchController | `production.execution.fiber` |
| BatchLineage, InventoryTransaction | `production.execution.lineage` / `inventory` (domain + app / api) |

---

## 7. Önemli Notlar

- **BaseEntity:** Tüm entity’lerde `id`, `tenant_id`, `uid`, `created_at`, `updated_at`, `is_active`, `version` (optimistic lock) ortaktır.
- **Optimistic locking:** Response DTO’lara ve update request’lere `version` taşınması TODO’da (OPTIMISTIC_LOCKING_TODO.md).
- **Batch generalization:** FiberBatch → evrensel Batch (material_id + JSONB attributes) ve Yarn modülüne geçiş yol haritası: [UNIVERSAL_BATCH_ROADMAP.md](UNIVERSAL_BATCH_ROADMAP.md); aşama bazlı görev listesi: [BATCH_GENERALIZATION_TODO.md](../todo/BATCH_GENERALIZATION_TODO.md).

Detaylı alan listeleri ve indeksler için: [FIBER_ENTITY_REPORT.md](FIBER_ENTITY_REPORT.md).

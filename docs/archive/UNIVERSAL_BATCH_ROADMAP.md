# FabricOS: Fiber'den Yarn'a Evrensel Batch Geçiş Yol Haritası

Bu doküman, **Genişlet–Daralt (Expand–Contract)** stratejisine dayalı, Fiber modülünü stabilize edip Yarn modülüne teknik borçsuz geçişi tanımlayan nihai yol haritasıdır. Mevcut mimari ile uyum için yapılacaklar burada özetlenir.

**İlgili dokümanlar:**
- [FIBER_BACKEND_OZET_RAPOR.md](FIBER_BACKEND_OZET_RAPOR.md) — Fiber entity/DTO/tablo özeti
- [BATCH_GENERALIZATION_TODO.md](../todo/BATCH_GENERALIZATION_TODO.md) — Görev listesi ve checkbox’lar
- [batch-generalization-strategy SKILL](../../agents/skills/TODO/batch-generalization-strategy/SKILL.md) — Strateji kuralları

---

## Strateji Özeti: Genişlet–Daralt

| Aşama | Hedef |
|-------|--------|
| **1. Dondurma** | Fiber özelliklerini stabilize et; yeni büyük değişiklik yapma. |
| **2. Genişletme** | Evrensel tablo + JSONB + nullable `location_id` ile DB’yi hazırla; IWM’e henüz bağımlı olma. |
| **3. Refactoring** | Backend `Batch` entity + evrensel `BatchDto`; frontend `BatchDto` + modüle özel attribute interface’leri. |
| **4. Yarn başlangıç** | Aynı `production_execution_batch` tablosunu kullan; Yarn attribute’ları JSONB’de; `BatchAttributeInheritancePort` ile Fiber→Yarn kalıtımı. |
| **5. Daraltma** | IWM hazır olunca `warehouse_location` → `location_id` veri eşlemesi, FK, eski kolonun kaldırılması. |

---

## AŞAMA 1: Fiber Geliştirmelerini Dondurma (Mevcut Durum)

**Amaç:** Fiber modülü kendi içinde çalışır ve stabil olsun; evrensel Batch’e geçiş öncesi son dokunuşlar tamamlansın.

### Yapılacaklar

- [ ] Fiber test sonuçları (FiberTestResult), spesifikasyonlar (FiberSpecification) ve mevcut DTO’lar stabil hale getirilsin.
- [ ] Yeni Fiber-only büyük özellik başlatılmadan önce Aşama 2 migration’ı planlansın.
- [ ] Optimistic locking: DTO’lara `version` taşınması (FiberDto, FiberBatchDto, CreateFiberRequest, QuantityRequest) isteniyorsa bu aşamada veya Aşama 3 ile birlikte yapılabilir — [OPTIMISTIC_LOCKING_TODO.md](../todo/OPTIMISTIC_LOCKING_TODO.md).

### Mimari referans

- Entity/tablo/DTO özeti: [FIBER_BACKEND_OZET_RAPOR.md](FIBER_BACKEND_OZET_RAPOR.md).
- Şu an parti tablosu: `production.production_execution_fiber_batch`, entity: `FiberBatch` (`fiber_id` → Fiber).

---

## AŞAMA 2: Genişletme (Expand) Migration’ı

**Amaç:** Evrensel yapıyı kurmak; IWM modülü hazır olmadan ilerlemek (location için geçici nullable kolon).

**Araç:** Flyway veya Liquibase migration script’i.

### 2.1 Veritabanı adımları (tek migration’da veya sıralı migration’lar)

| # | İşlem | Açıklama |
|---|--------|----------|
| 1 | **Rename tablo** | `production_execution_fiber_batch` → `production_execution_batch` |
| 2 | **Rename kolon** | `fiber_id` → `material_id` (aynı FK hedefi: Material’a değil, şu an Fiber’ın material’ı; evrensel Batch’te her parti bir Material’a bağlı olacak — Fiber/Yarn/Fabric hepsi Material üzerinden). Not: FK ilişkisi `material_id` → `prod_material(id)` olacak şekilde güncellenmeli. |
| 3 | **Yeni kolon** | `attributes` (JSONB) ekle. Varsayılan `'{}'` veya `NULL` (tercih: `NULL` kabul edilebilir, uygulama tarafında boş obje kullanılır). |
| 4 | **İndeks** | `attributes` üzerinde GIN index (JSONB filtreleme/sorgulama için). |
| 5 | **Yeni kolon** | `location_id` (UUID) ekle, **NULLABLE** bırak. **FK EKLEME** (IWM lokasyon tablosu hazır değil). |
| 6 | **Mevcut kolon** | `warehouse_location` (String) — **dokunma**; Aşama 5’e kadar kalacak. |

### 2.2 Migration sonrası şema (özet)

- Tablo adı: `production_execution_batch`
- Kolonlar: `id`, `tenant_id`, `uid`, `material_id`, `batch_code`, `supplier_batch_code`, `quantity`, `reserved_quantity`, `consumed_quantity`, `waste_quantity`, `unit`, `production_date`, `expiry_date`, `status`, `warehouse_location`, **`attributes` (JSONB)**, **`location_id` (UUID, nullable)**, `remarks` + audit/version.
- GIN index: `CREATE INDEX idx_batch_attributes_gin ON production_execution_batch USING GIN (attributes);` (veya schema adıyla uyumlu).

### 2.3 Mevcut mimari ile uyum

- [FIBER_BACKEND_OZET_RAPOR.md](FIBER_BACKEND_OZET_RAPOR.md) § 3.2’deki “FiberBatch” artık bu tabloya karşılık gelecek; entity adı Aşama 3’te `Batch` olacak.
- [BATCH_GENERALIZATION_TODO.md](../todo/BATCH_GENERALIZATION_TODO.md) “Veritabanı” maddesi bu aşamada gerçekleşir.

---

## AŞAMA 3: Kod Refactoring (Backend & Frontend)

**Amaç:** Uygulamanın yeni veritabanı şemasını ve evrensel Batch modelini kullanması.

### 3.1 Backend

- [ ] **Entity:** `FiberBatch` → `Batch` olarak yeniden adlandır. Paket: `production.execution.batch.domain` (veya mevcut execution yapısına göre `production.execution.fiber` yerine ortak `production.execution.batch`).
- [ ] **Alan eşlemesi:** `fiberId` → `materialId` (UUID, Material’a FK). Fiber partileri için `material_id`, ilgili Fiber’ın `material_id`’si ile aynı olacak.
- [ ] **Yeni alanlar:** `attributes` (Map<String, Object> / JSONB), `locationId` (UUID, nullable).
- [ ] **DTO:** Evrensel `BatchDto`: `materialId`, `materialType`, `batchCode`, `quantity`, `availableQuantity`, `unit`, `locationId`, `status`, `attributes` (+ audit, `version`). `FiberBatchDto` kaldırılır veya sadece BatchDto’nun Fiber bağlamında kullanımı olur.
- [ ] **Controller/Service:** Fiber batch endpoint’leri Batch entity ve BatchDto kullanacak şekilde güncellenir; filtreleme `materialType = FIBER` veya Material üzerinden Fiber ilişkisi ile yapılır.
- [ ] **FiberTestResult / InventoryTransaction / BatchLineage:** Hâlâ batch’e `batch_id` ile bağlı; tablo adı değişince FK referansı `production_execution_batch(id)` olacak şekilde güncellenir (gerekirse migration’da).

### 3.2 Frontend

- [ ] **Kontrat:** `Record<string, any>` yerine modüle özel interface’ler:
  - `FiberAttributes` (örn. `micronaire`, `stapleLength`, `fiber_grade`, `fiber_shade` — naming convention’a uygun).
  - `YarnAttributes` (örn. `twist_direction`, `yarn_count`) — Yarn aşamasında kullanılacak.
- [ ] **BatchDto:** `attributes` tipi `FiberAttributes | YarnAttributes` veya genel bir tip + type guard’lar.
- [ ] **Type guard’lar:** `isFiberBatch(batch)`, `isYarnBatch(batch)` veya `batch.materialType === 'FIBER'` ile `attributes as FiberAttributes`.
- [ ] Mevcut Fiber ekranları BatchDto ve `attributes` kullanacak şekilde refactor edilir.

### 3.3 Mevcut mimari ile uyum

- [FIBER_BACKEND_OZET_RAPOR.md](FIBER_BACKEND_OZET_RAPOR.md) § 5.4 ve § 6’daki “BatchDto” ve servis konumları bu aşamadan sonra güncel olur.
- [BATCH_GENERALIZATION_TODO.md](../todo/BATCH_GENERALIZATION_TODO.md) “Backend” ve “Frontend” maddeleri bu aşamada yapılır.

---

## AŞAMA 4: Yarn Modülüne Başlangıç

**Amaç:** Yarn partileri için ayrı tablo yok; evrensel Batch kullanılır.

### Kurallar

- **YASAK:** Yarn için ayrı `YarnBatch` tablosu veya `production_execution_yarn_batch` açmak.
- Yeni Yarn partileri **doğrudan** `production_execution_batch` tablosuna yazılır.
- `material_id`: Yarn tipinde bir Material’a bağlı (MaterialType.YARN veya benzeri).
- Yarn’a özel nitelikler: `twist_direction`, `yarn_count` vb. **attributes** (JSONB) içinde; naming convention’a uygun (örn. `yarn_` öneki veya şemada tanımlı anahtarlar).

### BatchAttributeInheritancePort

- **Amaç:** Fiber’dan Yarn’a üretimde (örn. eğirme) attribute kalıtımı (weight-average, min, pass-through kuralları).
- **Konum:** `production.execution.lineage.domain.port` (veya ortak `production.common.port`) — [cross-module-lineage-strategy](../../agents/skills/TODO/cross-module-lineage-strategy/SKILL.md) ile uyumlu.
- **Interface (örnek):**
  ```java
  public interface BatchAttributeInheritancePort {
      Map<String, Object> resolveInheritedAttributes(
          List<BatchAttributes> parentAttributes,
          MaterialType targetType
      );
  }
  ```
- Fiber modülü için default/boş implementasyon; Yarn modülü geliştirilirken bu port implemente edilir.
- İlgili strateji: [cross-module-lineage-strategy](../../agents/skills/TODO/cross-module-lineage-strategy/SKILL.md).

### Yapılacaklar

- [ ] Yarn master data (Yarn entity, MaterialType.YARN) ve gerekli referans tabloları tanımlanır.
- [ ] Yarn parti oluşturma: Batch entity kullanılır, `materialId` = Yarn material, `attributes` = Yarn attribute’ları.
- [ ] BatchLineage: Fiber batch(es) → Yarn batch ilişkisi aynı `production_execution_batch_lineage` tablosunda tutulur.
- [ ] Frontend: Yarn sekmesi/filtresi ve `YarnAttributes` ile form/liste.

---

## AŞAMA 5: Daraltma (Contract) — IWM Entegrasyonu Hazır Olduğunda

**Amaç:** Geçici çözümleri kaldırıp lokasyonu tam IWM ile bağlamak.

### 5.1 Veri eşleme

- [ ] Backend’de bir kerelik job veya SQL script: `warehouse_location` (String) değerlerini IWM lokasyon tablosundaki karşılıklarıyla eşleştirip `location_id` (UUID) alanını doldurur.
- [ ] Eşlenemeyen kayıtlar için politika belirlenir (varsayılan lokasyon, manuel düzeltme veya uyarı).

### 5.2 Sıkılaştırma

- [ ] `location_id` kolonuna **FOREIGN KEY** constraint eklenir (IWM lokasyon tablosuna).
- [ ] Gerekirse `location_id` için NOT NULL’a geçiş (eşleme tamamlandıktan sonra).

### 5.3 Temizlik

- [ ] **DROP COLUMN** `warehouse_location` — artık kullanılmıyor.

---

## Özet: Mevcut Mimari ile Uyum Checklist’i

| Kaynak | Yapılacak |
|--------|------------|
| **FIBER_BACKEND_OZET_RAPOR.md** | Aşama 3 sonrası § 3.2’de tablo adı `production_execution_batch`, entity `Batch`; § 5.4 BatchDto nihai; § 6’da Batch servis konumu güncellenir. |
| **BATCH_GENERALIZATION_TODO.md** | Aşama 1–5’e göre checkbox’lar ve alt maddeler eklenir; bu yol haritasına referans verilir. |
| **batch-generalization-strategy SKILL** | “Görevler” kısmında bu yol haritası (UNIVERSAL_BATCH_ROADMAP.md) referans alınır. |
| **OPTIMISTIC_LOCKING_TODO** | Version DTO’lara Aşama 1 veya 3’te taşınır; BatchDto için de `version` dahil edilir. |

---

## Sıra ve Bağımlılıklar

```
AŞAMA 1 (Fiber dondurma) 
    → AŞAMA 2 (DB Expand migration) 
    → AŞAMA 3 (Backend + Frontend refactoring) 
    → AŞAMA 4 (Yarn başlangıç)
    → (IWM hazır olduğunda) AŞAMA 5 (Contract migration)
```

Aşama 2 ile 3 arasında uygulama kodu hâlâ eski entity/tablo adına referans veriyorsa, migration sonrası tek deploy’da hem migration hem entity/DTO/controller güncellemesi yapılmalı veya geriye dönük uyumluluk (eski tablo adına view/synonym) kısa süreliğine düşünülebilir — tercih edilen: aynı release’te Aşama 2 + 3 birlikte.

Bu yol haritası, mevcut mimariyi evrensel Batch ve Yarn’a geçişe uyumlu hale getirmek için yapılacakları tanımlar.

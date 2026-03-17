# Batch Generalization Strategy (Universal Inventory Model)

Bu doküman, sisteme giren tüm fiziksel nesnelerin (Elyaf, İplik, Kumaş, Kimyasal) tek bir evrensel "Batch" (Parti/Lot) yapısında toplanması stratejisini ve refactor yol haritasını içerir. Güncel iş listesi bu dosyadadır.

**Nihai yol haritası (Fiber → Yarn Expand-Contract):** [../architecture/UNIVERSAL_BATCH_ROADMAP.md](../architecture/UNIVERSAL_BATCH_ROADMAP.md) — beş aşamalı plan, migration adımları ve Yarn modülüne geçiş bu dokümanda.

## 1. Temel İlke: Evrensel Fiziksel Nesne (Universal Batch)

`FiberBatch`, `YarnBatch`, `FabricRoll` gibi modüle özel ayrı tablolar KESİNLİKLE YASAKTIR. Sistemdeki her fiziksel nesne, ortak bir `production_execution_batch` tablosunda tutulmalıdır.

### Ortak Çekirdek (Core Attributes)
Tüm fiziksel nesnelerin sahip olduğu ortak özellikler ilişkisel sütunlar olarak tutulur:
*   `id`, `tenant_id`, `uid`
*   `material_id` (Neyin partisi olduğu - Fiber, Yarn, Fabric referansı)
*   `batch_code` (Lot/Parti numarası)
*   `quantity`, `reserved_quantity`, `consumed_quantity`, `waste_quantity`, `unit`
*   `location_id` (Hangi depo/rafta veya makinede olduğu)
*   `status` (AVAILABLE, RESERVED, IN_PROGRESS, QUARANTINE, REJECTED, DEPLETED)

### Esnek Nitelikler (Flexible Attributes via JSONB)
Modüle özel teknik detaylar (Elyafın mikron değeri, İpliğin büküm yönü, Kumaşın eni) veritabanı şemasını bozmadan PostgreSQL'in `JSONB` veri tipinde tutulmalıdır.
*   Sütun: `attributes` (JSONB)
*   Örnek Elyaf: `{"micronaire": 4.2, "staple_length": 28.5}`
*   Örnek İplik: `{"twist_direction": "Z", "yarn_count": "30/1 Ne"}`
*   Örnek Kumaş: `{"width_cm": 150, "weight_gsm": 200}`

## 2. Uçtan Uca DTO Kontratı (End-to-End DTO Contract)

Frontend ve Backend arasındaki veri transferi, bu esnek yapıyı destekleyecek şekilde tasarlanmalıdır.

### Backend (Java) DTO Yapısı
```java
public class BatchDto {
    private UUID id;
    private UUID materialId;
    private MaterialType materialType; // FIBER, YARN, FABRIC, vb.
    private String batchCode;
    private BigDecimal quantity;
    private BigDecimal availableQuantity;
    private String unit;
    private UUID locationId;
    private BatchStatus status;
    private Map<String, Object> attributes; // JSONB alanının karşılığı
    // ... audit alanları
}
```

### Frontend (TypeScript) Kontratı
```typescript
// Ortak Batch Arayüzü
export interface BatchDto {
  id: string;
  materialId: string;
  materialType: MaterialType; // "FIBER" | "YARN" | "FABRIC" vb.
  batchCode: string;
  quantity: number;
  availableQuantity: number;
  unit: string;
  locationId?: string;
  status: BatchStatus;
  attributes: Record<string, any>; // Esnek JSON alanı
  // ... audit alanları
}

// Modüle Özel Type Guard'lar (Opsiyonel ama önerilir)
export interface FiberAttributes {
  micronaire?: number;
  stapleLength?: number;
}

export interface YarnAttributes {
  twistDirection?: "S" | "Z";
  yarnCount?: string;
}

// Kullanım örneği:
// const fiberAttrs = batch.attributes as FiberAttributes;
```

## 3. UI/UX Tasarım Stratejisi (Dynamic Rendering)

UI tarafında her modül için sıfırdan tablo veya detay ekranı çizmek yerine, veri güdümlü (data-driven) bir yaklaşım benimsenmelidir.

### Dinamik Tablolar (Dynamic Data Tables)
*   **Ortak Sütunlar:** Lot Kodu, Miktar, Kullanılabilir Miktar, Lokasyon ve Durum her zaman sabit sütunlar olarak gösterilir.
*   **Dinamik Sütunlar:** Kullanıcı "Elyaf" sekmesindeyse, `attributes.micronaire` değeri tabloya dinamik bir sütun olarak eklenir. "İplik" sekmesindeyse `attributes.yarnCount` gösterilir.

### Dinamik Formlar (Dynamic Forms)
*   Yeni bir Lot oluşturulurken, seçilen `MaterialType`'a göre formun alt kısmında dinamik input alanları (JSONB'yi dolduracak alanlar) render edilmelidir.
*   Bu dinamik alanların konfigürasyonu (hangi tipte hangi alanlar sorulacak) tercihen frontend'de bir konfigürasyon dosyasında (veya backend'den gelen bir şemada) tutulmalıdır.

## 4. Refactor Yol Haritası — Aşama Bazlı TODO

Aşağıdaki liste [UNIVERSAL_BATCH_ROADMAP.md](../architecture/UNIVERSAL_BATCH_ROADMAP.md) ile uyumludur. Sıra: Aşama 1 → 2 → 3 → 4 → 5.

### Aşama 1: Fiber dondurma
- [ ] Fiber test sonuçları, spesifikasyonlar ve mevcut DTO'lar stabil.
- [ ] Optimistic locking (DTO'lara `version`) isteniyorsa [OPTIMISTIC_LOCKING_TODO.md](OPTIMISTIC_LOCKING_TODO.md) ile planlandı.

### Aşama 2: Genişletme (Expand) migration
- [ ] **Veritabanı:** `production_execution_fiber_batch` → `production_execution_batch` (tablo rename).
- [ ] **Veritabanı:** `fiber_id` → `material_id` (kolon rename; FK `prod_material(id)`).
- [ ] **Veritabanı:** `attributes` (JSONB) kolonu ekle.
- [ ] **Veritabanı:** `attributes` üzerinde GIN index.
- [ ] **Veritabanı:** `location_id` (UUID, nullable) ekle; FK ekleme (IWM hazır olana kadar).
- [ ] `warehouse_location` kolonuna dokunulmadı (Aşama 5'e kadar kalacak).

### Aşama 3: Kod refactoring
- [ ] **Backend:** `FiberBatch` entity → `Batch`; paket/alanlar evrensel yapıya (materialId, attributes, locationId).
- [ ] **Backend:** Evrensel `BatchDto` (materialId, materialType, batchCode, quantity, availableQuantity, status, attributes, version).
- [ ] **Backend:** Controller/Service Batch entity ve BatchDto kullanacak şekilde güncellendi.
- [ ] **Frontend:** `BatchDto` + `FiberAttributes` / `YarnAttributes` interface'leri ve type guard'lar.
- [ ] **Frontend:** Fiber ekranları BatchDto ve attributes kullanacak şekilde refactor edildi.

### Aşama 4: Yarn modülüne başlangıç
- [ ] Yarn partileri için ayrı tablo yok; `production_execution_batch` kullanılıyor.
- [ ] Yarn attribute'ları (`twist_direction`, `yarn_count` vb.) JSONB `attributes` içinde.
- [ ] `BatchAttributeInheritancePort` tanımlandı; Yarn için implementasyon (Fiber→Yarn kalıtımı) eklenecek.
- [ ] BatchLineage: Fiber → Yarn ilişkisi aynı lineage tablosunda.

### Aşama 5: Daraltma (Contract) — IWM hazır olduğunda
- [ ] `warehouse_location` → `location_id` veri eşleme job/script çalıştırıldı.
- [ ] `location_id` üzerine FK constraint eklendi.
- [ ] `warehouse_location` kolonu kaldırıldı (DROP COLUMN).

# Cross-Module Lineage Strategy (End-to-End Traceability)

Bu doküman, FabricOS sisteminde "Pamuktan Tişörte" kadar uzanan kesintisiz izlenebilirlik (Traceability / Lineage) altyapısının nasıl kurulacağını ve yönetileceğini belirler. Geliştirme kuralları ve senaryolar bu dosyadadır.

İlgili teknik backlog (IWM stok entegrasyonu, CQRS/graph read model) için [LINEAGE_TODO.md](LINEAGE_TODO.md) dosyasına bakın.

## 1. Temel İlke: Evrensel Soy Ağacı (Universal Graph)

İzlenebilirlik sadece aynı tip malların bölünmesi (Örn: Elyaf Lotunun ikiye bölünmesi) olarak değil, **Farklı Modüller Arası Dönüşüm (Cross-Module Conversion)** olarak ele alınmalıdır.

*   `BatchLineage` tablosu, modülden bağımsız olarak her zaman iki `Batch` (Parti/Lot) arasındaki ilişkiyi temsil eder.
*   "Batch Genelleştirmesi" (Batch Generalization) sayesinde, `parentBatchId` ve `childBatchId` alanları herhangi bir malzemenin (Elyaf, İplik, Kumaş) lotunu işaret edebilir.

## 2. Dönüşüm Senaryoları (Conversion Scenarios)

Sistemdeki üretim süreçleri, `BatchLineage` tablosuna aşağıdaki gibi yansıtılmalıdır:

### Senaryo A: Eğirme (Spinning) - Elyaftan İpliğe
*   **İşlem:** 1000 kg Pamuk Elyafı (Lot-A) makineye girer, 950 kg Pamuk İpliği (Lot-B) çıkar.
*   **Lineage Kaydı:**
    *   `parentBatchId`: Lot-A (MaterialType: FIBER)
    *   `childBatchId`: Lot-B (MaterialType: YARN)
    *   `consumedQuantity`: 1000 kg
    *   `processReference`: "SPINNING_ORDER_123"

### Senaryo B: Harmanlama (Blending) - Çoklu Girdi, Tek Çıktı
*   **İşlem:** 600 kg Pamuk (Lot-A) ve 400 kg Polyester (Lot-B) karıştırılarak 1000 kg Harman İplik (Lot-C) üretilir.
*   **Lineage Kaydı 1:** `parent: Lot-A`, `child: Lot-C`, `consumedQty: 600`, `percentage: 60%`
*   **Lineage Kaydı 2:** `parent: Lot-B`, `child: Lot-C`, `consumedQty: 400`, `percentage: 40%`

### Senaryo C: Dokuma/Örme (Weaving/Knitting) - İplikten Kumaşa
*   **İşlem:** İplik (Lot-C) kullanılarak Ham Kumaş (Lot-D) üretilir.
*   **Lineage Kaydı:** `parent: Lot-C (YARN)`, `child: Lot-D (FABRIC)`

## 3. Geriye ve İleriye Dönük İzlenebilirlik (Traceability Queries)

Bu yapı sayesinde, veritabanında basit bir Graph (Ağaç) sorgusu veya Recursive CTE (Common Table Expression) kullanılarak uçtan uca izlenebilirlik sağlanır.

*   **Backward Trace (Geriye Dönük):** "Bu hatalı kumaş (Lot-D) hangi iplikten yapıldı? O iplik hangi pamuktan yapıldı?" (Kalite kontrol ve hata tespiti için kullanılır).
*   **Forward Trace (İleriye Dönük):** "Tedarikçiden aldığımız bu pamuk partisinde (Lot-A) zehirli madde çıktı. Bu pamuğu kullanarak hangi iplikleri ve kumaşları ürettik? Hangi müşterilere gönderdik?" (Geri çağırma / Recall operasyonları için kullanılır).

## 4. Geliştirme Kuralları (Development Guidelines)

### TODO

- [ ] **Üretim Sonu Onayı (Production Completion):** Herhangi bir üretim modülünde (İplik, Kumaş) üretim tamamlanıp yeni bir ürün (Child Batch) ortaya çıktığında, tüketilen hammaddeler (Parent Batches) ile yeni ürün arasına `BatchLineage` kaydı atmak zorunludur.
- [ ] **Bağımsızlık:** `BatchLineageService`, ne tür bir malzemenin dönüştürüldüğünü bilmek zorunda değildir. Sadece iki UUID (Batch ID) ve tüketim miktarını alıp ilişkiyi kurar.
- [ ] **UI/UX:** İzlenebilirlik raporları, kullanıcının bir Lot'a tıklayıp bir ağaç yapısı (Tree View veya Graph Node) şeklinde atalarını (Parents) ve çocuklarını (Children) görebileceği görsel bir bileşen olarak tasarlanmalıdır.

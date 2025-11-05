# 🔬 FIBER LABORATUVAR ÖLÇÜMLERİ KALDIRMA PLANI

**Tarih:** 2025-01-27  
**Durum:** ⚠️ DOMAIN MODEL HATASI  
**Manifesto:** CLEAN DOMAIN MODEL | SEPARATION OF CONCERNS

---

## 🎯 PROBLEM

**Fiber Entity = Katalog Tanımı (Definition)**

- Fiber sadece "bu lif türü nedir?" sorusuna cevap vermeli
- Sabit, değişmeyen tanımlayıcı bilgiler içermeli

**Mevcut Hata:**

- `fineness`, `lengthMm`, `strengthCndTex`, `elongationPercent` → **Laboratuvar/Üretim ölçümleri**
- Bu değerler her üretim partisinde değişir
- Fiber'ın tanımı değil, ölçüm sonuçları

---

## 📊 DOĞRU YAPILANDIRMA

### **Fiber Entity (Katalog Tanımı) - KALACAKLAR:**

- ✅ `fiberName` - Tanım adı
- ✅ `fiberGrade` - Sınıf (A, B, C gibi)
- ✅ `material` - Material ilişkisi
- ✅ `fiberCategory` - Kategori
- ✅ `fiberIsoCode` - ISO kodu
- ✅ `status` - Aktif/Pasif
- ✅ `remarks` - Genel açıklama
- ✅ `composition` - Karışım oranları (JSONB)

### **KALDIRILACAKLAR (Fiber'dan):**

- ❌ `fineness` → FiberBatch veya FiberTestResult
- ❌ `lengthMm` → FiberBatch veya FiberTestResult
- ❌ `strengthCndTex` → FiberBatch veya FiberTestResult
- ❌ `elongationPercent` → FiberBatch veya FiberTestResult

---

## 🏗️ YENİ YAPILANDIRMA

### **Seçenek 1: FiberBatch'e Ekle (Basit)**

```java
@Entity
public class FiberBatch extends BaseEntity {

    @Column(name = "fiber_id")
    private UUID fiberId;

    // Laboratuvar ölçümleri (her batch için)
    @Column(name = "fineness")
    private Double fineness;

    @Column(name = "length_mm")
    private Double lengthMm;

    @Column(name = "strength_cn_dtex")
    private Double strengthCndTex;

    @Column(name = "elongation_percent")
    private Double elongationPercent;

    // ... diğer alanlar
}
```

**Avantaj:** Basit, hızlı  
**Dezavantaj:** Her batch için tek ölçüm (laboratuvar testleri birden fazla olabilir)

### **Seçenek 2: FiberTestResult Entity (Önerilen)**

```java
@Entity
@Table(name = "production_quality_fiber_test_result")
public class FiberTestResult extends BaseEntity {

    @Column(name = "fiber_batch_id", nullable = false)
    private UUID fiberBatchId;

    @Column(name = "test_date", nullable = false)
    private Instant testDate;

    @Column(name = "test_type") // LABORATORY, PRODUCTION, INCOMING
    private String testType;

    // Ölçüm sonuçları
    @Column(name = "fineness")
    private Double fineness;

    @Column(name = "length_mm")
    private Double lengthMm;

    @Column(name = "strength_cn_dtex")
    private Double strengthCndTex;

    @Column(name = "elongation_percent")
    private Double elongationPercent;

    @Column(name = "test_lab")
    private String testLab;

    @Column(name = "test_standard") // ISO 1833, ASTM D7641, etc.
    private String testStandard;
}
```

**Avantaj:**

- ✅ Her batch için birden fazla test sonucu
- ✅ Test tarihi, laboratuvar, standart bilgisi
- ✅ İleriye dönük analiz ve raporlama
- ✅ Traceability (izlenebilirlik)

**Dezavantaj:** Daha fazla entity

---

## 📝 MİGRASYON PLANI

### **Adım 1: Yeni Tablo Oluştur**

```sql
CREATE TABLE production.production_quality_fiber_test_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,

    fiber_batch_id UUID NOT NULL REFERENCES production.production_execution_fiber_batch(id),
    test_date TIMESTAMP WITH TIME ZONE NOT NULL,
    test_type VARCHAR(50), -- LABORATORY, PRODUCTION, INCOMING

    fineness DOUBLE PRECISION,
    length_mm DOUBLE PRECISION,
    strength_cn_dtex DOUBLE PRECISION,
    elongation_percent DOUBLE PRECISION,

    test_lab VARCHAR(255),
    test_standard VARCHAR(100),

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_test_batch ON production.production_quality_fiber_test_result(fiber_batch_id);
CREATE INDEX idx_fiber_test_date ON production.production_quality_fiber_test_result(test_date);
```

### **Adım 2: Mevcut Veriyi Migrate Et**

```sql
-- Fiber'daki mevcut ölçümleri FiberBatch'e taşı (eğer varsa)
-- Veya direkt kaldır (çünkü bu ölçümler gerçek üretim partilerine ait olmalı)
```

### **Adım 3: Fiber Tablosundan Kaldır**

```sql
ALTER TABLE production.prod_fiber
DROP COLUMN IF EXISTS fineness,
DROP COLUMN IF EXISTS length_mm,
DROP COLUMN IF EXISTS strength_cn_dtex,
DROP COLUMN IF EXISTS elongation_percent;
```

---

## 🔧 KOD DEĞİŞİKLİKLERİ

### **Kaldırılacaklar:**

- ❌ `Fiber.fineness`
- ❌ `Fiber.lengthMm`
- ❌ `Fiber.strengthCndTex`
- ❌ `Fiber.elongationPercent`
- ❌ `Fiber.createPureFiber()` - bu parametreler kaldırılacak
- ❌ `Fiber.update()` - bu parametreler kaldırılacak
- ❌ `CreateFiberRequest` - bu alanlar kaldırılacak
- ❌ `FiberDto` - bu alanlar kaldırılacak

### **Yeni Entity:**

- ✅ `FiberTestResult` entity
- ✅ `FiberTestResultRepository`
- ✅ `FiberTestResultService`
- ✅ `FiberTestResultController` (opsiyonel)

---

## 💡 SONUÇ

**Domain Model Netliği:**

- ✅ Fiber = Katalog tanımı (sabit)
- ✅ FiberBatch = Üretim partisi (stok)
- ✅ FiberTestResult = Laboratuvar ölçümleri (değişken)

**Avantajlar:**

- ✅ Her batch için birden fazla test sonucu
- ✅ Test tarihi, laboratuvar, standart bilgisi
- ✅ İleriye dönük analiz ve raporlama kolay
- ✅ Traceability (izlenebilirlik)

**Manifesto Uyumu:**

- ✅ CLEAN DOMAIN MODEL
- ✅ SEPARATION OF CONCERNS
- ✅ KISS (her entity tek sorumluluğu)

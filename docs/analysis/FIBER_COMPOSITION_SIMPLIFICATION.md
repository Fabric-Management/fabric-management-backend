# 🔄 FIBER COMPOSITION BASİTLEŞTİRME PLANI

**Tarih:** 2025-01-27  
**Durum:** ⚠️ GEREKSİZ KARMAŞIKLIK  
**Manifesto:** KISS | YAGNI | DRY

---

## 📊 MEVCUT DURUM

### **Karmaşık Yapı:**

- `FiberComposition` (Junction Entity)
- `FiberCompositionId` (Composite Key)
- `FiberCompositionRepository` (5+ query method)
- `FiberCompositionService` (separate service)
- Ayrı tablo: `prod_fiber_composition`

### **Kullanım:**

- Sadece `Map<UUID, BigDecimal>` tutmak için (fiberId → percentage)
- %100 fiber = boş composition
- Karışım fiber = birkaç entry

---

## 🎯 BASİTLEŞTİRME: JSON/JSONB KOLON

### **Yeni Yapı:**

```java
@Entity
public class Fiber extends BaseEntity {

    // Basit JSONB kolon - Map<UUID, BigDecimal> direkt
    @Type(JsonType.class)
    @Column(name = "composition", columnDefinition = "jsonb")
    private Map<UUID, BigDecimal> composition;

    // Helper methods
    public Map<UUID, BigDecimal> getComposition() {
        return composition != null ? composition : Map.of();
    }

    public void setComposition(Map<UUID, BigDecimal> composition) {
        this.composition = composition;
    }

    public boolean isBlended() {
        return composition != null && !composition.isEmpty();
    }

    public boolean isPure() {
        return composition == null || composition.isEmpty();
    }
}
```

### **Avantajlar:**

- ✅ Tek kolon - ayrı tablo yok
- ✅ FiberComposition entity yok
- ✅ FiberCompositionService yok (direkt Fiber'da)
- ✅ Daha az kod, daha basit
- ✅ PostgreSQL JSON sorguları ile arama mümkün

### **Dezavantajlar:**

- ⚠️ Reverse lookup (findByBaseFiberId) JSON sorgusu ile yapılmalı
- ⚠️ Type safety biraz daha az (ama Map kullanımı aynı)

---

## 🔧 MİGRASYON PLANI

### **Adım 1: Yeni Kolon Ekle**

```sql
ALTER TABLE production.prod_fiber
ADD COLUMN composition JSONB;

-- Mevcut veriyi migrate et
UPDATE production.prod_fiber f
SET composition = (
    SELECT jsonb_object_agg(
        base_fiber_id::text,
        percentage::text
    )
    FROM production.prod_fiber_composition fc
    WHERE fc.blended_fiber_id = f.id
    AND fc.is_active = true
)
WHERE EXISTS (
    SELECT 1 FROM production.prod_fiber_composition fc
    WHERE fc.blended_fiber_id = f.id
);
```

### **Adım 2: Index Ekle (JSON sorguları için)**

```sql
CREATE INDEX idx_fiber_composition_gin ON production.prod_fiber
USING GIN (composition);
```

### **Adım 3: Eski Tabloyu Kaldır**

```sql
DROP TABLE IF EXISTS production.prod_fiber_composition CASCADE;
```

---

## 📝 KOD DEĞİŞİKLİKLERİ

### **Kaldırılacaklar:**

- ❌ `FiberComposition.java`
- ❌ `FiberCompositionId.java`
- ❌ `FiberCompositionRepository.java`
- ❌ `FiberCompositionService.java`
- ❌ `FiberCompositionChangedEvent.java`

### **Güncellenecekler:**

- ✅ `Fiber.java` - composition kolonu ekle
- ✅ `FiberService.java` - compositionService kullanımını kaldır
- ✅ `FiberValidationService.java` - direkt composition kullan
- ✅ `FiberDto.java` - composition mapping

---

## 🎯 SONUÇ

**Basitleştirme:**

- 5 dosya kaldırılacak
- 1 kolon eklenecek
- Kod %40 daha basit

**YAGNI Prensibi:**

- Junction table gereksiz karmaşıklık
- JSON/JSONB yeterli ve pratik

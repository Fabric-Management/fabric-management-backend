# Code Review Raporu — Batch Inheritance & Universal Batch (Chat Güncellemeleri)

## Özet

İncelenen değişiklikler: **BatchAttributes**, **BatchAttributeInheritancePort**, **BatchAttributeInheritanceEngine**, **FiberToYarnInheritanceTest**, **CreateBatchRequest**, **BatchService.resolveAttributes**, **FiberService.assertFiberMaterialType**. Genel kalite iyi; kritik güvenlik veya bug yok. Tespit edilenler: kullanılmayan import (Engine), DRY ihlali (RequireEqual/PassThrough aynı mantık), switch’te ölü DROP case’i ve tip güvenliği için toBigDecimal edge case’i. Önerilen düzeltmeler aşağıda ve sonunda refactor snippet’lerle verilmiştir.

---

**Tüm maddeler (orta + düşük) uygulandı:** Engine (import, anyMatch, DRY, toBigDecimal, switch default), BatchAttributes (defensive copy), FiberService (assertFiberMaterialType JavaDoc), applySingleValueOrConflict (distinct/scale notu).

## 1. Ölü ve İşlevsiz Kodlar

- **[Orta]** **BatchAttributeInheritanceEngine — Kullanılmayan import:** `java.util.stream.Collectors` import edilmiş ancak hiçbir yerde kullanılmıyor. Gereksiz bağımlılık ve lint uyarısı kaynağı. **Öneri:** Import satırını kaldır.

- **[Düşük]** **BatchAttributeInheritanceEngine — Switch içinde ulaşılamayan DROP case:** `resolveInheritedAttributes` içinde DROP aksiyonu for döngüsünün başında `continue` ile atlanıyor; switch’e hiç DROP ile gelinmiyor. `case DROP -> { /* already handled above */ }` dalı ölü kod. **Öneri:** Switch’ten DROP case’ini kaldır; sadece gerçekten kullanılan aksiyonlar kalsın (veya switch yerine if-else / strategy ile sadeleştirilebilir).

---

## 2. Kod Kalitesi ve Temiz Kod

- **[Orta]** **BatchAttributeInheritanceEngine — DRY ihlali (applyRequireEqual / applyPassThrough):** `applyRequireEqual` ile `applyPassThrough` gövde olarak birebir aynı: distinct değerleri topla, 0 ise çık, 1 ise yaz, >1 ise `BatchDomainException` at. Tekrarlanan mantık bakım maliyetini ve hata riskini artırıyor. **Öneri:** Ortak bir private metod çıkar (örn. `applySingleValueOrConflict`) ve her iki aksiyon için bu metodu çağır.

- **[Düşük]** **BatchAttributeInheritanceEngine — valuesWithQuantity isimlendirmesi:** `collectSourceValues` sonucu sadece `isEmpty()` için kullanılıyor; “values with quantity” ifadesi yanıltıcı (liste içeriği kullanılmıyor). **Öneri:** İsimlendirmeyi amaca uygun yap: örn. `parentsWithAttribute` veya doğrudan `hasAnyParentWithAttribute(parentAttributes, rule.sourceAttribute())` gibi bir predicate ile kontrol et; böylece gereksiz liste oluşturmayı da kaldırabilirsin (aşağıda refactor’ta örnek var).

- **[Düşük]** **FiberService — assertFiberMaterialType kullanımı:** Guard metot tanımlı ancak şu an hiçbir yerde çağrılmıyor. FiberService batch üretmiyor; ileride Batch yükleyip fiber’e özel iş yapan bir metot eklendiğinde kullanılacak. **Öneri:** Kullanım planı netse dokümante et (class-level veya metot JavaDoc); aksi halde ilk kullanım noktası eklendiğinde çağrıyı ekle.

---

## 3. Tip Güvenliği ve Hata Yönetimi

- **[Orta]** **BatchAttributeInheritanceEngine — toBigDecimal(Object) edge case:** `value` Number/BigDecimal dışında (örn. Map, List, String "abc") gelirse `new BigDecimal(value.toString())` `NumberFormatException` fırlatır. JSONB’den gelen attributes’ta yanlış tip mümkün. **Öneri:** Ya (a) JavaDoc’ta “caller must ensure numeric attribute values for WEIGHTED_AVERAGE/MIN/MAX” yaz, ya (b) try-catch ile `NumberFormatException` yakalayıp o parent’ı skip et / veya kuralı skip et, ya (c) `value instanceof Number` (ve BigDecimal) kontrolü ile sadece sayısal tipleri kabul et; diğerlerinde net bir hata mesajı veya skip. Tercih: (c) + kısa JavaDoc.

- **[Düşük]** **REQUIRE_EQUAL / PASS_THROUGH — distinct() ve BigDecimal:** `Object` üzerinde `distinct()` equals() kullanır. İki parent’ta aynı sayı farklı scale ile (4.0 vs 4.00) gelirse iki farklı değer sayılıp “inconsistent values” hatası çıkabilir. **Öneri:** Şimdilik dokümante et; ileride sayısal attribute’lar için normalize (örn. BigDecimal’a çevirip scale’i eşitle) veya rule’da “numeric comparison” seçeneği eklenebilir.

- **[Düşük]** **BatchAttributes — attributes defensive copy:** Compact constructor’da `attributes == null` için `emptyMap()` kullanılıyor; non-null için referans aynen saklanıyor. Çağıran sonradan map’i değiştirirse record’un davranışı değişir. **Öneri:** İmmutability gerekiyorsa `attributes = Map.copyOf(attributes)` (veya new HashMap(attributes)) ile kopya al; performans kritikse ve “read-only snapshot” sözleşmesi varsa mevcut hali + JavaDoc yeterli.

---

## 4. Performans ve Güvenlik

- **[Düşük]** **BatchAttributeInheritanceEngine — collectSourceValues sadece isEmpty için:** Tüm parent’lar üzerinde dönüp liste dolduruluyor; tek ihtiyaç “en az bir parent’ta bu key var mı?”. **Öneri:** `parents.stream().anyMatch(p -> p.attributes().containsKey(sourceKey))` ile erken çıkış; büyük parent listelerinde gereksiz liste allocation’ı kalkar.

- **Güvenlik / log:** Loglarda sadece parent sayısı ve source/target type var; attribute değerleri veya PII loglanmıyor. Hassas veri sızıntısı riski yok.

---

## Düzeltilmiş / Refactor Edilmiş Kod

### 1. BatchAttributeInheritanceEngine — Unused import kaldırma

```java
// Kaldır:
import java.util.stream.Collectors;
```

### 2. BatchAttributeInheritanceEngine — DROP case kaldırma

```java
switch (rule.action()) {
    case WEIGHTED_AVERAGE -> applyWeightedAverage(result, rule, parentAttributes);
    case MIN -> applyMin(result, rule, parentAttributes);
    case MAX -> applyMax(result, rule, parentAttributes);
    case COLLECT_TO_ARRAY -> applyCollectToArray(result, rule, parentAttributes);
    case REQUIRE_EQUAL -> applyRequireEqual(result, rule, parentAttributes);
    case PASS_THROUGH -> applyPassThrough(result, rule, parentAttributes);
    // DROP already handled above; no case needed
}
```

### 3. BatchAttributeInheritanceEngine — DRY: RequireEqual / PassThrough ortak metot

```java
private void applyRequireEqual(Map<String, Object> result, InheritanceRule rule, List<BatchAttributes> parents) {
    applySingleValueOrConflict(result, rule, parents);
}

private void applyPassThrough(Map<String, Object> result, InheritanceRule rule, List<BatchAttributes> parents) {
    applySingleValueOrConflict(result, rule, parents);
}

/**
 * Puts the source attribute value into the result if all parents have the same value;
 * otherwise throws BatchDomainException (for REQUIRE_EQUAL / PASS_THROUGH).
 */
private void applySingleValueOrConflict(Map<String, Object> result, InheritanceRule rule, List<BatchAttributes> parents) {
    List<Object> distinct = parents.stream()
            .map(p -> p.attributes().get(rule.sourceAttribute()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (distinct.isEmpty()) return;
    if (distinct.size() == 1) {
        result.put(rule.targetAttribute(), distinct.get(0));
        return;
    }
    throw new BatchDomainException(
            "Attribute inheritance conflict: attribute '" + rule.sourceAttribute()
                    + "' has inconsistent values across parent batches: " + distinct);
}
```

### 4. BatchAttributeInheritanceEngine — isEmpty kontrolü için anyMatch (isteğe bağlı)

```java
// collectSourceValues kullanımı yerine:
boolean hasAttribute = parentAttributes.stream()
        .anyMatch(p -> p.attributes().containsKey(rule.sourceAttribute()));
if (!hasAttribute) {
    log.warn("Skipping rule: no parent has attribute '{}' (action: {})",
            rule.sourceAttribute(), rule.action());
    continue;
}
// collectSourceValues metodunu artık sadece apply* metodları kullanıyorsa koruyabilir veya kaldırabilirsin
```

### 5. toBigDecimal — Tip kontrolü (isteğe bağlı)

```java
/**
 * Safe numeric extraction: values in the map may be Double, Integer, Float, or BigDecimal.
 * Non-numeric types throw IllegalArgumentException.
 */
private BigDecimal toBigDecimal(Object value) {
    if (value instanceof BigDecimal bd) {
        return bd;
    }
    if (value instanceof Number num) {
        return BigDecimal.valueOf(num.doubleValue());
    }
    throw new IllegalArgumentException("Attribute value is not numeric: " + value);
}
```

---

## Özet Tablo

| Önem    | Konu                                      | Dosya / Yer                          |
|--------|-------------------------------------------|--------------------------------------|
| Orta   | Kullanılmayan import                      | BatchAttributeInheritanceEngine      |
| Orta   | DRY: RequireEqual / PassThrough aynı kod | BatchAttributeInheritanceEngine      |
| Orta   | toBigDecimal non-numeric edge case       | BatchAttributeInheritanceEngine      |
| Düşük  | Switch’te ölü DROP case                   | BatchAttributeInheritanceEngine      |
| Düşük  | valuesWithQuantity isimlendirme / anyMatch | BatchAttributeInheritanceEngine    |
| Düşük  | assertFiberMaterialType henüz kullanılmıyor | FiberService                      |
| Düşük  | distinct() ve BigDecimal scale           | Engine (dokümante edilebilir)        |
| Düşük  | attributes defensive copy                | BatchAttributes (isteğe bağlı)       |

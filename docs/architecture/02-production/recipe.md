# Recipe — Üretim Reçetesi

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Recipe ve RecipeComponent entity'leri burada tanımlanır.

---

## Genel Bakış

Recipe, blend karışımının tanımıdır. Kullanıcı component ekler; `name` ve `isoCode` sistem tarafından otomatik üretilir. Recipe saf bir **tanım entity'sidir** — maliyet bilgisi taşımaz. Maliyet hesaplaması CostCalculation service'inde yapılır.

---

## 1. Recipe

> Tablo: `production.prod_recipe`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `name` | String (500) | Evet | **Otomatik** — `"60% Cotton GOTS TR, 40% Abaca GOTS PH"` |
| `isoCode` | String (255) | Evet | **Otomatik** — `"CO 60 / AF 40"` |
| `components` | JSONB | Evet | Fiber bileşen listesi — bkz. Components Yapısı |
| `status` | RecipeStatus (Enum) | Evet | ACTIVE / ARCHIVED |
| `version` | Integer | Evet | 1, 2, 3... |
| `parentRecipeId` | UUID | Hayır | Versiyonlama — düzenlenen recipe'nin atası |

> **Maliyet notu:** Recipe'ye `unitCost` alanı **eklenmeyecek**. Aynı recipe farklı tedarikçilerden farklı fiyatlarla üretilebilir — maliyet, CostCalculation service'inde PriceList verilerinden hesaplanır.  
> Detay: `06-costing/cost-calculation.md`

### Components JSONB Yapısı

```json
[
  {
    "fiberId": "uuid-cotton",
    "fiberName": "Cotton",
    "fiberIsoCode": "CO",
    "percentage": 60.00,
    "certification": "GOTS",
    "origin": "TR"
  },
  {
    "fiberId": "uuid-abaca",
    "fiberName": "Abaca",
    "fiberIsoCode": "AF",
    "percentage": 40.00,
    "certification": "GOTS",
    "origin": "PH"
  }
]
```

**Validasyon:** `percentage` toplamı 100 olmalı — backend zorunlu kontrol.  
**Max component:** Önerilir ≤ 6 — UI'da belirlenecek.

---

## 2. RecipeComponent (Ayrı Tablo — Hibrit Yaklaşım)

> Tablo: `production.prod_recipe_component`  
> `BaseEntity`'den miras alır.

**Neden ayrı tablo?** JSONB hızlı okuma için kalır (UI'da recipe kartı gösterme). Ayrı tablo hızlı sorgulama için (raporlama, RuleEngine filtreleme). Bu bir denormalizasyon değil, sorumluluk ayrımı.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `recipeId` | UUID | Evet | FK → Recipe |
| `fiberId` | UUID | Evet | FK → Fiber |
| `fiberName` | String (255) | Evet | Snapshot — Fiber adı |
| `fiberIsoCode` | String (10) | Evet | Snapshot — ISO kodu |
| `percentage` | Decimal | Evet | Oran — 0.01 ile 100.00 arası |
| `certification` | String (50) | Hayır | GOTS, BCI, OEKO-TEX... |
| `origin` | String (10) | Hayır | Menşei ülke kodu — TR, IN, PH... |
| `displayOrder` | Integer | Evet | Sıralama |

**Unique kısıt:** `(recipe_id, fiber_id)` — aynı recipe'de aynı fiber iki kez eklenemez.

**Index stratejisi:**
```sql
CREATE INDEX idx_recipe_component_certification 
  ON production.prod_recipe_component (certification);
CREATE INDEX idx_recipe_component_origin 
  ON production.prod_recipe_component (origin);
```

> **Kural:** Recipe oluşturulurken hem `components` JSONB hem `RecipeComponent` kayıtları aynı transaction'da yazılır. Güncelleme de aynı şekilde — iki veri kaynağı her zaman senkron.

---

## Otomatik `name` Üretim Kuralı

```
{percentage}% {fiberName} {certification} {origin}
— yüksekten düşüğe sıralı, virgülle ayrılmış
```
Örnek: `"60% Cotton GOTS TR, 40% Abaca GOTS PH"`

## Otomatik `isoCode` Üretim Kuralı

```
{fiberIsoCode} {percentage} / {fiberIsoCode} {percentage}
```
Örnek: `"CO 60 / AF 40"`

---

## Otomatik Eşleşme Mantığı

Kullanıcı component ekledikçe sistem arka planda arar:

```
Tüm componentlerin (fiberId + percentage + certification + origin) eşleştiği
ve component sayısının aynı olduğu ACTIVE Recipe var mı?
```

- Varsa → modal altında gösterilir, kullanıcı uyarılır
- Yoksa → **Create** veya **Create & Go WorkOrder** butonu aktif olur

---

## Versiyonlama

Kullanıcı mevcut bir recipe'yi düzenlediğinde:

1. Mevcut recipe `ARCHIVED` olur
2. Yeni recipe oluşturulur, `version` bir artar, `parentRecipeId` eskisini gösterir
3. Eski WorkOrder'lar eski recipe'ye bağlı kalır — geriye dönük etki yok

```
Recipe V1 (ARCHIVED) ← parentRecipeId
    ↑
Recipe V2 (ACTIVE) — düzenlenmiş bileşenler
```

---

## İlişki Özeti

```
Recipe ──→ RecipeComponent (1:N)
  │              └──→ Fiber (FK)
  │
  ├──→ WorkOrder (1:N) — bir recipe birden fazla iş emrinde kullanılabilir
  ├──→ Recipe.parentRecipeId ──→ Recipe (self) — versiyon zinciri
  └──→ CostCalculation (1:N) — maliyet hesabı (ESTIMATED aşamasında)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/material-fiber.md` | Fiber entity — RecipeComponent.fiberId FK |
| `02-production/work-order.md` | WorkOrder.recipeId → Recipe FK |
| `03-sales/sales-order.md` | RuleEngine recipe eşleştirme — SalesOrderLine → Recipe |
| `06-costing/cost-calculation.md` | CostCalculation (ESTIMATED) — recipe bileşenlerinden maliyet hesabı |
| `11-cross-cutting/jsonb-strategy.md` | Recipe.components JSONB + RecipeComponent ayrı tablo kararı |

---

## Açık Kararlar

- [ ] Component max limiti — UI'da kaç component eklenebilir?
- [ ] Recipe versiyonlama — kullanıcıya versiyon geçmişi gösterilecek mi?
- [ ] RuleEngine recipe eşleştirme mantığı tekrar değerlendirilecek

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — RecipeComponent ayrı tablo kararı, unitCost eklenmeme kararı |

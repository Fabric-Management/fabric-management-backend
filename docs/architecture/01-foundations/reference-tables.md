# Referans Tabloları

> Modül: Temel Yapılar (01-foundations)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: CertificationType, FiberCategory, FiberIsoCode burada tanımlanır.

---

## Genel Bakış

Referans tabloları sistem tarafından tanımlanır. Tenant'lar oluşturamaz veya güncelleyemez; yalnızca `isActive` durumu değiştirilebilir. Seed verisi migrasyon dosyalarında yönetilir.

---

## 1. CertificationType

> Tablo: `production.prod_certification_type`  
> `BaseEntity`'den miras alır.  
> Eski adı: `FiberCertification` — sadece fiber değil, partner ve batch tarafında da kullanıldığı için yeniden adlandırıldı.

| Alan | Tip | Açıklama |
|---|---|---|
| `certificationCode` | String (50) | Benzersiz kod, güncellenemez — GOTS, OEKO-TEX, BCI... |
| `certificationName` | String (100) | Sertifika adı |
| `certifyingBody` | String (255) | Sertifika veren kurum |
| `description` | String (TEXT) | Açıklama |
| `displayOrder` | Integer | Sıralama |

**Kullanım yerleri:**
- `PartnerCertification.certificationTypeId` → sertifika tipi (bkz. `trading-partner.md`)
- `Recipe.components[].certification` → reçete bileşen sertifikası (bkz. `02-production/recipe.md`)
- `SalesOrderLine.moduleSpecs.certificationReq` → müşteri sertifika kısıtı (bkz. `03-sales/sales-order.md`)

---

## 2. FiberCategory

> Tablo: `production.prod_fiber_category`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Açıklama |
|---|---|---|
| `categoryCode` | String (50) | Benzersiz kategori kodu, güncellenemez |
| `categoryName` | String (100) | Kategori adı |
| `description` | String (TEXT) | Açıklama |
| `displayOrder` | Integer | Sıralama |

**Seed verisi (8 kategori):**

| # | Code | Name | Açıklama |
|---|---|---|---|
| 1 | `NATURAL_PLANT` | Natural Plant | Cotton, Linen, Hemp, Jute... |
| 2 | `NATURAL_ANIMAL` | Natural Animal | Wool, Silk, Cashmere, Alpaca... |
| 3 | `REGENERATED_CELLULOSIC` | Regenerated Cellulosic | Viscose, Modal, Lyocell, Acetate... |
| 4 | `REGENERATED_PROTEIN` | Regenerated Protein | Soy, Milk, Chitin... |
| 5 | `SYNTHETIC_POLYMER` | Synthetic Polymer | Polyester, Nylon, Polypropylene... |
| 6 | `TECHNICAL_ADVANCED` | Technical & Advanced | Carbon, Aramid, Ceramic... |
| 7 | `MINERAL` | Mineral | Asbestos, Glass Fiber... |
| 8 | `MIXED_BLEND` | Mixed Blend | Farklı kökenli karışımlar |

**Kullanım yerleri:**
- `Fiber.fiberCategoryId` → lif kategorisi (bkz. `02-production/material-fiber.md`)
- `FiberIsoCode.fiberType` → ISO kodu kategori eşleşmesi (aşağıda)

---

## 3. FiberIsoCode

> Tablo: `production.prod_fiber_iso_code`  
> `BaseEntity`'den miras alır.  
> ISO 2076 standardına göre lif kodları.

| Alan (DB) | Java Alanı | Tip | Açıklama |
|---|---|---|---|
| `iso_code` | `isoCode` | String (10) | Benzersiz, güncellenemez — ISO 2076 kodu |
| `fiber_name` | `fiberName` | String (255) | Lif adı |
| `fiber_type` | `fiberType` | String (100) | Lif tipi — `FiberCategory.categoryCode` ile eşleşir |
| `description` | `description` | String (TEXT) | Açıklama |
| `is_official_iso` | `isOfficialIso` | Boolean | Resmi ISO 2076 kaydı mı |
| `display_order` | `displayOrder` | Integer | Sıralama |

**Indeksler:**
```sql
CREATE INDEX idx_fiber_iso_code ON production.prod_fiber_iso_code (iso_code);
CREATE INDEX idx_fiber_iso_active ON production.prod_fiber_iso_code (is_active);
```

**Seed verisi:** 52 kayıt — tümü `is_official_iso = TRUE`. `V010__SEEDS.sql` dosyasında, `ON CONFLICT (uid) DO NOTHING` ile idempotent.

| iso_code | fiber_name | fiber_type |
|---|---|---|
| `CO` | Cotton | NATURAL_PLANT |
| `LI` | Linen | NATURAL_PLANT |
| `WO` | Wool | NATURAL_ANIMAL |
| `SE` | Silk | NATURAL_ANIMAL |
| `CV` | Viscose | REGENERATED_CELLULOSIC |
| `PES` | Polyester | SYNTHETIC_POLYMER |
| `PA` | Polyamide (Nylon) | SYNTHETIC_POLYMER |
| ... | ... (toplam 52 kayıt) | ... |

**API:**
- `FiberIsoCodeService.findAll(baseOnly=true)` → sadece `is_official_iso=true` olan 52 kayıt
- `FiberIsoCodeService.findAll(baseOnly=false)` → tüm aktif kayıtlar

**Seed bağlantısı:** `R__001__fiber_seeds.sql` bu 52 ISO kaydını kullanarak her biri için Material + Fiber oluşturur (UID: `SYS-FIB-000001`, `SYS-FIB-000002`...).

**Kullanım yerleri:**
- `Fiber.fiberIsoCodeId` → lif ISO kodu (bkz. `02-production/material-fiber.md`)
- `Recipe.components[].fiberIsoCode` → reçete bileşen kodu (bkz. `02-production/recipe.md`)

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/base-entity.md` | Tüm entity'ler BaseEntity'den miras alır |
| `02-production/material-fiber.md` | Fiber → FiberCategory, FiberIsoCode FK'ları |
| `01-foundations/trading-partner.md` | PartnerCertification → CertificationType FK |
| `02-production/recipe.md` | Recipe.components JSONB içinde referans |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — FiberIsoCode gerçek koddan eklendi, seed verisi belgelendi |

---

## 4. FiberAttribute

> Tablo: `production.prod_fiber_attribute`  
> `BaseEntity`'den miras alır.  
> Sistem tanımlı — lif özellikleri.

Fiber'a atanabilir özellik etiketleri: ORGANIC, RECYCLED, VIRGIN, FAIR_TRADE vb.

**Kullanım:** Fiber entity'sinde referans olarak, katalog ve filtreleme'de.

---

## 5. FiberCertification (Referans)

> Tablo: `production.prod_fiber_certification`  
> `BaseEntity`'den miras alır.  
> Sistem tanımlı — fiber'a özgü sertifika referansları.

Fiber bazlı sertifika referansları: GOTS, OEKO-TEX, BCI vb. `CertificationType` ile benzer ama fiber modülüne özgü UI referansı olarak kullanılır.

> **Not:** `CertificationType` partner ve batch sertifikaları için genel referans. `FiberCertification` ise fiber kataloğu UI'ında filtreleme ve etiketleme için kullanılır.

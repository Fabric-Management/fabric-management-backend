# Material & Fiber

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Material ve Fiber entity'leri burada tanımlanır.

---

## Genel Bakış

Material tüm malzeme tiplerinin (fiber, yarn, fabric, chemical, consumable) üst entity'sidir. Fiber saf hammadde tanımıdır — blend bilgisi taşımaz, blend mantığı `Recipe.components` içinde yaşar.

Şu an **Fiber modülü** geliştirme aşamasında. Yarn, Fabric ve diğer Material alt tipleri sırasıyla eklenecek.

---

## 1. Material

> Tablo: `production.prod_material`  
> Sınıf: `com.fabricmanagement.production.masterdata.material.domain.Material`  
> `BaseEntity`'den miras alır.

| Alan (DB) | Java Alanı | Tip | Zorunlu | Açıklama |
|---|---|---|---|---|
| `material_type` | `materialType` | MaterialType (Enum) | Evet | FIBER / YARN / FABRIC / CHEMICAL / CONSUMABLE |
| `unit` | `unit` | String (20) | Evet | Birim — KG, MT, PIECE vb. |

### MaterialType Enum

| Değer | Alt Entity | Durum |
|---|---|---|
| `FIBER` | Fiber | Aktif geliştirme |
| `YARN` | (ileride) | Planlandı |
| `FABRIC` | (ileride) | Planlandı |
| `CHEMICAL` | (ileride) | Planlandı |
| `CONSUMABLE` | (ileride) | Planlandı |

**Kullanım:** Material, modüller arası ortak "ürün" katmanı olarak çalışır. SalesOrderLine, StockTransaction, StockLedger gibi entity'ler `materialId` FK'sı ile Material'a bağlanır — bu sayede modülden bağımsız stok ve sipariş yönetimi mümkün olur.

---

## 2. Fiber

> Tablo: `production.prod_fiber`  
> Sınıf: `com.fabricmanagement.production.masterdata.fiber.domain.Fiber`  
> `BaseEntity`'den miras alır.

Saf hammadde tanımı. Blend bilgisi taşımaz — blend mantığı `Recipe.components` JSONB içinde yaşar.

| Alan (DB) | Java Alanı | Tip | Zorunlu | Açıklama |
|---|---|---|---|---|
| `material_id` | `material` | Material (OneToOne) | Evet | 1:1 — Her lif bir materyale bağlı |
| `fiber_category_id` | `fiberCategory` | FiberCategory (ManyToOne) | Hayır | Lif kategorisi |
| `fiber_iso_code_id` | `fiberIsoCode` | FiberIsoCode (ManyToOne) | Hayır | ISO 2076 kodu |
| `fiber_name` | `fiberName` | String (255) | Evet | Lif adı |
| `fiber_grade` | `fiberGrade` | String (50) | Hayır | Lif sınıfı |
| `status` | `status` | FiberStatus (Enum) | Evet | ACTIVE / OBSOLETE — varsayılan ACTIVE |
| `remarks` | `remarks` | String (TEXT) | Hayır | Notlar |
| `composition` | `composition` | JSONB (Map) | Hayır | Karışım oranları: baseFiberId → yüzde |

### FiberStatus Enum

| Değer | Açıklama |
|---|---|
| `ACTIVE` | Kullanımda |
| `OBSOLETE` | Artık kullanılmıyor |

### composition Alanı Hakkında

> **Not:** `composition` JSONB alanı mevcut kodda hâlâ var. Mimari kararla blend mantığı `Recipe.components` içine taşındı. Bu alan geriye dönük uyumluluk için saklanıyor — yeni kodda kullanılmamalı, ileride kaldırılacak.

### Helper Metodlar

| Metod | Açıklama |
|---|---|
| `getMaterialId()` | Material FK id'si |
| `getFiberCategoryId()` | FiberCategory FK id'si |
| `getFiberIsoCodeId()` | FiberIsoCode FK id'si |

### Seed Verisi

`R__001__fiber_seeds.sql` ile 52 adet FiberIsoCode kaydından her biri için otomatik Material + Fiber oluşturulur:
- UID: `SYS-FIB-000001`, `SYS-FIB-000002`...
- Her birinin Material kaydı `materialType = FIBER`
- Bu seed kayıtları sistem tanımlıdır — tenant değiştiremez

---

## İlişki Özeti

```
Material (materialType = FIBER)
  └──→ Fiber (1:1)
        ├──→ FiberCategory (ManyToOne)
        └──→ FiberIsoCode (ManyToOne)

Material ←── SalesOrderLine.materialId (sipariş kalemi)
Material ←── StockLedger.materialId (stok bakiyesi)
Material ←── StockTransaction.materialId (stok hareketi)
Material ←── Recipe.components[].fiberId (reçete bileşeni)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/reference-tables.md` | FiberCategory, FiberIsoCode referansları |
| `02-production/recipe.md` | Recipe.components[].fiberId → Fiber |
| `03-sales/sales-order.md` | SalesOrderLine.materialId → Material |
| `05-iwm/stock-transaction-ledger.md` | StockTransaction.materialId, StockLedger.materialId |

---

## Açık Kararlar

- [ ] `composition` JSONB alanının kaldırılma zamanlaması — migration planı
- [ ] Yarn entity tanımı — Fiber modülü tamamlandıktan sonra
- [ ] Material'a ortak alanlar eklenecek mi (ör. defaultUnit, category)?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek koddan türetildi, composition durumu belgelendi |

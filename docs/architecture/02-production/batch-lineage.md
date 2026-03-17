# BatchLineage — İzlenebilirlik & Attribute Inheritance

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: BatchLineage entity'si ve AttributeInheritanceEngine burada tanımlanır.

---

## Genel Bakış

BatchLineage parent batch → child batch tüketim ilişkilerini tutarak **"bir adım geri / bir adım ileri"** izlenebilirlik sağlar. ISO 22005 ve EU Textile Regulation 1007/2011 gereksinimlerini karşılar. Child batch oluşturulduğunda parent'lardan attribute'lar **otomatik hesaplanabilir** (blend ratio, kalite parametreleri vb.).

### Ne Çözer?

- "Bu kumaş hangi ipliklerden yapıldı?" → trace back
- "Bu liften hangi ürünler çıktı?" → trace forward
- "GOTS sertifikalı mı?" → sertifika zinciri izlenebilirlik
- "Blend oranları nedir?" → attribute inheritance

---

## 1. BatchLineage

> Tablo: `production.production_execution_batch_lineage`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `parentBatchId` | UUID | Evet | FK → Batch (kaynak — tüketilen batch) |
| `childBatchId` | UUID | Evet | FK → Batch (çıktı — üretilen batch) |
| `consumedQuantity` | Decimal | Evet | Parent'tan tüketilen miktar |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `consumptionPercentage` | Decimal | Hayır | Child'daki oran (%) |
| `consumedAt` | Timestamp | Evet | Tüketim zamanı |
| `processReference` | String | Hayır | Üretim süreç referansı (WorkOrder no vb.) |
| `remarks` | String (TEXT) | Hayır | Notlar |

---

## İzlenebilirlik Sorguları

### Trace Back (Child → Parent'lar)

"Bu kumaş topunun hammaddeleri neler?"

```
Fabric Batch (child)
    ↑
Yarn Batch A (parent) — 60% tüketim
    ↑
Fiber Batch X (grandparent) — Cotton GOTS TR
Fiber Batch Y (grandparent) — Polyester

Yarn Batch B (parent) — 40% tüketim
    ↑
Fiber Batch Z (grandparent) — Cotton BCI IN
```

API: `GET /api/production/batch-lineage/trace-back/{childBatchId}`  
Dönen: Ağaç yapısında tüm ancestor'lar, her seviyede tüketim miktarı ve oranı.

### Trace Forward (Parent → Child'lar)

"Bu fiber lotundan hangi ürünler çıktı?"

```
Fiber Batch X (parent)
    ↓
Yarn Batch A (child) — 500kg tüketildi
    ↓
Fabric Batch 1 (grandchild) — 200 mt
Fabric Batch 2 (grandchild) — 150 mt
```

API: `GET /api/production/batch-lineage/trace-forward/{parentBatchId}`

---

## 2. Attribute Inheritance Engine

Child batch oluşturulduğunda, parent batch'lerin attribute'larından child attribute'ları **otomatik hesaplama**.

### Inheritance Kuralları

| Kural | Açıklama | Örnek |
|---|---|---|
| `WEIGHTED_AVERAGE` | Tüketim oranına göre ağırlıklı ortalama | fiber_micronaire: parent A (4.2, %60) + parent B (3.8, %40) → 4.04 |
| `MIN` | En düşük değer | fiber_strength: parent'lar arasında en düşük |
| `MAX` | En yüksek değer | — |
| `COLLECT_TO_ARRAY` | Tüm parent değerlerini diziye topla | certifications: ["GOTS", "BCI"] |
| `REQUIRE_EQUAL` | Tüm parent'larda aynı olmalı, farklıysa hata | origin_country: hepsi "TR" olmalı |
| `DROP` | Child'a taşınmaz | parent'a özel alan |
| `PASS_THROUGH` | İlk parent'ın değeri doğrudan geçer | — |

### Schema Yapısı

`AttributeInheritanceSchema` konfigürasyonu ile her attribute için hangi kuralın uygulanacağı tanımlanır:

```json
{
  "fiber_micronaire": "WEIGHTED_AVERAGE",
  "fiber_strength": "MIN",
  "origin_country": "REQUIRE_EQUAL",
  "certification": "COLLECT_TO_ARRAY",
  "fiber_grade": "DROP"
}
```

`AttributeInheritanceSchemaLoader` bu konfigürasyonu yükler. `BatchAttributeInheritanceEngine` hesaplamayı yapar.

### Hesaplama Akışı

```
Lineage oluşturulur (parent A %60 + parent B %40 → child C)
    ↓
BatchAttributeInheritanceEngine çalışır
    ↓
Her attribute için schema'dan kural bulunur
    ↓
Kural uygulanır:
  fiber_micronaire → WEIGHTED_AVERAGE → 4.2×0.6 + 3.8×0.4 = 4.04
  fiber_strength → MIN → min(30.1, 28.5) = 28.5
  certification → COLLECT_TO_ARRAY → ["GOTS", "BCI"]
    ↓
Child batch attributes JSONB güncellenir
```

---

## Domain Event'ler

| Event | Tetikleyen |
|---|---|
| `BatchLineageCreatedEvent` | Yeni lineage ilişkisi oluşturulduğunda |
| `BatchLineageDeletedEvent` | Lineage ilişkisi silindiğinde |

---

## API

Base path: `/api/production/batch-lineage`

| Endpoint | Metod | Açıklama |
|---|---|---|
| `/` | POST | Lineage oluştur (parent → child bağlantısı) |
| `/{id}` | DELETE | Lineage sil |
| `/trace-back/{childBatchId}` | GET | Child'dan parent'lara — ağaç |
| `/trace-forward/{parentBatchId}` | GET | Parent'tan child'lara — ağaç |

**Yetkilendirme:** `ProductionAccessService` — BATCH READ/WRITE.

---

## Üretim Zinciri Örneği

```
Cotton Fiber Batch (LOT-F-001)         Polyester Fiber Batch (LOT-F-002)
  quantity: 600 kg, GOTS TR               quantity: 400 kg
           │                                        │
           └──── %60 ─────┐    ┌──── %40 ──────────┘
                           ▼    ▼
                    Yarn Batch (LOT-Y-001)
                      quantity: 950 kg (50 kg fire)
                      attributes:
                        fiber_micronaire: 4.04 (weighted avg)
                        certification: ["GOTS"] (collect — sadece GOTS olanlar)
                           │
                           ▼
                    Fabric Batch (LOT-FB-001)
                      quantity: 900 mt
                      attributes: inherited + fabric_weight_gsm, fabric_width_cm
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/batch-production.md` | Batch entity — parent/child |
| `02-production/material-fiber.md` | Material → Fiber → Batch zinciri |
| `02-production/quality-fiber.md` | QC sonuçları lineage üzerinden izlenebilir |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek koddan türetildi |

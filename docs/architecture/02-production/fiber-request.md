# FiberRequest — Yeni Lif Talebi

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: FiberRequest entity'si burada tanımlanır.

---

## Genel Bakış

Tenant'ın platforma "bu lif sistemde yok, ekler misiniz?" talebi. Platform admin onaylar veya reddeder. Onaylanırsa FiberIsoCode + Fiber kaydı oluşturulur.

---

## FiberRequest

> Tablo: `production.production_fiber_request`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `requestedBy` | UUID | Evet | FK → User (talep eden) |
| `isoCode` | String | Evet | Önerilen ISO kodu |
| `fiberName` | String | Evet | Önerilen lif adı |
| `fiberType` | String | Hayır | Önerilen kategori (NATURAL_PLANT vb.) |
| `description` | String (TEXT) | Hayır | Açıklama |
| `status` | Enum | Evet | PENDING / APPROVED / REJECTED |
| `reviewedBy` | UUID | Hayır | FK → User (platform admin) |
| `reviewNote` | String (TEXT) | Hayır | İnceleme notu |

### Akış

```
Tenant kullanıcısı: "Bamboo lifleri sistemde yok"
    → FiberRequest oluşturur (PENDING)
    ↓
Platform admin inceler
    → APPROVED: FiberIsoCode + Fiber kaydı oluşturulur
    → REJECTED: reviewNote ile bilgi verilir
```

### API

| Endpoint | Açıklama |
|---|---|
| `POST /api/production/fibers/requests` | Tenant kullanıcısı talep oluşturur |
| `GET /api/production/fibers/requests` | Kendi taleplerini listeler |
| `POST /internal/fiber-requests/{id}/approve` | Platform admin onaylar |
| `POST /internal/fiber-requests/{id}/reject` | Platform admin reddeder |

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/reference-tables.md` | Onay sonrası FiberIsoCode oluşturulur |
| `02-production/material-fiber.md` | Onay sonrası Material + Fiber oluşturulur |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek koddan |

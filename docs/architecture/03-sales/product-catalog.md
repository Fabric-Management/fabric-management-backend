# ProductCatalog — Ürün Kataloğu

> Modül: Satış Zinciri (03-sales)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: ProductCatalog entity'si burada tanımlanır.

---

## Genel Bakış

Pazarlamacının tablette veya masaüstünde gördüğü ürün listesi. Offline kullanım için cihaza indirilir. Material entity'sine bağlıdır — modül bazlı özellikler `specs` JSONB'de yaşar.

---

## ProductCatalog

> Tablo: `sales.product_catalog`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `materialId` | UUID | Evet | FK → Material |
| `moduleType` | Enum | Evet | FIBER / YARN / FABRIC / DYE_FINISHING |
| `listPrice` | Decimal | Evet | Liste fiyatı — DiscountPolicy baz fiyatı |
| `currency` | Enum | Evet | USD / EUR / TRY |
| `moq` | Decimal | Hayır | Minimum sipariş miktarı |
| `moqUnit` | Enum | Hayır | KG / MT / PIECE |
| `leadTimeDays` | Integer | Hayır | Teslimat süresi (gün) |
| `specs` | JSONB | Hayır | Modüle özel özellikler |
| `photos` | JSONB | Hayır | Ürün fotoğrafları — URL listesi |
| `isActive` | Boolean | Evet | Katalogda görünsün mü |

### Fiber Specs Örneği

```json
{
  "fiberName": "Cotton",
  "fiberIsoCode": "CO",
  "certification": "GOTS",
  "origin": "TR",
  "category": "NATURAL_PLANT"
}
```

> **Tasarım kararı:** Eski `productId → Product` FK'sı yerine `materialId → Material` kullanılır. Material tüm modüllerin ortak ürün katmanıdır.

---

## Offline Kullanım

Katalog tablet/telefona indirilebilir — internet olmadan browsing yapılabilir.

```
Fuar sabahı — tablet internete bağlıyken:
  Katalog indirilir (ProductCatalog)
  Müşteri listesi indirilir (TradingPartner)
    ↓
Gün boyu offline çalışma:
  Ürünler görüntülenebilir
  Quote oluşturulabilir (fiyat → offline kontrol)
```

> **Detay:** `10-mobile-offline/offline-sync.md`

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/material-fiber.md` | ProductCatalog.materialId → Material |
| `03-sales/discount-policy.md` | ProductCatalog.listPrice → baz fiyat |
| `03-sales/quote-approval.md` | QuoteLine.listPrice kaynağı |
| `10-mobile-offline/offline-sync.md` | Offline katalog indirme |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — productId→materialId |

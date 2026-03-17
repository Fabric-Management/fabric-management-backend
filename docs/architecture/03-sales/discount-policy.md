# DiscountPolicy — İndirim & Fiyat Kuralları

> Modül: Satış Zinciri (03-sales)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: DiscountPolicy ve fiyat bölgesi hesaplama mantığı burada tanımlanır.

---

## Genel Bakış

Pazarlamacı fiyat belirlerken 3 bölge kuralı geçerlidir. Amaç: minimum kar marjını korumak (kırmızı çizgi) ve yüksek indirimler için manager onayı zorunlu kılmak.

---

## DiscountPolicy

> Tablo: `sales.discount_policy`  
> `BaseEntity`'den miras alır.  
> Tenant ayarlarında yapılandırılır — modül bazında farklı olabilir.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `moduleType` | Enum | Evet | FIBER / YARN / FABRIC / DYE_FINISHING |
| `baseDiscountLimit` | Decimal | Evet | Serbest indirim üst sınırı — varsayılan %10 |
| `minProfitMargin` | Decimal | Evet | Kırmızı çizgi — varsayılan %5 |
| `requireManagerAbove` | Decimal | Evet | Manager onayı eşiği — varsayılan %10 |

---

## Fiyat Bölgesi Hesaplama

### Girdiler

```
listPrice       = katalog fiyatı (ProductCatalog.listPrice)
offeredPrice    = pazarlamacının teklif ettiği fiyat
estimatedCost   = tahmini maliyet (CostCalculation ESTIMATED → Quote.estimatedUnitCost)
```

### Hesaplama Formülleri

```
discountRate = (listPrice - offeredPrice) / listPrice × 100
profitMargin = (offeredPrice - estimatedCost) / offeredPrice × 100
```

### Bölge Belirleme (Sıralı Kontrol)

```
1. KONTROL — Kırmızı Çizgi (önce):
   IF profitMargin ≤ minProfitMargin
     → BLOCKED (kırmızı)
     → Sistem engeller — manager dahil kimse geçemez
     → "Bu fiyat minimum kar marjının altında"

2. KONTROL — İndirim Limiti:
   ELSE IF discountRate > baseDiscountLimit
     → MANAGER_APPROVAL (sarı)
     → İndirim yüksek — manager onayı şart
     → "İndirim oranı %{discountRate} — manager onayı gerekiyor"

3. VARSAYILAN:
   ELSE
     → FREE (yeşil)
     → Serbest — direkt gönderebilir
```

> **Kritik:** Bu iki kontrol bağımsızdır ve toplanmaz. Kırmızı çizgi **her zaman** önce kontrol edilir.

---

## Görsel Akış

```
Liste fiyatı (baz)
      │
      ▼
──────────────────────────────
 SERBEST BÖLGE (yeşil)
 discountRate ≤ baseDiscountLimit
 VE profitMargin > minProfitMargin
 → Onay gerekmez
──────────────────────────────
 MANAGER ONAY BÖLGESİ (sarı)
 discountRate > baseDiscountLimit
 VE profitMargin > minProfitMargin
 → Manager onayı şart
──────────────────────────────
 KIRMIZI ÇİZGİ (kırmızı)
 profitMargin ≤ minProfitMargin
 → Kesinlikle geçilemez
──────────────────────────────
 Tahmini maliyet (estimatedCost)
```

---

## Canlı Fiyat Hesaplama (UI)

```
Pazarlamacı fiyat girer
    ↓
Sistem anlık hesaplar:
  discountRate = (listPrice - offeredPrice) / listPrice × 100
  profitMargin = (offeredPrice - estimatedCost) / offeredPrice × 100
    ↓
Bölge rengi değişir:
  yeşil  → "Direkt gönderilebilir"
  sarı   → "Manager onayı gerekiyor"
  kırmızı → "Bu fiyatla teklif oluşturamazsınız" — gönder butonu disabled
```

### Maliyet Kaynağı

`estimatedCost` nereden geliyor?

```
Quote oluşturulurken CostCalculation service çalışır:
  1. Recipe.components'teki her fiber için aktif PriceList'ten birim fiyat çek
  2. percentage ile çarp → hammadde maliyeti
  3. CostTemplate'ten overhead yüzdesi ekle
  4. Toplam = estimatedUnitCost → Quote.estimatedUnitCost'a yazılır
```

> **Detay:** `06-costing/cost-calculation.md`

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `03-sales/quote-approval.md` | QuoteLine.priceZone — bölge belirleme sonucu |
| `03-sales/product-catalog.md` | ProductCatalog.listPrice — liste fiyatı kaynağı |
| `06-costing/cost-calculation.md` | CostCalculation (ESTIMATED) → estimatedUnitCost |
| `06-costing/price-list.md` | PriceList — maliyet hesabında kullanılır |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — hesaplama formülü düzeltildi (toplama hatası giderildi) |

# StockReservation & Lot Seçim Motoru

> Modül: IWM (05-iwm)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: StockReservation, lot seçim motoru, FIFO/FEFO burada tanımlanır.

---

## Genel Bakış

Sipariş için stok ayırma. Pazarlamacı ürün ve miktar girince sistem FIFO bazlı lot önerisi sunar. Seçilen lot ve toplar rezerve edilir — başka siparişe verilemez.

---

## StockReservation

> Tablo: `iwm.stock_reservation`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `salesOrderLineId` | UUID | Evet | FK → SalesOrderLine |
| `locationId` | UUID | Evet | FK → Location |
| `materialId` | UUID | Evet | FK → Material |
| `lotNumber` | String | Evet | Rezerve edilen lot |
| `goodsReceiptItemId` | UUID | Hayır | FK → GoodsReceiptItem — top bazlı rezervasyon |
| `qtyReserved` | Decimal | Evet | Rezerve edilen miktar |
| `status` | StockReservationStatus (Enum) | Evet | ACTIVE / RELEASED / CONVERTED |
| `expiresAt` | Timestamp | Hayır | Rezervasyon son tarihi |

### Status Akışı

```
ACTIVE → CONVERTED  (sipariş SHIPPED → StockTransaction SHIPMENT oluşur)
ACTIVE → RELEASED   (sipariş iptal → rezervasyon serbest bırakılır)
```

Rezervasyon oluştuğunda: `StockLedger.qtyReserved += qtyReserved`  
Serbest bırakıldığında: `StockLedger.qtyReserved -= qtyReserved`

---

## Lot Seçim Motoru

Sipariş formunda pazarlamacı ürün ve miktar girince sistem devreye girer.

### Akış

```
Pazarlamacı: "Cotton GOTS — 450 mt"
        ↓
Sistem FIFO bazlı lot önerisi oluşturur:
  Lot A → 450 mt — 3 top (önerilen ✓)
  Lot B → 280 mt — 2 top
  Lot C → 48 mt  — 1 top ⚠ Son top!
        ↓
Pazarlamacı Lot A'ya tıkladı → top detayları:
  Top 1: 150 mt  BCH-2024-042-001
  Top 2: 150 mt  BCH-2024-042-002
  Top 3: 150 mt  BCH-2024-042-003
        ↓
Pazarlamacı seçim yapar:
  "Tümünü al"         → 450 mt, Lot A tümü
  "Sadece 2 top"      → 300 mt, Top 1+2
  "Lot C'yi de ekle"  → 498 mt, Lot A + Lot C
        ↓
StockReservation oluşur, sipariş formunda qty güncellenir
```

### Top Detayı Gösterimi

Lot tıklandığında `GoodsReceiptItem` verileri gösterilir:

| Alan | Kaynak |
|---|---|
| Miktar | GoodsReceiptItem.netWeight |
| Barkod | GoodsReceiptItem.barcode |
| Seri no | GoodsReceiptItem.serialNumber |

---

## Son Top Uyarısı (LotEndRule)

```
Sipariş miktarı karşılandı ama lot artığı var mı?
  LotEndRule.thresholdQty altında lot tespit edildi
        ↓
Sarı banner: "⚠ Lot C'de 48 mt son top kaldı. Eklemek ister misiniz?"
        ↓
Pazarlamacı "Ekle" → Lot C rezerve edilir, lot tamamen kapanır
Pazarlamacı "Hayır" → devam edilir
```

> LotEndRule tanımı: `05-iwm/stock-rules.md`

---

## FIFO / FEFO Kuralı

Stok çıkışında sistem otomatik olarak doğru lot sırasını önerir.

| Kural | Açıklama | Kullanım |
|---|---|---|
| `FIFO` | First In First Out — en eski GoodsReceipt tarihi önce | Genel hammadde |
| `FEFO` | First Expired First Out — son kullanma tarihi en yakın önce | Kimyasal, boya |

Pazarlamacı sistemi override edebilir — ama sistem her zaman doğru sırayı gösterir.

> **Açık karar:** FEFO için GoodsReceiptItem'a `expiryDate` alanı eklenecek mi?

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `03-sales/sales-order.md` | StockReservation.salesOrderLineId |
| `05-iwm/location.md` | StockReservation.locationId |
| `05-iwm/stock-transaction-ledger.md` | StockLedger.qtyReserved güncelleme |
| `05-iwm/stock-rules.md` | LotEndRule — son top uyarısı eşiği |
| `02-production/goods-receipt.md` | GoodsReceiptItem — top detayı |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — lot seçim motoru, FIFO/FEFO, son top uyarısı |

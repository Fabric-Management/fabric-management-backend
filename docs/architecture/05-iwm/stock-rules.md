# Stok Kuralları — MinStockRule, LotEndRule, ReturnRateRule

> Modül: IWM (05-iwm)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: MinStockRule, LotEndRule, ReturnRateRule burada tanımlanır.

---

## 1. MinStockRule

> Tablo: `iwm.min_stock_rule`  
> `BaseEntity`'den miras alır.

Minimum stok eşiği. Düşünce FlowBoard + NotificationHub tetiklenir.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `locationId` | UUID | Evet | FK → Location |
| `materialId` | UUID | Evet | FK → Material |
| `minQty` | Decimal | Evet | Minimum stok miktarı |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `lastAlertAt` | Timestamp | Hayır | Son uyarı zamanı |
| `alertCooldownHours` | Integer | Evet | Tekrar uyarı bekleme — varsayılan 24 |

**Tetikleyici:**
```
StockLedger.qtyAvailable < MinStockRule.minQty
    ↓
Event: MinStockAlert (HIGH)
    ↓
NotificationHub → PROCUREMENT bildirimi
FlowBoard → PROCUREMENT task: "Stok kritik — temin edilmeli"
```

---

## 2. LotEndRule

> Tablo: `iwm.lot_end_rule`  
> `BaseEntity`'den miras alır.

Lot sonu artık yönetimi. Sipariş formunda pazarlamacıya uyarı gösterir.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `moduleType` | Enum | Evet | FIBER / YARN / FABRIC / DYE_FINISHING |
| `thresholdQty` | Decimal | Evet | Bu miktarın altı "son lot" sayılır |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `showWarning` | Boolean | Evet | Sipariş formunda uyarı göster |

**Kullanım:** Lot seçim motorunda (bkz. `05-iwm/stock-reservation.md`)

---

## 3. ReturnRateRule

> Tablo: `iwm.return_rate_rule`  
> `BaseEntity`'den miras alır.

Tedarikçi iade oranı eşiği. Aşılınca uyarı ve task oluşturulur.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `tradingPartnerId` | UUID | Evet | FK → TradingPartner |
| `thresholdRate` | Decimal | Evet | Eşik oran — örn. %5 |
| `windowDays` | Integer | Evet | Kaç günlük pencere — örn. 90 gün |

**Tetikleyici:**
```
Son {windowDays} gün içinde iade oranı > thresholdRate
    ↓
Event: ReturnRateExceeded (CRITICAL)
    ↓
NotificationHub → uyarı bildirimi
FlowBoard → RETURN task: "Supplier X iade oranı eşiği aştı — değerlendirme yapılmalı"
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `05-iwm/stock-transaction-ledger.md` | StockLedger.qtyAvailable → MinStockRule kontrolü |
| `05-iwm/stock-reservation.md` | LotEndRule → lot seçim motorunda son top uyarısı |
| `05-iwm/rma.md` | ReturnRateRule → RMA verileriyle hesaplanır |
| `11-cross-cutting/event-catalog.md` | MinStockAlert, ReturnRateExceeded event'leri |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon |

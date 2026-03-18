# Manuel Düzeltme — StockAdjustmentRequest

> Modül: IWM (05-iwm)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: StockAdjustmentRequest entity'si burada tanımlanır.

---

## Genel Bakış

Sayım dışında yapılan manuel stok düzeltmeleri. Fire, hasar, kayıp, fazlalık gibi nedenlerle. Yetki matrisine göre onay akışı değişir.

---

## StockAdjustmentRequest

> Tablo: `iwm.stock_adjustment_request`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `adjustmentType` | Enum | Evet | FIRE / DAMAGE / LOSS / ERROR / SURPLUS / SAMPLE |
| `locationId` | UUID | Evet | FK → Location |
| `materialId` | UUID | Evet | FK → Material |
| `lotNumber` | String | Evet | Lot numarası |
| `qtyBefore` | Decimal | Evet | Düzeltme öncesi miktar |
| `qtyChange` | Decimal | Evet | Değişim — negatif veya pozitif |
| `qtyAfter` | Decimal | Evet | Düzeltme sonrası miktar |
| `reason` | String (TEXT) | Evet | Açıklama — zorunlu |
| `photoEvidence` | JSONB | Hayır | Fotoğraf kanıtı |
| `status` | Enum | Evet | PENDING / APPROVED / REJECTED |
| `approvedBy` | UUID | Hayır | FK → User |

---

## Yetki Matrisi

| adjustmentType | Kim Yapabilir | Onay |
|---|---|---|
| `FIRE` | Depo personeli önerir | Manager onaylar |
| `DAMAGE` | Depo personeli önerir | Manager onaylar |
| `LOSS` | Depo personeli önerir | Manager onaylar |
| `SURPLUS` | Depo personeli önerir | Manager onaylar |
| `ERROR` | Sadece Manager / Admin | Direkt — gerekçe zorunlu |
| `SAMPLE` | Depo personeli | Direkt — SampleRequest zaten onaylı |

---

## Düzeltme Akışı

```
StockAdjustmentRequest oluşturulur
    ↓
Yetki kontrolü:
  SAMPLE → direkt onaylı
  ERROR  → Manager/Admin direkt işler
  Diğerleri → Manager onayı beklenir
    ↓
Onaylandı
    ↓
StockTransaction: ADJUSTMENT yazılır (immutable)
StockLedger.qtyOnHand güncellenir
    ↓
FIRE / DAMAGE / LOSS → CostHistory'ye zarar kaydı
ERROR → denetim logu: kim, ne zaman, neden, önceki değer
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `05-iwm/stock-transaction-ledger.md` | ADJUSTMENT transaction |
| `05-iwm/location.md` | StockAdjustmentRequest.locationId |
| `06-costing/exchange-rate-history.md` | CostHistory — zarar kaydı |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon |

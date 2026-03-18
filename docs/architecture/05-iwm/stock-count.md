# Stok Sayımı

> Modül: IWM (05-iwm)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: StockCount, StockCountLine, StockCountAssignee, StockCountTolerance burada tanımlanır.

---

## Genel Bakış

3 sayım tipi: FULL (tüm depo), PARTIAL (bölge bazlı), CYCLE (periyodik rotasyon). Sayım sırasında çıkış hareketi dondurulur, giriş serbest — üretim durmuyor.

---

## Entity'ler

### StockCount

> Tablo: `iwm.stock_count` | `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `countNumber` | String | Evet | **Otomatik** — `CNT-2024-001` |
| `countType` | Enum | Evet | FULL / PARTIAL / CYCLE |
| `locationId` | UUID | Evet | FK → Location (sayım yapılacak yer) |
| `status` | Enum | Evet | PLANNED / IN_PROGRESS / COMPLETED / CANCELLED |
| `plannedAt` | Date | Evet | Planlanan tarih |
| `startedAt` | Timestamp | Hayır | Başlangıç — çıkış dondurulur |
| `completedAt` | Timestamp | Hayır | Tamamlanma — çıkış açılır |
| `notes` | String (TEXT) | Hayır | Notlar |

### StockCountAssignee

> Tablo: `iwm.stock_count_assignee`

| Alan | Tip | Açıklama |
|---|---|---|
| `stockCountId` | UUID | FK → StockCount |
| `userId` | UUID | FK → User |
| `assignedZone` | String | Sorumlu bölge — opsiyonel |

### StockCountLine

> Tablo: `iwm.stock_count_line`

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `stockCountId` | UUID | Evet | FK → StockCount |
| `materialId` | UUID | Evet | FK → Material |
| `lotNumber` | String | Evet | Lot numarası |
| `goodsReceiptItemId` | UUID | Hayır | FK → GoodsReceiptItem — top/balya bazlı |
| `barcode` | String | Hayır | Barkod okuyucuyla girildiyse |
| `systemQty` | Decimal | Evet | StockLedger'dan — sistem miktarı |
| `countedQty` | Decimal | Evet | Fiziksel sayılan miktar |
| `variance` | Decimal | Evet | **Hesaplanan** — `countedQty - systemQty` |
| `varianceReason` | String (TEXT) | Hayır | Fark sebebi — manager onayında zorunlu |
| `entryMethod` | Enum | Evet | BARCODE / MANUAL |
| `isVerified` | Boolean | Evet | Doğrulandı mı |

### StockCountTolerance

> Tablo: `iwm.stock_count_tolerance`

| Alan | Tip | Açıklama |
|---|---|---|
| `moduleType` | Enum | FIBER / YARN / FABRIC / GENERAL |
| `autoAdjustThreshold` | Decimal | Bu oran altı otomatik düzeltilir — örn. %2 |
| `requiresManagerApproval` | Decimal | Bu oran üstü manager onayı — örn. %2+ |

---

## Sayım Sırasında Hareket Yönetimi

```
Sayım başladı (IN_PROGRESS)
  → İlgili lokasyonda ÇIKIŞ hareketi dondurulur
  → GİRİŞ hareketi serbest — üretim durmuyor
Sayım tamamlandı (COMPLETED)
  → Çıkış hareketi açılır
  → Sayım sırasında gelen girişler otomatik dahil edilir
```

---

## Sayım Yöntemleri

**Kağıt liste ile:**
```
Sistem → stok listesi PDF çıktısı
Her top/balya için satır (lotNumber + barkod + sistem miktarı)
Personel fiziksel bulur → miktarı not eder
Sistem üzerinden girilir (entryMethod = MANUAL)
```

**Barkod okuyucu ile (mobil):**
```
Tablet/telefon üzerinden sayım ekranı
Her top/balya barkodu okutulur
StockCountLine otomatik oluşur (entryMethod = BARCODE)
Bulunamayan toplar kırmızı işaretlenir
```

---

## Düzeltme Akışı (StockAdjustment)

```
Sayım tamamlandı → sistem variance hesaplar
    ↓
|variance| / systemQty ≤ autoAdjustThreshold
  Evet → ADJUSTMENT transaction otomatik yazılır
  Hayır → Manager onayı beklenir
    ↓
Manager varianceReason girer → onaylar
    ↓
StockTransaction ADJUSTMENT yazılır
StockLedger.qtyOnHand güncellenir
Event: StockCountVariance (HIGH) → NotificationHub

variance > 0 → fazla stok → ADJUSTMENT (pozitif)
variance < 0 → eksik stok → ADJUSTMENT (negatif)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `05-iwm/stock-transaction-ledger.md` | ADJUSTMENT transaction |
| `05-iwm/location.md` | StockCount.locationId |
| `02-production/goods-receipt.md` | GoodsReceiptItem — top bazlı referans |
| `11-cross-cutting/event-catalog.md` | StockCountVariance, StockCountPlanned |

---

## Açık Kararlar

- [ ] Stok sayımı sırasında ilgili lokasyonda giriş hareketi de dondurulsun mu?
- [ ] Barkod okuyucu entegrasyonu — mobil uygulama gereksinimi

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — 3 sayım tipi, tolerans, barkod/kağıt yöntemleri |

# GoodsReceipt — Teslim Alma

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: GoodsReceipt ve GoodsReceiptItem entity'leri burada tanımlanır.

---

## Genel Bakış

GoodsReceipt fiziksel teslim alma kaydıdır. Hem kendi üretim (Batch), hem dış alım (PurchaseOrder), hem fason dönüş (SubcontractOrder) için kullanılır. Tek form, polimorfik kaynak. `GoodsReceiptItem` her balya/top/birim için ayrı satırdır.

---

## 1. GoodsReceipt

> Tablo: `production.goods_receipt`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `receiptNumber` | String | Evet | **Otomatik** — `GR-2024-0001` |
| `sourceType` | GoodsReceiptSourceType (Enum) | Evet | BATCH / PURCHASE_ORDER / SUBCONTRACT_ORDER |
| `sourceId` | UUID | Evet | İlgili kaydın id'si — polimorfik FK |
| `receivedById` | UUID | Evet | FK → User (teslim alan personel) |
| `receivedAt` | Timestamp | Evet | Teslim alma zamanı |
| `packageCount` | Integer | Evet | Balya / koli sayısı |
| `grossWeight` | Decimal | Hayır | Brüt ağırlık |
| `netWeight` | Decimal | Hayır | Net ağırlık |
| `vehicleInfo` | String (255) | Hayır | Araç / taşıma bilgisi |
| `damageNotes` | String (TEXT) | Hayır | Hasar / fire kaydı |
| `status` | GoodsReceiptStatus (Enum) | Evet | DRAFT / CONFIRMED |

### GoodsReceiptSourceType Enum

| Değer | Kaynak | Açıklama |
|---|---|---|
| `BATCH` | Kendi üretim | Batch kapatma akışından otomatik oluşur |
| `PURCHASE_ORDER` | Dış alım | PurchaseOrder teslim alımı |
| `SUBCONTRACT_ORDER` | Fason dönüş | SubcontractOrder tamamlanma |

### GoodsReceiptStatus

| Değer | Açıklama |
|---|---|
| `DRAFT` | Henüz kesinleşmemiş — QC bekleniyor veya bilgiler tamamlanıyor |
| `CONFIRMED` | Kesinleşti — IWM stok girişi tetiklenir |

---

## 2. GoodsReceiptItem

> Tablo: `production.goods_receipt_item`  
> `BaseEntity`'den miras alır.

Her balya / top / birim ayrı satır. Seri numarası ve barkod burada yaşar.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `goodsReceiptId` | UUID | Evet | FK → GoodsReceipt |
| `sequenceNo` | Integer | Evet | Sıra numarası — 1, 2, 3... |
| `barcode` | String | Evet | **Otomatik** — `BCH-2024-0042-001` |
| `serialNumber` | String | Hayır | Seri numarası |
| `netWeight` | Decimal | Evet | Bu birimin net ağırlığı |
| `grossWeight` | Decimal | Hayır | Bu birimin brüt ağırlığı |
| `notes` | String (TEXT) | Hayır | Birime özel not |

---

## Kaynak Bazlı Akışlar

> **Tasarım Kararı:** İç üretim (Batch) için GoodsReceipt **opsiyoneldir** — Batch evrensel modelde QC event-driven akışı (FiberTestResult → BatchQcEventListener → Batch.status = AVAILABLE) yeterlidir. Dış alım (PO, SC) için GoodsReceipt **zorunludur** — fiziksel teslim alma kaydı gerekir.

### Kendi Üretim (Batch) Akışı — Opsiyonel

GoodsReceipt Batch kapatma sürecinin bir parçası olarak oluşur:

```
Batch → QC_PENDING
  GoodsReceipt oluşturulur (DRAFT)
  GoodsReceiptItem'lar hazır (barkodlar üretilmiş)
      ↓
QC_PASSED:
  GoodsReceipt.status → CONFIRMED
  GoodsReceiptConfirmed eventi → IWM stok girişi
      ↓
QC_FAILED:
  GoodsReceipt DRAFT kalır
  Batch yeniden işleme döner
```

> **Detay:** `02-production/batch-production.md`

### Dış Alım (PurchaseOrder) Akışı

```
Depo personeli GoodsReceipt formu açar
    ↓
Dropdown: teslim alınmayı bekleyen kayıtlar
  - CONFIRMED PurchaseOrder'lar
    ↓
Kaynak seçilince form üst bilgileri otomatik dolar
  (kimden geldi, ne, ne kadar)
    ↓
Personel fiziksel bilgileri girer:
  packageCount, grossWeight, netWeight
  vehicleInfo, damageNotes
    ↓
Birim bazında seri no / barkod girilir (GoodsReceiptItem)
    ↓
GoodsReceipt CONFIRMED → IWM stok girişi tetiklenir
PurchaseOrder.status → PARTIALLY_RECEIVED veya RECEIVED
```

### Fason Dönüş (SubcontractOrder) Akışı

```
SubcontractOrder.status = IN_PROGRESS (fason firma üretiyor)
    ↓
İşlenmiş ürün depoya gelir
    ↓
Depo personeli GoodsReceipt formu açar
  sourceType = SUBCONTRACT_ORDER seçer
    ↓
GoodsReceiptItem'lar girilir (balya/top bazında)
    ↓
GoodsReceipt CONFIRMED
  IWM: StockTransaction RECEIPT
  SubcontractOrder.status → COMPLETED (otomatik)
    ↓
Fire kontrolü:
  Gönderilen miktar (materialSent) vs geri dönen miktar (actualQty)
  Fark varsa → StockTransaction REJECT (fire kaydı)
```

> **Detay:** `04-procurement/subcontract-order.md`

---

## GoodsReceipt → IWM Entegrasyonu

GoodsReceipt `CONFIRMED` olunca `GoodsReceiptConfirmed` eventi yayınlanır. IWM bu eventi dinleyerek:

1. Her GoodsReceiptItem için `StockTransaction (RECEIPT)` oluşturur
2. Hedef lokasyon belirlenir:
   - **Batch (kendi üretim):** FINISHED warehouse'a varsayılan yerleşim
   - **PurchaseOrder (dış alım):** RAW warehouse'a varsayılan yerleşim
   - **SubcontractOrder (fason):** FINISHED veya WIP warehouse'a
3. Depo personeli lokasyonu değiştirebilir (varsayılan override)
4. `StockLedger` güncellenir
5. SalesOrderLine varsa → `lineStatus` güncellenir

> **Detay:** `05-iwm/stock-transaction-ledger.md`

---

## Barkod Üretim Kuralı

| Kaynak | Barkod Formatı | Örnek |
|---|---|---|
| Batch | `BCH-{batchNumber}-{sıra}` | `BCH-2024-0042-001` |
| PurchaseOrder | `PO-{poNumber}-{sıra}` | `PO-2024-001-001` |
| SubcontractOrder | `SC-{scNumber}-{sıra}` | `SC-2024-001-001` |

---

## İlişki Özeti

```
Batch ──────────────→ GoodsReceipt (1:1)
PurchaseOrder ──────→ GoodsReceipt (1:N — kısmi teslimat)
SubcontractOrder ───→ GoodsReceipt (1:1)
                            │
                            └──→ GoodsReceiptItem (1:N)
                                    │
                                    ├──→ StockTransaction (IWM)
                                    ├──→ StockLedger (IWM)
                                    └──→ StockCountLine (sayımda referans)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/batch-production.md` | Batch kapatma → GoodsReceipt DRAFT |
| `04-procurement/purchase-order.md` | PO teslim alımı |
| `04-procurement/subcontract-order.md` | SC dönüş teslim alımı + fire kontrolü |
| `05-iwm/stock-transaction-ledger.md` | GoodsReceiptConfirmed → StockTransaction RECEIPT |
| `05-iwm/stock-reservation.md` | GoodsReceiptItem — lot seçiminde top detayı |
| `07-flowboard/smart-task-generator.md` | GoodsReceiptConfirmed → WAREHOUSE task |
| `11-cross-cutting/event-catalog.md` | GoodsReceiptConfirmed eventi |
| `11-cross-cutting/polymorphic-fk-rules.md` | sourceType + sourceId polimorfik FK |

---

## Açık Kararlar

- [ ] Barkod format standardı — tüm kaynak tipleri için onaylanacak
- [ ] GoodsReceiptItem'a `qcResultId` FK — QC modülü tasarlanınca
- [ ] Varsayılan lokasyon atama mantığı — IWM ile detaylandırılacak
- [ ] FEFO için GoodsReceiptItem'a `expiryDate` eklenecek mi?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — DRAFT/CONFIRMED akışı netleştirildi, 3 kaynak akışı belgelendi, IWM lokasyon seçimi eklendi |

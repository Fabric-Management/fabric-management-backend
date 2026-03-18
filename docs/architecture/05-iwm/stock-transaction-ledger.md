# StockTransaction & StockLedger — Stok Hareketleri

> Modül: IWM (05-iwm)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: StockTransaction ve StockLedger entity'leri burada tanımlanır.

---

## Genel Bakış

**StockTransaction = Source of Truth.** Her stok hareketi buraya yazılır, silinmez, değiştirilemez. **StockLedger = Anlık bakiye** — StockTransaction'dan türetilir, hızlı okuma için.

---

## 1. StockTransaction

> Tablo: `iwm.stock_transaction`  
> `BaseEntity`'den miras alır.  
> **Immutable** — oluşturulduktan sonra değiştirilemez, silinemez.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `transactionNumber` | String | Evet | **Otomatik** — `TXN-2024-0001` |
| `transactionType` | StockTransactionType (Enum) | Evet | Bkz. Transaction Tipleri |
| `locationId` | UUID | Evet | FK → Location (çıkış lokasyonu) |
| `toLocationId` | UUID | Hayır | FK → Location (varış — TRANSFER için) |
| `materialId` | UUID | Evet | FK → Material |
| `lotNumber` | String | Evet | Lot numarası |
| `goodsReceiptItemId` | UUID | Hayır | FK → GoodsReceiptItem — izlenebilirlik |
| `qty` | Decimal | Evet | Hareket miktarı |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `sourceType` | Enum | Evet | GOODS_RECEIPT / WORK_ORDER / SALES_ORDER / STOCK_COUNT / MANUAL / SUBCONTRACT_ORDER |
| `sourceId` | UUID | Evet | İlgili kaydın id'si — polimorfik FK |
| `notes` | String (TEXT) | Hayır | Notlar |
| `transactedAt` | Timestamp | Evet | Hareket zamanı |

### StockTransactionType (Enum)

| Tip | Açıklama | Tetikleyen |
|---|---|---|
| `RECEIPT` | Stok girişi | GoodsReceipt CONFIRMED |
| `ISSUE` | Üretime verildi | WorkOrder başladığında |
| `TRANSFER` | Depolar arası | Manuel veya sistem |
| `SHIPMENT` | Müşteriye gönderildi | SalesOrder SHIPPED |
| `ADJUSTMENT` | Sayım farkı düzeltmesi | StockCount |
| `SAMPLE_OUT` | Numune çıkışı | SampleRequest DISPATCHED |
| `REJECT` | Fire / ret | QC başarısız veya fire |
| `RETURN` | İade girişi | Müşteriden iade (RMA) |

---

## Giriş Tetikleyicileri (RECEIPT / RETURN)

| Kaynak | Tetikleyici | Transaction Tipi |
|---|---|---|
| GoodsReceipt (Batch) | `GoodsReceiptConfirmed` | RECEIPT |
| GoodsReceipt (PO) | `GoodsReceiptConfirmed` | RECEIPT |
| GoodsReceipt (SC) | `GoodsReceiptConfirmed` | RECEIPT |
| Batch COMPLETED | MACHINE → FINISHED | RECEIPT |
| Müşteri iadesi | RMA RECEIVED | RETURN |

## Çıkış Tetikleyicileri (ISSUE / SHIPMENT / SAMPLE_OUT / REJECT)

| Kaynak | Tetikleyici | Transaction Tipi |
|---|---|---|
| WorkOrder | Üretim başladığında | ISSUE |
| SubcontractOrder | CONFIRMED (hammadde gönderimi) | ISSUE |
| SalesOrder | SHIPPED statüsüne geçince | SHIPMENT |
| SampleRequest | DISPATCHED statüsüne geçince | SAMPLE_OUT |
| Batch QC_FAILED | QC reddi | REJECT |
| SubcontractOrder | Fire tespit edildiğinde | REJECT |

---

## 2. StockLedger

> Tablo: `iwm.stock_ledger`  
> `BaseEntity`'den miras alır.

Anlık stok bakiyesi. Her `lokasyon + malzeme + lot` kombinasyonu için tek kayıt.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `locationId` | UUID | Evet | FK → Location |
| `materialId` | UUID | Evet | FK → Material |
| `lotNumber` | String | Evet | Lot numarası |
| `qtyOnHand` | Decimal | Evet | Eldeki miktar |
| `qtyReserved` | Decimal | Evet | Rezerve edilen miktar |
| `qtyAvailable` | Decimal | Evet | **Hesaplanan** — `qtyOnHand - qtyReserved` |
| `lastTransactionAt` | Timestamp | Hayır | Son hareket zamanı |

**Unique kısıt:** `(tenant_id, location_id, material_id, lot_number)`

**Kural:** `qtyAvailable` negatif olamaz. Negatife düşecek işlem engellenir.

> **Açık karar:** Acil durumda negatif stok izni verilecek mi?

### StockLedger Güncelleme Mantığı

Her `StockTransaction` yazıldığında `StockLedger` senkron güncellenir:

```
RECEIPT / RETURN      → qtyOnHand += qty
ISSUE / SHIPMENT /
SAMPLE_OUT / REJECT   → qtyOnHand -= qty
ADJUSTMENT            → qtyOnHand += qty (pozitif veya negatif)
TRANSFER              → from: qtyOnHand -= qty
                        to:   qtyOnHand += qty
```

---

## İlişki Özeti

```
StockTransaction (immutable, source of truth)
      ↓ günceller
StockLedger (anlık bakiye, hızlı okuma)

Tetikleyiciler:
  GoodsReceipt → RECEIPT
  WorkOrder    → ISSUE
  SalesOrder   → SHIPMENT
  StockCount   → ADJUSTMENT
  QC Failed    → REJECT
  SampleReq    → SAMPLE_OUT
  RMA          → RETURN
  Transfer     → TRANSFER (çıkış + giriş)
  SubcontractOrder → ISSUE (hammadde gönderim) + REJECT (fire)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `05-iwm/location.md` | StockTransaction.locationId, toLocationId |
| `02-production/material-fiber.md` | StockTransaction.materialId → Material |
| `02-production/goods-receipt.md` | GoodsReceiptConfirmed → RECEIPT |
| `02-production/batch-production.md` | Batch COMPLETED → RECEIPT (MACHINE→FINISHED) |
| `02-production/work-order.md` | WorkOrder start → ISSUE |
| `03-sales/sales-order.md` | SalesOrder SHIPPED → SHIPMENT |
| `03-sales/sample-management.md` | SampleRequest DISPATCHED → SAMPLE_OUT |
| `04-procurement/subcontract-order.md` | SC CONFIRMED → ISSUE, fire → REJECT |
| `05-iwm/stock-reservation.md` | StockLedger.qtyReserved |
| `05-iwm/stock-count.md` | StockCount → ADJUSTMENT |
| `05-iwm/rma.md` | RMA RECEIVED → RETURN |
| `05-iwm/transfer.md` | StockTransfer → TRANSFER |
| `11-cross-cutting/polymorphic-fk-rules.md` | sourceType + sourceId polimorfik FK |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — SubcontractOrder ISSUE/REJECT tetikleyicileri eklendi |

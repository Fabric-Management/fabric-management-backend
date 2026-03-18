# Inventory — Stok Hareketleri & Bakiye (CQRS)

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: InventoryTransaction ve InventoryBalance burada tanımlanır.

---

## Genel Bakış

Parti bazlı stok hareketlerini kaydetmek ve lokasyon bazlı bakiyeyi güncellemek. CQRS tarzı: command ile yazma (InventoryTransaction), query ile okuma (InventoryBalance).

### IWM ile Sorumluluk Ayrımı

| Katman | Kapsam | Şema |
|---|---|---|
| **Production Inventory (bu modül)** | Batch içi hareketler: reserve, consume, waste, adjust, transfer | `production` |
| **IWM (05-iwm)** | Modüller arası hareketler: GoodsReceipt → stok girişi, SalesOrder → sevkiyat | `iwm` (ileride) |

Her iki katman da aynı `WarehouseLocation`'ı kullanır. Production Inventory batch'in miktar değişimlerini izler; IWM genel stok görünürlüğünü sağlar.

---

## 1. InventoryTransaction

> Tablo: `production.production_execution_inventory_transaction`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `batchId` | UUID | Evet | FK → Batch |
| `transactionType` | Enum | Evet | IN / OUT / ADJUST |
| `quantity` | Decimal | Evet | Hareket miktarı |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `locationId` | UUID | Hayır | FK → WarehouseLocation |
| `referenceType` | String | Hayır | İlişkili kayıt tipi (lineage, salesOrder vb.) |
| `referenceId` | UUID | Hayır | İlişkili kaydın id'si |
| `transactionDate` | Timestamp | Evet | Hareket zamanı |
| `reasonCode` | String | Hayır | Neden kodu |
| `idempotencyKey` | String | Hayır | Aynı işlemin tekrar yazılmasını önler |

### Transaction Tipleri

| Tip | Açıklama | Batch Etkisi |
|---|---|---|
| `IN` | Stok girişi (üretim, alım, iade) | quantity += |
| `OUT` | Stok çıkışı (tüketim, sevkiyat, fire) | quantity -= |
| `ADJUST` | Manuel düzeltme | quantity ± |

### Idempotency

`idempotencyKey` ile aynı işlem iki kez yazılamaz. Retry senaryolarında veri bütünlüğünü korur.

---

## 2. InventoryBalance

> Tablo: `production.production_execution_inventory_balance`  
> `BaseEntity`'den miras alır.

Batch + Location bazlı **tek bakiye satırı**. Her hareket bu bakiyeyi günceller.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `batchId` | UUID | Evet | FK → Batch |
| `locationId` | UUID | Evet | FK → WarehouseLocation |
| `quantity` | Decimal | Evet | Mevcut miktar |
| `reservedQuantity` | Decimal | Evet | Rezerve |
| `consumedQuantity` | Decimal | Evet | Tüketilen |
| `wasteQuantity` | Decimal | Evet | Fire |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `lastTransactionId` | UUID | Hayır | Son hareket referansı |
| `lastTransactionDate` | Timestamp | Hayır | Son hareket zamanı |

**Unique kısıt:** `(tenant_id, batch_id, location_id)` — aynı batch aynı lokasyonda tek bakiye.

---

## Servisler

| Servis | Tip | Açıklama |
|---|---|---|
| `InventoryTransactionCommandService` | Command | Hareket oluşturma + bakiye güncelleme |
| `InventoryBalanceUpdater` | Internal | Balance güncelleme mantığı |
| `InventoryTransactionQueryService` | Query | Hareket sorgulama |
| `InventoryBalanceQueryService` | Query | Bakiye sorgulama |

### Akış

```
Batch operasyonu (reserve, consume, waste, transfer)
    ↓
BatchService işlemi yapar → Batch miktarları günceller
    ↓
InventoryTransactionCommandService → InventoryTransaction yazılır
    ↓
InventoryBalanceUpdater → InventoryBalance güncellenir
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/batch-production.md` | Batch operasyonları → InventoryTransaction |
| `02-production/warehouse-location.md` | InventoryBalance.locationId |
| `05-iwm/stock-transaction-ledger.md` | IWM katmanı — modüller arası stok |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek koddan, IWM sorumluluk ayrımı belgelendi |

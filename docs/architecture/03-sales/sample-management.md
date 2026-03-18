# Numune Yönetimi

> Modül: Satış Zinciri (03-sales)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: SampleRequest ve SampleDelivery burada tanımlanır.

---

## Genel Bakış

Sipariş öncesi numune talebi. Numune onaylanırsa SalesOrder'a bağlanabilir. Teslimat yöntemi (kargo, pazarlamacı, rota) ayrıca takip edilir.

---

## 1. SampleRequest

> Tablo: `sales.sample_request`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `customerId` | UUID | Evet | FK → TradingPartner |
| `materialId` | UUID | Evet | FK → Material |
| `requestedQty` | Decimal | Evet | Talep edilen numune miktarı |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `deliveryMethod` | Enum | Evet | CARGO / SALESPERSON / DELIVERY_ROUTE |
| `deliveryAddress` | JSONB | Hayır | Teslimat adresi |
| `status` | SampleRequestStatus (Enum) | Evet | Bkz. status akışı |
| `salesOrderId` | UUID | Hayır | Numune sonrası sipariş oluştuysa FK → SalesOrder |
| `notes` | String (TEXT) | Hayır | Notlar |

### Status Akışı

```
REQUESTED → PREPARING → DISPATCHED → DELIVERED → CONVERTED_TO_ORDER / NO_ORDER
          ↘ CANCELLED
```

---

## 2. SampleDelivery

> Tablo: `sales.sample_delivery`

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `sampleRequestId` | UUID | Evet | FK → SampleRequest |
| `deliveryMethod` | Enum | Evet | CARGO / SALESPERSON / DELIVERY_ROUTE |
| `trackingNumber` | String | Hayır | Kargo takip numarası |
| `cargoCompany` | String | Hayır | Kargo firması |
| `deliveredById` | UUID | Hayır | FK → User (SALESPERSON için) |
| `dispatchedAt` | Timestamp | Hayır | Gönderim zamanı |
| `deliveredAt` | Timestamp | Hayır | Teslim zamanı |
| `recipientName` | String | Hayır | Teslim alan kişi |
| `deliveryPhoto` | String | Hayır | Teslim fotoğrafı URL |

---

## IWM Bağlantısı

Numune gönderilirken IWM'de `StockTransaction (SAMPLE_OUT)` oluşturulur.

> **Detay:** `05-iwm/stock-transaction-ledger.md`

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `03-sales/sales-order.md` | SalesOrder.sampleRequestId |
| `05-iwm/stock-transaction-ledger.md` | SAMPLE_OUT transaction |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — salesorder-fiber'den ayrıldı |

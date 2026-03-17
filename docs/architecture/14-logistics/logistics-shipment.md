# Logistics — Sevkiyat Yönetimi

> Modül: Logistics (14-logistics)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Shipment entity'si burada tanımlanır.

---

## Genel Bakış

Gelen/giden sevkiyat ve kargo takibi. TradingPartner ile müşteri (outbound) veya tedarikçi (inbound) ilişkisi. Kargo firması, takip numarası, teslimat kanıtı destekli. Tüm tablolar `logistics` şemasında.

---

## Shipment

> Tablo: `logistics.logistics_shipment`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `tradingPartnerId` | UUID | Evet | FK → TradingPartner |
| `shipmentNumber` | String | Evet | Otomatik — SHP-yyyyMMdd-xxxxx (outbound), RCV-yyyyMMdd-xxxxx (inbound) |
| `orderReference` | String | Hayır | Sipariş referansı (metin — FK yok) |
| `shipmentType` | ShipmentType (Enum) | Evet | OUTBOUND / INBOUND / RETURN_INBOUND / RETURN_OUTBOUND / TRANSFER |
| `status` | ShipmentStatus (Enum) | Evet | Bkz. status akışı |
| `carrierName` | String | Hayır | Kargo firması |
| `carrierCode` | String | Hayır | UPS, FEDEX, DHL vb. |
| `trackingNumber` | String | Hayır | Takip numarası |
| `trackingUrl` | String | Hayır | Takip URL'i |
| `shipDate` | LocalDate | Hayır | Gönderim tarihi |
| `estimatedDeliveryDate` | LocalDate | Hayır | Tahmini teslimat |
| `actualDeliveryDate` | LocalDate | Hayır | Gerçek teslimat |
| `originAddress` | String | Hayır | Çıkış adresi |
| `destinationAddress` | String | Evet | Varış adresi |
| `totalWeight` | Decimal | Hayır | Toplam ağırlık |
| `weightUnit` | String | Hayır | Varsayılan KG |
| `packageCount` | Integer | Hayır | Koli sayısı |
| `shippingCost` | Decimal | Hayır | Sevkiyat maliyeti |
| `currency` | String | Hayır | Varsayılan TRY |
| `deliveryProof` | String | Hayır | Teslimat kanıtı |
| `recipientName` | String | Hayır | Teslim alan kişi |
| `notes` | String (TEXT) | Hayır | Notlar |
| `metadata` | JSONB | Hayır | Ek bilgiler |

### ShipmentStatus Akışı

```
PENDING → PREPARING → READY → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED
                                                                        ↘ DELIVERY_FAILED → RETURNED
     ↘ CANCELLED
```

### ShipmentType

| Değer | Açıklama |
|---|---|
| `OUTBOUND` | Müşteriye gönderim |
| `INBOUND` | Tedarikçiden alım |
| `RETURN_INBOUND` | Müşteriden iade alımı |
| `RETURN_OUTBOUND` | Tedarikçiye iade gönderim |
| `TRANSFER` | Depolar arası transfer |

### API

Base path: `/api/v1/shipments`

CRUD + lifecycle (prepare, ready, pickup, in-transit, out-for-delivery, deliver, delivery-failed, cancel) + sorgular (partner, in-transit, pending, late, outbound, inbound, order) + tracking güncelleme.

---

## Order ile İlişki

`orderReference` sadece string — SalesOrder'a FK yok. İleride:
- SalesOrder SHIPPED → Shipment otomatik oluşturulabilir
- Shipment DELIVERED → SalesOrder status güncelleme (event-driven)

---

## IWM ile İlişki

Shipment line veya Batch/IWM bağlantısı şu an yok. İleride:
- Shipment oluşturulduğunda IWM'den StockTransaction SHIPMENT tetiklenebilir
- ShipmentLine entity eklenebilir (hangi batch/lot gönderildi)

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/trading-partner.md` | Shipment.tradingPartnerId |
| `03-sales/sales-order.md` | orderReference ile gevşek bağlantı |
| `05-iwm/stock-transaction-ledger.md` | İleride SHIPMENT transaction tetiklemesi |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek koddan |

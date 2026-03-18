# PurchaseOrder — Satın Alma Siparişi

> Modül: Tedarik Zinciri (04-procurement)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: PurchaseOrder ve PurchaseOrderLine TEK kanonik tanımı burasıdır.

---

## Genel Bakış

Tedarikçiye verilen kesinleşmiş satın alma siparişi. RFQ sürecinden veya anlaşmalı tedarikçi fiyatından oluşabilir. GoodsReceipt ile teslim alınır. Maliyet modülüne veri besler.

> **Önemli:** Eski `production.prod_purchase_order` tanımı kaldırılmıştır. PurchaseOrder sadece `procurement` şemasında yaşar.

---

## 1. PurchaseOrder

> Tablo: `procurement.purchase_order`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `poNumber` | String | Evet | **Otomatik** — `PO-2024-001` |
| `workOrderId` | UUID | Evet | FK → WorkOrder |
| `tradingPartnerId` | UUID | Evet | FK → TradingPartner |
| `supplierQuoteId` | UUID | Hayır | FK → SupplierQuote — RFQ'dan geldiyse |
| `status` | PurchaseOrderStatus (Enum) | Evet | Bkz. `11-cross-cutting/status-enum-catalog.md` |
| `currency` | Enum | Evet | USD / EUR / TRY |
| `paymentTerms` | Enum | Hayır | CASH / NET_30 / NET_60 / OPEN_ACCOUNT |
| `expectedDelivery` | Date | Hayır | Beklenen teslimat tarihi |
| `totalAmount` | Decimal | Evet | Toplam tutar |
| `revisionNumber` | Integer | Evet | Varsayılan 1 |
| `changeReason` | String (TEXT) | Hayır | Revizyon gerekçesi |
| `notes` | String (TEXT) | Hayır | Notlar |
| `attachments` | JSONB | Hayır | Ekler |

### Status Akışı

```
DRAFT → SENT → CONFIRMED → PARTIALLY_RECEIVED → RECEIVED → CLOSED
                          ↘ CANCELLED
```

### Event'ler

| Event | Önem |
|---|---|
| `PoConfirmed` | NORMAL |
| `PoPartiallyReceived` | NORMAL |
| `PoDeliveryLate` | HIGH |

---

## 2. PurchaseOrderLine

> Tablo: `procurement.purchase_order_line`

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `purchaseOrderId` | UUID | Evet | FK → PurchaseOrder |
| `rfqLineId` | UUID | Hayır | FK → SupplierRFQLine |
| `materialId` | UUID | Hayır | FK → Material |
| `productDesc` | String (TEXT) | Hayır | Serbest tanım |
| `qty` | Decimal | Evet | Sipariş miktarı |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `unitPrice` | Decimal | Evet | Birim fiyat |
| `currency` | Enum | Evet | Para birimi |
| `totalPrice` | Decimal | Evet | Toplam fiyat |

---

## Anlaşmalı Tedarikçi — Direkt PO

Sözleşmeli fiyatı olan tedarikçiler için RFQ atlanır:

```
WorkOrder → PURCHASE kararı
    ↓
Sistem kontrol: Bu material + tradingPartner için aktif PriceList kaydı var mı?
    ↓
Evet → PO direkt oluşturulur (PriceList fiyatıyla)
Hayır → RFQ akışı başlar
```

> **Sözleşmeli fiyat:** `PriceListItem.tradingPartnerId` FK ile tedarikçiye özgü fiyat.
> **Detay:** `06-costing/price-list.md`

---

## Maliyet Entegrasyonu

GoodsReceipt CONFIRMED olunca 3 güncelleme yapılır:

1. **PO'ya özgü:** `PurchaseOrderLine.unitPrice` → bu PO'ya özel
2. **PriceList güncelleme:** Fiilen ödenen fiyat → aktif PriceList güncellenir
3. **CostHistory kaydı:** Trend analizi için tarihsel kayıt

> **Detay:** `06-costing/price-list.md`, `06-costing/exchange-rate-history.md`

---

## Cari Hesap Bağlantısı (Placeholder)

| Olay | Cari Hareket |
|---|---|
| PO CONFIRMED | Tedarikçi borcu doğar |
| GoodsReceipt CONFIRMED | Borç kesinleşir |
| PO CLOSED | Ödeme tamamlandı |

> **Detay:** `11-cross-cutting/cari-hesap-iskelet.md`

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/work-order.md` | WorkOrder.fulfillmentType=PURCHASE, fulfillmentId |
| `02-production/goods-receipt.md` | GoodsReceipt.sourceType=PURCHASE_ORDER |
| `04-procurement/supplier-quote.md` | PO.supplierQuoteId |
| `06-costing/price-list.md` | PriceList + PriceListItem güncelleme |
| `11-cross-cutting/cari-hesap-iskelet.md` | Cari borç tetikleyicisi |

---

## Açık Kararlar

- [ ] Sözleşmeli tedarikçi: ayrı `SupplierContract` entity'si mi gerekecek?
- [ ] Kısmi teslimat yönetimi detaylandırılacak

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — TEK kanonik tanım, production şemasından taşındı |

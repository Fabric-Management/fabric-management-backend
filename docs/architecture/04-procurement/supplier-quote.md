# SupplierQuote — Tedarikçi Teklifi

> Modül: Tedarik Zinciri (04-procurement)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: SupplierQuote, SupplierQuoteLine, SupplierQuoteToken burada tanımlanır.

---

## Genel Bakış

Tedarikçinin verdiği fiyat teklifi. Portal veya e-posta ile gelebilir. N teklif karşılaştırılarak en uygun seçilir → PurchaseOrder veya SubcontractOrder oluşturulur.

---

## 1. SupplierQuote

> Tablo: `procurement.supplier_quote`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `quoteNumber` | String | Evet | **Otomatik** — `SQ-2024-001` |
| `rfqId` | UUID | Evet | FK → SupplierRFQ |
| `tradingPartnerId` | UUID | Evet | FK → TradingPartner |
| `status` | SupplierQuoteStatus (Enum) | Evet | RECEIVED / ACCEPTED / REJECTED / EXPIRED |
| `validUntil` | Date | Evet | Teklif geçerlilik tarihi |
| `currency` | Enum | Evet | USD / EUR / TRY |
| `paymentTerms` | Enum | Hayır | CASH / NET_30 / NET_60 |
| `leadTimeDays` | Integer | Hayır | Teslimat süresi |
| `entryMethod` | Enum | Evet | PORTAL / MANUAL_ENTRY |
| `notes` | String (TEXT) | Hayır | Tedarikçi notları |
| `attachments` | JSONB | Hayır | Teknik belgeler |
| `submittedAt` | Timestamp | Hayır | Teklif giriş zamanı |

## 2. SupplierQuoteLine

> Tablo: `procurement.supplier_quote_line`

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `supplierQuoteId` | UUID | Evet | FK → SupplierQuote |
| `rfqLineId` | UUID | Evet | FK → SupplierRFQLine |
| `unitPrice` | Decimal | Evet | Birim fiyat |
| `currency` | Enum | Evet | Para birimi |
| `qty` | Decimal | Evet | Teklif edilen miktar |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `volumeDiscounts` | JSONB | Hayır | Toplu alım indirimleri |
| `notes` | String (TEXT) | Hayır | Kalem notu |

## 3. SupplierQuoteToken

> Tablo: `procurement.supplier_quote_token`

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `rfqRecipientId` | UUID | Evet | FK → SupplierRFQRecipient |
| `token` | String | Evet | Tahmin edilemez UUID |
| `expiresAt` | Timestamp | Evet | Token geçerlilik süresi |
| `status` | Enum | Evet | PENDING / USED / EXPIRED |
| `usedAt` | Timestamp | Hayır | Kullanılma zamanı |
| `entryMethod` | Enum | Evet | PORTAL / EMAIL |

---

## Fiyat Karşılaştırma

Sistem 4 kriteri gösterir — karar insanda:

| Kriter | Açıklama |
|---|---|
| En düşük fiyat | SupplierQuoteLine.unitPrice |
| En kısa teslimat | SupplierQuote.leadTimeDays |
| En uzun geçerlilik | SupplierQuote.validUntil |
| Geçmiş performans | CostHistory'den |

Seçim yapılınca: seçilen → ACCEPTED, diğerleri → REJECTED.

---

## Event'ler

| Event | Önem |
|---|---|
| `SupplierQuoteReceived` | NORMAL |

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `04-procurement/supplier-rfq.md` | SupplierQuote.rfqId |
| `04-procurement/purchase-order.md` | Kabul → PO oluşturulur |
| `04-procurement/subcontract-order.md` | Kabul → SC oluşturulur |
| `06-costing/exchange-rate-history.md` | CostHistory — geçmiş performans |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon |

# SupplierRFQ — Fiyat Teklifi Talebi

> Modül: Tedarik Zinciri (04-procurement)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: SupplierRFQ, SupplierRFQLine, SupplierRFQRecipient burada tanımlanır.

---

## Genel Bakış

RFQ birden fazla tedarikçiye aynı anda fiyat teklifi talebi gönderir. WorkOrder'ın `fulfillmentType = PURCHASE / SUBCONTRACT` olduğunda ve anlaşmalı tedarikçi yoksa başlatılır.

---

## 1. SupplierRFQ

> Tablo: `procurement.supplier_rfq`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `rfqNumber` | String | Evet | **Otomatik** — `RFQ-2024-001` |
| `workOrderId` | UUID | Evet | FK → WorkOrder |
| `moduleType` | Enum | Evet | FIBER / YARN / FABRIC / DYE_FINISHING |
| `rfqType` | Enum | Evet | PURCHASE / SUBCONTRACT |
| `status` | SupplierRFQStatus (Enum) | Evet | Bkz. `11-cross-cutting/status-enum-catalog.md` |
| `deadline` | Date | Evet | Teklif son tarihi |
| `notes` | String (TEXT) | Hayır | Tedarikçiye özel notlar |
| `attachments` | JSONB | Hayır | Teknik şartname, numune bilgisi |

## 2. SupplierRFQRecipient

> Tablo: `procurement.supplier_rfq_recipient`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `rfqId` | UUID | Evet | FK → SupplierRFQ |
| `tradingPartnerId` | UUID | Evet | FK → TradingPartner |
| `sentAt` | Timestamp | Hayır | Gönderim zamanı |
| `status` | Enum | Evet | SENT / QUOTE_RECEIVED / NO_RESPONSE |
| `responseDeadline` | Date | Hayır | Bu tedarikçi için son tarih |

## 3. SupplierRFQLine

> Tablo: `procurement.supplier_rfq_line`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `rfqId` | UUID | Evet | FK → SupplierRFQ |
| `materialId` | UUID | Hayır | FK → Material |
| `productDesc` | String (TEXT) | Hayır | Serbest tanım |
| `requestedQty` | Decimal | Evet | Talep edilen miktar |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `moduleSpecs` | JSONB | Hayır | certificationReq, originReq... |

---

## Event'ler

| Event | Önem | Açıklama |
|---|---|---|
| `RfqSent` | NORMAL | RFQ tedarikçilere gönderildi |
| `RfqDeadlineApproaching` | HIGH | Deadline yaklaşıyor — yanıt gelmedi |
| `RfqNoResponse` | HIGH | Tedarikçi yanıt vermedi |

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/work-order.md` | SupplierRFQ.workOrderId → WorkOrder |
| `04-procurement/supplier-quote.md` | RFQ → SupplierQuote zinciri |
| `01-foundations/trading-partner.md` | SupplierRFQRecipient.tradingPartnerId |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon |

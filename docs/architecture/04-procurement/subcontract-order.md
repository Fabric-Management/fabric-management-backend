# SubcontractOrder — Fason İş Siparişi

> Modül: Tedarik Zinciri (04-procurement)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: SubcontractOrder TEK kanonik tanımı burasıdır.

---

## Genel Bakış

Fason iş siparişi: hammaddeyi biz gönderiyoruz, fason firma işliyor, işlenmiş ürünü geri gönderiyor. Hammadde çıkışı ve geri dönüş fire takibi bu dökümanın kapsamındadır.

> **Önemli:** Eski `production.prod_subcontract_order` tanımı kaldırılmıştır. SubcontractOrder sadece `procurement` şemasında yaşar.

---

## SubcontractOrder

> Tablo: `procurement.subcontract_order`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `scNumber` | String | Evet | **Otomatik** — `SC-2024-001` |
| `workOrderId` | UUID | Evet | FK → WorkOrder |
| `tradingPartnerId` | UUID | Evet | FK → TradingPartner (fason firma) |
| `supplierQuoteId` | UUID | Hayır | FK → SupplierQuote |
| `status` | SubcontractOrderStatus (Enum) | Evet | Bkz. status akışı |
| `serviceType` | String (255) | Evet | Yapılacak iş tanımı |
| `materialSent` | JSONB | Hayır | Gönderilen hammadde listesi |
| `materialSentAt` | Timestamp | Hayır | Hammadde gönderim zamanı |
| `expectedReturn` | Date | Hayır | Geri dönüş tarihi |
| `unitPrice` | Decimal | Hayır | Hizmet birim fiyatı |
| `currency` | Enum | Hayır | Para birimi |
| `totalAmount` | Decimal | Hayır | Toplam tutar |
| `notes` | String (TEXT) | Hayır | Notlar |

### Status Akışı

```
DRAFT → SENT → CONFIRMED → IN_PROGRESS → COMPLETED → CLOSED
                          ↘ CANCELLED
```

---

## Hammadde Çıkış/Giriş Akışı

```
SubcontractOrder CONFIRMED
    ↓
Hammadde gönderilir
  IWM: StockTransaction ISSUE (RAW → fason firmaya çıkış)
  SubcontractOrder.materialSentAt = now()
  SubcontractOrder.status = IN_PROGRESS
    ↓
Fason firma üretimi tamamlar
    ↓
İşlenmiş ürün depoya gelir
  Depo personeli GoodsReceipt formu açar
  sourceType = SUBCONTRACT_ORDER
  GoodsReceiptItem'lar girilir
    ↓
GoodsReceipt CONFIRMED
  IWM: StockTransaction RECEIPT
  SubcontractOrder.status → COMPLETED (otomatik)
    ↓
Fire kontrolü:
  Gönderilen miktar (materialSent toplamı) vs geri dönen miktar (GR.netWeight)
  Fark > tolerans → StockTransaction REJECT (fire kaydı)
  Fark ≤ tolerans → kabul edilebilir fire
```

### materialSent JSONB Yapısı

```json
[
  {
    "materialId": "uuid-cotton",
    "materialName": "Cotton GOTS",
    "qty": 500,
    "unit": "KG",
    "lotNumber": "LOT-2024-001",
    "sentAt": "2024-03-15T10:00:00Z"
  }
]
```

> **İleride:** `materialSent` JSONB ayrı tabloya taşınabilir (fire takibi detaylandırıldığında).

---

## IWM Tetikleyicileri

| Olay | StockTransaction | Açıklama |
|---|---|---|
| SC CONFIRMED | `ISSUE` | Hammadde RAW'dan çıkış |
| GR CONFIRMED (dönüş) | `RECEIPT` | İşlenmiş ürün FINISHED'a giriş |
| Fire tespit | `REJECT` | Fark miktarı fire olarak kaydedilir |

---

## Cari Hesap Bağlantısı (Placeholder)

| Olay | Cari Hareket |
|---|---|
| SC COMPLETED | Fason borcu kesinleşir |
| SC CLOSED | Ödeme tamamlandı |

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/work-order.md` | WorkOrder.fulfillmentType=SUBCONTRACT |
| `02-production/goods-receipt.md` | GoodsReceipt.sourceType=SUBCONTRACT_ORDER |
| `05-iwm/stock-transaction-ledger.md` | ISSUE + RECEIPT + REJECT transactions |
| `04-procurement/supplier-quote.md` | SC.supplierQuoteId |
| `11-cross-cutting/cari-hesap-iskelet.md` | Cari borç tetikleyicisi |

---

## Açık Kararlar

- [ ] materialSent JSONB → ayrı tablo geçişi zamanlaması
- [ ] Fire tolerans eşiği — tenant bazında yapılandırılabilir mi?
- [ ] Hammadde gönderim bildirim akışı

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — hammadde çıkış/giriş ve fire akışı eklendi |

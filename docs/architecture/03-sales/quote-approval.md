# Quote & Onay Mekanizması

> Modül: Satış Zinciri (03-sales)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Quote, QuoteLine, QuoteApprovalToken burada tanımlanır.

---

## Genel Bakış

Quote resmi sipariş öncesi fiyat teklifidir. Revize edilebilir, versiyonlanır. Müşteri onayı 4 farklı yöntemle alınabilir. Onaylanan Quote SalesOrder'a dönüştürülür.

---

## 1. Quote

> Tablo: `sales.quote`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `quoteNumber` | String | Evet | **Otomatik** — `QT-2024-001` |
| `customerId` | UUID | Evet | FK → TradingPartner |
| `assignedToId` | UUID | Evet | FK → User (pazarlamacı) |
| `moduleType` | Enum | Evet | FIBER / YARN / FABRIC / DYE_FINISHING |
| `status` | QuoteStatus (Enum) | Evet | Bkz. `11-cross-cutting/status-enum-catalog.md` |
| `estimatedUnitCost` | Decimal | Hayır | Tahmini birim maliyet — CostCalculation'dan |
| `validUntil` | Date | Evet | Geçerlilik tarihi |
| `paymentTerms` | Enum | Hayır | CASH / NET_30 / NET_60 / OPEN_ACCOUNT |
| `leadTimeDays` | Integer | Hayır | Teslimat süresi |
| `notes` | String (TEXT) | Hayır | Notlar |
| `attachments` | JSONB | Hayır | Ekler |
| `revisionNumber` | Integer | Evet | Kaçıncı revizyon — varsayılan 1 |
| `parentQuoteId` | UUID | Hayır | Revize edilen Quote'un atası |
| `offlineCreatedAt` | Timestamp | Hayır | Offline oluşturulduysa |
| `deviceId` | String | Hayır | Hangi tabletten |

> **estimatedUnitCost:** Quote oluşturulurken CostCalculation service'i aktif PriceList'ten tahmini maliyet hesaplar ve buraya yazar. DiscountPolicy bu değeri kullanarak kar marjını kontrol eder.

### Versiyonlama

```
Müşteri "fiyatı düşürür müsün?" der
    ↓
Pazarlamacı mevcut Quote'u revize eder
    ↓
Yeni Quote: revisionNumber = 2, parentQuoteId = eski
Eski Quote: status = SUPERSEDED
    ↓
Müşteriye yeni link gönderilir
```

---

## 2. QuoteLine

> Tablo: `sales.quote_line`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `quoteId` | UUID | Evet | FK → Quote |
| `materialId` | UUID | Hayır | FK → Material |
| `productDesc` | String (TEXT) | Hayır | Serbest tanım |
| `requestedQty` | Decimal | Evet | Miktar |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `listPrice` | Decimal | Evet | Katalog liste fiyatı |
| `offeredPrice` | Decimal | Evet | Teklif edilen fiyat |
| `discountRate` | Decimal | Evet | İndirim oranı (%) — **otomatik** |
| `profitMargin` | Decimal | Evet | Kar marjı (%) — **otomatik** |
| `priceZone` | Enum | Evet | FREE / MANAGER_APPROVAL / BLOCKED — **otomatik** |
| `moduleSpecs` | JSONB | Hayır | certificationReq, originReq vb. |

---

## 3. QuoteApprovalToken

> Tablo: `sales.quote_approval_token`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `quoteId` | UUID | Evet | FK → Quote |
| `token` | String | Evet | Tahmin edilemez UUID — tek kullanımlık |
| `channel` | Enum | Evet | EMAIL / WHATSAPP / IN_PERSON |
| `sentTo` | String | Hayır | E-posta adresi veya telefon |
| `expiresAt` | Timestamp | Evet | Token geçerlilik süresi |
| `status` | Enum | Evet | PENDING / USED / EXPIRED |
| `usedAt` | Timestamp | Hayır | Kullanılma zamanı |
| `ipAddress` | String | Hayır | Müşteri IP adresi |
| `userAgent` | String | Hayır | Tarayıcı bilgisi |
| `location` | JSONB | Hayır | Konum (enlem/boylam) |

**Onay URL:** `https://app.domain.com/quote/approve?token={token}`

### Süresi Dolmuş Link Akışı

```
Müşteri süresi dolmuş linke tıklar
    ↓
"Teklif Süresi Doldu" sayfası
  "Temsilciniz [Ad Soyad] sizinle iletişime geçecek."
    ↓
Pazarlamacıya bildirim: QuoteExpiredLinkClicked
```

---

## 4. Onay Yöntemleri

| Yöntem | approvalMethod | Nasıl çalışır |
|---|---|---|
| E-posta onay linki | `EMAIL_LINK` | Token link gönderilir, müşteri tıklar |
| Sözlü onay | `VERBAL` | Pazarlamacı işaretler, tarih+konum kaydedilir |
| Fotoğraf | `PHOTO` | Kartvizit/kimlik fotoğrafı çekilir |
| Dijital imza | `DIGITAL_SIGNATURE` | Tablet ekranında müşteri imzalar |

---

## Quote → SalesOrder Dönüşümü

```
Pazarlamacı "Siparişe Dönüştür" tıklar
    ↓
Tüm QuoteLine alanları SalesOrderLine'a kopyalanır
Kullanıcı eksik alanları doldurur (deadline vb.)
    ↓
Quote.status → CONVERTED
SalesOrder.quoteId → Quote.id
SalesOrder.status = CONFIRMED (onay zaten alındı)
    ↓
FlowBoard → PLANNING task açılır
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `03-sales/sales-order.md` | Quote → SalesOrder dönüşümü |
| `03-sales/discount-policy.md` | priceZone belirleme, profitMargin hesaplama |
| `03-sales/product-catalog.md` | listPrice kaynağı |
| `06-costing/cost-calculation.md` | Quote.estimatedUnitCost hesaplama |
| `08-notification-i18n/notification-hub.md` | Onay e-postası, süresi dolmuş link bildirimi |

---

## Açık Kararlar

- [ ] WhatsApp Business API entegrasyonu
- [ ] Dijital imza yasal geçerliliği araştırılacak
- [ ] Manager onay süreci — Quote için ApprovalRequest mi kullanılsın?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — estimatedUnitCost eklendi, productId→materialId |

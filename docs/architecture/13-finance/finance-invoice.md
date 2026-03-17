# Finance — Fatura Yönetimi

> Modül: Finance (13-finance)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Invoice entity'si burada tanımlanır.

---

## Genel Bakış

AR (Accounts Receivable) ve AP (Accounts Payable) fatura yönetimi. TradingPartner ile müşteri/tedarikçi ilişkisi. Lifecycle: draft → issued → sent → paid. Vade takibi ve kısmi ödeme destekli. Tüm tablolar `finance` şemasında.

---

## Invoice

> Tablo: `finance.finance_invoice`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `tradingPartnerId` | UUID | Evet | FK → TradingPartner |
| `invoiceNumber` | String | Evet | Tenant içinde benzersiz |
| `orderReference` | String | Hayır | Sipariş referansı (metin — FK yok) |
| `externalReference` | String | Hayır | Dış sistem referansı |
| `invoiceType` | InvoiceType (Enum) | Evet | SALES / PURCHASE / CREDIT_NOTE / DEBIT_NOTE / PROFORMA |
| `status` | InvoiceStatus (Enum) | Evet | Bkz. status akışı |
| `issueDate` | LocalDate | Evet | Fatura tarihi |
| `dueDate` | LocalDate | Hayır | Vade tarihi |
| `paymentDate` | LocalDate | Hayır | Ödeme tarihi |
| `subtotal` | Decimal | Evet | Ara toplam |
| `taxAmount` | Decimal | Hayır | Vergi tutarı |
| `discountAmount` | Decimal | Hayır | İndirim tutarı |
| `totalAmount` | Decimal | Evet | Toplam tutar |
| `amountPaid` | Decimal | Evet | Ödenen tutar |
| `amountDue` | Decimal | Evet | Kalan tutar |
| `currency` | String | Evet | Para birimi — varsayılan TRY |
| `taxRate` | Decimal | Hayır | Vergi oranı |
| `billingAddress` | String (TEXT) | Hayır | Fatura adresi |
| `notes` | String (TEXT) | Hayır | Notlar |
| `metadata` | JSONB | Hayır | Payment terms, incoterms vb. |

### InvoiceType

| Değer | Açıklama | AR/AP |
|---|---|---|
| `SALES` | Satış faturası | AR (alacak) |
| `PURCHASE` | Alım faturası | AP (borç) |
| `CREDIT_NOTE` | Alacak dekontu | — |
| `DEBIT_NOTE` | Borç dekontu | — |
| `PROFORMA` | Proforma fatura | — |

### InvoiceStatus Akışı

```
DRAFT → ISSUED → SENT → PARTIALLY_PAID → PAID
                      → OVERDUE
     ↘ CANCELLED / VOIDED / DISPUTED
```

### API

Base path: `/api/v1/invoices`

CRUD + lifecycle (issue, send, recordPayment, cancel, void) + sorgular (partner, unpaid, outstanding, overdue, AR/AP) + batch markOverdue.

---

## Cari Hesap İskelet ile İlişki

Bu modül dökümanlarımızdaki `11-cross-cutting/cari-hesap-iskelet.md`'nin somut implementasyonunun **başlangıcı**. Invoice entity AccountTransaction'ların temelini oluşturur. İleride:

- PO CONFIRMED → Invoice (PURCHASE) otomatik oluşturulabilir
- SO SHIPPED → Invoice (SALES) otomatik oluşturulabilir
- RMA PROCESSED → Invoice (CREDIT_NOTE) otomatik oluşturulabilir

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/trading-partner.md` | Invoice.tradingPartnerId |
| `11-cross-cutting/cari-hesap-iskelet.md` | Cari hesap konsepti — Invoice temel yapı taşı |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek koddan |

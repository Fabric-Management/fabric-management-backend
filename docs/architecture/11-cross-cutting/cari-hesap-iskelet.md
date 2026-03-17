# Cari Hesap — İskelet Tasarım

> Modül: Cross-Cutting (11-cross-cutting) | Versiyon: 0.1 | Durum: Placeholder
> Son güncelleme: 2026-03-17

## Genel Bakış

Cari Hesap modülü ileride ayrıca tasarlanacak. Bu döküman bağlantı noktalarını tanımlar.

## İskelet Entity'ler

**AccountLedger:** tradingPartnerId FK, accountType (PAYABLE/RECEIVABLE), currency, balance.

**AccountTransaction:** accountLedgerId FK, transactionType (INVOICE/PAYMENT/CREDIT_NOTE/DEBIT_NOTE/OFFSET), amount, currency, sourceType (PO/SO/RMA/MANUAL), sourceId (polimorfik), transactedAt, dueDate, status (PENDING/COMPLETED/OVERDUE/CANCELLED).

## Tetikleyiciler

| Event | AccountTransaction |
|---|---|
| PO CONFIRMED | INVOICE (PAYABLE) — tedarikçi borcu |
| GoodsReceipt CONFIRMED | Borç kesinleşir |
| SO SHIPPED | INVOICE (RECEIVABLE) — müşteri alacağı |
| Ödeme yapıldı | PAYMENT |
| RMA PROCESSED + CREDIT | CREDIT_NOTE |
| RMA PROCESSED + OFFSET | OFFSET |

## Bağlı Modüller

purchase-order.md, subcontract-order.md, sales-order.md, rma.md

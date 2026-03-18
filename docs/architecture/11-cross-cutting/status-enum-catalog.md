# Status Enum Kataloğu

> Modül: Cross-Cutting (11-cross-cutting)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Tüm status enum'larının TEK listesi burasıdır.

---

## Genel Bakış

Bu döküman sistemdeki tüm status enum'larını, geçiş kurallarını ve tetikleyicilerini listeler. Enum tanımı **sadece burada** yapılır — modül dökümanları buraya referans verir.

---

## Üretim Zinciri

### WorkOrderStatus

```java
DRAFT → PENDING_APPROVAL → APPROVED → SENT → IN_PROGRESS → COMPLETED
                         ↘ REJECTED → DRAFT
DRAFT / PENDING_APPROVAL / APPROVED / SENT / IN_PROGRESS → CANCELLED
```

| Değer | Açıklama | Tetikleyen |
|---|---|---|
| `DRAFT` | Taslak | Oluşturulduğunda |
| `PENDING_APPROVAL` | Onay bekliyor | ApprovalPolicy eşleşiyorsa |
| `APPROVED` | Onaylandı | ApprovalRequest APPROVED |
| `REJECTED` | Reddedildi | ApprovalRequest REJECTED → DRAFT'a dönebilir |
| `SENT` | Gönderildi | Send Email tıklanınca |
| `IN_PROGRESS` | Üretimde | İlk Batch açıldığında |
| `COMPLETED` | Tamamlandı | Tüm Batch'ler COMPLETED |
| `CANCELLED` | İptal | Kullanıcı (COMPLETED hariç her yerden) |

> **Kanonik tanım:** `02-production/work-order.md`

---

### BatchStatus

```
OPEN → IN_PROGRESS → QC_PENDING → QC_PASSED → COMPLETED
                                ↘ QC_FAILED → IN_PROGRESS
     ↘ CANCELLED
```

| Değer | Açıklama | Tetikleyen |
|---|---|---|
| `OPEN` | Açıldı | Oluşturulduğunda |
| `IN_PROGRESS` | Üretimde | Personel başlattığında |
| `QC_PENDING` | QC bekliyor | "Batch'i kapat" + GR DRAFT oluştu |
| `QC_PASSED` | QC geçti | QC onayı → GR CONFIRMED |
| `QC_FAILED` | QC başarısız | QC reddi → yeniden işlem |
| `COMPLETED` | Tamamlandı | QC_PASSED sonrası otomatik |
| `CANCELLED` | İptal | Kullanıcı |

> **Kanonik tanım:** `02-production/batch-production.md`

---

### GoodsReceiptStatus

```
DRAFT → CONFIRMED
```

| Değer | Açıklama | Tetikleyen |
|---|---|---|
| `DRAFT` | Taslak | Batch kapatma veya form doldurma |
| `CONFIRMED` | Kesinleşti | QC_PASSED (batch) veya personel onayı (dış alım) |

> **Kanonik tanım:** `02-production/goods-receipt.md`

---

## Satış Zinciri

### SalesOrderStatus

```
DRAFT → CONFIRMED → IN_PRODUCTION → IN_WAREHOUSE → SHIPPED → DELIVERED → CLOSED
                  ↘ CANCELLED (DELIVERED ve CLOSED hariç)
```

| Değer | Açıklama | Tetikleyen |
|---|---|---|
| `DRAFT` | Taslak | Oluşturulduğunda |
| `CONFIRMED` | Onaylandı | Kullanıcı onaylar |
| `IN_PRODUCTION` | Üretimde | İlk WorkOrder IN_PROGRESS |
| `IN_WAREHOUSE` | Depoda | Tüm SOL'lar IN_WAREHOUSE |
| `SHIPPED` | Gönderildi | IWM çıkış hareketi |
| `DELIVERED` | Teslim edildi | Teslimat onayı |
| `CLOSED` | Kapatıldı | Kullanıcı |
| `CANCELLED` | İptal | DELIVERED/CLOSED hariç |

> **Kanonik tanım:** `03-sales/sales-order.md`

---

### SalesOrderLineStatus

```
PENDING → RECIPE_ASSIGNED → IN_PRODUCTION → COMPLETED → IN_WAREHOUSE → SHIPPED
        ↘ CANCELLED
```

| Değer | Açıklama | Tetikleyen |
|---|---|---|
| `PENDING` | Bekliyor | Oluşturulduğunda |
| `RECIPE_ASSIGNED` | Recipe atandı | RuleEngine veya manuel |
| `IN_PRODUCTION` | Üretimde | Bağlı WorkOrder IN_PROGRESS |
| `COMPLETED` | Üretim bitti | Tüm bağlı WO'lar COMPLETED |
| `IN_WAREHOUSE` | Depoda | IWM ProductStoredEvent |
| `SHIPPED` | Gönderildi | IWM çıkış hareketi |
| `CANCELLED` | İptal | Kullanıcı |

> **Kanonik tanım:** `03-sales/sales-order.md`

---

### QuoteStatus

```
DRAFT → PENDING_MANAGER_APPROVAL → SENT → ACCEPTED → CONVERTED
                                        ↘ REJECTED / EXPIRED / SUPERSEDED
     ↘ CANCELLED
```

| Değer | Açıklama |
|---|---|
| `DRAFT` | Taslak |
| `PENDING_MANAGER_APPROVAL` | İndirim manager bölgesinde |
| `SENT` | Müşteriye gönderildi |
| `ACCEPTED` | Müşteri onayladı |
| `REJECTED` | Müşteri reddetti |
| `EXPIRED` | Süresi doldu |
| `CONVERTED` | SalesOrder'a dönüştürüldü |
| `SUPERSEDED` | Yeni revizyon oluşturuldu |
| `CANCELLED` | İptal |

> **Kanonik tanım:** `03-sales/quote-approval.md`

---

### SampleRequestStatus

```
REQUESTED → PREPARING → DISPATCHED → DELIVERED → CONVERTED_TO_ORDER / NO_ORDER
          ↘ CANCELLED
```

> **Kanonik tanım:** `03-sales/sample-management.md`

---

## Tedarik Zinciri

### SupplierRFQStatus

```
DRAFT → SENT → PARTIALLY_RECEIVED → FULLY_RECEIVED → CLOSED
                                                    ↘ CANCELLED
```

> **Kanonik tanım:** `04-procurement/supplier-rfq.md`

---

### SupplierQuoteStatus

| Değer | Açıklama |
|---|---|
| `RECEIVED` | Teklif geldi |
| `ACCEPTED` | Teklif kabul edildi → PO oluşturulur |
| `REJECTED` | Teklif reddedildi |
| `EXPIRED` | Süresi doldu |

> **Kanonik tanım:** `04-procurement/supplier-quote.md`

---

### PurchaseOrderStatus

```
DRAFT → SENT → CONFIRMED → PARTIALLY_RECEIVED → RECEIVED → CLOSED
                          ↘ CANCELLED
```

> **Kanonik tanım:** `04-procurement/purchase-order.md`

---

### SubcontractOrderStatus

```
DRAFT → SENT → CONFIRMED → IN_PROGRESS → COMPLETED → CLOSED
                          ↘ CANCELLED
```

> **Kanonik tanım:** `04-procurement/subcontract-order.md`

---

## IWM

### StockReservationStatus

```
ACTIVE → CONVERTED (sipariş SHIPPED) / RELEASED (sipariş iptal)
```

> **Kanonik tanım:** `05-iwm/stock-reservation.md`

---

### StockCountStatus

```
PLANNED → IN_PROGRESS → COMPLETED / CANCELLED
```

> **Kanonik tanım:** `05-iwm/stock-count.md`

---

### StockTransferStatus

```
DRAFT → IN_TRANSIT → COMPLETED
```

> **Kanonik tanım:** `05-iwm/transfer.md`

---

### RMAStatus

```
PENDING → APPROVED → RECEIVED → PROCESSED / REJECTED
```

> **Kanonik tanım:** `05-iwm/rma.md`

---

### StockAdjustmentRequestStatus

```
PENDING → APPROVED / REJECTED
```

> **Kanonik tanım:** `05-iwm/manual-adjustment.md`

---

## Onay Sistemi

### ApprovalRequestStatus

```
PENDING → APPROVED / REJECTED / CANCELLED (süresi dolunca)
```

> **Kanonik tanım:** `09-approval/approval-request.md`

---

### UserPromotionRequestStatus

```
PENDING → APPROVED / REJECTED
```

> **Kanonik tanım:** `09-approval/approval-request.md`

---

## FlowBoard

### TaskStatus

```
BACKLOG → TODO → IN_PROGRESS → IN_REVIEW → DONE
                ↘ BLOCKED (herhangi bir yerden)
                ↘ CANCELLED
BLOCKED → IN_PROGRESS
DONE → IN_PROGRESS (yeniden açılırsa)
```

> **Kanonik tanım:** `07-flowboard/board-task.md`

---

## Ortak Enum'lar

### ModuleType

```java
FIBER, YARN, FABRIC, DYE_FINISHING, GENERAL
```
Kullanım: SalesOrder, SalesOrderLine, FlowBoard Board/Task, CostItem, DiscountPolicy, LotEndRule

### FulfillmentType

```java
INTERNAL, PURCHASE, SUBCONTRACT
```
Kullanım: WorkOrder

### Currency

```java
USD, EUR, TRY, GBP
```

### Unit

```java
KG, MT, PIECE
```

### BatchSourceType

```java
INTERNAL_PRODUCTION, PURCHASE, SUBCONTRACT, ADJUSTMENT, RETURN, INITIAL_STOCK
```
Kullanım: Batch.sourceType — "bu batch nereden geldi?"

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — 18 status enum, WorkOrderStatus düzeltildi |

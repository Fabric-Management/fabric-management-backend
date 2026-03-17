# WorkOrder — İş Emri

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: WorkOrder ve WorkOrderAssignee entity'leri burada tanımlanır.

---

## Genel Bakış

WorkOrder üretim planlama belgesidir. Recipe ve TradingPartner seçilince ilgili alanlar otomatik dolar. Üretimin nasıl karşılanacağı `fulfillmentType` ile belirlenir: kendi fabrikasında (INTERNAL), dış tedarikçiden (PURCHASE), veya fason firma ile (SUBCONTRACT).

---

## 1. WorkOrder

> Tablo: `production.prod_work_order`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `workOrderNumber` | String | Evet | **Otomatik** — `WO-2024-0001` |
| `recipeId` | UUID | Hayır | FK → Recipe — DRAFT'ta boş olabilir |
| `tradingPartnerId` | UUID | Hayır | FK → TradingPartner (tedarikçi) |
| `salesOrderLineId` | UUID | Hayır | FK → SalesOrderLine — null ise iç planlama |
| `fulfillmentType` | FulfillmentType (Enum) | Evet | INTERNAL / PURCHASE / SUBCONTRACT |
| `fulfillmentId` | UUID | Hayır | FK → PurchaseOrder veya SubcontractOrder — INTERNAL için null |
| `plannedQty` | Decimal | Evet | Planlanan miktar |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `unitCost` | Decimal | Hayır | Kullanıcı girer |
| `currency` | Enum | Hayır | USD / EUR / TRY |
| `plannedCost` | Decimal | Hayır | CostCalculation (PLANNED) sonucu — snapshot |
| `plannedCostCurrency` | String (10) | Hayır | Planlı maliyet para birimi |
| `status` | WorkOrderStatus (Enum) | Evet | Bkz. Status Akışı |
| `deadline` | Date | Hayır | Teslim tarihi — FlowBoard'a event olarak akar |
| `notes` | String (TEXT) | Hayır | Supplier'a özel üretim talimatı |
| `attachments` | JSONB | Hayır | Referans dokümanlar, dosya URL listesi |

### salesOrderLineId — Müşteri Siparişi Bağlantısı

- **Dolu:** SalesOrder → RuleEngine → WorkOrder zinciri ile oluşturulmuş. SalesOrderLine'ın `lineStatus` geçişleri bu WorkOrder'ın durumuna göre otomatik güncellenir.
- **Null:** İç planlama — müşteri siparişine bağlı değil, stok veya iç ihtiyaç için üretim.
- **Birden fazla WorkOrder aynı SalesOrderLine'a bağlanabilir:** Sipariş bölünme senaryosu (500kg sipariş → 300kg Supplier A + 200kg Supplier B → 2 ayrı WorkOrder).

### Supplier Snapshot Alanları

TradingPartner seçildiği anda aktif sertifika bilgileri kopyalanır.

| Alan | Tip | Açıklama |
|---|---|---|
| `supplierCertificationCode` | String | Snapshot — o anki `certificationCode` |
| `supplierLicenseNo` | String | Snapshot — o anki `licenseNo` |
| `supplierLicenseValidUntil` | Date | Snapshot — o anki `validUntil` |

> **Neden snapshot?** GOTS denetiminde "bu iş emrini verdiğinde supplier sertifikalı mıydı?" sorusuna cevap verebilmek için.  
> **Kontrol:** `supplierLicenseValidUntil < today` ise uyarı gösterilir — engellenmez.

---

## Status Akışı

### WorkOrderStatus Enum

```java
public enum WorkOrderStatus {
    DRAFT,              // Taslak — alanlar doldurulabilir
    PENDING_APPROVAL,   // Onay bekliyor (PROBATION/STANDARD kullanıcı)
    APPROVED,           // Onaylandı — gönderilmeye hazır
    REJECTED,           // Reddedildi — DRAFT'a geri dönebilir
    SENT,               // Tedarikçiye gönderildi
    IN_PROGRESS,        // Üretim başladı (ilk Batch açıldı)
    COMPLETED,          // Tüm Batch'ler tamamlandı
    CANCELLED           // İptal edildi
}
```

### Kullanıcı Seviyesine Göre Akış

**PROBATION kullanıcısı** (ApprovalPolicy devrede):
```
DRAFT → PENDING_APPROVAL → APPROVED → SENT → IN_PROGRESS → COMPLETED
                         ↘ REJECTED → DRAFT
```

**STANDARD kullanıcısı** (ApprovalPolicy'de kural varsa yukarıdaki akış, yoksa):
```
DRAFT → SENT → IN_PROGRESS → COMPLETED
```

**TRUSTED kullanıcısı** (her zaman):
```
DRAFT → SENT → IN_PROGRESS → COMPLETED
```

**İptal:**
```
DRAFT / PENDING_APPROVAL / APPROVED / SENT / IN_PROGRESS → CANCELLED
COMPLETED → iptal edilemez
```

### Geçiş Kuralları

| Geçiş | Tetikleyen | Koşul |
|---|---|---|
| `DRAFT → PENDING_APPROVAL` | Kullanıcı "Gönder" tıklar | ApprovalPolicy eşleşiyor |
| `DRAFT → SENT` | Kullanıcı "Gönder" tıklar | ApprovalPolicy eşleşmiyor |
| `PENDING_APPROVAL → APPROVED` | Onaylayan kabul eder | ApprovalRequest.status = APPROVED |
| `PENDING_APPROVAL → REJECTED` | Onaylayan reddeder | ApprovalRequest.status = REJECTED |
| `REJECTED → DRAFT` | Otomatik | Kullanıcı düzeltip tekrar gönderebilir |
| `APPROVED → SENT` | Kullanıcı "Send Email" tıklar | status = APPROVED zorunlu |
| `SENT → IN_PROGRESS` | Batch oluşturulduğunda | İlk Batch kaydı açılır |
| `IN_PROGRESS → COMPLETED` | Son Batch kapatıldığında | Tüm Batch'ler COMPLETED |

> **Send Email butonu:** Yalnızca `status = APPROVED` durumunda aktif.

### Event'ler

| Geçiş | Event | Alıcı |
|---|---|---|
| `→ PENDING_APPROVAL` | `WorkOrderPendingApproval` | FlowBoard (APPROVAL task), NotificationHub |
| `→ APPROVED` | `WorkOrderApproved` | FlowBoard (PRODUCTION task), NotificationHub |
| `deadline` set edildi | `WorkOrderDeadlineSet` | FlowBoard (PriorityScore güncelleme) |

> **Tam event listesi:** `11-cross-cutting/event-catalog.md`

---

## FulfillmentType

| fulfillmentType | Açıklama | İlgili Entity |
|---|---|---|
| `INTERNAL` | Kendi fabrikasında üretim | Doğrudan Batch açılır |
| `PURCHASE` | Dış supplier'dan satın alma | PurchaseOrder (bkz. `04-procurement/purchase-order.md`) |
| `SUBCONTRACT` | Fason firma üretiyor | SubcontractOrder (bkz. `04-procurement/subcontract-order.md`) |

---

## 2. WorkOrderAssignee

> Tablo: `production.prod_work_order_assignee`  
> `BaseEntity`'den miras alır.

WorkOrder'a atanan kişiler ve departmanlar. Her satır bir atama kaydı.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `workOrderId` | UUID | Evet | FK → WorkOrder |
| `role` | AssigneeRole (Enum) | Evet | OWNER / ASSIGNEE / APPROVER / OBSERVER |
| `departmentId` | UUID | Hayır | FK → Department |
| `userId` | UUID | Hayır | FK → User |
| `assignedAt` | Timestamp | Evet | Atama zamanı |

**Validasyon kuralları:**
- `departmentId` veya `userId` — en az biri zorunlu, ikisi de null olamaz.
- `OWNER` rolü bir WorkOrder'da yalnızca 1 kayıt olabilir.
- Unique kısıt: `(workOrderId, role, userId)`

**UI akışı:** Departman seçilir → o departmandaki kullanıcılar dropdown'a gelir → kişi seçimi opsiyonel.

---

## WorkOrder Oluşturma Akışı

```
1. Kullanıcı recipe arar veya seçer → detaylar UI'da gösterilir
2. TradingPartner seçilir → snapshot alanlar otomatik dolar
3. supplierLicenseValidUntil < today → uyarı gösterilir
4. Kullanıcı plannedQty, unitCost, currency girer
5. "Create WorkOrder" veya "Create & Go WorkOrder"
```

### SalesOrder'dan Otomatik Oluşturma

```
SalesOrder CONFIRMED
    ↓
RuleEngine her SalesOrderLine için:
  - Recipe eşleştirme (4 adımlı kaskad — bkz. sales-order.md)
  - WorkOrder taslağı oluşturur (DRAFT)
  - salesOrderLineId = SalesOrderLine.id
  - plannedQty ← requestedQty
  - deadline ← SalesOrder.deadline
    ↓
FlowBoard'a task açılır
```

### WorkOrder → Batch İlişkisi

WorkOrder doğrudan Batch oluşturmaz. İlişki `sourceType + sourceId` ve `BatchLineage` üzerinden kurulur:

```
WorkOrder IN_PROGRESS olduğunda:

1. CONSUME — Hammadde Batch'leri tüketilir
   Recipe'ye göre gerekli hammadde miktarı hesaplanır
   Depodaki Batch'lerden consume yapılır
   Batch.quantity azalır, Batch.consumedQuantity artar
   InventoryTransaction: OUT yazılır

2. ÜRETİM — Makine çalışır, çıktı üretilir

3. YENİ BATCH — Üretim çıktısı yeni Batch olarak doğar
   Batch.sourceType = INTERNAL_PRODUCTION
   Batch.sourceId = WorkOrder.id
   Batch.status = PENDING_QC

4. LINEAGE — Soy ağacı kaydedilir
   Parent Batch'ler (tüketilen hammaddeler) → Child Batch (çıktı)
   BatchLineage kayıtları oluşturulur
   Attribute inheritance çalışır (ağırlıklı ortalama vb.)
```

> **Detay:** `02-production/batch-production.md` — Batch.sourceType/sourceId  
> **Detay:** `02-production/batch-lineage.md` — Lineage + attribute inheritance

---

## İlişki Özeti

```
Recipe ──────────────────────→ WorkOrder ──→ Batch (1:N)
TradingPartner ──→ (snapshot) → WorkOrder
SalesOrderLine ──→ (FK) ─────→ WorkOrder (N:1 — birden fazla WO aynı SOL'a bağlanabilir)
                                    │
                       ┌────────────┼─────────────┐
                       ↓            ↓             ↓
                 (INTERNAL)   PurchaseOrder  SubcontractOrder
                 Batch direkt   (procurement)  (procurement)

WorkOrder ──→ WorkOrderAssignee (OWNER · ASSIGNEE · APPROVER · OBSERVER)
WorkOrder ──→ ApprovalRequest (PENDING_APPROVAL durumunda)
WorkOrder ──→ CostCalculation (PLANNED aşamasında)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/recipe.md` | WorkOrder.recipeId → Recipe |
| `02-production/batch-production.md` | Batch.workOrderId → WorkOrder |
| `01-foundations/trading-partner.md` | WorkOrder.tradingPartnerId + snapshot |
| `01-foundations/organization-department.md` | WorkOrderAssignee.departmentId |
| `03-sales/sales-order.md` | WorkOrder.salesOrderLineId → SalesOrderLine |
| `04-procurement/purchase-order.md` | fulfillmentType = PURCHASE |
| `04-procurement/subcontract-order.md` | fulfillmentType = SUBCONTRACT |
| `06-costing/cost-calculation.md` | WorkOrder.plannedCost |
| `09-approval/approval-request.md` | PENDING_APPROVAL → ApprovalRequest |
| `11-cross-cutting/status-enum-catalog.md` | WorkOrderStatus tam tanımı |

---

## Açık Kararlar

- [ ] `workOrderNumber` format standardı onaylanacak
- [ ] COMPLETED → ARCHIVED geçişi eklenecek mi?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — status enum düzeltildi, salesOrderLineId FK eklendi, plannedCost eklendi, PO/SC tanımları procurement'a taşındı |

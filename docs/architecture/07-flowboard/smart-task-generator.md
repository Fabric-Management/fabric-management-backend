# SmartTaskGenerator & AutomationEngine

> Modül: FlowBoard (07-flowboard)  
> Versiyon: 2.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: SmartTaskGenerator akışı, TaskTemplate ve AutomationRule burada tanımlanır.  
> İlham: Monday.com Automation Center — If-Then-That otomasyon mantığı.

---

## Genel Bakış

FlowBoard'da iki otomasyon katmanı vardır:

1. **SmartTaskGenerator** — Domain event'lerden otomatik task oluşturur (deterministik, event-driven)
2. **AutomationRule** — Kullanıcı tanımlı if-then-that kuralları ile FlowBoard içi otomasyonlar yapar

Bu iki katman birbirini tamamlar: SmartTaskGenerator dış dünyadan gelen olayları task'a çevirir, AutomationRule ise FlowBoard içindeki olaylara tepki verir.

```
Dış Dünya (Event'ler)                    FlowBoard İçi
─────────────────────                    ──────────────────
SalesOrderConfirmed  ─┐                  Task status değişti ──┐
WorkOrderApproved    ─┤                  Task atandı          ─┤
BatchQcFailed        ─┼→ SmartTaskGen    Deadline yaklaştı    ─┼→ AutomationEngine
GoodsReceiptConfirmed─┤                  Etiket eklendi       ─┤
MinStockAlert        ─┘                  WIP aşıldı           ─┘
        ↓                                       ↓
    Task oluştur                         Aksiyon çalıştır
    PriorityScore hesapla                (bildirim, status, atama, vs.)
    Ata + bildir
```

---

## 1. TaskTemplate

> Tablo: `flowboard.task_template`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `eventType` | String | Evet | Tetikleyici event — `SalesOrderConfirmed` |
| `titleTemplate` | String (500) | Evet | `"Recipe ata — {salesOrder.orderNumber}"` |
| `taskType` | Enum | Evet | TaskType |
| `moduleType` | Enum | Hayır | null ise tüm modüller |
| `defaultPriority` | Enum | Evet | LOW / MEDIUM / HIGH / CRITICAL — varsayılan MEDIUM |
| `defaultAssigneeRole` | Enum | Evet | DEPARTMENT_ADMIN / MANAGER / ANY |
| `estimatedHours` | Decimal | Hayır | Varsayılan süre |
| `checklistTemplate` | JSONB | Hayır | Alt görev şablonları |
| `autoLabels` | JSONB | Hayır | Otomatik atanacak etiket ID'leri |
| `isActive` | Boolean | Evet | Aktif mi |

### checklistTemplate JSONB Örneği

```json
[
  {"title": "Mevcut stok durumunu kontrol et", "order": 1},
  {"title": "Uygun recipe seç", "order": 2},
  {"title": "WorkOrder taslağı oluştur", "order": 3},
  {"title": "Manager onayına sun", "order": 4}
]
```

### autoLabels JSONB Örneği

```json
["URGENT", "VIP_CLIENT"]
```

### Seed Data — Varsayılan TaskTemplate'ler

| eventType | taskType | titleTemplate | defaultPriority | estimatedHours |
|---|---|---|---|---|
| `SalesOrderConfirmed` | PLANNING | `"Planlama — {salesOrder.orderNumber}"` | HIGH | 2.0 |
| `WorkOrderPendingApproval` | APPROVAL | `"WO onay — {workOrder.orderNumber}"` | HIGH | 0.5 |
| `WorkOrderApproved` | PRODUCTION | `"Üretim başlat — {workOrder.orderNumber}"` | MEDIUM | 8.0 |
| `BatchQcPending` | QUALITY | `"QC kontrol — {batch.batchNumber}"` | MEDIUM | 1.0 |
| `BatchQcFailed` | QUALITY | `"QC başarısız — yeniden işlem — {batch.batchNumber}"` | CRITICAL | 4.0 |
| `GoodsReceiptConfirmed` | WAREHOUSE | `"Depo yerleştirme — {goodsReceipt.receiptNumber}"` | MEDIUM | 1.5 |
| `SalesOrderInWarehouse` | SHIPMENT | `"Sevkiyat hazırla — {salesOrder.orderNumber}"` | HIGH | 2.0 |
| `RecipeAssignmentNeeded` | RECIPE_ASSIGNMENT | `"Manuel recipe seç — {salesOrderLine.productDesc}"` | HIGH | 1.0 |
| `MinStockAlert` | PROCUREMENT | `"Stok kritik — {material.name} temin et"` | HIGH | 2.0 |
| `PoDeliveryLate` | PROCUREMENT | `"PO gecikti — {purchaseOrder.poNumber} takip et"` | HIGH | 1.0 |
| `CostVarianceDetected` | COSTING | `"Maliyet sapması — {costCalculation.referenceNumber}"` | MEDIUM | 1.5 |
| `ReturnRateExceeded` | RETURN | `"İade oranı aşıldı — {tradingPartner.companyName}"` | HIGH | 2.0 |
| `ApprovalPending` | APPROVAL | `"Onay bekliyor — {approvalRequest.referenceNumber}"` | HIGH | 0.5 |
| `UserPromotionReady` | APPROVAL | `"Kullanıcı yükseltme — {user.displayName}"` | MEDIUM | 0.5 |
| `SupplierLicenseExpiringSoon` | GENERAL | `"Lisans yenileme — {tradingPartner.companyName}"` | MEDIUM | 1.0 |

---

## 2. Event → Task Zinciri (SmartTaskGenerator)

> **Tam event listesi:** `11-cross-cutting/event-catalog.md`

### Akış

```
1. Event yayınlanır (Spring ApplicationEvent)
2. SmartTaskGeneratorListener dinler
3. TaskTemplate bulunur (eventType eşleşmesi)
4. Stok kontrolü (SalesOrderConfirmed için — bkz. Stok Kontrol Motoru)
5. Task oluşturulur:
   a. title → titleTemplate + event payload'dan değişken interpolasyonu
   b. deadline → bağlı entity'nin deadline'ından hesaplanır
   c. checklist → checklistTemplate'den oluşturulur
   d. labels → autoLabels'den atanır
   e. boardGroupId → deadline bazlı varsayılan gruba atanır
6. PriorityScore hesaplanır (labelBonus dahil)
7. BACKLOG'a düşer
8. Atama denenir (SYSTEM) → müsait personel varsa atanır, yoksa BACKLOG'da bekler
9. WebSocket üzerinden board kullanıcılarına TASK_CREATED event gönderilir
10. NotificationHub'a TaskAssigned event yayınlanır
```

---

## 3. Stok Kontrol Motoru

`SalesOrderConfirmed` tetiklendiğinde çalışır:

| Senaryo | Durum | Oluşan Task |
|---|---|---|
| Stok yeterli | requestedQty ≤ stok | WAREHOUSE — "Stoktan ayır" |
| Stok yetersiz | stok = 0 | PRODUCTION veya PROCUREMENT |
| Kısmi stok | 0 < stok < requestedQty | İkisi birden (miktarlar bölünür) |

PRODUCTION mu PROCUREMENT mu kararı kullanıcıya bırakılır — sistem her ikisini de task olarak önerir.

---

## 4. Otomatik Bağımlılıklar

SmartTaskGenerator task oluştururken bağımlılıkları da kurar:

```
PLANNING task (recipe ata)
    ↓ FINISH_TO_START
PRODUCTION task (batch başlat)
    ↓ FINISH_TO_START
QUALITY task (QC yap)
    ↓ FINISH_TO_START
WAREHOUSE task (lokasyona yerleştir)
    ↓ FINISH_TO_START
SHIPMENT task (sevkiyat hazırla)
```

> **Detay:** `07-flowboard/task-details.md` — TaskDependency + TaskRelation

---

## 5. AutomationRule (If-Then-That Kuralları)

> Tablo: `flowboard.automation_rule`  
> `BaseEntity`'den miras alır.  
> İlham: Monday.com Automation Center — trigger + condition + action

### Entity

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `name` | String (255) | Evet | Kural adı — "QC bitti → depo görevi aç" |
| `description` | String (TEXT) | Hayır | Kural açıklaması |
| `triggerType` | AutomationTriggerType (Enum) | Evet | Tetikleyici tipi |
| `triggerConfig` | JSONB | Evet | Tetikleyici detayları |
| `conditionConfig` | JSONB | Hayır | "and only if" koşulları — null ise her zaman çalışır |
| `actionType` | AutomationActionType (Enum) | Evet | Aksiyon tipi |
| `actionConfig` | JSONB | Evet | Aksiyon detayları |
| `boardId` | UUID | Hayır | FK → Board — null ise tüm board'larda aktif |
| `isActive` | Boolean | Evet | Aktif mi |
| `executionCount` | Long | Evet | Kaç kez çalıştı (varsayılan 0) |
| `lastExecutedAt` | Timestamp | Hayır | Son çalışma zamanı |
| `createdByUserId` | UUID | Evet | FK → User — kuralı oluşturan |

### AutomationTriggerType (Enum)

| Değer | Açıklama | triggerConfig Örneği |
|---|---|---|
| `STATUS_CHANGED` | Task status'ü değiştiğinde | `{"fromStatus": "IN_PROGRESS", "toStatus": "DONE"}` |
| `DEADLINE_APPROACHING` | Deadline'a yaklaşıldığında | `{"hoursBeforeDeadline": 24}` |
| `TASK_ASSIGNED` | Task birine atandığında | `{"assigneeType": "ANY"}` |
| `TASK_UNASSIGNED_TOO_LONG` | Task uzun süre atanmadığında | `{"maxHoursUnassigned": 2}` |
| `LABEL_ADDED` | Etiket eklendiğinde | `{"labelName": "VIP_CLIENT"}` |
| `LABEL_REMOVED` | Etiket kaldırıldığında | `{"labelName": "URGENT"}` |
| `CHECKLIST_COMPLETED` | Tüm checklist tamamlandığında | `{}` |
| `TIMER_EXCEEDED` | Toplam süre tahmini aştığında | `{"thresholdPercent": 150}` |
| `WIP_EXCEEDED` | Kullanıcı WIP limiti aşıldığında | `{}` |
| `PRIORITY_CHANGED` | Priority değiştiğinde | `{"newPriority": "CRITICAL"}` |

### AutomationActionType (Enum)

| Değer | Açıklama | actionConfig Örneği |
|---|---|---|
| `CHANGE_STATUS` | Task status'ünü değiştir | `{"newStatus": "IN_REVIEW"}` |
| `ASSIGN_USER` | Belirli kişiye ata | `{"userId": "...", "assignedBy": "SYSTEM"}` |
| `ASSIGN_DEPARTMENT` | Departman bazlı ata | `{"departmentId": "...", "role": "DEPARTMENT_ADMIN"}` |
| `NOTIFY_USER` | Belirli kişiye bildirim | `{"userId": "...", "message": "Task'ınız tamamlandı!"}` |
| `NOTIFY_MANAGER` | Manager'a bildirim | `{"message": "WIP limiti aşıldı!"}` |
| `CREATE_TASK` | Yeni task oluştur | `{"taskType": "WAREHOUSE", "titleTemplate": "Depo görevi — {task.title}"}` |
| `ADD_LABEL` | Etiket ekle | `{"labelName": "URGENT"}` |
| `REMOVE_LABEL` | Etiket kaldır | `{"labelName": "WAITING_EXTERNAL"}` |
| `UPDATE_PRIORITY` | Priority score güncelle | `{"priorityBonus": 20}` |
| `ESCALATE` | Eskalasyon tetikle | `{"escalateTo": "DEPARTMENT_ADMIN"}` |

### Seed Data — Varsayılan Otomasyon Kuralları

| # | Kural Adı | Trigger | Koşul | Aksiyon |
|---|---|---|---|---|
| 1 | QC bitti → depo görevi | `STATUS_CHANGED → DONE` | `taskType = QUALITY` | `CREATE_TASK(WAREHOUSE)` |
| 2 | Kritik deadline uyarısı | `DEADLINE_APPROACHING (24h)` | `priority IN [HIGH, CRITICAL]` | `NOTIFY_MANAGER` |
| 3 | WIP aşımı uyarısı | `WIP_EXCEEDED` | — | `NOTIFY_MANAGER("WIP limiti aşıldı!")` |
| 4 | Büyük task tıkandı | `STATUS_CHANGED → BLOCKED` | `estimatedHours > 8` | `ESCALATE(DEPARTMENT_ADMIN)` |
| 5 | VIP etiketi → öncelik | `LABEL_ADDED(VIP_CLIENT)` | — | `UPDATE_PRIORITY(+20)` |
| 6 | Checklist tamamlandı → review | `CHECKLIST_COMPLETED` | — | `CHANGE_STATUS(IN_REVIEW)` |
| 7 | Süre tahmini aşıldı | `TIMER_EXCEEDED(150%)` | — | `NOTIFY_MANAGER + ADD_LABEL(URGENT)` |
| 8 | Atanmamış task uyarısı | `TASK_UNASSIGNED_TOO_LONG(2h)` | — | `NOTIFY_MANAGER` |

### AutomationEngine — Çalışma Mekanizması

```
1. FlowBoard içi event tetiklenir (status değişikliği, atama, etiket, vs.)
2. AutomationEngine aktif kuralları tarar:
   a. triggerType eşleşmesi
   b. triggerConfig detay kontrolü
   c. conditionConfig koşul değerlendirmesi
3. Koşullar sağlanıyorsa:
   a. actionType + actionConfig ile aksiyon çalıştırılır
   b. executionCount++
   c. lastExecutedAt güncellenir
4. TaskActivityLog'a AUTOMATION_EXECUTED kaydı yazılır
5. Aksiyon başarısız olursa → log yazılır, kural devre dışı bırakılmaz

Güvenlik:
- CREATE_TASK aksiyonu → aynı entityId için aynı taskType'ta zaten açık task varsa → oluşturmaz (idempotent)
- Sonsuz döngü koruması: Bir otomasyon tarafından tetiklenen aksiyon başka otomasyonu tetikleyebilir (max 3 derinlik)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `07-flowboard/board-task.md` | Task, TaskLabel entity |
| `07-flowboard/task-details.md` | TaskDependency, TaskRelation — otomatik bağımlılıklar |
| `05-iwm/stock-transaction-ledger.md` | Stok kontrol motoru — StockLedger sorgusu |
| `08-notification-i18n/notification-hub.md` | NOTIFY_USER / NOTIFY_MANAGER aksiyonları |
| `11-cross-cutting/event-catalog.md` | Event → TaskType eşlemesi |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 2.0 | 2026-03-17 | AutomationRule eklendi. TaskTemplate'e defaultPriority/autoLabels alanları eklendi. Varsayılan seed data detaylandırıldı. AutomationEngine çalışma mekanizması tanımlandı. SmartTaskGenerator akışına WebSocket + label adımları eklendi. |
| 1.0 | 2026-03-17 | İlk versiyon |

# Board & Task — FlowBoard Çekirdeği

> Modül: FlowBoard (07-flowboard)  
> Versiyon: 2.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Board, BoardGroup, BoardView, Task, TaskAssignee, TaskLabel entity'leri burada tanımlanır.  
> İlham: Monday.com Work OS + Tekstil Domain Gereksinimleri

---

## Genel Bakış

FlowBoard işletmedeki tüm operasyonel görevlerin tek bir akışta yönetildiği modüldür. Sprint yok — saf Kanban akışı. PriorityScore deadline yaklaştıkça otomatik yükselir. Her aktif modül için ayrı Board oluşturulur.

### Mimari Felsefe

- **Sprint yok** — tekstil üretim hattı durmadan akar
- **Saf Kanban akışı** — sürekli akış, WIP disiplini, öncelik sırası
- **PriorityScore yönetir** — deadline yaklaştıkça task yukarı çıkar
- **Deterministic RuleEngine** — AI bağımlı değil
- **Çoklu görünüm** — Kanban, Tablo, Timeline, Takvim, Workload
- **Etiket & filtre** — esnek organizasyon ve hızlı erişim

---

## 1. Board

> Tablo: `flowboard.board`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `name` | String (255) | Evet | Board adı — "Fiber Board", "Global Board" |
| `boardType` | BoardType (Enum) | Evet | FIBER / YARN / FABRIC / DYE_FINISHING / TRADING / GLOBAL |
| `wipLimitDefault` | Integer | Evet | Varsayılan WIP limiti — varsayılan 5 |
| `isActive` | Boolean | Evet | Aktif mi |
| `description` | String (TEXT) | Hayır | Board açıklaması |
| `defaultViewType` | ViewType (Enum) | Evet | Varsayılan görünüm — varsayılan KANBAN |

### Board Tipleri ve Erişim

| boardType | Kimler görür | Hangi task'lar |
|---|---|---|
| `FIBER` | Fiber ekibi + Manager | moduleType = FIBER |
| `YARN` | Yarn ekibi + Manager | moduleType = YARN |
| `FABRIC` | Fabric ekibi + Manager | moduleType = FABRIC |
| `DYE_FINISHING` | Dye ekibi + Manager | moduleType = DYE_FINISHING |
| `TRADING` | Ticari ekip + Manager | PLANNING + SHIPMENT + APPROVAL |
| `GLOBAL` | Sadece Manager / Admin | Tüm modüller |

**Board oluşturma:** Tenant onboard olurken aktif modüller belirlenir → Board'lar otomatik oluşur.

---

## 2. BoardGroup

> Tablo: `flowboard.board_group`  
> `BaseEntity`'den miras alır.  
> İlham: Monday.com board grupları — renk kodlu, daraltılabilir, sıralanabilir item grupları.

Board içindeki task'ları mantıksal gruplara ayırmak için kullanılır. Her board'da varsayılan gruplar otomatik oluşur.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `boardId` | UUID | Evet | FK → Board |
| `name` | String (255) | Evet | Grup adı — "Bu Hafta", "Bekleyenler", "Acil" |
| `color` | String (7) | Evet | HEX renk kodu — `#FF5733` |
| `displayOrder` | Integer | Evet | Sıralama |
| `isCollapsed` | Boolean | Evet | Daraltılmış mı (varsayılan: false) |
| `groupType` | GroupType (Enum) | Evet | STATUS_BASED / DEADLINE_BASED / MANUAL / CUSTOM |
| `filterCriteria` | JSONB | Hayır | Otomatik gruplama kuralı |

### GroupType (Enum)

| Değer | Davranış | Kullanım |
|---|---|---|
| `STATUS_BASED` | Task'lar status'e göre otomatik gruplanır | Tablo görünümünde Kanban benzeri gruplama |
| `DEADLINE_BASED` | "Bu Hafta", "Gelecek Hafta", "Geciken" | Deadline bazlı akış takibi |
| `MANUAL` | Manager sürükle-bırak ile gruplar | Özel organizasyon |
| `CUSTOM` | filterCriteria JSONB ile dinamik | "CRITICAL priority + PRODUCTION taskType" |

### filterCriteria JSONB Örneği

```json
{
  "rules": [
    {"field": "priority", "operator": "IN", "values": ["HIGH", "CRITICAL"]},
    {"field": "taskType", "operator": "EQ", "value": "PRODUCTION"}
  ],
  "matchType": "ALL"
}
```

### Varsayılan Board Grupları (seed data)

Her yeni board oluştuğunda 3 varsayılan grup otomatik oluşur:

| Grup | Renk | groupType | Açıklama |
|---|---|---|---|
| Geciken | `#E74C3C` (kırmızı) | DEADLINE_BASED | `deadline < today` |
| Bu Hafta | `#F39C12` (turuncu) | DEADLINE_BASED | `deadline between today and endOfWeek` |
| Diğer | `#3498DB` (mavi) | MANUAL | Geri kalan task'lar |

---

## 3. BoardView

> Tablo: `flowboard.board_view`  
> `BaseEntity`'den miras alır.  
> İlham: Monday.com çoklu görünüm sistemi — Table, Kanban, Timeline, Calendar, Workload, Chart.

Her board birden fazla görünüme sahip olabilir. Kullanıcılar kişisel görünümler oluşturabilir veya paylaşılan görünümleri kullanabilir.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `boardId` | UUID | Evet | FK → Board |
| `name` | String (255) | Evet | Görünüm adı — "Üretim Kanban", "Haftalık Timeline" |
| `viewType` | ViewType (Enum) | Evet | KANBAN / TABLE / TIMELINE / CALENDAR / WORKLOAD |
| `config` | JSONB | Hayır | Görünüm konfigürasyonu (filtreler, sıralama, gizli alanlar) |
| `isDefault` | Boolean | Evet | Board açıldığında varsayılan görünüm mü |
| `createdByUserId` | UUID | Evet | FK → User |
| `isShared` | Boolean | Evet | Tüm board kullanıcıları görebilir mi |

### ViewType (Enum)

| Görünüm | Kullanım | Odak |
|---|---|---|
| `KANBAN` | Saf Kanban akışı — mevcut ana tasarım | status bazlı sütunlar, sürükle-bırak |
| `TABLE` | Tablo görünümü — sıralama, filtreleme, toplu işlem | spreadsheet benzeri, hızlı tarama |
| `TIMELINE` | Gantt/timeline — deadline bazlı zaman çizelgesi | deadline, süre, bağımlılık çizgileri |
| `CALENDAR` | Takvim görünümü — haftalık/aylık | deadline tarihlerinde task kartları |
| `WORKLOAD` | İş yükü dağılımı — kişi/department bazlı | kapasite çubuğu, aşırı yük uyarısı |

### config JSONB Yapısı

```json
{
  "filters": [
    {"field": "taskType", "operator": "IN", "values": ["PRODUCTION", "QUALITY"]},
    {"field": "priority", "operator": "GTE", "value": "HIGH"},
    {"field": "deadline", "operator": "BEFORE", "value": "2026-04-01"}
  ],
  "sortBy": [
    {"field": "priorityScore", "direction": "DESC"},
    {"field": "deadline", "direction": "ASC"}
  ],
  "hiddenColumns": ["estimatedHours", "entityId"],
  "groupBy": "status",
  "kanbanConfig": {
    "columnField": "status",
    "swimlaneField": "moduleType"
  },
  "timelineConfig": {
    "startField": "createdAt",
    "endField": "deadline",
    "groupBy": "moduleType"
  }
}
```

### Varsayılan Görünümler (seed data)

Her yeni board oluştuğunda otomatik oluşturulur:

| Görünüm | viewType | isDefault | Açıklama |
|---|---|---|---|
| Kanban | KANBAN | ✓ | Status bazlı sütunlar |
| Tablo | TABLE | ✗ | Tüm alanlar, priorityScore sıralı |
| Timeline | TIMELINE | ✗ | Deadline bazlı zaman çizelgesi |

---

## 4. Task

> Tablo: `flowboard.task`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskNumber` | String | Evet | **Otomatik** — `TSK-0001` |
| `boardId` | UUID | Evet | FK → Board |
| `boardGroupId` | UUID | Hayır | FK → BoardGroup — null ise varsayılan gruba düşer |
| `title` | String (500) | Evet | TaskTemplate'den otomatik üretilir |
| `description` | String (TEXT) | Hayır | Açıklama |
| `taskType` | TaskType (Enum) | Evet | Bkz. TaskType |
| `moduleType` | ModuleType (Enum) | Evet | FIBER / YARN / FABRIC / DYE_FINISHING / GENERAL |
| `priority` | Priority (Enum) | Evet | LOW / MEDIUM / HIGH / CRITICAL |
| `priorityScore` | Integer | Evet | **Otomatik** — deadline + taskType + entity önemine göre |
| `deadline` | Date | Hayır | Bağlı entity'den gelir |
| `estimatedHours` | Decimal | Hayır | Tahmini süre |
| `actualHours` | Decimal | Hayır | **Otomatik** — TaskTimeEntry'lerden toplam (bkz. `task-details.md`) |
| `status` | TaskStatus (Enum) | Evet | Bkz. `11-cross-cutting/status-enum-catalog.md` |
| `entityType` | Enum | Hayır | SALES_ORDER / WORK_ORDER / BATCH / GOODS_RECEIPT / ... |
| `entityId` | UUID | Hayır | Polimorfik FK — bağlı kaydın id'si |
| `startedAt` | Timestamp | Hayır | İlk IN_PROGRESS geçişi |
| `completedAt` | Timestamp | Hayır | DONE geçişi |

### TaskType (Enum) — Güncellenmiş

| Değer | Açıklama |
|---|---|
| `PLANNING` | Recipe ata, WorkOrder oluştur |
| `PRODUCTION` | Batch başlat, üretimi yönet |
| `QUALITY` | QC kontrol yap |
| `WAREHOUSE` | Lokasyona yerleştir, stok yönet |
| `SHIPMENT` | Sevkiyat hazırla, gönder |
| `APPROVAL` | Onay bekleyen işlem |
| `RECIPE_ASSIGNMENT` | Manuel recipe seçimi gerekiyor |
| `PROCUREMENT` | Tedarikçiden temin et |
| `COSTING` | Maliyet sapması inceleme |
| `SAMPLE` | Numune hazırlama/gönderme |
| `RETURN` | İade işleme |
| `STOCK_COUNT` | Stok sayımı |
| `MAINTENANCE` | Bakım görevi |
| `GENERAL` | Genel görev |

### Task Status Akışı

```
BACKLOG → TODO → IN_PROGRESS → IN_REVIEW → DONE
                ↘ BLOCKED (herhangi bir yerden)
                ↘ CANCELLED
BLOCKED → IN_PROGRESS
DONE → IN_PROGRESS (yeniden açılırsa)
```

**Status geçiş yan etkileri:**
- `→ IN_PROGRESS` : `startedAt` set edilir (ilk kez ise), aktif timer otomatik başlatılabilir
- `→ DONE` : `completedAt` set edilir, aktif timer durdurulur
- `→ BLOCKED` : aktif timer durdurulur, eskalasyon süre sayacı başlar
- `DONE → IN_PROGRESS` : `completedAt` null yapılır, `reopenedTaskCount++`

### PriorityScore Hesaplama

```
priorityScore = deadlineScore + taskTypeScore + entityScore + labelBonus

deadlineScore  : bugün - deadline (gün farkı) × ağırlık
taskTypeScore  : QUALITY > SHIPMENT > PRODUCTION > PLANNING > GENERAL
entityScore    : bağlı SalesOrder'ın toplam tutarına göre
labelBonus     : VIP_CLIENT → +20, URGENT → +15
```

Deadline geçtiyse score otomatik MAX → board'da kırmızıya döner.

---

## 5. TaskAssignee

> Tablo: `flowboard.task_assignee`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskId` | UUID | Evet | FK → Task |
| `departmentId` | UUID | Hayır | FK → Department |
| `userId` | UUID | Hayır | FK → User |
| `assignedBy` | Enum | Evet | SYSTEM / MANAGER / SELF |
| `assignedAt` | Timestamp | Evet | Atama zamanı |

### 3 Atama Yolu

| Yol | Nasıl çalışır | WIP dolu ise |
|---|---|---|
| `SYSTEM` | taskType + moduleType → departman → müsait personel | Sıradaki müsait personele geçer |
| `MANAGER` | Havuzdan seçer | Uyarı gösterilir, zorla atanabilir |
| `SELF` | Personel kendisi alır | Kesinlikle alamaz |

---

## 6. TaskLabel (Etiket Sistemi)

> İlham: Monday.com etiket sistemi — renkli label'lar, çoklu etiketleme, etiket bazlı filtreleme.

Task'lara esnek etiketler atanarak filtreleme, gruplama ve önceliklendirme zenginleştirilir.

### 6.1 TaskLabel

> Tablo: `flowboard.task_label` | `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `name` | String (100) | Evet | Etiket adı — "Acil", "VIP Müşteri" |
| `color` | String (7) | Evet | HEX renk kodu — `#E74C3C` |
| `boardId` | UUID | Hayır | FK → Board — null ise tüm board'larda geçerli (global etiket) |
| `icon` | String (10) | Hayır | Emoji ikonu — "🔴", "⭐" |

### 6.2 TaskLabelAssignment (M:N Bağlantı)

> Tablo: `flowboard.task_label_assignment`

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskId` | UUID | Evet | FK → Task |
| `labelId` | UUID | Evet | FK → TaskLabel |

**Unique constraint:** `(taskId, labelId)` — aynı etiket bir task'a birden fazla atanamaz.

### Varsayılan Etiketler (seed data — global)

| Etiket | Renk | İkon | Kullanım | PriorityScore Etkisi |
|---|---|---|---|---|
| `URGENT` | `#E74C3C` | 🔴 | Acil işlem gerekiyor | +15 |
| `VIP_CLIENT` | `#9B59B6` | ⭐ | VIP müşteri siparişi | +20 |
| `FIRST_ORDER` | `#3498DB` | 🆕 | İlk sipariş (yeni müşteri) | +10 |
| `SAMPLE` | `#2ECC71` | 🧪 | Numune ile ilgili | +0 |
| `REWORK` | `#E67E22` | 🔄 | Yeniden işlem | +5 |
| `WAITING_EXTERNAL` | `#95A5A6` | ⏳ | Dış bekleme (tedarikçi yanıtı vs.) | +0 |

**SmartTaskGenerator entegrasyonu:** Belirli event'lerde etiket otomatik atanır:
- `RecipeAssignmentNeeded` → `URGENT` etiketi
- `BatchQcFailed` → `REWORK` etiketi
- İlk sipariş tespit edilirse → `FIRST_ORDER` etiketi

---

## WIP Limiti

`User.wipLimit` — kişi başı, varsayılan 5. Manager değiştirebilir.

```
IN_PROGRESS task sayısı < wipLimit → yeni task alabilir
IN_PROGRESS task sayısı = wipLimit → yeni task alamaz (SELF)
```

---

## WebSocket — Gerçek Zamanlı Board Güncellemeleri

> İlham: Monday.com gerçek zamanlı board güncellemeleri.

Board'daki değişiklikler WebSocket (STOMP) üzerinden anlık olarak tüm board kullanıcılarına iletilir.

### WebSocket Kanalları

```
/topic/board/{boardId}          → Board güncellemeleri (task ekleme, status değişikliği, grup değişikliği)
/topic/task/{taskId}            → Task detay güncellemeleri (yorum, checklist, timer)
/user/{userId}/queue/tasks      → Kişisel task bildirimleri (atama, mention)
```

### BoardWebSocketEventType (Enum)

| Değer | Açıklama |
|---|---|
| `TASK_CREATED` | Yeni task oluştu |
| `TASK_STATUS_CHANGED` | Task status'ü değişti |
| `TASK_ASSIGNED` | Task birine atandı |
| `TASK_PRIORITY_UPDATED` | PriorityScore güncellendi |
| `TASK_COMMENTED` | Yeni yorum eklendi |
| `TASK_BLOCKED` | Task engellendi |
| `TASK_COMPLETED` | Task tamamlandı |
| `TASK_MOVED_GROUP` | Task farklı gruba taşındı |
| `CHECKLIST_UPDATED` | Checklist öğesi işaretlendi/kaldırıldı |
| `TIMER_STARTED` | Zaman takip timer'ı başladı |
| `TIMER_STOPPED` | Zaman takip timer'ı durdu |
| `LABEL_CHANGED` | Etiket eklendi/kaldırıldı |

### WebSocket Mesaj Yapısı

```json
{
  "eventType": "TASK_STATUS_CHANGED",
  "boardId": "...",
  "taskId": "...",
  "payload": {
    "oldStatus": "TODO",
    "newStatus": "IN_PROGRESS",
    "changedBy": "userId",
    "timestamp": "2026-03-17T14:30:00Z"
  }
}
```

> **Not:** WebSocket altyapısı Faz 7 (NotificationHub) ile kurulmuştur — FlowBoard bunu genişletir.

---

## Manager Dashboard (Operations View)

5 panel: Sipariş Bazlı, Personel Bazlı, Modül Bazlı, Tıkanıklık, Kapasite.

> **Detay:** `07-flowboard/performance.md` — DashboardConfig + DashboardWidget ile özelleştirilebilir.

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/user-auth.md` | User.wipLimit |
| `01-foundations/organization-department.md` | TaskAssignee.departmentId |
| `07-flowboard/smart-task-generator.md` | Event → Task oluşturma, AutomationRule |
| `07-flowboard/task-details.md` | TaskChecklist, TaskComment, TaskActivityLog, TaskTimeEntry, TaskAttachment, TaskReminder, TaskRelation |
| `07-flowboard/escalation.md` | EscalationLog |
| `07-flowboard/performance.md` | UserPerformanceSnapshot, DashboardConfig, DashboardWidget, Workload API |
| `07-flowboard/recurring-tasks.md` | RecurringTaskTemplate |
| `08-notification-i18n/notification-hub.md` | NotificationHub entegrasyonu |
| `11-cross-cutting/event-catalog.md` | Event → TaskType eşlemesi |
| `11-cross-cutting/polymorphic-fk-rules.md` | Task.entityType + entityId |

---

## Açık Kararlar

- [x] ~~Manager dashboard: WebSocket mı, periyodik yenileme mi?~~ → WebSocket (STOMP)
- [ ] PriorityScore ağırlıkları kesinleştirilecek
- [ ] BLOCKED task'larda manager müdahalesi zorunlu mu?
- [ ] TRADING board task tipleri kesinleştirilecek
- [ ] BoardView: Workload görünümünde estimatedHours mı totalTrackedHours mı baz alınacak?
- [ ] TaskLabel: Tenant-spesifik özel etiket oluşturma limiti?
- [ ] BoardGroup: CUSTOM gruplarda maksimum filtre derinliği?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 2.0 | 2026-03-17 | Monday.com ilhamlı genişleme — BoardGroup, BoardView, TaskLabel eklendi. Task'a boardGroupId/actualHours/startedAt/completedAt alanları eklendi. Board'a description/defaultViewType eklendi. WebSocket canlı board desteği. PriorityScore'a labelBonus eklendi. |
| 1.0 | 2026-03-17 | İlk versiyon — 5 yeni TaskType eklendi (COSTING, SAMPLE, RETURN, STOCK_COUNT, MAINTENANCE), bildirim tercihi NotificationHub'a taşındı |

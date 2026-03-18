# Board & Task — FlowBoard Çekirdeği

> Modül: FlowBoard (07-flowboard)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Board, Task, TaskAssignee entity'leri burada tanımlanır.

---

## Genel Bakış

FlowBoard işletmedeki tüm operasyonel görevlerin tek bir akışta yönetildiği modüldür. Sprint yok — saf Kanban akışı. PriorityScore deadline yaklaştıkça otomatik yükselir. Her aktif modül için ayrı Board oluşturulur.

### Mimari Felsefe

- **Sprint yok** — tekstil üretim hattı durmadan akar
- **Saf Kanban akışı** — sürekli akış, WIP disiplini, öncelik sırası
- **PriorityScore yönetir** — deadline yaklaştıkça task yukarı çıkar
- **Deterministic RuleEngine** — AI bağımlı değil

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

## 2. Task

> Tablo: `flowboard.task`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskNumber` | String | Evet | **Otomatik** — `TSK-0001` |
| `boardId` | UUID | Evet | FK → Board |
| `title` | String (500) | Evet | TaskTemplate'den otomatik üretilir |
| `description` | String (TEXT) | Hayır | Açıklama |
| `taskType` | TaskType (Enum) | Evet | Bkz. TaskType |
| `moduleType` | ModuleType (Enum) | Evet | FIBER / YARN / FABRIC / DYE_FINISHING / GENERAL |
| `priority` | Priority (Enum) | Evet | LOW / MEDIUM / HIGH / CRITICAL |
| `priorityScore` | Integer | Evet | **Otomatik** — deadline + taskType + entity önemine göre |
| `deadline` | Date | Hayır | Bağlı entity'den gelir |
| `estimatedHours` | Decimal | Hayır | Tahmini süre |
| `status` | TaskStatus (Enum) | Evet | Bkz. `11-cross-cutting/status-enum-catalog.md` |
| `entityType` | Enum | Hayır | SALES_ORDER / WORK_ORDER / BATCH / GOODS_RECEIPT / ... |
| `entityId` | UUID | Hayır | Polimorfik FK — bağlı kaydın id'si |

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

### PriorityScore Hesaplama

```
priorityScore = deadlineScore + taskTypeScore + entityScore

deadlineScore  : bugün - deadline (gün farkı) × ağırlık
taskTypeScore  : QUALITY > SHIPMENT > PRODUCTION > PLANNING > GENERAL
entityScore    : bağlı SalesOrder'ın toplam tutarına göre
```

Deadline geçtiyse score otomatik MAX → board'da kırmızıya döner.

---

## 3. TaskAssignee

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

## WIP Limiti

`User.wipLimit` — kişi başı, varsayılan 5. Manager değiştirebilir.

```
IN_PROGRESS task sayısı < wipLimit → yeni task alabilir
IN_PROGRESS task sayısı = wipLimit → yeni task alamaz (SELF)
```

---

## Manager Dashboard (Operations View)

5 panel: Sipariş Bazlı, Personel Bazlı, Modül Bazlı, Tıkanıklık, Kapasite.

> **Detay:** `07-flowboard/performance.md`

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/user-auth.md` | User.wipLimit |
| `01-foundations/organization-department.md` | TaskAssignee.departmentId |
| `07-flowboard/smart-task-generator.md` | Event → Task oluşturma |
| `07-flowboard/task-details.md` | TaskChecklist, TaskComment, TaskActivityLog |
| `11-cross-cutting/event-catalog.md` | Event → TaskType eşlemesi |
| `11-cross-cutting/polymorphic-fk-rules.md` | Task.entityType + entityId |

---

## Açık Kararlar

- [ ] PriorityScore ağırlıkları kesinleştirilecek
- [ ] BLOCKED task'larda manager müdahalesi zorunlu mu?
- [ ] TRADING board task tipleri kesinleştirilecek
- [ ] Manager dashboard: WebSocket mı, periyodik yenileme mi?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — 5 yeni TaskType eklendi (COSTING, SAMPLE, RETURN, STOCK_COUNT, MAINTENANCE), bildirim tercihi NotificationHub'a taşındı |

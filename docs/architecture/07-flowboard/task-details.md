# Task Detayları — Checklist, Yorum, Log, Bağımlılık, Zaman, Dosya, Hatırlatma, İlişki

> Modül: FlowBoard (07-flowboard)  
> Versiyon: 2.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: TaskChecklist, TaskComment, TaskActivityLog, TaskDependency, TaskTimeEntry, TaskAttachment, TaskReminder, TaskRelation burada tanımlanır.  
> İlham: Monday.com — Updates, Time Tracking, File Attachments, Connected Boards.

---

## 1. TaskChecklist

> Tablo: `flowboard.task_checklist` | `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskId` | UUID | Evet | FK → Task |
| `title` | String (255) | Evet | Alt görev başlığı |
| `isCompleted` | Boolean | Evet | Varsayılan false |
| `completedAt` | Timestamp | Hayır | Tamamlanma zamanı |
| `completedByUserId` | UUID | Hayır | FK → User — tamamlayan kişi |
| `displayOrder` | Integer | Evet | Sıralama |

**Otomasyon entegrasyonu:** Tüm checklist item'ları tamamlandığında `CHECKLIST_COMPLETED` event'i tetiklenir → AutomationRule ile otomatik status değişikliği yapılabilir (örn. IN_REVIEW'a geçiş).

---

## 2. TaskComment

> Tablo: `flowboard.task_comment` | `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskId` | UUID | Evet | FK → Task |
| `userId` | UUID | Evet | FK → User |
| `content` | String (TEXT) | Evet | Yorum içeriği |
| `mentionedUserIds` | JSONB | Hayır | @mention edilen kullanıcı ID'leri |

**@Mention desteği:** Yorum içinde `@userId` formatında mention yapılabilir. Mention edilen kullanıcılara NotificationHub üzerinden bildirim gönderilir.

**WebSocket:** Yeni yorum eklendiğinde `/topic/task/{taskId}` kanalına `TASK_COMMENTED` event gönderilir.

---

## 3. TaskActivityLog

> Tablo: `flowboard.task_activity_log` | `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskId` | UUID | Evet | FK → Task |
| `userId` | UUID | Hayır | FK → User — null ise SYSTEM tarafından |
| `action` | TaskAction (Enum) | Evet | Aksiyon tipi |
| `oldValue` | String | Hayır | Önceki değer |
| `newValue` | String | Hayır | Yeni değer |
| `metadata` | JSONB | Hayır | Ek bilgi (otomasyon adı, override gerekçesi, vs.) |

### TaskAction (Enum)

| Değer | Açıklama |
|---|---|
| `STATUS_CHANGED` | Task status'ü değişti |
| `ASSIGNED` | Task birine atandı |
| `REASSIGNED` | Task başka birine aktarıldı |
| `COMMENTED` | Yorum eklendi |
| `REOPENED` | DONE → IN_PROGRESS (yeniden açıldı) |
| `BLOCKED` | Task engellendi |
| `UNBLOCKED` | Task engeli kaldırıldı |
| `DEPENDENCY_OVERRIDDEN` | Bağımlılık kural dışı geçildi |
| `LABEL_ADDED` | Etiket eklendi |
| `LABEL_REMOVED` | Etiket kaldırıldı |
| `TIMER_STARTED` | Zaman takip timer'ı başladı |
| `TIMER_STOPPED` | Zaman takip timer'ı durdu |
| `CHECKLIST_COMPLETED` | Checklist tamamlandı |
| `ATTACHMENT_ADDED` | Dosya eklendi |
| `PRIORITY_CHANGED` | Öncelik değişti |
| `GROUP_CHANGED` | Board grubu değişti |
| `AUTOMATION_EXECUTED` | Otomasyon kuralı çalıştı |
| `REMINDER_SET` | Hatırlatma oluşturuldu |
| `DEADLINE_CHANGED` | Deadline değişti |

---

## 4. TaskDependency

> Tablo: `flowboard.task_dependency`

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskId` | UUID | Evet | FK → Task (bağımlı olan) |
| `dependsOnTaskId` | UUID | Evet | FK → Task (önce bitmesi gereken) |
| `dependencyType` | DependencyType (Enum) | Evet | FINISH_TO_START / START_TO_START / PARALLEL |

### DependencyType (Enum)

| Değer | Açıklama | Kullanım |
|---|---|---|
| `FINISH_TO_START` | Bağımlı task, bağımlılık DONE olmadan başlayamaz | PLANNING → PRODUCTION → QUALITY → WAREHOUSE → SHIPMENT |
| `START_TO_START` | İkisi birlikte başlayabilir | Paralel çalışması gereken ama ilişkili task'lar |
| `PARALLEL` | Bağımlılık belirtir ama bloklamaz | Bilgi amaçlı ilişki |

### Role Göre Override

| Rol | Bağımlılık geçilmek istendiğinde |
|---|---|
| Personel | Sistem engeller |
| Manager | Uyarı + gerekçe ile geçebilir |
| Admin | Her zaman geçebilir |

Override olduğunda `TaskActivityLog`'a `DEPENDENCY_OVERRIDDEN` yazılır — gerekçe zorunlu (`metadata.overrideReason`).

---

## 5. TaskTimeEntry (Zaman Takip)

> Tablo: `flowboard.task_time_entry` | `BaseEntity`'den miras alır.  
> İlham: Monday.com Time Tracking — Start/stop timer, manuel giriş, timesheet raporu.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskId` | UUID | Evet | FK → Task |
| `userId` | UUID | Evet | FK → User |
| `startedAt` | Timestamp | Evet | Başlangıç zamanı |
| `endedAt` | Timestamp | Hayır | Bitiş zamanı — null ise hala çalışıyor (aktif timer) |
| `durationMinutes` | Integer | Hayır | Süre (dakika) — endedAt set edilince otomatik hesaplanır |
| `entryType` | TimeEntryType (Enum) | Evet | TIMER / MANUAL |
| `note` | String (500) | Hayır | Çalışma notu |

### TimeEntryType (Enum)

| Değer | Açıklama |
|---|---|
| `TIMER` | Start/stop ile gerçek zamanlı takip |
| `MANUAL` | Geçmiş çalışma süresi elle ekleme |

### İş Kuralları

1. **Tek aktif timer:** Kullanıcı aynı anda sadece 1 timer çalıştırabilir (herhangi bir task'ta)
2. **Status kontrolü:** Task status `IN_PROGRESS` olmadan timer başlatılamaz
3. **Otomatik status:** Timer başlatıldığında task status'ü `TODO` ise otomatik `IN_PROGRESS` yapılır
4. **Otomatik durdurma:** Task `DONE` veya `BLOCKED` olduğunda aktif timer otomatik durdurulur
5. **Toplam hesaplama:** `Task.actualHours` = tüm ilişkili TaskTimeEntry'lerin `durationMinutes` toplamı / 60

### WebSocket Entegrasyonu

Timer başlatıldığında / durdurulduğunda:
- `/topic/board/{boardId}` → `TIMER_STARTED` / `TIMER_STOPPED`
- `TaskActivityLog` → `TIMER_STARTED` / `TIMER_STOPPED`

### Timesheet Raporu API

```
GET /api/flowboard/timesheet?userId={userId}&startDate={date}&endDate={date}

Yanıt:
{
  "userId": "...",
  "period": {"start": "2026-03-11", "end": "2026-03-17"},
  "totalMinutes": 2340,
  "dailyBreakdown": [
    {"date": "2026-03-11", "minutes": 480, "taskCount": 3},
    {"date": "2026-03-12", "minutes": 420, "taskCount": 2}
  ],
  "taskBreakdown": [
    {
      "taskId": "...",
      "taskNumber": "TSK-0042",
      "title": "Üretim başlat — WO-0015",
      "totalMinutes": 180,
      "entries": [
        {"startedAt": "...", "endedAt": "...", "durationMinutes": 120, "entryType": "TIMER"},
        {"startedAt": "...", "endedAt": "...", "durationMinutes": 60, "entryType": "MANUAL"}
      ]
    }
  ]
}
```

---

## 6. TaskAttachment (Dosya Ekleri)

> Tablo: `flowboard.task_attachment` | `BaseEntity`'den miras alır.  
> İlham: Monday.com dosya yükleme ve dosya görünümü.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskId` | UUID | Evet | FK → Task |
| `fileName` | String (500) | Evet | Orijinal dosya adı |
| `fileType` | String (50) | Evet | MIME type — `application/pdf`, `image/jpeg` |
| `fileSizeBytes` | Long | Evet | Dosya boyutu (byte) |
| `storagePath` | String | Evet | Object storage yolu (S3/MinIO) |
| `uploadedByUserId` | UUID | Evet | FK → User |
| `attachmentType` | AttachmentType (Enum) | Evet | Dosya kategorisi |
| `description` | String (255) | Hayır | Dosya açıklaması |

### AttachmentType (Enum)

| Değer | Açıklama | Kullanım |
|---|---|---|
| `DOCUMENT` | PDF, DOCX, XLSX | Rapor, sertifika, fatura |
| `IMAGE` | JPEG, PNG, WEBP | Üretim fotoğrafı, QC görsel |
| `REPORT` | Sistem tarafından üretilen rapor | QC raporu, maliyet raporu |
| `OTHER` | Diğer | Sınıflandırılamayan dosyalar |

### Kısıtlamalar

- Maksimum dosya boyutu: **25 MB** (konfigürasyon ile değiştirilebilir)
- Board bazlı toplam depolama limiti: Tenant aboneliğine göre
- İzin verilen dosya tipleri: PDF, DOCX, XLSX, JPEG, PNG, WEBP, CSV

---

## 7. TaskReminder (Hatırlatma Sistemi)

> Tablo: `flowboard.task_reminder` | `BaseEntity`'den miras alır.  
> İlham: Monday.com kişisel hatırlatmalar ve akıllı öneriler.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskId` | UUID | Evet | FK → Task |
| `userId` | UUID | Evet | FK → User — hatırlatılacak kişi |
| `reminderType` | ReminderType (Enum) | Evet | Hatırlatma tipi |
| `triggerAt` | Timestamp | Evet | Hatırlatma zamanı |
| `offsetMinutes` | Integer | Hayır | DEADLINE_OFFSET için → deadline'dan X dakika önce |
| `message` | String (500) | Hayır | Özel mesaj |
| `isSent` | Boolean | Evet | Gönderildi mi (varsayılan false) |
| `sentAt` | Timestamp | Hayır | Gönderim zamanı |
| `channel` | ReminderChannel (Enum) | Evet | IN_APP / EMAIL / BOTH |

### ReminderType (Enum)

| Değer | Açıklama |
|---|---|
| `MANUAL` | Kullanıcı tarafından belirli bir zamana kurulmuş |
| `DEADLINE_OFFSET` | Deadline'dan belirli süre önce otomatik tetiklenir |
| `FOLLOW_UP` | Task DONE olduktan sonra takip hatırlatması |

### Scheduler Job

`FlowBoardReminderJob` — her 5 dakikada çalışır:
1. `triggerAt <= now() AND isSent = false` olan hatırlatmalar bulunur
2. NotificationHub üzerinden bildirim gönderilir
3. `isSent = true`, `sentAt = now()` güncellenir

---

## 8. TaskRelation (Task İlişki Ağı)

> Tablo: `flowboard.task_relation` | `BaseEntity`'den miras alır.  
> İlham: Monday.com Connect Boards — board'lar arası veri bağlantısı.

TaskDependency'den farkı: TaskDependency iş akışı zorlama kuralıdır (FINISH_TO_START bloklama yapar). TaskRelation ise bilgi amaçlı ilişki tanımıdır — farklı board'lardaki task'lar arasında bile kurulabilir.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `sourceTaskId` | UUID | Evet | FK → Task (kaynak) |
| `targetTaskId` | UUID | Evet | FK → Task (hedef) |
| `relationType` | RelationType (Enum) | Evet | İlişki tipi |
| `createdByUserId` | UUID | Evet | FK → User |
| `note` | String (255) | Hayır | İlişki notu |

### RelationType (Enum)

| Değer | Açıklama | Kullanım |
|---|---|---|
| `RELATED` | İlişkili task'lar | Aynı SalesOrder'dan doğan farklı board task'ları |
| `DUPLICATES` | Tekrarlayan/mükerrer task | Aynı iş için açılmış iki task |
| `CAUSED_BY` | Sebep-sonuç ilişkisi | QC fail → rework task |
| `PARENT_CHILD` | Üst-alt görev ilişkisi | Büyük görevin alt parçalara bölünmesi |

### Otomatik İlişki Kurma

SmartTaskGenerator aynı `entityId` + `entityType` için birden fazla task oluştururken otomatik `RELATED` ilişki kurar. Örnek:

```
SalesOrder SO-0042 → PLANNING task (Fiber Board)
                   → PRODUCTION task (Fiber Board)
                   → QUALITY task (Fiber Board)
                   → WAREHOUSE task (Fiber Board)
                   → SHIPMENT task (Trading Board)

Tüm bu task'lar birbirine RELATED olarak bağlanır.
Ayrıca TaskDependency ile FINISH_TO_START zinciri kurulur.
```

### Cross-Board İlişki

Manager, farklı board'lardaki task'lar arasında manuel ilişki kurabilir:
- Fiber Board'daki PRODUCTION task'ı ↔ Trading Board'daki SHIPMENT task'ı
- Global Board'dan tüm ilişkili task'lar görülebilir

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `07-flowboard/board-task.md` | Task, TaskLabel entity tanımları |
| `07-flowboard/smart-task-generator.md` | AutomationRule — CHECKLIST_COMPLETED, TIMER_EXCEEDED trigger'ları |
| `07-flowboard/escalation.md` | EscalationLog |
| `07-flowboard/performance.md` | TaskTimeEntry → UserPerformanceSnapshot entegrasyonu |
| `08-notification-i18n/notification-hub.md` | @Mention bildirimleri, hatırlatma gönderimi |
| `11-cross-cutting/polymorphic-fk-rules.md` | Task.entityType + entityId |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 2.0 | 2026-03-17 | TaskTimeEntry (zaman takip), TaskAttachment (dosya ekleri), TaskReminder (hatırlatma), TaskRelation (cross-board ilişki) eklendi. TaskChecklist'e completedAt/completedByUserId eklendi. TaskComment'e mentionedUserIds eklendi. TaskActivityLog action enum genişletildi (17 değer). TaskDependency'ye START_TO_START tipi eklendi. |
| 1.0 | 2026-03-17 | İlk versiyon |

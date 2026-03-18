# Eskalasyon Sistemi

> Modül: FlowBoard (07-flowboard) | Versiyon: 2.0 | Son güncelleme: 2026-03-17  
> Kanonik kaynak: EscalationLog burada tanımlanır.

---

## Genel Bakış

Eskalasyon sistemi, operasyonel sorunları zamanında tespit ederek yöneticilere bildiren otomatik mekanizmadır. Scheduler job ile periyodik kontrol yapılır — tespit edilen durumlar EscalationLog'a yazılır ve NotificationHub üzerinden bildirim gönderilir.

---

## Eskalasyon Kuralları

| # | Kural | Tetikleyici | Aksiyon | Önem |
|---|---|---|---|---|
| 1 | Deadline geçti | `deadline < today AND status NOT IN (DONE, CANCELLED)` | priorityScore → MAX, tüm kanallardan bildirim, board'da kırmızı vurgulama | CRITICAL |
| 2 | 24 saat dokunulmadı | `status NOT IN (IN_PROGRESS, IN_REVIEW) AND deadline - today <= 2 gün` | Manager'a bildirim: "Task {taskNumber} deadline yaklaşıyor ama hareket yok" | HIGH |
| 3 | 48 saat BLOCKED | `status = BLOCKED AND (now - lastStatusChange) > 48h` | Üst yöneticiye bildirim: "Task {taskNumber} 48 saattir engelli" | CRITICAL |
| 4 | 2 saat atanmamış | `status = BACKLOG AND assignee = null AND (now - createdAt) > 2h` | Manager'a uyarı: "Task {taskNumber} 2 saattir atanmamış" | HIGH |
| 5 | Süre tahmini %200 aşıldı | `actualHours > estimatedHours * 2 AND status = IN_PROGRESS` | Manager'a bildirim: "Task {taskNumber} tahmini süreyi çok aştı" | HIGH |
| 6 | Aynı task 3 kez yeniden açıldı | `reopenCount >= 3` (TaskActivityLog'dan hesaplanır) | Manager + Admin'e bildirim: "Task {taskNumber} kalite sorunu" | CRITICAL |

### Eskalasyon Scheduler Job

`FlowBoardEscalationJob` — her 15 dakikada çalışır:

```
1. Açık task'ları tara (status NOT IN [DONE, CANCELLED])
2. Her eskalasyon kuralını kontrol et
3. Eşleşen task'lar için:
   a. Aynı task + aynı escalationType için son 24 saatte zaten log varsa → tekrar eskalasyon yapma (debounce)
   b. EscalationLog kaydı oluştur
   c. NotificationHub'a event yayınla (EscalationTriggered)
   d. Kural 1 ise → Task.priorityScore = Integer.MAX_VALUE
```

---

## EscalationLog

> Tablo: `flowboard.escalation_log` | `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `taskId` | UUID | Evet | FK → Task |
| `escalationType` | EscalationType (Enum) | Evet | Eskalasyon tipi |
| `escalatedToUserId` | UUID | Evet | FK → User — bildirim gönderilen yönetici |
| `message` | String (500) | Evet | Eskalasyon mesajı |
| `resolvedAt` | Timestamp | Hayır | Çözüme kavuşma zamanı |
| `resolvedByUserId` | UUID | Hayır | FK → User — çözen kişi |
| `resolutionNote` | String (500) | Hayır | Çözüm notu |

### EscalationType (Enum)

| Değer | Açıklama |
|---|---|
| `DEADLINE_PASSED` | Deadline geçti |
| `UNASSIGNED` | Task uzun süre atanmamış |
| `BLOCKED_TOO_LONG` | Task uzun süre engelli |
| `UNTOUCHED` | Task'a dokunulmadı, deadline yaklaşıyor |
| `TIME_EXCEEDED` | Tahmini süre çok aşıldı |
| `QUALITY_ISSUE` | Task defalarca yeniden açıldı |

### Eskalasyon Zinciri

Eğer ilk eskalasyondan 24 saat sonra hala çözülmediyse, bir üst seviyeye eskalasyon yapılır:

```
Seviye 1 (0-24 saat)  : Department Admin'e bildirim
Seviye 2 (24-48 saat)  : Manager'a bildirim
Seviye 3 (48+ saat)    : Admin'e bildirim + Task board'da KIRMIZI banner
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `07-flowboard/board-task.md` | Task.priorityScore → MAX güncelleme |
| `07-flowboard/smart-task-generator.md` | AutomationRule ESCALATE aksiyonu |
| `07-flowboard/task-details.md` | TaskActivityLog, TaskTimeEntry (süre aşımı kontrolü) |
| `08-notification-i18n/notification-hub.md` | EscalationTriggered event |
| `11-cross-cutting/event-catalog.md` | EscalationTriggered event |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 2.0 | 2026-03-17 | 2 yeni eskalasyon kuralı (süre aşımı, kalite sorunu). EscalationLog'a message/resolvedByUserId/resolutionNote alanları eklendi. EscalationType'a TIME_EXCEEDED/QUALITY_ISSUE değerleri eklendi. Scheduler job detayı ve debounce mantığı eklendi. Eskalasyon zinciri (3 seviye) tanımlandı. |
| 1.0 | 2026-03-17 | İlk versiyon |

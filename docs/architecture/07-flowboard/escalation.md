# Eskalasyon Sistemi

> Modül: FlowBoard (07-flowboard) | Versiyon: 1.0 | Son güncelleme: 2026-03-17  
> Kanonik kaynak: EscalationLog burada tanımlanır.

---

## Eskalasyon Kuralları

| Kural | Tetikleyici | Aksiyon |
|---|---|---|
| Deadline geçti | `deadline < today` | priorityScore → MAX, tüm kanallardan bildirim |
| 24 saat dokunulmadı | IN_PROGRESS değil + deadline yaklaşıyor | Manager'a bildirim |
| 48 saat BLOCKED | status = BLOCKED + 48 saat | Üst yöneticiye bildirim |
| 2 saat atanmamış | BACKLOG + assignee yok + 2 saat | Manager'a uyarı |

## EscalationLog

> Tablo: `flowboard.escalation_log`

| Alan | Tip | Açıklama |
|---|---|---|
| `taskId` | UUID | FK → Task |
| `escalationType` | Enum | DEADLINE_PASSED / UNASSIGNED / BLOCKED_TOO_LONG / UNTOUCHED |
| `escalatedTo` | UUID | FK → User |
| `resolvedAt` | Timestamp | Çözüme kavuştu mu |
| `createdAt` | Timestamp | Eskalasyon zamanı |

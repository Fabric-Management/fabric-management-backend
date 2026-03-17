# Task Detayları — Checklist, Yorum, Log, Bağımlılık

> Modül: FlowBoard (07-flowboard)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: TaskChecklist, TaskComment, TaskActivityLog, TaskDependency burada tanımlanır.

---

## 1. TaskChecklist

> Tablo: `flowboard.task_checklist` | `BaseEntity`'den miras alır.

| Alan | Tip | Açıklama |
|---|---|---|
| `taskId` | UUID | FK → Task |
| `title` | String (255) | Alt görev başlığı |
| `isCompleted` | Boolean | Varsayılan false |
| `displayOrder` | Integer | Sıralama |

## 2. TaskComment

> Tablo: `flowboard.task_comment` | `BaseEntity`'den miras alır.

| Alan | Tip | Açıklama |
|---|---|---|
| `taskId` | UUID | FK → Task |
| `userId` | UUID | FK → User |
| `content` | String (TEXT) | Yorum içeriği |

## 3. TaskActivityLog

> Tablo: `flowboard.task_activity_log` | `BaseEntity`'den miras alır.

| Alan | Tip | Açıklama |
|---|---|---|
| `taskId` | UUID | FK → Task |
| `userId` | UUID | FK → User |
| `action` | Enum | STATUS_CHANGED / ASSIGNED / REASSIGNED / COMMENTED / REOPENED / BLOCKED / DEPENDENCY_OVERRIDDEN |
| `oldValue` | String | Önceki değer |
| `newValue` | String | Yeni değer |

## 4. TaskDependency

> Tablo: `flowboard.task_dependency`

| Alan | Tip | Açıklama |
|---|---|---|
| `taskId` | UUID | FK → Task (bağımlı olan) |
| `dependsOnTaskId` | UUID | FK → Task (önce bitmesi gereken) |
| `dependencyType` | Enum | FINISH_TO_START / PARALLEL |

### Role Göre Override

| Rol | Bağımlılık geçilmek istendiğinde |
|---|---|
| Personel | Sistem engeller |
| Manager | Uyarı + gerekçe ile geçebilir |
| Admin | Her zaman geçebilir |

Override olduğunda `TaskActivityLog`'a `DEPENDENCY_OVERRIDDEN` yazılır — gerekçe zorunlu.

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon |

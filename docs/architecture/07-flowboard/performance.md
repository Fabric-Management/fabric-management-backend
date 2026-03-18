# Performans Takibi, Dashboard & Workload

> Modül: FlowBoard (07-flowboard) | Versiyon: 2.0 | Son güncelleme: 2026-03-17  
> Kanonik kaynak: UserPerformanceSnapshot, DashboardConfig, DashboardWidget burada tanımlanır.  
> İlham: Monday.com Dashboard Builder, Workload View, Performance Widgets.

---

## 1. Performans Metrikleri

Tüm metrikler `TaskActivityLog` ve `TaskTimeEntry` üzerinden hesaplanır.

| Metrik | Hesaplama | Kaynak |
|---|---|---|
| Tamamlanan task sayısı | status = DONE | TaskActivityLog |
| Ortalama tamamlama süresi | completedAt - startedAt | Task alanları |
| Geciken task oranı | completedAt > deadline | Task alanları |
| Self-assign oranı | assignedBy = SELF | TaskAssignee |
| Blocked task oranı | BLOCKED geçen / toplam | TaskActivityLog |
| Yeniden açılan task | DONE → IN_PROGRESS geçişleri | TaskActivityLog |
| Toplam iz süre | SUM(durationMinutes) | TaskTimeEntry |
| Tahmin doğruluğu | estimatedHours / actualHours oranı | Task alanları |
| Peş peşe tamamlama günü | Ardışık gün sayısı (en az 1 task/gün) | TaskActivityLog |

---

## 2. UserPerformanceSnapshot

> Tablo: `flowboard.user_performance_snapshot` | `BaseEntity`'den miras alır.

| Alan | Tip | Açıklama |
|---|---|---|
| `userId` | UUID | FK → User |
| `periodStart` | Date | Dönem başlangıcı |
| `periodEnd` | Date | Dönem bitişi |
| `completedTaskCount` | Integer | Tamamlanan task sayısı |
| `avgCompletionHours` | Decimal | Ortalama tamamlama süresi (saat) |
| `lateTaskCount` | Integer | Geciken task sayısı |
| `lateTaskRatio` | Decimal | Gecikme oranı (%) |
| `selfAssignCount` | Integer | Self-assign sayısı |
| `blockedTaskCount` | Integer | BLOCKED geçen sayısı |
| `reopenedTaskCount` | Integer | Yeniden açılan sayısı |
| `totalTrackedMinutes` | Integer | Toplam izlenen süre (dakika) — TaskTimeEntry'den |
| `avgTaskDurationMinutes` | Integer | Ortalama task süresi (dakika) — TaskTimeEntry'den |
| `estimateAccuracyPercent` | Decimal | Tahmin doğruluğu (%) — estimatedHours/actualHours |
| `streakDays` | Integer | Peş peşe tamamlama günü |
| `weeklyRank` | Integer | Departman haftalık sıralaması |
| `achievementBadges` | JSONB | Kazanılan rozetler |

**Hesaplama:** `FlowBoardPerformanceJob` — haftalık snapshot, pazar gece 02:00'de çalışır.

---

## 3. Gamification — Rozet Sistemi

> İlham: Monday.com motivasyon unsurları + operasyonel teşvik.

### Rozet Tanımları

| Rozet | İkon | Koşul | Açıklama |
|---|---|---|---|
| `FAST_FINISHER` | 🥇 | 10 task'ı deadline'dan önce tamamla (hafta içinde) | Hızlı tamamlama |
| `ON_FIRE` | 🔥 | 5 gün peş peşe en az 1 task tamamla | Süreklilik |
| `PRECISION_EXPERT` | 🎯 | estimateAccuracyPercent ≥ 90% (haftalık) | Doğru tahmin |
| `TEAM_PLAYER` | 🤝 | 5+ farklı departmandan task al (ay içinde) | Çoklu sorumluluk |
| `QUALITY_GUARDIAN` | 🛡️ | 0 yeniden açılan task (ay içinde) | Kalite bilinci |
| `ZERO_LATE` | ⏰ | 0 geciken task (haftalık) | Zamanında teslimat |
| `SELF_STARTER` | 🚀 | selfAssignCount ≥ 5 (haftalık) | İnisiyatif |
| `UNBLOCKABLE` | 💪 | 0 BLOCKED task (haftalık) | Sorunsuz çalışma |

### achievementBadges JSONB Yapısı

```json
[
  {"badge": "FAST_FINISHER", "earnedAt": "2026-03-17", "count": 3},
  {"badge": "ON_FIRE", "earnedAt": "2026-03-10", "count": 1},
  {"badge": "QUALITY_GUARDIAN", "earnedAt": "2026-03-17", "count": 2}
]
```

### Görünürlük

| Metrik | Manager | Personel |
|---|---|---|
| Tüm ekip metrikleri | ✓ | ✗ |
| Kendi metrikleri | ✓ | ✓ |
| Kendi rozetleri | ✓ | ✓ |
| Departman leaderboard | ✓ | ✓ (sadece sıra, detay yok) |

---

## 4. DashboardConfig (Özelleştirilebilir Dashboard)

> Tablo: `flowboard.dashboard_config` | `BaseEntity`'den miras alır.  
> İlham: Monday.com Dashboard Builder — sürükle-bırak widget'lar, çoklu board veri toplama.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `name` | String (255) | Evet | Dashboard adı — "Üretim Müdürü Dashboard" |
| `description` | String (TEXT) | Hayır | Dashboard açıklaması |
| `ownerUserId` | UUID | Evet | FK → User — dashboard sahibi |
| `isShared` | Boolean | Evet | Tüm ilgili kullanıcılar görebilir mi |
| `isDefault` | Boolean | Evet | Varsayılan dashboard mı |
| `layout` | JSONB | Evet | Widget pozisyonları (grid sistemi) |

### layout JSONB Yapısı

```json
{
  "columns": 12,
  "rows": "auto",
  "widgets": [
    {"widgetId": "uuid-1", "row": 0, "col": 0, "width": 4, "height": 2},
    {"widgetId": "uuid-2", "row": 0, "col": 4, "width": 8, "height": 2},
    {"widgetId": "uuid-3", "row": 2, "col": 0, "width": 6, "height": 3}
  ]
}
```

---

## 5. DashboardWidget

> Tablo: `flowboard.dashboard_widget` | `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `dashboardId` | UUID | Evet | FK → DashboardConfig |
| `widgetType` | WidgetType (Enum) | Evet | Widget tipi |
| `title` | String (255) | Evet | Widget başlığı — "Geciken Task'lar" |
| `config` | JSONB | Evet | Veri kaynağı, filtreler, görsel ayarlar |

### WidgetType (Enum)

| Değer | Açıklama | Veri Kaynağı | Görsel |
|---|---|---|---|
| `TASK_COUNT` | Toplam task sayısı (filtreli) | Task tablosu | Büyük numara kartı |
| `STATUS_DISTRIBUTION` | Status dağılımı | Task.status | Pasta grafik veya bar |
| `PRIORITY_HEATMAP` | Öncelik ısı haritası | Task.priorityScore | Renkli ızgara |
| `DEADLINE_CALENDAR` | Yaklaşan deadline'lar | Task.deadline | Mini takvim |
| `WORKLOAD_BAR` | Kişi bazlı iş yükü | TaskAssignee + estimatedHours | Yatay çubuk grafik |
| `COMPLETION_TREND` | Tamamlanma trendi | TaskActivityLog (7/30 gün) | Çizgi grafik |
| `CYCLE_TIME` | Ortalama tamamlama süresi | Task.startedAt → completedAt | Çubuk grafik + trend |
| `BLOCKED_TASKS` | Tıkanan task listesi | Task.status = BLOCKED | Tablo görünümü |
| `OVERDUE_TASKS` | Geciken task listesi | Task.deadline < today | Tablo — kırmızı vurgu |
| `LEADERBOARD` | En çok task tamamlayan | UserPerformanceSnapshot | Sıralama tablosu + rozet |
| `SLA_COMPLIANCE` | SLA uyum oranı | Deadline vs completion | Yüzde göstergesi |
| `MODULE_SUMMARY` | Modül bazlı özet | Task.moduleType | Karşılaştırmalı bar |

### Widget config JSONB Örnekleri

**TASK_COUNT widget:**
```json
{
  "boardIds": ["uuid-fiber", "uuid-yarn"],
  "filters": [
    {"field": "status", "operator": "NOT_IN", "values": ["DONE", "CANCELLED"]},
    {"field": "priority", "operator": "IN", "values": ["HIGH", "CRITICAL"]}
  ],
  "displayStyle": "BIG_NUMBER",
  "color": "#E74C3C"
}
```

**COMPLETION_TREND widget:**
```json
{
  "boardIds": ["uuid-fiber"],
  "period": "LAST_30_DAYS",
  "groupBy": "DAILY",
  "showTarget": true,
  "targetPerDay": 5
}
```

**WORKLOAD_BAR widget:**
```json
{
  "boardIds": ["uuid-fiber", "uuid-yarn"],
  "departmentIds": ["uuid-production"],
  "metric": "ESTIMATED_HOURS",
  "showWipLimit": true,
  "highlightOverloaded": true
}
```

### Varsayılan Manager Dashboard (seed data)

Her yeni tenant için "Operasyon Genel Bakış" dashboard'u otomatik oluşturulur:

| Widget | Tip | Boyut | Pozisyon |
|---|---|---|---|
| Açık Task Sayısı | TASK_COUNT | 3×2 | (0,0) |
| Geciken Task Sayısı | TASK_COUNT | 3×2 | (0,3) |
| BLOCKED Task Sayısı | TASK_COUNT | 3×2 | (0,6) |
| SLA Uyum | SLA_COMPLIANCE | 3×2 | (0,9) |
| Status Dağılımı | STATUS_DISTRIBUTION | 6×3 | (2,0) |
| Tamamlanma Trendi | COMPLETION_TREND | 6×3 | (2,6) |
| İş Yükü | WORKLOAD_BAR | 12×3 | (5,0) |
| Geciken Task'lar | OVERDUE_TASKS | 6×4 | (8,0) |
| Haftalık Lider Tablosu | LEADERBOARD | 6×4 | (8,6) |

---

## 6. Workload API (İş Yükü Dağılımı)

> İlham: Monday.com Workload View — kişi bazlı kapasite dağılımı, aşırı yük uyarısı.

### Endpoint

```
GET /api/flowboard/workload?boardIds={ids}&startDate={date}&endDate={date}&departmentId={id}
```

### Yanıt Yapısı

```json
{
  "period": {"start": "2026-03-17", "end": "2026-03-23"},
  "users": [
    {
      "userId": "...",
      "displayName": "Ahmet Yılmaz",
      "department": "Fiber Üretim",
      "wipLimit": 5,
      "currentWip": 3,
      "capacityStatus": "AVAILABLE",
      "tasks": [
        {
          "taskId": "...",
          "taskNumber": "TSK-0042",
          "title": "Recipe ata — SO-0042",
          "deadline": "2026-03-19",
          "estimatedHours": 2,
          "trackedHours": 0.5,
          "status": "IN_PROGRESS",
          "priority": "HIGH"
        }
      ],
      "weeklyStats": {
        "totalEstimatedHours": 18,
        "totalTrackedHours": 12,
        "completedTasks": 4,
        "pendingTasks": 3
      }
    }
  ],
  "departmentSummary": [
    {
      "departmentId": "...",
      "name": "Fiber Üretim",
      "totalCapacityHours": 200,
      "allocatedHours": 145,
      "utilizationPercent": 72.5,
      "overloadedUserCount": 1,
      "unassignedTaskCount": 2
    }
  ]
}
```

### CapacityStatus (Enum)

| Değer | Koşul | Görsel |
|---|---|---|
| `AVAILABLE` | currentWip < wipLimit × 0.6 | 🟢 Yeşil |
| `OPTIMAL` | wipLimit × 0.6 ≤ currentWip < wipLimit | 🟡 Sarı |
| `OVERLOADED` | currentWip ≥ wipLimit | 🔴 Kırmızı |

---

## Manager Dashboard — 5 Panel (Önceki Tanım, Geriye Uyumlu)

Bu paneller DashboardWidget olarak modellenmiştir:

| Panel | Widget Karşılığı |
|---|---|
| Sipariş Bazlı | `MODULE_SUMMARY` + `TASK_COUNT` (entityType = SALES_ORDER filtresi) |
| Personel Bazlı | `WORKLOAD_BAR` + `LEADERBOARD` |
| Modül Bazlı | `MODULE_SUMMARY` |
| Tıkanıklık | `BLOCKED_TASKS` + `OVERDUE_TASKS` |
| Kapasite | `WORKLOAD_BAR` + `SLA_COMPLIANCE` |

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `07-flowboard/board-task.md` | Board, Task, TaskLabel, BoardView (WORKLOAD görünümü) |
| `07-flowboard/task-details.md` | TaskTimeEntry → totalTrackedMinutes, TaskActivityLog → metrikler |
| `07-flowboard/escalation.md` | EscalationLog → dashboard'da gösterilebilir |
| `01-foundations/user-auth.md` | User.wipLimit → kapasite hesaplama |
| `01-foundations/organization-department.md` | Department → departman bazlı workload |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 2.0 | 2026-03-17 | DashboardConfig + DashboardWidget eklendi (12 widget tipi). Workload API tanımlandı. UserPerformanceSnapshot'a totalTrackedMinutes/avgTaskDurationMinutes/estimateAccuracyPercent/streakDays/weeklyRank/achievementBadges alanları eklendi. Gamification rozet sistemi tanımlandı (8 rozet). Varsayılan Manager Dashboard seed data eklendi. Manager Dashboard 5 panel → DashboardWidget eşlemesi tanımlandı. |
| 1.0 | 2026-03-17 | İlk versiyon |

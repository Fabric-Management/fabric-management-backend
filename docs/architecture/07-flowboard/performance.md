# Performans Takibi

> Modül: FlowBoard (07-flowboard) | Versiyon: 1.0 | Son güncelleme: 2026-03-17  
> Kanonik kaynak: UserPerformanceSnapshot burada tanımlanır.

---

## Metrikler

Tüm metrikler `TaskActivityLog` üzerinden hesaplanır.

| Metrik | Hesaplama |
|---|---|
| Tamamlanan task sayısı | status = DONE |
| Ortalama tamamlama süresi | DONE - IN_PROGRESS başlangıcı |
| Geciken task oranı | DONE tarihi > deadline |
| Self-assign oranı | assignedBy = SELF |
| Blocked task oranı | BLOCKED geçen / toplam |
| Yeniden açılan task | DONE → IN_PROGRESS geçişleri |

## UserPerformanceSnapshot

> Tablo: `flowboard.user_performance_snapshot` | `BaseEntity`'den miras alır.

| Alan | Tip | Açıklama |
|---|---|---|
| `userId` | UUID | FK → User |
| `periodStart` | Date | Dönem başlangıcı |
| `periodEnd` | Date | Dönem bitişi |
| `completedTaskCount` | Integer | Tamamlanan |
| `avgCompletionHours` | Decimal | Ortalama süre |
| `lateTaskCount` | Integer | Geciken |
| `lateTaskRatio` | Decimal | Gecikme oranı (%) |
| `selfAssignCount` | Integer | Self-assign |
| `blockedTaskCount` | Integer | BLOCKED |
| `reopenedTaskCount` | Integer | Yeniden açılan |

**Hesaplama:** Haftalık snapshot job — geceleri çalışır.

## Manager Dashboard — 5 Panel

Sipariş Bazlı, Personel Bazlı, Modül Bazlı, Tıkanıklık, Kapasite.

### Görünürlük

| Metrik | Manager | Personel |
|---|---|---|
| Tüm ekip metrikleri | ✓ | ✗ |
| Kendi metrikleri | ✓ | ✓ |

# Tekrarlayan Task'lar

> Modül: FlowBoard (07-flowboard) | Versiyon: 1.0 | Son güncelleme: 2026-03-17  
> Kanonik kaynak: RecurringTaskTemplate burada tanımlanır.

---

## RecurringTaskTemplate

> Tablo: `flowboard.recurring_task_template` | `BaseEntity`'den miras alır.

| Alan | Tip | Açıklama |
|---|---|---|
| `title` | String (500) | Task başlık şablonu |
| `taskType` | Enum | TaskType |
| `moduleType` | Enum | Modül |
| `frequency` | Enum | DAILY / WEEKLY / MONTHLY / CUSTOM |
| `customIntervalDays` | Integer | CUSTOM için — her N günde bir |
| `estimatedHours` | Decimal | Tahmini süre |
| `defaultAssigneeRole` | Enum | Varsayılan rol |
| `checklistTemplate` | JSONB | Alt görev şablonları |
| `isActive` | Boolean | Aktif mi |

### Zamanlama Kuralı

**"Bir önceki kapanınca yenisi açılsın"** — aynı anda iki tekrarlayan task olmaz.

| Görev | Sıklık | Tetikleyici |
|---|---|---|
| Supplier lisans kontrolü | Aylık | Bir önceki kapanınca |
| Stok sayımı | Haftalık | Bir önceki kapanınca |
| Günlük üretim raporu | Günlük | Bir önceki kapanınca |
| Makine bakım kontrolü | Her 30 günde | Bir önceki kapanınca |

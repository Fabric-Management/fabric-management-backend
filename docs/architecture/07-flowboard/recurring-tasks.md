# Tekrarlayan Task'lar

> Modül: FlowBoard (07-flowboard) | Versiyon: 2.0 | Son güncelleme: 2026-03-17  
> Kanonik kaynak: RecurringTaskTemplate burada tanımlanır.

---

## Genel Bakış

Tekrarlayan task'lar periyodik olarak yapılması gereken operasyonel görevleri otomatize eder. Bir önceki task kapandığında yeni bir task otomatik oluşturulur — aynı anda iki tekrarlayan task olmaz.

---

## RecurringTaskTemplate

> Tablo: `flowboard.recurring_task_template` | `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `title` | String (500) | Evet | Task başlık şablonu — `"Haftalık stok sayımı — {date}"` |
| `description` | String (TEXT) | Hayır | Açıklama şablonu |
| `taskType` | Enum | Evet | TaskType |
| `moduleType` | Enum | Evet | Modül |
| `frequency` | RecurringFrequency (Enum) | Evet | Sıklık tipi |
| `customIntervalDays` | Integer | Hayır | CUSTOM için — her N günde bir |
| `estimatedHours` | Decimal | Hayır | Tahmini süre |
| `defaultPriority` | Enum | Evet | LOW / MEDIUM / HIGH / CRITICAL — varsayılan MEDIUM |
| `defaultAssigneeRole` | Enum | Evet | Varsayılan atama rolü |
| `boardId` | UUID | Evet | FK → Board — hangi board'da oluşacak |
| `checklistTemplate` | JSONB | Hayır | Alt görev şablonları |
| `autoLabels` | JSONB | Hayır | Otomatik atanacak etiket ID'leri |
| `isActive` | Boolean | Evet | Aktif mi |
| `lastGeneratedTaskId` | UUID | Hayır | FK → Task — son oluşturulan task |
| `lastGeneratedAt` | Timestamp | Hayır | Son oluşturma zamanı |

### RecurringFrequency (Enum)

| Değer | Açıklama | Deadline Hesaplama |
|---|---|---|
| `DAILY` | Her gün | createdAt + 1 gün |
| `WEEKLY` | Her hafta | createdAt + 7 gün |
| `BIWEEKLY` | İki haftada bir | createdAt + 14 gün |
| `MONTHLY` | Her ay | createdAt + 30 gün |
| `CUSTOM` | Her N günde | createdAt + customIntervalDays gün |

### Zamanlama Kuralı

**"Bir önceki kapanınca yenisi açılsın"** — aynı anda iki tekrarlayan task olmaz.

```
FlowBoardRecurringJob — her saat çalışır:

1. Aktif RecurringTaskTemplate'leri tara
2. Her template için:
   a. lastGeneratedTaskId null ise → yeni task oluştur (ilk çalıştırma)
   b. lastGeneratedTaskId'nin task'ı DONE veya CANCELLED ise → yeni task oluştur
   c. lastGeneratedTaskId'nin task'ı hala açık ise → atla (beklemeye devam)
3. Yeni task oluşturulurken:
   a. title → şablondan + tarih interpolasyonu
   b. deadline → frequency'ye göre hesapla
   c. checklist → checklistTemplate'den oluştur
   d. labels → autoLabels'den ata
   e. atama → SYSTEM yoluyla müsait personele
   f. lastGeneratedTaskId + lastGeneratedAt güncelle
```

### Seed Data — Varsayılan Tekrarlayan Görevler

| Görev | taskType | Sıklık | estimatedHours | Açıklama |
|---|---|---|---|---|
| Haftalık stok sayımı | STOCK_COUNT | WEEKLY | 4.0 | Hammadde ve ürün stok sayımı |
| Günlük üretim raporu | GENERAL | DAILY | 0.5 | Günlük üretim durumu özeti |
| Makine bakım kontrolü | MAINTENANCE | CUSTOM (30 gün) | 2.0 | Üretim makinesi bakım kontrolü |
| Supplier lisans kontrolü | GENERAL | MONTHLY | 1.0 | Tedarikçi lisans ve sertifika kontrolü |
| Haftalık QC kalibrasyon | QUALITY | WEEKLY | 1.0 | QC cihazları kalibrasyon kontrolü |
| Aylık maliyet raporu | COSTING | MONTHLY | 3.0 | Aylık maliyet analizi ve sapma raporu |

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `07-flowboard/board-task.md` | Task, Board, TaskLabel entity'leri |
| `07-flowboard/smart-task-generator.md` | TaskTemplate — benzer yapı, farklı tetikleyici |
| `07-flowboard/task-details.md` | TaskChecklist — checklistTemplate'den oluşturulur |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 2.0 | 2026-03-17 | RecurringTaskTemplate'e description/defaultPriority/boardId/autoLabels/lastGeneratedTaskId/lastGeneratedAt alanları eklendi. BIWEEKLY frequency eklendi. Scheduler job akışı detaylandırıldı. 2 yeni seed data (QC kalibrasyon, maliyet raporu) eklendi. |
| 1.0 | 2026-03-17 | İlk versiyon |

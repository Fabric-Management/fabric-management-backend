# SmartTaskGenerator & TaskTemplate

> Modül: FlowBoard (07-flowboard)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: SmartTaskGenerator akışı ve TaskTemplate burada tanımlanır.

---

## Genel Bakış

SmartTaskGenerator sistem olaylarını dinler, TaskTemplate'i bulur, Task oluşturur, atar ve bildirim gönderir. AI bağımlı değil — deterministik RuleEngine.

---

## TaskTemplate

> Tablo: `flowboard.task_template`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `eventType` | String | Evet | Tetikleyici event — `SalesOrderConfirmed` |
| `titleTemplate` | String (500) | Evet | `"Recipe ata — {salesOrder.orderNumber}"` |
| `taskType` | Enum | Evet | TaskType |
| `moduleType` | Enum | Hayır | null ise tüm modüller |
| `defaultAssigneeRole` | Enum | Evet | DEPARTMENT_ADMIN / MANAGER / ANY |
| `estimatedHours` | Decimal | Hayır | Varsayılan süre |
| `checklistTemplate` | JSONB | Hayır | Alt görev şablonları |
| `isActive` | Boolean | Evet | Aktif mi |

---

## Event → Task Zinciri

> **Tam event listesi:** `11-cross-cutting/event-catalog.md`

### Akış

```
1. Event yayınlanır
2. TaskTemplate bulunur (eventType eşleşmesi)
3. Stok kontrolü (SalesOrderConfirmed için — bkz. Stok Kontrol Motoru)
4. Task oluşturulur (title, deadline, checklist şablondan)
5. PriorityScore hesaplanır
6. BACKLOG'a düşer
7. Atama denenir (SYSTEM) → müsait personel varsa atanır, yoksa BACKLOG'da bekler
```

---

## Stok Kontrol Motoru

`SalesOrderConfirmed` tetiklendiğinde çalışır:

| Senaryo | Durum | Oluşan Task |
|---|---|---|
| Stok yeterli | requestedQty ≤ stok | WAREHOUSE — "Stoktan ayır" |
| Stok yetersiz | stok = 0 | PRODUCTION veya PROCUREMENT |
| Kısmi stok | 0 < stok < requestedQty | İkisi birden (miktarlar bölünür) |

PRODUCTION mu PROCUREMENT mu kararı kullanıcıya bırakılır — sistem her ikisini de task olarak önerir.

---

## Otomatik Bağımlılıklar

SmartTaskGenerator task oluştururken bağımlılıkları da kurar:

```
PLANNING task (recipe ata)
    ↓ FINISH_TO_START
PRODUCTION task (batch başlat)
    ↓ FINISH_TO_START
QUALITY task (QC yap)
    ↓ FINISH_TO_START
WAREHOUSE task (lokasyona yerleştir)
    ↓ FINISH_TO_START
SHIPMENT task (sevkiyat hazırla)
```

> **Detay:** `07-flowboard/task-details.md` — TaskDependency

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `07-flowboard/board-task.md` | Task entity |
| `07-flowboard/task-details.md` | TaskDependency — otomatik bağımlılıklar |
| `05-iwm/stock-transaction-ledger.md` | Stok kontrol motoru — StockLedger sorgusu |
| `11-cross-cutting/event-catalog.md` | Event → TaskType eşlemesi |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon |

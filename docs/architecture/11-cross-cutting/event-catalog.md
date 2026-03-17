# Event Kataloğu

> Modül: Cross-Cutting (11-cross-cutting)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Tüm domain event'lerinin TEK listesi burasıdır.

---

## Genel Bakış

Bu döküman sistemdeki tüm event'leri, yayınlayan modülü, dinleyen modülleri ve önem derecesini listeler. Yeni event eklendiğinde **sadece bu döküman** güncellenir — modül dökümanları buraya referans verir.

---

## Event Listesi

### Üretim Zinciri Event'leri

| Event | Yayınlayan | Dinleyen | Önem | Açıklama |
|---|---|---|---|---|
| `SalesOrderConfirmed` | SalesOrder | FlowBoard, NotificationHub, RuleEngine | NORMAL | Sipariş onaylandı — WorkOrder taslağı oluşur |
| `SalesOrderInWarehouse` | SalesOrder | FlowBoard, NotificationHub | NORMAL | Tüm kalemler depoda — sevkiyat hazırlanabilir |
| `SalesOrderDeadlineApproaching` | SalesOrder (Scheduler) | FlowBoard, NotificationHub | HIGH | Sipariş deadline'ına 48 saat kala |
| `WorkOrderPendingApproval` | WorkOrder | FlowBoard, NotificationHub | HIGH | WorkOrder onay bekliyor |
| `WorkOrderApproved` | WorkOrder | FlowBoard, NotificationHub | NORMAL | WorkOrder onaylandı |
| `WorkOrderDeadlineSet` | WorkOrder | FlowBoard | NORMAL | Deadline atandı — PriorityScore güncellenir |
| `BatchQcPending` | Batch | FlowBoard, NotificationHub | NORMAL | Batch QC bekliyor |
| `BatchQcFailed` | Batch | FlowBoard, NotificationHub | CRITICAL | QC başarısız — yeniden işlem |
| `GoodsReceiptConfirmed` | GoodsReceipt | IWM, FlowBoard, NotificationHub | NORMAL | Teslim alındı — stok girişi tetiklenir |

### Satış Zinciri Event'leri

| Event | Yayınlayan | Dinleyen | Önem | Açıklama |
|---|---|---|---|---|
| `QuoteExpired` | Quote (Scheduler) | NotificationHub | NORMAL | Teklif süresi doldu |
| `QuoteAccepted` | Quote | NotificationHub | NORMAL | Müşteri teklifi onayladı |
| `QuoteExpiredLinkClicked` | QuoteApprovalToken | NotificationHub | HIGH | Müşteri süresi dolmuş linke tıkladı |
| `SampleDelivered` | SampleDelivery | NotificationHub | NORMAL | Numune teslim edildi |
| `DuplicateQuoteDetected` | Offline Sync | NotificationHub | HIGH | Çakışan teklif tespit edildi |
| `RecipeAssignmentNeeded` | RuleEngine | FlowBoard, NotificationHub | HIGH | Manuel recipe seçimi gerekiyor |

### Tedarik Zinciri Event'leri

| Event | Yayınlayan | Dinleyen | Önem | Açıklama |
|---|---|---|---|---|
| `RfqSent` | SupplierRFQ | NotificationHub | NORMAL | RFQ tedarikçilere gönderildi |
| `SupplierQuoteReceived` | SupplierQuote | NotificationHub | NORMAL | Tedarikçi teklif verdi |
| `RfqDeadlineApproaching` | SupplierRFQ (Scheduler) | NotificationHub | HIGH | RFQ deadline yaklaşıyor |
| `RfqNoResponse` | SupplierRFQ (Scheduler) | NotificationHub | HIGH | Tedarikçi yanıt vermedi |
| `PoConfirmed` | PurchaseOrder | NotificationHub | NORMAL | PO tedarikçi tarafından onaylandı |
| `PoPartiallyReceived` | PurchaseOrder | NotificationHub | NORMAL | Kısmi teslim alındı |
| `PoDeliveryLate` | PurchaseOrder (Scheduler) | NotificationHub, FlowBoard | HIGH | PO teslimatı gecikti |

### IWM Event'leri

| Event | Yayınlayan | Dinleyen | Önem | Açıklama |
|---|---|---|---|---|
| `MinStockAlert` | MinStockRule | FlowBoard, NotificationHub | HIGH | Stok minimum eşiğin altında |
| `StockCountVariance` | StockCount | NotificationHub | HIGH | Sayım farkı tespit edildi |
| `StockCountPlanned` | StockCount | NotificationHub | NORMAL | Stok sayımı planlandı |
| `RmaReceived` | RMA | NotificationHub | NORMAL | İade depoya geldi |
| `RmaPendingApproval` | RMA | NotificationHub | HIGH | İade onay bekliyor |
| `ReturnRateExceeded` | ReturnRateRule | FlowBoard, NotificationHub | CRITICAL | Tedarikçi iade oranı eşiği aştı |
| `TransferCompleted` | StockTransfer | NotificationHub | NORMAL | Depolar arası transfer tamamlandı |
| `ProductStoredEvent` | IWM | SalesOrder | NORMAL | Ürün depoya yerleştirildi — SOL → IN_WAREHOUSE |
| `ShipmentDispatchedEvent` | IWM | SalesOrder | NORMAL | Sevkiyat çıkışı — SOL → SHIPPED |

### Maliyet Event'leri

| Event | Yayınlayan | Dinleyen | Önem | Açıklama |
|---|---|---|---|---|
| `CostVarianceDetected` | CostCalculation | FlowBoard, NotificationHub | HIGH | Maliyet sapması tespit edildi |
| `PriceListExpiring` | PriceList (Scheduler) | NotificationHub | NORMAL | Fiyat listesi süresi dolmak üzere |
| `ExchangeRateSignificantChange` | ExchangeRate (Scheduler) | NotificationHub | HIGH | Döviz kurunda önemli değişim |

### Onay Sistemi Event'leri

| Event | Yayınlayan | Dinleyen | Önem | Açıklama |
|---|---|---|---|---|
| `ApprovalPending` | ApprovalRequest | FlowBoard, NotificationHub | HIGH | Onay talebi oluştu |
| `ApprovalApproved` | ApprovalRequest | NotificationHub | NORMAL | Onay verildi |
| `ApprovalRejected` | ApprovalRequest | NotificationHub | HIGH | Onay reddedildi |
| `UserPromotionReady` | UserPromotion | FlowBoard, NotificationHub | NORMAL | Kullanıcı yükseltmeye hazır |
| `SupplierLicenseExpiringSoon` | PartnerCertification (Scheduler) | FlowBoard, NotificationHub | HIGH | Tedarikçi lisansı süresi dolmak üzere |

### FlowBoard Event'leri

| Event | Yayınlayan | Dinleyen | Önem | Açıklama |
|---|---|---|---|---|
| `TaskAssigned` | FlowBoard | NotificationHub | NORMAL | Task birine atandı |
| `TaskBlocked` | FlowBoard | NotificationHub | CRITICAL | Task engellendi |
| `TaskDeadlineNear` | FlowBoard (Scheduler) | NotificationHub | HIGH | Task deadline'ına 24 saat kala |
| `TaskDeadlinePassed` | FlowBoard (Scheduler) | NotificationHub | CRITICAL | Task deadline'ı geçti |
| `TaskUnassignedTooLong` | FlowBoard (Scheduler) | NotificationHub | HIGH | Task 2 saat atanmamış |
| `EscalationTriggered` | EscalationLog | NotificationHub | CRITICAL | Eskalasyon tetiklendi |

### Zamanlayıcı Event'leri

| Event | Yayınlayan | Dinleyen | Önem | Açıklama |
|---|---|---|---|---|
| `DailyTaskSummary` | Scheduler | NotificationHub | NORMAL | Günlük görev özeti |
| `WeeklyPerformanceSummary` | Scheduler | NotificationHub | NORMAL | Haftalık performans özeti |

---

## Toplam: 38 Event

| Kategori | Sayı |
|---|---|
| Üretim Zinciri | 9 |
| Satış Zinciri | 6 |
| Tedarik Zinciri | 7 |
| IWM | 9 |
| Maliyet | 3 |
| Onay Sistemi | 5 |
| FlowBoard | 6 |
| Zamanlayıcı | 2 |
| **Toplam** | **47** |

---

## Event → NotificationHub Önem Eşlemesi

| Önem | Davranış | Örnek |
|---|---|---|
| CRITICAL | Anında, tüm kanallar, kullanıcı tercihi görmezden gelinir | BatchQcFailed, TaskBlocked |
| HIGH | 5 dakika gruplama, tüm aktif kanallar | ApprovalPending, MinStockAlert |
| NORMAL | 5 dakika gruplama, kullanıcı tercihine göre kanal | TaskAssigned, QuoteAccepted |

---

## Event → FlowBoard TaskType Eşlemesi

| Event | TaskType |
|---|---|
| `SalesOrderConfirmed` | PLANNING |
| `WorkOrderPendingApproval` | APPROVAL |
| `WorkOrderApproved` | PRODUCTION |
| `BatchQcPending` | QUALITY |
| `BatchQcFailed` | QUALITY |
| `GoodsReceiptConfirmed` | WAREHOUSE |
| `SalesOrderInWarehouse` | SHIPMENT |
| `RecipeAssignmentNeeded` | RECIPE_ASSIGNMENT |
| `MinStockAlert` | PROCUREMENT |
| `PoDeliveryLate` | PROCUREMENT |
| `CostVarianceDetected` | COSTING |
| `ReturnRateExceeded` | RETURN |
| `ApprovalPending` | APPROVAL |
| `UserPromotionReady` | APPROVAL |
| `SupplierLicenseExpiringSoon` | GENERAL |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — 47 event, 8 kategori, Procurement/IWM/Costing event'leri eklendi |

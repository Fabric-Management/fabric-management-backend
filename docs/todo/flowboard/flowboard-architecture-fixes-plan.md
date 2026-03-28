# FlowBoard Architecture Fixes & Automation Enhancements

Bu doküman, FlowBoard modülündeki (özellikle görev otomasyonu, atama akışları ve şablon tabanlı task üretimindeki) teknik borçları gidermek ve tam özellikli (Faz 8.x) hedeflere ulaşmak için hazırlanmış Spring-tabanlı uygulama planıdır.

---

## Hedefler
- `AutomationEngine` kapsamındaki yarım kalan / bağlanmamış otomasyon tetiklerini (trigger) canlandırmak.
- Olay tabanlı (Event-Driven) asenkron mimaride oluşan veri/beklenti kopukluklarını gidermek (Assign, Create).
- `SmartTaskGenerator`'daki (Şablon) idempotency hatalarını ve eksik özelliklerini (etiket atama) tamamlamak.
- Dağıtık bir yapıda olan otomasyon aklı ile şablon aklı arasındaki görev ve sınırları netleştirmek.

---

## 🏗️ Faz 1: Event & Listener Altyapısının Onarımı

### 1.1. `TaskCreatedEvent` Akışının Düzeltilmesi
**Problem:** Javadoc'ta AutomationEngine'i tetikleyeceği yazıyor ancak `TaskEventListener.onTaskCreated` içinde yalnızca WebSocket yayını yapılıyor.
* **Görevler:**
  - [ ] `TaskEventListener.onTaskCreated` içerisine `automationEngine.evaluate(task, AutomationTriggerType.TASK_CREATED, ...)` çağrısını ekle.
  - [ ] `AutomationTriggerType` içerisine (eğer yoksa) `TASK_CREATED` tipini ekle ve `AutomationEngine.java` içindeki koşul eşleştirmelerini (triggerConfig) bu yeni tipe göre genişlet.

### 1.2. `TaskAssignedEvent` Akışının Tamamlanması
**Problem:** Atama sonrası `TaskAssignedEvent` fırlatılıyor fakat dinleyen bir metot (listener) yok; ne WebSocket kullanıcılara bildiriliyor ne de otomasyon kuralları çalışıyor.
* **Görevler:**
  - [ ] `TaskEventListener` içerisine yeni bir metot ekle: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) @Async public void onTaskAssigned(TaskAssignedEvent event)`.
  - [ ] Yeni metot içinde `BoardWebSocketEventType.TASK_ASSIGNED` yayını yap (`wsPublisher.publish`).
  - [ ] Yeni metot içinde `automationEngine.evaluate(task, AutomationTriggerType.TASK_ASSIGNED, ...)` çağrısını yaparak atama otomasyonlarını aktifleştir.

---

## 🤖 Faz 2: Smart Task Generator (Şablon) ve Idempotency

### 2.1. Otomatik Etiket Atama (Auto-Labels - Faz 8.3)
**Problem:** `SmartTaskGeneratorListener` içinde `template.getAutoLabels()` sadece loglanıyor, etiket ataması yapılmıyor.
* **Görevler:**
  - [ ] `SmartTaskGeneratorListener` sınıfına `TaskLabelService` inject et.
  - [ ] Task oluşturulduktan sonra, eğer `template.getAutoLabels()` doluysa bu etiketleri virgülle ayırarak (split) her biri için `taskLabelService.assignLabelByName` metodunu çağır (Bu işlemi yaparken mevcut Transaction/Depth yapısını korumaya dikkat et).

### 2.2. Idempotency (Mükerrer Kayıt) Güvenliği
**Problem:** `entityId` değerinin `null` olma durumunda idempotency kontrolü (exists check) atlanıp duplicate task yaratılma riski alınıyor.
* **Görevler:**
  - [ ] İş emri, Sipariş v.b. domain event'lerinde `entityId`'nin boş gelmesini önleyecek validation/assert'leri ekle.
  - [ ] Ya da alternatif olarak `SmartTaskGenerator` içerisinde bir "Idempotency Key" mekanizması kur (Örnek: `Hash(templateId + eventType + customPayload)`).
  - [ ] `entityId` `null` geldiğinde asenkron logda Warning vermek yerine retry veya dead-letter sürecine girmesini sağlayacak Exception fırlatımını değerlendir.

---

## ⚙️ Faz 3: AutomationEngine İyileştirmeleri ve Sınırların Belirlenmesi

### 3.1. Desteklenmeyen (Faz 8.x) Tetikleyicilerin Yönetimi
**Problem:** `AutomationTriggerType` fazlaca tip içeriyor (`DEADLINE_APPROACHING`, `CHECKLIST_COMPLETED` vb.) fakat bunlar henüz bir yerden çağrılmıyor.
* **Görevler:**
  - [ ] Mevcut `AutomationTriggerType` enum'larını gözden geçir.
  - [ ] Henüz implemente edilemeyecek schedule-based trigger'lar (örn. 24 saat kalan tasklar için olan `DEADLINE_APPROACHING`) için ya Quartz/Spring `@Scheduled` job'ları oluşturarak `AutomationEngine`'e bağla, ya da koddan ve API dokümantasyonundan (`AGENTS.md` ve Swagger) çıkararak beklenti yönetimini doğru yap.

### 3.2. Sonsuz Döngü (Infinite Cascade) ve Rate Limiting
**Problem:** `context.isDepthExceeded()` 3 olarak sınırlandırılmış, ancak bazı senaryolarda aksiyonların başarılı olmasına rağmen limit ve fail toleransı net değil.
* **Görevler:**
  - [ ] Kural çalıştırma (Action Execution) bloğunda fail olan rule'lar için retryable (örn: `@Retryable` annotation'u ile) mekanizma eklenip eklenmeyeceğini değerlendir.
  - [ ] Kurallar ile Şablonlar çakıştığında (örn: şablon görev yaratır, otomasyon bu görevin durumunu değiştirir ve başka bir görev daha yaratır) log bazlı audit trail oluştur.`TaskHistory` (eğer varsa) veya entity üzerinden `createdByRule` / `createdByTemplate` flag'ini ekle.

---

## 🛡️ Faz 4: Hata Dayanıklılığı (Resilience) ve Mimari Güvenlik

### 4.1. Genişletilebilir Event-Template Konfigürasyonu
**Problem:** Generator listener'ında yalnızca `SalesOrder`, `WorkOrder` ve `GoodsReceipt` event'leri hardcoded olarak dinleniyor. Event sayısı arttıkça sınıftaki yoğunluk aşırı artacak.
* **Görevler:**
  - [x] Temel `DomainEventAdapter<T>` arayüzünü tanımla. (Arayüz tasarımı şimdiden yapılsın, implementasyonu desteklenen event sayısı artınca [>5-6] eklensin.)
    - Metotlar: `extractEntityId(T event)`, `extractTenantId(T event)`, `buildContext(T event)`.

### 4.2. @Async AFTER_COMMIT Hata Yönetimi
**Problem:** Listener fail olduğunda ana işlem (`AFTER_COMMIT` olduğundan) başarılı sayılıyor ancak arka planda görev/otomasyon tetiklenmiyor ve sessizce yok oluyor.
* **Görevler:**
  - [x] Basit ama etkili olması adına ilk etapta `AsyncUncaughtExceptionHandler` implementasyonunu kur. Bu sayede düşen event'leri log/alert olarak yakala ve bir hafta production'da izle.
  - [ ] İleri aşama: Metrikler toplandıktan sonra eğer event kaybı tolere edilemezse, idempotent bir Outbox Pattern kurulumuna geçiş yap. (Şimdilik over-engineering riskinden kaçınılıyor.)

---

## 📝 Öncelikli Uygulama Sırası (Action Items)

1. `TaskAssignedEvent` için listener oluşturmak ve WS + Automation bağlamasını yapmak (En kritik kopukluk).
2. `TaskCreatedEvent` için eksik Automation bağlamasını eklemek.
3. `SmartTaskGeneratorListener` içindeki `auto_labels` iş mantığını `TaskLabelService` ile birleştirmek.
4. `AutomationTriggerType` içindeki kullanılmayan trigger'lar için dokümantasyon güncellemesi veya scheduler job prototiplerini oluşturmak.

---

## ✅ Faz 7: Tüm Backend API'nin DTO İzolasyonu (Tamamlandı)

**Problem (Geçmiş):** FlowBoard ve diğer modüller dahil olmak üzere, API katmanındaki controller'ların doğrudan `@Entity` (ve database tablolarını tutan modelleri) dönmesi veri sızıntılarına (`tenantId`, `version` gb.), transaction hatalarına ve performans açıklarına yol açıyordu.
* **Uygulanan Görevler & Başarılar:**
  - [x] **Global ArchUnit Koruması (CC-2):** `controllersShouldNotDependOnEntities` kuralı başarıyla aktifleşti ve Greenlist uygulaması dahi kaldırılarak global geçerli kılındı (0 pelanggaran). Artık hiçbir sınıf API'den entity sızdıramaz.
  - [x] **Sales, Human & Notification:** Kalan son 8 ihlalli controller (örneğin `HrPolicyPackController`, `LocaleController`, `PayrollSelfServiceController`) temizlendi; hepsi Facade ve AppService katmanlarına alındı.
  - [x] **FlowBoard Temizliği Onayı:** FlowBoard modülünün zaten entity exposure (sızıntı) yapmadığı testle güvence altına alındı.
  - [x] **ApiResponse<T> Standardı:** Tüm backend controller'ları `%100` oranında nesnelerini `{ success: true, data: T }` formatında sarmalayacak hale getirildi. Artık backend tamamen güvenli ve stabil.

---

## 🌐 Faz 8: Frontend DTO Senkronizasyonu (Önceden Faz 5)

**Problem:** Backend API katmanı tamamen DTO-Only yapıya ve standart `ApiResponse<T>` modeline geçirildi (Faz 7 tamamlandı). Ancak Frontend (Next.js) types/interfaces bu yeni yapıdan habersiz. FlowBoard özelindeki DTO'lar frontend'de mevcut değil.
* **Görevler:**
  - [ ] **FlowBoard Tipleri:** Backend'de bulunan ~25 adet FlowBoard DTO & Enum'ını (`BoardResponse`, `TaskResponse`, `AutomationTriggerType` vb.) frontend `src/types/flowboard.ts` altında oluştur.
  - [ ] **API Wrapper (Unwrap):** `api-client.ts` üzerinde, Spring Boot'un standart olarak dönmeye başladığı `{ success: true, data: T }` formatını frontend component'leri için hook/client seviyesinde unwrap etme stratejisi (örn. interceptor ile) seç.
  - [ ] **TypeScript Hataları:** Terminalde `tsc --noEmit` üzerinden gelen field eşzamanlılık (örn: silinen `tenantId`, `version`) hatalarını düzelt.

---

## 🚀 Faz 9: Production Gözlemi ve Optimizasyon (Gelecek)

**Problem:** Outbox pattern, `@Retryable` mekanizmaları veya asenkron fail-over yapılarının eksikliği nedeniyle production anında veri kaybı riski yaşanabilir. 
* **Görevler:**
  - [ ] `AsyncUncaughtExceptionHandler` tarafından yakalanan log hatalarını production'da (örn: Grafana/Micrometer ile) 1 hafta boyunca izle.
  - [ ] Gözlem sonucunda FlowBoard event'leri (örn. `TaskCreatedEvent` yoğunluğu) asenkron düşüşleri kritik seviyedeyse, Event tabloları (Outbox) yaratıp `TaskScheduler` ile retry mekanizmasını devreye al.


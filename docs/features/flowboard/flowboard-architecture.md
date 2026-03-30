∏# FlowBoard Modülü (Scrumban Board & Görev Yönetimi)

Bu döküman, `com.fabricmanagement.flowboard` modülünün temel mimarisini, etki alanlarını (bounded contexts) ve sahip olduğu yetenekleri kapsamlı bir şekilde açıklar. FlowBoard, sadece basit bir To-Do listesi değil; uygulamanın diğer modelleriyle (satış, üretim, kalite vb.) tam entegre çalışabilen, otomasyon destekli ve efor/performans takibi yapabilen güçlü bir operasyonel araçtır.

## 1. Alt Modüller ve Sorumluluklar

Modül kendi içerisinde domain isolation çerçevesinde mantıksal alt parçalara bölünmüştür:

### A. Board (Pano Yönetimi)

Platformdaki operasyonel işlerin görsel olarak organize edildiği ana kapsayıcıdır.

- Her pano belirli bir modüle (Örn: `PRODUCTION`, `QUALITY`, `SALES`) veya `GLOBAL` (tüm işlerin birleştirildiği) kapsama aittir.
- **WIP Limits (Work In Progress):** Panolarda dar boğazları (bottleneck) engellemek adına varsayılan WIP limitleri (`wipLimitDefault`) belirlenebilir.
- **View Types:** Standart Kanban dışında liste veya timeline gibi çeşitli görünümleri destekleyecek şekilde tasarlanmıştır.

### B. Task (Görev Yönetimi)

Akışın operasyonel kalbini oluşturan, durum makinesiyle (State Machine) yönetilen birimdir.

- **Durum Makinesi:** `BACKLOG` → `TO_DO` → `IN_PROGRESS` → `IN_REVIEW` → `DONE` (İlişkili `BLOCKED` ve `CANCELLED` geçişleri ile).
- **Polimorfik Bağlantı:** `entityType` ve `entityId` kullanılarak sistemdeki başka bir varlıkla (örn: `SalesOrder`, `Batch`, `WorkOrder`) doğrudan bağlanarak ilişkilendirme sağlanır.
- **Zaman ve Efor Takibi:** Tahmini süre (`estimatedHours`) ve gerçek gerçekleşen çalışma süresi logları (`TaskTimeEntry` → `actualHours`) ile hassas efor takibi yapılır.
- **Dinamik Önceli Skorlaması (Priority Score):** Temel önem derecesine ek olarak bitiş tarihine (deadline) yakınlığa bağlı olarak `PriorityScoreCalculator` skoru otomatik artırır. Deadline aşımlarında (`isOverdue()`) skor Integer.MAX_VALUE değerine escalate edilir ve panoda kırmızı/acil vurgusu kazanır.
- Görevler birbirlerine bağımlı (`TaskDependency`) olabilir, alt maddeler (`TaskChecklist`) barındırabilir ve dosya/yorumlar (`TaskAttachment`, `TaskComment`) içerebilir. Periyodik görev ihtiyaçları `RecurringTaskTemplate` aracılığı ile sağlanır.

### C. Generator (Akıllı Görev Üretici)

Sistemdeki olayları dinleyerek otomatik operasyonel görevler üreten Event-Driven bir tasarımdır.

- `SmartTaskGeneratorListener` asenkron bir şekilde diğer modüllerden yayımlanan `DomainEvent`'leri (Örn: Sipariş onaylandı, reçete iptal edildi) yakalar.
- Veritabanındaki `TaskTemplate` şablonları taranarak ilgili "trigger" için uygun bir taslak (`titleTemplate`, `defaultPriority`, `checklistTemplate`, `autoLabels`) varsa anında görev oluşturarak (`sourceType = TEMPLATE`) panoya ekler.

### D. Automation (Olay-Tepki Otomasyonu)

If-then-that mantığı ile panolardaki işleri veya durum güncellemelerini insan müdahalesiz ilerleten mekanizmadır.

- `AutomationRule` objesi üzerinden `triggerType` (tetikleyici, Örn: IN_PROGRESS'e geçildiğinde), `conditionConfig` (JSON tabanlı "sadece" koşulları, Örn: Eğer task_type=QUALITY ise) ve `actionType` (Yeniden ata veya Etiketle vb.) aksiyonları konfigüre edilir.
- Kurallar yalnızca bir Board'a özgü çalışabileceği gibi `GLOBAL` modda tüm sisteme yayılan otomasyon kuralları olarak da çalışabilir.

### E. Dashboard (Performans & Yük Takibi)

Takımın ve bireylerin iş yükü durumunu analiz etmek için kullanılır.

- Dashboard, JSON konfigürasyonlarını esnek bir şekilde tutabilen gösterge bileşenleri olan `DashboardWidget` nesneleri barındırır.
- `UserPerformanceSnapshot` üzerinden dönemsel başarı/gecikme durumları (tamamlanan iş/saat oranı gibi metrikler) kalıcı kayıtlara dönüştürülür.

## 2. Mimari Dağılım & Tasarım Kararları

1. **Tam Bağımsız Event-Driven Yaklaşım:** FlowBoard modülü diğer modüllerin (satis, üretim) servislerine ya da Repository'lerine doğrudan bağımlı değildir (tight-coupling yaşanmaz). Polimorfik entityID referansları ve Event Publisher üzerinden dinleyici (`Listener` → `EventAdapter`) mekanizmalarıyla diğer modüllerle asenkron konuşur.
2. **Kapsayıcı Audit-Trail (Task Activity Log):** Kapsamlı bir geriye dönük izlenebilirlik için, task üzerinde yapılan tüm majör/minör güncellemeler (sorumlu değişikliği, başlık editi, durum geçişi) `TaskActivityLog` altında muhafaza edilir. Böylece panodaki her iş emrinin "Kimin tarafından ne zaman hangi duruma taşındığı" %100 oranında çıkarılabilir.
3. **Escalation (Uyarı / Tırmandırma Merkezi):** Opsiyonel olarak, bitiş süresi geçen (overdue) ya da bir kural seti olarak "Review aşamasında 2 günden fazla bekleyen" işler `EscalationJob` yardımıyla `EscalationLog` atılarak sorumlusunun yöneticisine veya pano izleyicisine (Manager) raporlanır.

## 3. Akış Senaryosu Örneği

Sistemde örnek bir "Sipariş Onayı → Görevlendirme → Tamamlanma" akışı şöyle resmedilebilir:

1. `Sales` modülü `SalesOrderConfirmedEvent` olayını publish eder.
2. `FlowBoard Generator`, bu eventi dinler ve `TaskTemplate` eşleşmesini görerek **"SO-802 nolu sipariş için hammadde alokasyonu yap"** isimli yeni bir task yaratır.
3. İlgili takım üyesi görevi `TO_DO` durumundan `IN_PROGRESS` durumuna alır (`startedAt` işaretlenir).
4. `Automation`, bu durum geçişini yakalayarak önceden tanımlı "İş Başladığında 'ACİL' etiketini kaldır" kuralını işletir.
5. Kullanıcı efor kaydını (`TaskTimeEntry`) girer ve görevi `DONE` yapar. Task'ın `completedAt` alanı doldurulur ve dashboard'daki `UserPerformanceSnapshot` istatistiğine dahil olur.

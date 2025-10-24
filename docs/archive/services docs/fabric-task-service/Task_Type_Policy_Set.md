Task Type Policy Set

Service: fabric-task-service
Version: 1.0
Scope: Logical design — governs all task behaviors (no implementation)

🎯 PURPOSE

Task Type Policy Set, her görev tipinin:

SLA süresi

öncelik seviyesi

default atama kuralı

deadline davranışı

escalation politikası

visibility & workflow integration

gibi parametrelerini merkezi olarak tanımlar.

Tüm task’lar bu politikaları runtime’da policy engine üzerinden çeker.
Bu sayede sistem hiçbir zaman hardcoded eşiklerle çalışmaz.

🧩 STRUCTURE OVERVIEW
Field	Type	Description
taskType	String	Görev tipinin sistemsel adı (örn. MANAGER_APPROVAL)
displayName	String	Kullanıcıya görünen ad
priority	Enum(CRITICAL, HIGH, NORMAL, LOW)	Varsayılan öncelik
slaDuration	Duration	Görev süresi (örn. PT4H = 4 saat)
gracePeriod	Duration	Deadline sonrası tolerans
autoAssignTo	Enum(ROLE, QUEUE, USER)	Varsayılan atama tipi
defaultAssignee	String	Varsayılan rol/queue/user id
escalationAfter	Duration	Escalation tetikleme süresi
escalateTo	String	Escalation hedefi (ör. manager.role.id)
autoNextTask	String	Tamamlanınca oluşturulacak task tipi
requiresVerification	Boolean	İkinci onay gerekli mi
visibleTo	Array<Role>	Kimler görebilir
active	Boolean	Aktif mi (policy devre dışı bırakılabilir)
createdBy / updatedBy	String	Audit alanları
🧠 POLICY CATEGORIES
1️⃣ Approval Tasks
taskType	displayName	priority	slaDuration	defaultAssignee	autoNextTask	notes
MANAGER_APPROVAL	Manager Approval	HIGH	PT2H	ROLE:MANAGER	PLANNING	Sipariş veya işlem onayı
PLANNING_APPROVAL	Planning Confirmation	NORMAL	PT3H	ROLE:PLANNER	WAREHOUSE_PREPARATION	Planlama onayı sonrası depo hazırlığı
FINANCE_APPROVAL	Finance Confirmation	HIGH	PT4H	ROLE:FINANCE_MANAGER	INVOICE_CREATION	Ödeme veya limit onayları
2️⃣ Production & Logistics Tasks
taskType	displayName	priority	slaDuration	defaultAssignee	autoNextTask	notes
PLANNING	Production Planning	HIGH	PT6H	QUEUE:PLANNING	WAREHOUSE_PREPARATION	Üretim planlama görevleri
WAREHOUSE_PREPARATION	Warehouse Preparation	NORMAL	PT8H	QUEUE:WAREHOUSE	SHIPPING	Depodan kumaş hazırlığı
SHIPPING	Shipment Arrangement	NORMAL	PT12H	ROLE:LOGISTICS	INVOICING	Nakliye ve sevkiyat planlama
INVOICING	Invoice Creation	NORMAL	PT4H	ROLE:ACCOUNTING	PAYMENT_TRACKING	Faturalama işlemleri
PAYMENT_TRACKING	Payment Tracking	NORMAL	P1D	ROLE:FINANCE	—	Tahsilat süreci takibi
3️⃣ System & Alert Tasks
taskType	displayName	priority	slaDuration	escalationAfter	escalateTo	autoAssignTo	notes
STOCK_ALERT	Stock Shortage Alert	CRITICAL	PT1H	PT30M	ROLE:MANAGER	QUEUE:PROCUREMENT	Stok yetersizliği
PRODUCTION_DELAY_ALERT	Production Delay Alert	HIGH	PT2H	PT1H	ROLE:PRODUCTION_HEAD	QUEUE:PLANNING	Üretim gecikmesi
DELIVERY_ALERT	Late Delivery Alert	HIGH	PT3H	PT1H	ROLE:LOGISTICS_MANAGER	ROLE:LOGISTICS	Sevkiyat gecikmesi uyarısı
4️⃣ User-Created Tasks (Manual)
taskType	displayName	priority	slaDuration	autoAssignTo	notes
USER_PERSONAL	Personal Task	NORMAL	PT8H	USER:self	Kullanıcı kendi task’ı
USER_SHARED	Shared Task	NORMAL	PT8H	USER:selected	Başka kullanıcıya atanmış görev
DAILY_PLANNED	Daily Running Task	NORMAL	P1D	USER:self	Günlük plan dahilinde görev
5️⃣ Support & Collaboration Tasks
taskType	displayName	priority	slaDuration	autoAssignTo	notes
NOTE_REVIEW	Note / Comment Review	LOW	PT4H	ROLE:MANAGER	Not ekleyen task onayı
TASK_FEEDBACK	Feedback Collection	LOW	PT2H	USER:creator	Geri bildirim döngüsü
🧬 ESCALATION LOGIC

1️⃣ Deadline yaklaşıyor → task.deadline.soon event
2️⃣ SLA ihlali → task.sla.breached event
3️⃣ Escalation tanımlıysa:

escalateTo alanındaki role/user’a task.escalated event gönderilir

Orijinal atanan kişi değişmeden, paralel uyarı yapılır (ownership korunur)
4️⃣ Escalation sonrası kapatılmazsa yöneticinin dashboard’unda Overdue Task Count artar

🧮 SLA Calculation Rules

SLA süresi = slaDuration

Deadline = createdAt + slaDuration + gracePeriod

gracePeriod (örn. PT30M) sonrası task hâlâ open ise breach

Sistem otomatik deadline reminder üretir (task.deadline.soon)

SLA breach event’leri sadece production ve critical task’larda zorunludur

🧭 ASSIGNMENT RULES

autoAssignTo alanı policy bazlıdır:

USER:self → görevi oluşturan kişi

USER:selected → oluştururken seçilen kişi

ROLE:XYZ → rol bazlı dağıtım

QUEUE:XYZ → departman kuyruğu

Eğer defaultAssignee boşsa Task Pool’a düşer ve manuel alınır.

Round-robin veya load-based atama stratejisi kullanılabilir (configurable).

🔔 NOTIFICATIONS & EVENTS

Task oluşturulduğunda → task.created

SLA breach → task.sla.breached

Escalation tetiklendiğinde → task.escalated

Deadline yaklaşırken → task.deadline.soon

Task kapatıldığında → task.closed

Bu event’ler Notification Service’e yönlendirilir (kanal: app / email / Slack / push)

🧠 EXTENSIBILITY

Yeni task tipi eklemek:
1️⃣ taskType ve displayName tanımla
2️⃣ SLA, priority, atama ve escalation parametrelerini doldur
3️⃣ autoNextTask ile zincire bağla
4️⃣ visibleTo ile erişim kontrolünü ayarla
5️⃣ active=true yap ve publish et

Bu işlem kod değişikliği olmadan sadece policy veri güncellemesiyle olur.

📊 PERFORMANCE METRIC LINK

Her task tipi için expectedDuration = slaDuration

Gerçek tamamlanma süresiyle kıyaslanır → efficiencyIndex = expected / actual

Departman veya user bazlı performans metrikleri Analytics Service’e event ile gönderilir.

🧩 EXAMPLE POLICY YAML (Environment / Config Source)
taskPolicies:
  - taskType: MANAGER_APPROVAL
    displayName: Manager Approval
    priority: HIGH
    slaDuration: PT2H
    gracePeriod: PT30M
    autoAssignTo: ROLE
    defaultAssignee: MANAGER
    escalationAfter: PT3H
    escalateTo: DIRECTOR
    autoNextTask: PLANNING
    requiresVerification: false
    visibleTo: [MANAGER, DIRECTOR]
    active: true

  - taskType: PLANNING
    displayName: Production Planning
    priority: HIGH
    slaDuration: PT6H
    gracePeriod: PT1H
    autoAssignTo: QUEUE
    defaultAssignee: PLANNING_QUEUE
    escalationAfter: PT8H
    escalateTo: PRODUCTION_HEAD
    autoNextTask: WAREHOUSE_PREPARATION
    requiresVerification: false
    visibleTo: [PLANNER, MANAGER]
    active: true

✅ SUMMARY

Zero hardcoded → tüm eşikler ve roller konfigürasyondan

Zero over-engineering → sade yapı, sadece gerekli alanlar

Production-ready → escalation, SLA, atama, görünürlük kuralları dahil

Config-driven → policy değişikliği deploy gerektirmez

Traceable → her task event policy referansı içerir (policyVersion)

Extensible → yeni task tipleri tek kayıtla tanımlanabilir

🔹 Sonuç:
Task Type Policy Set, fabric-task-service’in beynidir.
Bütün süreç akışlarını, öncelikleri, SLA’ları, atama kurallarını ve yöneticilere giden eskalasyonları tek yerden kontrol eder.
Değişiklikler kod değil politika seviyesinde yapılır; böylece sistem her zaman esnek, sade ve kontrol edilebilir kalır.
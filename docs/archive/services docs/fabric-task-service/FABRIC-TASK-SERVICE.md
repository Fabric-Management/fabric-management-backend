FABRIC-TASK-SERVICE

Domain Architecture & Specification (Logical Design, v1.0)

Status: DNA-COMPLIANT • Production-Ready Design (logic only)
Principles: ZERO HARDCODED • ZERO OVER ENGINEERING • CLEAN • SOLID • DRY • YAGNI • KISS • SRP • CQRS
Scope: No code, no DB DDL, no tables — only the architecture and behavior model.

🎯 Purpose

fabric-task-service, işletmenin tüm süreçleri için tek görev dili sağlar.
Kullanıcıların oluşturduğu kişisel görevler ile sistemin ürettiği operasyonel görevleri aynı orkestrasyon altında yürütür.
Süreç ilerleyişi “daily running” akışıyla yönetilir; olaylar (events) otomatik görev zincirleri doğurur.

🔲 Bounded Context

In-Scope

Görev yaşam döngüsü ve durum makinesi

Daily Running (kişisel günlük çalışma)

Kullanıcı tarafından oluşturulan görevler

Sistem tarafından tetiklenen görevler

Atama, öncelik, SLA ve dead-line mantıkları

Yorumlar, mention’lar, küçük notlar, kanıt/ek bilgi alanları

Görev zinciri (parent → child) ve orkestrasyon

Event yayınlama ve tüketme (choreography)

Görev temelli performans metrikleri için olay üretimi

Out-of-Scope

Bildirim gönderimi (Notification Service tüketir)

Rapor görselleştirme (Analytics/BI tüketir)

Sipariş, üretim, stok, fiyat gibi domain iş kuralları (ilgili domain’lerde)

🧩 Core Concepts
1) TaskAggregate (mantıksal)

Kimlik, başlık, açıklama, tip (user-created, system-generated)

Durum: created, assigned, in_progress, waiting, completed, verified, closed (+ cancelled, rejected opsiyonel)

Öncelik: critical, high, normal, low (policy’den gelir; hardcoded değil)

SLA ve deadline: görev tipine göre policy tablosundan

Atama: kullanıcı, rol veya kuyruk; auto-assignment kuralları

İlişki: parentId, children[], bağlı domain referansları (orderId, shipmentId vb.)

Günlük çalışma bağı: dailyRunningId (o günün çalışma oturumu)

Notlar ve mention’lar: küçük not akışı, @kullanıcı desteği

Denetim: audit, versiyon, soft delete

2) Daily Running (kişisel)

Her kullanıcı için gün başlangıcında açılan günlük çalışma konteyneri

Task Pool’dan o güne çekilen görevler (manual pick veya auto-plan)

Gün bitiminde tamamlanmayanlar geri Task Pool’a iade veya ertelenir (policy)

3) Task Pool & Queues

Departman/rol bazlı kuyruklar (örn. PlanningQueue, WarehouseQueue)

Sistem görevleri önce doğru kuyruğa, ardından kişiye düşer (auto-assignment)

🔁 Lifecycle (State Machine)

created → assigned → in_progress → waiting → completed → verified → closed

cancelled ve rejected yalnızca exception akışları için

“verified” yalnızca kalite kontrol/çift onay gereken akışlarda kullanılır

Otomasyon: bir durum tamamlandığında bir sonraki görev otomatik üretilip atanır

⚙️ Task Types

UserCreatedTask: kullanıcı kendine veya birine görev açar; günlük planına alır

SystemGeneratedTask: bir domain olayı tetikler (sipariş, stok, sevkiyat, üretim vb.)

Örnek sistem görevleri: ManagerApproval, Planning, WarehousePreparation, Shipping, Invoicing, PaymentTracking

🧬 Orchestration vs. Choreography

Choreography (event-first): Domain servisleri olay yayınlar, Task Service uygun görevi üretir

Micro-orchestration (task-flow): Tek bir sipariş akışında görev zincirini Task Service kurar ve ilerletir

Büyük SAGA orkestrasyonu yok; her domain özerk, Task Service yalnızca süreç görünürlüğü ve iş akışı sağlar

📡 Event Topology (mantıksal isimler)

Consumes:

order.created, order.updated, order.approval.requested

inventory.check.failed, inventory.reservation.created

production.planning.requested, shipment.requested

Publishes:

task.created, task.assigned, task.started, task.waiting, task.completed, task.verified, task.closed, task.cancelled

task.flow.progressed (parent→child transition)

task.sla.breached, task.deadline.soon (operasyonel uyarılar)

metrics.task.activity (analytics için düşük hacimli özet)

Event yükleri domain kimliği, correlation-id ve minimal alanlarla taşınır; kurallar ve eşikler konfigürasyondur.

🧭 Reference Flows (örnek senaryolar, kural seti)

Sipariş stokla karşılanabiliyorsa

order.created → task.managerApproval.created

managerApproval.completed → task.planning.created

planning.completed → task.warehousePreparation.created

warehousePreparation.completed → task.shipping.created

shipping.completed → task.invoicing.created

invoicing.completed → task.paymentTracking.created → closed

Stok yetersiz ise

order.created → task.managerAttention.created (stok eksikliği, karar gerekir)

manager kararına göre üretim planlama veya tedarik task’ları zincire eklenir

Atama

Default queue by task type (policy)

Manuel override mümkün; sistem auto-assignment’ı respekt eder fakat kayıt altına alır

⏱️ SLA & Deadline (policy-driven)

Görev tipine göre varsayılan süreler policy’den yüklenir

Yaklaşan deadline ve ihlaller event ile bildirilir

Critical görevler Notification Service üzerinden özel kanallara yönlendirilir

Tüm eşikler environment/config/DB kaynaklı; sabit değer yok

🗣️ Collaboration

Yorumlar ve mention’lar: her görevde küçük not akışı, @user etiketleme

Görev üzerinde ek alanlar: istenen sevkiyat günü, “maksimum iki parti” gibi iş kuralı notları

Ekler/kanıt: referans link veya belge ID (dosya depolama başka serviste)

🧷 Parent–Child & Linking

Parent görev sipariş akışı, child görevler işlem adımları

Her child tamamlandığında parent ilerleme yüzdesi güncellenir

İlgili domain entity referansları ilişkilendirilir (orderId, productionOrderId, shipmentId)

🧰 Assignment Intelligence (basit kurallar, over-engineering yok)

Round-robin veya kapasite-temelli basit atama stratejileri (policy)

Manuel atama her zaman mümkün

Gelecekte “performans-temelli tahsis” eklenebilir; bugün kapsam dışı (YAGNI)

📈 Manager Dashboard Signals (analytics beslemeleri)

Completion rate, on-time rate, average completion time

Reopen rate (kalite göstergesi), overdue count

Throughput trend (gün/hafta/ay), efficiency index

Kuyruk bazlı darboğaz tespiti, auto-escalation işaretleri

Hepsi metrics.events ile Analytics’e akar; görselleştirme dış serviste

🔒 Security, Multi-Tenancy, Compliance

Multi-tenant: tenant-id zorunlu; ancak bazı akışlar cross-team olabilir

Policy-guard: görev oluşturma/atama/kapama yetkileri rol-policy ile

Audit & trace: OpenTelemetry, correlation-id, idempotency-key

PII/iş içeriği: notlar ve mention’lar log politikasına uygun maskeleme

⚡ CQRS Surface (mantık)

Commands (write)

Task oluşturma (user/system)

Atama ve statü geçişleri

Daily running başlat/bitir; görevi günlük çalışmaya al/çıkar

Not/mention ekleme; deadline/priority ayarı

Queries (read)

Task Pool: bekleyenler, kuyruklara göre

My Daily Running: o günkü kişisel liste

Timeline: bir siparişin tüm görev akışı

Yönetici görünümü: departman/kuyruk bazlı özet, gecikmeler, trendler

Not: API yüzeyi sade; query’lerde cache ve sayfalama var; yazma uçları idempotent.

🧪 Validation & Invariants

Durum geçişleri state machine’e uygun olmalı

Parent kapatılmadan önce gerekli child’lar tamamlanmış olmalı (policy)

SLA ve deadline hesapları görev tipine göre policy’den

Atama kuralları: ya userId ya queue/role zorunlu

Idempotent komutlar: aynı request-id iki kez ilerleme yapmamalı

🧱 Caching & Performance (mantık)

Read-heavy uçlar Redis ile hızlandırılır (Task Pool, My Daily Running, Timeline özet)

Event-driven invalidation; TTL konfigurasyondan

Büyük listeler için “cursor-based paging”; ağır raporlar analytics’e delege

🚦 Observability

Her task olayı structured log ve trace span üretir

Ölçümler: task_lookup_latency, task_transition_latency, queue_wait_time, sla_breach_count

DLQ politikası: başarısız event işleme ayrı kuyruğa düşer, yeniden işleme denemeleri

🧭 Integration Points (mantıksal)

User/Company: kimlik, roller ve atama yetkileri

Notification: task uyarıları, eskalasyonlar

Order/Inventory/Production/Shipping/Invoicing/Payment: tetikleyici domain olayları

Analytics: metrik ve özet event tüketimi

Gateway: rate-limit, authn/authz, correlation

🧬 Configuration (no hardcode)

SLA eşikleri, deadline pencereleri, öncelik eşik değerleri

Auto-assignment modu ve eşitlik ağırlıkları

Daily running gün sonu davranışı (iade/ertele)

Escalation kuralları ve bildirim kanalları

Tamamı environment/config/DB; release’siz değiştirilebilir

🧩 Minimal Workflow Templates (policy ile)

“Sipariş hazır stoktan” zinciri

“Stok yetersiz, planlama+tüketime hazırlık” zinciri

“Sevk–fatura–tahsilat” zinciri

Şablonlar yalnızca başlangıç davranışını tanımlar; runtime’da zincir genişleyebilir

✅ Summary

Tek bir Task Service ile manuel ve otomatik görevler aynı dilde yönetilir.

Daily Running kullanıcı üretkenliğini düzenler; queues departman yükünü dengeler.

Event-driven yaklaşım süreçleri zincirler; SLA/priority politikaları operasyonu görünür ve yönetilebilir kılar.

Manager dashboard için gerekli tüm sinyaller üretilir; görselleştirme ayrı servise devredilir.

Tüm eşikler konfigürasyon kaynaklı; over-engineering yok, domain-first sade bir mimari.
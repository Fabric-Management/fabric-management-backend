# Production System Roadmap — Akıllı Tedarik Zinciri & Üretim Yönetimi

Bu doküman, FabricOS üretim modülünün geleceğe dönük mimari vizyonunu ve yapılacaklar listesini içerir. Henüz implemente edilmemiştir — referans ve planlama amaçlıdır.

**Mevcut durum:** Fiber master data, Material, FiberBatch CRUD + reservation + location management + sub-lotting + custom exception hierarchy + ProductionAccessService (role + department) tamamlandı.

---

## Faz 0 — Depo ve Kalite Kontrol (QC) Karantina Akışı (Öncelikli)

Mevcut durumda lotlar oluşturulduğunda doğrudan `AVAILABLE` (Kullanılabilir) statüsünde oluyor. Gerçek bir fabrikada mallar geldiğinde önce Karantina/Kalite Kontrol Bekliyor statüsünde olmalı, laboratuvar testleri onaylandıktan sonra üretime açılmalıdır.

### TODO

- [ ] **Yeni Statüler:** `FiberBatchStatus` enum'ına `QUARANTINE` (Karantina/Test Bekliyor) ve `REJECTED` (Reddedildi) statülerinin eklenmesi.
- [ ] **Giriş Akışı:** `FiberBatch` oluşturulduğunda (Receipt) varsayılan statünün `QUARANTINE` olması.
- [ ] **Kalite Kontrol Entegrasyonu:** `FiberTestResult` (Kalite modülü) onaylandığında (APPROVED/CONDITIONAL_ACCEPT), lot statüsünün otomatik olarak `AVAILABLE`'a çekilmesi.
- [ ] **Red Akışı:** Test reddedilirse (REJECTED), lot statüsünün `REJECTED` olması ve üretime (reservation/consume) kapatılması.
- [ ] **Frontend Güncellemeleri:** Karantina ve Red statüleri için UI rozetleri (badge) ve bu statülerdeki lotlar için aksiyonların (reserve, consume) kısıtlanması.

---

## Faz 1 — Durum Makinesi Altyapısı (State Machine)

Tüm execution entity'leri (FiberBatch, YarnBatch, FabricRoll, DyeOrder) için tutarlı durum geçiş mekanizması.

### TODO

- [ ] **Generic `StateMachine<S extends Enum<S>>` altyapı sınıfı**
  - Transition tanımları: `from → to + guard + action`
  - Guard: iş kuralı kontrolü (ör: QC onayı var mı?)
  - Action: geçiş sonrası tetiklenecek yan etki (event publish, notification)
  - `InvalidStatusTransitionException` zaten mevcut — StateMachine entegrasyonu yapılacak

- [ ] **FiberBatch StateMachine konfigürasyonu**
  - `AVAILABLE → RESERVED` (guard: stok yeterli mi?)
  - `RESERVED → IN_PROGRESS` (guard: üretim emri aktif mi?)
  - `IN_PROGRESS → DEPLETED` (guard: tüm miktar tüketildi mi?)
  - `RESERVED → AVAILABLE` (action: release — sipariş iptali)

- [ ] **YarnBatch StateMachine** (Fiber → Yarn üretim akışı)
  - `PLANNED → SPINNING → QUALITY_CHECK → AVAILABLE → DEPLETED`

- [ ] **FabricRoll StateMachine** (Yarn → Fabric dokuma/örme)
  - `PLANNED → WEAVING/KNITTING → QUALITY_CHECK → AVAILABLE → SHIPPED`

- [ ] **DyeOrder StateMachine** (Fabric → Dye & Finishing)
  - `CREATED → DYE_IN_PROGRESS → FINISHING → QUALITY_CHECK → COMPLETED`

---

## Faz 2 — Olay Güdümlü Mimari (Event-Driven Architecture)

Modüller arası iletişim doğrudan bağımlılık yerine domain event'ler ile sağlanacak.

### TODO

- [ ] **Domain Event altyapısı genişletilmesi**
  - `DomainEventPublisher` mevcut — Spring `ApplicationEventPublisher` kullanıyor
  - `@TransactionalEventListener` ile asenkron dinleyiciler eklenecek
  - İleride Kafka/RabbitMQ'ya geçiş için `EventBus` abstraction'ı

- [ ] **Sipariş → Üretim akışı event zinciri**
  - `OrderConfirmedEvent` → Envanter modülü stok kontrolü yapar
  - `StockReservedEvent` → Görev modülü iş emri oluşturur
  - `ProductionCompletedEvent` → QC modülü kalite kontrol kaydı açar
  - `QualityApprovedEvent` → Lojistik modülü sevkiyat planlar
  - `ShipmentDispatchedEvent` → Bildirim modülü müşteriye SMS/email atar

- [ ] **Event Sourcing hazırlığı** (opsiyonel, Faz 5+)
  - Her state transition bir event olarak saklanır
  - Audit log otomatik olarak event store'dan türetilir

---

## Faz 3 — Görev Yönetimi & Otomatik Atama (Task Module)

### TODO

- [ ] **`Task` domain entity'si**
  - `id, tenantId, title, description, type (PRODUCTION/QC/LOGISTICS/MAINTENANCE)`
  - `assigneeId, departmentId, priority, dueDate, status, parentTaskId`
  - `sourceEvent` (hangi domain event tetikledi)

- [ ] **Otomatik görev oluşturma**
  - Event listener'lar ilgili event'i yakalar → Task oluşturur
  - `OrderConfirmedEvent` → "Üretim planla" görevi, Production Planning departmanına
  - `ProductionCompletedEvent` → "Kalite kontrol yap" görevi, QC departmanına

- [ ] **Akıllı atama algoritması (WorkloadBalancer)**
  - Departmandaki personellerin mevcut iş yükünü hesapla
  - Ürün grubu uzmanlığını dikkate al (opsiyonel)
  - En uygun personele otomatik ata
  - Yönetici override: `reassignTask(taskId, newAssigneeId)` — her zaman izinli

- [ ] **WIP (Work In Progress) limitleri**
  - Departman/personel bazında WIP limiti tanımlanabilir
  - Limit aşılırsa yeni görev atanamaz → `WipLimitExceededException` (409)

---

## Faz 4 — Scrumban Pano & Görselleştirme (Frontend)

### TODO

- [ ] **Kanban Board bileşeni**
  - Sütunlar = StateMachine'deki durumlar
  - Kartlar = Task entity'leri
  - Drag-and-drop = durum geçişi tetikler (guard kontrolü backend'de)
  - WIP limiti aşıldığında sütun kırmızıya döner

- [ ] **Rol bazlı görünüm (RBAC filtresi)**
  - WORKER: sadece kendi görevleri
  - SUPERVISOR: departmanındaki tüm görevler
  - MANAGER: tüm departmanlar (cross-department)
  - `ProductionAccessService` + `useUserFromToken` entegrasyonu

- [ ] **SLA zamanlayıcı ve renk kodlaması**
  - Yeşil: zamanında, Sarı: %80 doldu, Kırmızı: SLA aşıldı
  - Kırmızı kartlar günlük stand-up'ta öncelikli ele alınır

- [ ] **Dashboard istatistikleri**
  - Departman bazlı throughput (iş/gün)
  - Ortalama cycle time (sipariş → teslimat)
  - WIP kullanım oranı

---

## Faz 5 — Akıllı Sipariş & Kural Motoru (Rule Engine)

### TODO

- [ ] **Stok yoksa alternatif çözüm motoru**
  - Muadil ürün önerisi (benzer fiber/yarn/fabric)
  - Parçalı teslimat planı
  - Gelecek tarihli üretimden rezervasyon

- [ ] **Sipariş Onay Akışı (Order Approval Workflow)**
  - Miktar veya tutar eşiğine göre çok seviyeli onay
  - Guard: `MANAGER` onayı gerekli mi?
  - Onay timeout: X saat sonra otomatik eskalasyon

---

## Faz 6 — Kararlılık & Ölçeklenebilirlik

### TODO

- [ ] **Idempotency key mekanizması**
  - POST endpoint'lerde `X-Idempotency-Key` header desteği
  - Aynı key ile tekrar gelen istek → önceki response döndürülür
  - Çift sipariş / çift fatura engellenir

- [ ] **Distributed locking (Pessimistic)**
  - Yoğun concurrent erişimde (ör: aynı batch'e iki kişi reserve)
  - Mevcut: `@Lock(LockModeType.OPTIMISTIC)` — FiberBatchRepository'de
  - İhtiyaç olursa: Redis-based distributed lock (`@DistributedLock`)

- [ ] **CQRS — Okuma/Yazma ayrışması**
  - Kanban board yoğun read trafik → ayrı read-optimized projection
  - Write: JPA + PostgreSQL (mevcut)
  - Read: Materialized view veya read-replica + cache
  - İlk adım: `@Cacheable` ile kritik sorgularda cache

- [ ] **Denetim İzi (Audit Trail) genişletilmesi**
  - Mevcut: `AuditLog` entity var (`audit.ts` types mevcut)
  - StateMachine her geçişte otomatik audit kaydı yazacak
  - Guard sonuçları (neden reddedildi/onaylandı) da loglanacak

---

## Bağımlılık Haritası

```
Faz 1 (StateMachine) ─────────────────┐
                                       ├──▶ Faz 3 (Task Module)
Faz 2 (Event-Driven) ─────────────────┘         │
                                                 ▼
                                          Faz 4 (Scrumban UI)
                                                 │
Faz 5 (Rule Engine) ◀─── bağımsız               ▼
                                          Faz 6 (CQRS, Idempotency)
```

Faz 1 ve 2 paralel başlayabilir. Faz 3, ikisine bağımlı. Faz 5 bağımsız geliştirilebilir.

---

## Mevcut Tamamlanan Altyapı (Bu Roadmap'in Önkoşulları)

| Bileşen | Durum |
|---|---|
| `ProductionDomainException` hiyerarşisi | ✅ Tamamlandı |
| `FiberBatchStatus` enum (AVAILABLE → DEPLETED) | ✅ Tamamlandı |
| `InsufficientStockException` / `InvalidStatusTransitionException` | ✅ Tamamlandı |
| `RecipeInUseException` — aktif üretimde reçete koruma | ✅ Tamamlandı |
| `GlobalExceptionHandler` — tüm production + subscription handler'lar | ✅ Tamamlandı |
| `ProductionAccessService` — role + department authorization | ✅ Tamamlandı |
| `AuthenticatedUserContext` — JWT'den department claim'leri | ✅ Tamamlandı |
| `ApiError` record — immutable error contract | ✅ Tamamlandı |
| Frontend `types/production.ts` — full DTO contract | ✅ Tamamlandı |
| Frontend `lib/api-error.ts` — typed error utility | ✅ Tamamlandı |
| Backend logging strategy (INFO/WARN/ERROR) | ✅ Tamamlandı |
| `FiberBatchRepository.existsByTenantIdAndFiberIdAndStatusIn` | ✅ Tamamlandı |
| `FiberService` — deactivate + update composition guard | ✅ Tamamlandı |
| Seed: R&D / Product Development + Production Planning departments | ✅ Tamamlandı |

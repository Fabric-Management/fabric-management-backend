# FabriCodeOS Backend Backlog

> Son güncelleme: 2026-03-11

## Tamamlanan Fazlar

### ✅ Faz 1: TradingPartner Foundation

- [x] `TradingPartner` entity
- [x] `TradingPartnerRegistry` (golden record)
- [x] Deduplication logic (tax_id + country)
- [x] Event publisher + listener

### ✅ Faz 2: Migration & Dual-Read

- [x] `legacy_company_id` column
- [x] `TradingPartnerResolver`
- [x] Dual-read queries
- [x] Legacy fallback flag → `false`

### ✅ Faz 3: Company Decomposition

- [x] `Tenant` entity (common_tenant)
- [x] `Organization` entity (common_organization)
- [x] Company table rename
- [x] JWT claims update (organization_id + backward compat)
- [x] Onboarding flow (CreateTenantStep + CreateOrganizationStep)
- [x] CompanyFacade elimination
- [x] Event listeners (TenantEventListener, OrganizationEventListener)
- [x] Integration tests (JWT round-trip, TradingPartner deduplication)

---

## Bekleyen Özellikler

### ⏳ Faz 4: TradingPartnerContact (Priority: Low)

**Durum:** Deferred - Build when needed

**Amaç:** Trading partner'lara yapılandırılmış iletişim ve adres bilgisi ekleme

**Gerekçe:**

- Core problem (Company overloading) çözüldü
- JSONB alanları (`contactInfo`, `relationshipMeta`) geçici çözüm sağlıyor
- CompanyContact/CompanyAddress pattern hazır template olarak mevcut

**Gerekli Bileşenler:**

- [ ] `TradingPartnerContact` entity (junction table)
- [ ] `TradingPartnerAddress` entity (junction table)
- [ ] `TradingPartnerContactAssignmentService`
- [ ] `TradingPartnerAddressAssignmentService`
- [ ] Controller endpoints
- [ ] Flyway migration (V047)

**Tetikleyici:**

- İş biriminden "partner'a birden fazla contact eklemek istiyoruz" talebi
- Order/Invoice'da partner contact seçimi gereksinimi
- ERP entegrasyonunda yapılandırılmış contact verisi ihtiyacı

---

### 🔮 Gelecek İyileştirmeler

#### Audit System

- [ ] `AuditService` implementation
- [ ] `audit_log` table
- [ ] Event listener'larda gerçek audit kaydı

#### Notification System

- [x] `NotificationService` için event listener entegrasyonu
- [ ] Email/SMS template system
- [x] In-app notifications
- [x] **Task C.2** — Notify relevant MANAGERs when `CONDITIONAL_ACCEPT` → `QUARANTINE` transition occurs (FiberTestResult → BatchQcEventListener).

#### Cross-Tenant Features

- [ ] Partner portal (linked tenant erişimi)
- [ ] Shared document visibility
- [ ] Order status cross-tenant sync

---

## Teknik Borç

### Notification Modülü (N4/N8/N9 sonrası)

| ID   | Açıklama                                                                 | Tetikleyici                                      |
| ---- | ------------------------------------------------------------------------ | ------------------------------------------------ |
| TB-1 | **NotificationChannel + NotificationDeliveryChannel birleştirme** — Mevcut `NotificationChannel` (WHATSAPP/EMAIL/SMS) ile yeni `NotificationDeliveryChannel` (IN_APP, EMAIL, BOTH) aynı domain'de; ileride karışıklık yaratır. Uzun vadede birleştirilmeli veya rename edilmeli. | Yeni kanal eklenirken veya refactor sırasında    |
| TB-2 | **Polling → SSE geçişi** — Şu an 10 sn'de bir `getUnreadCount()` polling. 100 kullanıcı ≈ 600 istek/dakika. Server-Sent Events (SSE) ile gerçek zamanlı bildirim güncellemesi daha verimli. | Kullanıcı sayısı arttığında, performans sorunu   |
| TB-3 | **Broadcast notification için recipientId=null optimizasyonu** — `sendToTenantRoles()` N kullanıcıya N bildirim kaydı yazıyor. İleride `recipientId=null` + tenant-scoped sorgu ile tek kayıt + sorgu zamanında expand daha verimli olabilir. | Çok sayıda rol/recipient olduğunda               |

### Temizlenecekler (Sonraki Sprint)

| Dosya                                | Durum       | Aksiyon                              |
| ------------------------------------ | ----------- | ------------------------------------ |
| `Company.java`                       | @Deprecated | Organization'a tam geçiş sonrası sil |
| `User.getCompanyId()`                | @Deprecated | Frontend geçişi sonrası sil          |
| `UserDto.getCompanyId()`             | @Deprecated | API v2'de kaldır                     |
| `JwtService.getCompanyIdFromToken()` | @Deprecated | Frontend geçişi sonrası sil          |

### Listener'ı Olmayan Events (Low Priority)

Bu event'ler tanımlı ama henüz listener'ları yok. İhtiyaç doğduğunda eklenecek:

```
ContactAssignedEvent (company/user)
AddressAssignedEvent (company/user)
MaterialCreatedEvent
FiberCreatedEvent
UserOnboardingCompletedEvent
ProfileUpdateRequest* events
PolicyEvaluatedEvent
SubscriptionExpiredEvent
SubscriptionActivatedEvent
UserRegisteredEvent
UserLoginEvent
UserLogoutEvent
```

---

## Referanslar

- [TRADING_PARTNER_FAZ1_5.md](./TRADING_PARTNER_FAZ1_5.md) - TradingPartner implementasyon rehberi
- [TENANT_ORGANIZATION_REFACTORING.md](./TENANT_ORGANIZATION_REFACTORING.md) - Faz 3 migration detayları

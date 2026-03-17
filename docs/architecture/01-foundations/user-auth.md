# User & Kimlik Doğrulama

> Modül: Temel Yapılar (01-foundations)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: User, AuthUser, Role, Contact, UserType burada tanımlanır.

---

## Genel Bakış

User entity platformdaki kullanıcıyı temsil eder. Tenant'a ve Organization'a bağlıdır. Sistemde **username alanı yoktur** — giriş Contact entity'si (email, telefon) üzerinden yapılır. Departman erişimi `UserDepartment` junction ile sağlanır.

---

## 1. User

> Tablo: `common_user.common_user`  
> Sınıf: `com.fabricmanagement.common.platform.user.domain.User`  
> `BaseEntity`'den miras alır.

| Alan (DB) | Java Alanı | Tip | Zorunlu | Açıklama |
|---|---|---|---|---|
| `first_name` | `firstName` | String (100) | Evet | Ad |
| `last_name` | `lastName` | String (100) | Evet | Soyad |
| `organization_id` | `organizationId` | UUID | Evet | FK → Organization |
| `user_type` | `userType` | UserType (Enum) | Evet | INTERNAL / EXTERNAL |
| `role_id` | `role` | Role (ManyToOne) | Hayır | FK → Role |
| `last_active_at` | `lastActiveAt` | Instant | Hayır | Son aktivite zamanı |
| `onboarding_completed_at` | `onboardingCompletedAt` | Instant | Hayır | Onboarding tamamlanma zamanı |

**İleride eklenecek alanlar (FlowBoard + Approval sistemi için):**

| Alan | Tip | Açıklama | Bağlantı |
|---|---|---|---|
| `wip_limit` | Integer | Kişi başı WIP limiti — varsayılan 5 | `07-flowboard/board-task.md` |
| `trust_level` | Enum | PROBATION / STANDARD / TRUSTED | `09-approval/trust-level-policy.md` |

### UserType Enum

| Değer | Açıklama |
|---|---|
| `INTERNAL` | Tenant'ın kendi personeli — HR kaydı olabilir |
| `EXTERNAL` | Tedarikçi, müşteri vb. dış kullanıcı — HR kaydı yok |

### Helper Metodlar

| Metod | Açıklama |
|---|---|
| `getDisplayName()` | `firstName + " " + lastName` |
| `getAnyVerifiedContact()` | Doğrulanmış herhangi bir contact (giriş için) |
| `getDefaultContact()` | Varsayılan bildirim contact'ı |
| `getPrimaryAddress()` | Birincil adres |
| `hasCompletedOnboarding()` | Onboarding tamamlandı mı |
| `completeOnboarding()` | Onboarding tamamlandı olarak işaretle |
| `updateLastActive()` | Son aktivite zamanını güncelle |

### İlişkiler

| İlişki | Tip | Açıklama |
|---|---|---|
| `userDepartments` | OneToMany → UserDepartment | User ↔ Department N:M |
| `userContacts` | OneToMany → UserContact | User ↔ Contact N:M |
| `userAddresses` | OneToMany → UserAddress | User ↔ Address N:M |

### Multi-Tenancy & UID

- `tenantId` BaseEntity'den gelir — tüm sorgular tenant kapsamında.
- UID formatı: `{TENANT_UID}-USER-{SEQUENCE}` — örn. `ACME-001-USER-00042`

---

## 2. AuthUser

> Tablo: `common_auth.common_auth_user`

Kullanıcının giriş bilgileri. User entity'sinden ayrı tutulur — güvenlik katmanı.

| Alan | Tip | Açıklama |
|---|---|---|
| `userId` | UUID | FK → User |
| `passwordHash` | String | Şifrelenmiş parola |
| `mfaEnabled` | Boolean | İki faktörlü doğrulama aktif mi |
| `mfaSecret` | String | MFA gizli anahtarı |
| `lastLoginAt` | Instant | Son giriş zamanı |
| `failedAttempts` | Integer | Başarısız giriş denemesi sayısı |
| `lockedUntil` | Instant | Hesap kilitleme süresi |

---

## 3. Role

> Tablo: `common_auth.common_role`

Kullanıcı rolleri. Yetkilendirme `Role + Department` kombinasyonuyla belirlenir.

| Değer | Açıklama |
|---|---|
| `ADMIN` | Tüm modüllere tam erişim |
| `MANAGER` | Departmana göre farklı yetkiler |
| `SUPERVISOR` | Departmana göre kısıtlı yetkiler |
| `WORKER` | Sadece READ + kendi task'ları |
| `VIEWER` | Sadece READ |

**Yetkilendirme:** `ProductionAccessService` — Role + Department kombinasyonuna göre:
- ADMIN → Tüm modüllere tam erişim
- MANAGER + "R&D/Product Development" → FIBER WRITE
- MANAGER + "Warehouse" → WAREHOUSE_LOCATION WRITE
- WORKER → Sadece READ

> **Detay:** `01-foundations/organization-department.md`

---

## 4. Contact & UserContact

> Contact tablosu: `common_contact.common_contact`  
> Junction tablosu: `common_user.common_user_contact`

Kullanıcının iletişim bilgileri ayrı entity'de yaşar. Username olmadığı için giriş doğrulanmış contact üzerinden yapılır.

---

## İlişki Özeti

```
User ──→ Organization (zorunlu)
  │
  ├──→ UserDepartment ──→ Department (N:M, isPrimary flag)
  ├──→ UserContact ──→ Contact (N:M, giriş için)
  ├──→ UserAddress ──→ Address (N:M)
  │
  └──→ AuthUser (1:1, giriş bilgileri)
       └──→ Role (ManyToOne)
```

---

## API

| Endpoint | Açıklama |
|---|---|
| User CRUD | `common_user` modülünde |
| Auth | `common_auth` modülünde |
| Department atamaları | `01-foundations/organization-department.md` |

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/organization-department.md` | Organization, Department, UserDepartment |
| `07-flowboard/board-task.md` | `wipLimit` alanı — FlowBoard WIP limiti |
| `09-approval/trust-level-policy.md` | `trustLevel` alanı — onay sistemi |
| `01-foundations/base-entity.md` | `createdBy`, `updatedBy` → User FK |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek kod yapısından türetildi |

# Migration Analysis & Refactoring Plan

Bu doküman migration tablolarını domain entity'lere ve ilişkilere göre gözden geçirme planını içerir.

---

## 📋 Analiz Adımları

### 1. Entity-Migration Mapping Tablosu

Her entity için migration dosyasını kontrol et ve uyumunu doğrula.

#### Common Platform Entities

| Entity | Migration File | Schema | Status | Notes |
|--------|---------------|--------|--------|-------|
| `BaseEntity` | V001 | - | ✅ Base | Abstract, tüm entity'ler extend eder |
| `Company` | V002 | common_company | ⚠️ Review | Tenant_id = company_id special case |
| `Department` | V002 | common_company | ⚠️ Review | |
| `DepartmentCategory` | V013 | common_company | ⚠️ Review | |
| `Subscription` | V002 | common_company | ⚠️ Review | |
| `SubscriptionQuota` | V002 | common_company | ⚠️ Review | |
| `OSDefinition` | V002 | common_company | ⚠️ Review | |
| `FeatureCatalog` | V002 | common_company | ⚠️ Review | |
| `User` | V003 | common_user | ⚠️ Review | Deprecated contact_value/type removed? |
| `Role` | V013 | common_user | ⚠️ Review | |
| `UserDepartment` | V013 | common_user | ⚠️ Review | Junction table |
| `AuthUser` | V006 | common_auth | ⚠️ Review | Deprecated contact_value/type removed? |
| `RefreshToken` | V006 | common_auth | ⚠️ Review | |
| `RegistrationToken` | V004 | common_auth | ⚠️ Review | |
| `VerificationCode` | V006 | common_auth | ⚠️ Review | |
| `Contact` | V016 | common_communication | ⚠️ Review | |
| `Address` | V016 | common_communication | ⚠️ Review | |
| `UserContact` | V016 | common_communication | ⚠️ Review | Junction table |
| `UserAddress` | V016 | common_communication | ⚠️ Review | Junction table |
| `CompanyContact` | V016 | common_communication | ⚠️ Review | Junction table |
| `CompanyAddress` | V016 | common_communication | ⚠️ Review | Junction table |
| `Policy` | V007 | common_policy | ⚠️ Review | |
| `AuditLog` | V005 | common_audit | ⚠️ Review | |

#### Production Entities

| Entity | Migration File | Schema | Status | Notes |
|--------|---------------|--------|--------|-------|
| `Material` | V009? | production | ⚠️ Review | |
| `Fiber` | V009 | production | ⚠️ Review | |
| `FiberComposition` | V009 | production | ⚠️ Review | |
| `FiberBatch` | V011 | production | ⚠️ Review | |
| `FiberCategory` | V008 | production | ⚠️ Review | Reference table |
| `FiberIsoCode` | V008 | production | ⚠️ Review | Reference table |
| `FiberCertification` | V008 | production | ⚠️ Review | Reference table |
| `FiberAttribute` | V008 | production | ⚠️ Review | Reference table |
| `FiberCertificationLink` | V009 | production | ⚠️ Review | Junction table |
| `FiberAttributeLink` | V009 | production | ⚠️ Review | Junction table |

---

### 2. Deprecated Fields Kontrolü

Migration'larda deprecated field'ların NOT NULL constraint'leri kaldırılmış mı kontrol et:

#### ✅ Tamamlanan
- [x] `common_user.contact_value` - V018'de NOT NULL kaldırıldı
- [x] `common_user.contact_type` - V018'de NOT NULL kaldırıldı
- [x] `common_user.department` - V018'de index kaldırıldı (deprecated)
- [x] `common_auth_user.contact_value` - V018'de NOT NULL kaldırıldı
- [x] `common_auth_user.contact_type` - V018'de NOT NULL kaldırıldı

#### ⚠️ Kontrol Edilecek
- [ ] `common_user.contact_value` kolonu hala tabloda mı? (Gelecekte DROP edilecek)
- [ ] `common_user.contact_type` kolonu hala tabloda mı? (Gelecekte DROP edilecek)
- [ ] `common_user.department` kolonu hala tabloda mı? (Gelecekte DROP edilecek)
- [ ] `common_auth_user.contact_value` kolonu hala tabloda mı? (Gelecekte DROP edilecek)
- [ ] `common_auth_user.contact_type` kolonu hala tabloda mı? (Gelecekte DROP edilecek)

---

### 3. Junction Table Kontrolleri

Junction table'ların doğru yapılandırıldığını kontrol et:

- [ ] `common_user_contact` - Composite PK (user_id, contact_id)
- [ ] `common_user_address` - Composite PK (user_id, address_id)
- [ ] `common_company_contact` - Composite PK (company_id, contact_id)
- [ ] `common_company_address` - Composite PK (company_id, address_id)
- [ ] `common_user_department` - Composite PK (user_id, department_id)
- [ ] `production.fiber_certification_link` - Composite PK?
- [ ] `production.fiber_attribute_link` - Composite PK?

---

### 4. Foreign Key Kontrolleri

Tüm foreign key'ler doğru mu?

- [ ] Company → User (company_id)
- [ ] User → Role (role_id)
- [ ] User → UserDepartment → Department
- [ ] User → UserContact → Contact
- [ ] User → UserAddress → Address
- [ ] Company → CompanyContact → Contact
- [ ] Company → CompanyAddress → Address
- [ ] AuthUser → Contact (contact_id)
- [ ] Department → DepartmentCategory
- [ ] Subscription → Company
- [ ] Fiber → Material
- [ ] FiberComposition → Fiber

---

### 5. Index Kontrolleri

Performans için gerekli index'ler var mı?

- [ ] Tenant-scoped queries için (tenant_id, entity_id) composite index'ler
- [ ] Foreign key index'leri
- [ ] Unique constraint index'leri
- [ ] Query pattern'lere göre özel index'ler

---

### 6. Seed Data Ayrımı

Seed veriler migration'dan ayrılmalı:

- [ ] V010 (Fiber seeds) → Seed script'e taşı
- [ ] V014 (Role/DepartmentCategory seeds) → Seed script'e taşı
- [ ] Migration'lardan seed INSERT'ler kaldırılmalı

---

## 🎯 Yapılacaklar

### Phase 1: Analiz (Şu an)
1. ✅ Seed verilerini yedekle
2. ⏳ Tüm migration dosyalarını oku
3. ⏳ Entity'lerle karşılaştır
4. ⏳ Eksik/hatalı migration'ları tespit et

### Phase 2: Düzenleme
1. ⏳ Deprecated field'ları migration'dan kaldır (NOT NULL kaldırılmış, kolonlar gelecekte DROP edilecek)
2. ⏳ Seed verileri migration'lardan ayır
3. ⏳ Junction table'ları kontrol et
4. ⏳ Foreign key'leri kontrol et
5. ⏳ Index'leri optimize et

### Phase 3: Yeniden Yapılandırma
1. ⏳ Migration'ları domain'lere göre grupla
2. ⏳ Temiz bir migration sırası oluştur
3. ⏳ Seed data mekanizması oluştur (Spring @Component)

---

**Last Updated:** 2025-11-02
**Status:** In Progress


# 🔐 Platform Admin User Setup Guide

## 📋 ÖZET

Platform seviyesinde uygulamanın ilk admin kullanıcısı (`PLATFORM_ADMIN`) nasıl oluşturulur?

**Amaç:** Platform yönetimi, tenant oluşturma (sales-led onboarding), sistem genelindeki ayarları yönetme.

---

## 🎯 PLATFORM ADMIN NE İÇİN GEREKLİ?

### **Yetkiler:**

1. ✅ **Tenant Oluşturma:** Sales-led onboarding için tenant'ları oluşturma
   - `POST /api/admin/onboarding/tenant`
2. ✅ **Platform Yönetimi:** Sistem genelindeki ayarları yönetme
3. ✅ **Multi-Tenant Erişim:** Tüm tenant'lara erişebilme (gelecekte)

### **Korunan Endpoint'ler:**

```
/api/admin/** → requires PLATFORM_ADMIN role
```

---

## 🏗️ YAPISAL DETAYLAR

### **Platform Admin Özellikleri:**

| Özellik       | Değer                        | Açıklama                               |
| ------------- | ---------------------------- | -------------------------------------- |
| **Tenant ID** | `SYSTEM_TENANT_ID`           | `00000000-0000-0000-0000-000000000000` |
| **Company**   | Platform Company (opsiyonel) | Platform kendi company'si olabilir     |
| **Role**      | `PLATFORM_ADMIN`             | System tenant'a özel rol               |
| **Scope**     | Platform-wide                | Tüm tenant'lara erişim                 |

### **Tenant Yapısı:**

```
Platform Level (SYSTEM_TENANT_ID)
├─ Platform Admin Users
│  └─ PLATFORM_ADMIN role
├─ System Roles (seeded via V014)
│  └─ ADMIN, DIRECTOR, MANAGER, etc.
└─ System Reference Data
   └─ Fiber categories, certifications, etc.

Tenant Level (actual tenant_id)
├─ Tenant Admin Users
│  └─ ADMIN role (tenant-specific)
├─ Tenant Users
│  └─ Various roles
└─ Tenant Data
   └─ Companies, subscriptions, etc.
```

---

## 🚀 İLK PLATFORM ADMIN OLUŞTURMA

### **Yöntem 1: Migration ile (Önerilen)**

**Avantajlar:**

- ✅ Version-controlled
- ✅ Production'a deploy edildiğinde otomatik oluşur
- ✅ Repeatable (birden fazla environment)
- ✅ Audit trail

**Migration Dosyası:** `V019__create_platform_admin.sql`

```sql
-- ============================================================================
-- V19: Create Initial Platform Admin User
-- ============================================================================
-- Creates first platform admin user for platform management
-- This user can create tenants via sales-led onboarding
-- ============================================================================

-- System Tenant ID
DO $$
DECLARE
    system_tenant_id UUID := '00000000-0000-0000-0000-000000000000'::UUID;
    platform_admin_email VARCHAR := 'akkaya64@hotmail.com';  -- ⚠️ Güncellendi
    platform_admin_password VARCHAR := '$2a$10$...';  -- ⚠️ CHANGE THIS (BCrypt hash)
    platform_admin_first_name VARCHAR := 'Platform';
    platform_admin_last_name VARCHAR := 'Admin';

    -- Generated IDs
    user_id UUID;
    contact_id UUID;
    role_id UUID;
    auth_user_id UUID;
BEGIN
    -- Check if platform admin already exists
    IF EXISTS (
        SELECT 1 FROM common_user.common_user u
        JOIN common_communication.common_user_contact uc ON u.id = uc.user_id
        JOIN common_communication.common_contact c ON uc.contact_id = c.id
        WHERE u.tenant_id = system_tenant_id
        AND c.contact_value = platform_admin_email
        AND c.is_verified = TRUE
    ) THEN
        RAISE NOTICE 'Platform admin already exists, skipping...';
        RETURN;
    END IF;

    -- 1. Create User
    user_id := gen_random_uuid();
    INSERT INTO common_user.common_user (
        id, tenant_id, uid,
        first_name, last_name, display_name,
        company_id,  -- ⚠️ NULL or create platform company
        created_at, updated_at, version
    ) VALUES (
        user_id,
        system_tenant_id,
        'SYS-USER-0001',  -- UID for platform admin
        platform_admin_first_name,
        platform_admin_last_name,
        platform_admin_first_name || ' ' || platform_admin_last_name,
        NULL,  -- No company for platform admin (or create platform company)
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    -- 2. Create Contact
    contact_id := gen_random_uuid();
    INSERT INTO common_communication.common_contact (
        id, tenant_id, uid,
        contact_value, contact_type,
        is_verified, is_primary, is_personal,
        created_at, updated_at, version
    ) VALUES (
        contact_id,
        system_tenant_id,
        'SYS-CONTACT-0001',
        platform_admin_email,
        'EMAIL',
        TRUE,  -- Pre-verified for platform admin
        TRUE,
        TRUE,  -- Personal contact
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    -- 3. Link User and Contact
    INSERT INTO common_communication.common_user_contact (
        user_id, contact_id, tenant_id,
        is_default, is_for_authentication,
        created_at, updated_at
    ) VALUES (
        user_id,
        contact_id,
        system_tenant_id,
        TRUE,  -- Default contact
        TRUE,  -- For authentication
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

    -- 4. Get or Create PLATFORM_ADMIN Role
    SELECT id INTO role_id
    FROM common_user.common_role
    WHERE tenant_id = system_tenant_id
    AND role_code = 'PLATFORM_ADMIN'
    LIMIT 1;

    IF role_id IS NULL THEN
        -- Create PLATFORM_ADMIN role if it doesn't exist
        role_id := gen_random_uuid();
        INSERT INTO common_user.common_role (
            id, tenant_id, uid,
            role_name, role_code, description,
            is_active, created_at, updated_at, version
        ) VALUES (
            role_id,
            system_tenant_id,
            'SYS-ROLE-0001',
            'Platform Administrator',
            'PLATFORM_ADMIN',
            'Full platform access - can create tenants, manage system settings',
            TRUE,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            0
        );
    END IF;

    -- 5. Assign Role to User
    UPDATE common_user.common_user
    SET role_id = role_id
    WHERE id = user_id;

    -- 6. Create AuthUser (for password authentication)
    auth_user_id := gen_random_uuid();
    INSERT INTO common_auth.common_auth_user (
        id, tenant_id, uid,
        contact_id,
        password_hash,
        is_verified, is_active,
        created_at, updated_at, version
    ) VALUES (
        auth_user_id,
        system_tenant_id,
        'SYS-AUTH-0001',
        contact_id,
        platform_admin_password,  -- ⚠️ Pre-hashed BCrypt password
        TRUE,  -- Pre-verified
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    RAISE NOTICE '✅ Platform admin user created: %', platform_admin_email;
END $$;

COMMENT ON TABLE common_user.common_user IS 'Includes platform admin users with SYSTEM_TENANT_ID';
COMMENT ON COLUMN common_user.common_user.tenant_id IS 'SYSTEM_TENANT_ID for platform admins, actual tenant_id for tenant users';
```

**⚠️ ÖNEMLİ:** Migration'ı çalıştırmadan önce:

1. `platform_admin_email` değerini değiştirin
2. `platform_admin_password` için BCrypt hash oluşturun (aşağıdaki script ile)

---

### **Yöntem 2: Manual SQL Script**

**Kullanım:** İlk kurulum için, migration'dan önce manuel çalıştırılabilir.

```sql
-- Manual Platform Admin Creation Script
-- Run this ONCE to create first platform admin

DO $$
DECLARE
    system_tenant_id UUID := '00000000-0000-0000-0000-000000000000'::UUID;
    admin_email VARCHAR := 'akkaya64@hotmail.com';
    admin_password_hash VARCHAR := '$2a$10$...';  -- Generate with BCrypt
    user_id UUID := gen_random_uuid();
    contact_id UUID := gen_random_uuid();
    role_id UUID;
    auth_user_id UUID := gen_random_uuid();
BEGIN
    -- Create User
    INSERT INTO common_user.common_user (
        id, tenant_id, uid, first_name, last_name, display_name,
        created_at, updated_at, version
    ) VALUES (
        user_id, system_tenant_id, 'SYS-USER-0001',
        'Platform', 'Admin', 'Platform Admin',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
    );

    -- Create Contact
    INSERT INTO common_communication.common_contact (
        id, tenant_id, uid, contact_value, contact_type,
        is_verified, is_primary, is_personal,
        created_at, updated_at, version
    ) VALUES (
        contact_id, system_tenant_id, 'SYS-CONTACT-0001',
        admin_email, 'EMAIL', TRUE, TRUE, TRUE,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
    );

    -- Link User-Contact
    INSERT INTO common_communication.common_user_contact (
        user_id, contact_id, tenant_id,
        is_default, is_for_authentication,
        created_at, updated_at
    ) VALUES (
        user_id, contact_id, system_tenant_id,
        TRUE, TRUE,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    -- Get or Create PLATFORM_ADMIN Role
    SELECT id INTO role_id
    FROM common_user.common_role
    WHERE tenant_id = system_tenant_id AND role_code = 'PLATFORM_ADMIN';

    IF role_id IS NULL THEN
        role_id := gen_random_uuid();
        INSERT INTO common_user.common_role (
            id, tenant_id, uid, role_name, role_code, description,
            is_active, created_at, updated_at, version
        ) VALUES (
            role_id, system_tenant_id, 'SYS-ROLE-0001',
            'Platform Administrator', 'PLATFORM_ADMIN',
            'Full platform access',
            TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
        );
    END IF;

    -- Assign Role
    UPDATE common_user.common_user SET role_id = role_id WHERE id = user_id;

    -- Create AuthUser
    INSERT INTO common_auth.common_auth_user (
        id, tenant_id, uid, contact_id, password_hash,
        is_verified, is_active, created_at, updated_at, version
    ) VALUES (
        auth_user_id, system_tenant_id, 'SYS-AUTH-0001',
        contact_id, admin_password_hash,
        TRUE, TRUE,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
    );

    RAISE NOTICE 'Platform admin created: %', admin_email;
END $$;
```

---

### **Yöntem 3: Java Bootstrap Service (Gelecekte)**

**Avantajlar:**

- ✅ Application startup'ta otomatik kontrol
- ✅ Yapılandırılabilir (environment variables)
- ✅ Daha fazla validation

**Implementation:** `PlatformAdminBootstrapService` (şu an yok, eklenebilir)

---

## 🔑 ŞİFRE OLUŞTURMA

### **BCrypt Hash Oluşturma:**

**Java ile:**

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hash = encoder.encode("your-password-here");
System.out.println(hash);
```

**Online Tool:**

- https://bcrypt-generator.com/ (⚠️ Production'da kullanmayın!)
- Round: 10 (default)

**Örnek Hash:**

```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
```

---

## ✅ KURULUM ADIMLARI

### **1. İlk Kurulum (Development)**

```bash
# 1. Migration'ı hazırla
# V019__create_platform_admin.sql dosyasını düzenle:
# - Email: akkaya64@hotmail.com (zaten ayarlanmış)
# - Password hash: BCrypt ile oluştur

# 2. Migration'ı çalıştır
make db-migrate

# 3. Test et
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "contactValue": "akkaya64@hotmail.com",
    "password": "your-password"
  }'
```

### **2. Production Kurulum**

```bash
# 1. Environment variables ayarla
export PLATFORM_ADMIN_EMAIL=akkaya64@hotmail.com
export PLATFORM_ADMIN_PASSWORD=your-secure-password

# 2. Migration'ı production DB'ye uygula
flyway migrate

# 3. Security config'i aktif et
# SecurityConfig.java'da @PreAuthorize annotation'ı uncomment et
```

---

## 🔐 GÜVENLİK AYARLARI

### **Production'da Aktifleştirme:**

```java
// TenantOnboardingController.java
@PostMapping("/tenant")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")  // ✅ Uncomment this
public ResponseEntity<ApiResponse<TenantOnboardingResponse>> createTenant(...)
```

### **JWT Token'da Role:**

Platform admin login olduğunda JWT token'ında şu claim olmalı:

```json
{
  "roles": ["PLATFORM_ADMIN"],
  "tenantId": "00000000-0000-0000-0000-000000000000"
}
```

---

## 🧪 TEST ETME

### **1. Login Test:**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "contactValue": "akkaya64@hotmail.com",
    "password": "your-password"
  }'
```

### **2. Tenant Oluşturma Test:**

```bash
curl -X POST http://localhost:8080/api/admin/onboarding/tenant \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "companyName": "Test Tenant",
    "taxId": "1234567890",
    "companyType": "WEAVER",
    "adminFirstName": "Test",
    "adminLastName": "User",
    "adminContact": "test@example.com",
    "selectedOS": ["FabricOS"],
    "trialDays": 90
  }'
```

---

## 📊 VERİTABANI YAPISI

### **Platform Admin User:**

```
common_user.common_user
├─ id: UUID
├─ tenant_id: 00000000-0000-0000-0000-000000000000
├─ uid: SYS-USER-0001
├─ first_name: "Platform"
├─ last_name: "Admin"
├─ company_id: NULL (or platform company UUID)
└─ role_id: → PLATFORM_ADMIN role

common_communication.common_user_contact
├─ user_id: → Platform Admin User
└─ contact_id: → Email Contact

common_auth.common_auth_user
├─ contact_id: → Email Contact
└─ password_hash: BCrypt hash

common_user.common_role
├─ role_code: PLATFORM_ADMIN
└─ tenant_id: SYSTEM_TENANT_ID
```

---

## ⚠️ ÖNEMLİ NOTLAR

1. **İlk Platform Admin:**

   - Migration ile oluşturulmalı
   - Production'da migration'dan önce manuel oluşturulabilir

2. **Güvenlik:**

   - Password hash'i production'da güvenli şekilde oluşturun
   - Email adresi production'da gerçek bir email olmalı
   - İlk login'de password değiştirme zorunlu tutulabilir

3. **Role Management:**

   - PLATFORM_ADMIN rolü system tenant'ta olmalı
   - Migration'da rol yoksa otomatik oluşturulur

4. **Company:**
   - Platform admin'in company_id'si NULL olabilir
   - Veya "Platform Company" adında özel bir company oluşturulabilir

---

## 🔄 SONRAKI ADIMLAR

1. ✅ Migration dosyası oluştur (`V019__create_platform_admin.sql`)
2. ✅ BCrypt hash oluştur (script ile)
3. ✅ Migration'ı çalıştır
4. ✅ Login test et
5. ✅ Tenant oluşturma test et
6. ✅ Production'da `@PreAuthorize` aktifleştir

---

**Son Güncelleme:** 2025-01-27

# Seed Data Backup - Reference Data

Bu dosya migration'lardan çıkarılan seed/seed verilerini içerir.
Bu veriler daha sonra migration'dan ayrı bir seed script olarak veya application startup'ta kullanılabilir.

---

## 📋 Seed Verileri

### 1. Role Seed Data (V014)
**Tablo:** `common_user.common_role`
**Tenant:** SYSTEM_TENANT_ID (00000000-0000-0000-0000-000000000000)

| UID | Role Name | Role Code | Description |
|-----|-----------|-----------|-------------|
| SYS-ROLE-001 | Administrator | ADMIN | Full system access |
| SYS-ROLE-002 | Director | DIRECTOR | Üst yönetim erişimi |
| SYS-ROLE-003 | Manager | MANAGER | Departman yönetimi |
| SYS-ROLE-004 | Supervisor | SUPERVISOR | Vardiya / ekip lideri |
| SYS-ROLE-005 | User | USER | Standart çalışan |
| SYS-ROLE-006 | Intern | INTERN | Stajyer erişimi |
| SYS-ROLE-007 | Viewer | VIEWER | Sadece okuma yetkisi |

**Kullanım:** System-wide reference roles. Tüm tenant'lar bu rolleri kullanabilir.

---

### 2. Department Category Seed Data (V014)
**Tablo:** `common_company.common_department_category`
**Tenant:** SYSTEM_TENANT_ID

| UID | Category Name | Description | Display Order |
|-----|---------------|-------------|---------------|
| SYS-CAT-001 | Production | Üretim ile doğrudan ilgili departmanlar | 1 |
| SYS-CAT-002 | Administrative | Ofis / yönetim / destek birimleri | 2 |
| SYS-CAT-003 | Utility | Yardımcı hizmet birimleri | 3 |
| SYS-CAT-004 | Logistics & Warehouse | Depo / sevkiyat / stok operasyonları | 4 |
| SYS-CAT-005 | Support & Audit | Eğitim / dokümantasyon / denetim birimleri | 5 |

**Kullanım:** Reference categories. Her tenant kendi department'larını oluşturur ama kategorileri sistemden alır.

---

### 3. Fiber Seed Data (V010)
**Tablo:** `production.prod_fiber` + `production.prod_material`
**Tenant:** SYSTEM_TENANT_ID
**Tip:** 100% Pure fibers (ISO kodlarından otomatik oluşturulur)

**Not:** V010 migration'ı `prod_fiber_iso_code` tablosundaki aktif ISO kodlarından otomatik olarak Material + Fiber entity'leri oluşturur.

**Pattern:**
- Material: `SYS-MAT-XXXXXX`
- Fiber: `SYS-FIB-XXXXXX`
- Status: `NEW`
- Grade: `STANDARD`
- Name: `{fiber_name} (100%)`

**Kullanım:** System-wide reference fibers. Tüm tenant'lar bu fiberleri kullanarak BLENDED fiberler oluşturabilir.

---

## 📝 Notlar

1. **Seed Verileri Migration'dan Ayrılmalı:**
   - Seed veriler migration'lardan ayrı bir mekanizma ile yüklenmelidir
   - Application startup'ta @PostConstruct ile veya ayrı bir seed servisi ile
   - Idempotent olmalı (tekrar çalıştırıldığında duplicate oluşturmamalı)

2. **Reference Data vs Tenant Data:**
   - SYSTEM_TENANT_ID (00000000-0000-0000-0000-000000000000) = Tüm sistem için reference data
   - Normal tenant_id = Tenant-specific data

3. **Migration Strategy:**
   - Migration'lar sadece SCHEMA (tablo, kolon, index, constraint) oluşturmalı
   - Seed veriler migration'dan SONRA ayrı bir mekanizma ile yüklenmeli

---

## 🔄 Gelecek Plan

1. Seed verilerini `@Component` ile Spring bean olarak oluştur
2. `@PostConstruct` ile application startup'ta idempotent olarak yükle
3. Migration'lardan seed INSERT statement'larını kaldır
4. Migration'lar sadece SCHEMA yönetimi için kullan

---

**Last Updated:** 2025-11-02
**Source Migrations:** V010, V014


# 🔐 Platform Admin - Tenant Yönetimi ve Erişim

## 📋 ÖZET

Platform admin (`PLATFORM_ADMIN` role) artık **tüm tenant'ların datalarına erişebilir ve yönetebilir**.

---

## 🎯 YETKİLER

### **Platform Admin Yapabilecekler:**

1. ✅ **Tüm Tenant'ları Listeleme**

   - Sistemdeki tüm tenant'ları görüntüleme

2. ✅ **Tenant Detaylarına Erişim**

   - Herhangi bir tenant'ın detaylarını görüntüleme
   - Tenant istatistikleri (user count, company count, subscription count)

3. ✅ **Tenant Verilerine Erişim**

   - Tenant'daki tüm kullanıcıları görüntüleme
   - Tenant'daki tüm şirketleri görüntüleme
   - Tenant'daki spesifik kullanıcı/şirket detaylarına erişim

4. ✅ **Cross-Tenant Operations**
   - Herhangi bir tenant context'inde işlem yapabilme
   - TenantContext otomatik switching

---

## 🔌 API ENDPOINT'LERİ

### **Base Path:** `/api/admin`

**Tüm endpoint'ler `PLATFORM_ADMIN` role gerektirir.**

### **1. Tüm Tenant'ları Listele**

```http
GET /api/admin/tenants
Authorization: Bearer {platform-admin-token}
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "tenant-uuid-1",
      "uid": "ACME-001",
      "companyName": "ACME Corporation",
      "taxId": "1234567890",
      "companyType": "WEAVER",
      "isActive": true,
      ...
    },
    {
      "id": "tenant-uuid-2",
      "uid": "XYZ-001",
      "companyName": "XYZ Tekstil",
      ...
    }
  ],
  "message": "Found 2 tenants"
}
```

---

### **2. Tenant Detayları**

```http
GET /api/admin/tenants/{tenantId}
Authorization: Bearer {platform-admin-token}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "tenant-uuid",
    "uid": "ACME-001",
    "companyName": "ACME Corporation",
    "taxId": "1234567890",
    "companyType": "WEAVER",
    "isActive": true,
    ...
  }
}
```

---

### **3. Tenant İstatistikleri**

```http
GET /api/admin/tenants/{tenantId}/statistics
Authorization: Bearer {platform-admin-token}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "tenantId": "tenant-uuid",
    "tenantUid": "ACME-001",
    "companyName": "ACME Corporation",
    "userCount": 15,
    "companyCount": 3,
    "subscriptionCount": 5,
    "isActive": true
  }
}
```

---

### **4. Tenant'daki Tüm Kullanıcılar**

```http
GET /api/admin/tenants/{tenantId}/users
Authorization: Bearer {platform-admin-token}
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "user-uuid-1",
      "uid": "ACME-001-USER-0001",
      "firstName": "Ahmet",
      "lastName": "Yılmaz",
      "displayName": "Ahmet Yılmaz",
      "role": {
        "roleCode": "ADMIN",
        "roleName": "Administrator"
      },
      ...
    },
    ...
  ],
  "message": "Found 15 users in tenant"
}
```

**⚠️ Not:** Bu endpoint otomatik olarak target tenant'ın context'ine geçer. Platform admin'in kendi tenant context'i korunur.

---

### **5. Tenant'daki Tüm Şirketler**

```http
GET /api/admin/tenants/{tenantId}/companies
Authorization: Bearer {platform-admin-token}
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "company-uuid-1",
      "uid": "ACME-001-COMP-0001",
      "companyName": "ACME Corporation",
      ...
    },
    {
      "id": "company-uuid-2",
      "uid": "ACME-001-COMP-0002",
      "companyName": "ACME Subsidiary",
      ...
    }
  ],
  "message": "Found 3 companies in tenant"
}
```

---

### **6. Tenant'dan Spesifik Kullanıcı**

```http
GET /api/admin/tenants/{tenantId}/users/{userId}
Authorization: Bearer {platform-admin-token}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "user-uuid",
    "uid": "ACME-001-USER-0001",
    "firstName": "Ahmet",
    "lastName": "Yılmaz",
    "displayName": "Ahmet Yılmaz",
    "role": {
      "roleCode": "ADMIN",
      "roleName": "Administrator"
    },
    ...
  }
}
```

---

### **7. Tenant'dan Spesifik Şirket**

```http
GET /api/admin/tenants/{tenantId}/companies/{companyId}
Authorization: Bearer {platform-admin-token}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "company-uuid",
    "uid": "ACME-001-COMP-0001",
    "companyName": "ACME Corporation",
    ...
  }
}
```

---

### **8. Tenant Context Switch (Bilgilendirme)**

```http
POST /api/admin/tenants/{tenantId}/switch
Authorization: Bearer {platform-admin-token}
```

**Response:**

```json
{
  "success": true,
  "data": "Tenant context switch ready. Use tenant-specific endpoints with tenantId path parameter."
}
```

**⚠️ Not:** Bu endpoint sadece bilgilendirme amaçlıdır. Gerçek tenant switching her endpoint çağrısında otomatik olarak yapılır (`TenantContext.executeInTenantContext()`).

---

## 🔧 NASIL ÇALIŞIR?

### **Tenant Context Switching:**

```java
// PlatformAdminService.java

// Platform admin herhangi bir tenant'ın verilerine erişirken:
return TenantContext.executeInTenantContext(tenantId, () -> {
    // Bu blok içindeki tüm işlemler target tenant'ın context'inde çalışır
    List<User> users = userRepository.findByTenantIdAndIsActiveTrue(tenantId);
    // userRepository otomatik olarak target tenant'ın verilerini döndürür
    return users.stream().map(UserDto::from).collect(Collectors.toList());
});
```

**Önemli:**

- ✅ Platform admin'in kendi tenant context'i korunur
- ✅ Her endpoint çağrısı için geçici olarak target tenant context'ine geçilir
- ✅ İşlem bitince otomatik olarak geri dönülür
- ✅ Thread-safe (ThreadLocal kullanılıyor)

---

## 🛡️ GÜVENLİK

### **Authorization:**

Tüm endpoint'ler `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` ile korunmuştur:

```java
@GetMapping("/tenants")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public ResponseEntity<ApiResponse<List<CompanyDto>>> getAllTenants()
```

### **JWT Token'da Role:**

Platform admin login olduğunda JWT token'ında şu claim olmalı:

```json
{
  "roles": ["PLATFORM_ADMIN"],
  "tenantId": "00000000-0000-0000-0000-000000000000" // SYSTEM_TENANT_ID
}
```

---

## 📊 KULLANIM ÖRNEKLERİ

### **1. Tüm Tenant'ları Listele:**

```bash
curl -X GET http://localhost:8080/api/admin/tenants \
  -H "Authorization: Bearer YOUR_PLATFORM_ADMIN_TOKEN"
```

### **2. Bir Tenant'ın İstatistiklerini Görüntüle:**

```bash
curl -X GET http://localhost:8080/api/admin/tenants/{tenantId}/statistics \
  -H "Authorization: Bearer YOUR_PLATFORM_ADMIN_TOKEN"
```

### **3. Tenant'daki Tüm Kullanıcıları Görüntüle:**

```bash
curl -X GET http://localhost:8080/api/admin/tenants/{tenantId}/users \
  -H "Authorization: Bearer YOUR_PLATFORM_ADMIN_TOKEN"
```

### **4. Tenant'daki Spesifik Kullanıcıyı Görüntüle:**

```bash
curl -X GET http://localhost:8080/api/admin/tenants/{tenantId}/users/{userId} \
  -H "Authorization: Bearer YOUR_PLATFORM_ADMIN_TOKEN"
```

---

## 🔄 TENANT CONTEXT MİMARİSİ

### **Normal User Flow:**

```
User Login → JWT Token → TenantContext.setCurrentTenantId(userTenantId)
  ↓
All operations use userTenantId
  ↓
Data isolation guaranteed
```

### **Platform Admin Flow:**

```
Platform Admin Login → JWT Token → TenantContext.setCurrentTenantId(SYSTEM_TENANT_ID)
  ↓
GET /api/admin/tenants/{targetTenantId}/users
  ↓
PlatformAdminService.getTenantUsers(targetTenantId)
  ↓
TenantContext.executeInTenantContext(targetTenantId, () -> {
  // Temporary switch to targetTenantId
  userRepository.findByTenantId(...)  // Uses targetTenantId
})
  ↓
Context automatically restored to SYSTEM_TENANT_ID
  ↓
Response returned
```

---

## ✅ YAPILACAKLAR (Gelecekte)

1. ⏳ **Tenant Management Operations:**

   - Tenant activate/deactivate
   - Tenant subscription management
   - Tenant data export

2. ⏳ **Cross-Tenant Reporting:**

   - System-wide statistics
   - Tenant comparison reports
   - Usage analytics

3. ⏳ **Bulk Operations:**
   - Bulk user creation across tenants
   - Bulk data migration

---

## 📝 ÖZET

**Platform Admin Artık:**

✅ Tüm tenant'ları listeleyebilir  
✅ Herhangi bir tenant'ın verilerine erişebilir  
✅ Tenant istatistiklerini görüntüleyebilir  
✅ Cross-tenant operations yapabilir  
✅ Tenant context'i otomatik olarak switch eder

**Endpoint'ler:**

- `GET /api/admin/tenants` - Tüm tenant'lar
- `GET /api/admin/tenants/{tenantId}` - Tenant detayları
- `GET /api/admin/tenants/{tenantId}/statistics` - İstatistikler
- `GET /api/admin/tenants/{tenantId}/users` - Tenant kullanıcıları
- `GET /api/admin/tenants/{tenantId}/companies` - Tenant şirketleri
- `GET /api/admin/tenants/{tenantId}/users/{userId}` - Spesifik kullanıcı
- `GET /api/admin/tenants/{tenantId}/companies/{companyId}` - Spesifik şirket

**Güvenlik:**

- Tüm endpoint'ler `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` ile korunmuş
- PLATFORM_ADMIN role required

---

**Son Güncelleme:** 2025-01-27

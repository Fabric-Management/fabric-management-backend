# 📮 Postman Collections

## 🚀 Quick Start

### **1. İlk Tenant Kaydı (Önce Bu)**

**Collection:** `Tenant-Onboarding-Local.postman_collection.json`

```
1. Register New Tenant → Company + TENANT_ADMIN oluşturur
2. Check Contact → Email durumu
3. Setup Password → Şifre oluştur (email verified olunca)
4. Login → JWT token al
5. Create New User → İkinci kullanıcı ekle
```

### **2. User Management**

**Collection:** `User-Management-Local.postman_collection.json`

Login gerektirir. User CRUD işlemleri.

### **3. Company Management**

**Collection:** `Company-Management-Local.postman_collection.json`

Login gerektirir. Company CRUD işlemleri.

### **4. Contact Management**

**Collection:** `Contact-Management-Local.postman_collection.json`

Login gerektirir. Contact CRUD işlemleri.

---

## 📋 Execution Order

```
Tenant-Onboarding (1. kez çalıştır)
  ↓
Diğer collection'lar (her test için)
```

---

## 🔑 Variables

Otomatik set edilir:

- `accessToken` - JWT token
- `companyId` - Created company ID
- `userId` - Created user ID

---

## 📌 Notes

- Base URL: `http://localhost:8080` (API Gateway)
- Rate limits: Register 2 req/min, Login 5 req/min
- Default email: `admin@acmetekstil.com`
- Default password: `AcmeAdmin@123`

**Last Updated:** 2025-10-11

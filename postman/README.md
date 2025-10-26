# 📮 POSTMAN COLLECTION - FABRIC MANAGEMENT API

**Last Updated:** 2025-10-25  
**Version:** 2.0  
**Collection:** Fabric-Management-Modular-Monolith.postman_collection.json

---

## 📋 OVERVIEW

Bu Postman koleksiyonu, Fabric Management Modular Monolith API'sinin tüm endpoint'lerini içerir.

**⭐ NEW:** Dual-path onboarding system (Sales-Led + Self-Service)

---

## 📂 COLLECTION STRUCTURE

```
Fabric Management API
├─ 🎯 Health & Info
│  ├─ Health Check
│  ├─ Application Info
│  └─ Actuator Health
├─ 🚀 Onboarding (NEW!)
│  ├─ Sales-Led Tenant Creation
│  ├─ Self-Service Signup
│  ├─ Setup Password (Sales-Led)
│  └─ Setup Password (Self-Service with Code)
├─ 🔐 Authentication
│  ├─ Register - Check Eligibility
│  ├─ Register - Verify & Complete
│  └─ Login
├─ 🏢 Company Management
│  ├─ Create Company
│  ├─ Get All Companies
│  ├─ Get Tenant Companies Only
│  ├─ Get Companies by Type
│  └─ Get Company Subscriptions
├─ 👤 User Management
│  ├─ Create User
│  └─ Get All Users
├─ 📜 Policy Management
│  ├─ Create Policy
│  └─ Get All Policies
├─ 📊 Audit Logs
│  └─ Get Audit Logs
└─ 🧪 Development Tools (Local Only)
   ├─ ⚠️ Reset All Data
   ├─ ⚠️ Clean Expired Tokens
   ├─ ⚠️ Clean Verification Codes
   └─ Database Stats
```

---

## 🔧 SETUP

### **1. Import Collection**

1. Postman'ı aç
2. File → Import
3. `Fabric-Management-Modular-Monolith.postman_collection.json` seç
4. Import

### **2. Environment Variables (Auto-Set)**

Collection içinde otomatik set edilen değişkenler:

| Variable             | Auto-Set? | Purpose                                 |
| -------------------- | --------- | --------------------------------------- |
| `base_url`           | Manual    | `http://localhost:8080`                 |
| `access_token`       | ✅ Auto   | JWT token (login sonrası)               |
| `refresh_token`      | ✅ Auto   | Refresh token                           |
| `company_id`         | ✅ Auto   | Last created company ID                 |
| `tenant_id`          | ✅ Auto   | Tenant ID (onboarding sonrası)          |
| `registration_token` | ✅ Auto   | Registration token (onboarding sonrası) |

---

## 🚀 QUICK START - EN HIZLI TEST

### **Sales-Led Flow (2 Request)**

```
1️⃣ Sales-Led Tenant Creation
   POST /api/admin/onboarding/tenant

   Body:
   {
     "companyName": "Akkayalar Tekstil Dokuma San. Tic. Ltd.Sti",
     "taxId": "4420543162",
     "companyType": "WEAVER",
     "adminFirstName": "Fatih",
     "adminLastName": "Akkaya",
     "adminContact": "fatih@akkayalartekstil.com.tr",
     "selectedOS": ["LoomOS", "AccountOS"],
     "trialDays": 90
   }

   ✅ Auto-saves: company_id, tenant_id, registration_token
   ✅ Check Response Tab for setupUrl

2️⃣ Setup Password
   POST /api/auth/setup-password

   Body:
   {
     "token": "{{registration_token}}",  // Auto-filled!
     "password": "SecurePass123!"
   }

   ✅ Auto-saves: access_token, refresh_token
   ✅ Auto-login! No additional login needed
   ✅ Check "needsOnboarding" flag in response

3️⃣ Test API (authenticated)
   GET /api/common/companies

   ✅ Authorization header auto-added
   ✅ Should return Akkayalar Tekstil
```

**Total Time:** 30 seconds  
**Requests:** 2  
**Auth Status:** ✅ Logged in automatically

---

## 🧪 DEVELOPMENT TOOLS

### **⚠️ Fresh Start (Clean Database)**

```bash
POST /api/dev/reset-all

→ Deletes:
  • Companies
  • Users
  • Auth users
  • Subscriptions
  • Policies
  • Tokens
  • Verification codes

→ Returns deletion summary
```

### **Cleanup Specific Data**

```bash
# Clean expired tokens only
POST /api/dev/clean-tokens

# Clean expired verification codes only
POST /api/dev/clean-codes
```

### **Monitor Database**

```bash
GET /api/dev/stats

→ Returns counts:
{
  "companies": 1,
  "users": 1,
  "authUsers": 1,
  "subscriptions": 3,
  "policies": 0,
  "registrationTokens": 1,
  "verificationCodes": 0
}
```

**⚠️ IMPORTANT:** Development tools only work in `local` profile!

---

## 🎯 TEST SCENARIOS

### **Scenario 1: Tam Akış (Sıfırdan)**

```
1. GET  /api/dev/stats              → Database boş mu kontrol et
2. POST /api/dev/reset-all          → Temizle (eğer dolu)
3. POST /api/admin/onboarding/tenant → Tenant oluştur
4. POST /api/auth/setup-password    → Şifre oluştur (auto-login)
5. GET  /api/common/companies       → Company listele
6. POST /api/common/users           → Yeni user ekle
7. GET  /api/common/users           → User listele
8. GET  /api/dev/stats              → Final stats
```

### **Scenario 2: Self-Service Test**

```
1. POST /api/public/signup          → Public signup
2. (Check logs for verification code)
3. POST /api/auth/setup-password    → Code + password (auto-login)
4. GET  /api/common/companies       → Verify tenant created
```

### **Scenario 3: Login Error Testing**

```
# Test context-aware errors
POST /api/auth/login
{
  "contactValue": "unknown@akkayalartekstil.com.tr",
  "password": "anything"
}

→ Expected: "If you're a Akkayalar Tekstil employee, contact your IT team..."
```

---

## 📧 EMAIL VERIFICATION

Email sistemi çalışıyor! Credentials:

```env
MAIL_HOST=smtp.hostinger.com
MAIL_PORT=465
MAIL_USERNAME=info@storeandsale.shop
MAIL_PASSWORD=Akk789987@
MAIL_FROM_EMAIL=info@storeandsale.shop
MAIL_FROM_NAME=Fabricode
```

**Verification code:**

- Email gönderilir: `info@storeandsale.shop`
- Subject: "Verify your account - Fabricode"
- Code: 6-digit (email body'de)

---

## 🔑 TEST CREDENTIALS

### **Akkayalar Tekstil (Real Company)**

```
Email: fatih@akkayalartekstil.com.tr
Password: SecurePass123! (after setup)

Company Details:
- Name: Akkayalar Tekstil Dokuma San. Tic. Ltd.Sti
- Tax ID: 4420543162
- Type: WEAVER
- Location: Kahramanmaraş, Turkey
```

---

## 💡 PRO TIPS

### **1. Auto-Login Flow**

```
Onboarding → Password Setup → Auto-Login
NO additional login request needed!
```

### **2. Environment Variables**

```javascript
// Postman automatically sets:
pm.environment.set("access_token", response.data.accessToken);
pm.environment.set("company_id", response.data.companyId);
pm.environment.set("registration_token", response.data.registrationToken);

// Use in requests:
{
  {
    access_token;
  }
}
{
  {
    company_id;
  }
}
{
  {
    registration_token;
  }
}
```

### **3. Quick Reset**

```
POST /api/dev/reset-all

→ Fresh database
→ Start testing again immediately
```

### **4. Check Logs**

```bash
# Terminal - Watch logs
tail -f logs/application.log

# Or Docker logs
docker logs -f fabric-backend
```

---

## 🚨 TROUBLESHOOTING

| Problem               | Solution                                           |
| --------------------- | -------------------------------------------------- |
| "Token expired"       | POST /api/dev/clean-tokens then restart onboarding |
| "User already exists" | POST /api/dev/reset-all                            |
| "Invalid credentials" | Check password, or reset user                      |
| "No auth token"       | Run Login request to get token                     |
| "403 Forbidden"       | Token expired, login again                         |

---

## 📚 RELATED DOCS

- [ONBOARDING_FLOW.md](../docs/modular_monolith/common/platform/auth/ONBOARDING_FLOW.md) - Onboarding flows
- [AUTH_PROTOCOL.md](../docs/modular_monolith/common/platform/auth/AUTH_PROTOCOL.md) - Auth endpoints
- [LOGGING_PROTOCOL.md](../docs/modular_monolith/common/infrastructure/LOGGING_PROTOCOL.md) - PII masking

---

**Last Updated:** 2025-10-25  
**Maintained By:** Fabric Management Team  
**Latest Addition:** ⭐ Dual-Path Onboarding + Development Tools

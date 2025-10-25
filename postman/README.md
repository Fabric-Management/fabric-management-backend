# 📮 POSTMAN COLLECTION - FABRIC MANAGEMENT

**Last Updated:** 2025-10-25  
**API Version:** 1.0.0

---

## 📋 COLLECTION DOSYALARI:

- `Fabric-Management-Modular-Monolith.postman_collection.json` - Ana API collection

---

## 🚀 QUICK START:

### 1. Import Collection:
1. Postman'ı aç
2. Import → Upload Files
3. `Fabric-Management-Modular-Monolith.postman_collection.json` seç
4. Import

### 2. Environment Variables (Otomatik):
```
base_url: http://localhost:8080
access_token: (login sonrası otomatik set edilir)
refresh_token: (login sonrası otomatik set edilir)
company_id: (company create sonrası manuel set et)
```

### 3. Test Sırası:
```
1. Health & Info → Health Check (uygulama çalışıyor mu?)
2. Authentication → Register - Check Eligibility
   ↓ Email verification code gelecek (info@storeandsale.shop)
3. Authentication → Register - Verify & Complete (code'u gir)
   ↓ access_token otomatik kaydedilir
4. Company Management → Create Company
5. User Management → Create User
6. Policy Management → Create Policy
7. Audit Logs → Get Audit Logs
```

---

## 📧 EMAIL VERIFICATION:

**NOT:** Email gönderimi henüz yeni yapıda implement edilmedi!

**Geçici Çözüm:**
- Registration check endpoint çağrıldığında kod console'da log'lanır
- Log'dan kodu alıp verify endpoint'inde kullan

**TODO:**
```java
// RegistrationService.java satır 82
// TODO: Send via Communication module (multi-channel)
log.info("Verification code generated: contactValue={}, code={} (TODO: Send via Communication)",
    request.getContactValue(), code);
```

**Eski Mikroservis Email Config (Çalışıyor):**
```
PLATFORM_SMTP_HOST=smtp.hostinger.com
PLATFORM_SMTP_PORT=465
PLATFORM_SMTP_USERNAME=info@storeandsale.shop
PLATFORM_EMAIL_FROM_NAME=Fabricode
```

**İleride:**
- Communication module'e SMTP implementation ekleyeceğiz
- WhatsApp, Email, SMS stratejileri implement edeceğiz
- Template engine ekleyeceğiz

---

## 🧪 SWAGGER UI (Alternatif):

Browser'da test etmek için:
```
http://localhost:8080/swagger-ui.html
```

**Avantajları:**
- ✅ Otomatik API documentation
- ✅ Try it out button (direkt test)
- ✅ Request/Response örnekleri
- ✅ Schema validation

---

## 🎯 API ENDPOINTS:

### **Health:**
```
GET  /api/health
GET  /api/info
GET  /actuator/health
```

### **Auth:**
```
POST /api/auth/register/check          # Email gönderir (TODO!)
POST /api/auth/register/verify         # Code verify + complete
POST /api/auth/login                    # JWT tokens döner
```

### **Company:**
```
GET    /api/common/companies
GET    /api/common/companies/tenants
GET    /api/common/companies/type/{type}
POST   /api/common/companies
GET    /api/common/companies/{id}
DELETE /api/common/companies/{id}
```

### **User:**
```
GET    /api/common/users
POST   /api/common/users
GET    /api/common/users/{id}
PUT    /api/common/users/{id}
DELETE /api/common/users/{id}
```

### **Policy:**
```
GET  /api/common/policies
POST /api/common/policies
```

### **Audit:**
```
GET /api/common/audit/logs
GET /api/common/audit/logs/user/{userId}
GET /api/common/audit/logs/resource/{resource}
```

---

## 📝 NOTES:

1. **Security:** Development mode - tüm endpoint'ler permitAll (geçici!)
2. **Email:** Henüz implement edilmedi - console'dan code al
3. **JWT:** Login sonrası access_token otomatik kaydedilir
4. **Tenant:** Şu an tenant context manuel set edilmeli (TODO: JWT'den al)

---

**Happy Testing!** 🚀


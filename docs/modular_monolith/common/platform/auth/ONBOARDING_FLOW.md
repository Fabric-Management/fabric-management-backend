# 🚀 TENANT ONBOARDING FLOW

**Version:** 1.0  
**Last Updated:** 2025-10-25  
**Module:** `common/platform/auth`  
**Purpose:** Seamless tenant creation and admin registration

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Sales-Led Onboarding](#sales-led-onboarding)
3. [Self-Service Signup](#self-service-signup)
4. [Password Setup Flow](#password-setup-flow)
5. [Technical Implementation](#technical-implementation)
6. [Best Practices](#best-practices)

---

## 🎯 OVERVIEW

Two paths for tenant creation:

| Path             | User Journey                                                  | Trial Period | Security             | Target Segment |
| ---------------- | ------------------------------------------------------------- | ------------ | -------------------- | -------------- |
| **Sales-Led**    | Sales creates tenant → Email sent → Token-only password setup | 90 days      | Token validation     | Enterprise B2B |
| **Self-Service** | User signs up → Email sent → Token + Code password setup      | 14 days      | Token + 6-digit code | SMB/Growth     |

---

## 📞 SALES-LED ONBOARDING

### **User Experience**

```
Sales Meeting
  ↓
Internal Admin Creates Tenant
  POST /api/admin/onboarding/tenant
  {
    "companyName": "XYZ Tekstil",
    "taxId": "1234567890",
    "companyType": "WEAVER",
    "adminFirstName": "Ahmet",
    "adminLastName": "Yılmaz",
    "adminContact": "ahmet@xyztekstil.com",
    "selectedOS": ["LoomOS", "AccountOS"],
    "trialDays": 90
  }
  ↓
Welcome Email Sent
  "XYZ Tekstil için FabricOS hesabınız hazır!"
  [Şifremi Oluştur] → /setup?token=abc-123
  ↓
Password Setup (No verification code!)
  Email: ahmet@xyztekstil.com ✅ (Auto-filled, verified by token)
  Password: ••••••••••
  OR
  Use suggested: mK8#pL2$nQ9wR5tX [Kullan]
  [Hesabı Aktifleştir]
  ↓
Auto-Login → Onboarding Wizard (or Dashboard)
```

**Total Time:** 2 minutes  
**Friction:** Minimal (sales already qualified customer)

---

## 🌐 SELF-SERVICE SIGNUP

### **User Experience**

```
Website Visit
  fabricmanagement.com → Products → LoomOS
  ↓
Signup Form
  POST /api/public/signup
  {
    "companyName": "ABC Örme",
    "taxId": "9876543210",
    "companyType": "KNITTER",
    "firstName": "Zeynep",
    "lastName": "Aydın",
    "email": "zeynep@abcorme.com",
    "selectedOS": ["KnitOS"],
    "acceptedTerms": true
  }
  ↓
Email Sent (with token + code)
  "ABC Örme hesabınızı aktifleştirin"
  Doğrulama Kodu: 748291
  [Hesabı Aktifleştir] → /setup?token=xyz-789
  ↓
Password Setup (Token + Code required)
  Email: zeynep@abcorme.com ✅

  Verification Code:
  ┌─┬─┬─┬─┬─┬─┐
  │7│4│8│2│9│1│ ← From email
  └─┴─┴─┴─┴─┴─┘

  Password: ••••••••••
  [Başlayalım!]
  ↓
Auto-Login → Onboarding Wizard
```

**Total Time:** 3 minutes  
**Friction:** Low (double verification for security)

---

## 🔐 PASSWORD SETUP FLOW

### **Endpoint**

```
POST /api/auth/setup-password
Content-Type: application/json

{
  "token": "abc-123-xyz-789",
  "verificationCode": "748291",  // Optional for SALES_LED
  "password": "SecurePass123!"
}

Response:
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "refresh-uuid",
    "expiresIn": 900,
    "user": { ... },
    "needsOnboarding": true  // ⚠️ Frontend redirects to wizard
  },
  "message": "Welcome! Complete your profile to get started."
}
```

### **Logic**

```java
1. Validate token (UUID, not expired, not used)
   └─ If invalid → 400 "Invalid or expired token"

2. Check token type:
   ├─ SALES_LED: Skip verification code
   └─ SELF_SERVICE: Validate verification code
       └─ If invalid → 400 "Invalid verification code"

3. Check if password already set
   └─ If exists → 400 "User already registered"

4. Hash password (BCrypt)

5. Create AuthUser (isVerified = true)

6. Mark token as used

7. Generate JWT tokens

8. Check onboarding status (hasCompletedOnboarding)

9. Auto-login + Return response
```

---

## 🛠️ TECHNICAL IMPLEMENTATION

### **Critical: tenant_id = company_id**

```java
// TenantOnboardingService.createTenantCompany()

// ⚠️ CRITICAL: Save first to get company ID
Company saved = companyRepository.save(company);

// ⚠️ CRITICAL: Set tenant_id = company_id for ROOT tenant
saved.setTenantId(saved.getId());
Company finalCompany = companyRepository.save(saved);

// Now finalCompany.getTenantId() == finalCompany.getId()
```

### **Tenant Context Management**

```java
// Create company OUTSIDE tenant context
Company company = createTenantCompany(...);

// Create user/subscriptions INSIDE tenant context
TenantContext.executeInTenantContext(company.getTenantId(), () -> {
    User user = userRepository.save(adminUser);
    Subscription sub = subscriptionRepository.save(subscription);
    return user;
});
```

### **Initial Subscriptions**

```java
// Simple model: Create only selected OS subscriptions
// No mandatory OS, no tiers - just OS on/off

Subscription loomOS = Subscription.builder()
    .osCode("LoomOS")
    .osName("Weaving Production OS")
    .status(SubscriptionStatus.TRIAL)
    .startDate(Instant.now())
    .trialEndsAt(Instant.now().plus(90, ChronoUnit.DAYS))
    .features(Map.of())
    .build();

Subscription edgeOS = Subscription.builder()
    .osCode("EdgeOS")
    .osName("IoT & Edge Computing OS")
    .status(SubscriptionStatus.TRIAL)
    .startDate(Instant.now())
    .trialEndsAt(Instant.now().plus(90, ChronoUnit.DAYS))
    .features(Map.of())
    .build();

// Pricing:
// LoomOS → $149/mo
// EdgeOS → $49/mo
// IntelligenceOS → $99/mo
```

---

## ✅ BEST PRACTICES

### **1. Sales-Led: Pre-qualified Customers**

✅ Longer trial (90 days)  
✅ Token-only verification (no code)  
✅ Sales team validates legitimacy  
✅ Professional onboarding experience

### **2. Self-Service: Security First**

✅ Shorter trial (14 days)  
✅ Double verification (token + code)  
✅ Terms acceptance required  
✅ Fraud prevention

### **3. Auto-Login After Setup**

✅ No additional login step  
✅ Seamless experience  
✅ Immediate dashboard access  
✅ Onboarding wizard if needed

### **4. Context-Aware Errors**

✅ Helpful messages (not generic "user not found")  
✅ Actionable guidance (who to contact)  
✅ Differentiate tenant vs supplier

---

## 🔄 ONBOARDING WIZARD

### **Triggered When**

```javascript
// Frontend logic
if (response.needsOnboarding) {
  navigate("/onboarding"); // Wizard
} else {
  navigate("/dashboard"); // Direct
}
```

### **Wizard Steps**

```
Step 1/4: Complete Company Profile
  ├─ Address, phone, contact details
  └─ [Skip] [Next]

Step 2/4: Create Departments
  ├─ Production, Planning, Quality, etc.
  └─ [Skip] [Next]

Step 3/4: Invite Team Members
  ├─ Email addresses + roles
  └─ [Skip Later] [Send Invitations]

Step 4/4: Payment Method (Trial - No charge)
  ├─ Credit card info
  ├─ "Charged after {trialEndsAt}"
  └─ [Skip] [Complete Setup]

Dashboard
  ├─ user.completeOnboarding()
  └─ onboardingCompletedAt = now()
```

---

## 📊 DATABASE SCHEMA

```sql
-- Registration tokens
CREATE TABLE common_auth.common_registration_token (
    id UUID PRIMARY KEY,
    token VARCHAR(36) UNIQUE NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    token_type VARCHAR(20) NOT NULL,  -- SALES_LED, SELF_SERVICE
    expires_at TIMESTAMP NOT NULL,    -- 24 hours
    is_used BOOLEAN DEFAULT FALSE,
    user_id UUID,
    company_id UUID
);

-- User onboarding tracking
ALTER TABLE common_user.common_user
ADD COLUMN onboarding_completed_at TIMESTAMP;
```

---

## 🎯 API ENDPOINTS

| Endpoint                       | Method | Auth           | Purpose                   |
| ------------------------------ | ------ | -------------- | ------------------------- |
| `/api/admin/onboarding/tenant` | POST   | PLATFORM_ADMIN | Sales-led tenant creation |
| `/api/public/signup`           | POST   | Public         | Self-service signup       |
| `/api/auth/setup-password`     | POST   | Public (token) | Complete registration     |

---

**Last Updated:** 2025-10-25  
**Maintained By:** Fabric Management Team  
**Status:** ✅ Production Ready

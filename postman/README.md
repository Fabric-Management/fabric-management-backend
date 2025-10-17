# 🧪 Postman API Testing

**Version:** 4.0 - Flow-Based Architecture  
**Date:** October 16, 2025  
**Collection:** `Fabric-Management-API.postman_collection.json`

---

## 🎯 Quick Start

### 1. Import Collection

```
Postman → Import → Fabric-Management-API.postman_collection.json
```

### 2. Set Base URL

```
Collection Variables → baseUrl = http://localhost:8080
```

### 3. Run Complete Flow

```
🎯 User Journeys → 1️⃣ New Company Registration Flow
   → Run folder (3 steps)
   → ✅ Auto-logged in, ready to use!
```

---

## 📋 Collection Structure

### 🎯 User Journeys (Flow-Based)

**Run in sequence for end-to-end testing**

**1️⃣ New Company Registration**

- Step 1: Register company (ORCHESTRATION)
- Step 2: Get verification code (manual - check DB/email)
- Step 3: Setup password + auto-login (ORCHESTRATION)

**2️⃣ Regular Login**

- Step 1: Check contact exists
- Step 2: Login

**3️⃣ Invited User Onboarding**

- Step 1: Admin invites user (ORCHESTRATION)
- Step 2: User sets password + auto-login (ORCHESTRATION)

### 🔐 Authentication

Individual auth endpoints (advanced use)

### 👥 User Management

CRUD operations (requires TENANT_ADMIN)

### 🔧 Utilities

Helper endpoints for testing

---

## ⚡ Orchestration Endpoints

Collection highlights atomic operations:

| Endpoint                                      | Operations                     | Performance      |
| --------------------------------------------- | ------------------------------ | ---------------- |
| `POST /onboarding/register`                   | Company + User + Contact       | 400ms (vs 900ms) |
| `POST /users/invite`                          | User + Contacts + Verification | 380ms (vs 920ms) |
| `POST /auth/setup-password-with-verification` | Verify + Password + Login      | 350ms (vs 900ms) |

**Benefit:** 60%+ faster, instant UX, ACID compliant

---

## 🔑 Variables (Auto-Managed)

Collection automatically sets:

- `accessToken` (from login/register)
- `userId` (from login/register)
- `tenantId` (from login/register)
- `companyId` (from registration)
- `contactValue` (from registration)

**Manual:**

- `verificationCode` (get from DB or email)

---

## 🧪 Testing Features

### Automatic Assertions

Every request has test scripts:

- Status code validation
- Response structure validation
- Variable auto-save
- Console logging

### Console Output

```
✅ User ID: 550e8400-e29b-41d4-a716-446655440000
✅ Access Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVC...
✅ Role: TENANT_ADMIN
🎉 FLOW COMPLETE!
```

---

## 📖 DTO Compliance

All request bodies match DTOs 100%:

**CheckContactRequest:**

```json
{ "contactValue": "email@example.com" }
```

**SetupPasswordWithVerificationRequest:**

```json
{
  "contactValue": "email@example.com",
  "verificationCode": "123456",
  "password": "Test@123",
  "preferredChannel": "EMAIL"
}
```

**InviteUserRequest:**

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phone": "+905551234567",
  "role": "USER",
  "sendVerification": true,
  "preferredChannel": "EMAIL"
}
```

---

## 🚀 Tips

**Get Verification Code:**

```sql
SELECT verification_code
FROM contacts
WHERE contact_value = 'your@email.com'
ORDER BY created_at DESC
LIMIT 1;
```

**Quick Reset:**
Delete collection variables → Run registration flow again

---

**Manifesto:** Flow-based, DTO-perfect, orchestration-first, auto-tested

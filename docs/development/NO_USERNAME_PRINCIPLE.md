# 🚫 NO USERNAME PRINCIPLE 🚫

```
╔═══════════════════════════════════════════════════════════════════════════╗
║                                                                           ║
║                    ⛔ NO USERNAME IN THIS PROJECT! ⛔                     ║
║                                                                           ║
║  This is a CRITICAL architectural decision that MUST be respected        ║
║  by ALL developers, in ALL chat sessions, in ALL code contributions.     ║
║                                                                           ║
╚═══════════════════════════════════════════════════════════════════════════╝
```

---

## 📜 **Policy Statement**

**The Fabric Management System does NOT use username for user identification or authentication.**

This is not a temporary decision or a "to-be-refactored" state. This is a **permanent architectural principle** based on:

- Business requirements
- Security best practices
- Modern authentication patterns
- User experience optimization
- GDPR/KVKK compliance

---

## ❌ **What is FORBIDDEN**

### **1. NO username field**

```java
// ❌ NEVER DO THIS
@Entity
public class User {
    private String username;  // ⛔ FORBIDDEN!
}
```

### **2. NO username in authentication**

```java
// ❌ NEVER DO THIS
public LoginResponse login(String username, String password) { }

// ❌ NEVER DO THIS
POST /api/auth/login
{
  "username": "john.doe",  // ⛔ FORBIDDEN!
  "password": "..."
}
```

### **3. NO username in JWT**

```json
// ❌ NEVER DO THIS
{
  "sub": "john.doe", // ⛔ FORBIDDEN! Should be UUID
  "username": "john.doe" // ⛔ FORBIDDEN!
}
```

### **4. NO username in variables/parameters**

```java
// ❌ NEVER DO THIS
String username = jwtTokenProvider.extractUsername(token);
public void processUser(String username) { }
```

---

## ✅ **What to USE Instead**

### **1. Authentication: contactValue**

```java
// ✅ CORRECT
public LoginResponse login(String contactValue, String password) {
    // contactValue can be email or phone
}

// ✅ CORRECT
POST /api/auth/login
{
  "contactValue": "user@example.com",  // or "+905551234567"
  "password": "..."
}
```

### **2. Identification: userId (UUID)**

```java
// ✅ CORRECT
@Entity
public class User extends BaseEntity {
    // getId() returns UUID - this is the identifier
    // No username field!
}

// ✅ CORRECT
String userId = jwtTokenProvider.extractUserId(token);
UUID userId = user.getId();
```

### **3. JWT: userId in 'sub' claim**

```json
// ✅ CORRECT
{
  "sub": "550e8400-e29b-41d4-a716-446655440000", // userId (UUID)
  "tenantId": "7c9e6679-7425-40de-963d-42a6ee08cd6c",
  "role": "ADMIN"
}
```

### **4. Variables/Parameters: userId**

```java
// ✅ CORRECT
String userId = jwtTokenProvider.extractUserId(token);
public void processUser(String userId) { }
public void processUser(UUID userId) { }
```

---

## 📖 **Business Rationale**

### **Why NO Username?**

1. **Business Requirement**

   - Users register with email or phone (contactValue)
   - No separate username creation step
   - Users don't need to remember another credential
   - Simplifies registration flow

2. **User Experience**

   - ✅ Users already know their email/phone
   - ✅ No "username already taken" frustration
   - ✅ No "I forgot my username" support tickets
   - ✅ One less thing to remember

3. **Security**

   - ✅ UUID-based identification (no enumeration)
   - ✅ No PII in JWT tokens (GDPR compliant)
   - ✅ Contact info changes don't break authentication
   - ✅ Privacy by design

4. **Technical Benefits**
   - ✅ Reduced complexity (one less field)
   - ✅ No username uniqueness validation
   - ✅ No username-to-user mapping layer
   - ✅ Cleaner data model

---

## 🔐 **Security Implications**

### **JWT Token Structure**

```javascript
// ❌ INSECURE (other systems)
{
  "sub": "john.doe",           // Username visible
  "email": "john@example.com"  // Email visible
}
// Risk: PII in token, GDPR violation, information leakage

// ✅ SECURE (our system)
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // UUID only
  "tenantId": "7c9e6679-7425-40de-963d-42a6ee08cd6c"
}
// Benefit: No PII, GDPR compliant, privacy-safe
```

### **Attack Surface Reduction**

| Attack Vector                | Username-based | Our System (UUID) |
| ---------------------------- | -------------- | ----------------- |
| User enumeration             | ❌ Vulnerable  | ✅ Protected      |
| Username guessing            | ❌ Possible    | ✅ Impossible     |
| Social engineering           | ❌ Easy        | ✅ Harder         |
| GDPR "right to be forgotten" | ❌ Complex     | ✅ Simple         |
| Token information leakage    | ❌ Risk        | ✅ Safe           |

---

## 🏗️ **Architecture Flow**

### **Registration & Login Flow**

```
┌─────────────────────────────────────────────────────────────────┐
│ REGISTRATION FLOW (No Username!)                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. User provides: email/phone, password, name                  │
│     ❌ NO username field!                                        │
│                                                                  │
│  2. System creates:                                             │
│     - User entity (with UUID id)                                │
│     - Contact entity (email/phone)                              │
│     - Password hash                                             │
│                                                                  │
│  3. User receives: verification email/SMS                       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ LOGIN FLOW (No Username!)                                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. User provides: contactValue, password                       │
│     ✅ contactValue = email OR phone                            │
│                                                                  │
│  2. System:                                                     │
│     - Finds Contact by value                                    │
│     - Gets User via Contact.userId                              │
│     - Verifies password                                         │
│                                                                  │
│  3. JWT generated with:                                         │
│     - sub: user.getId() (UUID)                                  │
│     - tenantId: user.getTenantId() (UUID)                       │
│     - role: user.getRole()                                      │
│     ❌ NO username, NO email in JWT!                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🧪 **Code Review Checklist**

Before approving ANY pull request, verify:

### **Entity Layer**

- [ ] No `username` field in any entity
- [ ] User identification via `getId()` (UUID)
- [ ] Contact entity handles email/phone

### **API Layer**

- [ ] Login endpoints use `contactValue`, not `username`
- [ ] JWT generation uses `userId` (UUID) in 'sub' claim
- [ ] No `username` in request/response DTOs

### **Service Layer**

- [ ] Authentication logic uses `contactValue`
- [ ] User lookup by UUID, not username
- [ ] No username validation logic

### **Security Layer**

- [ ] JWT extraction uses `extractUserId()`, not `extractUsername()`
- [ ] Spring Security principal is `userId` (UUID string)
- [ ] No username in security context

### **Documentation**

- [ ] API docs show `contactValue`, not `username`
- [ ] Examples use email/phone, not username
- [ ] No username references in comments

---

## 🚨 **Common Mistakes & Fixes**

### **Mistake #1: Using extractUsername()**

```java
// ❌ WRONG
String username = jwtTokenProvider.extractUsername(token);

// ✅ CORRECT
String userId = jwtTokenProvider.extractUserId(token);
```

### **Mistake #2: Username in DTOs**

```java
// ❌ WRONG
public class UserDto {
    private String username;
}

// ✅ CORRECT
public class UserDto {
    private UUID id;       // Identification via UUID
    private String email;  // From Contact entity
    // No username field!
}
```

### **Mistake #3: Username in authentication**

```java
// ❌ WRONG
public LoginResponse login(String username, String password) { }

// ✅ CORRECT
public LoginResponse login(String contactValue, String password) { }
```

### **Mistake #4: Username in database**

```sql
-- ❌ WRONG
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL
);

-- ✅ CORRECT
CREATE TABLE users (
    id UUID PRIMARY KEY
    -- No username column!
);

CREATE TABLE contacts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(20) NOT NULL,  -- EMAIL or PHONE
    value VARCHAR(255) NOT NULL,
    is_primary BOOLEAN DEFAULT false,
    UNIQUE(value, type)
);
```

---

## 📚 **Related Documentation**

- 📘 [PRINCIPLES.md](./PRINCIPLES.md) - Full development principles
- 🔐 [SECURITY.md](../SECURITY.md) - UUID-based JWT security
- 👤 [User Service Documentation](../services/user-service.md) - Authentication flow
- 📊 [API Documentation](../api/README.md) - Endpoint examples

---

## 🎯 **Enforcement**

This principle is enforced through:

1. ✅ **Code Comments**: All key classes have warnings
2. ✅ **Documentation**: README, PRINCIPLES.md, this file
3. ✅ **Code Review**: Mandatory checklist item
4. ✅ **@Deprecated Tags**: Old methods marked as deprecated
5. ✅ **Entity Design**: No username column in database

---

## ❓ **FAQ**

### **Q: Why not username? It's standard!**

**A:** "Standard" doesn't mean "best". Modern systems use email/phone. We follow OIDC best practices (UUID in 'sub' claim).

### **Q: What if user wants to change email?**

**A:** No problem! userId (UUID) stays the same. JWT remains valid. Contact entity is updated.

### **Q: How do we display user name?**

**A:** Use `firstName + lastName` or `displayName`. These are in User entity.

### **Q: What about backward compatibility?**

**A:** We never had username. This is the design from the start. `extractUsername()` is deprecated wrapper around `extractUserId()`.

### **Q: Other projects use username...**

**A:** This project doesn't. This is an architectural decision specific to our business requirements.

---

## ✍️ **Summary**

```
╔═══════════════════════════════════════════════════════════════════╗
║                                                                   ║
║  ✅ USE: contactValue (email/phone) for authentication           ║
║  ✅ USE: userId (UUID) for identification                        ║
║  ✅ USE: extractUserId() for JWT parsing                         ║
║                                                                   ║
║  ❌ NEVER use: username field                                    ║
║  ❌ NEVER use: username in authentication                        ║
║  ❌ NEVER use: username in JWT                                   ║
║  ❌ NEVER use: extractUsername() (deprecated)                    ║
║                                                                   ║
║  This is NOT negotiable. This is architectural law.              ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

---

**Last Updated:** October 9, 2025  
**Version:** 1.0  
**Status:** ACTIVE - MANDATORY COMPLIANCE

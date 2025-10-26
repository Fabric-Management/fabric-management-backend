# 🔐 AUTH MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/platform/auth`  
**Dependencies:** `common/infrastructure/persistence`, `common/infrastructure/events`, `common/platform/user`, `common/platform/company`, `common/platform/communication`

---

## 🎯 MODULE PURPOSE

Auth module, **Authentication (Kimlik Doğrulama)** ve **Authorization (Yetkilendirme)** süreçlerini yönetir.

### **Core Responsibilities**

- ✅ **User Registration** - Email/Phone-based registration with verification
- ✅ **Login/Logout** - JWT-based authentication
- ✅ **Token Management** - Access token + Refresh token
- ✅ **Password Management** - Secure password hashing & reset
- ✅ **Verification** - Multi-channel verification (Email, WhatsApp, SMS)
- ✅ **Session Management** - Active session tracking

---

## 🧱 MODULE STRUCTURE

```
auth/
├─ api/
│  ├─ controller/
│  │  └─ AuthController.java           # REST endpoints
│  └─ facade/
│     └─ AuthFacade.java                # Internal API
├─ app/
│  ├─ AuthService.java                  # Business logic
│  ├─ RegistrationService.java          # Registration flow
│  ├─ JwtService.java                   # JWT management
│  └─ PasswordService.java              # Password operations
├─ domain/
│  ├─ AuthUser.java                     # Auth user entity
│  ├─ RefreshToken.java                 # Refresh token entity
│  ├─ VerificationCode.java             # Verification code entity
│  └─ event/
│     ├─ UserLoginEvent.java
│     ├─ UserLogoutEvent.java
│     ├─ UserRegisteredEvent.java
│     └─ PasswordChangedEvent.java
├─ infra/
│  └─ repository/
│     ├─ AuthUserRepository.java
│     ├─ RefreshTokenRepository.java
│     └─ VerificationCodeRepository.java
└─ dto/
   ├─ LoginRequest.java
   ├─ LoginResponse.java
   ├─ RegisterRequest.java
   ├─ RegisterResponse.java
   ├─ VerifyRequest.java
   └─ ChangePasswordRequest.java
```

---

## 🔑 KEY FEATURES

### **1. Registration Flow**

```
Step 1: Check Eligibility
  ├─ User enters email or phone
  ├─ System checks if contact exists in company's approved list
  ├─ If NOT found → "Your information is not registered. Our representative will contact you."
  └─ If found → Continue to verification

Step 2: Send Verification Code
  ├─ Generate 6-digit code
  ├─ Try WhatsApp (priority 1)
  ├─ If fail → Try Email (priority 2)
  └─ If fail → Try SMS/AWS SNS (priority 3)

Step 3: Verify Code
  ├─ User enters verification code
  ├─ System validates code
  ├─ If valid → Mark user as verified
  └─ If invalid → Show error

Step 4: Set Password
  ├─ User sets password
  ├─ System hashes password (BCrypt)
  ├─ Associate user with tenant
  └─ Return JWT tokens
```

### **2. Login Flow**

```
Step 1: Validate Credentials
  ├─ User enters email/phone + password
  ├─ System finds user by contact value
  ├─ System validates password hash
  └─ If invalid → "Invalid credentials"

Step 2: Check User Status
  ├─ Is user verified?
  ├─ Is user active?
  ├─ Is tenant active?
  └─ If any check fails → Deny login

Step 3: Generate Tokens
  ├─ Generate Access Token (JWT, 15min)
  ├─ Generate Refresh Token (UUID, 7 days)
  ├─ Save refresh token to database
  └─ Return both tokens

Step 4: Publish Event
  ├─ Publish UserLoginEvent
  └─ Log audit trail
```

### **3. JWT Structure**

```json
{
  "sub": "user@example.com",
  "tenant_id": "123e4567-e89b-12d3-a456-426614174000",
  "tenant_uid": "ACME-001",
  "user_id": "456e7890-e89b-12d3-a456-426614174000",
  "user_uid": "ACME-001-USER-00042",
  "roles": ["ROLE_ADMIN", "ROLE_PLANNER"],
  "permissions": ["fabric.material.read", "fabric.material.create"],
  "department": "production",
  "iat": 1706350000,
  "exp": 1706350900
}
```

### **4. Verification Strategies**

```
Priority 1: WhatsApp
  ├─ Fast delivery
  ├─ High open rate
  └─ Cost effective

Priority 2: Email
  ├─ Universal support
  ├─ Professional
  └─ No extra cost

Priority 3: SMS (AWS SNS)
  ├─ Fallback option
  ├─ Reliable delivery
  └─ Cost per message
```

---

## 🔗 ENDPOINTS

### **REST Endpoints**

| Endpoint                           | Method | Purpose                                  | Auth Required         |
| ---------------------------------- | ------ | ---------------------------------------- | --------------------- |
| `/api/auth/register/check`         | POST   | Check registration eligibility           | ❌ No                 |
| `/api/auth/register/verify`        | POST   | Verify code and complete registration    | ❌ No                 |
| `/api/auth/login`                  | POST   | Login with credentials                   | ❌ No                 |
| `/api/auth/logout`                 | POST   | Logout and invalidate token              | ✅ Yes                |
| `/api/auth/refresh`                | POST   | Refresh access token                     | ❌ No (refresh token) |
| `/api/auth/change-password`        | POST   | Change password                          | ✅ Yes                |
| `/api/auth/reset-password/request` | POST   | Request password reset                   | ❌ No                 |
| `/api/auth/reset-password/confirm` | POST   | Confirm password reset                   | ❌ No                 |
| `/api/auth/setup-password`         | POST   | ⭐ Complete password setup (token-based) | ❌ No (token)         |

### **Onboarding Endpoints** ⭐ NEW

| Endpoint                       | Method | Purpose                   | Auth Required     |
| ------------------------------ | ------ | ------------------------- | ----------------- |
| `/api/admin/onboarding/tenant` | POST   | Sales-led tenant creation | ✅ PLATFORM_ADMIN |
| `/api/public/signup`           | POST   | Self-service signup       | ❌ Public         |

### **Internal API (Facade)**

```java
public interface AuthFacade {

    /**
     * Validate JWT token
     *
     * @param token JWT token
     * @return true if valid
     */
    boolean validateToken(String token);

    /**
     * Extract user ID from token
     *
     * @param token JWT token
     * @return User ID
     */
    UUID getUserIdFromToken(String token);

    /**
     * Extract tenant ID from token
     *
     * @param token JWT token
     * @return Tenant ID
     */
    UUID getTenantIdFromToken(String token);

    /**
     * Check if user is authenticated
     *
     * @param userId User ID
     * @return true if authenticated
     */
    boolean isAuthenticated(UUID userId);
}
```

---

## 🎯 DOMAIN MODELS

### **AuthUser Entity**

```java
@Entity
@Table(name = "common_auth_user", schema = "common_auth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String contactValue; // Email or Phone

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactType contactType; // EMAIL, PHONE

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column
    private Instant lastLoginAt;

    @Column
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column
    private Instant lockedUntil;
}
```

### **RefreshToken Entity**

```java
@Entity
@Table(name = "common_refresh_token", schema = "common_auth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRevoked = false;

    @Column
    private Instant revokedAt;
}
```

### **VerificationCode Entity**

```java
@Entity
@Table(name = "common_verification_code", schema = "common_auth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationCode extends BaseEntity {

    @Column(nullable = false)
    private String contactValue;

    @Column(nullable = false)
    private String code; // 6-digit code

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationType type; // REGISTRATION, PASSWORD_RESET

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column
    private Instant usedAt;

    @Column
    @Builder.Default
    private Integer attemptCount = 0;
}
```

---

## 🔄 EVENTS

### **Domain Events**

| Event                   | Trigger                | Payload                        |
| ----------------------- | ---------------------- | ------------------------------ |
| `UserRegisteredEvent`   | Registration complete  | userId, tenantId, contactValue |
| `UserLoginEvent`        | Successful login       | userId, tenantId, ipAddress    |
| `UserLogoutEvent`       | User logout            | userId, tenantId               |
| `PasswordChangedEvent`  | Password changed       | userId, tenantId               |
| `VerificationSentEvent` | Verification code sent | contactValue, channel          |

---

## ✅ VALIDATION RULES

### **Registration**

- ✅ Email must be valid format
- ✅ Phone must be valid format (E.164)
- ✅ Contact must exist in company database
- ✅ Contact must not be already verified
- ✅ Password min 8 characters
- ✅ Password must contain: uppercase, lowercase, number, special char

### **Login**

- ✅ Contact value required
- ✅ Password required
- ✅ User must be verified
- ✅ User must be active
- ✅ Tenant must be active
- ✅ Max 5 failed attempts → lock for 30 minutes

---

## 📚 RELATED DOCUMENTATION

- [ONBOARDING_FLOW.md](./ONBOARDING_FLOW.md) - ⭐ **Detailed onboarding flows** (sales-led + self-service)

---

**Last Updated:** 2025-10-25  
**Maintained By:** Fabric Management Team  
**Latest Addition:** ⭐ Token-Based Onboarding System

# 🔐 IDENTITY & SECURITY

**Version:** 2.1  
**Last Updated:** 2025-01-27  
**Status:** ✅ Active Development  
**Policy Architecture:** ⏳ Next Phase

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Identity Structure](#identity-structure)
3. [User and Tenant Relationship](#user-and-tenant-relationship)
4. [Communication & Verification](#communication--verification)
5. [Registration Flow](#registration-flow)
6. [Authentication & Security](#authentication--security)
7. [Entity Examples](#entity-examples)
8. [Best Practices](#best-practices)

---

## 🎯 OVERVIEW

Fabric Management platformu, **Multi-Tenant Architecture** ile **Defense-in-Depth Security** prensiplerini uygular.

### **Core Principles**

| Principle                      | Description                                  |
| ------------------------------ | -------------------------------------------- |
| **UUID Primary Key**           | Globally unique, machine-level identifier    |
| **Tenant Isolation**           | Row-Level Security (RLS) with tenant_id      |
| **Human-Readable UID**         | Audit/debugging friendly identifier          |
| **Pre-Approved Registration**  | Only pre-registered contacts can sign up     |
| **Multi-Channel Verification** | WhatsApp → Email → SMS fallback              |
| **JWT-Based Auth**             | Stateless authentication with refresh tokens |

---

## 🧩 IDENTITY STRUCTURE

### **Triple-ID System: UUID + tenant_id + UID**

Her entity üç farklı identifier içerir:

| Field       | Type   | Purpose                                         | Example                                |
| ----------- | ------ | ----------------------------------------------- | -------------------------------------- |
| `id`        | UUID   | Primary key, machine-level unique identifier    | `123e4567-e89b-12d3-a456-426614174000` |
| `tenant_id` | UUID   | Tenant isolation, multi-tenant data segregation | `789e4567-e89b-12d3-a456-426614174000` |
| `uid`       | String | Human-readable reference for audit/debugging    | `ACME-001-USER-00042`                  |

### **Why Triple-ID?**

```
UUID → Security & Integrity
  ✅ Globally unique
  ✅ Prevents ID guessing attacks
  ✅ Database foreign key relationships
  ✅ Used in API responses

tenant_id → Multi-Tenant Isolation
  ✅ Data segregation between tenants
  ✅ Row-Level Security (RLS) in PostgreSQL
  ✅ Automatic tenant filtering
  ✅ Prevents cross-tenant data access

UID → Human Readability
  ✅ Audit logs
  ✅ Support tickets
  ✅ Admin dashboards
  ✅ Customer communication
  ✅ Debugging
```

### **UID Pattern**

```
Pattern: {TENANT_UID}-{MODULE}-{ENTITY}-{SEQUENCE}

Examples:
  ACME-001-USER-00042      → User #42 of ACME tenant
  ACME-001-MAT-05123       → Material #5123
  XYZ-002-INV-00891        → Invoice #891 of XYZ tenant
  ACME-001-ORD-00156       → Order #156
```

### **BaseEntity Implementation**

```java
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // Primary key (machine-level)

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // Tenant isolation

    @Column(name = "uid", nullable = false, unique = true, length = 50)
    private String uid; // Human-readable reference

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        // Auto-set tenant ID from context
        if (this.tenantId == null) {
            this.tenantId = TenantContext.getCurrentTenantId();
        }

        // Auto-generate UID
        if (this.uid == null) {
            this.uid = UIDGenerator.generate(
                TenantContext.getCurrentTenantUid(),
                getEntityModule(),
                getEntityType(),
                getNextSequence()
            );
        }

        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    protected abstract String getEntityModule(); // "USER", "MAT", "INV"
    protected abstract String getEntityType(); // "USER", "MATERIAL", "INVOICE"
    protected abstract Long getNextSequence(); // Sequence from repository
}
```

---

## 👥 USER AND TENANT RELATIONSHIP

### **Relationship Model**

```
Tenant (Company)
  ├─ tenant_id: UUID
  ├─ tenant_uid: "ACME-001"
  └─ Users (Multiple)
      ├─ User 1
      │  ├─ id: UUID
      │  ├─ tenant_id: ACME-001's UUID
      │  ├─ uid: "ACME-001-USER-00001"
      │  └─ role: ADMIN
      ├─ User 2
      │  ├─ id: UUID
      │  ├─ tenant_id: ACME-001's UUID
      │  ├─ uid: "ACME-001-USER-00002"
      │  └─ role: PLANNER
      └─ User 3
         ├─ id: UUID
         ├─ tenant_id: ACME-001's UUID
         ├─ uid: "ACME-001-USER-00003"
         └─ role: VIEWER
```

### **User Entity**

```java
@Entity
@Table(name = "common_user")
@Getter
@Setter
@Builder
public class User extends BaseEntity {

    // Identity (from BaseEntity)
    // - UUID id              → Primary key
    // - UUID tenantId        → Tenant isolation
    // - String uid           → "ACME-001-USER-00042"

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String displayName; // Auto: firstName + lastName

    @Column(nullable = false, unique = true)
    private String contactValue; // Email or Phone (NO separate username!)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactType contactType; // EMAIL, PHONE

    @Column(nullable = false)
    private UUID companyId; // Company relationship

    @Column
    private String department; // production, planning, finance, etc.

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
```

### **Key Rules**

- ❌ **NO separate username field** - Use `contactValue` (email/phone)
- ✅ **contactValue must be unique** across entire system
- ✅ **User ALWAYS belongs to a tenant** - tenant_id mandatory
- ✅ **displayName auto-generated** - firstName + lastName
- ✅ **User can belong to one company** - companyId foreign key

---

## 📧 COMMUNICATION & VERIFICATION

### **Verification Flow**

```
┌────────────────────────────────────────┐
│ 1. Generate 6-digit verification code │
└──────────────┬─────────────────────────┘
               │
               ▼
┌────────────────────────────────────────┐
│ 2. Try WhatsApp (Priority 1)          │
│    ├─ Check WhatsApp configured?      │
│    ├─ Check recipient has WhatsApp?   │
│    └─ Send via WhatsApp Business API  │
└──────────────┬─────────────────────────┘
               │ If fails or unavailable
               ▼
┌────────────────────────────────────────┐
│ 3. Try Email (Priority 2)             │
│    ├─ Check SMTP configured?          │
│    └─ Send via Email                  │
└──────────────┬─────────────────────────┘
               │ If fails or unavailable
               ▼
┌────────────────────────────────────────┐
│ 4. Try SMS/AWS SNS (Priority 3)       │
│    ├─ Check AWS SNS configured?       │
│    └─ Send via SMS                    │
└────────────────────────────────────────┘
```

### **VerificationStrategy Implementation**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final List<VerificationStrategy> strategies;

    public void sendVerificationCode(String recipient, String code) {
        log.info("Sending verification code to: {}", recipient);

        // Sort by priority (1 = highest)
        strategies.stream()
            .sorted(Comparator.comparing(VerificationStrategy::priority))
            .filter(VerificationStrategy::isAvailable)
            .findFirst()
            .ifPresentOrElse(
                strategy -> {
                    log.info("Using {} strategy", strategy.name());
                    strategy.sendVerificationCode(recipient, code);
                },
                () -> {
                    log.error("No verification strategy available");
                    throw new VerificationUnavailableException("All verification channels unavailable");
                }
            );
    }
}
```

### **Channel Configuration (.env)**

```env
# WhatsApp (Priority 1)
WHATSAPP_ENABLED=true
WHATSAPP_API_URL=https://api.whatsapp.com/v1
WHATSAPP_API_KEY=your-whatsapp-api-key
WHATSAPP_PHONE_NUMBER_ID=your-phone-number-id

# Email (Priority 2)
EMAIL_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
SMTP_FROM=noreply@fabricmanagement.com

# SMS/AWS SNS (Priority 3)
SMS_ENABLED=true
AWS_SNS_REGION=eu-west-1
AWS_SNS_ACCESS_KEY=your-aws-access-key
AWS_SNS_SECRET_KEY=your-aws-secret-key
```

---

## 🔄 REGISTRATION FLOW

### **Complete Registration Flow**

```
Step 1: Check Eligibility
  ┌─────────────────────────────────────────────┐
  │ POST /api/auth/register/check               │
  │ { "contactValue": "user@example.com" }      │
  └──────────────┬──────────────────────────────┘
                 │
                 ▼
  ┌─────────────────────────────────────────────┐
  │ System checks if contact exists in DB       │
  │ (Pre-approved user list from Company)       │
  └──────────────┬──────────────────────────────┘
                 │
                 ├─ NOT FOUND
                 │   └─> "Your information is not registered.
                 │        Our representative will contact you."
                 │
                 └─ FOUND → Continue
                             │
                             ▼
Step 2: Send Verification Code
  ┌─────────────────────────────────────────────┐
  │ Generate 6-digit code                       │
  │ Save to verification_code table             │
  │ Send via WhatsApp/Email/SMS (fallback)      │
  └──────────────┬──────────────────────────────┘
                 │
                 ▼
Step 3: Verify Code
  ┌─────────────────────────────────────────────┐
  │ POST /api/auth/register/verify              │
  │ { "contactValue": "...", "code": "123456" } │
  └──────────────┬──────────────────────────────┘
                 │
                 ▼
  ┌─────────────────────────────────────────────┐
  │ Validate verification code                  │
  │ ├─ Code matches?                            │
  │ ├─ Code not expired? (10 min TTL)           │
  │ └─ Code not already used?                   │
  └──────────────┬──────────────────────────────┘
                 │
                 ├─ INVALID → "Invalid or expired code"
                 │
                 └─ VALID → Continue
                             │
                             ▼
Step 4: Set Password & Complete
  ┌─────────────────────────────────────────────┐
  │ { "password": "SecurePass123!" }            │
  └──────────────┬──────────────────────────────┘
                 │
                 ▼
  ┌─────────────────────────────────────────────┐
  │ Hash password (BCrypt)                      │
  │ Mark user as verified                       │
  │ Generate JWT tokens                         │
  │ Return: { accessToken, refreshToken }       │
  └─────────────────────────────────────────────┘
```

### **Registration Implementation**

```java
@Service
@RequiredArgsConstructor
@Transactional
public class RegistrationService {

    private final UserRepository userRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final VerificationService verificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public RegistrationCheckResponse checkEligibility(String contactValue) {
        // 1. Find user by contact value
        Optional<User> user = userRepository.findByContactValue(contactValue);

        if (user.isEmpty()) {
            return RegistrationCheckResponse.notEligible(
                "Your information is not registered. Our representative will contact you."
            );
        }

        // 2. Check if already verified
        if (user.get().getIsVerified()) {
            return RegistrationCheckResponse.alreadyRegistered(
                "This account is already registered. Please login."
            );
        }

        // 3. Generate verification code
        String code = generateVerificationCode(); // 6-digit

        // 4. Save verification code
        VerificationCode verificationCode = VerificationCode.builder()
            .contactValue(contactValue)
            .code(code)
            .type(VerificationType.REGISTRATION)
            .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
            .isUsed(false)
            .build();
        verificationCodeRepository.save(verificationCode);

        // 5. Send verification code (multi-channel)
        verificationService.sendVerificationCode(contactValue, code);

        return RegistrationCheckResponse.eligible(
            "Verification code sent. Please check your messages."
        );
    }

    public RegisterResponse verifyAndRegister(VerifyRequest request) {
        // 1. Validate verification code
        VerificationCode verificationCode = verificationCodeRepository
            .findByContactValueAndCodeAndType(
                request.getContactValue(),
                request.getCode(),
                VerificationType.REGISTRATION
            )
            .orElseThrow(() -> new InvalidVerificationCodeException());

        // 2. Check expiry
        if (verificationCode.getExpiresAt().isBefore(Instant.now())) {
            throw new VerificationCodeExpiredException();
        }

        // 3. Check if already used
        if (verificationCode.getIsUsed()) {
            throw new VerificationCodeAlreadyUsedException();
        }

        // 4. Find user
        User user = userRepository.findByContactValue(request.getContactValue())
            .orElseThrow(() -> new UserNotFoundException());

        // 5. Hash password
        String passwordHash = passwordEncoder.encode(request.getPassword());

        // 6. Update user (mark as verified)
        AuthUser authUser = AuthUser.builder()
            .contactValue(user.getContactValue())
            .contactType(user.getContactType())
            .passwordHash(passwordHash)
            .isVerified(true)
            .isActive(true)
            .build();
        authUser.setTenantId(user.getTenantId());
        authUserRepository.save(authUser);

        // 7. Mark verification code as used
        verificationCode.setIsUsed(true);
        verificationCode.setUsedAt(Instant.now());
        verificationCodeRepository.save(verificationCode);

        // 8. Generate JWT tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // 9. Publish event
        eventPublisher.publish(new UserRegisteredEvent(user.getTenantId(), user.getId()));

        return RegisterResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .user(UserDto.from(user))
            .build();
    }

    private String generateVerificationCode() {
        return String.format("%06d", new Random().nextInt(999999));
    }
}
```

---

## 🔑 AUTHENTICATION & SECURITY

### **JWT Structure**

```json
{
  "sub": "user@example.com",
  "tenant_id": "123e4567-e89b-12d3-a456-426614174000",
  "tenant_uid": "ACME-001",
  "user_id": "456e7890-e89b-12d3-a456-426614174000",
  "user_uid": "ACME-001-USER-00042",
  "company_id": "789e4567-e89b-12d3-a456-426614174000",
  "roles": ["ROLE_ADMIN", "ROLE_PLANNER"],
  "permissions": ["fabric.material.read", "fabric.material.create"],
  "department": "production",
  "iat": 1706350000,
  "exp": 1706350900
}
```

### **Token Payload Breakdown**

| Field         | Type     | Purpose                               |
| ------------- | -------- | ------------------------------------- |
| `sub`         | String   | Subject (contact value: email/phone)  |
| `tenant_id`   | UUID     | Tenant isolation                      |
| `tenant_uid`  | String   | Human-readable tenant ID              |
| `user_id`     | UUID     | User primary key                      |
| `user_uid`    | String   | Human-readable user ID                |
| `company_id`  | UUID     | Company relationship                  |
| `roles`       | String[] | User roles (ROLE_ADMIN, ROLE_PLANNER) |
| `permissions` | String[] | Specific permissions                  |
| `department`  | String   | User department                       |
| `iat`         | Number   | Issued at (Unix timestamp)            |
| `exp`         | Number   | Expiry (Unix timestamp)               |

### **Authentication Flow**

```
Login Request
  ├─ POST /api/auth/login
  ├─ { "contactValue": "user@example.com", "password": "password123" }
  │
  ▼
Validate Credentials
  ├─ Find user by contactValue
  ├─ Verify password hash (BCrypt)
  ├─ Check user.isVerified = true
  ├─ Check user.isActive = true
  └─ Check tenant.isActive = true
  │
  ▼
Generate Tokens
  ├─ Access Token (JWT, 15 min)
  │  └─ Contains: tenant_id, user_id, roles, permissions
  ├─ Refresh Token (UUID, 7 days)
  │  └─ Saved to database for revocation
  │
  ▼
Response
  └─ {
      "accessToken": "eyJhbGciOiJIUzI1NiIs...",
      "refreshToken": "123e4567-e89b-12d3-...",
      "expiresIn": 900,
      "user": {
        "id": "456e7890-e89b-12d3-a456-426614174000",
        "uid": "ACME-001-USER-00042",
        "displayName": "John Doe"
      }
    }
```

---

## 🧾 ENTITY EXAMPLES

### **User Entity Example**

```java
User {
    id: UUID = "456e7890-e89b-12d3-a456-426614174000"
    tenantId: UUID = "123e4567-e89b-12d3-a456-426614174000"
    uid: String = "ACME-001-USER-00042"

    firstName: "John"
    lastName: "Doe"
    displayName: "John Doe"  // Auto-generated
    contactValue: "john.doe@acme.com"  // NO separate username!
    contactType: ContactType.EMAIL
    companyId: UUID = "789e4567-e89b-12d3-a456-426614174000"
    department: "production"
    isActive: true
}
```

### **Company Entity Example**

```java
Company {
    id: UUID = "789e4567-e89b-12d3-a456-426614174000"
    tenantId: UUID = "123e4567-e89b-12d3-a456-426614174000"
    uid: String = "ACME-001"

    companyName: "ACME Corporation"
    taxId: "1234567890"
    address: "123 Main Street"
    city: "Istanbul"
    country: "Turkey"
    phoneNumber: "+90 555 123 4567"
    email: "info@acme.com"
    companyType: CompanyType.MANUFACTURER
    parentCompanyId: null  // No parent
    isActive: true
}
```

---

## ✅ BEST PRACTICES

### **1. Always Use contactValue (NO username)**

```java
// ✅ Good: Use contactValue
User user = userRepository.findByContactValue("user@example.com");

// ❌ Bad: Separate username field
User user = userRepository.findByUsername("johndoe"); // NO username field!
```

### **2. Auto-Generate displayName**

```java
// ✅ Good: Auto-generate
User user = User.builder()
    .firstName("John")
    .lastName("Doe")
    .build();
// displayName = "John Doe" (auto)

// ❌ Bad: Manual
user.setDisplayName("John Doe"); // Redundant
```

### **3. Tenant-Scoped Queries**

```java
// ✅ Good: Tenant-scoped
List<User> users = userRepository.findByTenantIdAndIsActiveTrue(tenantId);

// ❌ Bad: No tenant filtering (cross-tenant data leak!)
List<User> users = userRepository.findAll(); // Dangerous!
```

### **4. UID for Human Readability Only**

```java
// ✅ Good: Use UID in logs/audit
log.info("User created: uid={}", user.getUid());  // "ACME-001-USER-00042"

// ❌ Bad: Use UID as primary key
User user = userRepository.findByUid("ACME-001-USER-00042"); // Slow, not indexed
```

### **5. Secure Sensitive Data in .env**

```java
// ✅ Good: Environment variables
@Value("${SMTP_PASSWORD}")
private String smtpPassword;

// ❌ Bad: Hardcoded
private String smtpPassword = "my-password"; // NEVER!
```

---

## 🔒 SECURITY CHECKLIST

- ✅ **UUID** as primary key (globally unique)
- ✅ **tenant_id** for multi-tenant isolation
- ✅ **UID** for human-readable reference
- ✅ **contactValue** (email/phone) instead of username
- ✅ **BCrypt** password hashing
- ✅ **JWT** with refresh token
- ✅ **Pre-approved registration** (contacts in DB)
- ✅ **Multi-channel verification** (WhatsApp → Email → SMS)
- ✅ **Row-Level Security (RLS)** in PostgreSQL
- ✅ **Audit logging** for all actions
- ✅ **Environment variables** for sensitive data

---

## 📊 SUMMARY

This identity model provides:

✅ **Security** - UUID prevents ID guessing attacks  
✅ **Tenant Isolation** - tenant_id ensures data segregation  
✅ **Readability** - UID for audit/debugging  
✅ **Simplicity** - NO separate username, use contactValue  
✅ **Flexibility** - Multi-channel verification  
✅ **Enterprise-Grade** - Scalable, maintainable, production-ready

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

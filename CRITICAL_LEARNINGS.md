# 🎯 CRITICAL LEARNINGS FROM DOCUMENTATION

**Date:** 2025-10-24  
**Purpose:** Key takeaways from modular_monolith documentation  
**Priority:** 🔴 MUST FOLLOW

---

## 📚 Documents Analyzed:

1. ✅ ARCHITECTURE.md
2. ✅ MODULE_PROTOCOLS.md
3. ✅ IDENTITY_AND_SECURITY.md
4. ✅ COMMUNICATION_PATTERNS.md
5. ✅ SECURITY_POLICIES.md
6. ✅ POLICY_ENGINE.md
7. ✅ SUBSCRIPTION_MANAGEMENT.md
8. ✅ All Protocol files (AUTH, USER, COMPANY, POLICY, AUDIT, COMMUNICATION)

---

## 🎯 TOP 10 CRITICAL RULES

### 1. ❌ NO USERNAME FIELD - Use contactValue

```java
// ✅ CORRECT
private String contactValue; // "user@example.com" or "+905551234567"
private ContactType contactType; // EMAIL or PHONE

// ❌ WRONG
private String username; // NEVER USE THIS!
```

### 2. ✅ Triple-ID System (UUID + tenant_id + uid)

```java
// Every entity MUST have:
private UUID id;           // Machine-level (database PK)
private UUID tenantId;     // Multi-tenant isolation
private String uid;        // Human-readable ("ACME-001-USER-00042")
```

### 3. ✅ Auto-Generate displayName

```java
// ✅ CORRECT - Auto in constructor/builder
this.displayName = firstName + " " + lastName;

// ❌ WRONG - Manual input
dto.setDisplayName("John Doe"); // Redundant!
```

### 4. ✅ All Queries MUST be Tenant-Scoped

```java
// ✅ CORRECT
List<Material> findByTenantIdAndIsActiveTrue(UUID tenantId);
Optional<Material> findByTenantIdAndId(UUID tenantId, UUID id);

// ❌ WRONG - Cross-tenant data leak!
List<Material> findAll(); // DANGEROUS!
```

### 5. ✅ ZERO Hardcoded Values

```java
// ✅ CORRECT
@Value("${POSTGRES_HOST:localhost}")
private String dbHost;

// ❌ WRONG
private String dbHost = "localhost"; // NEVER!
```

### 6. ✅ 5-Layer Policy Engine

```
Layer 1: OS SUBSCRIPTION → hasYarnOS? (MOST CRITICAL!)
Layer 2: TENANT → maxMaterials < 1000?
Layer 3: COMPANY → department = "production"?
Layer 4: USER → role = "PLANNER"?
Layer 5: CONDITIONS → time = 08:00-18:00?
```

**Decision:**

- Default: **DENY**
- Explicit **ALLOW** required
- **DENY** overrides **ALLOW** (Amazon IAM style)

### 7. ✅ Communication Patterns by Use Case

| Use Case                       | Pattern    | Why                      |
| ------------------------------ | ---------- | ------------------------ |
| Read data from another module  | **Facade** | Fast, simple, senkron    |
| Notify other modules of change | **Event**  | Loose coupling, asenkron |
| Reliable cross-module event    | **Outbox** | Transaction-safe         |
| Complex business logic         | **CQRS**   | Separation of concerns   |

### 8. ✅ Multi-Channel Verification (Priority Order)

```
Priority 1: WhatsApp (Fastest, Highest open rate)
Priority 2: Email (Universal, Professional)
Priority 3: SMS/AWS SNS (Fallback, Reliable)
```

**Implementation:**

```java
strategies.stream()
    .sorted(Comparator.comparing(VerificationStrategy::priority))
    .filter(VerificationStrategy::isAvailable)
    .findFirst()
    .ifPresent(strategy -> strategy.send(code));
```

### 9. ✅ Registration Flow (Pre-Approved Only!)

```
Step 1: Check Eligibility → Contact MUST exist in DB
Step 2: Send Verification Code → Multi-channel
Step 3: Verify Code → 6-digit, 10 min TTL
Step 4: Set Password → BCrypt hash
Step 5: Generate JWT → Access + Refresh tokens
```

**Critical:** Only pre-registered contacts can sign up!

### 10. ✅ JWT Structure (All Required Claims)

```json
{
  "sub": "user@example.com",
  "tenant_id": "uuid",
  "tenant_uid": "ACME-001",
  "user_id": "uuid",
  "user_uid": "ACME-001-USER-00042",
  "company_id": "uuid",
  "roles": ["ROLE_ADMIN"],
  "permissions": ["fabric.material.create"],
  "department": "production",
  "iat": timestamp,
  "exp": timestamp
}
```

---

## 🏗️ MODULE STRUCTURE (MANDATORY)

Every module MUST follow this structure:

```
{feature}/
├─ api/
│  ├─ controller/          # External REST API
│  │  └─ {Feature}Controller.java
│  └─ facade/              # Internal API (for other modules)
│     └─ {Feature}Facade.java
├─ app/
│  ├─ command/             # CQRS Commands
│  │  ├─ Create{Feature}Command.java
│  │  └─ Update{Feature}Command.java
│  ├─ query/               # CQRS Queries
│  │  └─ Get{Feature}Query.java
│  └─ {Feature}Service.java
├─ domain/
│  ├─ {Feature}.java       # Entity extends BaseEntity
│  └─ event/
│     ├─ {Feature}CreatedEvent.java
│     └─ {Feature}UpdatedEvent.java
├─ infra/
│  ├─ repository/
│  │  └─ {Feature}Repository.java
│  └─ client/
│     ├─ interface/
│     │  └─ {External}Client.java
│     └─ impl/
│        └─ {External}RestClient.java
└─ dto/
   ├─ {Feature}Dto.java
   ├─ Create{Feature}Request.java
   └─ Update{Feature}Request.java
```

---

## ⚠️ COMMON MISTAKES TO AVOID

| Mistake                       | Correct Approach                           |
| ----------------------------- | ------------------------------------------ |
| ❌ Using username field       | ✅ Use contactValue (email/phone)          |
| ❌ Manual displayName         | ✅ Auto-generate from firstName + lastName |
| ❌ findAll() queries          | ✅ findByTenantIdAnd...()                  |
| ❌ Hardcoded configs          | ✅ Environment variables                   |
| ❌ Direct module dependencies | ✅ Use Facade interfaces                   |
| ❌ Synchronous events         | ✅ Asynchronous @EventListener             |
| ❌ UID as primary key         | ✅ UID for display only, UUID for queries  |
| ❌ Password in plain text     | ✅ BCrypt hashing                          |
| ❌ Single auth channel        | ✅ Multi-channel (WhatsApp/Email/SMS)      |
| ❌ Default ALLOW              | ✅ Default DENY (whitelist approach)       |

---

## 🔒 SECURITY CHECKLIST (EVERY MODULE)

- ✅ JWT validation in filters
- ✅ @PolicyCheck annotation on endpoints
- ✅ Tenant-scoped queries (tenant_id)
- ✅ Audit logging (@AuditLog)
- ✅ Password hashing (BCrypt)
- ✅ Environment variables for secrets
- ✅ HTTPS in production
- ✅ Rate limiting
- ✅ Input validation (@Valid)
- ✅ SQL injection prevention (JPA)

---

## 📊 SUBSCRIPTION MODEL (CRITICAL!)

**Every tenant has OS subscriptions:**

```java
Company {
    subscriptions: [
        { osCode: "YarnOS", status: "ACTIVE" },
        { osCode: "LoomOS", status: "TRIAL" },
        { osCode: "PlanOS", status: "EXPIRED" }
    ]
}
```

**Policy checks subscription FIRST:**

```java
// Layer 1: OS Subscription
if (!hasActiveSubscription("YarnOS")) {
    return PolicyDecision.deny("No active YarnOS subscription");
}
```

---

## 🎯 MODULE DEPENDENCY MATRIX

| Module      | Can Depend On                          |
| ----------- | -------------------------------------- |
| common      | NONE                                   |
| production  | common                                 |
| logistics   | common, production                     |
| finance     | common, logistics, production          |
| human       | common                                 |
| procurement | common, finance                        |
| integration | common, production, logistics, finance |
| insight     | common, production, logistics, finance |

**Enforcement:** Spring Modulith `@ApplicationModule(allowedDependencies = {...})`

---

## 🚀 NEXT: COMPANY MODULE IMPLEMENTATION

**Based on:**

- COMPANY_PROTOCOL.md
- SUBSCRIPTION_MANAGEMENT.md
- OS_SUBSCRIPTION_MODEL.md

**Must Include:**

1. Company entity (with tenant_id, uid)
2. Department entity
3. Subscription entity
4. OSDefinition entity
5. Company CRUD endpoints
6. Subscription lifecycle management
7. OS-based access control integration

---

## 🏢 COMPANY TYPE CLASSIFICATION (2025-10-24 UPDATE)

### **22 Company Types - Real Textile Industry Model**

#### **TENANT Companies** (6 types - Can use platform)

```
SPINNER              → İplikçi (OS: SpinnerOS, YarnOS)
WEAVER               → Dokumacı (OS: WeaverOS, LoomOS)
KNITTER              → Örücü (OS: KnitterOS)
DYER_FINISHER        → Boyahane/Terbiye (OS: DyeOS, FinishOS)
VERTICAL_MILL        → Entegre Tesis (OS: FabricOS - all modules)
GARMENT_MANUFACTURER → Konfeksiyon (OS: GarmentOS)
```

#### **SUPPLIER Companies** (6 types)

```
FIBER_SUPPLIER, YARN_SUPPLIER, CHEMICAL_SUPPLIER
CONSUMABLE_SUPPLIER, PACKAGING_SUPPLIER, MACHINE_SUPPLIER
```

#### **SERVICE_PROVIDER Companies** (7 types)

```
LOGISTICS_PROVIDER, MAINTENANCE_SERVICE, IT_SERVICE_PROVIDER
KITCHEN_SUPPLIER, HR_SERVICE_PROVIDER, LAB, UTILITY_PROVIDER
```

#### **PARTNER Companies** (4 types)

```
FASON, AGENT, TRADER, FINANCE_PARTNER
```

#### **CUSTOMER Companies** (1 type)

```
CUSTOMER
```

### **Smart Business Logic**

```java
// Only tenants can subscribe to OS
if (companyType.isTenant()) {
    String[] recommendedOS = companyType.getSuggestedOS();
    // Create subscription
}

// Category-based filtering
CompanyCategory category = companyType.getCategory();
switch (category) {
    case TENANT -> allowOSSubscription();
    case SUPPLIER -> trackForProcurement();
    case SERVICE_PROVIDER -> manageContracts();
    case PARTNER -> commercialAgreements();
    case CUSTOMER -> salesManagement();
}
```

### **New Endpoints**

```
GET /api/common/companies/tenants         → Only platform users
GET /api/common/companies/type/SPINNER    → Only spinners
GET /api/common/companies/type/CHEMICAL_SUPPLIER → Suppliers
```

---

**This knowledge is CRITICAL for all future development!**

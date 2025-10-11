# 🏢 Tenant Model & User Roles - Complete Guide

**Date:** 2025-10-11  
**Version:** 1.0  
**Status:** 🎯 DEFINITIVE GUIDE  
**Purpose:** Crystal-clear explanation of tenant model and user role hierarchy

---

## 📋 Table of Contents

1. [Current State Analysis](#-current-state-analysis)
2. [Ideal Target Architecture](#-ideal-target-architecture)
3. [Tenant Model Explained](#-tenant-model-explained)
4. [Role Hierarchy](#-role-hierarchy)
5. [Visual Examples](#-visual-examples)
6. [Implementation Guide](#-implementation-guide)

---

## 🔍 Current State Analysis

### ❓ Problem: Yapı Biraz Karmaşık

**Şu an elimizde olan:**

```
✅ tenant_id isolation çalışıyor (her kayıtta var)
✅ Company table var
✅ User table var (tenant_id ile)
⚠️ Role field var AMA enum yok (String)
⚠️ SUPER_ADMIN kullanılıyor AMA tenant-scoped mı değil mi belirsiz
❌ TENANT_ADMIN rolü yok (sadece ADMIN var)
❌ Subscription management logic yok
❌ Module activation yok
```

**Karışıklık Noktaları:**

1. **Company vs Tenant?** → Aynı şey mi, farklı mı?
2. **SUPER_ADMIN vs ADMIN?** → Farkları ne?
3. **tenant_id + company_id?** → İkisi de var, hangisi ne için?
4. **Role String field** → Enum olmalı değil mi?

---

## 🎯 Ideal Target Architecture

### ✅ Net Yapı (Önerilen)

```
╔═══════════════════════════════════════════════════════════════╗
║                    FABRICODE PLATFORM                          ║
║                  (Multi-Tenant B2B SaaS)                       ║
╚═══════════════════════════════════════════════════════════════╝
                              │
                              │ manages
                              ▼
        ┌─────────────────────────────────────┐
        │      SUPER_ADMIN (Platform)         │
        │  - Manages ALL tenants              │
        │  - Manages subscriptions            │
        │  - System-wide governance           │
        │  - NO tenant_id (or tenant_id=PLATFORM)│
        └─────────────────────────────────────┘
                              │
                              │ controls
                              ▼
        ╔═════════════════════════════════════╗
        ║         TENANTS (Companies)         ║
        ╚═════════════════════════════════════╝
                    │
        ┌───────────┼───────────┐
        │           │           │
        ▼           ▼           ▼
    [Tenant 1]  [Tenant 2]  [Tenant 3]
     Atlas      Royal       Beta
     Textile    DyeWorks    Fabrics
```

---

## 🏢 Tenant Model Explained

### 🔑 Core Concept: **Company = Tenant**

**Basit Kural:**

```
1 Company = 1 Tenant
1 Tenant = İzole bir müşteri/şirket
Tenant = Company ID olarak kullanılıyor
```

### 📊 Tenant Entity Structure

```java
@Entity
@Table(name = "companies") // Company = Tenant
public class Company extends BaseEntity {

    // ═══════════════════════════════════════════════════════
    // TENANT IDENTITY
    // ═══════════════════════════════════════════════════════
    private UUID id;              // Bu = tenant_id (PK)
    private String name;          // "Atlas Textile"
    private String legalName;     // "Atlas Tekstil San. ve Tic. A.Ş."
    private String taxId;

    // ═══════════════════════════════════════════════════════
    // SUBSCRIPTION (B2B SaaS Model)
    // ═══════════════════════════════════════════════════════
    private SubscriptionPlan plan;           // TRIAL, STANDARD, PRO, ENTERPRISE
    private LocalDateTime subscriptionStartDate;
    private LocalDateTime subscriptionEndDate;
    private SubscriptionStatus status;       // ACTIVE, SUSPENDED, EXPIRED

    // ═══════════════════════════════════════════════════════
    // MODULE ACTIVATION (Feature Toggle)
    // ═══════════════════════════════════════════════════════
    // One-to-Many relationship
    private Set<ModuleActivation> activatedModules;
    // Example: [HR, ORDER, STOCK, WEAVING]

    // ═══════════════════════════════════════════════════════
    // TENANT LIMITS (Resource Management)
    // ═══════════════════════════════════════════════════════
    private int maxUsers;         // Subscription limit
    private int currentUsers;     // Current count
    private int maxStorage;       // GB limit

    // ═══════════════════════════════════════════════════════
    // BUSINESS CONTEXT (Multi-tenant relationships)
    // ═══════════════════════════════════════════════════════
    private CompanyType businessType;  // INTERNAL, CUSTOMER, SUPPLIER
    private UUID parentCompanyId;      // For supplier/customer relationships
}
```

### 🗂️ Module Activation Table

```sql
CREATE TABLE module_activations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES companies(id),
    module VARCHAR(50) NOT NULL,  -- 'HR', 'STOCK', 'WEAVING', etc.
    activated_at TIMESTAMP NOT NULL,
    activated_by UUID REFERENCES users(id),
    expires_at TIMESTAMP,         -- For trial modules
    is_active BOOLEAN DEFAULT TRUE,

    UNIQUE(tenant_id, module)     -- One activation per module per tenant
);
```

**Example Data:**

| tenant_id  | module  | activated_at | is_active |
| ---------- | ------- | ------------ | --------- |
| atlas-uuid | HR      | 2025-01-15   | TRUE      |
| atlas-uuid | ORDER   | 2025-01-15   | TRUE      |
| atlas-uuid | STOCK   | 2025-01-15   | TRUE      |
| atlas-uuid | WEAVING | 2025-02-10   | TRUE      |
| royal-uuid | HR      | 2025-02-01   | TRUE      |
| royal-uuid | DYEING  | 2025-02-01   | TRUE      |

---

## 👥 Role Hierarchy

### 🌟 Complete Role Structure

```
┌────────────────────────────────────────────────────┐
│               PLATFORM LEVEL                       │
│  (Scope: GLOBAL - All Tenants)                     │
└────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────┐
    │  👑 SUPER_ADMIN                          │
    │  - Platform owner/operator               │
    │  - Manages all tenants                   │
    │  - Manages subscriptions                 │
    │  - System configuration                  │
    │  - NO tenant_id (or special UUID)        │
    │  - Can access ANY tenant's data          │
    └─────────────────────────────────────────┘

═══════════════════════════════════════════════════════

┌────────────────────────────────────────────────────┐
│               TENANT LEVEL                         │
│  (Scope: Single Tenant - Isolated)                 │
└────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────┐
    │  🏢 TENANT_ADMIN                         │
    │  - Company owner/CEO                     │
    │  - Manages company users                 │
    │  - Manages company settings              │
    │  - Subscription visibility (read-only)   │
    │  - Can see all company data              │
    │  - CANNOT access other tenants           │
    │  - Has tenant_id (their company)         │
    └─────────────────────────────────────────┘
                    │
                    │ creates/manages
                    ▼
    ┌─────────────────────────────────────────┐
    │  👔 MANAGER                              │
    │  - Department/function head              │
    │  - Manages team members                  │
    │  - Approves requests                     │
    │  - Module-specific access                │
    │  - Has tenant_id                         │
    └─────────────────────────────────────────┘
                    │
                    │ supervises
                    ▼
    ┌─────────────────────────────────────────┐
    │  👤 USER (Staff)                         │
    │  - Regular employee                      │
    │  - Module-specific tasks                 │
    │  - Limited scope                         │
    │  - Has tenant_id                         │
    └─────────────────────────────────────────┘
                    │
                    │ limited access
                    ▼
    ┌─────────────────────────────────────────┐
    │  👁️ VIEWER (Read-Only)                   │
    │  - External partners/auditors            │
    │  - Read-only access                      │
    │  - Specific data only                    │
    │  - Has tenant_id (or guest)              │
    └─────────────────────────────────────────┘
```

### 📋 Role Definition (Enum)

```java
package com.fabricmanagement.shared.domain.role;

/**
 * System-wide role hierarchy
 *
 * Scope: PLATFORM vs TENANT
 */
public enum SystemRole {

    // ═════════════════════════════════════════════════════
    // PLATFORM LEVEL (GLOBAL Scope)
    // ═════════════════════════════════════════════════════

    /**
     * Platform Super Administrator
     * - Manages all tenants
     * - System-wide access
     * - Subscription management
     */
    SUPER_ADMIN("SUPER_ADMIN", RoleScope.PLATFORM, 100),


    // ═════════════════════════════════════════════════════
    // TENANT LEVEL (TENANT Scope)
    // ═════════════════════════════════════════════════════

    /**
     * Tenant Administrator
     * - Company owner/CEO
     * - Manages tenant users
     * - Full tenant access
     */
    TENANT_ADMIN("TENANT_ADMIN", RoleScope.TENANT, 80),

    /**
     * Department/Function Manager
     * - Manages team
     * - Approves requests
     * - Module-specific
     */
    MANAGER("MANAGER", RoleScope.TENANT, 60),

    /**
     * Regular User/Staff
     * - Performs daily tasks
     * - Module-specific
     */
    USER("USER", RoleScope.TENANT, 40),

    /**
     * Read-Only Viewer
     * - External partners
     * - Auditors
     */
    VIEWER("VIEWER", RoleScope.TENANT, 20);

    private final String value;
    private final RoleScope scope;
    private final int priority; // For comparison (higher = more powerful)

    SystemRole(String value, RoleScope scope, int priority) {
        this.value = value;
        this.scope = scope;
        this.priority = priority;
    }

    public boolean isPlatformRole() {
        return scope == RoleScope.PLATFORM;
    }

    public boolean isTenantRole() {
        return scope == RoleScope.TENANT;
    }

    public boolean hasHigherPriorityThan(SystemRole other) {
        return this.priority > other.priority;
    }
}

public enum RoleScope {
    PLATFORM,  // Cross-tenant (SUPER_ADMIN)
    TENANT     // Single tenant (all other roles)
}
```

---

## 🎨 Visual Examples

### Example 1: Multi-Tenant Platform Structure

```
╔═══════════════════════════════════════════════════════════════╗
║                    FABRICODE PLATFORM                          ║
║              (Hosted at app.fabricode.com)                     ║
╚═══════════════════════════════════════════════════════════════╝
                              │
            ┌─────────────────┴─────────────────┐
            │                                   │
            ▼                                   ▼
┌─────────────────────────┐       ┌─────────────────────────┐
│  SUPER_ADMIN Panel      │       │  Public Registration    │
│  (admin.fabricode.com)  │       │  (signup.fabricode.com) │
│                         │       │                         │
│  - All tenants list     │       │  New company signup →   │
│  - Subscriptions        │       │  Creates new tenant     │
│  - System metrics       │       │                         │
└─────────────────────────┘       └─────────────────────────┘
            │
            │ manages
            ▼
╔═══════════════════════════════════════════════════════════════╗
║                         TENANTS                                ║
╚═══════════════════════════════════════════════════════════════╝
            │
    ┌───────┼────────┬──────────┐
    │       │        │          │
    ▼       ▼        ▼          ▼
┌────────┐ ┌─────┐ ┌──────┐ ┌──────┐
│ Atlas  │ │Royal│ │ Beta │ │ Zeta │
│Textile │ │Dye  │ │Fabr. │ │Mills │
└────────┘ └─────┘ └──────┘ └──────┘
 tenant:1   tenant:2 tenant:3 tenant:4

 Plan: PRO  Plan:STD Plan:TRIAL Plan:ENT
 Users: 45  Users:12 Users: 5   Users:120
 Modules:   Modules: Modules:   Modules:
 ✅ HR      ✅ HR    ✅ HR       ✅ ALL
 ✅ ORDER   ✅ ORDER ✅ ORDER
 ✅ STOCK   ✅ STOCK ✅ STOCK
 ✅ WEAVING ❌       ❌
```

### Example 2: Single Tenant (Atlas Textile) Structure

```
╔═══════════════════════════════════════════════════════════════╗
║                    TENANT: Atlas Textile                       ║
║                    tenant_id: 550e8400-...                     ║
║                    Plan: PRO | Status: ACTIVE                  ║
║                    Modules: HR, ORDER, STOCK, WEAVING          ║
╚═══════════════════════════════════════════════════════════════╝
                              │
            ┌─────────────────┴─────────────────┐
            │                                   │
            ▼                                   ▼
    ┌──────────────────┐              ┌──────────────────┐
    │  TENANT_ADMIN    │              │  MODULES         │
    │  Mehmet Yılmaz   │              │  (Activated)     │
    │  (CEO)           │              │                  │
    │  - All Access    │              │  ✅ HR Module    │
    └──────────────────┘              │  ✅ ORDER Module │
            │                         │  ✅ STOCK Module │
            │ manages                 │  ✅ WEAVING      │
            ▼                         └──────────────────┘
    ┌──────────────────────────────────────┐
    │         DEPARTMENT MANAGERS          │
    ├──────────────────────────────────────┤
    │  👔 Ayşe (HR Manager)                │
    │     - HR Module access               │
    │     - Can manage HR staff            │
    │                                      │
    │  👔 Ali (Production Manager)         │
    │     - ORDER + STOCK + WEAVING        │
    │     - Can manage production staff    │
    │                                      │
    │  👔 Fatma (Sales Manager)            │
    │     - ORDER Module                   │
    │     - Can manage sales staff         │
    └──────────────────────────────────────┘
            │
            │ supervises
            ▼
    ┌──────────────────────────────────────┐
    │         REGULAR USERS (Staff)        │
    ├──────────────────────────────────────┤
    │  👤 Zeynep (HR Specialist)           │
    │     - HR Module only                 │
    │                                      │
    │  👤 Burak (Warehouse Staff)          │
    │     - STOCK Module only              │
    │                                      │
    │  👤 Deniz (Weaving Operator)         │
    │     - WEAVING Module only            │
    │                                      │
    │  ... 40 more users                   │
    └──────────────────────────────────────┘
```

### Example 3: User Data Structure

```sql
-- SUPER_ADMIN (Platform Level)
INSERT INTO users VALUES (
    '00000000-0000-0000-0000-000000000001',      -- id
    '00000000-0000-0000-0000-000000000000',      -- tenant_id = PLATFORM (special UUID)
    'Platform',                                   -- first_name
    'Administrator',                              -- last_name
    'super@fabricode.com',                       -- (in contacts)
    'SUPER_ADMIN',                               -- role ✅
    'ACTIVE'                                     -- status
);

-- TENANT_ADMIN (Atlas Textile)
INSERT INTO users VALUES (
    '550e8400-e29b-41d4-a716-446655440001',      -- id
    '550e8400-e29b-41d4-a716-446655440000',      -- tenant_id = Atlas Textile
    'Mehmet',                                    -- first_name
    'Yılmaz',                                    -- last_name
    'mehmet@atlastextile.com',                   -- (in contacts)
    'TENANT_ADMIN',                              -- role ✅
    'ACTIVE'                                     -- status
);

-- MANAGER (Atlas Textile - HR Manager)
INSERT INTO users VALUES (
    '550e8400-e29b-41d4-a716-446655440002',      -- id
    '550e8400-e29b-41d4-a716-446655440000',      -- tenant_id = Atlas Textile (same!)
    'Ayşe',                                      -- first_name
    'Demir',                                     -- last_name
    'ayse@atlastextile.com',                     -- (in contacts)
    'MANAGER',                                   -- role ✅
    'ACTIVE'                                     -- status
);

-- USER (Atlas Textile - HR Specialist)
INSERT INTO users VALUES (
    '550e8400-e29b-41d4-a716-446655440003',      -- id
    '550e8400-e29b-41d4-a716-446655440000',      -- tenant_id = Atlas Textile (same!)
    'Zeynep',                                    -- first_name
    'Kaya',                                      -- last_name
    'zeynep@atlastextile.com',                   -- (in contacts)
    'USER',                                      -- role ✅
    'ACTIVE'                                     -- status
);
```

---

## 🔐 Permission Matrix

### What Each Role Can Do

| Action                              | SUPER_ADMIN | TENANT_ADMIN | MANAGER      | USER         | VIEWER     |
| ----------------------------------- | ----------- | ------------ | ------------ | ------------ | ---------- |
| **Tenant Management**               |
| Create tenant                       | ✅          | ❌           | ❌           | ❌           | ❌         |
| Delete tenant                       | ✅          | ❌           | ❌           | ❌           | ❌         |
| Suspend tenant                      | ✅          | ❌           | ❌           | ❌           | ❌         |
| View all tenants                    | ✅          | ❌           | ❌           | ❌           | ❌         |
| **Subscription Management**         |
| Manage plans                        | ✅          | ❌           | ❌           | ❌           | ❌         |
| Upgrade/downgrade                   | ✅          | ⚠️ Request   | ❌           | ❌           | ❌         |
| Activate modules                    | ✅          | ⚠️ Request   | ❌           | ❌           | ❌         |
| View subscription                   | ✅          | ✅           | ❌           | ❌           | ❌         |
| **User Management (Within Tenant)** |
| Create TENANT_ADMIN                 | ✅          | ❌           | ❌           | ❌           | ❌         |
| Create MANAGER                      | ✅          | ✅           | ❌           | ❌           | ❌         |
| Create USER                         | ✅          | ✅           | ✅           | ❌           | ❌         |
| Delete users                        | ✅          | ✅           | ⚠️ Team only | ❌           | ❌         |
| View users                          | ✅          | ✅           | ✅           | ⚠️ Self only | ⚠️ Limited |
| **Data Access (Within Tenant)**     |
| View all data                       | ✅          | ✅           | ⚠️ Dept only | ⚠️ Limited   | ⚠️ Limited |
| Create records                      | ✅          | ✅           | ✅           | ✅           | ❌         |
| Update records                      | ✅          | ✅           | ✅           | ✅           | ❌         |
| Delete records                      | ✅          | ✅           | ⚠️ Approved  | ❌           | ❌         |
| **System Access**                   |
| Admin panel                         | ✅          | ❌           | ❌           | ❌           | ❌         |
| Tenant dashboard                    | ✅          | ✅           | ✅           | ✅           | ✅         |
| API access                          | ✅          | ✅           | ✅           | ✅           | ✅         |
| Reports                             | ✅          | ✅           | ✅           | ⚠️ Limited   | ⚠️ Limited |

**Legend:**

- ✅ Full access
- ⚠️ Partial/conditional access
- ❌ No access

---

## 🛠️ Implementation Guide

### Step 1: Database Schema Update

```sql
-- =================================================================
-- STEP 1: Add SystemRole enum to database
-- =================================================================

-- Create role enum type
CREATE TYPE system_role AS ENUM (
    'SUPER_ADMIN',
    'TENANT_ADMIN',
    'MANAGER',
    'USER',
    'VIEWER'
);

-- =================================================================
-- STEP 2: Alter users table
-- =================================================================

-- Change role from VARCHAR to enum
ALTER TABLE users
    ALTER COLUMN role TYPE system_role
    USING role::system_role;

-- Add NOT NULL constraint
ALTER TABLE users
    ALTER COLUMN role SET NOT NULL;

-- Add default value
ALTER TABLE users
    ALTER COLUMN role SET DEFAULT 'USER';

-- =================================================================
-- STEP 3: Create module_activations table
-- =================================================================

CREATE TABLE module_activations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    module VARCHAR(50) NOT NULL,
    activated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_by UUID REFERENCES users(id),
    expires_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Constraints
    UNIQUE(tenant_id, module)
);

-- Index for fast lookups
CREATE INDEX idx_module_activations_tenant ON module_activations(tenant_id);
CREATE INDEX idx_module_activations_active ON module_activations(tenant_id, is_active);

-- =================================================================
-- STEP 4: Add subscription fields to companies
-- =================================================================

ALTER TABLE companies ADD COLUMN IF NOT EXISTS subscription_plan VARCHAR(50);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(20);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS subscription_start_date TIMESTAMP;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS subscription_end_date TIMESTAMP;

-- Add check constraint for plan
ALTER TABLE companies ADD CONSTRAINT chk_subscription_plan
    CHECK (subscription_plan IN ('TRIAL', 'STANDARD', 'PRO', 'ENTERPRISE'));

-- Add check constraint for status
ALTER TABLE companies ADD CONSTRAINT chk_subscription_status
    CHECK (subscription_status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'CANCELLED'));
```

### Step 2: Java Enum Implementation

```java
// =================================================================
// FILE: shared-domain/src/.../role/SystemRole.java
// =================================================================

package com.fabricmanagement.shared.domain.role;

/**
 * System-wide role hierarchy
 */
public enum SystemRole {
    SUPER_ADMIN("SUPER_ADMIN", RoleScope.PLATFORM, 100),
    TENANT_ADMIN("TENANT_ADMIN", RoleScope.TENANT, 80),
    MANAGER("MANAGER", RoleScope.TENANT, 60),
    USER("USER", RoleScope.TENANT, 40),
    VIEWER("VIEWER", RoleScope.TENANT, 20);

    private final String value;
    private final RoleScope scope;
    private final int priority;

    SystemRole(String value, RoleScope scope, int priority) {
        this.value = value;
        this.scope = scope;
        this.priority = priority;
    }

    public String getValue() { return value; }
    public RoleScope getScope() { return scope; }
    public int getPriority() { return priority; }

    public boolean isPlatformRole() {
        return scope == RoleScope.PLATFORM;
    }

    public boolean isTenantRole() {
        return scope == RoleScope.TENANT;
    }

    public boolean hasHigherPriorityThan(SystemRole other) {
        return this.priority > other.priority;
    }

    public boolean canManageRole(SystemRole targetRole) {
        return this.priority > targetRole.priority;
    }
}

public enum RoleScope {
    PLATFORM,  // Cross-tenant (SUPER_ADMIN)
    TENANT     // Single tenant (all others)
}
```

```java
// =================================================================
// FILE: shared-domain/src/.../module/Module.java
// =================================================================

package com.fabricmanagement.shared.domain.module;

/**
 * System modules that can be activated per tenant
 */
public enum Module {

    // Base Modules (included in all plans)
    HR("HR", "Human Resources", ModuleCategory.BASE),
    ORDER("ORDER", "Order Management", ModuleCategory.BASE),
    STOCK("STOCK", "Stock Management", ModuleCategory.BASE),
    SALES("SALES", "Sales Management", ModuleCategory.BASE),
    ACCOUNTING("ACCOUNTING", "Accounting & Finance", ModuleCategory.BASE),
    PRODUCTION("PRODUCTION", "Production Planning", ModuleCategory.BASE),

    // Extension Modules (PRO/ENTERPRISE)
    WEAVING("WEAVING", "Weaving Operations", ModuleCategory.EXTENSION),
    DYEING("DYEING", "Dyeing Operations", ModuleCategory.EXTENSION),
    TASK("TASK", "Task Management", ModuleCategory.EXTENSION),
    QUALITY("QUALITY", "Quality Control", ModuleCategory.EXTENSION),
    MAINTENANCE("MAINTENANCE", "Maintenance Management", ModuleCategory.EXTENSION),
    ANALYTICS("ANALYTICS", "Analytics & Reports", ModuleCategory.EXTENSION);

    private final String code;
    private final String displayName;
    private final ModuleCategory category;

    Module(String code, String displayName, ModuleCategory category) {
        this.code = code;
        this.displayName = displayName;
        this.category = category;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public ModuleCategory getCategory() { return category; }

    public boolean isBaseModule() {
        return category == ModuleCategory.BASE;
    }

    public boolean isExtension() {
        return category == ModuleCategory.EXTENSION;
    }
}

public enum ModuleCategory {
    BASE,       // Included in all plans
    EXTENSION   // Optional, requires PRO/ENTERPRISE
}
```

### Step 3: Update User Entity

```java
// =================================================================
// FILE: user-service/.../domain/aggregate/User.java
// =================================================================

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // ... other fields ...

    // ✅ CHANGE: Use enum instead of String
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @lombok.Builder.Default
    private SystemRole role = SystemRole.USER;

    // Helper methods
    public boolean isSuperAdmin() {
        return role == SystemRole.SUPER_ADMIN;
    }

    public boolean isTenantAdmin() {
        return role == SystemRole.TENANT_ADMIN;
    }

    public boolean isManager() {
        return role == SystemRole.MANAGER;
    }

    public boolean hasHigherRoleThan(User other) {
        return this.role.hasHigherPriorityThan(other.getRole());
    }
}
```

### Step 4: Subscription Management Service

```java
// =================================================================
// FILE: company-service/.../service/SubscriptionService.java
// =================================================================

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final CompanyRepository companyRepository;
    private final ModuleActivationRepository moduleActivationRepository;

    /**
     * Start trial for new tenant
     */
    @Transactional
    public void startTrial(UUID tenantId, int durationDays) {
        Company company = companyRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        LocalDateTime now = LocalDateTime.now();

        company.setSubscriptionPlan("TRIAL");
        company.setSubscriptionStatus("ACTIVE");
        company.setSubscriptionStartDate(now);
        company.setSubscriptionEndDate(now.plusDays(durationDays));

        companyRepository.save(company);

        // Activate base modules
        activateBaseModules(tenantId);
    }

    /**
     * Activate base modules (HR, ORDER, STOCK)
     */
    @Transactional
    public void activateBaseModules(UUID tenantId) {
        List<Module> baseModules = Arrays.stream(Module.values())
            .filter(Module::isBaseModule)
            .toList();

        for (Module module : baseModules) {
            activateModule(tenantId, module, null); // No expiration for base
        }
    }

    /**
     * Activate specific module
     */
    @Transactional
    public void activateModule(UUID tenantId, Module module, LocalDateTime expiresAt) {
        // Check if already activated
        Optional<ModuleActivation> existing = moduleActivationRepository
            .findByTenantIdAndModule(tenantId, module);

        if (existing.isPresent()) {
            // Reactivate if inactive
            ModuleActivation activation = existing.get();
            activation.setActive(true);
            activation.setExpiresAt(expiresAt);
            moduleActivationRepository.save(activation);
        } else {
            // Create new activation
            ModuleActivation activation = ModuleActivation.builder()
                .tenantId(tenantId)
                .module(module)
                .activatedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .isActive(true)
                .build();

            moduleActivationRepository.save(activation);
        }
    }

    /**
     * Check if module is active for tenant
     */
    public boolean isModuleActive(UUID tenantId, Module module) {
        return moduleActivationRepository
            .findByTenantIdAndModule(tenantId, module)
            .map(activation -> {
                // Check if active AND not expired
                if (!activation.isActive()) {
                    return false;
                }

                if (activation.getExpiresAt() != null) {
                    return LocalDateTime.now().isBefore(activation.getExpiresAt());
                }

                return true;
            })
            .orElse(false);
    }
}
```

---

## 📊 Complete Examples

### Example 1: Tenant Onboarding Flow

```java
/**
 * Self-provisioning: New company registration
 */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    @PostMapping("/register")
    @Transactional
    public OnboardingResponse registerCompany(
        @RequestBody CompanyRegistrationRequest request) {

        // ═════════════════════════════════════════════════════
        // STEP 1: Create Tenant (Company)
        // ═════════════════════════════════════════════════════
        Company company = Company.builder()
            .name(request.getCompanyName())
            .legalName(request.getLegalName())
            .taxId(request.getTaxId())
            .type(CompanyType.INTERNAL) // They are creating their own tenant
            .status(CompanyStatus.ACTIVE)
            .isActive(true)
            .maxUsers(5) // Trial limit
            .build();

        company = companyRepository.save(company);
        UUID tenantId = company.getId();

        // ═════════════════════════════════════════════════════
        // STEP 2: Create TENANT_ADMIN User
        // ═════════════════════════════════════════════════════
        User tenantAdmin = User.builder()
            .tenantId(tenantId)
            .firstName(request.getAdminFirstName())
            .lastName(request.getAdminLastName())
            .role(SystemRole.TENANT_ADMIN) // ✅ Tenant admin
            .status(UserStatus.ACTIVE)
            .userContext(UserContext.INTERNAL)
            .build();

        tenantAdmin = userRepository.save(tenantAdmin);

        // ═════════════════════════════════════════════════════
        // STEP 3: Create Contact (Email for login)
        // ═════════════════════════════════════════════════════
        contactService.createContact(
            tenantId,
            tenantAdmin.getId(),
            request.getAdminEmail(),
            ContactType.EMAIL,
            true // isPrimary
        );

        // ═════════════════════════════════════════════════════
        // STEP 4: Start Trial Subscription (6 months)
        // ═════════════════════════════════════════════════════
        subscriptionService.startTrial(tenantId, 180);

        // ═════════════════════════════════════════════════════
        // STEP 5: Activate Base Modules
        // ═════════════════════════════════════════════════════
        subscriptionService.activateBaseModules(tenantId);
        // Activates: HR, ORDER, STOCK, SALES, ACCOUNTING, PRODUCTION

        // ═════════════════════════════════════════════════════
        // STEP 6: Send Welcome Email
        // ═════════════════════════════════════════════════════
        emailService.sendWelcomeEmail(tenantAdmin.getId());

        // ═════════════════════════════════════════════════════
        // STEP 7: Publish Event
        // ═════════════════════════════════════════════════════
        eventPublisher.publish(new TenantCreatedEvent(
            tenantId,
            company.getName(),
            tenantAdmin.getId()
        ));

        return OnboardingResponse.success(tenantId, tenantAdmin.getId());
    }
}
```

### Example 2: SUPER_ADMIN Operations

```java
/**
 * Super Admin dashboard operations
 */
@RestController
@RequestMapping("/admin/api/v1/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')") // ✅ Platform level only
public class TenantManagementController {

    /**
     * List all tenants (cross-tenant access)
     */
    @GetMapping
    public Page<TenantSummary> getAllTenants(Pageable pageable) {
        // SUPER_ADMIN can see ALL tenants
        return companyRepository.findAll(pageable)
            .map(this::toTenantSummary);
    }

    /**
     * Upgrade tenant subscription
     */
    @PutMapping("/{tenantId}/subscription")
    public void upgradeSubscription(
        @PathVariable UUID tenantId,
        @RequestBody SubscriptionUpdate update) {

        Company company = companyRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        // Update plan
        company.setSubscriptionPlan(update.getPlan());
        company.setSubscriptionEndDate(update.getEndDate());

        companyRepository.save(company);

        // Activate additional modules if PRO/ENTERPRISE
        if ("PRO".equals(update.getPlan()) || "ENTERPRISE".equals(update.getPlan())) {
            activateExtensionModules(tenantId, update.getModules());
        }
    }

    /**
     * Suspend tenant (non-payment, violation)
     */
    @PostMapping("/{tenantId}/suspend")
    public void suspendTenant(
        @PathVariable UUID tenantId,
        @RequestBody SuspensionRequest request) {

        Company company = companyRepository.findById(tenantId)
            .orElseThrow();

        company.setSubscriptionStatus("SUSPENDED");
        company.setIsActive(false);

        companyRepository.save(company);

        // Notify tenant admin
        notificationService.notifyTenantSuspension(tenantId, request.getReason());
    }
}
```

### Example 3: TENANT_ADMIN Operations

```java
/**
 * Tenant admin operations (within their tenant only)
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
public class TenantUserManagementController {

    /**
     * Create new user in tenant
     * TENANT_ADMIN can create MANAGER and USER
     * TENANT_ADMIN CANNOT create TENANT_ADMIN (only SUPER_ADMIN can)
     */
    @PostMapping
    public UserResponse createUser(
        @CurrentSecurityContext SecurityContext ctx,
        @RequestBody CreateUserRequest request) {

        UUID tenantId = ctx.getTenantId();
        SystemRole requestedRole = request.getRole();

        // ═════════════════════════════════════════════════════
        // AUTHORIZATION CHECK
        // ═════════════════════════════════════════════════════

        // Check: Can current user create this role?
        if (ctx.hasRole("TENANT_ADMIN")) {
            // TENANT_ADMIN can create MANAGER, USER, VIEWER only
            if (requestedRole == SystemRole.SUPER_ADMIN ||
                requestedRole == SystemRole.TENANT_ADMIN) {
                throw new ForbiddenException(
                    "TENANT_ADMIN cannot create SUPER_ADMIN or TENANT_ADMIN roles");
            }
        }

        // Check: Tenant user limit
        int currentUsers = userRepository.countByTenantId(tenantId);
        Company company = companyRepository.findById(tenantId).orElseThrow();

        if (currentUsers >= company.getMaxUsers()) {
            throw new BusinessException(
                "User limit reached. Upgrade subscription to add more users.");
        }

        // ═════════════════════════════════════════════════════
        // CREATE USER
        // ═════════════════════════════════════════════════════

        User user = userMapper.fromCreateRequest(
            request,
            tenantId,  // ✅ Same tenant as admin
            ctx.getUserId()
        );

        user = userRepository.save(user);

        // Update user count
        company.setCurrentUsers(currentUsers + 1);
        companyRepository.save(company);

        return userMapper.toResponse(user);
    }
}
```

---

## 🎯 Key Takeaways

### ✅ Remember These Rules:

1. **Company = Tenant**

   ```
   1 Company record = 1 Tenant = 1 Customer/Business
   company.id = tenant_id (used everywhere)
   ```

2. **Role Hierarchy**

   ```
   SUPER_ADMIN → TENANT_ADMIN → MANAGER → USER → VIEWER
   Priority: 100 → 80 → 60 → 40 → 20
   ```

3. **Scope Separation**

   ```
   SUPER_ADMIN = PLATFORM scope (all tenants)
   All others = TENANT scope (single tenant only)
   ```

4. **Module System**

   ```
   Base Modules = All plans (HR, ORDER, STOCK, etc.)
   Extensions = PRO/ENTERPRISE (WEAVING, DYEING, etc.)
   Stored in: module_activations table
   ```

5. **Subscription Model**
   ```
   TRIAL → STANDARD → PRO → ENTERPRISE
   Each plan = different modules + user limits
   Managed by SUPER_ADMIN
   ```

---

## 📝 Migration Checklist

### To Implement This Model:

- [ ] **Database**

  - [ ] Create SystemRole enum type
  - [ ] Alter users.role to enum
  - [ ] Create module_activations table
  - [ ] Add subscription fields to companies
  - [ ] Add constraints

- [ ] **Java Code**

  - [ ] Create SystemRole enum
  - [ ] Create RoleScope enum
  - [ ] Create Module enum
  - [ ] Update User entity
  - [ ] Create ModuleActivation entity
  - [ ] Update Company entity

- [ ] **Services**

  - [ ] Create SubscriptionService
  - [ ] Create ModuleActivationService
  - [ ] Update UserService (role checks)
  - [ ] Create OnboardingService

- [ ] **Controllers**

  - [ ] Create OnboardingController (public)
  - [ ] Create TenantManagementController (SUPER_ADMIN)
  - [ ] Update UserController (role-based creation)
  - [ ] Create SubscriptionController

- [ ] **Security**

  - [ ] Update @PreAuthorize annotations
  - [ ] Add role-based filters
  - [ ] Add module access checks
  - [ ] Update PolicyEngine

- [ ] **Testing**
  - [ ] Test tenant creation
  - [ ] Test role permissions
  - [ ] Test module activation
  - [ ] Test subscription limits

---

**Document Owner:** AI Assistant  
**Reviewed By:** [Pending User Review]  
**Status:** 🎯 DEFINITIVE GUIDE  
**Last Updated:** 2025-10-11 23:45 UTC+1  
**Version:** 1.0  
**Next Update:** After implementation feedback

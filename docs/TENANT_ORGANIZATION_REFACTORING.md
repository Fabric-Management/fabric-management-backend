# Tenant & Organization Refactoring (Faz 2)

Bu doküman, Company entity'sinin Tenant ve Organization olarak ayrılmasını açıklar.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  BEFORE (Company overloaded)                                     │
│  ├── Tenant (root company, tenant_id = company_id)              │
│  ├── Internal departments/branches                              │
│  └── External partners (→ TradingPartner'a taşındı - Faz 1)     │
├─────────────────────────────────────────────────────────────────┤
│  AFTER (Clean separation)                                        │
│                                                                  │
│  PLATFORM LEVEL (no tenant_id - global)                         │
│  ├── Tenant (NEW - subscription, billing, settings)             │
│  └── TradingPartnerRegistry                                     │
│                                                                  │
│  TENANT LEVEL (tenant_id scoped)                                │
│  ├── Organization (renamed from Company - internal structure)   │
│  ├── TradingPartner (external partners - Faz 1)                 │
│  └── User, Employee, Department, etc.                           │
└─────────────────────────────────────────────────────────────────┘
```

## Key Changes

### 1. Tenant Entity (NEW)

**Location:** `common/platform/tenant/`

Platform-level entity for subscription and settings:

```java
@Entity
@Table(name = "common_tenant", schema = "common_tenant")
public class Tenant {
    UUID id;           // Primary key - used as tenant_id everywhere
    String uid;        // Human-readable (e.g., "ACME-001")
    String slug;       // URL-friendly (e.g., "acme-corp")
    String name;
    TenantStatus status;  // TRIAL, ACTIVE, SUSPENDED, CANCELLED
    TenantSettings settings;  // JSONB (timezone, locale, currency)
    Instant trialEndsAt;
    String subscriptionPlan;
}
```

### 2. Organization Entity (renamed from Company)

**Location:** `common/platform/organization/`

Internal organizational structure:

```java
@Entity
@Table(name = "common_organization", schema = "common_company")
public class Organization extends BaseEntity {
    String name;           // Was: company_name
    String taxId;
    OrganizationType organizationType;  // Was: CompanyType (only tenant types)
    UUID parentOrganizationId;  // Was: parent_company_id
}
```

### 3. OrganizationType (cleaned)

Partner types removed (now in TradingPartner.PartnerType):

```java
public enum OrganizationType {
    SPINNER,
    WEAVER,
    KNITTER,
    DYER_FINISHER,
    VERTICAL_MILL,
    GARMENT_MANUFACTURER
    // REMOVED: SUPPLIER, SERVICE_PROVIDER, PARTNER, CUSTOMER categories
}
```

## Onboarding Flow Changes

### Before (tenant_id = company_id hack)

```
1. CreateCompanyStep → Company with tenant_id = company_id
2. CreateAdminUserStep
3. ...
```

### After (Clean separation)

```
1. CreateTenantStep → Tenant entity (platform-level)
2. CreateOrganizationStep → Organization (tenant-scoped)
3. CreateAdminUserStep
4. AssignContactAndAddressStep
5. CreateSubscriptionsStep
6. SeedOrganizationStep
7. CreateRegistrationTokenStep
8. SendWelcomeEmailStep
```

## Database Migrations

### V045: Create Tenant Table

```sql
CREATE SCHEMA common_tenant;
CREATE TABLE common_tenant.common_tenant (
    id UUID PRIMARY KEY,
    uid VARCHAR(50) UNIQUE NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    settings JSONB NOT NULL DEFAULT '...',
    ...
);
```

### V046: Migrate and Rename

1. Migrate root companies (tenant_id = id) to common_tenant
2. Add FK constraint to common_tenant
3. Rename common_company → common_organization
4. Rename columns (company_name → name, etc.)
5. Clean up CompanyType → OrganizationType

## API Changes

### New Endpoints

```
GET  /api/organizations          - List organizations
POST /api/organizations          - Create organization
GET  /api/organizations/{id}     - Get organization
PUT  /api/organizations/{id}     - Update organization
DELETE /api/organizations/{id}   - Deactivate organization
GET  /api/organizations/root     - Get root organization
GET  /api/organizations/{id}/children - List children
```

### Deprecated Endpoints

```
/api/common/companies  → Use /api/organizations instead
```

## Migration Strategy

### For New Code

Use the new entities directly:

- `TenantFacade` for tenant operations
- `OrganizationFacade` for organization operations

### For Existing Code

The old `CompanyFacade` continues to work but delegates to new services. Gradually migrate to new facades.

## File Structure

```
common/platform/
├── tenant/                          # NEW
│   ├── api/
│   │   └── facade/TenantFacade.java
│   ├── app/TenantService.java
│   ├── domain/
│   │   ├── Tenant.java
│   │   ├── TenantStatus.java
│   │   ├── TenantSettings.java
│   │   └── event/
│   ├── dto/
│   └── infra/repository/
│
├── organization/                    # NEW (replaces company/)
│   ├── api/
│   │   ├── controller/OrganizationController.java
│   │   └── facade/OrganizationFacade.java
│   ├── app/OrganizationService.java
│   ├── domain/
│   │   ├── Organization.java
│   │   ├── OrganizationType.java
│   │   ├── OrganizationContact.java
│   │   ├── OrganizationAddress.java
│   │   └── event/
│   ├── dto/
│   └── infra/repository/
│
└── company/                         # DEPRECATED - kept for migration
    └── (existing files - gradually migrate)
```

## Backward Compatibility

### OnboardingContext

```java
// New fields
UUID tenantId;
String tenantUid;
UUID organizationId;
String organizationUid;

// Deprecated (kept for backward compatibility)
@Deprecated UUID companyId;
@Deprecated String companyUid;
```

### TenantOnboardingResponse

Returns `organizationId` in `companyId` field for backward compatibility.

## Next Steps

1. **Test migrations** on staging
2. **Update frontend** to use new endpoints
3. **Migrate remaining services** from CompanyFacade to OrganizationFacade
4. **Remove deprecated code** after migration period

## Related Documents

- [Trading Partner Faz 1](./TRADING_PARTNER_FAZ1_5.md)
- [Local Setup](./LOCAL_SETUP.md)

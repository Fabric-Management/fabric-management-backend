# 🏢 Company Service Documentation

**Version:** 2.0  
**Last Updated:** 2025-10-10  
**Port:** 8083  
**Database:** fabric_management (company_schema)  
**Status:** ✅ Production Ready

---

## 📋 Overview

Company Service manages company profiles, multi-tenancy, and policy data. Implements Clean Architecture with Anemic Domain Model pattern.

### Core Responsibilities

- ✅ Company CRUD operations
- ✅ Multi-tenancy management (tenant isolation)
- ✅ Company type hierarchy (INTERNAL, CUSTOMER, SUPPLIER)
- ✅ Subscription plan management
- ✅ Department management
- ✅ Duplicate company detection (fuzzy matching)
- ✅ **Policy data management** (PolicyRegistry, UserPermission, Audit)

---

## 🏗️ Architecture

### Current Architecture (Post-Refactoring - Oct 2025)

```
company-service/
├── api/
│   ├── CompanyController.java [176 satır]
│   ├── PolicyAuditController.java [85 satır]
│   ├── UserPermissionController.java [108 satır]
│   └── dto/
│       ├── request/
│       │   ├── CreateCompanyRequest.java
│       │   ├── UpdateCompanyRequest.java
│       │   ├── UpdateCompanySettingsRequest.java
│       │   ├── UpdateSubscriptionRequest.java
│       │   ├── CheckDuplicateRequest.java
│       │   └── CreateUserPermissionRequest.java
│       └── response/
│           ├── CompanyResponse.java
│           ├── CheckDuplicateResponse.java
│           ├── CompanyAutocompleteResponse.java
│           ├── PolicyAuditResponse.java
│           └── UserPermissionResponse.java
│
├── application/
│   ├── mapper/
│   │   ├── CompanyMapper.java [126 satır]
│   │   ├── CompanyEventMapper.java [47 satır]
│   │   ├── PolicyAuditMapper.java
│   │   └── UserPermissionMapper.java
│   └── service/
│       ├── CompanyService.java [281 satır]
│       ├── PolicyAuditQueryService.java [83 satır]
│       └── UserPermissionService.java [95 satır]
│
├── domain/
│   ├── aggregate/
│   │   ├── Company.java [109 satır] ← Pure data holder!
│   │   ├── Department.java
│   │   └── CompanyRelationship.java
│   ├── event/
│   │   ├── CompanyCreatedEvent.java
│   │   ├── CompanyUpdatedEvent.java
│   │   └── CompanyDeletedEvent.java
│   └── valueobject/
│       ├── CompanyName.java
│       ├── CompanyStatus.java
│       ├── CompanyType.java
│       └── Industry.java
│
└── infrastructure/
    ├── repository/
    │   └── CompanyRepository.java
    ├── messaging/
    │   └── CompanyEventPublisher.java [39 satır]
    └── config/
        └── DuplicateDetectionConfig.java
```

### Key Patterns

- ✅ **Anemic Domain Model**: Entity = Pure data holder
- ✅ **Mapper Separation**: 4 focused mappers
- ✅ **Clean Architecture**: Clear layer separation
- ✅ **10 Golden Rules**: SRP, DRY, KISS, YAGNI applied
- ✅ **Policy Data Hub**: Manages policy tables for all services

---

## 📦 Domain Model

### Company Aggregate (109 lines - Anemic Domain)

```java
@Entity
@Table(name = "companies")
@Getter
@Setter
@SuperBuilder
public class Company extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;  // ← UUID type safety!

    @Embedded
    private CompanyName name;  // Value object

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CompanyType type;  // CORPORATION, LLC, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "industry", nullable = false)
    private Industry industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CompanyStatus status;

    @Type(JsonBinaryType.class)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;

    @Type(JsonBinaryType.class)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private Map<String, Object> preferences;

    // Subscription
    @Column(name = "subscription_start_date")
    private LocalDateTime subscriptionStartDate;

    @Column(name = "subscription_end_date")
    private LocalDateTime subscriptionEndDate;

    @Column(name = "subscription_plan")
    private String subscriptionPlan;

    @Column(name = "max_users")
    private int maxUsers;

    @Column(name = "current_users")
    private int currentUsers;

    // ========== POLICY FIELDS ==========
    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false)
    @lombok.Builder.Default
    private com.fabricmanagement.shared.domain.policy.CompanyType businessType
        = com.fabricmanagement.shared.domain.policy.CompanyType.INTERNAL;

    @Column(name = "parent_company_id")
    private UUID parentCompanyId;

    @Column(name = "relationship_type")
    private String relationshipType;

    // NO BUSINESS METHODS! (Anemic Domain)
}
```

**Key Changes (Oct 2025 Refactoring):**

- ✅ 430 lines → 109 lines (-75%)
- ✅ Removed 20+ business methods
- ✅ Pure @Getter/@Setter (Lombok)
- ✅ Policy fields (businessType, parentCompanyId, relationshipType)

---

## 🔐 Policy Data Management

### Managed Tables

Company-Service yönettiği policy tables:

1. **policy_registry** - Platform policy definitions (52 seed data)
2. **policy_decision_audit** - Authorization decision logs
3. **user_permissions** - User-specific grants (ALLOW/DENY)

### Policy Endpoints

**Policy Audit (Read-Only):**

```
GET /api/v1/policy-audit/user/{userId}      - User audit logs
GET /api/v1/policy-audit/denials            - Deny decisions
GET /api/v1/policy-audit/stats              - Statistics
GET /api/v1/policy-audit/trace/{correlationId}  - Trace by correlation
```

**User Permissions (CRUD):**

```
POST   /api/v1/user-permissions             - Create permission
GET    /api/v1/user-permissions/user/{userId}  - Get user permissions
GET    /api/v1/user-permissions/{id}        - Get permission
DELETE /api/v1/user-permissions/{id}        - Delete permission
```

### Policy Services

- `PolicyAuditQueryService` [83 lines] - Read-only audit queries
- `UserPermissionService` [95 lines] - Permission management

### Why Company-Service?

**Reasoning:**

```
⚠️ Pragmatic decision (not ideal DDD)
- Policy data merkezi bir yerde olmalı
- Diğer servisler Feign client ile erişir
- Şu anda volume düşük, ayrı service gerekmez

Gelecek: Volume artarsa → Policy-Service oluşturulabilir
```

**📖 Detaylı analiz:** [POLICY_ARCHITECTURE_ANALYSIS.md](../../POLICY_ARCHITECTURE_ANALYSIS.md)

---

## 🎯 Key Features

### 1. Duplicate Detection

**Stratejiler:**

- Exact match (Tax ID, Registration Number)
- Fuzzy matching (Company name - PostgreSQL trigram)
- Autocomplete search (search-as-you-type)

```java
// Endpoint
POST /api/v1/companies/check-duplicate

// Response
{
  "isDuplicate": true,
  "matchedCompanyName": "ABC Tekstil A.Ş.",
  "confidence": 0.85,
  "recommendation": "Similar company found. Please verify..."
}
```

### 2. Company Type Hierarchy

```
INTERNAL (Us)
    ├─> CUSTOMER (Müşteriler)
    ├─> SUPPLIER (Tedarikçiler)
    └─> SUBCONTRACTOR (Alt yükleniciler)
```

**Business Rules:**

- ✅ INTERNAL başka company create edebilir
- ❌ CUSTOMER/SUPPLIER company create EDEMEZ (TODO: enforce!)

---

## 📊 API Endpoints

### Company Management

| Method | Endpoint                            | Auth          | Description    |
| ------ | ----------------------------------- | ------------- | -------------- |
| POST   | `/api/v1/companies`                 | ADMIN         | Create company |
| GET    | `/api/v1/companies/{id}`            | Authenticated | Get company    |
| GET    | `/api/v1/companies`                 | Authenticated | List companies |
| PUT    | `/api/v1/companies/{id}`            | ADMIN         | Update company |
| DELETE | `/api/v1/companies/{id}`            | SUPER_ADMIN   | Delete company |
| POST   | `/api/v1/companies/{id}/activate`   | SUPER_ADMIN   | Activate       |
| POST   | `/api/v1/companies/{id}/deactivate` | SUPER_ADMIN   | Deactivate     |

### Duplicate Detection

| Method | Endpoint                                   | Description            |
| ------ | ------------------------------------------ | ---------------------- |
| POST   | `/api/v1/companies/check-duplicate`        | Check for duplicates   |
| GET    | `/api/v1/companies/autocomplete?q={query}` | Autocomplete search    |
| GET    | `/api/v1/companies/similar?name={name}`    | Find similar companies |

---

## 🔧 Configuration

```yaml
# application.yml
server:
  port: 8083

# Duplicate Detection
duplicate-detection:
  fuzzy-search-min-length: 3
  autocomplete-min-length: 2
  autocomplete-max-results: 10
  database-search-threshold: 0.3
  enable-full-text-search: true
```

---

## 📖 Related Documentation

- [Policy Authorization](../development/POLICY_AUTHORIZATION.md) - Policy system
- [Policy Usage Analysis](../../POLICY_USAGE_ANALYSIS_AND_RECOMMENDATIONS.md) - Policy integration guide
- [Company Refactoring](../../COMPANY_SERVICE_REFACTORING_COMPLETE.md) - Refactoring report
- [Code Structure](../development/code_structure_guide.md) - Coding standards

---

**Last Updated:** 2025-10-10  
**Version:** 2.0 (Post-Refactoring)  
**Status:** ✅ Production Ready  
**LOC:** 567 lines (Entity: 109, Service: 281, Mappers: 173)  
**Special:** Policy Data Hub (PolicyRegistry, UserPermission, Audit)

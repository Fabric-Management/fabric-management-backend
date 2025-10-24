# 🏢 COMPANY MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/platform/company`  
**Dependencies:** `common/infrastructure/persistence`, `common/infrastructure/events`

---

## 🎯 MODULE PURPOSE

Company module, **Şirket/Tenant Yönetimi** işlemlerini gerçekleştirir.

### **Core Responsibilities**

- ✅ **Company CRUD** - Create, Read, Update, Delete
- ✅ **Multi-Tenant Management** - Tenant isolation
- ✅ **Company Hierarchy** - Parent-child relationships
- ✅ **Department Management** - Department structure
- ✅ **Subscription Management** - OS subscription tracking ([See SUBSCRIPTION_MANAGEMENT.md](./SUBSCRIPTION_MANAGEMENT.md))
- ✅ **Commercial Relationships** - Fason, supplier agreements

### **Sub-Modules**

- 📋 [SUBSCRIPTION_MANAGEMENT.md](./SUBSCRIPTION_MANAGEMENT.md) - OS subscription lifecycle & management

---

## 🧱 MODULE STRUCTURE

```
company/
├─ api/
│  ├─ controller/
│  │  └─ CompanyController.java
│  └─ facade/
│     └─ CompanyFacade.java
├─ app/
│  ├─ CompanyService.java
│  └─ DepartmentService.java
├─ domain/
│  ├─ Company.java                  # UUID + tenant_id + uid
│  ├─ Department.java
│  ├─ Subscription.java             # OS subscription
│  ├─ CommercialRelationship.java   # Fason agreements
│  └─ event/
│     ├─ CompanyCreatedEvent.java
│     ├─ CompanyUpdatedEvent.java
│     ├─ DepartmentCreatedEvent.java
│     └─ SubscriptionActivatedEvent.java
├─ infra/
│  └─ repository/
│     ├─ CompanyRepository.java
│     ├─ DepartmentRepository.java
│     ├─ SubscriptionRepository.java
│     └─ CommercialRelationshipRepository.java
└─ dto/
   ├─ CompanyDto.java
   ├─ CreateCompanyRequest.java
   ├─ UpdateCompanyRequest.java
   ├─ DepartmentDto.java
   └─ SubscriptionDto.java
```

---

## 📋 DOMAIN MODELS

### **Company Entity**

```java
@Entity
@Table(name = "common_company", schema = "common_company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company extends BaseEntity {

    // Identity (from BaseEntity)
    // - UUID id
    // - UUID tenantId (for multi-tenant hierarchy)
    // - String uid (e.g., "ACME-001")

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false, unique = true)
    private String taxId; // Vergi numarası

    @Column
    private String address;

    @Column
    private String city;

    @Column
    private String country;

    @Column
    private String phoneNumber;

    @Column
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CompanyType companyType = CompanyType.MANUFACTURER; // MANUFACTURER, SUPPLIER, FASON, CUSTOMER

    @Column
    private UUID parentCompanyId; // Parent-child relationship

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Business methods
    public static Company create(String companyName, String taxId, CompanyType companyType) {
        return Company.builder()
            .companyName(companyName)
            .taxId(taxId)
            .companyType(companyType)
            .isActive(true)
            .build();
    }

    public void setParentCompany(UUID parentCompanyId) {
        this.parentCompanyId = parentCompanyId;
    }
}
```

### **Department Entity**

```java
@Entity
@Table(name = "common_department", schema = "common_company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department extends BaseEntity {

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String departmentName; // production, planning, finance, etc.

    @Column
    private String description;

    @Column
    private UUID managerId; // Department manager

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
```

### **Subscription Entity**

```java
@Entity
@Table(name = "common_subscription", schema = "common_company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseEntity {

    @Column(nullable = false)
    private String osCode; // YarnOS, LoomOS, PlanOS, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus subscriptionStatus; // ACTIVE, TRIAL, EXPIRED, CANCELLED, SUSPENDED

    @Column(nullable = false)
    private Instant startDate;

    @Column
    private Instant expiryDate;

    @Column
    private Instant trialEndsAt;

    @Column(columnDefinition = "JSONB")
    private Map<String, Boolean> features; // OS-specific feature toggles

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PricingTier pricingTier; // FREE, BASIC, PROFESSIONAL, ENTERPRISE
}
```

---

## 🔗 ENDPOINTS

| Endpoint                                   | Method | Purpose            | Auth Required        |
| ------------------------------------------ | ------ | ------------------ | -------------------- |
| `/api/common/companies`                    | GET    | List companies     | ✅ Yes               |
| `/api/common/companies/{id}`               | GET    | Get company by ID  | ✅ Yes               |
| `/api/common/companies`                    | POST   | Create company     | ✅ Yes (SUPER_ADMIN) |
| `/api/common/companies/{id}`               | PUT    | Update company     | ✅ Yes (ADMIN)       |
| `/api/common/companies/{id}/departments`   | GET    | List departments   | ✅ Yes               |
| `/api/common/companies/{id}/subscriptions` | GET    | List subscriptions | ✅ Yes (ADMIN)       |
| `/api/common/companies/{id}/subscriptions` | POST   | Add subscription   | ✅ Yes (ADMIN)       |

---

## 🔄 EVENTS

| Event                        | Trigger                | Listeners                               |
| ---------------------------- | ---------------------- | --------------------------------------- |
| `CompanyCreatedEvent`        | Company created        | All modules, Audit, Analytics           |
| `CompanyUpdatedEvent`        | Company updated        | Audit, Analytics                        |
| `DepartmentCreatedEvent`     | Department created     | Audit, User (assign users)              |
| `SubscriptionActivatedEvent` | Subscription activated | Policy (invalidate cache), Notification |

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

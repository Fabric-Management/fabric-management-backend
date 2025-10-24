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
    private CompanyType companyType = CompanyType.VERTICAL_MILL;

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

    public boolean isTenant() {
        return this.companyType.isTenant();
    }

    public CompanyCategory getCategory() {
        return this.companyType.getCategory();
    }

    public String[] getSuggestedOS() {
        return this.companyType.getSuggestedOS();
    }
}
```

### **CompanyType Enum (22 Types)**

```java
// TENANT COMPANIES (Platform Users - 6 types)
SPINNER              → İplikçi (OS: SpinnerOS, YarnOS)
WEAVER               → Dokumacı (OS: WeaverOS, LoomOS)
KNITTER              → Örücü (OS: KnitterOS)
DYER_FINISHER        → Boyahane/Terbiye (OS: DyeOS, FinishOS)
VERTICAL_MILL        → Entegre Tesis (OS: FabricOS - all modules)
GARMENT_MANUFACTURER → Konfeksiyon (OS: GarmentOS)

// SUPPLIER COMPANIES (Material Suppliers - 6 types)
FIBER_SUPPLIER       → Elyaf tedarikçisi
YARN_SUPPLIER        → İplik tedarikçisi
CHEMICAL_SUPPLIER    → Kimyasal tedarikçisi
CONSUMABLE_SUPPLIER  → Sarf malzeme (yağ, iğne, makine parçası)
PACKAGING_SUPPLIER   → Ambalaj tedarikçisi
MACHINE_SUPPLIER     → Makine tedarikçisi (Dornier, Monforts, Mayer)

// SERVICE PROVIDER COMPANIES (7 types)
LOGISTICS_PROVIDER   → Kargo, depo, gümrükleme
MAINTENANCE_SERVICE  → Makine bakım, teknik servis
IT_SERVICE_PROVIDER  → ERP, yazılım, network
KITCHEN_SUPPLIER     → Mutfak, kantin, hijyen
HR_SERVICE_PROVIDER  → İK danışmanlık, işe alım
LAB                  → Test, kalite kontrol, Ar-Ge
UTILITY_PROVIDER     → Elektrik, su, doğalgaz

// PARTNER COMPANIES (4 types)
FASON                → Fason üretim
AGENT                → Aracı, komisyoncu
TRADER               → Tüccar (al-sat)
FINANCE_PARTNER      → Banka, leasing, sigorta

// CUSTOMER COMPANIES (1 type)
CUSTOMER             → Son müşteri
```

### **CompanyCategory Enum**

```java
TENANT           → Platform kullanıcıları (kendi sistemleri var)
SUPPLIER         → Malzeme tedarikçileri
SERVICE_PROVIDER → Hizmet sağlayıcılar
PARTNER          → İş ortakları
CUSTOMER         → Müşteriler
```

### **Smart Methods**

```java
// Check if company can be platform tenant
boolean isTenant = companyType.isTenant();
// SPINNER, WEAVER, KNITTER, DYER_FINISHER, VERTICAL_MILL, GARMENT_MANUFACTURER → true
// Others → false

// Get category for grouping
CompanyCategory category = companyType.getCategory();
// Returns: TENANT, SUPPLIER, SERVICE_PROVIDER, PARTNER, or CUSTOMER

// Get suggested OS subscriptions
String[] suggestedOS = companyType.getSuggestedOS();
// SPINNER → ["SpinnerOS", "YarnOS"]
// VERTICAL_MILL → ["FabricOS"]
// CHEMICAL_SUPPLIER → [] (not a tenant)
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

| Endpoint                                                    | Method | Purpose               | Auth Required        |
| ----------------------------------------------------------- | ------ | --------------------- | -------------------- |
| `/api/common/companies`                                     | GET    | List all companies    | ✅ Yes               |
| `/api/common/companies/tenants`                             | GET    | List tenant companies | ✅ Yes               |
| `/api/common/companies/type/{type}`                         | GET    | List by type          | ✅ Yes               |
| `/api/common/companies/{id}`                                | GET    | Get company by ID     | ✅ Yes               |
| `/api/common/companies`                                     | POST   | Create company        | ✅ Yes (SUPER_ADMIN) |
| `/api/common/companies/{id}`                                | PUT    | Update company        | ✅ Yes (ADMIN)       |
| `/api/common/companies/{id}`                                | DELETE | Deactivate company    | ✅ Yes (ADMIN)       |
| `/api/common/companies/{id}/departments`                    | GET    | List departments      | ✅ Yes               |
| `/api/common/companies/{id}/subscriptions`                  | GET    | List subscriptions    | ✅ Yes (ADMIN)       |
| `/api/common/companies/{id}/subscriptions`                  | POST   | Add subscription      | ✅ Yes (ADMIN)       |
| `/api/common/companies/{id}/subscriptions/{subId}/activate` | POST   | Activate subscription | ✅ Yes (ADMIN)       |

---

## 🔄 EVENTS

| Event                        | Trigger                | Listeners                               |
| ---------------------------- | ---------------------- | --------------------------------------- |
| `CompanyCreatedEvent`        | Company created        | All modules, Audit, Analytics           |
| `CompanyUpdatedEvent`        | Company updated        | Audit, Analytics                        |
| `DepartmentCreatedEvent`     | Department created     | Audit, User (assign users)              |
| `SubscriptionActivatedEvent` | Subscription activated | Policy (invalidate cache), Notification |

---

## 💡 USAGE EXAMPLES

### **Create Tenant Company (Platform User)**

```java
CreateCompanyRequest request = CreateCompanyRequest.builder()
    .companyName("ACME İplik A.Ş.")
    .taxId("1234567890")
    .companyType(CompanyType.SPINNER) // Can be platform tenant
    .city("Istanbul")
    .country("Turkey")
    .build();

CompanyDto company = companyService.createCompany(request);
// company.isTenant() = true
// company.getSuggestedOS() = ["SpinnerOS", "YarnOS"]
```

### **Create Supplier Company**

```java
CreateCompanyRequest request = CreateCompanyRequest.builder()
    .companyName("Global Chemicals Ltd.")
    .taxId("9876543210")
    .companyType(CompanyType.CHEMICAL_SUPPLIER)
    .build();

CompanyDto supplier = companyService.createCompany(request);
// supplier.isTenant() = false (cannot use platform)
// supplier.getCategory() = SUPPLIER
```

### **Query by Category**

```java
// Get only tenant companies (platform users)
List<CompanyDto> tenants = companyService.getTenantCompanies();
// Returns: SPINNER, WEAVER, KNITTER, DYER_FINISHER, VERTICAL_MILL, GARMENT_MANUFACTURER

// Get specific type
List<CompanyDto> spinners = companyService.getCompaniesByType(CompanyType.SPINNER);

// Get all suppliers
List<CompanyDto> suppliers = companyService.findByCategory(CompanyCategory.SUPPLIER);
```

---

**Last Updated:** 2025-10-24  
**Maintained By:** Fabric Management Team  
**Latest Update:** Added 22 company types with category classification

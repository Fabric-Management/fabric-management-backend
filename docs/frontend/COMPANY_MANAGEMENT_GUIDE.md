# Company Management Guide - Frontend Integration

**Last Updated:** 2025-01-10  
**Status:** Active  
**Purpose:** Guide for frontend developers working with Company entity, Subscriptions, and related features.

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Company Entity Structure](#company-entity-structure)
3. [Company Types](#company-types)
4. [API Endpoints](#api-endpoints)
5. [Contacts & Addresses](#contacts--addresses)
6. [Subscriptions](#subscriptions)
7. [Code Examples](#code-examples)
8. [Common Patterns](#common-patterns)

---

## 🎯 Overview

### What is Company?

Company is the **primary tenant boundary** in the multi-tenant system. Each company:

- Has its own data isolation via `tenant_id`
- Can have multiple OS subscriptions
- Can have departments and users
- Can have parent-child relationships
- Can have commercial relationships (fason agreements)

### Key Features

✅ **Multi-Tenant Isolation** - Each company has separate data  
✅ **Hierarchical Structure** - Parent-child company relationships  
✅ **Multiple Company Types** - Tenant, Supplier, Service Provider, Partner, Customer  
✅ **OS Subscriptions** - Each company can subscribe to multiple Operating Systems  
✅ **Centralized Contacts/Addresses** - Via Communication module  

### Special Case: ROOT Tenant

For **ROOT tenant companies** (platform users), `tenant_id = company_id` (self-referencing). This is the ONLY entity where `tenant_id` is updatable.

---

## 📊 Company Entity Structure

### CompanyDto

```typescript
interface CompanyDto {
  id: string;                    // UUID
  tenantId: string;              // UUID - For ROOT tenants, equals id
  uid: string;                   // Auto-generated (e.g., "ACME-001")
  companyName: string;            // Required
  taxId: string;                 // Required, unique
  companyType: CompanyType;       // Enum - See Company Types section
  parentCompanyId?: string;        // UUID - For hierarchical companies
  isActive: boolean;
  isTenant: boolean;              // Computed - true if companyType.isTenant()
  createdAt: string;              // ISO 8601
  updatedAt: string;              // ISO 8601
}
```

### Key Points

- **No contacts/addresses in DTO** - Fetch via `/api/common/companies/{companyId}/contacts` and `/api/common/companies/{companyId}/addresses`
- **`isTenant`** - Computed field, true for tenant company types
- **`tenantId`** - For ROOT tenants, this equals `id` (self-referencing)
- **`parentCompanyId`** - Optional, for hierarchical company structures

---

## 🏢 Company Types

### Overview

Company types are **enums** with categories and metadata. Each type belongs to a category and may suggest OS subscriptions.

### Company Categories

- **TENANT** - Platform users (can use the system)
- **SUPPLIER** - Material suppliers
- **SERVICE_PROVIDER** - Service providers
- **PARTNER** - Business partners
- **CUSTOMER** - Customers

### Tenant Company Types

These can use the platform:

1. **SPINNER** - Yarn producer (İplikçi)
   - OS: SpinnerOS, YarnOS

2. **WEAVER** - Weaving producer (Dokumacı)
   - OS: WeaverOS, LoomOS

3. **KNITTER** - Knitting producer (Örücü)
   - OS: KnitterOS, KnitOS

4. **DYER_FINISHER** - Dyeing & Finishing plant (Boyahane/Terbiye)
   - OS: DyeOS, FinishOS

5. **VERTICAL_MILL** - Vertical integrated mill (Entegre Tesis)
   - OS: FabricOS (complete package)

6. **GARMENT_MANUFACTURER** - Garment manufacturer (Konfeksiyon)
   - OS: GarmentOS

### Supplier Company Types

- FIBER_SUPPLIER
- YARN_SUPPLIER
- CHEMICAL_SUPPLIER
- CONSUMABLE_SUPPLIER
- PACKAGING_SUPPLIER
- MACHINE_SUPPLIER

### Service Provider Types

- LOGISTICS_PROVIDER
- MAINTENANCE_SERVICE
- IT_SERVICE_PROVIDER
- KITCHEN_SUPPLIER
- HR_SERVICE_PROVIDER
- LAB
- UTILITY_PROVIDER

### Partner Types

- FASON
- AGENT
- TRADER
- FINANCE_PARTNER

### Customer Types

- CUSTOMER

---

## 📡 API Endpoints

### Base URL

All company endpoints: `/api/common/companies`

---

### Company CRUD Endpoints

#### Get Company by ID
```http
GET /api/common/companies/{companyId}
Authorization: Bearer {token}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "tenantId": "uuid",
    "uid": "ACME-001",
    "companyName": "ACME Corporation",
    "taxId": "1234567890",
    "companyType": "VERTICAL_MILL",
    "parentCompanyId": null,
    "isActive": true,
    "isTenant": true,
    "createdAt": "2025-01-10T10:00:00Z",
    "updatedAt": "2025-01-10T10:00:00Z"
  }
}
```

#### Get All Companies (Tenant)
```http
GET /api/common/companies
Authorization: Bearer {token}
```

**Response:** Array of `CompanyDto`

**Note:** Returns only companies within the current tenant scope.

#### Get Tenant Companies Only
```http
GET /api/common/companies/tenants
Authorization: Bearer {token}
```

**Response:** Array of `CompanyDto` (filtered to `isTenant: true`)

#### Get Companies by Type
```http
GET /api/common/companies/type/{type}
Authorization: Bearer {token}
```

**Example:** `GET /api/common/companies/type/SPINNER`

**Response:** Array of `CompanyDto` filtered by company type

#### Create Company
```http
POST /api/common/companies
Content-Type: application/json
Authorization: Bearer {token}

{
  "companyName": "ACME Corporation",
  "taxId": "1234567890",
  "companyType": "VERTICAL_MILL",
  "parentCompanyId": null,          // Optional
  "address": "123 Business St",    // Optional - deprecated (use Communication module)
  "city": "Istanbul",               // Optional - deprecated
  "country": "Turkey",               // Optional - deprecated
  "phoneNumber": "+905551234567",    // Optional - deprecated
  "email": "info@acme.com"           // Optional - deprecated
}
```

**Response:** `CompanyDto` with created company

**Note:** 
- Address/contact fields are deprecated. Use Communication module endpoints after creation.
- For tenant companies, consider using onboarding endpoints (`/api/admin/onboarding/tenant` or `/api/public/signup`)

#### Deactivate Company
```http
DELETE /api/common/companies/{companyId}
Authorization: Bearer {token}
```

**Response:**
```json
{
  "success": true,
  "message": "Company deactivated successfully"
}
```

---

### Company Type Endpoints (Public)

#### Get All Company Types
```http
GET /api/common/companies/types
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "name": "SPINNER",
      "displayName": "Spinner",
      "category": "TENANT",
      "isTenant": true,
      "suggestedOS": ["SpinnerOS", "YarnOS"],
      "description": "Yarn producer (İplikçi)"
    }
  ]
}
```

**Note:** Public endpoint - no authentication required. Useful for signup forms.

#### Get Tenant Company Types Only
```http
GET /api/common/companies/types/tenant
```

**Response:** Array of `CompanyTypeDto` (filtered to `isTenant: true`)

#### Get Company Types by Category
```http
GET /api/common/companies/types/category/{category}
```

**Example:** `GET /api/common/companies/types/category/TENANT`

**Categories:** `TENANT`, `SUPPLIER`, `SERVICE_PROVIDER`, `PARTNER`, `CUSTOMER`

---

### Subscription Endpoints

#### Get Company Subscriptions
```http
GET /api/common/companies/{companyId}/subscriptions
Authorization: Bearer {token}
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "osCode": "FabricOS",
      "subscriptionStatus": "ACTIVE",
      "pricingTier": "PROFESSIONAL",
      "startDate": "2025-01-01T00:00:00Z",
      "expiryDate": "2026-01-01T00:00:00Z",
      "trialEndsAt": "2025-01-31T00:00:00Z",
      "features": {
        "fabric.management": true,
        "analytics": true
      }
    }
  ]
}
```

#### Activate Subscription
```http
POST /api/common/companies/{companyId}/subscriptions/{subscriptionId}/activate
Authorization: Bearer {token}
```

**Response:** `SubscriptionDto` with activated subscription

---

## 📞 Contacts & Addresses

### Overview

Company contacts and addresses are managed via the **Communication module**. See `COMMUNICATION_SYSTEM_GUIDE.md` for details.

### Quick Reference

#### Get Company Contacts
```http
GET /api/common/companies/{companyId}/contacts
```

#### Get Default Contact
```http
GET /api/common/companies/{companyId}/contacts/default
```

#### Get Department Contacts
```http
GET /api/common/companies/{companyId}/contacts/department/{departmentName}
```

#### Get Company Addresses
```http
GET /api/common/companies/{companyId}/addresses
```

#### Get Primary Address
```http
GET /api/common/companies/{companyId}/addresses/primary
```

#### Get Headquarters
```http
GET /api/common/companies/{companyId}/addresses/headquarters
```

**See:** `docs/frontend/COMMUNICATION_SYSTEM_GUIDE.md` for complete details.

---

## 💻 Code Examples

### Example 1: Fetch Company with Full Details

```typescript
async function getCompanyWithDetails(companyId: string) {
  const [company, contacts, addresses, subscriptions] = await Promise.all([
    api.get(`/api/common/companies/${companyId}`),
    api.get(`/api/common/companies/${companyId}/contacts`),
    api.get(`/api/common/companies/${companyId}/addresses`),
    api.get(`/api/common/companies/${companyId}/subscriptions`)
  ]);

  return {
    company: company.data.data,
    contacts: contacts.data.data,
    addresses: addresses.data.data,
    subscriptions: subscriptions.data.data
  };
}
```

### Example 2: Create Company with Address/Contact

```typescript
async function createCompanyWithDetails(companyData: {
  companyName: string;
  taxId: string;
  companyType: CompanyType;
  address?: {
    streetAddress: string;
    city: string;
    country: string;
  };
  contact?: {
    email?: string;
    phone?: string;
  };
}) {
  // Step 1: Create company
  const companyResponse = await api.post('/api/common/companies', {
    companyName: companyData.companyName,
    taxId: companyData.taxId,
    companyType: companyData.companyType
  });

  const company = companyResponse.data.data;

  // Step 2: Add address if provided
  if (companyData.address) {
    await api.post(
      `/api/common/companies/${company.id}/addresses/create-and-assign`,
      {
        streetAddress: companyData.address.streetAddress,
        city: companyData.address.city,
        country: companyData.address.country,
        addressType: 'HEADQUARTERS',
        label: 'Headquarters'
      },
      {
        params: {
          isPrimary: true,
          isHeadquarters: true
        }
      }
    );
  }

  // Step 3: Add contacts if provided
  if (companyData.contact?.email) {
    await api.post(
      `/api/common/companies/${company.id}/contacts/create-and-assign`,
      {
        contactValue: companyData.contact.email,
        contactType: 'EMAIL',
        label: 'Main Email',
        isPersonal: false
      },
      {
        params: {
          isDefault: true
        }
      }
    );
  }

  if (companyData.contact?.phone) {
    await api.post(
      `/api/common/companies/${company.id}/contacts/create-and-assign`,
      {
        contactValue: companyData.contact.phone,
        contactType: 'PHONE',
        label: 'Main Phone',
        isPersonal: false
      },
      {
        params: {
          isDefault: false
        }
      }
    );
  }

  return company;
}
```

### Example 3: Company Type Selector

```typescript
import { useState, useEffect } from 'react';
import axios from 'axios';

interface CompanyTypeSelectorProps {
  value?: string;
  onChange: (companyType: CompanyType) => void;
  tenantOnly?: boolean;
}

function CompanyTypeSelector({ value, onChange, tenantOnly = false }: CompanyTypeSelectorProps) {
  const [types, setTypes] = useState<CompanyTypeDto[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const endpoint = tenantOnly 
      ? '/api/common/companies/types/tenant'
      : '/api/common/companies/types';

    axios.get(endpoint)
      .then(res => {
        setTypes(res.data.data);
        setLoading(false);
      })
      .catch(err => {
        console.error('Failed to fetch company types:', err);
        setLoading(false);
      });
  }, [tenantOnly]);

  if (loading) return <div>Loading company types...</div>;

  return (
    <select value={value || ''} onChange={(e) => onChange(e.target.value as CompanyType)}>
      <option value="">Select Company Type</option>
      {types.map(type => (
        <option key={type.name} value={type.name}>
          {type.displayName} ({type.category})
        </option>
      ))}
    </select>
  );
}
```

### Example 4: Company Profile Component

```typescript
function CompanyProfile({ companyId }: { companyId: string }) {
  const [company, setCompany] = useState<CompanyDto | null>(null);
  const [contacts, setContacts] = useState<CompanyContactDto[]>([]);
  const [addresses, setAddresses] = useState<CompanyAddressDto[]>([]);
  const [subscriptions, setSubscriptions] = useState<SubscriptionDto[]>([]);

  useEffect(() => {
    // Fetch company
    axios.get(`/api/common/companies/${companyId}`)
      .then(res => setCompany(res.data.data));

    // Fetch contacts
    axios.get(`/api/common/companies/${companyId}/contacts`)
      .then(res => setContacts(res.data.data));

    // Fetch addresses
    axios.get(`/api/common/companies/${companyId}/addresses`)
      .then(res => setAddresses(res.data.data));

    // Fetch subscriptions
    axios.get(`/api/common/companies/${companyId}/subscriptions`)
      .then(res => setSubscriptions(res.data.data));
  }, [companyId]);

  if (!company) return <div>Loading...</div>;

  const defaultContact = contacts.find(c => c.isDefault);
  const headquarters = addresses.find(a => a.isHeadquarters);
  const primaryAddress = addresses.find(a => a.isPrimary);

  return (
    <div>
      <h1>{company.companyName}</h1>
      <p>Type: {company.companyType}</p>
      <p>Tax ID: {company.taxId}</p>
      <p>UID: {company.uid}</p>
      <p>Is Tenant: {company.isTenant ? 'Yes' : 'No'}</p>

      <h2>Contact Information</h2>
      <p>Default: {defaultContact?.contact.contactValue || 'N/A'}</p>

      <h2>Addresses</h2>
      <p>Headquarters: {headquarters?.address.formattedAddress || 'N/A'}</p>
      <p>Primary: {primaryAddress?.address.formattedAddress || 'N/A'}</p>

      <h2>Subscriptions</h2>
      <ul>
        {subscriptions.map(sub => (
          <li key={sub.id}>
            {sub.osCode} - {sub.subscriptionStatus} ({sub.pricingTier})
          </li>
        ))}
      </ul>
    </div>
  );
}
```

### Example 5: Check if Company Type is Tenant

```typescript
async function isTenantCompanyType(companyType: CompanyType): Promise<boolean> {
  const response = await axios.get('/api/common/companies/types');
  const types = response.data.data as CompanyTypeDto[];
  
  const type = types.find(t => t.name === companyType);
  return type?.isTenant || false;
}

// Or use the enum directly (if available)
function isTenantType(companyType: CompanyType): boolean {
  // Check tenant types
  return [
    'SPINNER',
    'WEAVER',
    'KNITTER',
    'DYER_FINISHER',
    'VERTICAL_MILL',
    'GARMENT_MANUFACTURER'
  ].includes(companyType);
}
```

---

## 🎨 Common Patterns

### Pattern 1: Company Display Helper

```typescript
function getCompanyDisplayInfo(company: CompanyDto): {
  name: string;
  type: string;
  isTenant: boolean;
  uid: string;
} {
  return {
    name: company.companyName,
    type: company.companyType,
    isTenant: company.isTenant,
    uid: company.uid
  };
}
```

### Pattern 2: Get Suggested OS for Company Type

```typescript
async function getSuggestedOS(companyType: CompanyType): Promise<string[]> {
  const response = await axios.get('/api/common/companies/types');
  const types = response.data.data as CompanyTypeDto[];
  
  const type = types.find(t => t.name === companyType);
  return type?.suggestedOS || [];
}
```

### Pattern 3: Filter Companies by Category

```typescript
async function getCompaniesByCategory(category: CompanyCategory): Promise<CompanyDto[]> {
  // First get company types in this category
  const typesResponse = await axios.get(
    `/api/common/companies/types/category/${category}`
  );
  const types = typesResponse.data.data as CompanyTypeDto[];
  
  // Then fetch companies for each type
  const allCompanies: CompanyDto[] = [];
  for (const type of types) {
    const companiesResponse = await axios.get(
      `/api/common/companies/type/${type.name}`
    );
    allCompanies.push(...companiesResponse.data.data);
  }
  
  return allCompanies;
}
```

---

## ⚡ Best Practices

### 1. Use Company Type Endpoints for Dropdowns

Don't hardcode company types. Use `/api/common/companies/types` endpoint to get all available types with metadata.

### 2. Handle Tenant vs Non-Tenant Companies

Tenant companies can use the platform, while suppliers/partners are for relationship management only.

### 3. Fetch Contacts/Addresses Separately

Company DTO doesn't include contacts/addresses. Fetch them via Communication module endpoints.

### 4. Cache Company Types

Company types don't change frequently. Fetch once on app startup and cache.

### 5. Validate Parent Company

When creating a company with `parentCompanyId`, ensure:
- Parent exists
- Parent belongs to same tenant
- Parent is active
- No circular references

---

## 🐛 Error Handling

### Common Errors

#### Company Not Found
```json
{
  "success": false,
  "error": "COMPANY_NOT_FOUND",
  "message": "Company not found"
}
```

#### Tax ID Already Exists
```json
{
  "success": false,
  "error": "TAX_ID_EXISTS",
  "message": "Company with this tax ID already exists in your organization"
}
```

#### Invalid Company Type
```json
{
  "success": false,
  "error": "VALIDATION_ERROR",
  "message": "Invalid company type"
}
```

### Error Handling Example

```typescript
try {
  await createCompany(companyData);
} catch (error: any) {
  if (error.response?.status === 404) {
    // Handle not found
  } else if (error.response?.data?.error === 'TAX_ID_EXISTS') {
    // Handle tax ID exists
  } else {
    // Handle generic error
  }
}
```

---

## 📚 Additional Resources

- **Communication System:** `docs/frontend/COMMUNICATION_SYSTEM_GUIDE.md`
- **User Management:** `docs/frontend/USER_MANAGEMENT_GUIDE.md`
- **Backend API Docs:** Swagger UI at `/swagger-ui.html`

---

## ❓ FAQ

### Q: What's the difference between tenant and non-tenant companies?
**A:** Tenant companies can use the platform (have subscriptions, users, etc.). Non-tenant companies are for relationship management (suppliers, partners, customers).

### Q: Can a company have multiple subscriptions?
**A:** Yes. A company can subscribe to multiple Operating Systems (e.g., FabricOS + AnalyticsOS).

### Q: How do I check if a company is a tenant?
**A:** Use the `isTenant` field in `CompanyDto` or check if `companyType.isTenant()` returns true.

### Q: Can I create a tenant company via normal endpoint?
**A:** Technically yes, but it's recommended to use onboarding endpoints (`/api/admin/onboarding/tenant` or `/api/public/signup`) for proper tenant setup.

### Q: How do I handle parent-child company relationships?
**A:** Set `parentCompanyId` when creating a company. Ensure the parent exists, is active, and belongs to the same tenant.

---

**Questions?** Contact backend team or check Swagger documentation.


# 🏢 Tenant & Company Form Rehberi

## 📋 Genel Bakış

Bu dokümantasyon, tenant ve company ilişkili bir form oluştururken kullanılacak endpoint'leri ve akışları açıklar.

### 🔑 Önemli Notlar

- **Tenant = Company**: Bu sistemde her tenant bir company'dir. `tenant_id = company_id` (root tenant için)
- **Multi-Tenant**: Her company kendi tenant_id'si ile izole edilmiştir
- **Company Types**: Tenant olabilen ve olamayan company tipleri vardır

---

## 🎯 Senaryolar ve Endpoint'ler

### Senaryo 1: Yeni Tenant Company Oluşturma (Self-Service Signup)

**Kullanım:** Kullanıcı kendi şirketini kaydetmek istiyor

#### Adım 1: Company Types'ı Getir (Form için dropdown)

```http
GET /api/common/companies/types/tenant
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
      "suggestedOS": ["SpinnerOS", "YarnOS"]
    },
    {
      "name": "WEAVER",
      "displayName": "Weaver",
      "category": "TENANT",
      "isTenant": true,
      "suggestedOS": ["WeaverOS", "LoomOS"]
    },
    {
      "name": "VERTICAL_MILL",
      "displayName": "Vertical Mill",
      "category": "TENANT",
      "isTenant": true,
      "suggestedOS": ["FabricOS"]
    }
    // ... diğer tenant tipleri
  ]
}
```

**Frontend Kullanımı:**

```javascript
// Form yüklenirken company types'ı çek
useEffect(() => {
  const loadCompanyTypes = async () => {
    const response = await fetch("/api/common/companies/types/tenant");
    const result = await response.json();
    setCompanyTypes(result.data);
  };
  loadCompanyTypes();
}, []);
```

#### Adım 2: Company Oluştur (Contact ve Address ile birlikte)

**Seçenek A: Basit Company Oluşturma**

```http
POST /api/common/companies
Content-Type: application/json

{
  "companyName": "ACME Textiles Inc",
  "taxId": "1234567890",
  "companyType": "WEAVER",
  "parentCompanyId": null  // Optional: eğer alt şirket ise
}
```

**Seçenek B: Company + Contact + Address (Tek İşlemde)**

```http
POST /api/common/companies/with-contact
Content-Type: application/json

{
  "companyName": "ACME Textiles Inc",
  "taxId": "1234567890",
  "companyType": "WEAVER",
  "email": "info@acme.com",
  "phoneNumber": "+905551234567",
  "address": "Ataturk Cad. No:1",
  "city": "Istanbul",
  "state": "Istanbul",
  "postalCode": "34000",
  "country": "Turkey"
}
```

**Response:**

```json
{
  "success": true,
  "message": "Company created successfully",
  "data": {
    "id": "uuid",
    "tenantId": "uuid", // tenant_id = company_id (yeni tenant için)
    "uid": "ACME-001",
    "companyName": "ACME Textiles Inc",
    "taxId": "1234567890",
    "companyType": "WEAVER",
    "isActive": true,
    "isTenant": true,
    "createdAt": "2025-01-27T10:00:00Z"
  }
}
```

**Frontend Kullanımı:**

```javascript
const handleSubmit = async formData => {
  try {
    const response = await fetch("/api/common/companies/with-contact", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        companyName: formData.companyName,
        taxId: formData.taxId,
        companyType: formData.companyType,
        email: formData.email,
        phoneNumber: formData.phoneNumber,
        address: formData.address,
        city: formData.city,
        country: formData.country,
      }),
    });

    const result = await response.json();
    if (result.success) {
      // Company oluşturuldu, tenant_id = result.data.tenantId
      console.log("Tenant ID:", result.data.tenantId);
      console.log("Company ID:", result.data.id);
    }
  } catch (error) {
    console.error("Company creation failed:", error);
  }
};
```

---

### Senaryo 2: Admin Tarafından Tenant Oluşturma (Sales-Led Flow)

**Kullanım:** Platform admin yeni bir tenant oluşturuyor (admin user ile birlikte)

```http
POST /api/admin/onboarding/tenant
Content-Type: application/json
Authorization: Bearer {platform-admin-token}

{
  "companyName": "Global Textiles Ltd",
  "taxId": "9876543210",
  "companyType": "VERTICAL_MILL",
  "address": "Factory Street 123",
  "city": "Bursa",
  "country": "Turkey",
  "phoneNumber": "+902241234567",
  "companyEmail": "info@globaltextiles.com",
  "adminFirstName": "Ahmet",
  "adminLastName": "Yılmaz",
  "adminContact": "ahmet@globaltextiles.com",
  "adminDepartment": "Management",
  "selectedOS": ["FabricOS", "ProductionOS"],
  "trialDays": 90
}
```

**Response:**

```json
{
  "success": true,
  "message": "Tenant created successfully. Welcome email sent to admin.",
  "data": {
    "companyUid": "GLOBAL-001",
    "tenantId": "uuid",
    "companyId": "uuid",
    "adminUserId": "uuid",
    "setupUrl": "https://app.example.com/setup?token=xxx",
    "subscriptions": [
      {
        "osName": "FabricOS",
        "status": "TRIAL",
        "expiresAt": "2025-04-27T10:00:00Z"
      }
    ]
  }
}
```

**Not:** Bu endpoint:

- ✅ Company oluşturur
- ✅ Admin user oluşturur
- ✅ Subscription'ları oluşturur (trial)
- ✅ Welcome email gönderir
- ✅ Setup token oluşturur

---

### Senaryo 3: Mevcut Tenant'ın Company Bilgilerini Görüntüleme

**Kullanım:** Kullanıcı kendi şirketinin bilgilerini görmek istiyor

#### Adım 1: Current User'ın Company ID'sini Al

```http
GET /api/common/users/me
```

**Response'dan:** `companyId` alınır

#### Adım 2: Company Detaylarını Getir

```http
GET /api/common/companies/{companyId}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "tenantId": "uuid",
    "uid": "ACME-001",
    "companyName": "ACME Textiles Inc",
    "taxId": "1234567890",
    "companyType": "WEAVER",
    "isActive": true,
    "isTenant": true
  }
}
```

#### Adım 3: Company Contacts'ı Getir (Opsiyonel)

```http
GET /api/common/companies/{companyId}/contacts
```

#### Adım 4: Company Addresses'ı Getir (Opsiyonel)

```http
GET /api/common/companies/{companyId}/addresses
```

---

### Senaryo 4: Company Bilgilerini Güncelleme

**Kullanım:** Company bilgilerini düzenlemek

```http
PUT /api/common/companies/{companyId}
Content-Type: application/json

{
  "companyName": "ACME Textiles Inc (Updated)",
  "taxId": "1234567890",  // Değiştirilemez (unique constraint)
  "companyType": "VERTICAL_MILL"  // Değiştirilebilir
}
```

**Response:**

```json
{
  "success": true,
  "message": "Company updated successfully",
  "data": {
    "id": "uuid",
    "companyName": "ACME Textiles Inc (Updated)",
    "taxId": "1234567890",
    "companyType": "VERTICAL_MILL",
    "updatedAt": "2025-01-27T11:00:00Z"
  }
}
```

---

### Senaryo 4.1: Company'ye Sonradan Contact ve Address Ekleme

**Kullanım:** Company oluşturulduktan sonra, ek contact ve address bilgileri eklemek

#### Adım 1: Company'ye Contact Ekle

**Seçenek A: Yeni Contact Oluştur ve Ekle**

```http
POST /api/common/companies/{companyId}/contacts/create-and-assign
Content-Type: application/json
Authorization: Bearer {token}

{
  "contactValue": "sales@acme.com",
  "contactType": "EMAIL",
  "label": "Sales Email",
  "isPersonal": false
}
?isDefault=false&department=Sales
```

**Seçenek B: Mevcut Contact'ı Ekle**

```http
POST /api/common/companies/{companyId}/contacts
Content-Type: application/json
Authorization: Bearer {token}

{
  "contactId": "uuid...",  // Mevcut contact'ın ID'si
  "isDefault": false,
  "department": "Sales"  // Opsiyonel: department override
}
```

**Response:**

```json
{
  "success": true,
  "message": "Contact created and assigned successfully",
  "data": {
    "companyId": "uuid...",
    "contactId": "uuid...",
    "contact": {
      "id": "uuid...",
      "contactValue": "sales@acme.com",
      "contactType": "EMAIL",
      "label": "Sales Email",
      "isPersonal": false
    },
    "isDefault": false,
    "department": "Sales"
  }
}
```

#### Adım 2: Company'ye Address Ekle

**Seçenek A: Yeni Address Oluştur ve Ekle**

```http
POST /api/common/companies/{companyId}/addresses/create-and-assign
Content-Type: application/json
Authorization: Bearer {token}

{
  "streetAddress": "Factory Street 123",
  "city": "Bursa",
  "state": "Bursa",
  "postalCode": "16000",
  "country": "Turkey",
  "addressType": "WORK",
  "label": "Factory Address"
}
?isPrimary=false&isHeadquarters=false
```

**Seçenek B: Mevcut Address'ı Ekle**

```http
POST /api/common/companies/{companyId}/addresses
Content-Type: application/json
Authorization: Bearer {token}

{
  "addressId": "uuid...",  // Mevcut address'ın ID'si
  "isPrimary": false,
  "isHeadquarters": false
}
```

**Response:**

```json
{
  "success": true,
  "message": "Address created and assigned successfully",
  "data": {
    "companyId": "uuid...",
    "addressId": "uuid...",
    "address": {
      "id": "uuid...",
      "streetAddress": "Factory Street 123",
      "city": "Bursa",
      "state": "Bursa",
      "postalCode": "16000",
      "country": "Turkey",
      "addressType": "WORK"
    },
    "isPrimary": false,
    "isHeadquarters": false
  }
}
```

#### Adım 3: Company Contact'larını Listele

```http
GET /api/common/companies/{companyId}/contacts
Authorization: Bearer {token}
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "companyId": "uuid...",
      "contactId": "uuid...",
      "contact": {
        "id": "uuid...",
        "contactValue": "info@acme.com",
        "contactType": "EMAIL",
        "label": "Main Email",
        "isPersonal": false
      },
      "isDefault": true,
      "department": null
    },
    {
      "companyId": "uuid...",
      "contactId": "uuid...",
      "contact": {
        "id": "uuid...",
        "contactValue": "sales@acme.com",
        "contactType": "EMAIL",
        "label": "Sales Email",
        "isPersonal": false
      },
      "isDefault": false,
      "department": "Sales"
    }
  ]
}
```

#### Adım 4: Company Address'larını Listele

```http
GET /api/common/companies/{companyId}/addresses
Authorization: Bearer {token}
```

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "companyId": "uuid...",
      "addressId": "uuid...",
      "address": {
        "id": "uuid...",
        "streetAddress": "Ataturk Cad. No:1",
        "city": "Istanbul",
        "state": "Istanbul",
        "postalCode": "34000",
        "country": "Turkey",
        "addressType": "WORK"
      },
      "isPrimary": true,
      "isHeadquarters": true
    },
    {
      "companyId": "uuid...",
      "addressId": "uuid...",
      "address": {
        "id": "uuid...",
        "streetAddress": "Factory Street 123",
        "city": "Bursa",
        "state": "Bursa",
        "postalCode": "16000",
        "country": "Turkey",
        "addressType": "WORK"
      },
      "isPrimary": false,
      "isHeadquarters": false
    }
  ]
}
```

#### Frontend Kullanımı: Company'ye Contact ve Address Ekleme

```jsx
// Company oluşturulduktan sonra contact ve address ekleme
const addContactToCompany = async (companyId, contactData) => {
  try {
    const response = await fetch(
      `/api/common/companies/${companyId}/contacts/create-and-assign?isDefault=false&department=${
        contactData.department || ""
      }`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          contactValue: contactData.email || contactData.phone,
          contactType: contactData.email ? "EMAIL" : "PHONE",
          label: contactData.label || "Contact",
          isPersonal: false,
        }),
      }
    );

    const result = await response.json();
    if (result.success) {
      console.log("Contact added:", result.data);
    }
  } catch (error) {
    console.error("Failed to add contact:", error);
  }
};

const addAddressToCompany = async (companyId, addressData) => {
  try {
    const response = await fetch(
      `/api/common/companies/${companyId}/addresses/create-and-assign?isPrimary=${
        addressData.isPrimary || false
      }&isHeadquarters=${addressData.isHeadquarters || false}`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          streetAddress: addressData.streetAddress,
          city: addressData.city,
          state: addressData.state,
          postalCode: addressData.postalCode,
          country: addressData.country,
          addressType: addressData.addressType || "WORK",
          label: addressData.label || "Address",
        }),
      }
    );

    const result = await response.json();
    if (result.success) {
      console.log("Address added:", result.data);
    }
  } catch (error) {
    console.error("Failed to add address:", error);
  }
};

// Kullanım örneği
const handleAddContactAndAddress = async () => {
  const companyId = "uuid..."; // Yeni oluşturulan company'nin ID'si

  // Contact ekle
  await addContactToCompany(companyId, {
    email: "sales@acme.com",
    label: "Sales Email",
    department: "Sales",
  });

  // Address ekle
  await addAddressToCompany(companyId, {
    streetAddress: "Factory Street 123",
    city: "Bursa",
    state: "Bursa",
    postalCode: "16000",
    country: "Turkey",
    addressType: "WORK",
    isPrimary: false,
    isHeadquarters: false,
  });
};
```

---

## 📝 Form Örneği (React)

```jsx
import { useState, useEffect } from "react";

function TenantCompanyForm() {
  const [formData, setFormData] = useState({
    companyName: "",
    taxId: "",
    companyType: "",
    email: "",
    phoneNumber: "",
    address: "",
    city: "",
    country: "",
  });

  const [companyTypes, setCompanyTypes] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Company types'ı yükle
  useEffect(() => {
    const loadCompanyTypes = async () => {
      try {
        const response = await fetch("/api/common/companies/types/tenant");
        const result = await response.json();
        if (result.success) {
          setCompanyTypes(result.data);
        }
      } catch (err) {
        console.error("Failed to load company types:", err);
      }
    };
    loadCompanyTypes();
  }, []);

  const handleSubmit = async e => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const response = await fetch("/api/common/companies/with-contact", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${localStorage.getItem("token")}`,
        },
        body: JSON.stringify(formData),
      });

      const result = await response.json();

      if (result.success) {
        // Başarılı!
        console.log("Tenant created:", result.data);
        // Redirect veya success message göster
      } else {
        setError(result.message || "Company creation failed");
      }
    } catch (err) {
      setError("Network error. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <h2>Create Tenant Company</h2>

      {error && <div className="error">{error}</div>}

      <div>
        <label>Company Name *</label>
        <input
          type="text"
          value={formData.companyName}
          onChange={e =>
            setFormData({ ...formData, companyName: e.target.value })
          }
          required
        />
      </div>

      <div>
        <label>Tax ID *</label>
        <input
          type="text"
          value={formData.taxId}
          onChange={e => setFormData({ ...formData, taxId: e.target.value })}
          required
        />
      </div>

      <div>
        <label>Company Type *</label>
        <select
          value={formData.companyType}
          onChange={e =>
            setFormData({ ...formData, companyType: e.target.value })
          }
          required>
          <option value="">Select type...</option>
          {companyTypes.map(type => (
            <option key={type.name} value={type.name}>
              {type.displayName}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label>Email</label>
        <input
          type="email"
          value={formData.email}
          onChange={e => setFormData({ ...formData, email: e.target.value })}
        />
      </div>

      <div>
        <label>Phone Number</label>
        <input
          type="tel"
          value={formData.phoneNumber}
          onChange={e =>
            setFormData({ ...formData, phoneNumber: e.target.value })
          }
          placeholder="+905551234567"
        />
      </div>

      <div>
        <label>Address</label>
        <input
          type="text"
          value={formData.address}
          onChange={e => setFormData({ ...formData, address: e.target.value })}
        />
      </div>

      <div>
        <label>City</label>
        <input
          type="text"
          value={formData.city}
          onChange={e => setFormData({ ...formData, city: e.target.value })}
        />
      </div>

      <div>
        <label>Country</label>
        <input
          type="text"
          value={formData.country}
          onChange={e => setFormData({ ...formData, country: e.target.value })}
        />
      </div>

      <button type="submit" disabled={loading}>
        {loading ? "Creating..." : "Create Company"}
      </button>
    </form>
  );
}

export default TenantCompanyForm;
```

---

## 👥 Senaryo 5: Yeni Oluşturulan Company'ye Kullanıcı Ekleme

**Kullanım:** Company oluşturulduktan sonra, o company'ye kullanıcılar eklemek

### Adım 1: User Creation Options'ı Getir (Form için)

```http
GET /api/common/users/creation-options
```

**Response:**

```json
{
  "success": true,
  "data": {
    "roles": [
      {
        "id": "uuid...",
        "roleName": "Admin",
        "roleCode": "ADMIN"
      },
      {
        "id": "uuid...",
        "roleName": "Production Manager",
        "roleCode": "PROD_MANAGER"
      }
    ],
    "departmentCategories": [
      {
        "id": "uuid...",
        "categoryName": "Production",
        "displayOrder": 1
      }
    ],
    "departments": [
      {
        "id": "uuid...",
        "departmentName": "Production",
        "categoryId": "uuid...",
        "categoryName": "Production"
      }
    ],
    "positions": [
      {
        "id": "uuid...",
        "positionName": "Production Manager",
        "departmentId": "uuid...",
        "departmentName": "Production"
      }
    ]
  }
}
```

**Not:** Bu endpoint tenant-scoped çalışır. Yeni oluşturulan company'nin tenant_id'si ile context'e geçmeniz gerekebilir.

### Adım 2: Internal User Oluştur (Kendi Çalışanları İçin)

**Kullanım:** Company'nin kendi çalışanları için (HR data ile)

```http
POST /api/common/users/internal
Content-Type: application/json
Authorization: Bearer {token}

{
  "firstName": "Ahmet",
  "lastName": "Yılmaz",
  "contactValue": "ahmet.yilmaz@company.com",
  "contactType": "EMAIL",
  "companyId": "uuid...",  // Yeni oluşturulan company'nin ID'si
  "departmentId": "uuid...",
  "roleId": "uuid...",
  "positionId": "uuid...",
  "title": "Mr",
  "gender": "MALE",
  "birthDate": "1990-05-15",
  "nationality": "TR",
  "hireDate": "2025-01-15",
  "employeeNumber": "EMP-001",  // Opsiyonel, otomatik oluşturulur
  "emergencyContact": {
    "name": "Ayşe Yılmaz",
    "phone": "+905551234567",
    "relationship": "Spouse"
  },
  "additionalContacts": [
    {
      "contactValue": "+905551234567",
      "contactType": "PHONE",
      "label": "Mobile",
      "isPersonal": true,
      "isWhatsApp": true
    }
  ],
  "addresses": [
    {
      "streetAddress": "Ataturk Cad. No:1",
      "city": "Istanbul",
      "state": "Istanbul",
      "postalCode": "34000",
      "country": "Turkey",
      "addressType": "WORK",
      "isPrimary": true
    }
  ]
}
```

**Response:**

```json
{
  "success": true,
  "message": "Internal employee created successfully",
  "data": {
    "id": "uuid...",
    "uid": "ACME-001-USR-00001",
    "firstName": "Ahmet",
    "lastName": "Yılmaz",
    "displayName": "Ahmet Yılmaz",
    "companyId": "uuid...",
    "roleId": "uuid...",
    "isActive": true,
    "createdAt": "2025-01-27T10:00:00Z"
  }
}
```

### Adım 3: External User Oluştur (Partner/Supplier İçin)

**Kullanım:** Partner, supplier veya customer company'lerden kullanıcılar için (HR data olmadan)

```http
POST /api/common/users/external
Content-Type: application/json
Authorization: Bearer {token}

{
  "firstName": "John",
  "lastName": "Smith",
  "contactValue": "john.smith@partner.com",
  "contactType": "EMAIL",
  "companyId": "uuid...",  // Partner company'nin ID'si
  "department": "Sales",
  "additionalContacts": [
    {
      "contactValue": "+447553838399",
      "contactType": "PHONE",
      "label": "Work Phone",
      "isPersonal": false
    }
  ],
  "addresses": [
    {
      "streetAddress": "123 Partner Street",
      "city": "London",
      "state": "England",
      "postalCode": "SW1A 1AA",
      "country": "United Kingdom",
      "addressType": "WORK",
      "isPrimary": true
    }
  ]
}
```

**Response:**

```json
{
  "success": true,
  "message": "External user created successfully",
  "data": {
    "id": "uuid...",
    "uid": "ACME-001-USR-00002",
    "firstName": "John",
    "lastName": "Smith",
    "displayName": "John Smith",
    "companyId": "uuid...",
    "isActive": true,
    "createdAt": "2025-01-27T10:00:00Z"
  }
}
```

### Frontend Kullanımı: Company + User Creation Flow

```jsx
import { useState } from "react";

function CompanyAndUserCreationFlow() {
  const [step, setStep] = useState(1); // 1: Company, 2: Users
  const [companyId, setCompanyId] = useState(null);
  const [userFormData, setUserFormData] = useState({
    firstName: "",
    lastName: "",
    contactValue: "",
    contactType: "EMAIL",
    companyId: null,
    departmentId: null,
    roleId: null,
    positionId: null,
  });
  const [creationOptions, setCreationOptions] = useState(null);

  // Step 1: Company oluştur
  const handleCompanySubmit = async companyData => {
    try {
      const response = await fetch("/api/common/companies/with-contact", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(companyData),
      });

      const result = await response.json();
      if (result.success) {
        setCompanyId(result.data.id);
        setUserFormData({ ...userFormData, companyId: result.data.id });
        setStep(2); // User creation step'e geç

        // User creation options'ı yükle
        await loadUserCreationOptions();
      }
    } catch (error) {
      console.error("Company creation failed:", error);
    }
  };

  // User creation options'ı yükle
  const loadUserCreationOptions = async () => {
    try {
      const response = await fetch("/api/common/users/creation-options", {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      const result = await response.json();
      if (result.success) {
        setCreationOptions(result.data);
      }
    } catch (error) {
      console.error("Failed to load user creation options:", error);
    }
  };

  // Step 2: User oluştur
  const handleUserSubmit = async e => {
    e.preventDefault();
    try {
      const response = await fetch("/api/common/users/internal", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          ...userFormData,
          companyId: companyId,
        }),
      });

      const result = await response.json();
      if (result.success) {
        // User oluşturuldu
        console.log("User created:", result.data);
        // Form'u resetle veya başka user ekle
        setUserFormData({
          firstName: "",
          lastName: "",
          contactValue: "",
          contactType: "EMAIL",
          companyId: companyId,
          departmentId: null,
          roleId: null,
          positionId: null,
        });
      }
    } catch (error) {
      console.error("User creation failed:", error);
    }
  };

  return (
    <div>
      {step === 1 && <CompanyForm onSubmit={handleCompanySubmit} />}

      {step === 2 && companyId && (
        <div>
          <h2>Add Users to Company</h2>
          <p>Company ID: {companyId}</p>

          {creationOptions && (
            <UserForm
              formData={userFormData}
              setFormData={setUserFormData}
              creationOptions={creationOptions}
              onSubmit={handleUserSubmit}
            />
          )}
        </div>
      )}
    </div>
  );
}
```

---

## 🔗 İlgili Endpoint'ler Özeti

### Company Management

- `GET /api/common/companies/types/tenant` - Tenant company types (form için)
- `POST /api/common/companies` - Basit company oluştur
- `POST /api/common/companies/with-contact` - Company + contact + address (önerilen)
- `GET /api/common/companies/{id}` - Company detayları
- `PUT /api/common/companies/{id}` - Company güncelle
- `GET /api/common/companies/tenants` - Tüm tenant companies listesi

### Admin Onboarding

- `POST /api/admin/onboarding/tenant` - Admin tarafından tenant oluşturma (user + subscription ile)

### Company Relations

- `GET /api/common/companies/{id}/contacts` - Company contacts listesi
- `GET /api/common/companies/{id}/contacts/default` - Default contact
- `GET /api/common/companies/{id}/contacts/department/{department}` - Department bazlı contacts
- `POST /api/common/companies/{id}/contacts` - Mevcut contact'ı company'ye ekle
- `POST /api/common/companies/{id}/contacts/create-and-assign` - Yeni contact oluştur ve ekle
- `PUT /api/common/companies/{id}/contacts/{contactId}/default` - Default contact olarak ayarla
- `DELETE /api/common/companies/{id}/contacts/{contactId}` - Contact'ı kaldır
- `GET /api/common/companies/{id}/addresses` - Company addresses listesi
- `GET /api/common/companies/{id}/addresses/primary` - Primary address
- `GET /api/common/companies/{id}/addresses/headquarters` - Headquarters address
- `POST /api/common/companies/{id}/addresses` - Mevcut address'ı company'ye ekle
- `POST /api/common/companies/{id}/addresses/create-and-assign` - Yeni address oluştur ve ekle
- `PUT /api/common/companies/{id}/addresses/{addressId}/primary` - Primary address olarak ayarla
- `PUT /api/common/companies/{id}/addresses/{addressId}/headquarters` - Headquarters olarak ayarla
- `DELETE /api/common/companies/{id}/addresses/{addressId}` - Address'ı kaldır
- `GET /api/common/companies/{id}/departments` - Company departments
- `GET /api/common/companies/{id}/subscriptions` - Company subscriptions

### User Management (Company'ye Kullanıcı Ekleme)

- `GET /api/common/users/creation-options` - User creation form options (roles, departments, positions)
- `POST /api/common/users/internal` - Internal user oluştur (HR data ile)
- `POST /api/common/users/external` - External user oluştur (HR data olmadan)

---

## ⚠️ Önemli Notlar

1. **Tenant ID = Company ID**: Root tenant için `tenant_id = company_id`
2. **Tax ID Unique**: Tax ID sistem genelinde unique olmalı
3. **Company Type**: Tenant olabilen tipler için `isTenant: true` olanları kullan
4. **Validation**: Backend'de validation var, frontend'de de kontrol edin
5. **Authorization**: Company oluşturma için uygun yetki gerekli (genelde SUPER_ADMIN veya PLATFORM_ADMIN)
6. **User Creation**: Company oluşturulduktan sonra, o company'ye kullanıcı eklemek için:
   - Önce `GET /api/common/users/creation-options` ile form options'ı alın
   - Sonra `POST /api/common/users/internal` veya `POST /api/common/users/external` ile kullanıcı oluşturun
   - `companyId` field'ında yeni oluşturulan company'nin ID'sini kullanın
7. **Tenant Context**: User creation options tenant-scoped çalışır. Yeni tenant için doğru tenant context'inde olmalısınız
8. **Contact ve Address Ekleme**: Company oluşturulduktan sonra ek contact ve address bilgileri eklenebilir:
   - **Contact ekleme**: `POST /api/common/companies/{companyId}/contacts/create-and-assign` (yeni contact oluştur) veya `POST /api/common/companies/{companyId}/contacts` (mevcut contact'ı ekle)
   - **Address ekleme**: `POST /api/common/companies/{companyId}/addresses/create-and-assign` (yeni address oluştur) veya `POST /api/common/companies/{companyId}/addresses` (mevcut address'ı ekle)
   - Company oluştururken `/with-contact` endpoint'i ile de contact ve address eklenebilir (tek işlemde)

---

## 📚 İlgili Dokümantasyon

- [Company Management Guide](./COMPANY_MANAGEMENT_ANALYSIS_AND_GUIDE_FOR_FRONTEND.md)
- [Company Contact Endpoints](./COMPANY_CONTACT_ENDPOINTS_GUIDE.md) - Detaylı company contact yönetimi
- [Tenant Onboarding](./PLATFORM_ADMIN_ANALYSIS_AND_GUIDE_FOR_FRONTEND.md)
- [User Creation Guide](./CREATE_USER.md) - Detaylı user creation dokümantasyonu
- [Communication System Guide](./COMMUNICATION_SYSTEM_ANALYSIS_AND_GUIDE_FOR_FRONTEND.md) - Contact ve address yönetimi detayları

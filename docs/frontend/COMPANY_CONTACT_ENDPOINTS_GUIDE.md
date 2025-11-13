# Company Contact Endpoints Guide

**Last Updated:** 2025-11-12  
**Status:** Production-Ready

---

## Sorular ve Cevaplar

### 1. ✅ Company contact'larını getiren endpoint var mı?

**CEVAP: EVET, var!**

**Endpoint:**

```http
GET /api/common/companies/{companyId}/contacts
```

**Controller:** `CompanyContactController.java` (Line 30-40)

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "companyId": "uuid",
      "contactId": "uuid",
      "contact": {
        "id": "uuid",
        "contactValue": "+902121234567",
        "contactType": "LANDLINE",
        "label": "Main Phone",
        "isPersonal": false,
        "isWhatsApp": false,
        "parentContactId": null
      },
      "isDefault": true,
      "isWhatsApp": false
    }
  ]
}
```

**Kod:**

```java
@GetMapping
public ResponseEntity<ApiResponse<List<CompanyContactDto>>> getCompanyContacts(
    @PathVariable UUID companyId) {
    List<CompanyContactDto> contacts = companyContactService.getCompanyContacts(companyId)
        .stream()
        .map(CompanyContactDto::from)
        .collect(Collectors.toList());
    return ResponseEntity.ok(ApiResponse.success(contacts));
}
```

---

### 2. ❓ Existing contact seçildiğinde label/department override edilebilir mi?

**CEVAP: Kısmen**

#### Label → ❌ Override edilemez (Read-only)

**Sebep:** `label` field'ı `Contact` entity'de, `CompanyContact` junction entity'de değil.

**Kod Analizi:**

```java
// Contact.java - Line 113
@Column(name = "label", length = 100)
private String label;  // ✅ Contact entity'de

// CompanyContact.java - Line 87
@Column(name = "department", length = 100)
private String department;  // ✅ CompanyContact junction entity'de
```

**Sonuç:**

- `label` → Contact entity'de, tüm company'ler için aynı
- `department` → CompanyContact junction entity'de, company-specific override edilebilir

#### Department → ✅ Override edilebilir

**Endpoint:**

```http
POST /api/common/companies/{companyId}/contacts
Content-Type: application/json

{
  "contactId": "existing-contact-uuid",
  "isDefault": false,
  "department": "Sales"  // ✅ Override edilebilir
}
```

**Kod:**

```java
// CompanyContactService.java - Line 61-98
public CompanyContact assignContact(
    UUID companyId,
    UUID contactId,
    Boolean isDefault,
    String department) {  // ✅ department parametresi var

    CompanyContact companyContact = CompanyContact.builder()
        .companyId(companyId)
        .contactId(contactId)
        .isDefault(isDefault != null ? isDefault : false)
        .department(department)  // ✅ Override edilebilir
        .build();

    return companyContactRepository.save(companyContact);
}
```

**Örnek Senaryo:**

```
Contact (Global):
  - contactValue: "+902121234567"
  - label: "Main Phone"  // ❌ Tüm company'ler için aynı

CompanyContact (Company A):
  - companyId: company-a
  - contactId: contact-123
  - department: "Sales"  // ✅ Company A için Sales department

CompanyContact (Company B):
  - companyId: company-b
  - contactId: contact-123  // Aynı contact
  - department: "Support"  // ✅ Company B için Support department
```

---

### 3. ❓ Extension contact'ları da gösterilmeli mi? (PHONE_EXTENSION)

**CEVAP: EVET, gösterilmeli (şu anda gösteriliyor)**

**Kod Analizi:**

```java
// CompanyContactService.java - Line 41-46
public List<CompanyContact> getCompanyContacts(UUID companyId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return companyContactRepository.findByTenantIdAndCompanyId(tenantId, companyId);
    // ✅ Filtreleme yok - TÜM contact'lar döner (PHONE_EXTENSION dahil)
}
```

**Repository Query:**

```java
// CompanyContactRepository.java - Line 23-26
@Query("SELECT cc FROM CompanyContact cc WHERE cc.tenantId = :tenantId AND cc.companyId = :companyId")
List<CompanyContact> findByTenantIdAndCompanyId(
    @Param("tenantId") UUID tenantId,
    @Param("companyId") UUID companyId);
// ✅ PHONE_EXTENSION filtrelenmiyor - Tüm contact'lar döner
```

**Response Örneği:**

```json
{
  "success": true,
  "data": [
    {
      "contact": {
        "contactType": "LANDLINE",
        "contactValue": "+902121234567",
        "label": "Main Phone"
      }
    },
    {
      "contact": {
        "contactType": "PHONE_EXTENSION", // ✅ Extension gösteriliyor
        "contactValue": "101",
        "label": "Extension 101",
        "parentContactId": "parent-contact-uuid"
      },
      "department": "Sales"
    }
  ]
}
```

**Frontend İçin Öneri:**

```typescript
interface CompanyContactDto {
  companyId: string;
  contactId: string;
  contact: {
    id: string;
    contactValue: string;
    contactType: "EMAIL" | "MOBILE" | "LANDLINE" | "PHONE_EXTENSION" | ...;
    label: string;
    parentContactId?: string;  // ✅ PHONE_EXTENSION için
  };
  isDefault: boolean;
  department?: string;
}

// Frontend'de extension'ları gösterirken:
{contacts.map(contact => (
  <div key={contact.contactId}>
    {contact.contact.contactType === "PHONE_EXTENSION" ? (
      <span>
        Extension {contact.contact.contactValue}
        (Parent: {contact.contact.parentContactId})
      </span>
    ) : (
      <span>{contact.contact.contactValue}</span>
    )}
  </div>
))}
```

---

### 4. ✅ Department bazlı filtreleme gerekli mi?

**CEVAP: EVET, var ve kullanılmalı!**

**Endpoint:**

```http
GET /api/common/companies/{companyId}/contacts/department/{department}
```

**Controller:** `CompanyContactController.java` (Line 52-64)

**Kod:**

```java
@GetMapping("/department/{department}")
public ResponseEntity<ApiResponse<List<CompanyContactDto>>> getDepartmentContacts(
    @PathVariable UUID companyId,
    @PathVariable String department) {

    List<CompanyContactDto> contacts = companyContactService
        .getDepartmentContacts(companyId, department)
        .stream()
        .map(CompanyContactDto::from)
        .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(contacts));
}
```

**Service:**

```java
// CompanyContactService.java - Line 54-58
public List<CompanyContact> getDepartmentContacts(UUID companyId, String department) {
    return companyContactRepository.findByCompanyIdAndDepartment(companyId, department);
}
```

**Repository Query:**

```java
// CompanyContactRepository.java - Line 45-48
@Query("SELECT cc FROM CompanyContact cc WHERE cc.companyId = :companyId AND cc.department = :department")
List<CompanyContact> findByCompanyIdAndDepartment(
    @Param("companyId") UUID companyId,
    @Param("department") String department);
```

**Kullanım Örnekleri:**

```http
# Tüm company contact'ları
GET /api/common/companies/{companyId}/contacts

# Sadece Sales department contact'ları
GET /api/common/companies/{companyId}/contacts/department/Sales

# Sadece Support department contact'ları
GET /api/common/companies/{companyId}/contacts/department/Support

# Company-wide contact'lar (department = null)
GET /api/common/companies/{companyId}/contacts/department/null
```

**Frontend İçin Öneri:**

```typescript
// Tüm contact'ları getir
const allContacts = await getCompanyContacts(companyId);

// Department bazlı filtreleme
const salesContacts = await getDepartmentContacts(companyId, "Sales");
const supportContacts = await getDepartmentContacts(companyId, "Support");

// UI'da dropdown ile filtreleme
<select
  onChange={e => {
    const dept = e.target.value;
    if (dept === "ALL") {
      loadAllContacts();
    } else {
      loadDepartmentContacts(dept);
    }
  }}>
  <option value="ALL">All Departments</option>
  <option value="Sales">Sales</option>
  <option value="Support">Support</option>
</select>;
```

---

## Tüm Company Contact Endpoints

### 1. Get All Company Contacts

```http
GET /api/common/companies/{companyId}/contacts
```

**Response:** Tüm company contact'ları (PHONE_EXTENSION dahil)

---

### 2. Get Default Contact

```http
GET /api/common/companies/{companyId}/contacts/default
```

**Response:** Company'nin default contact'ı

---

### 3. Get Department Contacts

```http
GET /api/common/companies/{companyId}/contacts/department/{department}
```

**Response:** Belirli department'a ait contact'lar

---

### 4. Assign Existing Contact

```http
POST /api/common/companies/{companyId}/contacts
Content-Type: application/json

{
  "contactId": "existing-contact-uuid",
  "isDefault": false,
  "department": "Sales"  // ✅ Override edilebilir
}
```

---

### 5. Create and Assign New Contact

```http
POST /api/common/companies/{companyId}/contacts/create-and-assign?isDefault=false&department=Sales
Content-Type: application/json

{
  "contactValue": "+902121234567",
  "contactType": "LANDLINE",
  "label": "Sales Phone",  // ✅ Yeni contact için label set edilebilir
  "isPersonal": false,
  "parentContactId": null  // PHONE_EXTENSION için gerekli
}
```

---

### 6. Set as Default

```http
PUT /api/common/companies/{companyId}/contacts/{contactId}/default
```

---

### 7. Remove Contact

```http
DELETE /api/common/companies/{companyId}/contacts/{contactId}
```

---

## Özet Tablo

| Soru                                   | Cevap    | Detay                                                                    |
| -------------------------------------- | -------- | ------------------------------------------------------------------------ |
| **Endpoint var mı?**                   | ✅ EVET  | `GET /api/common/companies/{companyId}/contacts`                         |
| **Label override edilebilir mi?**      | ❌ HAYIR | Label Contact entity'de, read-only                                       |
| **Department override edilebilir mi?** | ✅ EVET  | Department CompanyContact junction entity'de                             |
| **Extension gösterilmeli mi?**         | ✅ EVET  | Şu anda gösteriliyor (filtreleme yok)                                    |
| **Department filtreleme var mı?**      | ✅ EVET  | `GET /api/common/companies/{companyId}/contacts/department/{department}` |

---

## Frontend Implementasyon Önerileri

### 1. Contact Listesi Gösterimi

```typescript
interface CompanyContactDisplay {
  contact: ContactDto;
  isDefault: boolean;
  department?: string;
  isExtension: boolean; // ✅ PHONE_EXTENSION kontrolü
}

function displayContact(contact: CompanyContactDto): CompanyContactDisplay {
  return {
    contact: contact.contact,
    isDefault: contact.isDefault,
    department: contact.department,
    isExtension: contact.contact.contactType === "PHONE_EXTENSION",
  };
}
```

### 2. Department Filtreleme

```typescript
async function getCompanyContacts(
  companyId: string,
  department?: string
): Promise<CompanyContactDto[]> {
  const endpoint = department
    ? `/api/common/companies/${companyId}/contacts/department/${department}`
    : `/api/common/companies/${companyId}/contacts`;

  const response = await axios.get(endpoint);
  return response.data.data;
}
```

### 3. Existing Contact Seçimi

```typescript
// Existing contact seçildiğinde
async function assignExistingContact(
  companyId: string,
  contactId: string,
  department?: string // ✅ Override edilebilir
) {
  await axios.post(`/api/common/companies/${companyId}/contacts`, {
    contactId,
    isDefault: false,
    department, // ✅ Department override edilebilir
    // ❌ Label override edilemez (Contact entity'de)
  });
}
```

---

## Sonuç

1. ✅ **Endpoint var:** `GET /api/common/companies/{companyId}/contacts`
2. ❌ **Label override edilemez:** Contact entity'de, read-only
3. ✅ **Department override edilebilir:** CompanyContact junction entity'de
4. ✅ **Extension gösterilmeli:** Şu anda gösteriliyor
5. ✅ **Department filtreleme var:** `GET /api/common/companies/{companyId}/contacts/department/{department}`

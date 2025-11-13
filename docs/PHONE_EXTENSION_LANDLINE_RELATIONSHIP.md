# PHONE_EXTENSION ve LANDLINE İlişkisi

**Last Updated:** 2025-11-12  
**Status:** Production-Ready

---

## Soru: PHONE_EXTENSION ile LANDLINE eşleşmesi nasıl yapılıyor?

**CEVAP:** `parentContactId` field'ı ile ve validation ile.

---

## ContactType.java'da PHONE_EXTENSION

**✅ EVET, PHONE_EXTENSION bir ContactType enum değeri olarak var!**

```java
// ContactType.java - Line 38-43
/**
 * Phone extension (internal)
 * <p>Format: Extension number (e.g., "101", "102")</p>
 * <p>Links to parent landline via parentContactId</p>
 */
PHONE_EXTENSION,
```

---

## İlişki Yapısı

### 1. Contact Entity'de parentContactId Field'ı

```java
// Contact.java - Line 116-122
/**
 * Parent contact ID (for PHONE_EXTENSION)
 * <p>For PHONE_EXTENSION type, this references the parent PHONE contact</p>
 * <p>Example: Extension "101" → parentContactId = company's main phone contact ID</p>
 */
@Column(name = "parent_contact_id")
private UUID parentContactId;
```

**Mantık:**
- `PHONE_EXTENSION` contact'ları bir `parentContactId` field'ına sahip
- Bu field, extension'ın bağlı olduğu LANDLINE contact'ın ID'sini tutar

---

### 2. Validation Logic (ContactService)

```java
// ContactService.java - Line 57-72
if (resolvedType == ContactType.PHONE_EXTENSION && parentContactId == null) {
    throw new DomainException("PHONE_EXTENSION requires parentContactId");
}

if (resolvedType == ContactType.PHONE_EXTENSION) {
    Contact parent = contactRepository.findById(parentContactId)
        .orElseThrow(() -> new DomainException("Parent contact not found"));

    // ✅ VALIDATION: Parent contact LANDLINE olmalı
    if (!parent.getContactType().isLandline()) {
        throw new DomainException("Parent contact must be of type LANDLINE");
    }

    if (!parent.getTenantId().equals(tenantId)) {
        throw new DomainException("Parent contact must belong to same tenant");
    }
}
```

**Validation Kuralları:**
1. ✅ `PHONE_EXTENSION` için `parentContactId` **zorunlu**
2. ✅ Parent contact **LANDLINE** olmalı (`isLandline()` kontrolü)
3. ✅ Parent contact aynı tenant'ta olmalı

---

## Senaryo Örneği

### Adım 1: LANDLINE Contact Oluştur

```java
// Company'nin ana telefonu (LANDLINE)
Contact mainPhone = Contact.builder()
    .contactValue("+902121234567")
    .contactType(ContactType.LANDLINE)  // ✅ LANDLINE
    .label("Main Phone")
    .isPersonal(false)
    .parentContactId(null)  // ✅ Parent yok (ana telefon)
    .build();

Contact savedMainPhone = contactRepository.save(mainPhone);
// savedMainPhone.getId() → "landline-contact-uuid"
```

---

### Adım 2: PHONE_EXTENSION Oluştur (LANDLINE'a bağlı)

```java
// Sales department extension (PHONE_EXTENSION)
Contact extension101 = Contact.builder()
    .contactValue("101")  // ✅ Extension numarası
    .contactType(ContactType.PHONE_EXTENSION)  // ✅ PHONE_EXTENSION
    .label("Extension 101")
    .isPersonal(false)
    .parentContactId(savedMainPhone.getId())  // ✅ LANDLINE contact ID'si
    .build();

Contact savedExtension = contactService.createContact(
    "101",
    ContactType.PHONE_EXTENSION,
    "Extension 101",
    false,
    savedMainPhone.getId()  // ✅ Parent LANDLINE contact ID
);
```

**Validation:**
- ✅ `parentContactId` var → Geçerli
- ✅ Parent contact `LANDLINE` → Geçerli
- ✅ Aynı tenant → Geçerli

---

## Veritabanı Yapısı

### Contact Tablosu

```sql
-- LANDLINE Contact
INSERT INTO common_communication.common_contact (
    id, tenant_id, contact_value, contact_type, label, 
    is_personal, parent_contact_id
) VALUES (
    'landline-uuid', 'tenant-uuid', '+902121234567', 'LANDLINE', 
    'Main Phone', false, NULL  -- ✅ parent_contact_id = NULL
);

-- PHONE_EXTENSION Contact
INSERT INTO common_communication.common_contact (
    id, tenant_id, contact_value, contact_type, label,
    is_personal, parent_contact_id
) VALUES (
    'extension-uuid', 'tenant-uuid', '101', 'PHONE_EXTENSION',
    'Extension 101', false, 'landline-uuid'  -- ✅ parent_contact_id = LANDLINE ID
);
```

---

## İlişki Diyagramı

```
┌─────────────────────────────────────────────────────────┐
│                    Contact Entity                       │
├─────────────────────────────────────────────────────────┤
│ id: landline-uuid                                       │
│ contactValue: "+902121234567"                           │
│ contactType: LANDLINE                                   │
│ parentContactId: NULL  ← Ana telefon (parent yok)       │
└─────────────────────────────────────────────────────────┘
                          ▲
                          │ parentContactId
                          │
┌─────────────────────────────────────────────────────────┐
│                    Contact Entity                       │
├─────────────────────────────────────────────────────────┤
│ id: extension-101-uuid                                  │
│ contactValue: "101"                                     │
│ contactType: PHONE_EXTENSION                             │
│ parentContactId: landline-uuid  ← LANDLINE'a bağlı      │
└─────────────────────────────────────────────────────────┘
```

---

## Frontend İçin Kullanım

### Extension Oluşturma Formu

```typescript
interface CreateExtensionRequest {
  contactValue: string;        // "101", "102", etc.
  contactType: "PHONE_EXTENSION";
  label?: string;             // "Extension 101"
  isPersonal: false;          // Company-provided
  parentContactId: string;    // ✅ LANDLINE contact ID (zorunlu)
}

// Frontend'de extension oluştururken
async function createExtension(
  parentLandlineId: string,
  extensionNumber: string
) {
  const request: CreateExtensionRequest = {
    contactValue: extensionNumber,  // "101"
    contactType: "PHONE_EXTENSION",
    label: `Extension ${extensionNumber}`,
    isPersonal: false,
    parentContactId: parentLandlineId  // ✅ LANDLINE contact ID
  };

  await axios.post('/api/common/contacts', request);
}
```

---

### Extension Listesi Gösterimi

```typescript
interface ContactWithExtension {
  contact: ContactDto;
  extensions?: ContactDto[];  // ✅ Bu contact'ın extension'ları
}

// Backend'den extension'ları getir
async function getContactWithExtensions(contactId: string) {
  const contact = await getContact(contactId);
  
  // Extension'ları getir
  const extensions = await axios.get(
    `/api/common/contacts/extensions?parentContactId=${contactId}`
  );
  
  return {
    contact,
    extensions: extensions.data.data
  };
}

// UI'da göster
{contactWithExtensions.extensions?.map(ext => (
  <div key={ext.id}>
    Extension {ext.contactValue} 
    (Parent: {contactWithExtensions.contact.contactValue})
  </div>
))}
```

---

## Repository Query'leri

### Extension'ları Parent Contact'a Göre Getir

```java
// ContactRepository.java - Line 44-50
/**
 * Find all extensions for a parent phone contact.
 */
@Query("SELECT c FROM Contact c WHERE c.tenantId = :tenantId " +
       "AND c.contactType = 'PHONE_EXTENSION' AND c.parentContactId = :parentContactId")
List<Contact> findExtensionsByParentContactId(
    @Param("tenantId") UUID tenantId,
    @Param("parentContactId") UUID parentContactId);
```

**Kullanım:**
```java
// Service'de
public List<Contact> findExtensionsByParent(UUID parentContactId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return contactRepository.findExtensionsByParentContactId(tenantId, parentContactId);
}
```

---

## ContactType Enum Metodları

```java
// ContactType.java - Line 66-85
/**
 * @return true if this type represents a phone number (mobile or landline).
 */
public boolean isPhone() {
    return this == MOBILE || this == LANDLINE;
    // ❌ PHONE_EXTENSION dahil değil (çünkü extension, telefon numarası değil)
}

/**
 * @return true if this type represents a mobile number.
 */
public boolean isMobile() {
    return this == MOBILE;
}

/**
 * @return true if this type represents a landline number.
 */
public boolean isLandline() {
    return this == LANDLINE;
}

// ✅ PHONE_EXTENSION için özel metod yok (parentContactId ile kontrol ediliyor)
```

**Not:** `PHONE_EXTENSION` için özel metod yok çünkü:
- Extension'lar `isPhone()` metoduna dahil değil (extension numarası, telefon numarası değil)
- Extension'lar `parentContactId` ile LANDLINE'a bağlı
- Validation ile parent'ın LANDLINE olduğu kontrol ediliyor

---

## Özet

### ✅ PHONE_EXTENSION ContactType'da var mı?

**EVET!** `ContactType.PHONE_EXTENSION` enum değeri olarak tanımlı.

### ✅ LANDLINE ile nasıl eşleşiyor?

**`parentContactId` field'ı ile:**

1. **LANDLINE Contact:**
   - `contactType = LANDLINE`
   - `parentContactId = NULL` (ana telefon)

2. **PHONE_EXTENSION Contact:**
   - `contactType = PHONE_EXTENSION`
   - `parentContactId = LANDLINE contact ID` (zorunlu)

3. **Validation:**
   - `PHONE_EXTENSION` için `parentContactId` zorunlu
   - Parent contact `LANDLINE` olmalı
   - Aynı tenant'ta olmalı

---

## API Endpoint Örnekleri

### 1. LANDLINE Contact Oluştur

```http
POST /api/common/contacts
Content-Type: application/json

{
  "contactValue": "+902121234567",
  "contactType": "LANDLINE",
  "label": "Main Phone",
  "isPersonal": false
  // parentContactId gönderilmez (NULL)
}
```

---

### 2. PHONE_EXTENSION Oluştur

```http
POST /api/common/contacts
Content-Type: application/json

{
  "contactValue": "101",
  "contactType": "PHONE_EXTENSION",
  "label": "Extension 101",
  "isPersonal": false,
  "parentContactId": "landline-contact-uuid"  // ✅ LANDLINE contact ID (zorunlu)
}
```

---

### 3. Extension'ları Getir

```http
GET /api/common/contacts/extensions?parentContactId={landline-contact-uuid}
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "extension-101-uuid",
      "contactValue": "101",
      "contactType": "PHONE_EXTENSION",
      "label": "Extension 101",
      "parentContactId": "landline-contact-uuid"
    },
    {
      "id": "extension-102-uuid",
      "contactValue": "102",
      "contactType": "PHONE_EXTENSION",
      "label": "Extension 102",
      "parentContactId": "landline-contact-uuid"
    }
  ]
}
```

---

## Sonuç

1. ✅ **PHONE_EXTENSION ContactType'da var:** `ContactType.PHONE_EXTENSION`
2. ✅ **LANDLINE ile eşleşme:** `parentContactId` field'ı ile
3. ✅ **Validation:** Parent contact LANDLINE olmalı
4. ✅ **Repository:** Extension'ları parent'a göre getiren query var

**Mantık:**
- LANDLINE → Ana telefon (parent yok)
- PHONE_EXTENSION → Extension numarası (parent = LANDLINE contact ID)


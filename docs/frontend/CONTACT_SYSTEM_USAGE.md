# İletişim Bilgileri (Contact) Sistemi Kullanım Kılavuzu

## Genel Yapı

Sistemde iletişim bilgileri **generic Contact entity** üzerinden yönetilir. Her contact hem **User** hem de **Company** ile ilişkilendirilebilir.

### İki Tip İlişkilendirme

1. **UserContact** - Kullanıcıya ait iletişim bilgileri
2. **CompanyContact** - Şirkete ait iletişim bilgileri

## ContactType Enum Değerleri

```typescript
type ContactType =
  | "EMAIL" // Email adresi
  | "PHONE" // Telefon numarası (E.164 format) - WhatsApp özelliği isWhatsApp flag'i ile
  | "PHONE_EXTENSION" // Telefon dahili numarası (parentContactId gerekli)
  | "FAX" // Faks numarası
  | "WEBSITE" // Website URL
  | "SOCIAL_MEDIA"; // Sosyal medya handle
```

**Not:** WhatsApp ayrı bir contact type değildir. PHONE contact'lerinde `isWhatsApp` flag'i ile WhatsApp özelliği belirtilir.

### Format Kuralları

- **EMAIL**: `user@example.com`
- **PHONE**: `+905551234567` (E.164 format, zorunlu)
  - WhatsApp özelliği `isWhatsApp` flag'i ile belirtilir
- **PHONE_EXTENSION**: `"101"` (sadece numara, parentContactId ile bağlanır)
- **WEBSITE**: `https://www.example.com`
- **SOCIAL_MEDIA**: `@username` veya platform-specific identifier

## WhatsApp Özelliği

### Otomatik Tespit

PHONE tipindeki contact'ler için WhatsApp özelliği **otomatik kontrol edilir**:

```typescript
// POST /api/common/users/{userId}/contacts/create-and-assign
{
  "contactValue": "+905551234567",
  "contactType": "PHONE",
  "isWhatsApp": null  // null = otomatik kontrol edilir
}
```

Backend işlem akışı:

1. Contact oluşturulur
2. WhatsApp Business API ile kontrol edilir
3. `isWhatsApp` flag'i otomatik setlenir
4. Verification kodları için öncelik: WhatsApp > SMS

### Manuel Setleme

```typescript
{
  "contactValue": "+905551234567",
  "contactType": "PHONE",
  "isWhatsApp": true  // Manuel olarak set edilir
}
```

### WhatsApp Kontrol Endpoint'i

```typescript
// GET /api/common/contacts/check-whatsapp?phoneNumber=+905551234567
// Response:
{
  "success": true,
  "data": {
    "phoneNumber": "+905551234567",
    "hasWhatsApp": true,
    "canReceiveMessages": true
  }
}
```

## Personal vs Company-Provided Ayrımı

### Contact Entity'de `isPersonal` Flag

```typescript
// Contact entity
{
  "contactValue": "john.doe@company.com",
  "contactType": "EMAIL",
  "isPersonal": false  // false = şirket sağladı (work email)
}
```

- **`isPersonal: true`** → Kullanıcının kişisel iletişim bilgisi
- **`isPersonal: false`** → Şirket tarafından sağlanan iletişim bilgisi

### Kullanım Senaryoları

#### User Contact (Kişisel)

```typescript
// POST /api/common/users/{userId}/contacts/create-and-assign
{
  "contactValue": "john.doe@gmail.com",
  "contactType": "EMAIL",
  "label": "Personal Email",
  "isPersonal": true,        // ✅ Kişisel
  "isDefault": true,
  "isForAuthentication": true
}
```

#### User Contact (Şirket Sağladı)

```typescript
{
  "contactValue": "john.doe@company.com",
  "contactType": "EMAIL",
  "label": "Work Email",
  "isPersonal": false,       // ❌ Şirket sağladı
  "isDefault": false,
  "isForAuthentication": false
}
```

#### Company Contact

```typescript
// POST /api/common/companies/{companyId}/contacts/create-and-assign
{
  "contactValue": "+90-212-123-4567",
  "contactType": "PHONE",
  "label": "Main Office Phone",
  "isDefault": true,
  "department": null  // null = şirket geneli
}
```

## UserContact Junction Entity

UserContact, User ile Contact arasındaki ilişkiyi yönetir:

### Özellikler

- **`isDefault`**: Bildirimler için varsayılan contact
- **`isForAuthentication`**: Login/authentication için kullanılabilir (sadece verified EMAIL/PHONE)

### Örnek Kullanım

```typescript
// POST /api/common/users/{userId}/contacts/create-and-assign
{
  "contactValue": "+905551234567",
  "contactType": "PHONE",
  "label": "Mobile Phone",
  "isDefault": true,              // ✅ Bildirimler için varsayılan
  "isForAuthentication": true,    // ✅ Login için kullanılabilir
  "isWhatsApp": null              // Otomatik kontrol
}
```

### Validation Kuralları

- **Authentication için**: Contact **verified** olmalı ve **EMAIL** veya **PHONE** tipinde olmalı
- **Default contact**: Bir kullanıcının sadece bir default contact'i olabilir
- **Authentication contact**: Bir kullanıcının sadece bir authentication contact'i olabilir

## CompanyContact Junction Entity

CompanyContact, Company ile Contact arasındaki ilişkiyi yönetir:

### Özellikler

- **`isDefault`**: Şirket geneli iletişim için varsayılan contact
- **`department`**: Departman bazlı contact (null = şirket geneli)

### Örnek Kullanım

```typescript
// POST /api/common/companies/{companyId}/contacts/create-and-assign
{
  "contactValue": "sales@company.com",
  "contactType": "EMAIL",
  "label": "Sales Department Email",
  "isDefault": false,
  "department": "Sales"  // ✅ Departman bazlı
}
```

### Phone Extension Örneği

```typescript
// 1. Önce ana telefon numarasını oluştur
POST /api/common/companies/{companyId}/contacts/create-and-assign
{
  "contactValue": "+90-212-123-4567",
  "contactType": "PHONE",
  "label": "Main Office Phone"
}
// Response: { "contactId": "uuid-1" }

// 2. Sonra extension'ı oluştur
POST /api/common/contacts
{
  "contactValue": "101",
  "contactType": "PHONE_EXTENSION",
  "parentContactId": "uuid-1",  // ✅ Ana telefon ID'si
  "label": "Extension 101"
}
// Response: { "contactId": "uuid-2" }

// 3. Extension'ı şirkete ata
POST /api/common/companies/{companyId}/contacts/assign
{
  "contactId": "uuid-2",
  "isDefault": false,
  "department": "Sales"
}
```

## Verification (Doğrulama) Sistemi

### Otomatik Channel Selection

Verification kodları gönderilirken otomatik olarak en uygun kanal seçilir:

**Priority Sırası:**

1. **WhatsApp** (PHONE + isWhatsApp=true)
2. **Email** (EMAIL tipi)
3. **SMS** (PHONE + isWhatsApp=false)

### Akış

```typescript
// Backend otomatik olarak:
1. PHONE numarası mı? → WhatsApp kontrolü yap
2. WhatsApp varsa → WhatsApp ile gönder
3. WhatsApp yoksa → SMS ile gönder
4. EMAIL ise → Email ile gönder
```

## TypeScript Tip Tanımları

```typescript
// Contact Entity
interface Contact {
  id: string;
  contactValue: string;
  contactType: ContactType;
  isVerified: boolean;
  isPrimary: boolean;
  label?: string;
  parentContactId?: string; // PHONE_EXTENSION için
  isPersonal: boolean; // true = kişisel, false = şirket sağladı
  isWhatsApp: boolean; // PHONE için WhatsApp özelliği
}

// UserContact Junction
interface UserContact {
  userId: string;
  contactId: string;
  isDefault: boolean; // Bildirimler için varsayılan
  isForAuthentication: boolean; // Login için kullanılabilir
  contact: Contact;
}

// CompanyContact Junction
interface CompanyContact {
  companyId: string;
  contactId: string;
  isDefault: boolean; // Şirket geneli varsayılan
  department?: string; // Departman bazlı (null = genel)
  contact: Contact;
}
```

## Önemli Notlar

1. **PHONE_EXTENSION**: Mutlaka `parentContactId` ile bir PHONE contact'ine bağlanmalı
2. **Authentication**: Sadece verified EMAIL veya PHONE contact'leri authentication için kullanılabilir
3. **WhatsApp**: PHONE contact'leri için otomatik kontrol edilir, manuel setlenebilir
4. **isPersonal**: Contact entity seviyesinde, UserContact/CompanyContact seviyesinde değil
5. **Default Contact**: User için bir, Company için bir default contact olabilir
6. **Department**: CompanyContact'te null ise şirket geneli, dolu ise departman bazlı

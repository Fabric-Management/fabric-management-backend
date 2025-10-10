# 📧 Contact Service Documentation

**Version:** 1.0  
**Last Updated:** 2025-10-10  
**Port:** 8082  
**Database:** fabric_management (contact_schema)  
**Status:** ✅ Production Ready

---

## 📋 Overview

Contact Service manages contact information (email, phone, address) for users and companies. Simple, focused service with owner-based authorization.

### Core Responsibilities

- ✅ Contact CRUD operations (email, phone, address, fax, website)
- ✅ Contact verification (email/phone)
- ✅ Primary contact management
- ✅ Owner-based authorization (USER or COMPANY)
- ✅ Integration with User and Company services

---

## 🏗️ Architecture

### Domain Model

```
contact-service/
├── api/
│   ├── ContactController.java
│   └── dto/
│       ├── request/
│       └── response/
│
├── application/
│   ├── mapper/
│   └── service/
│       └── ContactService.java
│
├── domain/
│   ├── aggregate/
│   │   └── Contact.java [232 lines]
│   ├── event/
│   │   ├── ContactCreatedEvent.java
│   │   ├── ContactUpdatedEvent.java
│   │   └── ContactDeletedEvent.java
│   └── valueobject/
│       └── ContactType.java (EMAIL, PHONE, ADDRESS, FAX, WEBSITE)
│
└── infrastructure/
    ├── repository/
    │   └── ContactRepository.java
    └── messaging/
        └── ContactEventPublisher.java
```

---

## 📦 Domain Model

### Contact Aggregate

```java
@Entity
@Table(name = "contacts")
@Getter
@Setter
@SuperBuilder
public class Contact extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;  // User ID or Company ID

    @Column(name = "owner_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private OwnerType ownerType;  // USER or COMPANY

    @Column(name = "contact_value", nullable = false)
    private String contactValue;  // email, phone, address

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false)
    private ContactType contactType;  // EMAIL, PHONE, ADDRESS, FAX, WEBSITE

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verification_code")
    private String verificationCode;

    @Column(name = "verification_expires_at")
    private LocalDateTime verificationExpiresAt;

    // Business methods (Domain logic)
    public void verify(String code) { }
    public void makePrimary() { }
    public String generateVerificationCode() { }
}
```

**Note:** Contact entity has business methods (NOT anemic domain) - appropriate for this domain!

---

## 🔐 Authorization Model

### Owner-Based Authorization (Simple & Sufficient)

```java
// ✅ DOĞRU: Basit owner check yeterli
public Contact getContact(UUID contactId, UUID currentUserId) {
    Contact contact = contactRepository.findById(contactId);

    // Simple check: Owner mı?
    if (!contact.getOwnerId().equals(currentUserId)) {
        throw new UnauthorizedException("Not your contact");
    }

    return contact;
}
```

**Why Policy NOT Needed:**

1. ✅ Basit domain (email, phone, address)
2. ✅ Owner-based access yeterli
3. ✅ Cross-company contact gereksiz
4. ✅ Gateway authorization yeterli

**📖 Policy analizi:** [POLICY_USAGE_ANALYSIS_AND_RECOMMENDATIONS.md](../../POLICY_USAGE_ANALYSIS_AND_RECOMMENDATIONS.md)

---

## 🎯 Key Features

### 1. Multi-Type Contact Support

- **EMAIL** - Email addresses
- **PHONE** - Phone numbers
- **ADDRESS** - Physical addresses
- **FAX** - Fax numbers
- **WEBSITE** - Website URLs

### 2. Contact Verification

```java
// Email/Phone verification flow
1. generateVerificationCode() → 6-digit code
2. Send code via email/SMS
3. verify(code) → Mark as verified
4. verifiedAt timestamp set
```

### 3. Primary Contact Management

```java
// Only one primary contact per type per owner
contact.makePrimary()
  → Other contacts of same type become non-primary
  → Only verified contacts can be primary
```

---

## 📊 API Endpoints

### Contact Management

| Method | Endpoint                             | Auth  | Description         |
| ------ | ------------------------------------ | ----- | ------------------- |
| POST   | `/api/v1/contacts`                   | Owner | Create contact      |
| GET    | `/api/v1/contacts/{id}`              | Owner | Get contact         |
| GET    | `/api/v1/contacts/owner/{ownerId}`   | Owner | List owner contacts |
| PUT    | `/api/v1/contacts/{id}`              | Owner | Update contact      |
| DELETE | `/api/v1/contacts/{id}`              | Owner | Delete contact      |
| POST   | `/api/v1/contacts/{id}/verify`       | Owner | Verify contact      |
| POST   | `/api/v1/contacts/{id}/make-primary` | Owner | Make primary        |

### Internal Endpoints

| Method | Endpoint                                       | Description                       |
| ------ | ---------------------------------------------- | --------------------------------- |
| GET    | `/api/v1/contacts/find-by-value?value={email}` | Find contact by value (auth flow) |

**Note:** Internal endpoint used by User-Service for authentication

---

## 🗄️ Database Schema

```sql
CREATE TABLE contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,                    -- ✅ UUID type!
    owner_type VARCHAR(20) NOT NULL,           -- USER, COMPANY
    contact_value VARCHAR(255) NOT NULL,
    contact_type VARCHAR(20) NOT NULL,         -- EMAIL, PHONE, ADDRESS, FAX, WEBSITE
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP,
    verification_code VARCHAR(10),
    verification_expires_at TIMESTAMP,
    deleted_at TIMESTAMP,

    -- BaseEntity fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN DEFAULT FALSE,
    version INTEGER DEFAULT 0,

    UNIQUE(owner_id, contact_value)  -- No duplicate contacts per owner
);

CREATE INDEX idx_contacts_owner ON contacts(owner_id, owner_type);
CREATE INDEX idx_contacts_value ON contacts(contact_value);
```

---

## 🤝 Integration Points

### Events Published

- `ContactCreatedEvent` - New contact created
- `ContactUpdatedEvent` - Contact updated (verified, primary changed)
- `ContactDeletedEvent` - Contact soft deleted
- `ContactVerifiedEvent` - Contact verified

### Events Consumed

- `UserCreatedEvent` - User created → Create email contact
- `CompanyCreatedEvent` - Company created → Create website contact

---

## 🔧 Configuration

```yaml
# application.yml
server:
  port: 8082

spring:
  application:
    name: contact-service

# Contact verification
contact:
  verification:
    code-expiry-minutes: 15
    code-length: 6
```

---

## 📈 Refactoring Status

### TODO (Low Priority)

Contact-Service also needs refactoring:

- ⚠️ Entity has business methods (232 lines) - can be refactored to Anemic Domain
- ⚠️ No DTO request/response separation
- ⚠️ No Mapper pattern yet

**📖 Refactoring guide:** Same pattern as User/Company service

---

## 🔗 Related Documentation

- [Code Structure](../development/code_structure_guide.md) - Coding standards
- [Security Guide](../SECURITY.md) - Security documentation
- [Policy Usage Analysis](../../POLICY_USAGE_ANALYSIS_AND_RECOMMENDATIONS.md) - Why policy not needed here

---

**Last Updated:** 2025-10-10  
**Version:** 1.0  
**Status:** ✅ Production Ready  
**Policy Integration:** ❌ NOT NEEDED (Owner-based auth sufficient)

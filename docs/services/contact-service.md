# 📞 Contact Service - Complete Documentation

**Version:** 1.0  
**Last Updated:** October 10, 2025  
**Port:** 8082  
**Database:** contact_db  
**Status:** ✅ Production

---

## 📋 Overview

Contact Service manages all contact information (emails, phones, addresses) for users and companies in the Fabric Management System. It implements Clean Architecture with straightforward CRUD operations.

### Core Responsibilities

- Contact information CRUD (email, phone, address)
- Multi-contact per user/company
- Contact verification (email/phone)
- Primary contact designation
- Contact history tracking
- Multi-tenancy support

---

## 🏗️ Architecture

### Clean Architecture Layers

```
┌─────────────────────────────────────┐
│  API Layer (Controllers)            │
│  - ContactController                │
│  - REST endpoints & DTOs            │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  Application Layer                  │
│  - ContactService                   │
│  - ContactMapper                    │
│  - Validation logic                 │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  Domain Layer                       │
│  - Contact entity                   │
│  - Domain events                    │
│  - Business rules                   │
└─────────────────────────────────────┐
              ↓
┌─────────────────────────────────────┐
│  Infrastructure Layer               │
│  - ContactRepository (JPA)          │
│  - Kafka event publishing           │
└─────────────────────────────────────┘
```

### Key Patterns

- ✅ **Clean Architecture**: Clear layer separation
- ✅ **DDD**: Domain-driven design with events
- ✅ **Event Publishing**: Kafka integration
- ✅ **Multi-tenancy**: Tenant isolation

---

## 📦 Domain Model

### Contact Entity

```java
@Entity
@Table(name = "contacts")
public class Contact extends BaseEntity {
    private UUID tenantId;
    private UUID userId;           // User contact
    private UUID companyId;        // Company contact
    private String contactType;    // EMAIL, PHONE, ADDRESS
    private String contactValue;
    private boolean isPrimary;
    private boolean isVerified;
    private LocalDateTime verifiedAt;
}
```

### Contact Types

- `EMAIL`: Email addresses
- `PHONE`: Phone numbers
- `ADDRESS`: Physical addresses

---

## 📊 API Endpoints

| Endpoint                               | Method | Description               |
| -------------------------------------- | ------ | ------------------------- |
| `/api/v1/contacts`                     | GET    | List contacts (paginated) |
| `/api/v1/contacts/{id}`                | GET    | Get contact by ID         |
| `/api/v1/contacts`                     | POST   | Create contact            |
| `/api/v1/contacts/{id}`                | PUT    | Update contact            |
| `/api/v1/contacts/{id}`                | DELETE | Delete contact            |
| `/api/v1/contacts/user/{userId}`       | GET    | Get user contacts         |
| `/api/v1/contacts/company/{companyId}` | GET    | Get company contacts      |
| `/api/v1/contacts/{id}/verify`         | POST   | Verify contact            |
| `/api/v1/contacts/{id}/set-primary`    | POST   | Set as primary            |

**📖 Complete API reference:** [docs/api/README.md](../api/README.md)

---

## 🗄️ Database Schema

### Main Tables

- `contacts` - Contact information
- `outbox_events` - Event sourcing outbox

### Flyway Migrations

```
V1__create_contacts_table.sql
V2__add_contact_indexes.sql
V3__add_verification_fields.sql
```

---

## 🔐 Security

### Authorization

- **Tenant Isolation**: All queries filtered by `tenantId`
- **Data Scope**: Users can only access their contacts
- **Role-Based**: Standard Spring Security roles

---

## 🧪 Testing

### Test Coverage

- Service layer: 85%
- Repository layer: 95%
- Overall: 88%

### Running Tests

```bash
mvn test
mvn verify
```

---

## 📚 Related Documentation

- **System Architecture**: [docs/ARCHITECTURE.md](../ARCHITECTURE.md)
- **API Standards**: [docs/development/MICROSERVICES_API_STANDARDS.md](../development/MICROSERVICES_API_STANDARDS.md)
- **UUID Standards**: [docs/development/DATA_TYPES_STANDARDS.md](../development/DATA_TYPES_STANDARDS.md)

---

**Maintained By:** Backend Team  
**Last Updated:** 2025-10-10  
**Status:** ✅ Production Ready

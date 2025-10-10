# 🏢 Company Service - Complete Documentation

**Version:** 2.0  
**Last Updated:** October 10, 2025  
**Port:** 8083  
**Database:** company_db  
**Status:** ✅ Production

---

## 📋 Overview

Company Service is the multi-tenancy and company management microservice of Fabric Management System. It implements Clean Architecture with CQRS pattern for optimal separation of read/write operations.

### Core Responsibilities

- Company CRUD operations
- Multi-tenancy management (tenant isolation)
- Company status lifecycle
- Subscription plan management
- Department management
- Company settings & preferences
- Integration with User and Contact services

---

## 🏗️ Architecture

### Clean Architecture Layers

```
┌─────────────────────────────────────┐
│  API Layer (Controllers)            │
│  - CompanyController                │
│  - REST endpoints & DTOs            │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  Application Layer                  │
│  - CompanyService                   │
│  - Command/Query handlers (CQRS)   │
│  - Mappers, Validators              │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  Domain Layer                       │
│  - Company aggregate root           │
│  - Domain events                    │
│  - Business rules                   │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  Infrastructure Layer               │
│  - CompanyRepository (JPA)          │
│  - Feign clients (User, Contact)   │
│  - Kafka event publishing           │
└─────────────────────────────────────┘
```

### Key Patterns

- ✅ **Clean Architecture**: Clear layer separation
- ✅ **CQRS**: Command/Query responsibility segregation
- ✅ **Event Sourcing**: Outbox Pattern for reliable events
- ✅ **DDD**: Aggregate roots, Value Objects, Domain Events
- ✅ **Multi-tenancy**: Tenant isolation at database level

---

## 📦 Domain Model

### Company Aggregate

```java
@Entity
@Table(name = "companies")
public class Company extends BaseEntity {
    private UUID tenantId;          // Multi-tenancy
    private String companyName;
    private CompanyType companyType; // INTERNAL, CUSTOMER, SUPPLIER
    private CompanyStatus status;    // ACTIVE, INACTIVE, SUSPENDED
    private String taxNumber;

    @OneToMany
    private List<Department> departments;
}
```

### Value Objects

- `CompanyType`: INTERNAL, CUSTOMER, SUPPLIER, SUBCONTRACTOR
- `CompanyStatus`: ACTIVE, INACTIVE, SUSPENDED, PENDING_APPROVAL
- `SubscriptionPlan`: FREE, BASIC, PREMIUM, ENTERPRISE

### Domain Events

- `CompanyCreatedEvent`
- `CompanyUpdatedEvent`
- `CompanyStatusChangedEvent`
- `CompanyDeletedEvent`
- `DepartmentAddedEvent`

---

## 🔗 Service Integration

### Feign Clients (with Resilience4j)

```java
// User Service Integration
@FeignClient(name = "user-service", fallback = UserServiceFallback.class)
public interface UserServiceClient {
    ApiResponse<List<UserDto>> getUsersByCompany(UUID companyId);
}

// Contact Service Integration
@FeignClient(name = "contact-service", fallback = ContactServiceFallback.class)
public interface ContactServiceClient {
    ApiResponse<ContactDto> getCompanyContact(UUID companyId);
}
```

**Circuit Breaker:** Resilience4j  
**Fallback:** Returns cached data or empty response  
**Retry:** 3 attempts with exponential backoff

---

## 📊 API Endpoints

### Company Management

| Endpoint                            | Method | Description                |
| ----------------------------------- | ------ | -------------------------- |
| `/api/v1/companies`                 | GET    | List companies (paginated) |
| `/api/v1/companies/{id}`            | GET    | Get company by ID          |
| `/api/v1/companies`                 | POST   | Create new company         |
| `/api/v1/companies/{id}`            | PUT    | Update company             |
| `/api/v1/companies/{id}`            | DELETE | Soft delete company        |
| `/api/v1/companies/{id}/activate`   | POST   | Activate company           |
| `/api/v1/companies/{id}/deactivate` | POST   | Deactivate company         |

### Department Management

| Endpoint                                         | Method | Description       |
| ------------------------------------------------ | ------ | ----------------- |
| `/api/v1/companies/{companyId}/departments`      | GET    | List departments  |
| `/api/v1/companies/{companyId}/departments`      | POST   | Add department    |
| `/api/v1/companies/{companyId}/departments/{id}` | PUT    | Update department |

**📖 Complete API reference:** [docs/api/README.md](../api/README.md)

---

## 🗄️ Database Schema

### Main Tables

- `companies` - Company master data
- `departments` - Company departments
- `company_settings` - Company preferences
- `outbox_events` - Event sourcing outbox

### Flyway Migrations

```
V1__create_companies_table.sql
V2__create_departments_table.sql
V3__add_company_indexes.sql
V4__create_outbox_table.sql
V5__add_subscription_fields.sql
```

**📖 Migration strategy:** [docs/deployment/DATABASE_MIGRATION_STRATEGY.md](../deployment/DATABASE_MIGRATION_STRATEGY.md)

---

## 🔐 Security

### Authorization

- **Tenant Isolation**: All queries filtered by `tenantId`
- **Policy Authorization**: Company type-based access control
- **Role-Based**: ADMIN, COMPANY_MANAGER, EMPLOYEE

### Data Scope

- **SELF**: Own company only
- **CROSS_COMPANY**: Customer/supplier access (limited)
- **GLOBAL**: Admin access (full)

---

## 🧪 Testing

### Test Coverage

- Domain logic: 100%
- Service layer: 90%
- Controller layer: 85%
- Overall: 92%

### Running Tests

```bash
mvn test                    # Unit tests
mvn verify                  # Integration tests
mvn clean verify jacoco:report  # With coverage
```

---

## 📚 Related Documentation

- **System Architecture**: [docs/ARCHITECTURE.md](../ARCHITECTURE.md)
- **Development Principles**: [docs/development/PRINCIPLES.md](../development/PRINCIPLES.md)
- **API Standards**: [docs/development/MICROSERVICES_API_STANDARDS.md](../development/MICROSERVICES_API_STANDARDS.md)

---

**Maintained By:** Backend Team  
**Last Updated:** 2025-10-10  
**Status:** ✅ Production Ready

# shared-domain

**Version**: 1.0.0  
**Last Updated**: 2025-10-20

## Purpose

Domain layer shared across all microservices - Contains base entities, domain events, exceptions, and value objects.

**Principle**: Domain logic is PURE - No Spring dependencies, no infrastructure concerns.

---

## Key Components

### 1. Base Entity (`base/BaseEntity.java`)

**Usage**: All domain entities extend this

```java
@Entity
public class Company extends BaseEntity {
    // Automatically gets: id, version, createdAt, updatedAt, deletedAt
}
```

**Fields**:

- `UUID id` - Auto-generated on persist
- `Long version` - Optimistic locking
- `LocalDateTime createdAt` - Audit timestamp
- `LocalDateTime updatedAt` - Audit timestamp
- `LocalDateTime deletedAt` - Soft delete support

### 2. Domain Events (`event/`)

**Available Events**:

- `UserCreatedEvent` - User registration
- `TenantRegisteredEvent` - Tenant onboarding
- `CompanyCreatedEvent` - Company creation

**Usage**:

```java
// 1. Create event
CompanyCreatedEvent event = CompanyCreatedEvent.builder()
    .companyId(company.getId())
    .tenantId(company.getTenantId())
    .build();

// 2. Publish via DomainEventPublisher
@Autowired
private DomainEventPublisher eventPublisher;

eventPublisher.publish(event);
```

**Pattern**: Event Choreography + Transactional Outbox

### 3. Exceptions (`exception/`)

**Exception Hierarchy** (17 exceptions):

**Authentication**:

- `AccountLockedException` - Account locked after failed attempts
- `InvalidPasswordException` - Wrong password
- `PasswordAlreadySetException` - Password already configured
- `PasswordNotSetException` - User needs password setup

**Domain Validation**:

- `InvalidUserStatusException` - User status transition invalid
- `InvalidCompositionException` - Fiber composition invalid
- `InvalidVerificationCodeException` - Wrong verification code

**Not Found**:

- `UserNotFoundException`
- `ContactNotFoundException`
- `FiberNotFoundException`

**Business Rules**:

- `DuplicateResourceException`
- `TenantRegistrationException`
- `VerificationCodeExpiredException`
- `ContactNotVerifiedException`

**Fiber-Specific**:

- `ImmutableFiberException` - Can't modify locked fiber
- `InactiveFiberException` - Can't use inactive fiber

**Usage**:

```java
// Throw domain exception
throw new UserNotFoundException(userId);

// In GlobalExceptionHandler (shared-infrastructure):
// Maps to 404 NOT_FOUND automatically
```

### 4. Outbox Pattern (`outbox/OutboxEvent.java`)

**Purpose**: Transactional event publishing

**Usage**:

```java
// Save entity + outbox event in SAME transaction
@Transactional
public void createCompany(Company company) {
    companyRepository.save(company);

    OutboxEvent outboxEvent = OutboxEvent.create(
        "company.created",
        company.getId(),
        new CompanyCreatedEvent(...)
    );
    outboxEventRepository.save(outboxEvent);
}

// Scheduler publishes to Kafka asynchronously
```

### 5. Policy Domain (`policy/`)

**RBAC Components**:

- `SystemRole` - Enum of system roles (PLATFORM_SUPER_ADMIN, TENANT_ADMIN, etc.)
- `RoleScope` - Role scope (PLATFORM, TENANT, COMPANY, DEPARTMENT)
- `PermissionType` - Permission types (READ, WRITE, DELETE, etc.)
- `PolicyDecision` - Allow/Deny decision
- `PolicyContext` - Request context for policy evaluation
- `UserContext` - User info for policy engine
- `UserPermission` - User-resource-permission mapping
- `PolicyRegistry` - Policy definitions
- `PolicyDecisionAudit` - Audit trail
- `PolicyAuditEvent` - Event for policy decisions

**Usage**:

```java
// Check permission
PolicyDecision decision = policyEngine.evaluate(
    PolicyContext.builder()
        .userId(userId)
        .tenantId(tenantId)
        .resourceType("COMPANY")
        .operation(OperationType.READ)
        .build()
);

if (decision.isAllowed()) {
    // Proceed
} else {
    throw new ForbiddenException(decision.getReason());
}
```

### 6. Message Keys (`message/`)

**Purpose**: I18n message key constants

**Usage**:

```java
// In service
throw new InvalidPasswordException(
    messageResolver.getMessage(AuthMessageKeys.INVALID_PASSWORD)
);
```

---

## Design Principles

### 1. Anemic Domain Model

- Entities are **pure data holders** (Lombok `@Data`, `@Builder`)
- Business logic in **Service layer**, NOT in entities
- No entity methods beyond getters/setters

### 2. Immutability (Events)

- All domain events are **immutable** (`@Builder`)
- No setters on events

### 3. Type Safety

- UUID everywhere (NO String IDs!)
- Enums for finite states
- Value objects for complex types

### 4. No Infrastructure Dependencies

- **ZERO** Spring annotations in domain classes
- **ZERO** JPA/Hibernate in domain events
- Pure Java POJOs

---

## Dependencies

```xml
<dependencies>
    <!-- Lombok only -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

**No Spring, No JPA in shared-domain!** (Except `@Entity` on BaseEntity)

---

## Testing

```bash
# Run tests
mvn -pl shared/shared-domain test

# Coverage report
mvn -pl shared/shared-domain clean test jacoco:report
open shared/shared-domain/target/site/jacoco/index.html
```

**Current Coverage**: ~40% (needs improvement to 85%+)

**Priority Tests Needed**:

- `BaseEntityTest` - UUID generation, version, timestamps
- `DomainEventPublisherTest` - Event publishing
- `OutboxEventTest` - Outbox creation
- Policy domain tests (already exist ✅)

---

## Migration Guide

### Breaking Changes (v1.0.0 - Oct 2025)

- `BaseEntity.setId()` removed - Use Hibernate auto-generation
- All domain events now require `.builder()` pattern

---

**Owner**: Fabric Management Team  
**Module Type**: Foundation  
**Stability**: Stable (v1.0.0)

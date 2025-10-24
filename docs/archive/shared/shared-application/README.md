# shared-application

**Version**: 1.0.0  
**Last Updated**: 2025-10-20

## Purpose

Application layer shared across all microservices - CQRS patterns, security context, API responses, JSON configuration.

**Principle**: Application orchestration patterns, NOT domain logic!

---

## Key Components

### 1. CQRS Pattern (`command/`, `query/`)

**Purpose**: Separate read and write operations

**Usage**:

```java
// Command (write operation)
public class CreateCompanyCommand implements Command {
    private final String name;
    private final UUID tenantId;
    // Validates and mutates state
}

// Query (read operation)
public class GetCompanyQuery implements Query {
    private final UUID companyId;
    // Read-only, no side effects
}

// In Service
public CompanyResponse createCompany(CreateCompanyCommand command) {
    // Write logic
}

public CompanyResponse getCompany(GetCompanyQuery query) {
    // Read logic (potentially cached)
}
```

**Benefits**:

- Clear separation of concerns
- Easier to optimize (cache queries, not commands)
- Better testing (test reads vs writes separately)

### 2. SecurityContext (`context/SecurityContext.java`)

**Purpose**: Thread-safe user context propagation

**Usage**:

```java
// In filter (set context)
SecurityContext context = SecurityContext.builder()
    .userId(userId)
    .tenantId(tenantId)
    .roles(roles)
    .build();

SecurityContextHolder.setContext(context);

// In service (read context)
@Autowired
private SecurityContext securityContext;

UUID currentUserId = securityContext.getUserId();
UUID currentTenantId = securityContext.getTenantId();
List<String> roles = securityContext.getRoles();

// Check permissions
if (!securityContext.hasRole("TENANT_ADMIN")) {
    throw new ForbiddenException();
}
```

**Thread Safety**: Uses ThreadLocal under the hood

### 3. API Response (`response/ApiResponse.java`)

**Purpose**: Standardized API response wrapper

**Usage**:

```java
// Success response
return ApiResponse.success(companyDto);

// Success with message
return ApiResponse.success(companyDto, "Company created successfully");

// Error response (handled by GlobalExceptionHandler)
return ApiResponse.error("NOT_FOUND", "Company not found", 404);
```

**Response Structure**:

```json
{
  "success": true,
  "data": { ... },
  "message": "Operation successful",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

### 4. Jackson Configuration (`config/JacksonConfig.java`)

**Purpose**: Consistent JSON serialization/deserialization

**Features**:

- Java 8 date/time support (`LocalDateTime`, `ZonedDateTime`)
- Null value handling
- Enum serialization
- UUID serialization

**Auto-configured**: Just include dependency!

---

## Design Principles

### 1. CQRS (Command Query Responsibility Segregation)

- Commands mutate state → return void or simple confirmation
- Queries read state → return DTOs
- Never mix reads and writes in same method

### 2. Context Propagation

- SecurityContext available everywhere via dependency injection
- Thread-safe (one context per request thread)
- Auto-cleanup after request

### 3. Response Consistency

- All controllers return `ApiResponse<T>`
- Uniform error structure
- Client-friendly format

---

## Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

---

## Testing

```bash
# Run tests
mvn -pl shared/shared-application test

# Coverage
mvn -pl shared/shared-application clean test jacoco:report
```

**Current Coverage**: ~60% (1 test file - `SecurityContextTest`)

**Priority Tests Needed**:

- `ApiResponseTest` ❌ (add)
- `JacksonConfigTest` ❌ (add)
- `CommandTest` ❌ (add)
- `QueryTest` ❌ (add)

---

## Examples

### Example 1: CQRS in CompanyService

```java
// Command
public record CreateCompanyCommand(
    String name,
    UUID tenantId,
    CompanyType type
) implements Command {}

// Query
public record GetCompanyQuery(
    UUID companyId
) implements Query {}

// Service
@Service
public class CompanyService {

    public CompanyResponse createCompany(CreateCompanyCommand command) {
        // Validation
        validateCommand(command);

        // Business logic
        Company company = Company.builder()
            .name(command.name())
            .tenantId(command.tenantId())
            .type(command.type())
            .build();

        // Persist
        companyRepository.save(company);

        // Publish event
        eventPublisher.publish(new CompanyCreatedEvent(...));

        return mapper.toResponse(company);
    }

    @Cacheable("companies")
    public CompanyResponse getCompany(GetCompanyQuery query) {
        Company company = companyRepository.findById(query.companyId())
            .orElseThrow(() -> new EntityNotFoundException(...));

        return mapper.toResponse(company);
    }
}
```

### Example 2: SecurityContext in Controller

```java
@RestController
public class CompanyController {

    @Autowired
    private SecurityContext securityContext;

    @PostMapping
    public ApiResponse<CompanyResponse> createCompany(
        @RequestBody @Valid CreateCompanyRequest request
    ) {
        // Auto-inject tenant from context
        CreateCompanyCommand command = new CreateCompanyCommand(
            request.getName(),
            securityContext.getTenantId(), // ← From JWT!
            request.getType()
        );

        CompanyResponse response = companyService.createCompany(command);

        return ApiResponse.success(response, "Company created");
    }
}
```

---

## Migration Guide

### Breaking Changes (v1.0.0 - Oct 2025)

- `ApiResponse` constructor is now private - Use `.success()` or `.error()` factory methods
- `SecurityContext` now requires `@Autowired` (no longer static methods)

---

**Owner**: Fabric Management Team  
**Module Type**: Application Foundation  
**Stability**: Stable (v1.0.0)

# 🚀 Microservices & API Gateway Standards

## 🎯 Overview

Bu doküman, Fabric Management System'deki tüm microservisler ve API Gateway için geçerli olan **zorunlu** standartları tanımlar. Her yeni microservice bu standartlara uygun olarak geliştirilmelidir.

**Son Güncelleme:** 2025-10-08  
**Durum:** ✅ Aktif ve Zorunlu  
**Kapsam:** Tüm Microservices + API Gateway

---

## 📐 API Gateway Architecture

### Gateway Routing Pattern

```
┌─────────────────────────────────────────────────────────────┐
│                      API Gateway (Port 8080)                 │
│                  Spring Cloud Gateway                        │
├─────────────────────────────────────────────────────────────┤
│  Request: GET /api/v1/users/123                             │
│                                                              │
│  StripPrefix: 3 (removes /api/v1/users)                     │
│  Forwards to: user-service:8081/123                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              User Service (Port 8081)                        │
├─────────────────────────────────────────────────────────────┤
│  Controller Base Path: @RequestMapping("/")                 │
│  Receives: GET /123                                          │
│  Handler: getUserById(UUID userId)                           │
└─────────────────────────────────────────────────────────────┘
```

---

## ✅ RULE 1: Controller Base Path Standards

### 🔴 ZORUNLU KURAL: Service-Aware Pattern (Full Paths)

> **API Gateway does NOT strip prefix. All services use full paths: `/api/v1/{service}`**

**⚠️ UPDATED:** October 8, 2025 - Migration to Service-Aware Pattern completed  
**Reference:** See [path_pattern_standardization.md](./path_pattern_standardization.md) for complete migration details

### Standard Pattern

```yaml
# API Gateway Configuration
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/v1/users/**
          # ✅ NO StripPrefix filter!
```

### Controller Implementation

#### ✅ CORRECT: Full Path Mapping

```java
/**
 * User REST Controller
 *
 * API Version: v1
 * Base Path: /api/v1/users (Service-Aware Pattern)
 *
 * External URL: /api/v1/users/**
 * Service URL: /api/v1/users/**
 */
@RestController
@RequestMapping("/api/v1/users")  // ✅ CORRECT - Full path
@RequiredArgsConstructor
@Slf4j
public class UserController {

    @GetMapping("/{userId}")  // Full path: /api/v1/users/{userId}
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        // Implementation
    }

    @GetMapping  // Full path: /api/v1/users
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        // Implementation
    }
}
```

#### ❌ WRONG: Root Path in Controller

```java
@RestController
@RequestMapping("/")  // ❌ WRONG - Old pattern (before Oct 8, 2025)
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        // This is the OLD pattern (deprecated)
        // Use full path: @RequestMapping("/api/v1/users")
    }
}
```

---

## ✅ RULE 2: Nested Resource Controllers

### Pattern for Sub-Resources

```java
/**
 * Company Contact Controller
 *
 * API Version: v1
 * Base Path: /api/v1/companies (Service-Aware Pattern)
 *
 * External URL: /api/v1/companies/{companyId}/contacts/**
 * Service URL: /api/v1/companies/{companyId}/contacts/**
 *
 * Note: This controller is part of company-service
 */
@RestController
@RequestMapping("/{companyId}/contacts")  // ✅ CORRECT - Nested under /api/v1/companies
@RequiredArgsConstructor
public class CompanyContactController {

    @PostMapping  // POST /api/v1/companies/{companyId}/contacts
    public ResponseEntity<ApiResponse<UUID>> addContact(
            @PathVariable UUID companyId,
            @Valid @RequestBody AddContactRequest request) {
        // Implementation
    }

    @DeleteMapping("/{contactId}")  // DELETE /api/v1/companies/{companyId}/contacts/{contactId}
    public ResponseEntity<ApiResponse<Void>> removeContact(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId) {
        // Implementation
    }
}
```

---

## ✅ RULE 3: UUID Path Variables

### 🔴 ZORUNLU: System-Generated IDs Must Be UUID

> **Tüm system-generated identifier'lar Controller'da UUID tipinde olmalı**

Detaylı UUID standartları için: [DATA_TYPES_STANDARDS.md](DATA_TYPES_STANDARDS.md)

#### ✅ CORRECT: UUID Path Variables

```java
@RestController
@RequestMapping("/")
public class UserController {

    // ✅ CORRECT: UUID type
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        UserResponse user = userService.getUser(userId, tenantId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    // ✅ CORRECT: Multiple UUID parameters
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getContactsByOwner(
            @PathVariable UUID ownerId) {
        // Implementation
    }

    // ✅ CORRECT: Query parameter also UUID
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> searchContacts(
            @RequestParam UUID ownerId,
            @RequestParam(required = false) String contactType) {
        // Implementation
    }
}
```

#### ❌ WRONG: String Path Variables for UUIDs

```java
@RestController
@RequestMapping("/")
public class UserController {

    // ❌ WRONG: String type for system ID
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String userId) {
        // Problems:
        // - No compile-time type safety
        // - Manual UUID.fromString() needed
        // - InvalidArgumentException not caught at binding
        UUID userUuid = UUID.fromString(userId);  // ❌ Manual parsing
    }

    // ❌ WRONG: Inconsistent types
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getContactsByOwner(
            @PathVariable String ownerId) {  // ❌ Should be UUID
        // Security risk: No validation until service layer
    }
}
```

---

## ✅ RULE 4: Endpoint Naming Conventions

### RESTful Endpoint Standards

```java
@RestController
@RequestMapping("/")
public class ResourceController {

    // ✅ CORRECT: Standard CRUD operations

    @PostMapping                    // POST /resources - Create
    public ResponseEntity<ApiResponse<UUID>> create(@Valid @RequestBody CreateRequest request)

    @GetMapping("/{id}")            // GET /resources/{id} - Read one
    public ResponseEntity<ApiResponse<Response>> get(@PathVariable UUID id)

    @GetMapping                     // GET /resources - Read all/list
    public ResponseEntity<ApiResponse<List<Response>>> list()

    @PutMapping("/{id}")            // PUT /resources/{id} - Update
    public ResponseEntity<ApiResponse<Void>> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request)

    @DeleteMapping("/{id}")         // DELETE /resources/{id} - Delete
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id)

    // ✅ CORRECT: Action endpoints (when needed)
    @PostMapping("/{id}/activate")  // POST /resources/{id}/activate
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable UUID id)

    // ✅ CORRECT: Search/filter endpoints
    @GetMapping("/search")          // GET /resources/search?param=value
    public ResponseEntity<ApiResponse<List<Response>>> search(@RequestParam String query)

    // ✅ CORRECT: Nested resources
    @GetMapping("/{id}/items")      // GET /resources/{id}/items
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getItems(@PathVariable UUID id)
}
```

### ❌ WRONG Patterns

```java
@RestController
@RequestMapping("/")
public class ResourceController {

    // ❌ WRONG: Non-RESTful naming
    @GetMapping("/getById/{id}")           // Should be: GET /{id}
    @PostMapping("/createNew")             // Should be: POST /
    @PostMapping("/updateResource/{id}")   // Should be: PUT /{id}
    @GetMapping("/deleteResource/{id}")    // Should be: DELETE /{id}

    // ❌ WRONG: RPC-style endpoints
    @PostMapping("/doSomething")           // Not RESTful
    @GetMapping("/performAction")          // Not RESTful
}
```

---

## ✅ RULE 5: Standard Response Format

### ApiResponse Wrapper (Mandatory)

```java
/**
 * Standard API Response
 *
 * All endpoints MUST return ApiResponse wrapper
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private String errorCode;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

### Usage Examples

```java
@RestController
@RequestMapping("/")
public class UserController {

    // ✅ CORRECT: With data
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        UserResponse user = userService.getUser(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    // ✅ CORRECT: With custom message
    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UUID userId = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(userId, "User created successfully"));
    }

    // ✅ CORRECT: Void response (no data)
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted successfully"));
    }

    // ✅ CORRECT: List response
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        List<UserResponse> users = userService.listUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }
}
```

---

## ✅ RULE 6: Security & Authorization

### Security Context Usage

```java
@RestController
@RequestMapping("/")
public class UserController {

    // ✅ CORRECT: Get tenant from security context
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();  // ✅ From JWT
        String currentUserId = SecurityContextHolder.getCurrentUserId();

        UserResponse user = userService.getUser(userId, tenantId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    // ✅ CORRECT: Role-based access
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UUID>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String createdBy = SecurityContextHolder.getCurrentUserId();

        UUID userId = userService.createUser(request, tenantId, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(userId));
    }
}
```

### ❌ WRONG: Accept Tenant from Request

```java
// ❌ WRONG: SECURITY VULNERABILITY!
@GetMapping("/{userId}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(
        @PathVariable UUID userId,
        @RequestParam UUID tenantId) {  // ❌ NEVER accept tenant ID from user!

    // User can access other tenants' data!
    UserResponse user = userService.getUser(userId, tenantId);
    return ResponseEntity.ok(ApiResponse.success(user));
}

// ❌ WRONG: Tenant ID in request body
@PostMapping
public ResponseEntity<ApiResponse<UUID>> createUser(@Valid @RequestBody CreateUserRequest request) {
    UUID tenantId = request.getTenantId();  // ❌ NEVER trust user input for tenant ID!
    return ResponseEntity.ok(ApiResponse.success(null));
}
```

---

## ✅ RULE 7: Exception Handling

### Global Exception Handler (Mandatory)

```java
/**
 * Global Exception Handler
 *
 * Each service MUST have GlobalExceptionHandler
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Business exceptions
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.error("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage(), "NOT_FOUND"));
    }

    // Validation exceptions
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed", "VALIDATION_ERROR"));
    }

    // Authorization exceptions
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Access denied", "FORBIDDEN"));
    }

    // Generic exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_SERVER_ERROR"));
    }
}
```

---

## ✅ RULE 8: Validation Standards

### Request Validation

```java
/**
 * Request DTO with validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be valid E.164 format")
    private String phoneNumber;

    @NotNull(message = "User type is required")
    private UserType userType;

    // ❌ NEVER include system-controlled fields in request!
    // private UUID tenantId;  ❌ From security context
    // private UUID userId;    ❌ System-generated
    // private LocalDateTime createdAt;  ❌ System-generated
}
```

### Controller Validation

```java
@RestController
@RequestMapping("/")
public class UserController {

    // ✅ CORRECT: @Valid annotation
    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> createUser(
            @Valid @RequestBody CreateUserRequest request) {  // ✅ @Valid triggers validation
        UUID userId = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(userId));
    }
}
```

---

## ✅ RULE 9: Service-to-Service Communication

### Feign Client Pattern

```java
/**
 * User Service Client
 *
 * Used by other services to call user-service
 */
@FeignClient(
    name = "user-service",
    url = "${services.user-service.url}",
    configuration = FeignConfig.class
)
public interface UserServiceClient {

    // ✅ CORRECT: Uses service's actual path (not gateway path)
    @GetMapping("/{userId}")
    UserDTO getUser(@PathVariable("userId") UUID userId);

    @GetMapping("/")
    List<UserDTO> listUsers();
}
```

### Internal vs External Endpoints

```java
@RestController
@RequestMapping("/")
public class UserController {

    // ✅ External endpoint (through Gateway)
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        // Full validation and authorization
    }

    // ✅ Internal endpoint (service-to-service)
    @GetMapping("/internal/users/{userId}")
    public ResponseEntity<UserDTO> getUserInternal(@PathVariable UUID userId) {
        // Minimal validation (trusted internal call)
        // No ApiResponse wrapper (DTO only)
    }
}
```

---

## ✅ RULE 10: Gateway Configuration Standards

### Service Registration Pattern

```yaml
# API Gateway application.yml
spring:
  cloud:
    gateway:
      routes:
        # Pattern: {service-name}-{route-type}

        # Public routes (no auth)
        - id: user-service-public
          uri: ${USER_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/v1/users/auth/**
          # ✅ NO StripPrefix - Service-Aware Pattern
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20

        # Protected routes (auth required)
        - id: user-service-protected
          uri: ${USER_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/v1/users/**
          # ✅ NO StripPrefix - Service-Aware Pattern
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100
```

---

## 📋 Service Implementation Checklist

### New Microservice Setup

- [ ] **Gateway Configuration**

  - [ ] Route configured in `api-gateway/application.yml`
  - [ ] NO StripPrefix (Service-Aware Pattern)
  - [ ] Rate limiting configured
  - [ ] Circuit breaker configured

- [ ] **Controller Standards**

  - [ ] Base path: `@RequestMapping("/api/v1/{service}")` ✅ Full path!
  - [ ] All system IDs are UUID type
  - [ ] All endpoints return `ApiResponse<T>`
  - [ ] Security annotations applied
  - [ ] Validation annotations on request DTOs

- [ ] **Security Implementation**

  - [ ] SecurityContextHolder for tenant/user ID
  - [ ] Never accept tenant ID from request
  - [ ] Role-based authorization configured
  - [ ] JWT authentication filter active

- [ ] **Exception Handling**

  - [ ] GlobalExceptionHandler implemented
  - [ ] Business exceptions defined
  - [ ] Consistent error codes
  - [ ] Proper logging

- [ ] **Documentation**
  - [ ] Controller methods documented
  - [ ] API endpoints in README
  - [ ] Request/Response examples
  - [ ] Postman collection updated

---

## 📊 Service Port Allocation

| Service             | Port | Gateway Path            | Controller Base |
| ------------------- | ---- | ----------------------- | --------------- |
| **API Gateway**     | 8080 | N/A                     | N/A             |
| **User Service**    | 8081 | `/api/v1/users/**`      | `/`             |
| **Contact Service** | 8082 | `/api/v1/contacts/**`   | `/`             |
| **Company Service** | 8083 | `/api/v1/companies/**`  | `/`             |
| **Notification**    | 8084 | `/api/v1/notify/**`     | `/`             |
| **HR Service**      | 8085 | `/api/v1/hr/**`         | `/`             |
| **Inventory**       | 8086 | `/api/v1/inventory/**`  | `/`             |
| **Procurement**     | 8087 | `/api/v1/procure/**`    | `/`             |
| **Order Service**   | 8088 | `/api/v1/orders/**`     | `/`             |
| **Logistics**       | 8089 | `/api/v1/logistics/**`  | `/`             |
| **Production**      | 8090 | `/api/v1/production/**` | `/`             |
| **Financial**       | 8091 | `/api/v1/finance/**`    | `/`             |
| **Payment**         | 8092 | `/api/v1/payments/**`   | `/`             |
| **Billing**         | 8093 | `/api/v1/billing/**`    | `/`             |
| **Analytics**       | 8094 | `/api/v1/analytics/**`  | `/`             |

---

## 🔄 Migration Guide (Existing Services)

### Step 1: Verify Controller Base Path

```java
// ✅ CURRENT PATTERN (Service-Aware)
@RestController
@RequestMapping("/api/v1/users")  // Full path
public class UserController { }

// ❌ OLD PATTERN (Before Oct 8, 2025 - Deprecated)
@RestController
@RequestMapping("/")  // Root path with StripPrefix
public class UserController { }
```

**⚠️ NOTE:** All services migrated to Service-Aware Pattern on October 8, 2025. No action needed.

### Step 2: Fix UUID Path Variables

```java
// Before ❌
@GetMapping("/{userId}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String userId) {
    UUID userUuid = UUID.fromString(userId);
}

// After ✅
@GetMapping("/{userId}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
    // Direct UUID usage
}
```

### Step 3: Update Documentation

```java
/**
 * User REST Controller
 *
 * API Version: v1
 * Base Path: /api/v1/users (Service-Aware Pattern)
 *
 * External URL: /api/v1/users/**
 * Service URL: /api/v1/users/**
 */
```

### Step 4: Rebuild & Test

```bash
# Rebuild service
mvn clean package -pl services/user-service -am -DskipTests

# Restart container
docker compose restart user-service

# Test endpoints (both should work identically)
curl http://localhost:8080/api/v1/users/{userId}  # Via Gateway
curl http://localhost:8081/api/v1/users/{userId}  # Direct to service
```

---

## 🚨 Common Mistakes & Solutions

### Mistake 1: Using Old Root Path Pattern

```java
// ❌ WRONG (Old pattern - before Oct 8, 2025)
@RequestMapping("/")  // Deprecated pattern with StripPrefix
public class UserController {
    @GetMapping("/{id}")  // Old pattern, don't use
}

// ✅ CORRECT (Current Service-Aware Pattern)
@RequestMapping("/api/v1/users")  // Full path, no stripping
public class UserController {
    @GetMapping("/{id}")  // Results in: /api/v1/users/{id} - Works!
}
```

### Mistake 2: String Instead of UUID

```java
// ❌ WRONG
@GetMapping("/{userId}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String userId) {
    // No validation, manual parsing
}

// ✅ CORRECT
@GetMapping("/{userId}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
    // Spring validates UUID format automatically
}
```

### Mistake 3: Tenant ID from Request

```java
// ❌ WRONG - SECURITY VULNERABILITY!
@PostMapping
public ResponseEntity<ApiResponse<UUID>> create(
        @RequestBody CreateRequest request,
        @RequestParam UUID tenantId) {  // ❌ User can fake tenant ID!
}

// ✅ CORRECT
@PostMapping
public ResponseEntity<ApiResponse<UUID>> create(@Valid @RequestBody CreateRequest request) {
    UUID tenantId = SecurityContextHolder.getCurrentTenantId();  // ✅ From JWT
}
```

---

## 📚 Related Documentation

- [DATA_TYPES_STANDARDS.md](DATA_TYPES_STANDARDS.md) - UUID usage standards
- [PRINCIPLES.md](PRINCIPLES.md) - Development principles
- [CODE_STRUCTURE_GUIDE.md](CODE_STRUCTURE_GUIDE.md) - Project structure
- [API Gateway Setup](../deployment/API_GATEWAY_SETUP.md) - Gateway configuration

---

## 🎓 Quick Reference

### Controller Template

```java
/**
 * {Resource} REST Controller
 *
 * API Version: v1
 * Base Path: /api/v1/{resource} (Service-Aware Pattern)
 *
 * External URL: /api/v1/{resource}/**
 * Service URL: /api/v1/{resource}/**
 */
@RestController
@RequestMapping("/api/v1/{resource}")  // ✅ Full path
@RequiredArgsConstructor
@Slf4j
public class ResourceController {

    private final ResourceService resourceService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UUID>> create(@Valid @RequestBody CreateRequest request) {
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String createdBy = SecurityContextHolder.getCurrentUserId();
        UUID id = resourceService.create(request, tenantId, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(id, "Resource created successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ResourceResponse>> get(@PathVariable UUID id) {
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        ResourceResponse resource = resourceService.get(id, tenantId);
        return ResponseEntity.ok(ApiResponse.success(resource));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> list() {
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        List<ResourceResponse> resources = resourceService.list(tenantId);
        return ResponseEntity.ok(ApiResponse.success(resources));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequest request) {
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String updatedBy = SecurityContextHolder.getCurrentUserId();
        resourceService.update(id, request, tenantId, updatedBy);
        return ResponseEntity.ok(ApiResponse.success(null, "Resource updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        UUID tenantId = SecurityContextHolder.getCurrentTenantId();
        String deletedBy = SecurityContextHolder.getCurrentUserId();
        resourceService.delete(id, tenantId, deletedBy);
        return ResponseEntity.ok(ApiResponse.success(null, "Resource deleted successfully"));
    }
}
```

---

## ✅ Verification Checklist

Before deploying a service, verify:

- [ ] Controller base path is `/api/v1/{service}` ✅ Full path (Service-Aware Pattern)
- [ ] All system-generated IDs are UUID type
- [ ] All endpoints return `ApiResponse<T>`
- [ ] Tenant ID from `SecurityContextHolder`, never from request
- [ ] User ID from `SecurityContextHolder`, never from request
- [ ] `@PreAuthorize` annotations on protected endpoints
- [ ] `@Valid` on request DTOs
- [ ] GlobalExceptionHandler implemented (or use shared)
- [ ] Gateway route configured WITHOUT StripPrefix ✅ Service-Aware Pattern
- [ ] Rate limiting configured
- [ ] Circuit breaker configured
- [ ] Tests cover all endpoints
- [ ] Documentation updated
- [ ] Postman collection updated

---

**Last Updated:** 2025-10-09 20:15 UTC+1  
**Version:** 2.0 (Service-Aware Pattern Migration)  
**Status:** ✅ Active - Aligned with path_pattern_standardization.md  
**Document Owner:** Architecture Team  
**Review Frequency:** Quarterly  
**Next Review:** 2026-01-08

**Questions?** #fabric-architecture | architecture-team@fabricmanagement.com

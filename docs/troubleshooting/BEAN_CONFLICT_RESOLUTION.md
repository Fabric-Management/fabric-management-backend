# Bean Conflict Resolution - GlobalExceptionHandler - RESOLVED ✅

## Problem Description

User service failed to start with Spring Bean conflict error:

```
org.springframework.context.annotation.ConflictingBeanDefinitionException:
Annotation-specified bean name 'globalExceptionHandler' for bean class
[com.fabricmanagement.shared.infrastructure.exception.GlobalExceptionHandler]
conflicts with existing, non-compatible bean definition of same name and class
[com.fabricmanagement.user.api.GlobalExceptionHandler]
```

**Affected Services:**

- `user-service` ❌ (failed to start)
- `company-service` ✅ (started successfully, but same architectural issue)
- `contact-service` ✅ (started successfully, no custom handler)

**Symptoms:**

- User service fails immediately on startup
- ConflictingBeanDefinitionException during Spring context initialization
- Two `@RestControllerAdvice` beans with same effective name
- Services unable to start due to ambiguous bean definitions

---

## Root Cause Analysis

### The Problem

**Multiple exception handlers with conflicting bean names:**

1. **Shared Infrastructure** provides `GlobalExceptionHandler`

   - Located in: `shared/shared-infrastructure/.../exception/GlobalExceptionHandler.java`
   - Annotated with: `@RestControllerAdvice`
   - Bean name: `globalExceptionHandler` (auto-generated from class name)

2. **User Service** also had `GlobalExceptionHandler`

   - Located in: `services/user-service/.../api/GlobalExceptionHandler.java`
   - Annotated with: `@RestControllerAdvice`
   - Bean name: `globalExceptionHandler` (same name!)

3. **Spring Component Scanning** found both:
   - `@SpringBootApplication` scans `com.fabricmanagement` package
   - Both handlers discovered → Bean name conflict → Application fails

### Why This Happened

**Architectural Anti-Pattern:**

- Shared exception handler intended as default/fallback
- Services with custom handlers should override, not conflict
- No mechanism to conditionally disable shared handler
- Tight coupling through inheritance was attempted but failed

---

## Architecture Anti-Pattern

This violates **microservices independence principles**:

| ❌ Wrong (Bean Conflict)         | ✅ Correct (Conditional Beans)       |
| -------------------------------- | ------------------------------------ |
| Multiple beans, same name        | Conditional bean registration        |
| Spring can't decide which to use | Spring auto-selects based on context |
| Application fails to start       | Services choose their handler        |
| Requires manual exclusion        | Automatic, declarative               |

---

## Solution: @ConditionalOnMissingBean Pattern

### The Elegant Fix

Use Spring Boot's conditional bean registration to allow services to override the shared handler automatically.

#### 1. Shared Infrastructure (Conditional Registration)

**File:** `shared/shared-infrastructure/.../exception/GlobalExceptionHandler.java`

```java
@Slf4j
@RestControllerAdvice
@ConditionalOnMissingBean(name = "serviceExceptionHandler")  // ✅ Key change
public class GlobalExceptionHandler {
    // Common exception handling logic
    // Only registered if no service-specific handler exists
}
```

**Key Addition:**

```java
@ConditionalOnMissingBean(name = "serviceExceptionHandler")
```

**How it works:**

- Spring checks: Is there a bean named `serviceExceptionHandler`?
- **YES** → Skip this handler (service has its own)
- **NO** → Register this handler (use shared default)

---

#### 2. User Service (Service-Specific Handler)

**File:** `services/user-service/.../api/UserServiceExceptionHandler.java`

```java
@Component("serviceExceptionHandler")  // ✅ Named bean
@RestControllerAdvice
@Slf4j
public class UserServiceExceptionHandler {

    // User-service specific exception handlers

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPassword(InvalidPasswordException ex) {
        log.warn("Invalid password attempt");
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid credentials", ex.getErrorCode()));
    }

    // ... other user-specific handlers

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR"));
    }
}
```

**Key Changes:**

- ❌ Removed: `extends GlobalExceptionHandler` (no inheritance)
- ✅ Added: `@Component("serviceExceptionHandler")` (named bean)
- ✅ Result: Shared handler disabled, this handler used
- ✅ Benefit: **Zero coupling**, full autonomy

---

#### 3. Company Service (Service-Specific Handler)

**File:** `services/company-service/.../api/CompanyExceptionHandler.java`

```java
@Component("serviceExceptionHandler")  // ✅ Named bean
@RestControllerAdvice
@Slf4j
public class CompanyExceptionHandler {

    // Company-service specific exception handlers

    @ExceptionHandler(CompanyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCompanyNotFound(CompanyNotFoundException ex) {
        // Company-specific error response format
    }

    // ... other company-specific handlers
}
```

**Same pattern:** Named bean disables shared handler.

---

#### 4. Contact Service (Uses Shared Handler)

**No custom exception handler** → Shared `GlobalExceptionHandler` is automatically registered and used.

**Result:**

- ✅ Zero code needed in contact service
- ✅ Shared handler provides consistent error responses
- ✅ Can be overridden in future if needed

---

## Implementation Steps

### Step 1: Update Shared Infrastructure

```bash
# File: shared/shared-infrastructure/.../exception/GlobalExceptionHandler.java
```

Add import:

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
```

Add annotation:

```java
@ConditionalOnMissingBean(name = "serviceExceptionHandler")
```

---

### Step 2: Update User Service Handler

```bash
# File: services/user-service/.../api/GlobalExceptionHandler.java
# Rename to: UserServiceExceptionHandler.java
```

Changes:

1. Rename class: `GlobalExceptionHandler` → `UserServiceExceptionHandler`
2. Add: `@Component("serviceExceptionHandler")`
3. Remove: `extends GlobalExceptionHandler`
4. Remove: `@Override` annotations
5. Adjust method signatures (remove `WebRequest` parameter if not used)

---

### Step 3: Update Company Service Handler

```bash
# File: services/company-service/.../api/CompanyExceptionHandler.java
```

Add:

```java
@Component("serviceExceptionHandler")
```

---

### Step 4: Rebuild and Deploy

```bash
# Stop containers
docker compose down

# Rebuild without cache
docker compose build --no-cache

# Start services
docker compose up -d

# Verify all services are healthy
docker compose ps
```

---

### Step 5: Verify Success

```bash
# Check service health
docker compose ps

# Expected output:
# fabric-user-service      Up X minutes (healthy)
# fabric-contact-service   Up X minutes (healthy)
# fabric-company-service   Up X minutes (healthy)

# Check logs for bean conflict (should be NONE)
docker compose logs user-service | grep -i "conflict"
# (no output = success)

# Verify services started successfully
docker compose logs user-service | grep "Started.*Application"
docker compose logs company-service | grep "Started.*Application"
docker compose logs contact-service | grep "Started.*Application"
```

**Success Indicators:**

- ✅ No `ConflictingBeanDefinitionException`
- ✅ All services show "Started [Service]Application in X seconds"
- ✅ All health checks passing
- ✅ Tomcat started on expected ports

---

## Why This Solution Works

### Before: Conflicting Beans

```
┌──────────────────────┐
│ Spring Context       │
├──────────────────────┤
│ Component Scanning:  │
│                      │
│ Found:               │
│  ├─ GlobalException  │ (shared)
│  │  Handler          │
│  │  name: global...  │
│  │                   │
│  └─ GlobalException  │ (user-service)
│     Handler          │
│     name: global...  │ ❌ CONFLICT!
│                      │
│ → FAILED TO START    │
└──────────────────────┘
```

### After: Conditional Registration

```
┌──────────────────────────────────────────┐
│ Spring Context Initialization            │
├──────────────────────────────────────────┤
│                                          │
│ Scanning: services/user-service          │
│   ├─ Found: @Component("serviceException│
│   │         Handler")                    │
│   └─ Registered: UserServiceException   │
│                   Handler ✅             │
│                                          │
│ Scanning: shared/shared-infrastructure   │
│   ├─ Found: GlobalExceptionHandler      │
│   ├─ Check: @ConditionalOnMissingBean   │
│   │         (name = "serviceException    │
│   │          Handler")                   │
│   ├─ Condition: Bean exists? YES        │
│   └─ Action: SKIP (not registered) ✅   │
│                                          │
│ Result:                                  │
│   → User Service uses its own handler   │
│   → No conflicts                         │
│   → Application starts successfully ✅   │
└──────────────────────────────────────────┘
```

---

## Benefits of This Approach

### 1. Microservices Autonomy ✅

- Each service decides its own exception handling strategy
- No cross-service dependencies
- Independent deployment and evolution

### 2. Loose Coupling ✅

- No inheritance between service and shared handlers
- Services don't depend on shared infrastructure implementation
- Shared handler can be changed without affecting services with custom handlers

### 3. Spring-Idiomatic ✅

- Uses Spring Boot's built-in conditional configuration
- Declarative, not imperative
- Auto-configuration pattern

### 4. Zero Configuration ✅

- No XML configuration needed
- No programmatic bean exclusion
- Works automatically through annotations

### 5. Flexibility ✅

- Services can add custom handlers anytime
- Services can remove custom handlers to use shared (just delete the file)
- New services automatically use shared handler (convention over configuration)

### 6. Testability ✅

- Each handler is independently testable
- No hidden dependencies through inheritance
- Clear boundaries

---

## Service Exception Handling Strategy

### Current Configuration

| Service             | Exception Handler                 | Type            | Status    |
| ------------------- | --------------------------------- | --------------- | --------- |
| **user-service**    | `UserServiceExceptionHandler`     | Custom          | ✅ Active |
| **company-service** | `CompanyExceptionHandler`         | Custom          | ✅ Active |
| **contact-service** | `GlobalExceptionHandler` (shared) | Shared          | ✅ Active |
| **api-gateway**     | N/A (WebFlux)                     | Different stack | ✅ N/A    |

### Exception Handler Decision Tree

```
Is this service starting?
  │
  ├─ Does it have @Component("serviceExceptionHandler")?
  │   │
  │   ├─ YES → Use service-specific handler
  │   │         Shared handler disabled
  │   │         ✅ Custom error handling
  │   │
  │   └─ NO  → Use shared GlobalExceptionHandler
  │             Shared handler enabled automatically
  │             ✅ Consistent error handling
```

---

## Best Practices

### 1. Naming Convention

```java
// Shared (in shared-infrastructure)
@ConditionalOnMissingBean(name = "serviceExceptionHandler")
public class GlobalExceptionHandler { }

// Service-specific (in each service)
@Component("serviceExceptionHandler")
public class [ServiceName]ExceptionHandler { }
```

### 2. When to Use Custom Handler

**Use Custom Handler When:**

- ✅ Service has domain-specific exceptions
- ✅ Need custom error response format
- ✅ Different HTTP status codes for same exception
- ✅ Additional logging/monitoring requirements

**Use Shared Handler When:**

- ✅ Standard CRUD service
- ✅ Generic error responses sufficient
- ✅ No special business rules for errors
- ✅ Faster development (less code)

### 3. Documentation

Each custom handler should document:

- Why it exists (what's custom about it)
- What exceptions it handles
- Any special error response formats
- Links to related domain exceptions

Example:

```java
/**
 * User Service Exception Handler
 *
 * Custom exception handling for user-service specific requirements:
 * - Authentication failures (InvalidPasswordException, AccountLockedException)
 * - User lifecycle exceptions (UserNotFoundException, ContactNotVerifiedException)
 * - Feign client errors (FeignException for service-to-service calls)
 *
 * When this handler is present, the shared GlobalExceptionHandler is
 * automatically disabled via @ConditionalOnMissingBean mechanism.
 */
@Component("serviceExceptionHandler")
@RestControllerAdvice
@Slf4j
public class UserServiceExceptionHandler {
    // ...
}
```

### 4. Migration Path

**Adding Custom Handler to Existing Service:**

```bash
1. Create [ServiceName]ExceptionHandler.java
2. Add @Component("serviceExceptionHandler")
3. Add @RestControllerAdvice
4. Implement service-specific @ExceptionHandler methods
5. Rebuild and deploy
6. Verify shared handler no longer logs for this service
```

**Removing Custom Handler (Use Shared):**

```bash
1. Delete [ServiceName]ExceptionHandler.java
2. Rebuild and deploy
3. Verify shared handler now handles exceptions
```

---

## Troubleshooting

### Issue: Bean still conflicting

**Symptom:**

```
ConflictingBeanDefinitionException: bean name 'serviceExceptionHandler'...
```

**Solution:**

1. Check all services have unique handler class names
2. Verify `@Component("serviceExceptionHandler")` is present
3. Check for duplicate `@RestControllerAdvice` in same service

---

### Issue: No exception handler working

**Symptom:**

- Exceptions return default Spring error page
- No custom error responses

**Solution:**

1. Verify `@RestControllerAdvice` is present
2. Check component scanning includes handler package
3. Verify handler is registered: Check Spring logs for bean creation

---

### Issue: Wrong handler being used

**Symptom:**

- Service using shared handler instead of custom
- Or vice versa

**Solution:**

1. Check service has `@Component("serviceExceptionHandler")`
2. Verify shared handler has `@ConditionalOnMissingBean(name = "serviceExceptionHandler")`
3. Check Spring logs during startup to see which beans are registered
4. Look for "Skipped ... as condition did not match" in logs

---

## Related Patterns

### 1. Auto-Configuration Pattern

This solution uses Spring Boot's auto-configuration pattern where shared defaults are provided but can be overridden.

### 2. Strategy Pattern

Each service can choose its exception handling strategy without affecting others.

### 3. Convention Over Configuration

Services follow naming convention (`serviceExceptionHandler`) to opt into custom handling.

---

## Status

✅ **RESOLVED** - October 7, 2025

**Final Solution:** @ConditionalOnMissingBean pattern with named beans

**Test Results:**

- ✅ User service starts successfully with custom handler
- ✅ Company service starts successfully with custom handler
- ✅ Contact service starts successfully with shared handler
- ✅ No bean conflicts
- ✅ All services healthy
- ✅ Production-ready

**Deployment Verified:**

```bash
docker compose ps
# All services: Up X minutes (healthy) ✅
```

---

## References

- [Spring Boot Conditional Beans](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.condition-annotations)
- [Spring @RestControllerAdvice](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/bind/annotation/RestControllerAdvice.html)
- [Microservices Exception Handling](https://microservices.io/patterns/observability/exception-tracking.html)

---

## Lessons Learned

1. **Shared libraries in microservices need conditional registration**

   - Don't assume shared code will always be used
   - Provide defaults that can be overridden
   - Use Spring's conditional features

2. **Avoid inheritance for cross-cutting concerns in microservices**

   - Inheritance creates tight coupling
   - Conditional beans provide loose coupling
   - Composition over inheritance principle

3. **Spring Boot provides elegant solutions for common problems**

   - @ConditionalOnMissingBean is perfect for defaults with overrides
   - Auto-configuration patterns are battle-tested
   - Follow Spring Boot conventions for best results

4. **Test bean conflicts early in development**
   - Component scanning can find unexpected beans
   - Integration tests should start full application context
   - Watch for duplicate @Component/@Service/@RestControllerAdvice

---

**Document Maintainer:** Development Team  
**Last Updated:** October 7, 2025  
**Resolution Time:** ~1 hour of analysis and implementation  
**Related Issues:** Bean conflict, microservices exception handling, Spring configuration

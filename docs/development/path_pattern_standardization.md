# Path Pattern Standardization Guide

**Last Updated:** October 8, 2025  
**Status:** ✅ Service-Aware Pattern (IMPLEMENTED)  
**Migration:** Completed October 8, 2025

---

## 📌 Current State - ✅ MIGRATION COMPLETE

### Solution Implemented

Our system now uses **SINGLE UNIFIED PATH PATTERN** for all service communication:

**Service-Aware Pattern:** All services use full paths (`/api/v1/{service}/{endpoint}`)

### What Changed

✅ **Controllers:** Updated to use full base paths (`@RequestMapping("/api/v1/{service}")`)  
✅ **API Gateway:** Removed all `StripPrefix` filters  
✅ **Security Config:** Simplified to single pattern (full paths only)  
✅ **Feign Clients:** No changes needed (already using full paths)

### Benefits Achieved

- ✅ Single source of truth for paths
- ✅ Services are self-contained and testable independently
- ✅ No path transformation needed in API Gateway
- ✅ Clear and maintainable security configuration
- ✅ Consistent behavior across all communication channels

---

## 🎯 Current Pattern Standard

### Service-Aware Pattern (Implemented)

**All communication uses full paths** - No path transformation anywhere

**Flow:**

```
Client → Gateway: /api/v1/contacts/find-by-value
Gateway (No Transform) → Service: /api/v1/contacts/find-by-value
Service receives: /api/v1/contacts/find-by-value
```

**Controller Implementation:**

```java
@RestController
@RequestMapping("/api/v1/contacts")  // Full base path
public class ContactController {

    @GetMapping("/find-by-value")  // Full path: /api/v1/contacts/find-by-value
    public ResponseEntity<?> findByValue() { ... }
}
```

**Feign Client Implementation:**

```java
@FeignClient(
    name = "contact-service",
    url = "${contact-service.url:http://localhost:8082}",
    path = "/api/v1/contacts"  // Full base path
)
public interface ContactServiceClient {

    @GetMapping("/find-by-value")  // Results in /api/v1/contacts/find-by-value
    ApiResponse<ContactDto> findByContactValue(@RequestParam String contactValue);
}
```

**Gateway Configuration:**

```yaml
- id: contact-service
  uri: http://contact-service:8082
  predicates:
    - Path=/api/v1/contacts/**
  # No StripPrefix filter!
```

---

## 🔧 Security Configuration

### Security Configuration

Single pattern in `DefaultSecurityConfig.java`:

```java
.requestMatchers(
    // Contact Service - Internal endpoints
    "/api/v1/contacts/find-by-value",
    "/api/v1/contacts/owner/**",
    "/api/v1/contacts/check-availability",

    // User Service - Authentication (public)
    "/api/v1/users/auth/**",

    // User Service - Internal endpoints
    "/api/v1/users/{userId}",
    "/api/v1/users/{userId}/exists",
    "/api/v1/users/company/**"
).permitAll()
```

### When Adding New Public Endpoints

**SIMPLE:** Just add the full path once:

```java
// Example: New internal endpoint for contact verification
.requestMatchers(
    "/api/v1/contacts/verify-status"  // Single pattern!
).permitAll()
```

---

## ✅ Implementation Summary

### Migration Completed: October 8, 2025

All services successfully migrated to Service-Aware Pattern.

### Changes Applied

#### ✅ Step 1: Updated Controllers

- **Contact Service:** `@RequestMapping("/api/v1/contacts")`
- **User Service:** `@RequestMapping("/api/v1/users")`
- **Auth Controller:** `@RequestMapping("/api/v1/users/auth")`
- **Company Service:** `@RequestMapping("/api/v1/companies")`

#### ✅ Step 2: Updated API Gateway

- Removed all `StripPrefix=3` filters from `application.yml`
- Removed all `StripPrefix=3` filters from `application-docker.yml`
- Gateway now forwards full paths without transformation

#### ✅ Step 3: Simplified Security Config

- Removed dual pattern support
- Single pattern only: full paths
- Clean and maintainable configuration

#### ✅ Step 4: Feign Clients

- No changes needed - already using full paths correctly

---

## 📋 Checklist for New Services

When creating a new microservice, follow this pattern:

### ✅ Controller

```java
@RestController
@RequestMapping("/api/v1/{service-name}")  // Full base path
public class MyController {

    @GetMapping("/endpoint")  // /api/v1/{service-name}/endpoint
    @PostMapping("/another")  // /api/v1/{service-name}/another
}
```

### ✅ Gateway Route

```yaml
- id: my-service
  uri: http://my-service:8080
  predicates:
    - Path=/api/v1/my-service/**
  # No StripPrefix needed!
```

### ✅ Feign Client

```java
@FeignClient(
    name = "my-service",
    url = "${my-service.url}",
    path = "/api/v1/my-service"  // Full path
)
```

### ✅ Security Config

```java
// Add public endpoints with full path
.requestMatchers(
    "/api/v1/my-service/public-endpoint"
).permitAll()
```

---

## 🚨 Common Pitfalls (What to Avoid)

### ❌ Mistake 1: Using Root Path in Controller

```java
// WRONG - Old pattern (stripped paths)
@RestController
@RequestMapping("/")  // Don't do this anymore!
public class ContactController {
    @GetMapping("/find-by-value")
}

// CORRECT - Service-Aware pattern
@RestController
@RequestMapping("/api/v1/contacts")  // Use full base path
public class ContactController {
    @GetMapping("/find-by-value")
}
```

### ❌ Mistake 2: Adding StripPrefix in Gateway

```yaml
# WRONG - Old pattern
- id: contact-service
  uri: http://contact-service:8082
  predicates:
    - Path=/api/v1/contacts/**
  filters:
    - StripPrefix=3 # Don't add this!

# CORRECT - Service-Aware pattern
- id: contact-service
  uri: http://contact-service:8082
  predicates:
    - Path=/api/v1/contacts/**
  # No StripPrefix!
```

### ❌ Mistake 3: Using Wildcard Patterns

```java
// WRONG - Wildcards don't work reliably
.requestMatchers("/api/v1/*/find-by-value").permitAll()

// CORRECT - Explicit full paths
.requestMatchers(
    "/api/v1/contacts/find-by-value",
    "/api/v1/users/find-by-value"
).permitAll()
```

---

## 📊 Service Endpoint Matrix

### Contact Service

| Endpoint           | Path                                  | Public       |
| ------------------ | ------------------------------------- | ------------ |
| Find by value      | `/api/v1/contacts/find-by-value`      | ✅ Internal  |
| Get by owner       | `/api/v1/contacts/owner/{id}`         | ✅ Internal  |
| Check availability | `/api/v1/contacts/check-availability` | ✅ Internal  |
| Get by ID          | `/api/v1/contacts/{id}`               | ❌ Protected |
| Create             | `/api/v1/contacts` (POST)             | ❌ Protected |
| Update             | `/api/v1/contacts/{id}` (PUT)         | ❌ Protected |
| Delete             | `/api/v1/contacts/{id}` (DELETE)      | ❌ Protected |

### User Service

| Endpoint       | Path                                | Public       |
| -------------- | ----------------------------------- | ------------ |
| Login          | `/api/v1/users/auth/login`          | ✅ Public    |
| Check contact  | `/api/v1/users/auth/check-contact`  | ✅ Public    |
| Setup password | `/api/v1/users/auth/setup-password` | ✅ Public    |
| Get user       | `/api/v1/users/{id}`                | ✅ Internal  |
| User exists    | `/api/v1/users/{id}/exists`         | ✅ Internal  |
| Get by company | `/api/v1/users/company/{id}`        | ✅ Internal  |
| List users     | `/api/v1/users` (GET)               | ❌ Protected |
| Create user    | `/api/v1/users` (POST)              | ❌ Protected |
| Update user    | `/api/v1/users/{id}` (PUT)          | ❌ Protected |
| Delete user    | `/api/v1/users/{id}` (DELETE)       | ❌ Protected |

### Company Service

| Endpoint  | Path                              | Public       |
| --------- | --------------------------------- | ------------ |
| Create    | `/api/v1/companies` (POST)        | ❌ Protected |
| Get by ID | `/api/v1/companies/{id}`          | ❌ Protected |
| List all  | `/api/v1/companies` (GET)         | ❌ Protected |
| Update    | `/api/v1/companies/{id}` (PUT)    | ❌ Protected |
| Delete    | `/api/v1/companies/{id}` (DELETE) | ❌ Protected |

---

## 🔄 Migration Timeline

### ✅ COMPLETED - October 8, 2025

All phases completed in single migration:

#### Phase 1: Controllers Updated

- ✅ Contact Service: Full path mapping
- ✅ User Service: Full path mapping
- ✅ Company Service: Full path mapping
- ✅ Auth Controller: Full path mapping

#### Phase 2: Gateway Updated

- ✅ Removed all `StripPrefix` filters
- ✅ Updated `application.yml`
- ✅ Updated `application-docker.yml`

#### Phase 3: Security Simplified

- ✅ Single pattern configuration
- ✅ Clean and maintainable
- ✅ Documentation updated

### Future Services

All new services MUST use Service-Aware pattern from day one:

- Controller: `@RequestMapping("/api/v1/{service}")`
- Gateway: No `StripPrefix`
- Security: Full paths only

---

## 📖 Related Documentation

- [API Gateway Setup](../deployment/API_GATEWAY_SETUP.md)
- [Microservices API Standards](./MICROSERVICES_API_STANDARDS.md)
- [Security Configuration Guide](../SECURITY.md)
- [Service Discovery Setup](../deployment/SERVICE_DISCOVERY_SETUP.md)

---

## ❓ FAQ

### Q: Why not use wildcards in security config?

**A:** Spring Security's wildcard matching (`/api/v1/*/endpoint`) is unreliable and doesn't work consistently with path variables. Explicit paths are more maintainable.

### Q: Should internal endpoints be public?

**A:** Internal service-to-service endpoints should be:

- Publicly accessible in security config (`.permitAll()`)
- Protected by network policies (private network/VPC)
- Rate-limited in API Gateway
- Monitored for unusual patterns

### Q: How do I test if my service is using the correct pattern?

**A:** Check the received URI in your controller:

```java
@GetMapping("/find-by-value")
public ResponseEntity<?> find(HttpServletRequest request) {
    log.info("Received URI: {}", request.getRequestURI());
    // Should always log: /api/v1/contacts/find-by-value
    // Both via Gateway and direct calls
}
```

### Q: How do I verify the migration worked?

**A:** Test both communication paths:

1. **Via Gateway:** `curl http://localhost:8080/api/v1/contacts/find-by-value`
2. **Direct to Service:** `curl http://localhost:8082/api/v1/contacts/find-by-value`

Both should work identically and show same URI in logs.

---

## 👥 Contributors

- Initial Analysis: October 8, 2025
- Migration to Service-Aware Pattern: October 8, 2025
- Documentation: October 8, 2025

---

**Success!** All services now use consistent Service-Aware Pattern. 🎉

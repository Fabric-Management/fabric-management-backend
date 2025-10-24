# shared-security

**Version**: 1.0.0  
**Last Updated**: 2025-10-20

## Purpose

Security layer shared across all microservices - JWT authentication, internal API validation, policy enforcement.

**Principle**: Defense-in-depth security with filter chain pattern.

---

## Key Components

### 1. JWT Authentication (`jwt/JwtTokenProvider.java`)

**Purpose**: Generate and validate JWT tokens

**Usage**:

```java
@Autowired
private JwtTokenProvider jwtTokenProvider;

// Generate token
String token = jwtTokenProvider.generateToken(userId, tenantId, roles);

// Validate token
boolean valid = jwtTokenProvider.validateToken(token);

// Extract claims
UUID userId = jwtTokenProvider.getUserIdFromToken(token);
UUID tenantId = jwtTokenProvider.getTenantIdFromToken(token);
List<String> roles = jwtTokenProvider.getRolesFromToken(token);
```

**Token Structure**:

```json
{
  "sub": "user-uuid",
  "tenantId": "tenant-uuid",
  "roles": ["TENANT_ADMIN", "COMPANY_MANAGER"],
  "iat": 1729440000,
  "exp": 1729443600,
  "iss": "fabric-management-system",
  "aud": "fabric-api"
}
```

### 2. @InternalEndpoint Annotation

**Purpose**: Mark endpoints as internal-only (service-to-service)

**Usage**:

```java
@RestController
@RequestMapping("/api/v1/internal")
public class InternalController {

    @GetMapping("/users/{id}")
    @InternalEndpoint  // ← Requires X-Internal-API-Key header!
    public UserDto getUser(@PathVariable UUID id) {
        // Only accessible from other services
    }
}
```

**Security**: `InternalAuthenticationFilter` validates `X-Internal-API-Key` header

### 3. Security Filters (Chain)

**Filter Order** (lowest to highest precedence):

1. `JwtAuthenticationFilter` - Validates JWT, sets SecurityContext
2. `InternalAuthenticationFilter` - Validates internal API key
3. `PolicyValidationFilter` - Enforces RBAC policies

**Auto-Configuration**: Filters auto-register via `@Component`

### 4. Filter Implementations

#### JwtAuthenticationFilter

**Purpose**: Extract JWT from `Authorization: Bearer <token>` header

**Flow**:

```
1. Extract token from header
2. Validate token (signature, expiry)
3. Extract claims (userId, tenantId, roles)
4. Set Spring SecurityContext
5. Add headers: X-User-Id, X-Tenant-Id, X-User-Roles
6. Continue filter chain
```

**Headers Added**:

- `X-User-Id: <uuid>`
- `X-Tenant-Id: <uuid>`
- `X-User-Roles: TENANT_ADMIN,COMPANY_MANAGER`

#### InternalAuthenticationFilter

**Purpose**: Validate `X-Internal-API-Key` for `@InternalEndpoint` endpoints

**Flow**:

```
1. Check if endpoint has @InternalEndpoint
2. Extract X-Internal-API-Key header
3. Compare with ${INTERNAL_API_KEY} env var
4. Allow/Deny
```

**Configuration**:

```yaml
# In each service
security:
  internal:
    api-key: ${INTERNAL_API_KEY}
```

#### PolicyValidationFilter

**Purpose**: Enforce RBAC policies via PolicyEngine

**Flow**:

```
1. Extract user context (from JWT headers)
2. Build PolicyContext (resource, operation)
3. Call PolicyEngine.evaluate()
4. Allow if decision.isAllowed(), else 403 Forbidden
```

### 5. DefaultSecurityConfig

**Purpose**: Base Spring Security configuration

**Usage**:

```java
@Configuration
@EnableWebSecurity
public class UserServiceSecurityConfig extends DefaultSecurityConfig {
    // Inherits:
    // - SecurityFilterChain
    // - Password encoder (BCrypt)
    // - CORS configuration
    // - CSRF disabled (stateless)
}
```

---

## Security Architecture

### Defense-in-Depth Layers

```
┌─────────────────────────────────────────┐
│ 1. API Gateway (Rate Limiting)          │
├─────────────────────────────────────────┤
│ 2. JwtAuthenticationFilter (JWT)        │
├─────────────────────────────────────────┤
│ 3. InternalAuthenticationFilter (API Key)│
├─────────────────────────────────────────┤
│ 4. PolicyValidationFilter (RBAC)        │
├─────────────────────────────────────────┤
│ 5. Controller (Input Validation)        │
└─────────────────────────────────────────┘
```

### Public vs Protected vs Internal Endpoints

**Public** (No auth):

- `/api/v1/users/auth/login`
- `/api/v1/users/auth/check-contact`
- `/actuator/health`

**Protected** (JWT required):

- `/api/v1/companies/**`
- `/api/v1/users/**` (except auth)

**Internal** (`@InternalEndpoint` - API key required):

- `/api/v1/internal/users/{id}`
- `/api/v1/internal/companies/{id}`

---

## Design Principles

### 1. Stateless Authentication

- JWT in every request (NO server-side sessions)
- Tokens self-contained (all claims in token)
- Redis for rate limiting only (NOT session storage)

### 2. Token Security

- HS256 algorithm (symmetric)
- Secret key from environment (`${JWT_SECRET}`)
- Short expiry (1 hour access, 24 hour refresh)

### 3. API Key Security

- Internal API key from environment (`${INTERNAL_API_KEY}`)
- Validated on every `@InternalEndpoint` call
- Prevents external access to internal APIs

### 4. RBAC (Role-Based Access Control)

- Roles in JWT claims
- Policy engine evaluates permissions
- Cached decisions for performance

---

## Configuration

### Required Environment Variables

```bash
# In each service
JWT_SECRET=your-256-bit-secret-key-change-in-production
INTERNAL_API_KEY=your-internal-key-for-service-to-service
```

### Security Properties

```java
@ConfigurationProperties(prefix = "security.internal")
public class InternalEndpointProperties {
    private String apiKey; // ${INTERNAL_API_KEY}
}
```

---

## Testing

```bash
# Run tests
mvn -pl shared/shared-security test

# Coverage
mvn -pl shared/shared-security clean test jacoco:report
```

**Current Coverage**: **0%** ⚠️ **CRITICAL - NO TESTS!**

**URGENT Tests Needed**:

```java
JwtTokenProviderTest.java       // Token generation, validation, expiry
JwtAuthenticationFilterTest.java // Filter logic, header extraction
InternalAuthenticationFilterTest.java // API key validation
PolicyValidationFilterTest.java // RBAC enforcement
DefaultSecurityConfigTest.java  // Security config
```

---

## Migration Guide

### Breaking Changes (v1.0.0 - Oct 2025)

- `@InternalEndpoint` now enforces API key validation
- JWT requires `tenantId` claim (no longer optional)

---

**Owner**: Fabric Management Team  
**Module Type**: Critical Security Foundation  
**Stability**: Stable (v1.0.0)  
**⚠️ URGENT**: Add test coverage before production!

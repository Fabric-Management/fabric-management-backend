# 🔒 SECURITY POLICIES

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Status:** ✅ Active Development

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Authentication](#authentication)
3. [Authorization](#authorization)
4. [Multi-Tenancy](#multi-tenancy)
5. [Audit Logging](#audit-logging)
6. [Rate Limiting](#rate-limiting)
7. [Data Encryption](#data-encryption)
8. [Security Best Practices](#security-best-practices)

---

## 🎯 OVERVIEW

Fabric Management platformu, **Defense-in-Depth** güvenlik prensibini uygular. Birden fazla güvenlik katmanı ile uygulama korunur.

### **Güvenlik Katmanları**

```
┌─────────────────────────────────────────┐
│  1. API Gateway (Rate Limiting)         │
├─────────────────────────────────────────┤
│  2. Authentication (JWT/OAuth2)          │
├─────────────────────────────────────────┤
│  3. Authorization (Policy Engine)        │
├─────────────────────────────────────────┤
│  4. Multi-Tenancy (Row-Level Security)   │
├─────────────────────────────────────────┤
│  5. Audit Logging (All Actions)          │
├─────────────────────────────────────────┤
│  6. Data Encryption (At-Rest & In-Transit) │
└─────────────────────────────────────────┘
```

---

## 🔑 AUTHENTICATION

### **JWT-Based Authentication**

Tüm API çağrıları JWT token ile authenticate edilir.

#### **Token Structure**

```json
{
  "sub": "user@example.com",
  "tenant_id": "123e4567-e89b-12d3-a456-426614174000",
  "user_id": "456e7890-e89b-12d3-a456-426614174000",
  "roles": ["ROLE_ADMIN", "ROLE_PLANNER"],
  "permissions": ["fabric.material.read", "fabric.material.create"],
  "iat": 1706350000,
  "exp": 1706360000
}
```

#### **Authentication Flow**

```
┌──────────┐                 ┌──────────┐                 ┌──────────┐
│  Client  │                 │   Auth   │                 │ Resource │
│          │                 │ Service  │                 │ Service  │
└────┬─────┘                 └────┬─────┘                 └────┬─────┘
     │                            │                            │
     │  1. POST /auth/login       │                            │
     ├───────────────────────────>│                            │
     │                            │                            │
     │  2. JWT Token              │                            │
     │<───────────────────────────┤                            │
     │                            │                            │
     │  3. GET /api/materials     │                            │
     │    Authorization: Bearer <JWT>                          │
     ├────────────────────────────┼───────────────────────────>│
     │                            │                            │
     │                            │  4. Validate JWT           │
     │                            │<───────────────────────────┤
     │                            │                            │
     │                            │  5. Token Valid            │
     │                            ├───────────────────────────>│
     │                            │                            │
     │  6. Response               │                            │
     │<───────────────────────────┼────────────────────────────┤
     │                            │                            │
```

#### **Implementation**

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {

        // 1. Extract JWT from Authorization header
        String jwt = extractJwtFromRequest(request);

        if (jwt != null && jwtUtils.validateToken(jwt)) {
            // 2. Extract user details from JWT
            String userEmail = jwtUtils.getUserEmailFromToken(jwt);
            UUID tenantId = jwtUtils.getTenantIdFromToken(jwt);
            UUID userId = jwtUtils.getUserIdFromToken(jwt);
            List<String> roles = jwtUtils.getRolesFromToken(jwt);
            List<String> permissions = jwtUtils.getPermissionsFromToken(jwt);

            // 3. Create authentication object
            UserDetails userDetails = new CustomUserDetails(userEmail, tenantId, userId, roles, permissions);
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // 4. Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 5. Set tenant context
            TenantContext.setCurrentTenantId(tenantId);
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

---

## 🛡️ AUTHORIZATION

### **Policy-Based Access Control (PBAC)**

Her endpoint `@PolicyCheck` anotasyonu ile korunur.

#### **Policy Structure**

```json
{
  "policyId": "fabric.material.create",
  "resource": "fabric.material",
  "action": "create",
  "conditions": {
    "roles": ["ROLE_ADMIN", "ROLE_PLANNER"],
    "permissions": ["fabric.material.create"],
    "tenantSubscription": ["fiber", "yarn"],
    "department": ["production", "planning"],
    "conditions": [
      {
        "field": "material.cost",
        "operator": "lessThan",
        "value": 10000
      }
    ]
  }
}
```

#### **Policy Evaluation Flow**

```
┌──────────┐                 ┌──────────┐                 ┌──────────┐
│  Client  │                 │  Policy  │                 │ Resource │
│          │                 │  Engine  │                 │ Service  │
└────┬─────┘                 └────┬─────┘                 └────┬─────┘
     │                            │                            │
     │  1. POST /api/materials    │                            │
     ├───────────────────────────>│                            │
     │                            │                            │
     │                            │  2. Check @PolicyCheck     │
     │                            │    annotation              │
     │                            │<───────────────────────────┤
     │                            │                            │
     │                            │  3. Evaluate Policy        │
     │                            │    - Check roles           │
     │                            │    - Check permissions     │
     │                            │    - Check subscription    │
     │                            │    - Check conditions      │
     │                            │                            │
     │                            │  4. Policy Decision        │
     │                            ├───────────────────────────>│
     │                            │                            │
     │  5. Response / 403 Forbidden                            │
     │<───────────────────────────┼────────────────────────────┤
     │                            │                            │
```

#### **Implementation**

```java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyCheckAspect {

    private final PolicyEvaluationEngine policyEngine;

    @Around("@annotation(policyCheck)")
    public Object checkPolicy(ProceedingJoinPoint joinPoint, PolicyCheck policyCheck) throws Throwable {
        log.info("Evaluating policy: resource={}, action={}", policyCheck.resource(), policyCheck.action());

        // 1. Get current user context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // 2. Build policy request
        PolicyRequest policyRequest = PolicyRequest.builder()
            .resource(policyCheck.resource())
            .action(policyCheck.action())
            .tenantId(userDetails.getTenantId())
            .userId(userDetails.getUserId())
            .roles(userDetails.getRoles())
            .permissions(userDetails.getPermissions())
            .build();

        // 3. Evaluate policy
        PolicyDecision decision = policyEngine.evaluate(policyRequest);

        if (!decision.isAllowed()) {
            log.warn("Policy denied: resource={}, action={}, reason={}",
                policyCheck.resource(), policyCheck.action(), decision.getReason());
            throw new AccessDeniedException(decision.getReason());
        }

        log.info("Policy allowed: resource={}, action={}", policyCheck.resource(), policyCheck.action());

        // 4. Proceed with method execution
        return joinPoint.proceed();
    }
}
```

#### **Usage**

```java
@RestController
@RequestMapping("/api/production/materials")
@RequiredArgsConstructor
public class MaterialController {

    @PolicyCheck(resource="fabric.material.create", action="POST")
    @PostMapping
    public ResponseEntity<ApiResponse<MaterialDto>> createMaterial(@RequestBody CreateMaterialRequest request) {
        // Implementation
    }

    @PolicyCheck(resource="fabric.material.read", action="GET")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MaterialDto>> getMaterial(@PathVariable UUID id) {
        // Implementation
    }

    @PolicyCheck(resource="fabric.material.update", action="PUT")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MaterialDto>> updateMaterial(@PathVariable UUID id, @RequestBody UpdateMaterialRequest request) {
        // Implementation
    }

    @PolicyCheck(resource="fabric.material.delete", action="DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMaterial(@PathVariable UUID id) {
        // Implementation
    }
}
```

---

## 🏢 MULTI-TENANCY

### **Row-Level Security (RLS)**

Her tablo `tenant_id` içerir ve PostgreSQL RLS policy'leri ile tenant izolasyonu sağlanır.

#### **Database Schema**

```sql
CREATE TABLE prod_material (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    unit_cost DECIMAL(10, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Enable Row-Level Security
ALTER TABLE prod_material ENABLE ROW LEVEL SECURITY;

-- Create RLS Policy
CREATE POLICY tenant_isolation_policy ON prod_material
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);
```

#### **Tenant Context Filter**

```java
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final DataSource dataSource;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {

        // 1. Get tenant ID from JWT
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            UUID tenantId = userDetails.getTenantId();

            // 2. Set tenant context in database session
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {

                statement.execute(String.format("SET app.current_tenant_id = '%s'", tenantId));

                // 3. Continue with request
                filterChain.doFilter(request, response);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to set tenant context", e);
            }
        } else {
            // No authentication - return 401
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
        }
    }
}
```

#### **BaseEntity**

```java
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        // Auto-set tenant ID from context
        if (this.tenantId == null) {
            this.tenantId = TenantContext.getCurrentTenantId();
        }

        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
```

---

## 📊 AUDIT LOGGING

### **Comprehensive Audit Trail**

Tüm kritik işlemler audit log'a kaydedilir.

#### **Audit Log Structure**

```java
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String action; // CREATE, UPDATE, DELETE, READ

    @Column(nullable = false)
    private String resource; // material, invoice, user, etc.

    @Column(nullable = false)
    private String resourceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private String userAgent;

    @Column(nullable = false)
    private Instant timestamp;
}
```

#### **Audit Aspect**

```java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        // 1. Get user context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // 2. Get request context
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        // 3. Execute method
        Object result = joinPoint.proceed();

        // 4. Log audit
        auditService.logAction(
            auditLog.action(),
            auditLog.resource(),
            extractResourceId(result),
            auditLog.description(),
            userDetails.getTenantId(),
            userDetails.getUserId(),
            request.getRemoteAddr(),
            request.getHeader("User-Agent")
        );

        return result;
    }
}
```

#### **Usage**

```java
@Service
@RequiredArgsConstructor
public class MaterialService {

    @AuditLog(action="MATERIAL_CREATE", resource="material", description="Material created")
    public MaterialDto createMaterial(CreateMaterialRequest request) {
        // Implementation
    }

    @AuditLog(action="MATERIAL_UPDATE", resource="material", description="Material updated")
    public MaterialDto updateMaterial(UUID id, UpdateMaterialRequest request) {
        // Implementation
    }

    @AuditLog(action="MATERIAL_DELETE", resource="material", description="Material deleted")
    public void deleteMaterial(UUID id) {
        // Implementation
    }
}
```

---

## ⏱️ RATE LIMITING

### **Request Throttling**

API endpoint'leri rate limiting ile korunur.

#### **Rate Limiting Configuration**

```java
@Configuration
public class RateLimitingConfig {

    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.of("api-rate-limiter", RateLimiterConfig.custom()
            .limitForPeriod(100) // 100 requests
            .limitRefreshPeriod(Duration.ofSeconds(1)) // per second
            .timeoutDuration(Duration.ofMillis(500))
            .build());
    }
}
```

#### **Rate Limiting Aspect**

```java
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitingAspect {

    private final RateLimiter rateLimiter;

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1. Check rate limit
        boolean allowed = rateLimiter.acquirePermission();

        if (!allowed) {
            throw new TooManyRequestsException("Rate limit exceeded");
        }

        // 2. Proceed with method execution
        return joinPoint.proceed();
    }
}
```

#### **Usage**

```java
@RestController
@RequestMapping("/api/production/materials")
public class MaterialController {

    @RateLimit(value = 100, duration = 1, unit = TimeUnit.SECONDS)
    @PostMapping
    public ResponseEntity<ApiResponse<MaterialDto>> createMaterial(@RequestBody CreateMaterialRequest request) {
        // Implementation
    }
}
```

---

## 🔐 DATA ENCRYPTION

### **At-Rest Encryption**

Database encryption ile sensitive data korunur.

```sql
-- Encrypt sensitive column
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE users
ADD COLUMN encrypted_password BYTEA;

UPDATE users
SET encrypted_password = pgp_sym_encrypt(password, 'encryption_key');
```

### **In-Transit Encryption**

TLS/SSL ile network communication korunur.

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

---

## ✅ SECURITY BEST PRACTICES

### **1. Never Trust User Input**

```java
// ✅ Good: Validate and sanitize
@PostMapping
public ResponseEntity<?> create(@Valid @RequestBody CreateMaterialRequest request) {
    // Validation happens automatically with @Valid
}

// ❌ Bad: No validation
@PostMapping
public ResponseEntity<?> create(@RequestBody CreateMaterialRequest request) {
    // Direct use without validation
}
```

### **2. Use Parameterized Queries**

```java
// ✅ Good: Parameterized query
@Query("SELECT m FROM Material m WHERE m.name = :name")
List<Material> findByName(@Param("name") String name);

// ❌ Bad: String concatenation (SQL injection risk)
String query = "SELECT * FROM material WHERE name = '" + name + "'";
```

### **3. Secure Password Storage**

```java
// ✅ Good: BCrypt password encoder
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// ❌ Bad: Plain text or MD5
String password = "plaintext"; // Never do this!
```

### **4. Principle of Least Privilege**

```java
// ✅ Good: Specific permissions
@PolicyCheck(resource="fabric.material.create", action="POST")

// ❌ Bad: Wildcard permissions
@PolicyCheck(resource="fabric.*", action="*")
```

### **5. Secure Headers**

```java
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers()
                .xssProtection()
                .and()
                .contentSecurityPolicy("default-src 'self'")
                .and()
                .frameOptions().deny()
                .and()
                .httpStrictTransportSecurity()
                .maxAgeInSeconds(31536000);

        return http.build();
    }
}
```

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

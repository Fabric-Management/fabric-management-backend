# 👥 System Roles - Quick Reference

**Version:** 1.0  
**Date:** October 19, 2025  
**Status:** ✅ Current Production Configuration  
**Purpose:** Quick reference for system roles and permissions

---

## 🎯 Current Role Structure (SystemRole Enum)

### ✅ Active Roles in System

```java
public enum SystemRole {
    SUPER_ADMIN(RoleScope.PLATFORM, 100),  // Platform owner
    TENANT_ADMIN(RoleScope.TENANT, 80),    // Company admin
    MANAGER(RoleScope.TENANT, 60),         // Department manager
    USER(RoleScope.TENANT, 40),            // Regular staff
    VIEWER(RoleScope.TENANT, 20);          // Read-only access
}
```

**Source of Truth:** `shared-domain/src/main/java/com/fabricmanagement/shared/domain/role/SystemRole.java`

---

## 📊 Role Hierarchy & Priorities

```
┌─────────────────────────────────────┐
│  👑 SUPER_ADMIN (Priority: 100)     │  Platform Level
│  - Manages ALL tenants              │  Scope: GLOBAL
│  - System-wide governance           │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│  🏢 TENANT_ADMIN (Priority: 80)     │  Tenant Level
│  - Company owner/CEO                │  Scope: TENANT
│  - Manages company users            │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│  👔 MANAGER (Priority: 60)          │  Department Level
│  - Department head                  │  Scope: TENANT
│  - Approves requests                │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│  👤 USER (Priority: 40)             │  Staff Level
│  - Regular employee                 │  Scope: TENANT
│  - Daily operations                 │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│  👁️ VIEWER (Priority: 20)           │  Read-Only
│  - External partners                │  Scope: TENANT
│  - Auditors                         │
└─────────────────────────────────────┘
```

---

## 🔐 Permission Matrix

| Operation              | SUPER_ADMIN | TENANT_ADMIN | MANAGER | USER | VIEWER |
| ---------------------- | ----------- | ------------ | ------- | ---- | ------ |
| **User Management**    |
| Create TENANT_ADMIN    | ✅          | ❌           | ❌      | ❌   | ❌     |
| Create MANAGER         | ✅          | ✅           | ❌      | ❌   | ❌     |
| Create USER            | ✅          | ✅           | ✅      | ❌   | ❌     |
| Delete User            | ✅          | ✅           | ⚠️      | ❌   | ❌     |
| List Users             | ✅          | ✅           | ✅      | ⚠️   | ⚠️     |
| **Company Management** |
| Create Company         | ✅          | ✅           | ❌      | ❌   | ❌     |
| Update Company         | ✅          | ✅           | ⚠️      | ❌   | ❌     |
| Delete Company         | ✅          | ❌           | ❌      | ❌   | ❌     |
| **Data Operations**    |
| Create Records         | ✅          | ✅           | ✅      | ✅   | ❌     |
| Update Records         | ✅          | ✅           | ✅      | ✅   | ❌     |
| Delete Records         | ✅          | ✅           | ⚠️      | ❌   | ❌     |
| Export Data            | ✅          | ✅           | ✅      | ⚠️   | ⚠️     |
| **System Access**      |
| Platform Admin Panel   | ✅          | ❌           | ❌      | ❌   | ❌     |
| Tenant Dashboard       | ✅          | ✅           | ✅      | ✅   | ✅     |

**Legend:**

- ✅ Full access
- ⚠️ Partial/conditional access
- ❌ No access

---

## 🔧 Usage Examples

### Creating Users (Controller)

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    // TENANT_ADMIN can create users
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {

        UUID userId = userService.createUser(
            request,
            ctx.getTenantId(),  // Always from JWT!
            ctx.getUserId()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(userId));
    }

    // Only SUPER_ADMIN can delete users
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal SecurityContext ctx) {

        userService.deleteUser(userId, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

### JWT Token Structure

```json
{
  "sub": "1099d95d-dd43-4143-bab7-8928d288a60e",
  "tenantId": "67841a85-0849-4fbd-9551-bbd8f13c503d",
  "firstName": "Admin",
  "lastName": "User",
  "role": "TENANT_ADMIN",
  "iss": "fabric-management-system",
  "aud": "fabric-api",
  "iat": 1760858354,
  "exp": 1760861954
}
```

**Note:** No PII in JWT (no email, no phone)

---

## 📋 Policy Registry Configuration

PolicyRegistry entries in database use role names as strings:

```sql
-- Example: Only TENANT_ADMIN can create users
INSERT INTO policy_registry (
    endpoint,
    operation,
    default_roles,
    allowed_company_types
) VALUES (
    '/api/v1/users',
    'WRITE',
    ARRAY['TENANT_ADMIN']::TEXT[],  ✅ Correct role name
    ARRAY['INTERNAL', 'CUSTOMER']::TEXT[]
);
```

**⚠️ Important:** Policy role names MUST match SystemRole enum values exactly!

| ❌ Wrong     | ✅ Correct       |
| ------------ | ---------------- |
| `'ADMIN'`    | `'TENANT_ADMIN'` |
| `'SUPER'`    | `'SUPER_ADMIN'`  |
| `'EMPLOYEE'` | `'USER'`         |

---

## 🚀 Role Assignment Flow

### 1. Tenant Onboarding (Auto-Assignment)

```java
// First user = TENANT_ADMIN (auto)
User tenantAdmin = User.builder()
    .role(SystemRole.TENANT_ADMIN)  // ✅ Automatic
    .tenantId(newTenantId)
    .build();
```

### 2. Inviting Users (Manual Assignment)

```java
// TENANT_ADMIN invites user
POST /api/v1/users/invite
{
  "email": "employee@example.com",
  "role": "USER"  ← TENANT_ADMIN chooses
}

// Validation:
if (requestedRole == SystemRole.TENANT_ADMIN && !ctx.hasRole("SUPER_ADMIN")) {
    throw new ForbiddenException("Only SUPER_ADMIN can create TENANT_ADMIN");
}
```

### 3. Role Promotion (Restricted)

```java
// Only users with higher priority can promote
if (!currentUser.getRole().hasHigherPriorityThan(targetRole)) {
    throw new ForbiddenException("Cannot assign higher or equal role");
}
```

---

## 🛡️ Security Rules

### 1. Role Creation Restrictions

| Creator Role | Can Create                         |
| ------------ | ---------------------------------- |
| SUPER_ADMIN  | All roles (including TENANT_ADMIN) |
| TENANT_ADMIN | MANAGER, USER, VIEWER only         |
| MANAGER      | USER, VIEWER only                  |
| USER         | None                               |
| VIEWER       | None                               |

### 2. Tenant Isolation

```java
// ✅ ALWAYS from SecurityContext (JWT)
UUID tenantId = ctx.getTenantId();

// ❌ NEVER from request
@PostMapping
public void createUser(@RequestBody CreateUserRequest request) {
    // request.tenantId is IGNORED!
    UUID tenantId = ctx.getTenantId();  // ✅ From JWT
}
```

### 3. Role-Based Access Control (RBAC)

```java
// Method-level security
@PreAuthorize("hasRole('TENANT_ADMIN')")

// Multiple roles
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")

// Complex conditions
@PreAuthorize("hasRole('MANAGER') and #userId == authentication.principal.userId")
```

---

## 📝 Common Patterns

### Pattern 1: Admin-Only Endpoint

```java
@PostMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
public ResponseEntity<UUID> createUser(@RequestBody CreateUserRequest request) {
    // Implementation
}
```

### Pattern 2: Self or Admin Access

```java
@GetMapping("/api/v1/users/{userId}")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) {
    UUID tenantId = ctx.getTenantId();

    // Check: Self or admin
    if (!userId.equals(ctx.getUserId()) && !ctx.hasRole("TENANT_ADMIN")) {
        throw new ForbiddenException("Can only view own profile");
    }

    return userService.getUser(userId, tenantId);
}
```

### Pattern 3: Manager Team Access

```java
@GetMapping("/api/v1/users/team")
@PreAuthorize("hasAnyRole('MANAGER', 'TENANT_ADMIN')")
public ResponseEntity<List<UserResponse>> getTeamMembers() {
    if (ctx.hasRole("MANAGER")) {
        // Manager sees only their department
        return userService.getUsersByDepartment(ctx.getDepartmentId());
    } else {
        // TENANT_ADMIN sees all
        return userService.getAllUsers(ctx.getTenantId());
    }
}
```

---

## 🔍 Troubleshooting

### Problem: 403 Forbidden on POST /api/v1/users

**Cause:** User role doesn't match PolicyRegistry requirements

**Check:**

```sql
-- Verify policy role requirements
SELECT endpoint, operation, default_roles
FROM policy_registry
WHERE endpoint = '/api/v1/users' AND deleted = false;

-- Expected: default_roles = {TENANT_ADMIN}
```

**Fix:** Ensure user has TENANT_ADMIN role in JWT

### Problem: "ADMIN role not found"

**Cause:** Old migration data uses 'ADMIN' instead of 'TENANT_ADMIN'

**Fix:**

```sql
-- Update policy registry
UPDATE policy_registry
SET default_roles = ARRAY['TENANT_ADMIN']::TEXT[]
WHERE 'ADMIN' = ANY(default_roles);
```

---

## 📚 Related Documentation

- [TENANT_MODEL_AND_ROLES_GUIDE.md](./TENANT_MODEL_AND_ROLES_GUIDE.md) - Complete role architecture
- [SECURITY.md](../SECURITY.md) - Security implementation details
- [development/principles.md](../development/principles.md) - Coding standards

---

## ⚡ Quick Checklist

Before deploying role changes:

- [ ] SystemRole enum updated
- [ ] Database migration updated (V8 or newer)
- [ ] PolicyRegistry entries use correct role names
- [ ] Controller @PreAuthorize annotations match
- [ ] JWT generation includes correct role
- [ ] Postman collection updated
- [ ] Documentation updated
- [ ] Tests pass with new roles

---

**Last Updated:** 2025-10-19  
**Reviewed By:** Development Team  
**Status:** ✅ Production Ready  
**Next Review:** When adding new roles

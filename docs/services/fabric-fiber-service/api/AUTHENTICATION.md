# 🔐 Fiber Service - Authentication & Authorization

**Version:** 1.0.0  
**Last Updated:** 2025-10-20

---

## 🎯 AUTHENTICATION TYPES

### 1. Public Endpoints (No Auth)

```http
GET /api/v1/fibers/default
```

**Who can access:** Anyone (unauthenticated)  
**Use case:** Frontend needs default fiber list before login

---

### 2. Authenticated Endpoints (JWT Required)

```http
GET /api/v1/fibers/{id}
GET /api/v1/fibers
GET /api/v1/fibers/search?query=cotton
GET /api/v1/fibers/category/NATURAL
```

**Who can access:** Any authenticated user  
**Header:**

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**JWT Claims:**

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "tenantId": "7c9e6679-7425-40de-963d-42a6ee08cd6c",
  "role": "USER",
  "exp": 1698765432
}
```

---

### 3. Admin Endpoints (Role-Based)

```http
POST /api/v1/fibers
POST /api/v1/fibers/blend
PATCH /api/v1/fibers/{id}
DELETE /api/v1/fibers/{id}
```

**Who can access:** TENANT_ADMIN, SUPER_ADMIN  
**Authorization:** `@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")`

**Example:**

```bash
curl -X POST http://localhost:8094/api/v1/fibers \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"code": "BM", "name": "Bamboo", "category": "REGENERATED"}'
```

**Error if insufficient permissions (403 Forbidden):**

```json
{
  "success": false,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

---

### 4. Internal Endpoints (Service-to-Service)

```http
POST /api/v1/fibers/internal/validate
GET /api/v1/fibers/internal/batch
GET /api/v1/fibers/internal/exists/{code}
```

**Who can access:** Other microservices only  
**Header:**

```
X-Internal-API-Key: ${INTERNAL_API_KEY}
```

**Example:**

```bash
curl -X POST http://fiber-service:8094/api/v1/fibers/internal/validate \
  -H "X-Internal-API-Key: ${INTERNAL_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '["CO", "PE", "WO"]'
```

**Security:**

- Internal API Key validated by `InternalAuthenticationFilter`
- NOT accessible via API Gateway (internal routing only)
- Used by: Yarn Service, Fabric Service

---

## 🛡️ SECURITY ARCHITECTURE

### Multi-Layer Defense

```
┌─────────────────────────────────────────┐
│  Layer 1: API Gateway                   │
│  ├─ JWT validation                      │
│  ├─ Rate limiting                       │
│  └─ Primary policy enforcement          │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│  Layer 2: Fiber Service                 │
│  ├─ JwtAuthenticationFilter (Order 1)   │
│  ├─ PolicyValidationFilter (Order 2)    │
│  └─ @PreAuthorize annotations           │
└─────────────────────────────────────────┘
```

### Filter Chain Order

```
1. JwtAuthenticationFilter (shared-security)
   └─ Validates JWT, sets SecurityContext

2. PolicyValidationFilter (shared-security)
   └─ Defense-in-depth policy check

3. Controller Method
   └─ @PreAuthorize validation
```

---

## 🔑 ROLE PERMISSIONS

| Endpoint                   | USER                  | MANAGER | TENANT_ADMIN | SUPER_ADMIN |
| -------------------------- | --------------------- | ------- | ------------ | ----------- |
| `GET /default`             | ✅                    | ✅      | ✅           | ✅          |
| `GET /{id}`                | ✅                    | ✅      | ✅           | ✅          |
| `GET /` (list)             | ✅                    | ✅      | ✅           | ✅          |
| `GET /search`              | ✅                    | ✅      | ✅           | ✅          |
| `GET /category/{category}` | ✅                    | ✅      | ✅           | ✅          |
| `POST /`                   | ❌                    | ❌      | ✅           | ✅          |
| `POST /blend`              | ❌                    | ❌      | ✅           | ✅          |
| `PATCH /{id}`              | ❌                    | ❌      | ✅           | ✅          |
| `DELETE /{id}`             | ❌                    | ❌      | ❌           | ✅          |
| `POST /internal/validate`  | Internal API Key only |
| `GET /internal/batch`      | Internal API Key only |
| `GET /internal/exists`     | Internal API Key only |

---

## 🚨 COMMON AUTH ERRORS

### 401 Unauthorized

```json
{
  "success": false,
  "message": "Unauthorized",
  "errorCode": "UNAUTHORIZED",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Causes:**

- Missing Authorization header
- Invalid JWT token
- Expired JWT token
- Malformed token

---

### 403 Forbidden

```json
{
  "success": false,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Causes:**

- Insufficient role (USER trying to create fiber)
- Policy denied (defense-in-depth check)
- Trying to modify default fiber

---

## 📚 Related Documentation

- [Endpoints](./ENDPOINTS.md) - Complete endpoint reference
- [Error Handling](./ERROR_HANDLING.md) - Error codes & troubleshooting
- [Security Guide](../../../SECURITY.md) - System-wide security

---

**Last Updated:** 2025-10-20  
**Maintained By:** Fabric Management Team

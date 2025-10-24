# ⚠️ Fiber Service - Error Handling Guide

**Version:** 1.0.0  
**Last Updated:** 2025-10-20

---

## 📋 ERROR CODE REFERENCE

### Standard Error Response Format

```json
{
  "success": false,
  "message": "Human-readable error message",
  "errorCode": "MACHINE_READABLE_CODE",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

---

## 🔴 ERROR CATALOG

### FIBER_NOT_FOUND (404)

**When:** Fiber ID doesn't exist

```json
{
  "success": false,
  "message": "Fiber not found: 550e8400-e29b-41d4-a716-446655440000",
  "errorCode": "FIBER_NOT_FOUND",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Solution:** Check fiber ID, use valid UUID

---

### DUPLICATE_RESOURCE (409)

**When:** Fiber code already exists

```json
{
  "success": false,
  "message": "Fiber code already exists: CO",
  "errorCode": "DUPLICATE_RESOURCE",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Solution:** Use different fiber code

---

### INVALID_COMPOSITION (400)

**When:** Blend validation fails

**Scenarios:**

1. **Less than 2 components:**

```json
{
  "success": false,
  "message": "Blend must have at least 2 components",
  "errorCode": "INVALID_COMPOSITION",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

2. **Total percentage ≠ 100%:**

```json
{
  "success": false,
  "message": "Total percentage must equal 100, but was: 95",
  "errorCode": "INVALID_COMPOSITION",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

3. **Duplicate fiber codes:**

```json
{
  "success": false,
  "message": "Duplicate fiber code in composition: CO",
  "errorCode": "INVALID_COMPOSITION",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Solution:** Fix composition to meet validation rules

---

### INACTIVE_FIBER (400)

**When:** Component fiber is inactive

```json
{
  "success": false,
  "message": "Component fiber is inactive: AC",
  "errorCode": "INACTIVE_FIBER",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Solution:** Use only ACTIVE fibers in blend

---

### FORBIDDEN (403)

**When:** Trying to modify immutable fiber

```json
{
  "success": false,
  "message": "Cannot update default fiber: CO",
  "errorCode": "FORBIDDEN",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Solution:** Default fibers are immutable

---

### VALIDATION_ERROR (400)

**When:** Request validation fails

```json
{
  "success": false,
  "message": "Validation failed",
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Common validations:**

- `code` must not be blank
- `name` must not be blank
- `category` must be valid enum
- `percentage` must be 0.01-100

---

### UNAUTHORIZED (401)

**When:** Missing or invalid JWT

```json
{
  "success": false,
  "message": "Unauthorized",
  "errorCode": "UNAUTHORIZED",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Solution:** Include valid JWT in Authorization header

---

### ACCESS_DENIED (403)

**When:** Insufficient role permissions

```json
{
  "success": false,
  "message": "Access denied",
  "errorCode": "ACCESS_DENIED",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Solution:** Requires TENANT_ADMIN or SUPER_ADMIN role

---

## 🛠️ TROUBLESHOOTING

### Issue: Cannot create fiber

**Check:**

1. ✅ JWT token valid?
2. ✅ Role = TENANT_ADMIN or SUPER_ADMIN?
3. ✅ Fiber code unique?
4. ✅ All required fields present?

---

### Issue: Blend validation fails

**Check:**

1. ✅ At least 2 components?
2. ✅ Total = 100%?
3. ✅ No duplicate codes?
4. ✅ All component fibers ACTIVE?

**Example Fix:**

```json
// ❌ WRONG (Total = 95%)
{
  "components": [
    {"fiberCode": "CO", "percentage": 60},
    {"fiberCode": "PE", "percentage": 35}
  ]
}

// ✅ CORRECT (Total = 100%)
{
  "components": [
    {"fiberCode": "CO", "percentage": 60},
    {"fiberCode": "PE", "percentage": 40}
  ]
}
```

---

### Issue: Internal endpoint returns 401

**Check:**

1. ✅ X-Internal-API-Key header present?
2. ✅ Key matches ${INTERNAL_API_KEY}?
3. ✅ Calling from internal network?

**Correct usage:**

```bash
curl -H "X-Internal-API-Key: ${INTERNAL_API_KEY}" \
  http://fiber-service:8094/api/v1/fibers/internal/validate
```

---

## 📊 ERROR STATISTICS

### Common Errors (Production)

```
VALIDATION_ERROR:        40% (user input errors)
FIBER_NOT_FOUND:         25% (invalid IDs)
DUPLICATE_RESOURCE:      15% (code conflicts)
INVALID_COMPOSITION:     10% (blend validation)
FORBIDDEN:               5%  (permission/immutable)
Other:                   5%
```

---

## 🔗 Related Documentation

- [Endpoints](./ENDPOINTS.md) - Complete endpoint reference
- [Authentication](./AUTHENTICATION.md) - Auth guide
- [Security](../../../SECURITY.md) - System security

---

**Last Updated:** 2025-10-20  
**Maintained By:** Fabric Management Team

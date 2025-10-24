# 🔌 Fiber Service - API Documentation

**Version:** 1.0.0  
**Base URL:** `http://localhost:8094/api/v1/fibers`  
**Last Updated:** 2025-10-20

---

## 📋 ENDPOINT INDEX

| Document                                 | Description                               |
| ---------------------------------------- | ----------------------------------------- |
| [ENDPOINTS.md](./ENDPOINTS.md)           | Complete endpoint reference with examples |
| [AUTHENTICATION.md](./AUTHENTICATION.md) | Authentication & authorization guide      |
| [ERROR_HANDLING.md](./ERROR_HANDLING.md) | Error codes & troubleshooting             |
| [EXAMPLES.md](./EXAMPLES.md)             | Real-world usage examples                 |

---

## ⚡ QUICK START

```bash
# Get default fibers (public - no auth)
curl http://localhost:8094/api/v1/fibers/default

# Create fiber (requires TENANT_ADMIN or SUPER_ADMIN)
curl -X POST http://localhost:8094/api/v1/fibers \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "BM",
    "name": "Bamboo",
    "category": "REGENERATED",
    "compositionType": "PURE"
  }'
```

---

## 🎯 ENDPOINT CATEGORIES

### Public Endpoints (No Auth)

- `GET /default` - Get default fibers

### Authenticated Endpoints

- `GET /{id}` - Get fiber details
- `GET /` - List fibers (paginated)
- `GET /search` - Search fibers
- `GET /category/{category}` - Filter by category

### Admin Endpoints (TENANT_ADMIN, SUPER_ADMIN)

- `POST /` - Create pure fiber
- `POST /blend` - Create blend fiber
- `PATCH /{id}` - Update fiber properties
- `DELETE /{id}` - Deactivate fiber

### Internal Endpoints (Service-to-Service)

- `POST /internal/validate` - Validate composition
- `GET /internal/batch` - Batch fiber lookup
- `GET /internal/exists/{code}` - Check fiber exists

---

**See:** [ENDPOINTS.md](./ENDPOINTS.md) for complete documentation

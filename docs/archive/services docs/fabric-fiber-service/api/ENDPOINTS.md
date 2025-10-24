# 🔌 Fiber Service - Complete Endpoint Reference

**Version:** 1.0.0  
**Base URL:** `/api/v1/fibers`

---

## 📋 TABLE OF CONTENTS

1. [Create Pure Fiber](#1-create-pure-fiber)
2. [Create Blend Fiber](#2-create-blend-fiber)
3. [Get Fiber by ID](#3-get-fiber-by-id)
4. [List Fibers (Paginated)](#4-list-fibers-paginated)
5. [Get Default Fibers](#5-get-default-fibers)
6. [Search Fibers](#6-search-fibers)
7. [Filter by Category](#7-filter-by-category)
8. [Update Fiber Properties](#8-update-fiber-properties)
9. [Deactivate Fiber](#9-deactivate-fiber)
10. [Validate Composition (Internal)](#10-validate-composition-internal)
11. [Batch Fiber Lookup (Internal)](#11-batch-fiber-lookup-internal)
12. [Check Fiber Exists (Internal)](#12-check-fiber-exists-internal)

---

## 1. Create Pure Fiber

**Endpoint:** `POST /api/v1/fibers`  
**Auth:** TENANT_ADMIN, SUPER_ADMIN  
**Purpose:** Create a new pure (100%) fiber definition

### Request

```json
{
  "code": "BM",
  "name": "Bamboo",
  "category": "REGENERATED",
  "compositionType": "PURE",
  "originType": "IMPORTED",
  "sustainabilityType": "ORGANIC",
  "property": {
    "stapleLength": 38.5,
    "fineness": 1.8,
    "tenacity": 3.2,
    "moistureRegain": 11.0,
    "color": "Natural White"
  }
}
```

### Response (201 Created)

```json
{
  "success": true,
  "data": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Fiber created successfully",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

### cURL Example

```bash
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

## 2. Create Blend Fiber

**Endpoint:** `POST /api/v1/fibers/blend`  
**Auth:** TENANT_ADMIN, SUPER_ADMIN  
**Purpose:** Create a blend (multi-component) fiber

### Request

```json
{
  "code": "COPE6040",
  "name": "Cotton/Polyester 60/40",
  "category": "BLEND",
  "compositionType": "BLEND",
  "components": [
    {
      "fiberCode": "CO",
      "percentage": 60.0,
      "sustainabilityType": "ORGANIC"
    },
    {
      "fiberCode": "PE",
      "percentage": 40.0,
      "sustainabilityType": "RECYCLED"
    }
  ],
  "reusable": true
}
```

### Validation Rules

```
✅ Minimum 2 components
✅ Total percentage = 100%
✅ No duplicate fiber codes
✅ All component fibers must be ACTIVE
✅ Component fibers must exist
```

### Response (201 Created)

```json
{
  "success": true,
  "data": "650e8400-e29b-41d4-a716-446655440001",
  "message": "Blend fiber created successfully",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

---

## 3. Get Fiber by ID

**Endpoint:** `GET /api/v1/fibers/{fiberId}`  
**Auth:** Authenticated users  
**Purpose:** Retrieve detailed fiber information

### Request

```
GET /api/v1/fibers/550e8400-e29b-41d4-a716-446655440000
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "code": "CO",
    "name": "Cotton",
    "category": "NATURAL",
    "compositionType": "PURE",
    "originType": "DOMESTIC",
    "sustainabilityType": "ORGANIC",
    "status": "ACTIVE",
    "isDefault": true,
    "reusable": true,
    "property": {
      "stapleLength": 28.5,
      "fineness": 1.5,
      "tenacity": 3.5,
      "moistureRegain": 8.5,
      "color": "White"
    },
    "components": null,
    "createdAt": "2025-01-15T10:00:00Z"
  },
  "timestamp": "2025-10-20T10:30:00Z"
}
```

---

## 4. List Fibers (Paginated)

**Endpoint:** `GET /api/v1/fibers?page={page}&size={size}`  
**Auth:** Authenticated users  
**Purpose:** List all fibers with pagination

### Request

```
GET /api/v1/fibers?page=0&size=20&sort=code,asc
```

### Response (200 OK)

```json
{
  "content": [
    {
      "id": "...",
      "code": "AC",
      "name": "Acrylic",
      "category": "SYNTHETIC"
    },
    {
      "id": "...",
      "code": "CO",
      "name": "Cotton",
      "category": "NATURAL"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3,
  "last": false
}
```

---

## 5. Get Default Fibers

**Endpoint:** `GET /api/v1/fibers/default`  
**Auth:** Public (no authentication required)  
**Purpose:** Retrieve system default fibers (9 fibers)

### Response (200 OK)

```json
{
  "success": true,
  "data": [
    {
      "id": "...",
      "code": "CO",
      "name": "Cotton",
      "category": "NATURAL",
      "compositionType": "PURE",
      "status": "ACTIVE",
      "isDefault": true
    },
    {
      "id": "...",
      "code": "PE",
      "name": "Polyester",
      "category": "SYNTHETIC",
      "compositionType": "PURE",
      "status": "ACTIVE",
      "isDefault": true
    }
    // ... 7 more default fibers
  ],
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Default Fibers:**

- CO (Cotton)
- PE (Polyester)
- WO (Wool)
- PA (Nylon/Polyamide)
- VI (Viscose)
- EA (Elastane/Spandex)
- PP (Polypropylene)
- LI (Linen)
- SI (Silk)

---

## 6. Search Fibers

**Endpoint:** `GET /api/v1/fibers/search?query={searchTerm}`  
**Auth:** Authenticated users  
**Purpose:** Search fibers by code or name

### Request

```
GET /api/v1/fibers/search?query=cotton
```

### Response (200 OK)

```json
{
  "success": true,
  "data": [
    {
      "id": "...",
      "code": "CO",
      "name": "Cotton",
      "category": "NATURAL"
    },
    {
      "id": "...",
      "code": "COPE6040",
      "name": "Cotton/Polyester 60/40",
      "category": "BLEND"
    }
  ],
  "timestamp": "2025-10-20T10:30:00Z"
}
```

**Search Features:**

- Case-insensitive
- Searches both code and name
- Returns summary (not full details)

---

## 7. Filter by Category

**Endpoint:** `GET /api/v1/fibers/category/{category}`  
**Auth:** Authenticated users  
**Purpose:** Get all fibers in a category

### Request

```
GET /api/v1/fibers/category/NATURAL
```

### Valid Categories

- `NATURAL` (Cotton, Wool, Silk, Linen)
- `SYNTHETIC` (Polyester, Nylon, Acrylic)
- `REGENERATED` (Viscose, Modal, Lyocell)
- `MINERAL` (Glass fiber, Metal fiber)
- `BLEND` (Multi-component fibers)

### Response (200 OK)

```json
{
  "success": true,
  "data": [
    {
      "id": "...",
      "code": "CO",
      "name": "Cotton",
      "category": "NATURAL"
    },
    {
      "id": "...",
      "code": "WO",
      "name": "Wool",
      "category": "NATURAL"
    }
  ],
  "timestamp": "2025-10-20T10:30:00Z"
}
```

---

## 8. Update Fiber Properties

**Endpoint:** `PATCH /api/v1/fibers/{fiberId}`  
**Auth:** TENANT_ADMIN, SUPER_ADMIN  
**Purpose:** Update physical/chemical properties

**Note:** Cannot update default fibers (isDefault=true)

### Request

```json
{
  "sustainabilityType": "RECYCLED",
  "stapleLength": 30.0,
  "fineness": 1.6,
  "tenacity": 3.8,
  "moistureRegain": 9.0,
  "color": "Bleached White"
}
```

### Response (200 OK)

```json
{
  "success": true,
  "data": null,
  "message": "Fiber updated successfully",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

---

## 9. Deactivate Fiber

**Endpoint:** `DELETE /api/v1/fibers/{fiberId}`  
**Auth:** SUPER_ADMIN only  
**Purpose:** Soft-delete fiber (status → INACTIVE)

**Note:** Cannot deactivate default fibers (isDefault=true)

### Request

```
DELETE /api/v1/fibers/650e8400-e29b-41d4-a716-446655440001
```

### Response (200 OK)

```json
{
  "success": true,
  "data": null,
  "message": "Fiber deactivated successfully",
  "timestamp": "2025-10-20T10:30:00Z"
}
```

---

## 10. Validate Composition (Internal)

**Endpoint:** `POST /api/v1/fibers/internal/validate`  
**Auth:** Internal API Key  
**Purpose:** Validate fiber composition (used by Yarn Service)

### Request

```json
["CO", "PE", "WO", "INVALID_CODE"]
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "valid": false,
    "activeFibers": ["CO", "PE", "WO"],
    "inactiveFibers": [],
    "notFoundFibers": ["INVALID_CODE"],
    "message": "Some fibers are invalid"
  },
  "timestamp": "2025-10-20T10:30:00Z"
}
```

---

## 11. Batch Fiber Lookup (Internal)

**Endpoint:** `GET /api/v1/fibers/internal/batch?fiberCodes={codes}`  
**Auth:** Internal API Key  
**Purpose:** Get multiple fibers in single request

### Request

```
GET /api/v1/fibers/internal/batch?fiberCodes=CO,PE,WO
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "CO": {
      "id": "...",
      "code": "CO",
      "name": "Cotton",
      "category": "NATURAL"
    },
    "PE": {
      "id": "...",
      "code": "PE",
      "name": "Polyester",
      "category": "SYNTHETIC"
    },
    "WO": {
      "id": "...",
      "code": "WO",
      "name": "Wool",
      "category": "NATURAL"
    }
  },
  "timestamp": "2025-10-20T10:30:00Z"
}
```

---

## 12. Check Fiber Exists (Internal)

**Endpoint:** `GET /api/v1/fibers/internal/exists/{fiberCode}`  
**Auth:** Internal API Key  
**Purpose:** Quick existence check

### Request

```
GET /api/v1/fibers/internal/exists/CO
```

### Response (200 OK)

```json
{
  "success": true,
  "data": true,
  "timestamp": "2025-10-20T10:30:00Z"
}
```

---

## 🔗 Related Documentation

- [Authentication Guide](./AUTHENTICATION.md)
- [Error Handling](./ERROR_HANDLING.md)
- [Usage Examples](./EXAMPLES.md)
- [Service Overview](../fabric-fiber-service.md)

---

**Last Updated:** 2025-10-20  
**Maintained By:** Fabric Management Team

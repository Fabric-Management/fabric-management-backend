# 💡 Fiber Service - Real-World Examples

**Version:** 1.0.0  
**Last Updated:** 2025-10-20

---

## 🎯 SCENARIO 1: Create Cotton/Polyester Blend

### Business Requirement

> "We need to create a 60/40 cotton-polyester blend for our new product line"

### Step 1: Check if component fibers exist

```bash
# Check Cotton
curl http://localhost:8094/api/v1/fibers/internal/exists/CO \
  -H "X-Internal-API-Key: ${INTERNAL_API_KEY}"

# Response: {"success": true, "data": true}

# Check Polyester
curl http://localhost:8094/api/v1/fibers/internal/exists/PE \
  -H "X-Internal-API-Key: ${INTERNAL_API_KEY}"

# Response: {"success": true, "data": true}
```

### Step 2: Create blend fiber

```bash
curl -X POST http://localhost:8094/api/v1/fibers/blend \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
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
  }'
```

### Step 3: Verify creation

```bash
curl http://localhost:8094/api/v1/fibers/search?query=COPE6040 \
  -H "Authorization: Bearer ${JWT_TOKEN}"
```

---

## 🎯 SCENARIO 2: Yarn Service Validates Composition

### Business Requirement

> "Yarn Service needs to validate fiber composition before creating yarn"

### Implementation (Yarn Service side)

```java
@Service
@RequiredArgsConstructor
public class YarnService {

    private final FiberServiceClient fiberClient;

    @Transactional
    public UUID createYarn(CreateYarnRequest request) {
        // Validate all fiber codes exist and are ACTIVE
        List<String> fiberCodes = request.getComposition().stream()
            .map(YarnCompositionDto::getFiberCode)
            .toList();

        FiberValidationResponse validation =
            fiberClient.validateComposition(fiberCodes);

        if (!validation.isValid()) {
            throw new InvalidCompositionException(
                "Invalid fibers: " + validation.getNotFoundFibers()
            );
        }

        // Proceed with yarn creation...
    }
}
```

### API Call

```http
POST /api/v1/fibers/internal/validate
Content-Type: application/json
X-Internal-API-Key: ${INTERNAL_API_KEY}

["CO", "PE", "WO", "EA"]
```

### Response

```json
{
  "success": true,
  "data": {
    "valid": true,
    "activeFibers": ["CO", "PE", "WO", "EA"],
    "inactiveFibers": [],
    "notFoundFibers": [],
    "message": "All fibers are valid"
  }
}
```

---

## 🎯 SCENARIO 3: Batch Fiber Lookup for Display

### Business Requirement

> "Order Service needs to display fiber names for 50 yarn records"

### Without Batch API (N+1 Problem)

```java
// ❌ BAD: 50 HTTP calls!
for (Yarn yarn : yarns) {
    FiberResponse fiber = fiberClient.getFiber(yarn.getFiberCode());
    yarn.setFiberName(fiber.getName());
}
// Performance: 50 × 100ms = 5000ms (5 seconds!)
```

### With Batch API (Optimized)

```java
// ✅ GOOD: 1 HTTP call!
List<String> fiberCodes = yarns.stream()
    .map(Yarn::getFiberCode)
    .distinct()
    .toList();

Map<String, FiberResponse> fibers =
    fiberClient.getFibersBatch(fiberCodes);

for (Yarn yarn : yarns) {
    FiberResponse fiber = fibers.get(yarn.getFiberCode());
    yarn.setFiberName(fiber.getName());
}
// Performance: 1 × 100ms = 100ms (0.1 second!)
// 50x faster! 🚀
```

### API Call

```http
GET /api/v1/fibers/internal/batch?fiberCodes=CO,PE,WO,EA
X-Internal-API-Key: ${INTERNAL_API_KEY}
```

### Response

```json
{
  "success": true,
  "data": {
    "CO": { "id": "...", "code": "CO", "name": "Cotton" },
    "PE": { "id": "...", "code": "PE", "name": "Polyester" },
    "WO": { "id": "...", "code": "WO", "name": "Wool" },
    "EA": { "id": "...", "code": "EA", "name": "Elastane" }
  }
}
```

---

## 🎯 SCENARIO 4: Search Fibers for Autocomplete

### Business Requirement

> "Product creation form needs fiber autocomplete"

### Frontend Implementation

```typescript
// React/TypeScript example
async function searchFibers(query: string): Promise<Fiber[]> {
  const response = await fetch(
    `http://localhost:8094/api/v1/fibers/search?query=${query}`,
    {
      headers: {
        Authorization: `Bearer ${getJwtToken()}`,
      },
    }
  );

  const result = await response.json();
  return result.data;
}

// Usage in autocomplete
<Autocomplete
  loadOptions={query => searchFibers(query)}
  placeholder="Search fibers..."
/>;
```

### API Calls & Responses

```bash
# User types "co"
GET /api/v1/fibers/search?query=co

# Response:
[
  {"code": "CO", "name": "Cotton", "category": "NATURAL"},
  {"code": "COPE6040", "name": "Cotton/Polyester 60/40", "category": "BLEND"}
]

# User types "poly"
GET /api/v1/fibers/search?query=poly

# Response:
[
  {"code": "PE", "name": "Polyester", "category": "SYNTHETIC"},
  {"code": "PP", "name": "Polypropylene", "category": "SYNTHETIC"}
]
```

---

## 🎯 SCENARIO 5: Category Filter for UI

### Business Requirement

> "Show only natural fibers in dropdown"

### Frontend Implementation

```typescript
async function getNaturalFibers(): Promise<Fiber[]> {
  const response = await fetch(
    `http://localhost:8094/api/v1/fibers/category/NATURAL`,
    {
      headers: {
        Authorization: `Bearer ${getJwtToken()}`,
      },
    }
  );

  const result = await response.json();
  return result.data;
}
```

### Response

```json
{
  "success": true,
  "data": [
    { "code": "CO", "name": "Cotton", "category": "NATURAL" },
    { "code": "WO", "name": "Wool", "category": "NATURAL" },
    { "code": "LI", "name": "Linen", "category": "NATURAL" },
    { "code": "SI", "name": "Silk", "category": "NATURAL" }
  ]
}
```

---

## 🎯 SCENARIO 6: Handle Validation Errors

### Business Requirement

> "Show clear error messages when blend creation fails"

### Frontend Implementation

```typescript
async function createBlend(blendData: CreateBlendRequest) {
  try {
    const response = await fetch("http://localhost:8094/api/v1/fibers/blend", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${getJwtToken()}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(blendData),
    });

    const result = await response.json();

    if (!result.success) {
      // Show error to user
      showError(result.message);
    }
  } catch (error) {
    showError("Network error - please try again");
  }
}
```

### Error Handling

```typescript
// Example error responses
switch (errorCode) {
  case "INVALID_COMPOSITION":
    message = "Blend percentages must total 100%";
    break;
  case "INACTIVE_FIBER":
    message = "One or more component fibers are inactive";
    break;
  case "DUPLICATE_RESOURCE":
    message = "Fiber code already exists";
    break;
  default:
    message = "An error occurred";
}
```

---

## 🎯 SCENARIO 7: Pagination for Large Lists

### Business Requirement

> "Display 20 fibers per page, allow sorting"

### API Call

```bash
# Page 1 (first 20)
GET /api/v1/fibers?page=0&size=20&sort=code,asc

# Page 2 (next 20)
GET /api/v1/fibers?page=1&size=20&sort=code,asc

# Sort by name descending
GET /api/v1/fibers?page=0&size=20&sort=name,desc
```

### Response

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "last": false,
  "first": true
}
```

### Frontend Implementation

```typescript
async function loadFibers(page: number, size: number) {
  const response = await fetch(
    `http://localhost:8094/api/v1/fibers?page=${page}&size=${size}`,
    {
      headers: {
        Authorization: `Bearer ${getJwtToken()}`,
      },
    }
  );

  return await response.json();
}
```

---

## 📚 Related Documentation

- [Endpoints](./ENDPOINTS.md) - Complete API reference
- [Authentication](./AUTHENTICATION.md) - Auth guide
- [Integration Guide](../guides/yarn-service-integration.md)

---

**Last Updated:** 2025-10-20  
**Maintained By:** Fabric Management Team

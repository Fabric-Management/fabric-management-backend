# 🧵 Fiber Module - Complete Design

**Last Updated:** 2025-10-28  
**Status:** ✅ PRODUCTION-READY

---

## 📋 MODULE OVERVIEW

The Fiber module consists of **TWO critical sub-modules:**

1. **`masterdata/fiber`** - What is fiber? (definitions, categories, attributes)
2. **`execution/fiber`** - How much fiber? (batches, inventory, tracking)

### Key Concept

```
Fiber Masterdata = "Cotton 100%" definition (reusable, system-wide)
Fiber Batch     = "500 kg of Cotton received on 2025-01-15 from Supplier X"
```

---

## 🏗️ MASTERDATA MODULE

### Purpose

Define fiber types, compositions, and properties.

### Key Entities

#### **Fiber** (`production/masterdata/fiber`)

- Represents a fiber type (e.g., "Cotton 100%", "Cotton 60% + Viscose 40%")
- Links to `Material` base entity
- Has reference table links: `FiberCategory`, `FiberIsoCode`

#### **FiberComposition**

- Many-to-many relationship for blended fibers
- Tracks percentage composition (must sum to 100%)

#### **Reference Tables**

- `FiberCategory`: NATURAL, SYNTHETIC, REGENERATED, MIXED_BLEND, etc.
- `FiberIsoCode`: ISO 2076 codes (CO, PES, CLY, etc.)

### Key Features

- ✅ 100% pure fibers are system-defined (SYSTEM_TENANT_ID)
- ✅ Users create blended fibers using system fibers
- ✅ Circular reference prevention (no blending already-blended fibers)
- ✅ Automatic naming for blended fibers (e.g., "Cotton 60% + Viscose 40%")
- ✅ Duplicate composition detection
- ✅ Composition percentages must sum to 100%

### API Endpoints

```http
GET  /api/production/fibers                    # List all fibers
GET  /api/production/fibers/{id}               # Get fiber details
GET  /api/production/fibers/search?name=       # Search by name
GET  /api/production/fibers/categories          # List categories
POST /api/production/fibers/blended             # Create blended fiber
```

### Create Blended Fiber

```http
POST /api/production/fibers/blended

{
  "materialId": "uuid-of-material",           # Auto-created for you
  "fiberCategoryId": "uuid-of-mixed-blend",
  "fiberGrade": "Premium",
  "composition": {
    "cotton_fiber_uuid": 60.0,
    "viscose_fiber_uuid": 40.0
  }
}

Response:
{
  "id": "uuid",
  "fiberName": "Cotton 60% + Viscose 40%",    # Auto-generated
  "materialId": "uuid",
  "fiberCategoryId": "uuid",
  "fiberGrade": "Premium",
  "composition": {
    "Cotton (100%)": 60.0,
    "Viscose (100%)": 40.0
  },
  "status": "APPROVED"
}
```

---

## 🏭 EXECUTION MODULE

### Purpose

Track physical fiber batches/lots for production.

### Key Entity

#### **FiberBatch** (`production/execution/fiber`)

- Represents a physical lot/batch of fiber
- Links to `Fiber` masterdata via `fiberId`
- Has unique `batchCode` for traceability
- Tracks quantity, warehouse location, status

### Batch Lifecycle

```
NEW → RESERVED → IN_USE → DEPLETED
```

### Key Features

- ✅ Quantity tracking with precision (15,3)
- ✅ Reserved quantity tracking (prevents over-booking)
- ✅ Consumed quantity tracking
- ✅ Supplier batch code support
- ✅ Warehouse location tracking
- ✅ Status-based lifecycle management
- ✅ Optimistic locking (prevents race conditions)
- ✅ Automatic depletion when quantity = 0

### API Endpoints

```http
POST /api/production/batches/fiber                    # Create batch
GET  /api/production/batches/fiber                    # List all batches
GET  /api/production/batches/fiber/{id}               # Get batch details
GET  /api/production/batches/fiber/fiber/{fiberId}    # Get batches for fiber
POST /api/production/batches/fiber/{id}/reserve       # Reserve quantity
POST /api/production/batches/fiber/{id}/release       # Release quantity
POST /api/production/batches/fiber/{id}/consume       # Consume quantity
```

### Reserve Quantity (Production-Order)

```http
POST /api/production/batches/fiber/{batchId}/reserve

{
  "quantity": 200.000
}

Purpose: Prevents multiple orders from competing for same batch
Status: NEW → RESERVED
```

### Consume Quantity

```http
POST /api/production/batches/fiber/{batchId}/consume

{
  "quantity": 150.000
}

Purpose: Actually use fiber in production
Logic: Consumes from reserved first, then available
Status: IN_USE → DEPLETED (if all consumed)
```

---

## 🔗 RELATIONSHIP DIAGRAM

```
┌─────────────────────────────────────────────────────────────┐
│                    MATERIAL (Base)                          │
│  - id, tenant_id, uid                                       │
│  - material_type, unit, is_active                           │
└────────────────────────┬────────────────────────────────────┘
                        │
                        │ (one-to-one)
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    FIBER (Masterdata)                       │
│  - fiber_name: "Cotton 60% + Viscose 40%"                   │
│  - fiber_grade, fineness, strength                           │
│  - fiber_category_id, fiber_iso_code_id                      │
│  - status: NEW, APPROVED, DISCONTINUED                       │
└────────────────────────┬────────────────────────────────────┘
                        │
                        │ (one-to-many)
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                 FIBER_BATCH (Execution)                     │
│  - batch_code: "BATCH-2025-001"                             │
│  - supplier_batch_code: "SUP-12345"                         │
│  - quantity: 500.000 kg (total)                             │
│  - reserved_quantity: 100.000 kg                            │
│  - consumed_quantity: 50.000 kg                             │
│  - available_quantity: 350.000 kg                          │
│  - warehouse_location: "WH-A-01"                            │
│  - status: NEW, RESERVED, IN_USE, DEPLETED                  │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│              FIBER_COMPOSITION (Masterdata)                  │
│  - Maps blended fibers to their base components            │
│  - Example: "Cotton 60%" = 60% of Cotton fiber_id           │
│  - Must sum to 100%                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 USAGE SCENARIOS

### Scenario 1: Create a Blended Fiber (Masterdata)

```
1. User wants to create "Cotton 60% + Viscose 40%" fiber
2. System checks if base fibers exist (Cotton 100%, Viscose 100%)
3. System validates composition sums to 100%
4. System prevents circular reference (no blending blended fibers)
5. System creates blended fiber with composition map
6. System auto-generates name "Cotton 60% + Viscose 40%"
```

### Scenario 2: Receive Fiber Batch (Execution)

```
1. Supplier delivers 500 kg of "Cotton 100%" fiber
2. Warehouse creates batch with code "BATCH-2025-001"
3. Batch status: NEW
4. Reserved: 0 kg, Consumed: 0 kg, Available: 500 kg
5. Links to Fiber (masterdata) via fiberId
```

### Scenario 3: Reserve & Consume Fiber in Production

```
1. Production order needs 200 kg of "Cotton 60% + Viscose 40%"
2. System finds batch "BATCH-2025-001" with 500 kg available
3. System reserves 200 kg → status: RESERVED
4. Production consumes 150 kg → consumes from reserved
5. Reserved: 50 kg, Consumed: 150 kg, Available: 300 kg
6. Batch status: IN_USE
```

### Scenario 4: Batch Depleted

```
1. Production needs 300 kg more
2. Batch has 300 kg available
3. System consumes 300 kg
4. Consumed: 500 kg (all)
5. Status automatically: DEPLETED
```

---

## 📊 DATABASE SCHEMA

### Masterdata Schema

- `production.prod_material` (base for all materials)
- `production.prod_fiber` (fiber-specific details)
- `production.prod_fiber_composition` (blended fiber components)

### Reference Schema

- `production.prod_fiber_category` (NATURAL, SYNTHETIC, MIXED_BLEND)
- `production.prod_fiber_iso_code` (ISO 2076 codes)

### Execution Schema

- `production.production_execution_fiber_batch` (physical batches)

---

## 🔐 SECURITY & MULTI-TENANCY

### Tenant Isolation

All queries filtered by `tenantId`:

```java
UUID tenantId = TenantContext.getCurrentTenantId();
fiberRepository.findByTenantId(tenantId);
```

### System Fibers

100% pure fibers are created with `SYSTEM_TENANT_ID` and accessible by all tenants.

---

## 🚀 NEXT STEPS

1. ✅ **Test Execution Module** - Run application and verify batch operations
2. ✅ **Update Postman Collection** - Add Fiber Batch endpoints
3. **Add Yarn Module** - Similar structure for yarn masterdata + execution
4. **Add Fabric Module** - Similar structure for fabric masterdata + execution
5. **Add Planning Module** - Capacity, scheduling, work centers
6. **Add Quality Module** - Inspection, testing, results

---

## 📁 FILE STRUCTURE

### Masterdata

```
masterdata/fiber/
├── domain/
│   ├── Fiber.java                  # Main entity
│   ├── FiberComposition.java       # Composition mapping
│   ├── reference/
│   │   ├── FiberCategory.java      # NATURAL, SYNTHETIC, etc.
│   │   └── FiberIsoCode.java        # ISO 2076 codes
│   └── event/
│       ├── FiberCreatedEvent.java
│       └── FiberCompositionChangedEvent.java
├── dto/
│   ├── FiberDto.java
│   ├── CreateBlendedFiberRequest.java
│   └── FiberCategoryDto.java
├── app/
│   ├── FiberService.java           # Business logic
│   ├── FiberValidationService.java  # Validation
│   └── FiberCompositionService.java # Composition management
├── infra/repository/
│   ├── FiberRepository.java
│   ├── FiberCompositionRepository.java
│   └── FiberCategoryRepository.java
└── api/controller/
    └── FiberController.java
```

### Execution

```
execution/fiber/
├── domain/
│   ├── FiberBatch.java             # Main entity with reservation logic
│   └── FiberBatchStatus.java       # NEW, RESERVED, IN_USE, DEPLETED
├── dto/
│   ├── FiberBatchDto.java
│   └── CreateFiberBatchRequest.java
├── app/
│   └── FiberBatchService.java      # Business logic + optimistic locking
├── infra/repository/
│   └── FiberBatchRepository.java   # @Lock(LockModeType.OPTIMISTIC)
└── api/controller/
    └── FiberBatchController.java
```

---

## ✅ COMPLETED FEATURES

### Masterdata

- ✅ System-defined 100% pure fibers
- ✅ User-created blended fibers
- ✅ Circular reference prevention
- ✅ Automatic naming
- ✅ Composition validation (sums to 100%)
- ✅ Tenant isolation

### Execution

- ✅ Reserved quantity tracking
- ✅ Consumed quantity tracking
- ✅ Optimistic locking
- ✅ Status-based lifecycle
- ✅ Automatic depletion
- ✅ Exception handling
- ✅ Global exception handler
- ✅ Custom exceptions (404, 400, 409)

---

**See Also:**

- [Fiber Batch Execution Details](./execution/EXECUTION_FIBER_BATCH.md)
- [Fiber Module Implementation](../execution/fiber/README.md)

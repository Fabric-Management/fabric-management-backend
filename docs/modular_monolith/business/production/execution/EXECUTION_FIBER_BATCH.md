# 🏭 Fiber Batch Execution Module

**Location:** `production/execution/fiber`  
**Status:** ✅ PRODUCTION-READY  
**Last Updated:** 2025-10-28

---

## 📋 OVERVIEW

The **Fiber Batch** module tracks physical fiber inventory (batches/lots) for production.

### Key Concept

```
Fiber (Masterdata)  = "Cotton 60% + Viscose 40%" definition
Fiber Batch         = "500 kg of Cotton batch received on 2025-01-15"
```

---

## 🏗️ ARCHITECTURE

### Domain Model

```java
FiberBatch {
    fiberId: UUID                      // Links to Fiber masterdata
    batchCode: "BATCH-2025-001"       // Unique batch identifier
    supplierBatchCode: String         // Supplier's batch code
    quantity: 500.000                 // Total quantity (DECIMAL 15,3)
    reservedQuantity: 100.000         // Reserved for production orders
    consumedQuantity: 50.000          // Already consumed in production
    unit: "kg"                        // Measurement unit
    status: FiberBatchStatus          // NEW → RESERVED → IN_USE → DEPLETED
    warehouseLocation: "WH-A-01"      // Physical location
    productionDate: Instant           // When batch was produced
    expiryDate: Instant               // Expiry date
}
```

### Quantity Tracking

```
availableQuantity = quantity - reservedQuantity - consumedQuantity

Example:
- Total: 500 kg
- Reserved: 100 kg
- Consumed: 50 kg
- Available: 350 kg
```

---

## 🔄 STATUS LIFECYCLE

```
┌─────┐    reserve()       ┌──────────┐    markInUse()     ┌────────┐
│ NEW │──────────────────► │ RESERVED │──────────────────► │IN_USE │
└─────┘                      └──────────┘                    └────┬───┘
                                                                 │
                                               consume() → quantity = 0
                                                                 │
                                                                 ▼
                                                           ┌──────────┐
                                                           │ DEPLETED │
                                                           └──────────┘
```

### Status Transitions

| From     | To       | Method        | Trigger                  |
| -------- | -------- | ------------- | ------------------------ |
| NEW      | RESERVED | `reserve()`   | Reserve for production   |
| RESERVED | NEW      | `release()`   | Cancel reservation       |
| NEW      | IN_USE   | `markInUse()` | Start using immediately  |
| RESERVED | IN_USE   | `consume()`   | Start consuming reserved |
| IN_USE   | DEPLETED | `consume()`   | All quantity consumed    |

---

## 🔧 KEY OPERATIONS

### 1. Create Batch

```http
POST /api/production/batches/fiber

{
  "fiberId": "uuid-of-fiber",
  "batchCode": "BATCH-2025-001",
  "supplierBatchCode": "SUP-12345",
  "quantity": 500.000,
  "unit": "kg",
  "warehouseLocation": "WH-A-01",
  "productionDate": "2025-01-15T00:00:00Z",
  "expiryDate": "2026-01-15T00:00:00Z",
  "remarks": "High-grade cotton"
}
```

### 2. Reserve Quantity

Prevents race conditions when multiple production orders compete for the same batch.

```http
POST /api/production/batches/{batchId}/reserve

{
  "quantity": 100.000
}
```

**Business Rules:**

- Cannot reserve more than available quantity
- Status changes: NEW → RESERVED
- Uses optimistic locking to prevent concurrent updates

### 3. Release Reserved Quantity

Cancels a previous reservation (e.g., production order cancelled).

```http
POST /api/production/batches/{batchId}/release

{
  "quantity": 50.000
}
```

**Business Rules:**

- Cannot release more than reserved
- Status changes: RESERVED → NEW (if all released)

### 4. Consume Quantity

Actually uses the fiber in production. Consumes from reserved first, then available.

```http
POST /api/production/batches/{batchId}/consume

{
  "quantity": 150.000
}
```

**Business Rules:**

- First consumes from reserved quantity
- Then consumes from available quantity
- Status auto-updates: DEPLETED when consumed = quantity
- Cannot consume more than available

### 5. List Batches

```http
GET /api/production/batches/fiber

Response:
[
  {
    "id": "uuid",
    "fiberId": "uuid",
    "batchCode": "BATCH-2025-001",
    "quantity": 500.000,
    "reservedQuantity": 100.000,
    "consumedQuantity": 50.000,
    "availableQuantity": 350.000,
    "unit": "kg",
    "status": "IN_USE",
    "warehouseLocation": "WH-A-01",
    "productionDate": "2025-01-15T00:00:00Z",
    "expiryDate": "2026-01-15T00:00:00Z"
  }
]
```

### 6. Get Batches by Fiber

```http
GET /api/production/batches/fiber/fiber/{fiberId}

Response: List of all batches for a specific fiber
```

---

## 🗄️ DATABASE SCHEMA

```sql
CREATE TABLE production.production_execution_fiber_batch (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    fiber_id UUID NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    supplier_batch_code VARCHAR(100),
    quantity DECIMAL(15,3) NOT NULL,
    reserved_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    consumed_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    unit VARCHAR(20) NOT NULL,
    production_date TIMESTAMPTZ,
    expiry_date TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,  -- NEW, RESERVED, IN_USE, DEPLETED
    warehouse_location VARCHAR(100),
    remarks TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    version BIGINT NOT NULL,

    CONSTRAINT fk_batch_fiber FOREIGN KEY (fiber_id) REFERENCES production.prod_fiber(id),
    CONSTRAINT uq_batch_tenant_code UNIQUE (tenant_id, batch_code),
    CONSTRAINT uq_batch_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT ck_qty_nonneg CHECK (quantity >= 0 AND reserved_quantity >= 0 AND consumed_quantity >= 0),
    CONSTRAINT ck_qty_bounds CHECK (reserved_quantity + consumed_quantity <= quantity)
);

CREATE INDEX idx_batch_tenant_fiber_status ON production.production_execution_fiber_batch (tenant_id, fiber_id, status);
CREATE INDEX idx_batch_tenant_loc_status ON production.production_execution_fiber_batch (tenant_id, warehouse_location, status);
```

### Key Constraints

- **Tenant Isolation:** All queries filtered by `tenant_id`
- **Unique Batch Code:** `UNIQUE (tenant_id, batch_code)` per tenant
- **Quantity Integrity:** `reserved + consumed ≤ total`
- **Optimistic Locking:** `version` field prevents concurrent update conflicts

---

## 🎯 USAGE SCENARIOS

### Scenario 1: Create and Reserve Batch

```
1. Warehouse receives 500 kg of "Cotton 100%" from supplier
2. Create batch: BATCH-2025-001
3. Production order needs 200 kg → RESERVE 200 kg
4. Batch status: RESERVED
5. Available: 300 kg (500 - 200 reserved)
```

### Scenario 2: Consume Reserved Quantity

```
1. Batch: 500 kg total, 200 kg reserved, 0 consumed
2. Production consumes: 150 kg
3. From reserved: 150 kg
4. Reserved: 50 kg remaining
5. Status: IN_USE (not depleted yet)
```

### Scenario 3: Batch Depleted

```
1. Batch: 500 kg total, 200 reserved, 300 consumed
2. Production needs: 200 kg more
3. Consume: 150 kg from reserved + 50 kg from available
4. Consumed: 500 kg (all)
5. Status: DEPLETED (automatic)
```

---

## 🔐 SECURITY & MULTI-TENANCY

### Tenant Isolation

All operations filtered by `tenantId`:

```java
UUID tenantId = TenantContext.getCurrentTenantId();
fiberBatchRepository.findByTenantIdAndBatchCode(tenantId, batchCode);
```

### Roles

- `ADMIN` - Full access
- `PRODUCTION_MANAGER` - Create, reserve, consume batches
- `WAREHOUSE_MANAGER` - Create and read batches

---

## 🛡️ EXCEPTION HANDLING

### Custom Exceptions

```java
NotFoundException         // Batch not found (404)
DomainException           // Business rule violation (400)
IllegalStateException     // Status transition invalid (409)
```

### Error Responses

```json
{
  "success": false,
  "error": {
    "code": "BAD_REQUEST",
    "message": "Insufficient available quantity: 350.000 kg < 400.000 kg"
  },
  "timestamp": "2025-10-28T15:30:00.000Z"
}
```

---

## 🚀 FUTURE ENHANCEMENTS

1. **Split Batch** - Divide one batch into multiple lots
2. **Merge Batches** - Combine batches of same fiber
3. **Quality Tracking** - Link to quality test results
4. **Cost Tracking** - FIFO/LIFO/Moving Average cost calculation
5. **Supplier Integration** - Auto-create from PO receipts
6. **Expiry Alerts** - Notify when batch nearing expiry
7. **Audit Trail** - Track all quantity changes

---

## 📁 FILE STRUCTURE

```
execution/fiber/
├── domain/
│   ├── FiberBatch.java           # Main entity with business logic
│   └── FiberBatchStatus.java     # NEW, RESERVED, IN_USE, DEPLETED
├── dto/
│   ├── CreateFiberBatchRequest.java
│   ├── FiberBatchDto.java        # Includes availableQuantity
│   └── ReserveRequest.java
├── app/
│   └── FiberBatchService.java   # Business logic with optimistic locking
├── infra/
│   └── repository/
│       └── FiberBatchRepository.java  # @Lock(LockModeType.OPTIMISTIC)
└── api/
    └── controller/
        └── FiberBatchController.java
```

---

## ✅ PRODUCTION-READY FEATURES

- ✅ Reserved quantity tracking (prevents over-booking)
- ✅ Optimistic locking (prevents race conditions)
- ✅ Tenant-scoped unique constraints
- ✅ Status-based lifecycle management
- ✅ Automatic depletion when consumed = quantity
- ✅ Exception handling with clear error messages
- ✅ UID generation for external traceability
- ✅ Audit fields (created_at, updated_at, version)

---

**Next:** Implement Yarn Batch Execution module (similar pattern)

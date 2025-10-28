# 🏭 EXECUTION - Fiber Batch Module

**Location:** `production/execution/fiber`  
**Purpose:** Track physical fiber batches/lots for production inventory

---

## 📋 MODULE OVERVIEW

**Fiber Batch** represents the **physical reality** of fiber inventory, not just the definition.

### **Key Difference:**

| Aspect          | Masterdata Fiber             | Execution Batch                  |
| --------------- | ---------------------------- | -------------------------------- |
| **Purpose**     | What is fiber? (definition)  | How much fiber? (inventory)      |
| **Example**     | "Cotton 60% + Viscose 40%"   | "500 kg Cotton - BATCH-2025-001" |
| **Granularity** | Type-level                   | Lot-level                        |
| **Tracking**    | Composition, attributes      | Quantity, location, status       |
| **Lifecycle**   | Created once, rarely changed | Frequently updated (deductions)  |

---

## 🏗️ ARCHITECTURE

### **Domain Model:**

```java
FiberBatch {
    fiberId: UUID              // Links to Fiber (masterdata)
    batchCode: "BATCH-2025-001" // Unique batch identifier
    supplierBatchCode: String   // Supplier's batch code
    quantity: BigDecimal        // Available quantity (15,3 precision)
    unit: String                // kg, lbs, etc.
    status: FiberBatchStatus   // NEW → RESERVED → IN_USE → DEPLETED
    warehouseLocation: String   // WH-A-01
    productionDate: Instant
    expiryDate: Instant
}
```

### **Status Lifecycle:**

```
┌─────┐    markInUse()     ┌──────┐
│ NEW │──────────────────►  │IN_USE│
└─────┘                      └──────┘
                             │     │
                             │     │ deduct() → quantity = 0
                             │     │
                             ▼     ▼
                          ┌──────────┐
                          │ DEPLETED │
                          └──────────┘
```

---

## 🔧 KEY OPERATIONS

### **1. Create Batch**

```java
// When receiving fiber from supplier
POST /api/production/batches/fiber
{
  "fiberId": "uuid-of-cotton-fiber",
  "batchCode": "BATCH-2025-001",
  "supplierBatchCode": "SUP-12345",
  "quantity": 500.000,
  "unit": "kg",
  "warehouseLocation": "WH-A-01"
}
```

### **2. List Batches**

```java
// Get all active batches for current tenant
GET /api/production/batches/fiber
```

### **3. Use Fiber in Production**

```java
// Deduct quantity from batch
batch.deduct(new BigDecimal("100")); // Use 100 kg
// Status automatically changes to DEPLETED if quantity = 0
```

### **4. Mark as In Use**

```java
// Reserve batch for production order
batch.markInUse(); // Status: NEW → IN_USE
```

---

## 📊 DATABASE SCHEMA

```sql
CREATE TABLE production.production_execution_fiber_batch (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    fiber_id UUID NOT NULL,           -- FK to prod_fiber
    batch_code VARCHAR(100) UNIQUE,    -- "BATCH-2025-001"
    supplier_batch_code VARCHAR(100),
    quantity DECIMAL(15,3) NOT NULL,    -- Precision: 15,3
    unit VARCHAR(20) NOT NULL,         -- kg, lbs
    production_date TIMESTAMP,
    expiry_date TIMESTAMP,
    status VARCHAR(20) NOT NULL,       -- NEW, RESERVED, IN_USE, DEPLETED
    warehouse_location VARCHAR(100),
    remarks TEXT,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT NOT NULL
);
```

---

## 🎯 USAGE SCENARIO

### **Scenario: Production Order Needs Fiber**

1. **Order Created:** Need 200 kg of "Cotton 60% + Viscose 40%"
2. **Find Batch:** System finds batch with 500 kg available
3. **Reserve:** Mark batch as IN_USE
4. **Deduct:** Deduct 200 kg → batch now has 300 kg
5. **Status:** Batch still IN_USE (quantity > 0)

### **Scenario: Batch Depleted**

1. **Production:** Need 200 kg more
2. **Deduct:** batch has 200 kg remaining
3. **Deduct 200 kg:** quantity becomes 0
4. **Auto Status:** Status automatically changes to DEPLETED

---

## 🔐 SECURITY

### **Roles:**

- `ADMIN` - Full access
- `PRODUCTION_MANAGER` - Create and manage batches
- `WAREHOUSE_MANAGER` - Read-only access

### **Tenant Isolation:**

All queries filtered by `tenantId` from `TenantContext`

---

## 🚀 FUTURE ENHANCEMENTS

1. **Batch Splitting:** Split one batch into multiple lots
2. **Batch Merging:** Merge multiple batches (same fiber)
3. **Quality Tracking:** Link to quality test results
4. **Cost Tracking:** Track cost per batch
5. **Supplier Integration:** Auto-create batches from PO receipts
6. **Expiry Alerts:** Notify when batch nearing expiry

---

## 📝 FILE STRUCTURE

```
execution/fiber/
├── domain/
│   ├── FiberBatch.java           # Main entity
│   └── FiberBatchStatus.java    # Enum
├── dto/
│   ├── CreateFiberBatchRequest.java
│   └── FiberBatchDto.java
├── app/
│   └── FiberBatchService.java   # Business logic
├── infra/
│   └── repository/
│       └── FiberBatchRepository.java
└── api/
    └── controller/
        └── FiberBatchController.java
```

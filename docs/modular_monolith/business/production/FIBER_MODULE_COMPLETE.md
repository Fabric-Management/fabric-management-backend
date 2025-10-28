# 🧵 FIBER MODULE - COMPLETE DESIGN

**Last Updated:** 2025-01-XX  
**Status:** ✅ COMPLETE

---

## 📋 MODULE OVERVIEW

The Fiber module consists of **TWO critical sub-modules:**

1. **`masterdata/fiber`** - What is fiber? (definitions, categories, attributes)
2. **`execution/fiber`** - How much fiber? (batches, inventory, tracking)

### **Key Concept:**

```
Fiber Masterdata = "Cotton 100%" definition (reusable, system-wide)
Fiber Batch = "500 kg of Cotton received on 2025-01-15 from Supplier X"
```

---

## 🏗️ MASTERDATA MODULE

### **Purpose:**

Define fiber types, compositions, and properties.

### **Key Entities:**

#### **Fiber** (production/masterdata/fiber)

- Represents a fiber type (e.g., "Cotton 100%", "Cotton 60% + Viscose 40%")
- Links to `Material` base entity
- Has reference table links: `FiberCategory`, `FiberIsoCode`

#### **FiberComposition**

- Many-to-many relationship for blended fibers
- Tracks percentage composition (must sum to 100%)

#### **Reference Tables:**

- `FiberCategory`: NATURAL, SYNTHETIC, REGENERATED, MIXED_BLEND, etc.
- `FiberIsoCode`: ISO 2076 codes (CO, PES, CLY, etc.)
- `FiberAttribute`: Physical/chemical properties
- `FiberCertification`: GOTS, OEKO-TEX, etc.

### **Key Features:**

- ✅ 100% pure fibers are system-defined (SYSTEM_TENANT_ID)
- ✅ Users create blended fibers using system fibers
- ✅ Circular reference prevention (no blending already-blended fibers)
- ✅ Automatic naming for blended fibers
- ✅ Duplicate composition detection

### **API Endpoints:**

```
GET  /api/production/fibers                # List all fibers
GET  /api/production/fibers/{id}            # Get fiber details
GET  /api/production/fibers/search?name=    # Search by name
POST /api/production/fibers/blended         # Create blended fiber
GET  /api/production/fibers/categories      # List categories
```

---

## 🏭 EXECUTION MODULE

### **Purpose:**

Track physical fiber batches/lots for production.

### **Key Entity:**

#### **FiberBatch** (production/execution/fiber)

- Represents a physical lot/batch of fiber
- Links to `Fiber` masterdata via `fiberId`
- Has unique `batchCode` for traceability
- Tracks quantity, warehouse location, status

### **Batch Lifecycle:**

```
NEW → RESERVED → IN_USE → DEPLETED
```

### **Key Features:**

- ✅ Quantity tracking with precision (15,3)
- ✅ Supplier batch code support
- ✅ Warehouse location tracking
- ✅ Status-based lifecycle management
- ✅ Automatic depletion when quantity = 0

### **API Endpoints:**

```
POST /api/production/batches/fiber                    # Create batch
GET  /api/production/batches/fiber                    # List all batches
GET  /api/production/batches/fiber/{id}               # Get batch details
GET  /api/production/batches/fiber/fiber/{fiberId}    # Get batches for fiber
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
│                 FIBER_BATCH (Execution)                      │
│  - batch_code: "BATCH-2025-001"                             │
│  - supplier_batch_code: "SUP-12345"                         │
│  - quantity: 500.000 kg                                     │
│  - warehouse_location: "WH-A-01"                            │
│  - status: NEW, IN_USE, DEPLETED                            │
│  - production_date, expiry_date                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│              FIBER_COMPOSITION (Masterdata)                 │
│  - Maps blended fibers to their base components            │
│  - Example: "Cotton 60%" = 60% of Cotton fiber_id          │
│  - Must sum to 100%                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 USAGE SCENARIOS

### **Scenario 1: Create a Blended Fiber (Masterdata)**

```
1. User wants to create "Cotton 60% + Viscose 40%" fiber
2. System checks if base fibers exist (Cotton 100%, Viscose 100%)
3. System creates blended fiber with composition map
4. System auto-generates name "Cotton 60% + Viscose 40%"
```

### **Scenario 2: Receive Fiber Batch (Execution)**

```
1. Supplier delivers 500 kg of "Cotton 100%" fiber
2. Warehouse creates batch with code "BATCH-2025-001"
3. Batch status: NEW
4. Links to Fiber (masterdata) via fiberId
```

### **Scenario 3: Use Fiber in Production**

```
1. Production order needs 100 kg of "Cotton 60% + Viscose 40%"
2. System finds batch "BATCH-2025-001" with 500 kg
3. System deducts 100 kg → batch quantity = 400 kg
4. Batch status: IN_USE
```

---

## 📊 DATABASE SCHEMA

### **Masterdata Schema:**

- `production.prod_material` (base for all materials)
- `production.prod_fiber` (fiber-specific details)
- `production.prod_fiber_composition` (blended fiber components)
- `production.prod_fiber_attribute_link` (fiber properties)
- `production.prod_fiber_certification_link` (certifications)

### **Reference Schema:**

- `production.prod_fiber_category` (NATURAL, SYNTHETIC, etc.)
- `production.prod_fiber_iso_code` (ISO 2076 codes)
- `production.prod_fiber_attribute` (physical/chemical properties)
- `production.prod_fiber_certification` (certifications)

### **Execution Schema:**

- `production.production_execution_fiber_batch` (physical batches)

---

## 🚀 NEXT STEPS

1. **Test Execution Module** - Run Postman tests for batch creation
2. **Add Yarn Module** - Similar structure for yarn masterdata + execution
3. **Add Fabric Module** - Similar structure for fabric masterdata + execution
4. **Add Planning Module** - Capacity, scheduling, work centers
5. **Add Quality Module** - Inspection, testing, results

---

## 🔧 DEVELOPMENT NOTES

- All fibers are created with proper tenant isolation
- System fibers (100% pure) are accessible by all tenants
- User-created fibers (blended) are tenant-specific
- Batch tracking enables inventory management
- Status-based lifecycle supports production workflows

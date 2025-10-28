# 🧶 YARN MODULE - IMPLEMENTATION PLAN

**Status:** 📋 Planning  
**Priority:** ⭐ HIGH

---

## 📋 OVERVIEW

Yarn is created by spinning fibers. The Yarn module will follow the same dual-structure as Fiber:

1. **Masterdata** - Define yarn types and properties
2. **Execution** - Track physical yarn batches

---

## 🏗️ YARN MASTERDATA

### **Key Entities:**

#### **Yarn**
- Links to `Material` (base entity)
- Has reference table links: `YarnCategory`, `YarnAttribute`
- Contains properties: count, twist, strength, elongation

#### **YarnCategory** (Reference Table)
- SEWING
- KNITTING
- WEAVING
- EMBROIDERY
- SPECIALTY

#### **YarnComposition**
- Maps yarn to its base fibers (e.g., Cotton 100% fiber → Cotton 100% yarn)
- Percentage composition tracking

### **Reference Tables:**
- `prod_yarn_category`
- `prod_yarn_attribute`
- `prod_yarn_certification`

---

## 🏭 YARN EXECUTION

### **YarnBatch**
- Links to `Yarn` masterdata via `yarnId`
- Tracks: batch_code, quantity, warehouse_location
- Lifecycle: NEW → IN_USE → DEPLETED

---

## 🎯 IMPLEMENTATION STRATEGY

### **Step 1: Reference Tables**
```
V12__yarn_reference_tables.sql
- Create prod_yarn_category
- Create prod_yarn_attribute
- Seed default data
```

### **Step 2: Yarn Masterdata**
```
V13__production_yarn_and_composition.sql
- Create prod_yarn table
- Link to prod_material
- Create prod_yarn_composition for blends
```

### **Step 3: Domain Models**
```
- Yarn.java
- YarnCategory.java
- YarnAttribute.java
- YarnComposition.java
```

### **Step 4: Yarn Execution**
```
V14__execution_yarn_batch.sql
- Create production_execution_yarn_batch table
```

### **Step 5: REST API**
```
GET  /api/production/yarns
POST /api/production/yarns/blended
GET  /api/production/batches/yarn
POST /api/production/batches/yarn
```

---

## 🔗 RELATIONSHIP

```
Fiber (masterdata) → YarnBatch (execution)
                 ↓
            Yarn (masterdata)
```

Yarn is created **from** Fiber batches, so we need to:
1. Select fiber batch(s)
2. Create yarn composition
3. Track resulting yarn batch

---

## 📊 EXAMPLE SCENARIO

1. **Have Fiber Batch:** "BATCH-2025-001" - 500 kg Cotton 60%+Viscose 40%
2. **Create Yarn:** "Cotton 60% + Viscose 40% - Count 30"
3. **Yarn Batch:** "BATCH-2025-002" - 450 kg (yield from fiber batch)
4. **Track:** Used 500 kg fiber → Produced 450 kg yarn

---

## 🚀 ESTIMATED EFFORT

- Reference tables: 1 hour
- Yarn masterdata: 2 hours
- Yarn execution: 1 hour
- REST API: 2 hours
- Testing: 1 hour

**Total: ~7 hours**


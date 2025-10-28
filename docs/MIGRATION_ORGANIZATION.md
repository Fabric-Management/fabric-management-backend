# 📁 MIGRATION FILES ORGANIZATION

**Last Updated:** 2025-01-XX

---

## 🎯 STRATEGY

All migration files are in a **flat structure** (`db/migration/`) to ensure Flyway works correctly.

### **Naming Convention:**

```
V{XXX}__{description}.sql
```

- **XXX**: Sequential version number (001-999)
- **description**: Brief description of what the migration does

### **Version Numbering:**

| Range | Module | Description |
|-------|--------|-------------|
| 001-020 | Common | Platform foundation (schemas, company, user, auth, policy, audit) |
| 021-050 | Production - Masterdata | Reference tables, fiber, yarn, fabric definitions |
| 051-080 | Production - Execution | Batch tracking for fiber, yarn, loom, knit |
| 081-099 | Production - Planning | Capacity, scheduling, work centers |
| 100+ | Logistics | Inventory, shipment, customs |

---

## 📋 CURRENT MIGRATIONS

### **Common (001-007):**
- `V001__common_schemas_init.sql` - Create all schemas
- `V002__common_user_tables.sql` - User management
- `V003__onboarding_enhancements.sql` - Tenant onboarding
- `V004__common_company_tables.sql` - Company management
- `V005__common_auth_tables.sql` - Authentication
- `V006__common_policy_tables.sql` - Policy management
- `V007__common_audit_tables.sql` - Audit logging

### **Production - Masterdata (008-012):**
- `V008__fiber_reference_tables.sql` - Fiber categories, ISO codes, attributes, certifications
- `V009__production_fiber_and_composition.sql` - Fiber entities and compositions
- `V010__seed_system_100_pure_fibers.sql` - Pre-seed 100% pure fiber entities
- `V011__execution_fiber_batch.sql` - Fiber batch tracking
- `V012__yarn_reference_tables.sql` - Yarn categories, attributes, certifications

---

## 🎨 DOCUMENTATION STRUCTURE

While migration files are flat, we document them logically:

```
docs/
├── modular_monolith/
│   └── business/
│       └── production/
│           ├── FIBER_MODULE_COMPLETE.md    # Fiber module docs
│           ├── YARN_MODULE_PLAN.md         # Yarn module plan
│           └── ...                         # Other modules
```

---

## 🔧 FLYWAY CONFIGURATION

**`application.yml`:**
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    validate-on-migrate: true
```

---

## ✅ BEST PRACTICES

1. **Sequential Versioning:** Always use the next available version number
2. **Descriptive Names:** Use clear, descriptive names for migration files
3. **Idempotent Operations:** Use `IF NOT EXISTS` where possible
4. **Comments:** Add header comments explaining the purpose
5. **Rollback Support:** Consider providing rollback scripts (optional)

---

## 📊 FUTURE MIGRATIONS

### **Planned:**
- `V013__yarn_masterdata.sql` - Yarn entities and compositions
- `V014__execution_yarn_batch.sql` - Yarn batch tracking
- `V015__fabric_reference_tables.sql` - Fabric categories and attributes
- `V016__fabric_masterdata.sql` - Fabric entities
- `V017__execution_fabric_batch.sql` - Fabric batch tracking
- `V018__production_planning_tables.sql` - Planning and scheduling
- `V019__logistics_inventory_tables.sql` - Inventory management


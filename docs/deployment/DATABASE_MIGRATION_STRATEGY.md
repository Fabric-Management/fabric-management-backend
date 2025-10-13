# 🗄️ DATABASE MIGRATION STRATEGY

## 📅 Last Updated: 11 October 2025

## 🎯 Principles: DRY, Microservice Autonomy, Idempotency, Check Existing First

---

## 🏗️ CURRENT STRUCTURE

### Database Initialization Files

```
fabric-management-backend/
├── init.sql/                           # PostgreSQL initialization scripts
│   └── 01-init-db.sql                 # Database setup, extensions, functions
└── services/
    ├── user-service/
    │   └── src/main/resources/db/migration/
    │       └── V1__create_user_tables.sql
    ├── contact-service/
    │   └── src/main/resources/db/migration/
    │       └── V1__create_contact_tables.sql
    └── company-service/
        └── src/main/resources/db/migration/
            └── V1__create_company_tables.sql
```

---

## 🎯 STRATEGY: TWO-TIER APPROACH

### Tier 1: Global Database Setup (`init.sql/`)

**Purpose**: PostgreSQL-level initialization

- Extensions (uuid-ossp, pgcrypto)
- Performance tuning
- Global functions
- User/role management

**When**: Runs ONCE when PostgreSQL container first starts

**Location**: `init.sql/01-init-db.sql`

**Mount Point**: `/docker-entrypoint-initdb.d/` (PostgreSQL convention)

### Tier 2: Service-Specific Migrations (`db/migration/`)

**Purpose**: Service-owned schema

- Tables
- Indexes
- Triggers
- Service-specific functions (idempotent)

**When**: Runs on each service startup (Flyway)

**Principle**: **Microservice Autonomy** - Each service is self-contained

---

## 🔑 KEY DECISION: Function Definitions

### Problem

Migrations need `update_updated_at_column()` function but:

- `init.sql` runs only once (timing issue)
- Migrations may run before init.sql
- Service should be autonomous

### Solution: Idempotent Function Definition

Each migration defines its required functions using `CREATE OR REPLACE`:

```sql
-- =============================================================================
-- COMMON FUNCTIONS (Idempotent - Self-contained)
-- =============================================================================
-- Each migration defines its own dependencies (Microservice Principle)
-- CREATE OR REPLACE ensures idempotency and no conflicts

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

**Why This Approach:**

✅ **Microservice Principle**: Each service is autonomous
✅ **DRY Spirit**: Function logic defined once, reused via CREATE OR REPLACE
✅ **Idempotent**: Safe to run multiple times
✅ **Order Independent**: Doesn't matter which migration runs first
✅ **Testable**: Migrations can be tested in isolation
✅ **No External Dependencies**: Doesn't rely on init.sql timing

---

## 📊 BENEFITS vs ALTERNATIVES

| Approach                        | DRY     | Autonomous | Idempotent | Simple | Score   |
| ------------------------------- | ------- | ---------- | ---------- | ------ | ------- |
| **CREATE OR REPLACE** (Current) | ✅ 90%  | ✅ 100%    | ✅ 100%    | ✅ 95% | **96%** |
| Single V0\_\_init migration     | ✅ 100% | ❌ 50%     | ✅ 80%     | ⚠️ 70% | 75%     |
| Java Flyway Callbacks           | ✅ 100% | ⚠️ 70%     | ✅ 90%     | ❌ 40% | 75%     |
| Only init.sql (no redundancy)   | ✅ 100% | ❌ 30%     | ⚠️ 60%     | ✅ 90% | 70%     |

---

## 🔄 MIGRATION EXECUTION FLOW

### Docker Deployment

```
1. PostgreSQL container starts
   ├── Runs init.sql/01-init-db.sql
   │   ├── CREATE EXTENSIONS
   │   ├── CREATE USERS
   │   └── CREATE FUNCTION update_updated_at_column()
   └── Container ready ✅

2. User Service starts
   ├── Flyway detects V1__create_user_tables.sql
   ├── Runs: CREATE OR REPLACE FUNCTION update_updated_at_column()
   │   └── Function already exists → Replaced (idempotent) ✅
   ├── CREATE TABLE users
   ├── CREATE TRIGGER (uses function)
   └── Migration complete ✅

3. Contact Service starts
   ├── CREATE OR REPLACE FUNCTION update_updated_at_column()
   │   └── Function exists → Replaced (idempotent) ✅
   ├── CREATE TABLE contacts
   └── Migration complete ✅

4. Company Service starts
   ├── CREATE OR REPLACE FUNCTION update_updated_at_column()
   │   └── Function exists → Replaced (idempotent) ✅
   ├── CREATE TABLE companies
   └── Migration complete ✅
```

**Result**: No timing issues, all services autonomous! 🎉

---

## 📝 BEST PRACTICES

### 1. Check Existing Migrations First

**Rule:** Before creating new migration, check if you can add to existing one

**Example (2025-10-11):**

```
Need to add: is_platform field to companies

❌ Create V11__add_platform_flag.sql
✅ Add to V2__add_policy_fields_to_companies.sql (already adding policy fields)
```

**Benefits:**

- Fewer migration files
- Logical grouping
- Easier to understand

**When to Create New:**

- Different feature/scope
- Breaking changes
- Already deployed to production

### 2. NEVER Use Conditional Logic

**Rule:** Migrations must be deterministic

```sql
❌ BAD: Conditional migration
DO $$
BEGIN
    IF EXISTS (table check) THEN
        INSERT...
    END IF;
END $$;

✅ GOOD: Put migration in correct service
-- If table doesn't exist in service, service doesn't need migration
INSERT INTO policy_registry (...);
```

### 3. Idempotency

```sql
-- ✅ ALWAYS use IF NOT EXISTS for tables
CREATE TABLE IF NOT EXISTS users (...)

-- ✅ ALWAYS use CREATE OR REPLACE for functions
CREATE OR REPLACE FUNCTION my_function() ...

-- ✅ ALWAYS use DROP IF EXISTS for triggers
DROP TRIGGER IF EXISTS my_trigger ON my_table;
```

### 4. Self-Contained Migrations

Each migration should:

- Define all required functions
- Create all required types
- Be runnable in isolation

### 5. Clean Separation

```
init.sql/           → PostgreSQL-level (extensions, users, tuning)
db/migration/       → Service-level (tables, triggers, data)
```

### 6. Naming Convention

```
init.sql/
├── 01-init-db.sql       # Database setup
├── 02-extensions.sql    # Optional: Additional extensions
└── 03-global-config.sql # Optional: Global configuration

services/*/db/migration/
├── V1__create_tables.sql
├── V2__add_columns.sql
└── V3__add_indexes.sql
```

---

## ⚠️ ANTI-PATTERNS TO AVOID

### ❌ DON'T: Rely on init.sql for migration dependencies

```sql
-- ❌ BAD: Assumes function exists
CREATE TRIGGER ... EXECUTE FUNCTION update_updated_at_column();
-- No guarantee function exists!
```

### ❌ DON'T: Duplicate function logic

```sql
-- ❌ BAD: Different implementations
-- V1 migration
CREATE FUNCTION update_updated_at_column() ... -- Version A

-- V2 migration
CREATE FUNCTION update_updated_at_column() ... -- Version B (different!)
```

### ✅ DO: Use CREATE OR REPLACE with exact same logic

```sql
-- ✅ GOOD: Exact same function definition everywhere
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

---

## 🚀 DEPLOYMENT NOTES

### Fresh Deployment

1. PostgreSQL starts → runs `init.sql/01-init-db.sql`
2. Services start → run their Flyway migrations
3. Functions already exist → `CREATE OR REPLACE` is no-op (safe)

### Existing Deployment

1. PostgreSQL already has data
2. `init.sql/` scripts DON'T run again
3. Flyway migrations run normally
4. `CREATE OR REPLACE` ensures function exists

### Testing Migrations Locally

```bash
# Test single migration
psql -U fabric_user -d fabric_management -f services/user-service/src/main/resources/db/migration/V1__create_user_tables.sql

# Should work independently! ✅
```

---

## 📚 REFERENCES

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [PostgreSQL Init Scripts](https://hub.docker.com/_/postgres)
- [Microservice Database Patterns](https://microservices.io/patterns/data/database-per-service.html)
- [Project Principles](../development/PRINCIPLES.md)

---

**Last Updated:** 2025-10-11 02:00 UTC+1  
**Version:** 1.1  
**Status:** ✅ Active & Working  
**Maintained By:** Development Team

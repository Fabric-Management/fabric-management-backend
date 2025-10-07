# Flyway Migration Checksum Mismatch Error - RESOLVED ✅

## Problem Description

Multiple microservices fail to start with Flyway validation errors:

```
org.flywaydb.core.api.exception.FlywayValidateException: Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1
-> Applied to database : -168710817
-> Resolved locally    : 632080022
Either revert the changes to the migration, or run repair to update the schema history.
```

**Affected Services:**
- `user-service` ❌
- `contact-service` ✅ (starts successfully)
- `company-service` ❌ (depends on user-service)

**Symptoms:**
- Contact-service starts successfully and writes V1 migration to database
- User-service fails with checksum mismatch
- Company-service never starts due to dependency on user-service
- Error persists after:
  - `docker-compose down -v`
  - Database volume cleanup
  - `docker-compose build --no-cache`
  - Line ending normalization (`dos2unix`)
  - Rewriting migration files

---

## Root Cause Analysis

### ❌ Initial Hypothesis (INCORRECT)

We initially suspected:
1. Docker build cache issues
2. Line ending differences (CRLF vs LF)
3. File encoding problems
4. Migration file corruption

**These were NOT the root cause!**

---

### ✅ Actual Root Cause (CORRECT)

**All microservices were sharing the same Flyway schema history table!**

```sql
-- Problem: All services used the same table
flyway_schema_history
```

**What Actually Happened:**

1. **Contact-service starts first** → Creates `flyway_schema_history` → Writes V1 migration with checksum `-168710817`
2. **User-service starts** → Reads same `flyway_schema_history` table → Finds V1 entry
3. **Flyway validates** → Compares contact-service's V1 checksum with user-service's V1 migration file
4. **Checksum mismatch** → Different migration files, different checksums! ❌

**Why This Happens:**

Each microservice has its own `V1__create_*.sql` file:
- `V1__create_contact_tables.sql` → Checksum: `-168710817`
- `V1__create_user_tables.sql` → Checksum: `632080022`
- `V1__create_company_tables.sql` → Checksum: (different)

But they all write to **the same metadata table** → Collision!

---

## Architecture Anti-Pattern

This violates **microservices best practices**:

| ❌ Wrong (Shared History) | ✅ Correct (Isolated History) |
|---------------------------|-------------------------------|
| All services → `flyway_schema_history` | Each service → own history table |
| Tight coupling between services | Loose coupling, independence |
| Version conflicts possible | No conflicts, isolated versions |
| One service affects others | Each service manages itself |

---

## Solution Timeline

### Attempt 1: Disable Validation ❌
```yaml
spring:
  flyway:
    validate-on-migrate: false
```
**Result:** Services started but validation was disabled (not a real fix)

---

### Attempt 2: Docker Cache Cleanup ❌
```bash
docker-compose down -v
docker builder prune -af
docker-compose build --no-cache
```
**Result:** No change, error persisted

---

### Attempt 3: Line Ending Normalization ❌
```bash
dos2unix services/*/src/main/resources/db/migration/*.sql
```
**Result:** No change, files were already LF format

---

### Attempt 4: Database Cleanup + Rebuild ❌
```bash
docker-compose down
docker volume rm fabric-management-backend_postgres_data
docker-compose build --no-cache
docker-compose up -d
```
**Result:** Contact-service started first and wrote checksum, user-service failed again

---

### Attempt 5: Rewrite Migration Files ❌
Manually rewrote migration files using Read → Write to normalize encoding.

**Result:** No change, root cause was not in the files themselves

---

### ✅ FINAL SOLUTION: Separate Flyway History Tables

**The Correct Fix:**

Configure each microservice to use its own Flyway schema history table:

#### User Service
```yaml
# services/user-service/src/main/resources/application-docker.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    table: user_flyway_schema_history # ✅ Separate table
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    clean-disabled: true
    repair-on-migrate: false
```

#### Contact Service
```yaml
# services/contact-service/src/main/resources/application-docker.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    table: contact_flyway_schema_history # ✅ Separate table
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    clean-disabled: true
    repair-on-migrate: false
```

#### Company Service
```yaml
# services/company-service/src/main/resources/application-docker.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    table: company_flyway_schema_history # ✅ Separate table
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    clean-disabled: true
    repair-on-migrate: false
```

---

## Implementation Steps

### Step 1: Update Configuration Files

Add `table: <service>_flyway_schema_history` to each service's `application-docker.yml`.

### Step 2: Clean Rebuild

```bash
# Stop all containers
docker-compose down

# Remove old database volumes
docker volume rm fabric-management-backend_postgres_data
docker volume rm fabric-management-backend_redis_data

# Rebuild without cache
docker-compose build --no-cache

# Start services
docker-compose up -d
```

### Step 3: Verify Success

```bash
# Check all services are healthy
docker-compose ps

# Expected output:
# fabric-user-service      Up X minutes (healthy)
# fabric-contact-service   Up X minutes (healthy)
# fabric-company-service   Up X minutes (healthy)
```

### Step 4: Verify Database Schema

```bash
docker exec -it fabric-postgres psql -U fabric_user -d fabric_management -c "\dt *flyway*"
```

**Expected Output:**
```
                      List of relations
 Schema |             Name              | Type  |    Owner
--------+-------------------------------+-------+-------------
 public | company_flyway_schema_history | table | fabric_user
 public | contact_flyway_schema_history | table | fabric_user
 public | user_flyway_schema_history    | table | fabric_user
(3 rows)
```

✅ Three separate tables → Each service is isolated!

---

## Why This Solution Works

### Before (Shared Table)
```
┌─────────────────┐
│ contact-service │──┐
└─────────────────┘  │
                     ├──► flyway_schema_history (SHARED)
┌─────────────────┐  │     ├─ V1 (contact checksum)
│   user-service  │──┤     └─ ❌ Conflict!
└─────────────────┘  │
                     │
┌─────────────────┐  │
│ company-service │──┘
└─────────────────┘
```

### After (Isolated Tables)
```
┌─────────────────┐
│ contact-service │──► contact_flyway_schema_history
└─────────────────┘     └─ V1 (contact checksum) ✅

┌─────────────────┐
│   user-service  │──► user_flyway_schema_history
└─────────────────┘     └─ V1 (user checksum) ✅

┌─────────────────┐
│ company-service │──► company_flyway_schema_history
└─────────────────┘     └─ V1 (company checksum) ✅
```

**Benefits:**
- ✅ No checksum conflicts
- ✅ Each service validates its own migrations
- ✅ True microservices independence
- ✅ Services can be deployed independently
- ✅ No shared metadata coupling

---

## Additional Improvements

### .gitattributes for Line Ending Consistency

Although not the root cause, we added `.gitattributes` to prevent future issues:

```gitattributes
# Ensure consistent line endings across platforms
*.sql text eol=lf
*.java text eol=lf
*.yml text eol=lf
*.yaml text eol=lf
*.xml text eol=lf
```

This ensures:
- Cross-platform consistency (Windows/Mac/Linux)
- Predictable Flyway checksums
- No CRLF/LF conflicts

---

## Prevention & Best Practices

### 1. Always Use Service-Specific Flyway Tables

```yaml
spring:
  flyway:
    table: ${spring.application.name}_flyway_schema_history
```

### 2. Never Modify Existing Migrations

Once a migration runs in production:
- ❌ Don't edit it
- ✅ Create a new versioned migration (V2, V3, etc.)

### 3. Test Migrations in Clean Environment

```bash
# Always test with fresh database
docker-compose down -v
docker-compose up -d
```

### 4. Validate Configuration

Check each service has its own history table:
```bash
grep -r "table:" services/*/src/main/resources/application-docker.yml
```

### 5. Document Database Schema Ownership

| Service | Schema History Table | Owns Tables |
|---------|---------------------|-------------|
| user-service | `user_flyway_schema_history` | `users`, `user_sessions`, `password_reset_tokens`, `user_events`, `user_outbox_events` |
| contact-service | `contact_flyway_schema_history` | `contacts`, `contact_outbox_events` |
| company-service | `company_flyway_schema_history` | `companies`, `company_contacts`, `company_events`, `company_outbox_events` |

---

## Troubleshooting Checklist

If you encounter Flyway checksum errors:

- [ ] Check each service has unique `spring.flyway.table` configured
- [ ] Verify `validate-on-migrate: true` is enabled
- [ ] Confirm database has separate history tables
- [ ] Check migration files haven't been modified
- [ ] Verify Docker build used `--no-cache`
- [ ] Ensure `.gitattributes` enforces LF line endings
- [ ] Test with clean database volumes

---

## Status

✅ **RESOLVED** - 2025-10-06

**Final Solution:** Separate Flyway schema history tables per microservice

**Test Results:**
- All services start successfully
- No checksum conflicts
- Production-ready configuration
- Follows microservices best practices

**Deployment Verified:**
```bash
docker-compose ps
# All services: Up X minutes (healthy) ✅
```

---

## References

- [Flyway Configuration Options](https://documentation.red-gate.com/flyway/flyway-cli-and-api/configuration/parameters)
- [Microservices Database Patterns](https://microservices.io/patterns/data/database-per-service.html)
- Spring Boot Flyway Auto-configuration
- Git Line Ending Handling

---

## Lessons Learned

1. **Shared metadata tables violate microservices principles**
   - Each service must own its metadata
   - Avoid cross-service dependencies at data layer

2. **Flyway defaults are for monoliths**
   - Default `flyway_schema_history` assumes single application
   - Multi-service deployments need explicit configuration

3. **Test assumptions early**
   - Don't chase symptoms (line endings, cache)
   - Verify architecture fundamentals first

4. **Document root causes**
   - Failed attempts teach what NOT to do
   - Help future developers avoid same pitfalls

---

**Document Maintainer:** Development Team
**Last Updated:** 2025-10-06
**Resolution Time:** ~2 hours of investigation and testing

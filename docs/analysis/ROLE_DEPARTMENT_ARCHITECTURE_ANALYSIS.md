# Role & Department Architecture Analysis

**Date:** 2025-01-27  
**Purpose:** Evaluate current backend architecture against enterprise-grade dynamic role/department management requirements  
**Status:** ❌ **NOT COMPLIANT** - Major structural gaps identified

---

## Executive Summary

The current architecture **does NOT support** fully dynamic, scalable, enterprise-grade management of roles, departments, and department categories. Critical gaps exist in entity relationships, extensibility, and authorization integration.

**Compliance Score: 2/6 categories compliant**

---

## 1. Entity Structure Review

| Aspect                        | Status            | Observations                                                                                                                                                                                                                              | Recommendations                                                                                |
| ----------------------------- | ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| **Department Entity**         | ✅ **COMPLIANT**  | `Department` exists as database entity (`common_company.common_department`) with proper JPA mapping. Fields: `id`, `tenant_id`, `uid`, `company_id`, `department_name`, `description`, `manager_id`                                       | ✅ No changes needed                                                                           |
| **DepartmentCategory Entity** | ❌ **MISSING**    | No `DepartmentCategory` entity exists. No categorization mechanism for departments.                                                                                                                                                       | **CRITICAL:** Create `DepartmentCategory` entity with One-to-Many relationship to `Department` |
| **Role Entity**               | ❌ **MISSING**    | No `Role` entity exists. Documentation mentions `UserRole` but implementation is missing. Only enum `SystemRole` found in archive (not in use).                                                                                           | **CRITICAL:** Create `Role` entity as database-driven (not enum)                               |
| **Enum/Constant Usage**       | ⚠️ **PARTIAL**    | - `DepartmentType` enum exists in archive (not in use)<br>- `SystemRole` enum exists in archive (not in use)<br>- `User.department` is **String field** (not relational)<br>- Policy conditions use string arrays (not entity references) | Remove hardcoded enums. Convert to entity-based architecture                                   |
| **JPA Relationships**         | ❌ **INCOMPLETE** | - No `@ManyToMany` between `User` and `Department`<br>- No `@ManyToOne` from `Department` to `DepartmentCategory`<br>- No `@ManyToOne` or `@ManyToMany` from `User` to `Role`<br>- `User.department` is primitive String field            | **CRITICAL:** Implement proper JPA relationships                                               |

### Current Structure Issues

**User Entity:**

```73:74:src/main/java/com/fabricmanagement/common/platform/user/domain/User.java
    @Column(name = "department", length = 100)
    private String department;
```

- ❌ Department stored as **String** (hardcoded reference)
- ❌ No foreign key to `Department` entity
- ❌ Cannot enforce referential integrity
- ❌ Cannot query by department relationships

**Department Entity:**

```42:79:src/main/java/com/fabricmanagement/common/platform/company/domain/Department.java
public class Department extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "department_name", nullable = false, length = 100)
    private String departmentName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "manager_id")
    private UUID managerId;
```

- ✅ Entity exists
- ❌ No `departmentCategoryId` field
- ❌ No relationship to `DepartmentCategory`

---

## 2. Extensibility Evaluation

| Aspect                       | Status               | Observations                                                                                                                            | Recommendations                                                                                           |
| ---------------------------- | -------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| **Database-Driven**          | ⚠️ **PARTIAL**       | - `Department`: ✅ Database-driven<br>- `Role`: ❌ Not implemented<br>- `DepartmentCategory`: ❌ Not implemented                        | Implement missing entities                                                                                |
| **CRUD Operations**          | ❌ **INCOMPLETE**    | - `DepartmentRepository` exists but no controller endpoints found<br>- No Role CRUD endpoints<br>- No DepartmentCategory CRUD endpoints | Create REST controllers for all entities                                                                  |
| **Dynamic Loading**          | ❌ **NOT SUPPORTED** | - No API endpoints to fetch roles/departments for dropdowns<br>- Frontend cannot load available options dynamically                     | Implement `GET /api/common/departments`, `GET /api/common/roles`, `GET /api/common/department-categories` |
| **No Code Changes Required** | ❌ **FALSE**         | Adding new departments requires code changes (string values hardcoded in User entity). Adding roles is impossible (no entity).          | Make all additions database-only                                                                          |

### Migration Script Analysis

From `V002__common_company_tables.sql`:

```50:76:src/main/resources/db/migration/V002__common_company_tables.sql
CREATE TABLE common_company.common_department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,

    company_id UUID NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    manager_id UUID,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_department_company FOREIGN KEY (company_id)
        REFERENCES common_company.common_company(id) ON DELETE CASCADE
);
```

- ✅ Department table exists
- ❌ No `department_category_id` column
- ❌ No junction table for User-Department Many-to-Many

From `V003__common_user_tables.sql`:

```12:25:src/main/resources/db/migration/V003__common_user_tables.sql
CREATE TABLE common_user.common_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,

    contact_value VARCHAR(255) UNIQUE NOT NULL,
    contact_type VARCHAR(20) NOT NULL,

    company_id UUID NOT NULL,
    department VARCHAR(100),
```

- ❌ `department` is **VARCHAR**, not foreign key
- ❌ No `role_id` column
- ❌ No junction table for roles

---

## 3. Data Relationship Verification

| Relationship                        | Expected                      | Current                           | Status            | Action Required                                 |
| ----------------------------------- | ----------------------------- | --------------------------------- | ----------------- | ----------------------------------------------- |
| **User ↔ Department**               | Many-to-Many                  | String field                      | ❌ **WRONG**      | Create junction table `common_user_department`  |
| **Department → DepartmentCategory** | One-to-Many                   | None                              | ❌ **MISSING**    | Add `department_category_id` FK to `Department` |
| **User → Role**                     | One-to-Many (or Many-to-Many) | None                              | ❌ **MISSING**    | Create `Role` entity + relationship             |
| **Foreign Key Constraints**         | Required                      | Partial (only Department→Company) | ⚠️ **INCOMPLETE** | Add all FK constraints                          |

### Required Database Changes

1. **Create DepartmentCategory Table:**

```sql
CREATE TABLE common_company.common_department_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    display_order INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    -- BaseEntity fields...
);
```

2. **Alter Department Table:**

```sql
ALTER TABLE common_company.common_department
ADD COLUMN department_category_id UUID;

ALTER TABLE common_company.common_department
ADD CONSTRAINT fk_department_category
FOREIGN KEY (department_category_id)
REFERENCES common_company.common_department_category(id);
```

3. **Create Role Table:**

```sql
CREATE TABLE common_user.common_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    -- BaseEntity fields...
);
```

4. **Create User-Department Junction Table:**

```sql
CREATE TABLE common_user.common_user_department (
    user_id UUID NOT NULL,
    department_id UUID NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, department_id),
    CONSTRAINT fk_user_dept_user FOREIGN KEY (user_id)
        REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_dept_dept FOREIGN KEY (department_id)
        REFERENCES common_company.common_department(id) ON DELETE CASCADE
);
```

5. **Create User-Role Relationship:**

```sql
-- Option A: One-to-Many (one role per user)
ALTER TABLE common_user.common_user
ADD COLUMN role_id UUID;

ALTER TABLE common_user.common_user
ADD CONSTRAINT fk_user_role
FOREIGN KEY (role_id)
REFERENCES common_user.common_role(id);

-- Option B: Many-to-Many (multiple roles per user)
CREATE TABLE common_user.common_user_role (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)
        REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id)
        REFERENCES common_user.common_role(id) ON DELETE CASCADE
);
```

---

## 4. Authorization & Policy Integration

| Aspect                           | Status              | Observations                                                                                           | Recommendations                                                      |
| -------------------------------- | ------------------- | ------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------- |
| **Policy Role References**       | ⚠️ **STRING-BASED** | Policies use string arrays in JSONB conditions: `{"roles": ["PLANNER", "ADMIN"]}`                      | **CRITICAL:** Change to entity references (role IDs)                 |
| **Policy Department References** | ⚠️ **STRING-BASED** | Policies use string arrays: `{"departments": ["production"]}`                                          | **CRITICAL:** Change to entity references (department IDs)           |
| **Dynamic Policy Evaluation**    | ⚠️ **PARTIAL**      | Policy evaluation reads from context map, but context built from authentication (not database queries) | Query database for user's actual roles/departments during evaluation |

### Current Policy Implementation

From `PolicyService.java`:

```117:141:src/main/java/com/fabricmanagement/common/platform/policy/app/PolicyService.java
        // Check roles
        if (conditions.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> requiredRoles = (List<String>) conditions.get("roles");
            @SuppressWarnings("unchecked")
            List<String> userRoles = (List<String>) context.getOrDefault("roles", List.of());

            boolean hasRole = requiredRoles.stream().anyMatch(userRoles::contains);
            if (!hasRole) {
                log.debug("Role check failed: required={}, user={}", requiredRoles, userRoles);
                return false;
            }
        }

        // Check departments
        if (conditions.containsKey("departments")) {
            @SuppressWarnings("unchecked")
            List<String> requiredDepartments = (List<String>) conditions.get("departments");
            String userDepartment = (String) context.get("department");

            if (userDepartment == null || !requiredDepartments.contains(userDepartment)) {
                log.debug("Department check failed: required={}, user={}", requiredDepartments, userDepartment);
                return false;
            }
        }
```

**Issues:**

- ❌ Roles compared as **strings** (case-sensitive, typo-prone)
- ❌ Departments compared as **strings** (no referential integrity)
- ❌ No validation that role/department exists in database
- ❌ Context built from JWT claims (may be stale)

**Required Changes:**

1. Policy conditions should store **UUIDs** or **codes** (not free-form strings)
2. Evaluation should query database: `SELECT role_id FROM user_role WHERE user_id = ?`
3. Add validation service to verify role/department exists before policy creation

---

## 5. Data Initialization & Migration

| Aspect                | Status         | Observations                                                                             | Recommendations                                              |
| --------------------- | -------------- | ---------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| **Migration Scripts** | ✅ **EXISTS**  | Flyway migrations found in `src/main/resources/db/migration/`                            | ✅ Continue using Flyway                                     |
| **Seed Data**         | ❌ **MISSING** | No seed scripts for initial roles/departments/categories                                 | Create seed migration: `VXXX__seed_role_department_data.sql` |
| **Safe Updates**      | ⚠️ **PARTIAL** | Can add departments via database, but User.department field is string (no FK validation) | After implementing FKs, updates will be safe                 |

### Recommended Seed Migration Structure

```sql
-- Seed Department Categories
INSERT INTO common_company.common_department_category (id, tenant_id, uid, category_name, description, display_order, is_active)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'SYS-CAT-001', 'Production', 'Production-related departments', 1, TRUE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'SYS-CAT-002', 'Administrative', 'Administrative departments', 2, TRUE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'SYS-CAT-003', 'Support', 'Support departments', 3, TRUE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'SYS-CAT-004', 'Utility', 'Utility departments', 4, TRUE);

-- Seed Roles
INSERT INTO common_user.common_role (id, tenant_id, uid, role_name, role_code, description, is_active)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'SYS-ROLE-001', 'Administrator', 'ADMIN', 'Full system access', TRUE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'SYS-ROLE-002', 'Planner', 'PLANNER', 'Production planning access', TRUE),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'SYS-ROLE-003', 'Viewer', 'VIEWER', 'Read-only access', TRUE);
```

---

## 6. Scalability & Maintainability

| Aspect                        | Status               | Observations                                                                                           | Recommendations                                         |
| ----------------------------- | -------------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------- |
| **Admin Panel Support**       | ❌ **NO ENDPOINTS**  | No REST endpoints for managing roles/departments                                                       | Create full CRUD API for admin panel                    |
| **Department-Level Features** | ❌ **NOT SUPPORTED** | Cannot filter/group by department categories. Cannot query users by multiple departments.              | Implement after entity relationships are fixed          |
| **Circular Dependencies**     | ✅ **CLEAR**         | No circular dependencies detected in current structure                                                 | Maintain clear hierarchy: Category → Department → User  |
| **Modularity**                | ⚠️ **PARTIAL**       | Entities exist in separate modules (`common/user`, `common/company`) but relationships not established | Add relationship mappings with proper module boundaries |

### Required API Endpoints

**Department Management:**

- `GET /api/common/departments` - List all departments
- `GET /api/common/departments/{id}` - Get department details
- `POST /api/common/departments` - Create department
- `PUT /api/common/departments/{id}` - Update department
- `DELETE /api/common/departments/{id}` - Deactivate department
- `GET /api/common/departments/by-category/{categoryId}` - Filter by category

**Department Category Management:**

- `GET /api/common/department-categories` - List all categories
- `POST /api/common/department-categories` - Create category
- `PUT /api/common/department-categories/{id}` - Update category
- `DELETE /api/common/department-categories/{id}` - Deactivate category

**Role Management:**

- `GET /api/common/roles` - List all roles
- `POST /api/common/roles` - Create role
- `PUT /api/common/roles/{id}` - Update role
- `DELETE /api/common/roles/{id}` - Deactivate role

**User Assignment:**

- `POST /api/common/users/{userId}/departments` - Assign user to department(s)
- `DELETE /api/common/users/{userId}/departments/{departmentId}` - Remove department assignment
- `GET /api/common/users/{userId}/departments` - Get user's departments
- `PUT /api/common/users/{userId}/role` - Assign role to user

---

## 📊 Overall Compliance Summary

| Category                      | Score    | Status                     |
| ----------------------------- | -------- | -------------------------- |
| Entity Structure              | 1/5      | ❌ **NON-COMPLIANT**       |
| Extensibility                 | 1/4      | ❌ **NON-COMPLIANT**       |
| Data Relationships            | 0/4      | ❌ **NON-COMPLIANT**       |
| Authorization & Policy        | 2/4      | ⚠️ **PARTIALLY COMPLIANT** |
| Data Initialization           | 1/3      | ⚠️ **PARTIALLY COMPLIANT** |
| Scalability & Maintainability | 1/4      | ❌ **NON-COMPLIANT**       |
| **TOTAL**                     | **6/24** | **25% Compliant**          |

---

## 🔧 Priority Implementation Plan

### Phase 1: Critical Foundation (Priority 1 - HIGH)

1. ✅ Create `Role` entity + table + repository
2. ✅ Create `DepartmentCategory` entity + table + repository
3. ✅ Add `department_category_id` FK to `Department`
4. ✅ Create `common_user_department` junction table
5. ✅ Add `role_id` FK to `User` (or create junction table for Many-to-Many)

**Estimated Impact:** Enables proper relationships, referential integrity

### Phase 2: Entity Relationship Mapping (Priority 2 - HIGH)

1. ✅ Add `@ManyToMany` mapping in `User` entity
2. ✅ Add `@ManyToOne` mapping in `Department` → `DepartmentCategory`
3. ✅ Add `@ManyToOne` or `@ManyToMany` mapping in `User` → `Role`
4. ✅ Remove `User.department` String field (migrate data to junction table)

**Estimated Impact:** Enables JPA relationship queries, automatic FK management

### Phase 3: CRUD APIs (Priority 3 - MEDIUM)

1. ✅ Create `DepartmentController` with full CRUD
2. ✅ Create `DepartmentCategoryController`
3. ✅ Create `RoleController`
4. ✅ Add user assignment endpoints

**Estimated Impact:** Enables admin panel, dynamic management without code changes

### Phase 4: Policy Integration (Priority 4 - MEDIUM)

1. ✅ Update `Policy.conditions` to use UUID/code references instead of strings
2. ✅ Update `PolicyService.evaluate()` to query database for user roles/departments
3. ✅ Add validation in policy creation (verify role/department exists)

**Estimated Impact:** Makes authorization truly dynamic and type-safe

### Phase 5: Migration & Seed Data (Priority 5 - LOW)

1. ✅ Create data migration script to move `User.department` string → junction table
2. ✅ Create seed migration for initial roles/categories/departments
3. ✅ Add data validation constraints

**Estimated Impact:** Production-ready initialization

---

## ⚠️ Breaking Changes Required

1. **User Entity:** Remove `department` String field → Use junction table
2. **Policy Conditions:** Change from string arrays to UUID/code arrays
3. **JWT Token:** May need to include role/department IDs instead of names
4. **Frontend:** Update to work with entity IDs instead of string values

---

## ✅ Conclusion

The current architecture **does NOT support** the target enterprise-grade dynamic management requirements. Major structural changes are required:

1. ❌ **Role entity does not exist** (critical gap)
2. ❌ **DepartmentCategory entity does not exist** (critical gap)
3. ❌ **User-Department relationship is String-based** (not relational)
4. ❌ **User-Role relationship does not exist**
5. ⚠️ **Policy evaluation uses string matching** (not entity references)
6. ❌ **No CRUD endpoints for dynamic management**

**Recommendation:** Implement Phases 1-3 immediately to establish proper foundation. Phases 4-5 can follow for full compliance.

**Estimated Implementation Time:** 2-3 weeks for full compliance (Phases 1-5)

---

**Last Updated:** 2025-01-27  
**Analyzed By:** Architecture Review System  
**Status:** ❌ **NON-COMPLIANT** - Requires major refactoring

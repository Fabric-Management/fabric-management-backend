-- ============================================================================
-- V1_0_9: Remove DepartmentCategory — use parent department hierarchy instead
-- ============================================================================
-- DepartmentCategory was a separate entity for grouping departments.
-- This migration replaces it with parent-child department hierarchy:
--   - Top-level departments (no parent) serve as group headers
--   - Child departments are the actual organizational units
-- ============================================================================

-- Step 1: Remove the FK constraint from department -> department_category
ALTER TABLE common_company.common_department
    DROP CONSTRAINT IF EXISTS fk_department_category;

-- Also try the auto-generated constraint name
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT tc.constraint_name INTO constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
        ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_schema = 'common_company'
        AND tc.table_name = 'common_department'
        AND kcu.column_name = 'department_category_id'
        AND tc.constraint_type = 'FOREIGN KEY';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE common_company.common_department DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- Step 2: Drop the department_category_id column from department
ALTER TABLE common_company.common_department
    DROP COLUMN IF EXISTS department_category_id;

-- Step 3: Remove FK from role -> department_category (if it exists)
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT tc.constraint_name INTO constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
        ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_schema = 'common_user'
        AND tc.table_name = 'common_role'
        AND kcu.column_name = 'department_category_id'
        AND tc.constraint_type = 'FOREIGN KEY';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE common_user.common_role DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- Step 4: Set department_category_id to NULL in roles (column kept for backward compat)
UPDATE common_user.common_role SET department_category_id = NULL WHERE department_category_id IS NOT NULL;

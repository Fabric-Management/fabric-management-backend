-- ============================================================================
-- V1_1_0: Remove Position entity and UserPosition junction table
-- ============================================================================
-- Position is no longer needed. Users now only have Department + Role.
-- This migration removes:
--   - common_user_position (junction table)
--   - common_position (entity table)
--   - positions relationship from Department
-- ============================================================================

-- Step 1: Drop UserPosition junction table
DROP TABLE IF EXISTS common_user.common_user_position CASCADE;

-- Step 2: Drop Position table
DROP TABLE IF EXISTS common_company.common_position CASCADE;

-- Step 3: Remove default_role_id FK from Role (if it exists)
-- Note: Role.department_category_id was already handled in V1_0_9
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
        AND kcu.column_name = 'default_position_id'
        AND tc.constraint_type = 'FOREIGN KEY';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE common_user.common_role DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

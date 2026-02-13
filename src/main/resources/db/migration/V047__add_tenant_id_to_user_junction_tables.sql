-- ============================================================================
-- V047: Add tenant_id to UserPosition and UserDepartment junction tables
-- ============================================================================
-- These junction tables were created without tenant_id, breaking multi-tenant
-- isolation. This migration adds tenant_id (populated from User's tenant_id),
-- audit fields, and proper indexes for tenant-scoped queries.
-- ============================================================================

-- ============================================================================
-- 1. ADD COLUMNS TO common_user_department
-- ============================================================================
ALTER TABLE common_user.common_user_department
    ADD COLUMN IF NOT EXISTS tenant_id UUID,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Backfill tenant_id from User entity
UPDATE common_user.common_user_department ud
SET tenant_id = u.tenant_id
FROM common_user.common_user u
WHERE ud.user_id = u.id
  AND ud.tenant_id IS NULL;

-- Make tenant_id NOT NULL after backfill
ALTER TABLE common_user.common_user_department
    ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant-scoped indexes
CREATE INDEX IF NOT EXISTS idx_user_dept_tenant
    ON common_user.common_user_department(tenant_id);

CREATE INDEX IF NOT EXISTS idx_user_dept_tenant_user
    ON common_user.common_user_department(tenant_id, user_id);

-- ============================================================================
-- 2. ADD COLUMNS TO common_user_position
-- ============================================================================
ALTER TABLE common_user.common_user_position
    ADD COLUMN IF NOT EXISTS tenant_id UUID,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Backfill tenant_id from User entity
UPDATE common_user.common_user_position up
SET tenant_id = u.tenant_id
FROM common_user.common_user u
WHERE up.user_id = u.id
  AND up.tenant_id IS NULL;

-- Make tenant_id NOT NULL after backfill
ALTER TABLE common_user.common_user_position
    ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant-scoped indexes
CREATE INDEX IF NOT EXISTS idx_user_pos_tenant
    ON common_user.common_user_position(tenant_id);

CREATE INDEX IF NOT EXISTS idx_user_pos_tenant_user
    ON common_user.common_user_position(tenant_id, user_id);

-- ============================================================================
-- 3. UPDATE CHECK CONSTRAINT for registration token types (add INVITED_USER)
-- ============================================================================
ALTER TABLE common_auth.common_registration_token
    DROP CONSTRAINT IF EXISTS common_registration_token_token_type_check;
ALTER TABLE common_auth.common_registration_token
    ADD CONSTRAINT common_registration_token_token_type_check
    CHECK (token_type IN ('SALES_LED', 'SELF_SERVICE', 'INVITED_USER'));

-- ============================================================================
-- 4. ADD UNIQUE CONSTRAINT on Contact (tenant-scoped contact uniqueness)
-- ============================================================================
-- Prevents race conditions where two concurrent requests create duplicate contacts
-- for the same tenant. ContactService already does an application-level check but
-- this provides a database-level safety net.
CREATE UNIQUE INDEX IF NOT EXISTS uk_contact_tenant_value_type
    ON common_communication.common_contact(tenant_id, contact_value, contact_type)
    WHERE is_active = TRUE;

-- ============================================================================
-- ROLLBACK (commented)
-- ============================================================================
-- ALTER TABLE common_user.common_user_department DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE common_user.common_user_department DROP COLUMN IF EXISTS is_active;
-- ALTER TABLE common_user.common_user_department DROP COLUMN IF EXISTS created_at;
-- ALTER TABLE common_user.common_user_department DROP COLUMN IF EXISTS updated_at;
-- ALTER TABLE common_user.common_user_position DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE common_user.common_user_position DROP COLUMN IF EXISTS is_active;
-- ALTER TABLE common_user.common_user_position DROP COLUMN IF EXISTS created_at;
-- ALTER TABLE common_user.common_user_position DROP COLUMN IF EXISTS updated_at;

-- ============================================================================
-- V1_1_1: Add role_scope column and simplify roles
-- ============================================================================
-- Roles are now generic (not department-specific). Authorization is:
--   Role  = WHAT the user can do (Admin, Manager, Worker, Viewer...)
--   Dept  = WHERE the user operates (Production, Logistics, HR...)
--
-- role_scope determines visibility:
--   INTERNAL = tenant employee roles
--   PARTNER  = trading partner roles
--   SYSTEM   = platform admin (hidden from tenant UI)
-- ============================================================================

-- Step 1: Add role_scope column
ALTER TABLE common_user.common_role
    ADD COLUMN IF NOT EXISTS role_scope VARCHAR(20) DEFAULT 'INTERNAL' NOT NULL;

-- Step 2: Set scope for existing roles based on role_code
UPDATE common_user.common_role SET role_scope = 'SYSTEM'
    WHERE role_code = 'PLATFORM_ADMIN';

UPDATE common_user.common_role SET role_scope = 'PARTNER'
    WHERE role_code IN ('PARTNER_OWNER', 'PARTNER_ACCOUNTANT', 'PARTNER_BUYER', 'PARTNER_VIEWER');

UPDATE common_user.common_role SET role_scope = 'INTERNAL'
    WHERE role_scope NOT IN ('SYSTEM', 'PARTNER');

-- Step 3: Deactivate department-specific roles (replaced by generic ones)
-- Users assigned to these will be migrated in seed migration
UPDATE common_user.common_role SET is_active = FALSE
    WHERE role_code IN ('PROD_MANAGER', 'PROD_WORKER', 'HR_MANAGER', 'LOG_MANAGER', 'WAREHOUSE_WORKER');

-- Step 4: Add index for scope-based queries
CREATE INDEX IF NOT EXISTS idx_role_scope
    ON common_user.common_role (role_scope);

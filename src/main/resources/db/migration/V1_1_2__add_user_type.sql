-- ============================================================================
-- V1_1_2: Add user_type column to common_user
-- ============================================================================
-- Makes Internal vs External distinction explicit on the User entity.
--   INTERNAL = tenant's own staff (may have Employee/HR records)
--   EXTERNAL = partner, supplier, or customer users (no HR records)
--
-- Backfill strategy: derive from role_scope.
--   role_scope = PARTNER  → user_type = EXTERNAL
--   everything else       → user_type = INTERNAL
-- ============================================================================

-- Step 1: Add column with INTERNAL default (safe for most users)
ALTER TABLE common_user.common_user
    ADD COLUMN IF NOT EXISTS user_type VARCHAR(20) DEFAULT 'INTERNAL' NOT NULL;

-- Step 2: Backfill EXTERNAL for users whose role has PARTNER scope
UPDATE common_user.common_user u
SET user_type = 'EXTERNAL'
FROM common_user.common_role r
WHERE u.role_id = r.id
  AND r.role_scope = 'PARTNER';

-- Step 3: Index for filtering by user type
CREATE INDEX IF NOT EXISTS idx_user_type
    ON common_user.common_user (user_type);

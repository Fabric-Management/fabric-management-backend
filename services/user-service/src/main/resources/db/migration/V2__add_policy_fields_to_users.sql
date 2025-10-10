-- ============================================================================
-- Migration V2: Add Policy Authorization Fields to Users Table
-- Feature: Policy Authorization System (Phase 1)
-- Description: Extends users table with company, department, and context fields
-- 
-- Changes:
-- - Add company_id (user's company)
-- - Add department_id (user's department - INTERNAL only)
-- - Add station_id (machine/station assignment - optional)
-- - Add job_title (user's job title)
-- - Add user_context (INTERNAL/CUSTOMER/SUPPLIER/SUBCONTRACTOR)
-- - Add functions (array of function codes)
-- 
-- Impact: Backward compatible - all new fields nullable (except user_context)
-- Dependencies: Requires V1 (users table exists)
-- ============================================================================

-- Add policy-related fields to users table (IF NOT EXISTS)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS company_id UUID,
    ADD COLUMN IF NOT EXISTS department_id UUID,
    ADD COLUMN IF NOT EXISTS station_id UUID,
    ADD COLUMN IF NOT EXISTS job_title VARCHAR(100),
    ADD COLUMN IF NOT EXISTS user_context VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN IF NOT EXISTS functions TEXT[];

-- Add comments for documentation
COMMENT ON COLUMN users.company_id IS 'Company that user belongs to. NULL for system users (Super Admin).';
COMMENT ON COLUMN users.department_id IS 'Department assignment. Only for INTERNAL users. NULL for external users.';
COMMENT ON COLUMN users.station_id IS 'Station/Machine assignment. Optional. Used for production floor operators.';
COMMENT ON COLUMN users.job_title IS 'User job title. Examples: Dokumacı, Muhasebeci, Kalite Kontrolcü';
COMMENT ON COLUMN users.user_context IS 'User relationship to system: INTERNAL, CUSTOMER, SUPPLIER, SUBCONTRACTOR';
COMMENT ON COLUMN users.functions IS 'Array of function codes. Example: {WEAVING, QUALITY, FINANCE}';

-- Add check constraint for user_context (IF NOT EXISTS)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_user_context') THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_user_context
                CHECK (user_context IN ('INTERNAL', 'CUSTOMER', 'SUPPLIER', 'SUBCONTRACTOR'));
    END IF;
END $$;

-- Create indexes for performance (IF NOT EXISTS)
-- Partial indexes (WHERE clause) for better performance when field is not null
CREATE INDEX IF NOT EXISTS idx_users_company_id ON users(company_id) WHERE company_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_department_id ON users(department_id) WHERE department_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_user_context ON users(user_context);
CREATE INDEX IF NOT EXISTS idx_users_station_id ON users(station_id) WHERE station_id IS NOT NULL;

-- Composite index for common queries (company + context)
CREATE INDEX IF NOT EXISTS idx_users_company_context ON users(company_id, user_context) 
    WHERE company_id IS NOT NULL AND deleted = FALSE;

-- Update table comment
COMMENT ON TABLE users IS 'Users table - Extended with policy authorization fields (V2)';

-- Note: Foreign key for company_id will be added after companies table has business_type field (V3)
-- Note: Foreign key for department_id will be added after departments table created (V5)


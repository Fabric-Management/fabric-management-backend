-- ============================================================================
-- Migration V2: Add Policy Authorization Fields to Companies Table
-- Feature: Policy Authorization System
-- Description: Extends companies table with business relationship fields
-- 
-- Changes:
-- - Add business_type (MANUFACTURER/CUSTOMER/SUPPLIER/SUBCONTRACTOR)
-- - Add parent_company_id (for external companies - links to manufacturer)
-- - Add relationship_type (CUSTOMER/SUPPLIER/SUBCONTRACTOR)
-- 
-- Note: 'type' column already exists (legal entity: CORPORATION, LLC, etc.)
--       'business_type' is different - business relationship to system
-- 
-- Dependencies: V1 (companies table exists)
-- ============================================================================

-- Add business relationship fields (IF NOT EXISTS)
ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS business_type VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN IF NOT EXISTS parent_company_id UUID,
    ADD COLUMN IF NOT EXISTS relationship_type VARCHAR(50);

-- Add comments
COMMENT ON COLUMN companies.type IS 'Legal entity type (CORPORATION, LLC, PARTNERSHIP, etc.)';
COMMENT ON COLUMN companies.business_type IS 'Business relationship: INTERNAL (us), CUSTOMER, SUPPLIER, SUBCONTRACTOR';
COMMENT ON COLUMN companies.parent_company_id IS 'Parent company (INTERNAL). NULL for INTERNAL companies, set for external companies.';
COMMENT ON COLUMN companies.relationship_type IS 'Relationship to parent: CUSTOMER, SUPPLIER, SUBCONTRACTOR. NULL for INTERNAL.';

-- Add constraints (IF NOT EXISTS using DO block)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_companies_business_type') THEN
        ALTER TABLE companies
            ADD CONSTRAINT chk_companies_business_type
                CHECK (business_type IN ('INTERNAL', 'CUSTOMER', 'SUPPLIER', 'SUBCONTRACTOR'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_companies_relationship_type') THEN
        ALTER TABLE companies
            ADD CONSTRAINT chk_companies_relationship_type
                CHECK (
                    (business_type = 'INTERNAL' AND relationship_type IS NULL) OR
                    (business_type != 'INTERNAL' AND relationship_type IN ('CUSTOMER', 'SUPPLIER', 'SUBCONTRACTOR'))
                );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_companies_parent') THEN
        ALTER TABLE companies
            ADD CONSTRAINT fk_companies_parent
                FOREIGN KEY (parent_company_id) REFERENCES companies(id)
                ON DELETE SET NULL;
    END IF;
END $$;

-- Create indexes (IF NOT EXISTS)
CREATE INDEX IF NOT EXISTS idx_companies_business_type ON companies(business_type);
CREATE INDEX IF NOT EXISTS idx_companies_parent ON companies(parent_company_id) WHERE parent_company_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_companies_business_parent ON companies(business_type, parent_company_id) WHERE deleted = FALSE;

-- Update existing companies to INTERNAL (our main company)
UPDATE companies SET business_type = 'INTERNAL' WHERE business_type IS NULL;

-- =============================================================================
-- PLATFORM TENANT (Reserved for SUPER_ADMIN)
-- =============================================================================
-- Add is_platform flag to identify platform tenant vs normal tenants
-- Only ONE platform tenant allowed in entire system

-- Add is_platform column
ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS is_platform BOOLEAN NOT NULL DEFAULT FALSE;

-- Unique constraint: Only ONE platform company allowed
CREATE UNIQUE INDEX IF NOT EXISTS idx_companies_platform_unique 
    ON companies (is_platform) WHERE is_platform = TRUE;

-- Add comment
COMMENT ON COLUMN companies.is_platform IS 'Platform tenant flag. TRUE only for system platform (reserved for SUPER_ADMIN). Default: FALSE for normal tenants.';

-- Insert PLATFORM tenant (reserved for future SUPER_ADMIN functionality)
-- This tenant is referenced by user-service SUPER_ADMIN seed data
INSERT INTO companies (
    id,
    tenant_id,
    name,
    legal_name,
    type,
    industry,
    status,
    business_type,
    is_platform,
    is_active,
    max_users,
    current_users,
    created_by,
    updated_by
) VALUES (
    '00000000-0000-0000-0000-000000000000'::UUID,
    '00000000-0000-0000-0000-000000000000'::UUID,
    'Fabricode Platform',
    'Fabricode Platform Inc.',
    'OTHER',
    'TECHNOLOGY',
    'ACTIVE',
    'INTERNAL',
    TRUE,
    TRUE,
    999999,
    0,
    'SYSTEM',
    'SYSTEM'
) ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE companies IS 'Companies table - Extended with policy fields + platform tenant (V2)';


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

COMMENT ON TABLE companies IS 'Companies table - Extended with policy fields (V2)';


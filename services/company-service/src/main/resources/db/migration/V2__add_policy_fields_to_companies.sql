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

-- Add business relationship fields
ALTER TABLE companies
    ADD COLUMN business_type VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN parent_company_id UUID,
    ADD COLUMN relationship_type VARCHAR(50);

-- Add comments
COMMENT ON COLUMN companies.type IS 'Legal entity type (CORPORATION, LLC, PARTNERSHIP, etc.)';
COMMENT ON COLUMN companies.business_type IS 'Business relationship: INTERNAL (us), CUSTOMER, SUPPLIER, SUBCONTRACTOR';
COMMENT ON COLUMN companies.parent_company_id IS 'Parent company (INTERNAL). NULL for INTERNAL companies, set for external companies.';
COMMENT ON COLUMN companies.relationship_type IS 'Relationship to parent: CUSTOMER, SUPPLIER, SUBCONTRACTOR. NULL for INTERNAL.';

-- Add constraints
ALTER TABLE companies
    ADD CONSTRAINT chk_companies_business_type
        CHECK (business_type IN ('INTERNAL', 'CUSTOMER', 'SUPPLIER', 'SUBCONTRACTOR'));

ALTER TABLE companies
    ADD CONSTRAINT chk_companies_relationship_type
        CHECK (
            (business_type = 'INTERNAL' AND relationship_type IS NULL) OR
            (business_type != 'INTERNAL' AND relationship_type IN ('CUSTOMER', 'SUPPLIER', 'SUBCONTRACTOR'))
        );

-- Add foreign key (self-referencing)
ALTER TABLE companies
    ADD CONSTRAINT fk_companies_parent
        FOREIGN KEY (parent_company_id) REFERENCES companies(id)
        ON DELETE SET NULL;

-- Create indexes
CREATE INDEX idx_companies_business_type ON companies(business_type);
CREATE INDEX idx_companies_parent ON companies(parent_company_id) WHERE parent_company_id IS NOT NULL;
CREATE INDEX idx_companies_business_parent ON companies(business_type, parent_company_id) WHERE deleted = FALSE;

-- Update existing companies to INTERNAL (our main company)
UPDATE companies SET business_type = 'INTERNAL' WHERE business_type IS NULL;

COMMENT ON TABLE companies IS 'Companies table - Extended with policy fields (V2)';


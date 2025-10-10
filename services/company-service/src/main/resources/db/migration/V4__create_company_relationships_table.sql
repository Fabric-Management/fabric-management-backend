-- ============================================================================
-- Migration V4: Create Company Relationships Table
-- Feature: Policy Authorization System
-- Description: Creates company_relationships for trust-based cross-company access
-- 
-- Purpose:
-- - Define business relationships between companies
-- - Enable cross-company data access based on trust
-- - Configure allowed modules and actions per relationship
-- 
-- Dependencies: V1 (companies table)
-- ============================================================================

CREATE TABLE IF NOT EXISTS company_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_company_id UUID NOT NULL,
    target_company_id UUID NOT NULL,
    relationship_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    allowed_modules TEXT[],
    allowed_actions TEXT[],
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uk_relationships_pair UNIQUE (source_company_id, target_company_id),
    CONSTRAINT chk_relationships_type CHECK (relationship_type IN ('CUSTOMER', 'SUPPLIER', 'SUBCONTRACTOR')),
    CONSTRAINT chk_relationships_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT chk_relationships_different CHECK (source_company_id != target_company_id)
);

COMMENT ON TABLE company_relationships IS 'Company trust relationships (V4)';

-- Foreign keys (IF NOT EXISTS)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_relationships_source') THEN
        ALTER TABLE company_relationships
            ADD CONSTRAINT fk_relationships_source 
                FOREIGN KEY (source_company_id) REFERENCES companies(id) ON DELETE CASCADE;
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_relationships_target') THEN
        ALTER TABLE company_relationships
            ADD CONSTRAINT fk_relationships_target 
                FOREIGN KEY (target_company_id) REFERENCES companies(id) ON DELETE CASCADE;
    END IF;
END $$;

-- Indexes (IF NOT EXISTS)
CREATE INDEX IF NOT EXISTS idx_relationships_source ON company_relationships(source_company_id);
CREATE INDEX IF NOT EXISTS idx_relationships_target ON company_relationships(target_company_id);
CREATE INDEX IF NOT EXISTS idx_relationships_active ON company_relationships(source_company_id, target_company_id, status) WHERE status = 'ACTIVE';

-- Trigger (IF NOT EXISTS)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_company_relationships_updated_at') THEN
        CREATE TRIGGER update_company_relationships_updated_at
            BEFORE UPDATE ON company_relationships
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;


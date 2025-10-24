-- ============================================================================
-- Migration V3: Create Departments Table
-- ============================================================================

CREATE TABLE IF NOT EXISTS departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    name_en VARCHAR(200),
    type VARCHAR(50) NOT NULL,
    manager_id UUID,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    
    CONSTRAINT uk_departments_company_code UNIQUE (company_id, code),
    CONSTRAINT chk_departments_type
        CHECK (type IN ('PRODUCTION', 'QUALITY', 'WAREHOUSE', 'FINANCE', 
                       'SALES', 'PURCHASING', 'HR', 'IT', 'MANAGEMENT'))
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_departments_company') THEN
        ALTER TABLE departments
            ADD CONSTRAINT fk_departments_company
                FOREIGN KEY (company_id) REFERENCES companies(id)
                ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_departments_company ON departments(company_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_departments_type ON departments(type) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_departments_company_type ON departments(company_id, type) WHERE deleted = FALSE AND active = TRUE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_departments_updated_at') THEN
        CREATE TRIGGER update_departments_updated_at
            BEFORE UPDATE ON departments
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
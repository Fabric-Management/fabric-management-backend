-- ============================================================================
-- Migration V3: Create Departments Table
-- Feature: Policy Authorization System
-- Description: Creates departments table for organizational structure
-- 
-- Purpose:
-- - Define functional departments within companies
-- - Enable department-based dashboard routing
-- - Support department-level permissions (INTERNAL users only)
-- 
-- Dependencies: V1 (companies table)
-- ============================================================================

-- Create departments table
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

-- Add comments
COMMENT ON TABLE departments IS 'Departments table - Organizational units (V3)';
COMMENT ON COLUMN departments.code IS 'Department code. Example: WEAVING, FINANCE, QC';
COMMENT ON COLUMN departments.type IS 'Department type: PRODUCTION, QUALITY, WAREHOUSE, FINANCE, etc.';

-- Foreign keys
ALTER TABLE departments
    ADD CONSTRAINT fk_departments_company
        FOREIGN KEY (company_id) REFERENCES companies(id)
        ON DELETE CASCADE;

-- Indexes
CREATE INDEX idx_departments_company ON departments(company_id) WHERE deleted = FALSE;
CREATE INDEX idx_departments_type ON departments(type) WHERE deleted = FALSE;
CREATE INDEX idx_departments_company_type ON departments(company_id, type) WHERE deleted = FALSE AND active = TRUE;

-- Trigger
CREATE TRIGGER update_departments_updated_at
    BEFORE UPDATE ON departments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- ============================================================================
-- V3: User Module Tables
-- ============================================================================
-- Creates user tables (depends on company tables from V002)
-- User management - NO username field! Uses contactValue (email/phone)
-- Last Updated: 2025-10-25
-- ============================================================================

-- ============================================================================
-- TABLE: common_user
-- ============================================================================
CREATE TABLE common_user.common_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    
    -- Deprecated fields (kept for backward compatibility, will be removed in future)
    -- Use UserContact junction table and Contact entity instead
    -- NOT NULL constraints removed in V018
    contact_value VARCHAR(255),
    contact_type VARCHAR(20),
    
    company_id UUID NOT NULL,
    -- Deprecated field (kept for backward compatibility, will be removed in future)
    -- Use UserDepartment junction table instead
    -- Index removed in V018
    department VARCHAR(100),
    last_active_at TIMESTAMP,
    
    -- NOTE: role_id added in V013, onboarding_completed_at added in V004
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_user_company FOREIGN KEY (company_id) 
        REFERENCES common_company.common_company(id) ON DELETE RESTRICT
);

-- Index on contact_value removed in V018 (deprecated field)
CREATE INDEX idx_user_tenant_company ON common_user.common_user(tenant_id, company_id);
-- Index on department removed in V018 (deprecated field)
CREATE INDEX idx_user_active ON common_user.common_user(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_user.common_user IS 'Platform users - NO username! Use Contact entity via UserContact junction';
COMMENT ON COLUMN common_user.common_user.contact_value IS 'DEPRECATED: Removed from entity. Use Contact entity via UserContact junction instead. NOT NULL removed in V018. Will be dropped in future migration.';
COMMENT ON COLUMN common_user.common_user.contact_type IS 'DEPRECATED: Removed from entity. Use Contact entity via UserContact junction instead. NOT NULL removed in V018. Will be dropped in future migration.';
COMMENT ON COLUMN common_user.common_user.department IS 'DEPRECATED: Removed from entity. Use UserDepartment junction table instead. Index removed in V018. Will be dropped in future migration.';
COMMENT ON COLUMN common_user.common_user.display_name IS 'Auto-generated: firstName + lastName';


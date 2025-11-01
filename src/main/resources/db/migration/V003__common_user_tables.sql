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
    
    contact_value VARCHAR(255) UNIQUE NOT NULL,
    contact_type VARCHAR(20) NOT NULL,
    
    company_id UUID NOT NULL,
    department VARCHAR(100),
    last_active_at TIMESTAMP,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_user_company FOREIGN KEY (company_id) 
        REFERENCES common_company.common_company(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX idx_user_contact ON common_user.common_user(contact_value);
CREATE INDEX idx_user_tenant_company ON common_user.common_user(tenant_id, company_id);
CREATE INDEX idx_user_department ON common_user.common_user(department);
CREATE INDEX idx_user_active ON common_user.common_user(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_user.common_user IS 'Platform users - NO username! contactValue is the identifier';
COMMENT ON COLUMN common_user.common_user.contact_value IS 'Email or phone (E.164) - PRIMARY identifier for login';
COMMENT ON COLUMN common_user.common_user.contact_type IS 'EMAIL or PHONE';
COMMENT ON COLUMN common_user.common_user.display_name IS 'Auto-generated: firstName + lastName';


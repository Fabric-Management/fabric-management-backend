-- ============================================================================
-- V071: Trading Partner Certification
-- ============================================================================
-- Links trading partners to certifications (GOTS, OEKO-TEX, etc.) with
-- license details and validity period.
-- References production.prod_fiber_certification for certification types.
-- ============================================================================

CREATE TABLE IF NOT EXISTS common_company.partner_trading_partner_certification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    
    trading_partner_id UUID NOT NULL,
    certification_id UUID NOT NULL,
    
    license_no VARCHAR(100),
    issued_at DATE,
    valid_until DATE,
    document_ref VARCHAR(255),
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_ptpc_tenant FOREIGN KEY (tenant_id)
        REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ptpc_trading_partner FOREIGN KEY (trading_partner_id)
        REFERENCES common_company.common_trading_partner(id) ON DELETE CASCADE,
    CONSTRAINT fk_ptpc_certification FOREIGN KEY (certification_id)
        REFERENCES production.prod_fiber_certification(id) ON DELETE RESTRICT
);

CREATE INDEX idx_ptpc_tenant ON common_company.partner_trading_partner_certification(tenant_id);
CREATE INDEX idx_ptpc_trading_partner ON common_company.partner_trading_partner_certification(trading_partner_id);
CREATE INDEX idx_ptpc_certification ON common_company.partner_trading_partner_certification(certification_id);
CREATE INDEX idx_ptpc_active ON common_company.partner_trading_partner_certification(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_company.partner_trading_partner_certification IS 
'Partner certifications (GOTS, OEKO-TEX, etc.) with license number and validity period.';

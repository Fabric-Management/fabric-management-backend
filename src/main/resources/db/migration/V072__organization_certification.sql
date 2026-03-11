-- ============================================================================
-- V072: Organization Certification
-- ============================================================================
-- Links organizations to certifications (GOTS, OEKO-TEX, etc.) with
-- license details and validity period.
-- References production.prod_fiber_certification for certification types.
-- ============================================================================

CREATE TABLE IF NOT EXISTS common_company.organization_certification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    
    organization_id UUID NOT NULL,
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
    
    CONSTRAINT fk_oc_tenant FOREIGN KEY (tenant_id)
        REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_oc_organization FOREIGN KEY (organization_id)
        REFERENCES common_company.common_organization(id) ON DELETE CASCADE,
    CONSTRAINT fk_oc_certification FOREIGN KEY (certification_id)
        REFERENCES production.prod_fiber_certification(id) ON DELETE RESTRICT
);

CREATE INDEX idx_oc_tenant ON common_company.organization_certification(tenant_id);
CREATE INDEX idx_oc_organization ON common_company.organization_certification(organization_id);
CREATE INDEX idx_oc_certification ON common_company.organization_certification(certification_id);
CREATE INDEX idx_oc_active ON common_company.organization_certification(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE common_company.organization_certification IS 
'Organization certifications (GOTS, OEKO-TEX, etc.) with license number and validity period.';

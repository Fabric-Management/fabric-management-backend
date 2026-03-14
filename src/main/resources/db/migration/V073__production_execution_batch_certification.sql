-- ============================================================================
-- V073: Batch Certification
-- ============================================================================
-- Links batches to certifications (GOTS, OEKO-TEX, etc.) with optional
-- references to partner or organization certification records.
-- References production.prod_fiber_certification for certification types.
-- ============================================================================

CREATE TABLE IF NOT EXISTS production.production_execution_batch_certification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,

    batch_id UUID NOT NULL,
    certification_id UUID NOT NULL,
    scope VARCHAR(50) NOT NULL DEFAULT 'BATCH',

    partner_certification_id UUID,
    org_certification_id UUID,

    cert_number VARCHAR(100),
    valid_from DATE,
    valid_until DATE,
    certifying_body_ref VARCHAR(255),
    document_url VARCHAR(512),
    remarks TEXT,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_bc_tenant FOREIGN KEY (tenant_id)
        REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bc_batch FOREIGN KEY (batch_id)
        REFERENCES production.production_execution_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_bc_certification FOREIGN KEY (certification_id)
        REFERENCES production.prod_fiber_certification(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bc_partner_cert FOREIGN KEY (partner_certification_id)
        REFERENCES common_company.partner_trading_partner_certification(id) ON DELETE SET NULL,
    CONSTRAINT fk_bc_org_cert FOREIGN KEY (org_certification_id)
        REFERENCES common_company.organization_certification(id) ON DELETE SET NULL
);

CREATE INDEX idx_bc_tenant ON production.production_execution_batch_certification(tenant_id);
CREATE INDEX idx_bc_batch ON production.production_execution_batch_certification(batch_id);
CREATE INDEX idx_bc_certification ON production.production_execution_batch_certification(certification_id);
CREATE INDEX idx_bc_active ON production.production_execution_batch_certification(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.production_execution_batch_certification IS
'Batch certifications (GOTS, OEKO-TEX, etc.) with optional link to partner or organization certification.';
COMMENT ON COLUMN production.production_execution_batch_certification.scope IS
'BATCH, FACILITY, or SUPPLIER';
COMMENT ON COLUMN production.production_execution_batch_certification.partner_certification_id IS
'Optional link to trading partner certification record';
COMMENT ON COLUMN production.production_execution_batch_certification.org_certification_id IS
'Optional link to organization certification record';

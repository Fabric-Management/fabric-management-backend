-- ============================================================================
-- V080: Production Fiber Request
-- ============================================================================
-- Tenant-initiated requests to add new fibers to the platform catalog.
-- Platform admins approve or reject; approved requests can create Fiber entities.
-- Follows BaseEntity pattern.
-- ============================================================================

CREATE TABLE IF NOT EXISTS production.production_fiber_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,

    requested_by UUID NOT NULL,
    iso_code VARCHAR(20) NOT NULL,
    fiber_name VARCHAR(255) NOT NULL,
    fiber_type VARCHAR(50) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by UUID,
    review_note TEXT,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_fiber_request_tenant FOREIGN KEY (tenant_id)
        REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT chk_fiber_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_fiber_request_tenant ON production.production_fiber_request(tenant_id);
CREATE INDEX idx_fiber_request_status ON production.production_fiber_request(status);
CREATE INDEX idx_fiber_request_requested_by ON production.production_fiber_request(requested_by);
CREATE INDEX idx_fiber_request_active ON production.production_fiber_request(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.production_fiber_request IS
'Tenant-initiated requests to add new fibers to the platform catalog. PENDING → APPROVED/REJECTED by platform.';
COMMENT ON COLUMN production.production_fiber_request.iso_code IS
'ISO code for the requested fiber (e.g. KPS for Kapok).';
COMMENT ON COLUMN production.production_fiber_request.fiber_type IS
'Fiber category/type code.';
COMMENT ON COLUMN production.production_fiber_request.status IS
'PENDING: tenant submitted; APPROVED: platform approved; REJECTED: platform rejected.';

-- SOI-D4: piece-level hard allocation at sales-order confirmation (A05, TK-4). The lot counter
-- stays in production_execution_batch_reservation; this table names the pieces.
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS production.stock_unit_allocation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    stock_unit_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    batch_reservation_id UUID NOT NULL
        REFERENCES production.production_execution_batch_reservation (id),
    sales_order_id UUID NOT NULL,
    sales_order_line_id UUID NOT NULL,
    quantity NUMERIC(15, 3) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED')),
    allocated_by UUID NOT NULL,
    allocated_at TIMESTAMPTZ NOT NULL,
    released_by UUID,
    released_at TIMESTAMPTZ,
    release_reason VARCHAR(60),
    CONSTRAINT chk_stock_unit_allocation_release CHECK (
        (status = 'ACTIVE' AND released_at IS NULL AND released_by IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL AND released_by IS NOT NULL
            AND release_reason IS NOT NULL))
);
-- A piece has at most one active hard allocation (business-rules §5 rule 3).
CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_unit_allocation_active_piece
    ON production.stock_unit_allocation (tenant_id, stock_unit_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_stock_unit_allocation_line
    ON production.stock_unit_allocation (tenant_id, sales_order_line_id, status);
CREATE INDEX IF NOT EXISTS idx_stock_unit_allocation_batch
    ON production.stock_unit_allocation (tenant_id, batch_id, status);

ALTER TABLE production.stock_unit_allocation ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.stock_unit_allocation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON production.stock_unit_allocation FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT, UPDATE ON production.stock_unit_allocation TO fabric_app;
        REVOKE DELETE ON production.stock_unit_allocation FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON production.stock_unit_allocation TO fabric_system;
    END IF;
END $$;

COMMENT ON TABLE production.stock_unit_allocation IS
    'Whole piece held for one sales-order line from confirmation (SOI D4, TK-4).';

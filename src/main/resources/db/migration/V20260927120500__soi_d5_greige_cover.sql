-- SOI-D5: finished goods planning confirms from available greige (A08, R13, K10). Stock held at
-- confirmation is read from production.stock_unit_allocation; new supply is the remainder.
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS sales_ord.line_greige_cover (
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
    sales_order_id UUID NOT NULL,
    sales_order_line_id UUID NOT NULL,
    finished_qty NUMERIC(15, 3) NOT NULL CHECK (finished_qty > 0),
    unit VARCHAR(20) NOT NULL,
    greige_batch_ids JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(greige_batch_ids) = 'array'),
    basis_note TEXT NOT NULL CHECK (length(trim(basis_note)) > 0),
    estimate_sample_size BIGINT NOT NULL CHECK (estimate_sample_size >= 0),
    estimate_avg_yield_pct NUMERIC(7, 2),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    confirmed_by UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    withdrawn_by UUID,
    withdrawn_at TIMESTAMPTZ,
    CONSTRAINT fk_line_greige_cover_line FOREIGN KEY (tenant_id, sales_order_line_id)
        REFERENCES sales_ord.sales_order_line (tenant_id, id),
    CONSTRAINT chk_line_greige_cover_withdrawal CHECK (
        (status = 'ACTIVE') = (withdrawn_at IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_line_greige_cover_active
    ON sales_ord.line_greige_cover (tenant_id, sales_order_line_id) WHERE status = 'ACTIVE';

ALTER TABLE sales_ord.line_greige_cover ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.line_greige_cover FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sales_ord.line_greige_cover FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT, UPDATE ON sales_ord.line_greige_cover TO fabric_app;
        REVOKE DELETE ON sales_ord.line_greige_cover FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.line_greige_cover TO fabric_system;
    END IF;
END $$;

COMMENT ON TABLE sales_ord.line_greige_cover IS
    'Finished quantity of a line confirmed to come from available greige; the estimate is kept, the confirmation counts (SOI A08).';

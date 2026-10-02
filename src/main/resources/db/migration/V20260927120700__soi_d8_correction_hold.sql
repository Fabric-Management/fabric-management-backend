-- SOI-D8: traced product correction (K18, R19) and holds on running work (K19, R20, A12).
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS sales_ord.line_product_correction (
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
    old_product_id UUID NOT NULL,
    new_product_id UUID NOT NULL,
    line_version BIGINT NOT NULL,
    reason TEXT,
    corrected_by UUID NOT NULL,
    corrected_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_line_product_correction_line FOREIGN KEY (tenant_id, sales_order_line_id)
        REFERENCES sales_ord.sales_order_line (tenant_id, id),
    CONSTRAINT chk_line_product_correction_change CHECK (old_product_id <> new_product_id)
);
CREATE INDEX IF NOT EXISTS idx_line_product_correction_order
    ON sales_ord.line_product_correction (tenant_id, sales_order_id, corrected_at DESC);

CREATE TABLE IF NOT EXISTS production.work_order_hold (
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
    work_order_id UUID NOT NULL,
    sales_order_id UUID,
    sales_order_line_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('HOLD_REQUESTED', 'HOLD_CONFIRMED', 'RESUMED', 'WITHDRAWN')),
    request_reason TEXT NOT NULL CHECK (length(trim(request_reason)) > 0),
    requested_by UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    stop_note TEXT,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    resume_note TEXT,
    resumed_by UUID,
    resumed_at TIMESTAMPTZ,
    CONSTRAINT chk_work_order_hold_confirmed CHECK (
        status NOT IN ('HOLD_CONFIRMED', 'RESUMED')
        OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL AND stop_note IS NOT NULL)),
    CONSTRAINT chk_work_order_hold_resumed CHECK (
        status <> 'RESUMED'
        OR (resumed_by IS NOT NULL AND resumed_at IS NOT NULL AND resume_note IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_work_order_hold_open
    ON production.work_order_hold (tenant_id, work_order_id)
    WHERE status IN ('HOLD_REQUESTED', 'HOLD_CONFIRMED');
CREATE INDEX IF NOT EXISTS idx_work_order_hold_line
    ON production.work_order_hold (tenant_id, sales_order_line_id, requested_at DESC);

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['sales_ord.line_product_correction', 'production.work_order_hold'] LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($p$CREATE POLICY rls_tenant_isolation ON %s FOR ALL
            USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
            WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID)$p$, t);
    END LOOP;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT ON sales_ord.line_product_correction TO fabric_app;
        REVOKE UPDATE, DELETE ON sales_ord.line_product_correction FROM fabric_app;
        GRANT SELECT, INSERT, UPDATE ON production.work_order_hold TO fabric_app;
        REVOKE DELETE ON production.work_order_hold FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, DELETE ON sales_ord.line_product_correction TO fabric_system;
        GRANT SELECT, INSERT, UPDATE, DELETE ON production.work_order_hold TO fabric_system;
    END IF;
END $$;

COMMENT ON TABLE sales_ord.line_product_correction IS
    'Traced replacement of a wrongly chosen product; acceptances stop covering the line (SOI R19).';
COMMENT ON TABLE production.work_order_hold IS
    'Hold request, confirmed physical stop and resume of running work; not a cancellation (SOI A12).';

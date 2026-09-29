-- SOI-D6: readiness per cover portion (planning / warehouse) and the sourced arrival estimate
-- (K13, R15, A07, A07-b). No lead time is stored as a constant anywhere.
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS sales_ord.line_portion_readiness (
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
    portion VARCHAR(20) NOT NULL CHECK (portion IN ('FINISHED_STOCK', 'FROM_GREIGE', 'NEW_SUPPLY')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED', 'CONFIRMED', 'WITHDRAWN')),
    requested_by UUID,
    requested_at TIMESTAMPTZ,
    ready_on DATE,
    basis_note TEXT,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT fk_line_portion_readiness_line FOREIGN KEY (tenant_id, sales_order_line_id)
        REFERENCES sales_ord.sales_order_line (tenant_id, id),
    CONSTRAINT chk_line_portion_readiness_request CHECK ((requested_by IS NULL) = (requested_at IS NULL)),
    CONSTRAINT chk_line_portion_readiness_confirmed CHECK (
        status <> 'CONFIRMED'
        OR (ready_on IS NOT NULL AND basis_note IS NOT NULL AND confirmed_by IS NOT NULL
            AND confirmed_at IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_line_portion_readiness_open
    ON sales_ord.line_portion_readiness (tenant_id, sales_order_line_id, portion)
    WHERE status IN ('REQUESTED', 'CONFIRMED');
CREATE INDEX IF NOT EXISTS idx_line_portion_readiness_queue
    ON sales_ord.line_portion_readiness (tenant_id, status, portion, requested_at);

CREATE TABLE IF NOT EXISTS sales_ord.order_arrival_estimate (
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
    earliest_on DATE NOT NULL,
    latest_on DATE NOT NULL,
    source VARCHAR(30) NOT NULL CHECK (source IN ('CARRIER', 'AUTHORISED_RECORD')),
    source_reference VARCHAR(500) NOT NULL CHECK (length(trim(source_reference)) > 0),
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    superseded_at TIMESTAMPTZ,
    CONSTRAINT chk_order_arrival_estimate_range CHECK (latest_on >= earliest_on)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_order_arrival_estimate_current
    ON sales_ord.order_arrival_estimate (tenant_id, sales_order_id) WHERE superseded_at IS NULL;

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['sales_ord.line_portion_readiness', 'sales_ord.order_arrival_estimate'] LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($p$CREATE POLICY rls_tenant_isolation ON %s FOR ALL
            USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
            WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID)$p$, t);
    END LOOP;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT, UPDATE ON sales_ord.line_portion_readiness,
            sales_ord.order_arrival_estimate TO fabric_app;
        REVOKE DELETE ON sales_ord.line_portion_readiness,
            sales_ord.order_arrival_estimate FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.line_portion_readiness,
            sales_ord.order_arrival_estimate TO fabric_system;
    END IF;
END $$;

COMMENT ON TABLE sales_ord.line_portion_readiness IS
    'Confirmed ready-to-ship date of a cover portion; not a delivery date (SOI A07).';
COMMENT ON TABLE sales_ord.order_arrival_estimate IS
    'Expected arrival at the customer from a carrier or an authorised sourced record (SOI A07-b).';

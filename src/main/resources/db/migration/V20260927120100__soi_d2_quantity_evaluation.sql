-- SOI-D2: whole-piece quantity evaluation.
--   production: finished-width lot evidence (limited LOT-EVIDENCE-1), piece cut records (A06) and
--               technical lot-compatibility confirmations (A04).
--   sales:      customer acceptance of a concrete shade difference (A04-b), agreed quantity
--               tolerance (A03) and append-only quantity proposals.
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS production.batch_finished_width_measurement (
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
    batch_id UUID NOT NULL,
    width_value NUMERIC(8, 2) NOT NULL CHECK (width_value > 0),
    width_unit VARCHAR(10) NOT NULL CHECK (width_unit IN ('CM', 'IN')),
    method_note TEXT,
    measured_by UUID NOT NULL,
    measured_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_batch_width_measurement_batch
    ON production.batch_finished_width_measurement (tenant_id, batch_id, measured_at DESC);

CREATE TABLE IF NOT EXISTS production.stock_unit_cut (
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
    cut_length NUMERIC(15, 3) NOT NULL CHECK (cut_length > 0),
    remaining_length NUMERIC(15, 3) NOT NULL CHECK (remaining_length >= 0),
    length_unit VARCHAR(10) NOT NULL,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    remaining_verified_by UUID,
    remaining_verified_at TIMESTAMPTZ,
    CONSTRAINT chk_stock_unit_cut_verification CHECK (
        (remaining_verified_by IS NULL) = (remaining_verified_at IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_stock_unit_cut_unit
    ON production.stock_unit_cut (tenant_id, stock_unit_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS production.lot_compatibility_confirmation (
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
    batch_ids JSONB NOT NULL CHECK (jsonb_typeof(batch_ids) = 'array'
        AND jsonb_array_length(batch_ids) >= 2),
    customer_id UUID,
    conditions TEXT NOT NULL CHECK (length(trim(conditions)) > 0),
    confirmed_by UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    revoked_by UUID,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT chk_lot_compatibility_revocation CHECK (
        (revoked_by IS NULL) = (revoked_at IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_lot_compatibility_batches
    ON production.lot_compatibility_confirmation USING GIN (batch_ids);

CREATE TABLE IF NOT EXISTS sales_ord.customer_tone_acceptance (
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
    customer_id UUID NOT NULL,
    sales_order_line_id UUID NOT NULL,
    batch_ids JSONB NOT NULL CHECK (jsonb_typeof(batch_ids) = 'array'
        AND jsonb_array_length(batch_ids) >= 2),
    evidence_note TEXT NOT NULL CHECK (length(trim(evidence_note)) > 0),
    evidence_attachment_id UUID,
    customer_contact VARCHAR(200) NOT NULL CHECK (length(trim(customer_contact)) > 0),
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('PHONE', 'EMAIL', 'MESSAGE', 'IN_PERSON')),
    accepted_at TIMESTAMPTZ NOT NULL,
    customer_statement_confirmed BOOLEAN NOT NULL CHECK (customer_statement_confirmed),
    recorded_by UUID NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_customer_tone_acceptance_customer
    ON sales_ord.customer_tone_acceptance (tenant_id, customer_id);

CREATE TABLE IF NOT EXISTS sales_ord.quantity_proposal (
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
    requested_qty NUMERIC(15, 3) NOT NULL CHECK (requested_qty > 0),
    unit VARCHAR(20) NOT NULL,
    evaluation_status VARCHAR(30) NOT NULL CHECK (evaluation_status IN
        ('EXACT', 'OPTIONS', 'NO_ELIGIBLE_STOCK', 'UNKNOWN')),
    evidence_fingerprint VARCHAR(64) NOT NULL CHECK (evidence_fingerprint ~ '^[0-9a-f]{64}$'),
    result JSONB NOT NULL CHECK (jsonb_typeof(result) = 'object'),
    evaluated_by UUID NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_quantity_proposal_line FOREIGN KEY (tenant_id, sales_order_line_id)
        REFERENCES sales_ord.sales_order_line (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_quantity_proposal_line
    ON sales_ord.quantity_proposal (tenant_id, sales_order_line_id, evaluated_at DESC);

-- Quantity tolerance agreed with the customer (SOI A03) is per distribution line, like its colour
-- and price (Fatih, 2026-10-01): each line carries its own limits, who recorded them and when.
-- The customer accepts them with the sent order version; no per-line source is kept.
ALTER TABLE sales_ord.sales_order_line
    ADD COLUMN IF NOT EXISTS tolerance_up_pct NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS tolerance_down_pct NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS tolerance_recorded_by UUID,
    ADD COLUMN IF NOT EXISTS tolerance_recorded_at TIMESTAMPTZ;

ALTER TABLE sales_ord.sales_order_line
    ADD CONSTRAINT chk_sales_line_tolerance CHECK (
        (tolerance_up_pct IS NULL OR tolerance_up_pct BETWEEN 0 AND 100)
        AND (tolerance_down_pct IS NULL OR tolerance_down_pct BETWEEN 0 AND 100)
        AND ((tolerance_up_pct IS NULL AND tolerance_down_pct IS NULL)
            OR (tolerance_recorded_by IS NOT NULL AND tolerance_recorded_at IS NOT NULL))
    );

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'production.batch_finished_width_measurement',
        'production.stock_unit_cut',
        'production.lot_compatibility_confirmation',
        'sales_ord.customer_tone_acceptance',
        'sales_ord.quantity_proposal'] LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($p$CREATE POLICY rls_tenant_isolation ON %s FOR ALL
            USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
            WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID)$p$, t);
    END LOOP;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        -- Evidence and proposals are append-only facts.
        GRANT SELECT, INSERT ON production.batch_finished_width_measurement,
            sales_ord.customer_tone_acceptance, sales_ord.quantity_proposal TO fabric_app;
        REVOKE UPDATE, DELETE ON production.batch_finished_width_measurement,
            sales_ord.customer_tone_acceptance, sales_ord.quantity_proposal FROM fabric_app;
        -- A cut gains its verification once; a confirmation may be revoked once.
        GRANT SELECT, INSERT, UPDATE ON production.stock_unit_cut,
            production.lot_compatibility_confirmation TO fabric_app;
        REVOKE DELETE ON production.stock_unit_cut,
            production.lot_compatibility_confirmation FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, DELETE ON production.batch_finished_width_measurement,
            sales_ord.customer_tone_acceptance, sales_ord.quantity_proposal TO fabric_system;
        GRANT SELECT, INSERT, UPDATE, DELETE ON production.stock_unit_cut,
            production.lot_compatibility_confirmation TO fabric_system;
    END IF;
END $$;

COMMENT ON TABLE production.batch_finished_width_measurement IS
    'Measured finished width of a lot; the latest measurement is the lot''s width evidence (SOI IK-08).';
COMMENT ON TABLE production.stock_unit_cut IS
    'A cut taken from a piece; the remaining length counts only after verification (SOI A06).';
COMMENT ON TABLE production.lot_compatibility_confirmation IS
    'Technical confirmation that lots may ship together under the stated conditions (SOI A04).';
COMMENT ON TABLE sales_ord.customer_tone_acceptance IS
    'Customer acceptance of a concrete shade difference between named lots; tone only (SOI A04-b).';
COMMENT ON TABLE sales_ord.quantity_proposal IS
    'Whole-piece quantity options computed for a line; not a reservation (SOI K08, A05).';

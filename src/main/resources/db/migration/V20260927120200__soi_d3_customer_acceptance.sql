-- SOI-D3: the stock option a line takes and the customer's acceptance of it (K08, A01, A11), and
-- open lot-compatibility questions raised by a conditional acceptance (A04, IK-13).
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS sales_ord.quantity_acceptance (
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
    proposal_id UUID NOT NULL REFERENCES sales_ord.quantity_proposal (id),
    option_key VARCHAR(80) NOT NULL,
    option_kind VARCHAR(30) NOT NULL CHECK (option_kind IN
        ('EXACT', 'ABOVE', 'BELOW', 'REQUESTED_WITH_REMNANT')),
    compatibility VARCHAR(30) NOT NULL CHECK (compatibility IN
        ('SINGLE_LOT', 'CONFIRMED', 'TONE_ACCEPTED', 'PENDING_CONFIRMATION')),
    accepted_qty NUMERIC(15, 3) NOT NULL CHECK (accepted_qty > 0),
    canonical_qty NUMERIC(19, 6) NOT NULL CHECK (canonical_qty > 0),
    unit VARCHAR(20) NOT NULL,
    piece_ids JSONB NOT NULL CHECK (jsonb_typeof(piece_ids) = 'array'
        AND jsonb_array_length(piece_ids) >= 1),
    batch_ids JSONB NOT NULL CHECK (jsonb_typeof(batch_ids) = 'array'
        AND jsonb_array_length(batch_ids) >= 1),
    basis VARCHAR(30) NOT NULL CHECK (basis IN ('EXACT_MATCH', 'CUSTOMER_ACCEPTED')),
    conditional BOOLEAN NOT NULL,
    remnant_acknowledged BOOLEAN NOT NULL,
    remaining_need VARCHAR(30) CHECK (remaining_need IN ('REDUCED_BY_CUSTOMER', 'REMAINS_OPEN')),
    remaining_qty NUMERIC(15, 3) CHECK (remaining_qty IS NULL OR remaining_qty > 0),
    customer_contact VARCHAR(200),
    channel VARCHAR(20) CHECK (channel IN ('PHONE', 'EMAIL', 'MESSAGE', 'IN_PERSON')),
    accepted_at TIMESTAMPTZ,
    customer_statement_confirmed BOOLEAN NOT NULL,
    evidence_note TEXT,
    evidence_attachment_id UUID,
    terms_fingerprint VARCHAR(64) NOT NULL CHECK (terms_fingerprint ~ '^[0-9a-f]{64}$'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'WITHDRAWN')),
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    idempotency_key VARCHAR(100),
    CONSTRAINT fk_quantity_acceptance_line FOREIGN KEY (tenant_id, sales_order_line_id)
        REFERENCES sales_ord.sales_order_line (tenant_id, id),
    CONSTRAINT chk_quantity_acceptance_customer CHECK (
        basis = 'EXACT_MATCH'
        OR (customer_contact IS NOT NULL AND channel IS NOT NULL AND accepted_at IS NOT NULL
            AND customer_statement_confirmed)),
    CONSTRAINT chk_quantity_acceptance_remaining CHECK (
        (option_kind = 'BELOW') = (remaining_need IS NOT NULL)),
    CONSTRAINT chk_quantity_acceptance_closed CHECK ((status = 'ACTIVE') = (closed_at IS NULL))
);
-- One active stock choice per line; one command per client key.
CREATE UNIQUE INDEX IF NOT EXISTS uq_quantity_acceptance_active_line
    ON sales_ord.quantity_acceptance (tenant_id, sales_order_line_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX IF NOT EXISTS uq_quantity_acceptance_idempotency
    ON sales_ord.quantity_acceptance (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS production.lot_compatibility_request (
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
    batch_key VARCHAR(800) NOT NULL,
    product_id UUID NOT NULL,
    customer_id UUID,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    note TEXT,
    requested_by UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'CONFIRMED', 'DECLINED', 'WITHDRAWN')),
    resolved_by UUID,
    resolved_at TIMESTAMPTZ,
    resolution_note TEXT,
    confirmation_id UUID REFERENCES production.lot_compatibility_confirmation (id),
    CONSTRAINT chk_lot_compatibility_request_resolution CHECK (
        (status = 'OPEN') = (resolved_at IS NULL)),
    CONSTRAINT chk_lot_compatibility_request_declined CHECK (
        status <> 'DECLINED' OR resolution_note IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lot_compatibility_request_open
    ON production.lot_compatibility_request (tenant_id, source_id, batch_key) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_lot_compatibility_request_status
    ON production.lot_compatibility_request (tenant_id, status, requested_at);

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['sales_ord.quantity_acceptance', 'production.lot_compatibility_request'] LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($p$CREATE POLICY rls_tenant_isolation ON %s FOR ALL
            USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
            WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID)$p$, t);
    END LOOP;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        -- Status moves once (superseded/withdrawn, answered); rows are never deleted.
        GRANT SELECT, INSERT, UPDATE ON sales_ord.quantity_acceptance,
            production.lot_compatibility_request TO fabric_app;
        REVOKE DELETE ON sales_ord.quantity_acceptance,
            production.lot_compatibility_request FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.quantity_acceptance,
            production.lot_compatibility_request TO fabric_system;
    END IF;
END $$;

COMMENT ON TABLE sales_ord.quantity_acceptance IS
    'Stock option chosen for a line and the customer''s acceptance; never a reservation (SOI D3).';
COMMENT ON COLUMN sales_ord.quantity_acceptance.terms_fingerprint IS
    'Line terms covered: product, colour, width, unit, quantity, single-lot, price (SOI A11).';
COMMENT ON TABLE production.lot_compatibility_request IS
    'Open question whether lots may ship together; asking is never the answer (SOI A04).';

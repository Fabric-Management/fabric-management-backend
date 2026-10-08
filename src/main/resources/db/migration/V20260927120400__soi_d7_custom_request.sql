-- SOI-D7: custom customer requests (K14–K16, R16–R18, N01) and files received from the customer
-- (IK-12). The order-level partial-delivery preference (A10) was replaced by the line's
-- shipment_preference (LINE-PREFERENCES-1).
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS sales_ord.customer_product_request (
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
    sales_order_id UUID,
    origin_order_id UUID,
    description TEXT,
    reference_product_id UUID,
    requested_qty NUMERIC(15, 3) CHECK (requested_qty IS NULL OR requested_qty > 0),
    unit VARCHAR(20),
    requested_color_note VARCHAR(500),
    requested_width NUMERIC(8, 2) CHECK (requested_width IS NULL OR requested_width > 0),
    requested_width_unit VARCHAR(10) CHECK (requested_width_unit IN ('CM', 'IN')),
    requested_delivery_date DATE,
    sample_received_at TIMESTAMPTZ,
    sample_note TEXT,
    status VARCHAR(30) NOT NULL CHECK (status IN ('OPEN', 'NEEDS_INFO', 'NOT_FEASIBLE',
        'PROPOSAL_READY', 'SENT_TO_CUSTOMER', 'CUSTOMER_APPROVED', 'CUSTOMER_REJECTED',
        'RESOLVED', 'CLOSED')),
    current_revision_no INTEGER NOT NULL DEFAULT 0 CHECK (current_revision_no >= 0),
    resolved_line_id UUID,
    approved_line_terms VARCHAR(64),
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_customer_request_quantity CHECK ((requested_qty IS NULL) = (unit IS NULL)),
    CONSTRAINT chk_customer_request_width CHECK (
        (requested_width IS NULL) = (requested_width_unit IS NULL)),
    CONSTRAINT chk_customer_request_resolved CHECK (
        (status = 'RESOLVED') = (resolved_line_id IS NOT NULL)),
    CONSTRAINT fk_customer_request_line FOREIGN KEY (tenant_id, resolved_line_id)
        REFERENCES sales_ord.sales_order_line (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_customer_request_order
    ON sales_ord.customer_product_request (tenant_id, sales_order_id);
CREATE INDEX IF NOT EXISTS idx_customer_request_origin
    ON sales_ord.customer_product_request (tenant_id, origin_order_id)
    WHERE origin_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_customer_request_customer_status
    ON sales_ord.customer_product_request (tenant_id, customer_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_request_resolved_line
    ON sales_ord.customer_product_request (tenant_id, resolved_line_id)
    WHERE resolved_line_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS sales_ord.customer_request_evaluation (
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
    request_id UUID NOT NULL REFERENCES sales_ord.customer_product_request (id),
    outcome VARCHAR(30) NOT NULL CHECK (outcome IN
        ('MATCH_EXISTING', 'NEW_PRODUCT', 'EQUIVALENT', 'NEEDS_INFO', 'NOT_FEASIBLE')),
    note TEXT NOT NULL CHECK (length(trim(note)) > 0),
    evaluated_by UUID NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_customer_request_evaluation_request
    ON sales_ord.customer_request_evaluation (tenant_id, request_id, evaluated_at DESC);

CREATE TABLE IF NOT EXISTS sales_ord.customer_request_revision (
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
    request_id UUID NOT NULL REFERENCES sales_ord.customer_product_request (id),
    revision_no INTEGER NOT NULL CHECK (revision_no >= 1),
    solution VARCHAR(30) NOT NULL CHECK (solution IN ('MATCH_EXISTING', 'NEW_PRODUCT', 'EQUIVALENT')),
    product_id UUID NOT NULL,
    summary TEXT NOT NULL CHECK (length(trim(summary)) > 0),
    counter_sample_note TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('PROPOSED', 'SENT', 'APPROVED', 'REJECTED', 'SUPERSEDED')),
    proposed_by UUID NOT NULL,
    proposed_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    CONSTRAINT uq_customer_request_revision UNIQUE (tenant_id, request_id, revision_no)
);

CREATE TABLE IF NOT EXISTS sales_ord.customer_request_decision (
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
    request_id UUID NOT NULL REFERENCES sales_ord.customer_product_request (id),
    revision_id UUID NOT NULL REFERENCES sales_ord.customer_request_revision (id),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('APPROVED', 'REJECTED')),
    terms_fingerprint VARCHAR(64) NOT NULL CHECK (terms_fingerprint ~ '^[0-9a-f]{64}$'),
    customer_contact VARCHAR(200) NOT NULL CHECK (length(trim(customer_contact)) > 0),
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('PHONE', 'EMAIL', 'MESSAGE', 'IN_PERSON')),
    decided_at TIMESTAMPTZ NOT NULL,
    customer_statement_confirmed BOOLEAN NOT NULL CHECK (customer_statement_confirmed),
    note TEXT,
    evidence_attachment_id UUID,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_customer_request_decision_request
    ON sales_ord.customer_request_decision (tenant_id, request_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS sales_ord.intake_attachment (
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
    sales_order_id UUID,
    request_id UUID REFERENCES sales_ord.customer_product_request (id),
    kind VARCHAR(30) NOT NULL CHECK (kind IN
        ('SAMPLE_PHOTO', 'SPECIFICATION', 'CUSTOMER_REPLY', 'OTHER')),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL CHECK (content_type IN
        ('image/jpeg', 'image/png', 'image/webp', 'application/pdf', 'text/plain')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    sha256 VARCHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    content BYTEA NOT NULL,
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_intake_attachment_order
    ON sales_ord.intake_attachment (tenant_id, sales_order_id);
CREATE INDEX IF NOT EXISTS idx_intake_attachment_request
    ON sales_ord.intake_attachment (tenant_id, request_id);

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'sales_ord.customer_product_request',
        'sales_ord.customer_request_evaluation',
        'sales_ord.customer_request_revision',
        'sales_ord.customer_request_decision',
        'sales_ord.intake_attachment'] LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($p$CREATE POLICY rls_tenant_isolation ON %s FOR ALL
            USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
            WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID)$p$, t);
    END LOOP;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        -- Evaluations and decisions are append-only facts.
        GRANT SELECT, INSERT ON sales_ord.customer_request_evaluation,
            sales_ord.customer_request_decision TO fabric_app;
        REVOKE UPDATE, DELETE ON sales_ord.customer_request_evaluation,
            sales_ord.customer_request_decision FROM fabric_app;
        GRANT SELECT, INSERT, UPDATE ON sales_ord.customer_product_request,
            sales_ord.customer_request_revision, sales_ord.intake_attachment TO fabric_app;
        REVOKE DELETE ON sales_ord.customer_product_request, sales_ord.customer_request_revision,
            sales_ord.intake_attachment FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.customer_product_request,
            sales_ord.customer_request_evaluation, sales_ord.customer_request_revision,
            sales_ord.customer_request_decision, sales_ord.intake_attachment TO fabric_system;
    END IF;
END $$;

COMMENT ON TABLE sales_ord.customer_product_request IS
    'Customer request for a product that is not (yet) a catalogue item; quantity may be unknown (SOI N01).';
COMMENT ON TABLE sales_ord.customer_request_decision IS
    'Customer answer to a revision recorded by an authorised salesperson (SOI A11).';
COMMENT ON TABLE sales_ord.intake_attachment IS
    'File received from or about the customer; bytea until an object store exists (SOI IK-12).';

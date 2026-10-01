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

-- Delivery promises agreed with the buyer, append-only: the first promise is kept for ever and
-- every change records who asked for it, why, under which term and how the buyer agreed.
CREATE TABLE IF NOT EXISTS sales_ord.delivery_commitment (
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
    sequence_no INTEGER NOT NULL CHECK (sequence_no > 0),
    previous_commitment_id UUID,
    committed_on DATE NOT NULL,
    delivery_term VARCHAR(3) NOT NULL
        CHECK (delivery_term IN ('EXW', 'FCA', 'CPT', 'CIP', 'DAT', 'DAP', 'DPU', 'DDP',
                                 'FAS', 'FOB', 'CFR', 'CIF')),
    delivery_place VARCHAR(200) NOT NULL CHECK (length(trim(delivery_place)) > 0),
    incoterms_version VARCHAR(20) NOT NULL
        CHECK (incoterms_version IN ('INCOTERMS_2010', 'INCOTERMS_2020')),
    delivery_event VARCHAR(40) NOT NULL
        CHECK (delivery_event IN ('AVAILABLE_FOR_COLLECTION', 'HANDED_TO_CARRIER',
                                  'ALONGSIDE_VESSEL', 'ON_BOARD_VESSEL',
                                  'READY_FOR_UNLOADING_AT_DESTINATION',
                                  'UNLOADED_AT_DESTINATION')),
    origin VARCHAR(20) NOT NULL
        CHECK (origin IN ('INITIAL', 'BUYER_REQUEST', 'SELLER_REVISION')),
    reason VARCHAR(1000),
    customer_contact VARCHAR(200) NOT NULL CHECK (length(trim(customer_contact)) > 0),
    channel VARCHAR(20) NOT NULL
        CHECK (channel IN ('PHONE', 'EMAIL', 'MESSAGE', 'IN_PERSON', 'DOCUMENT')),
    agreed_at TIMESTAMPTZ NOT NULL,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_delivery_commitment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_delivery_commitment_sequence UNIQUE (tenant_id, sales_order_id, sequence_no),
    CONSTRAINT fk_delivery_commitment_order FOREIGN KEY (tenant_id, sales_order_id)
        REFERENCES sales_ord.sales_order (tenant_id, id),
    CONSTRAINT fk_delivery_commitment_previous FOREIGN KEY (tenant_id, previous_commitment_id)
        REFERENCES sales_ord.delivery_commitment (tenant_id, id),
    CONSTRAINT chk_delivery_commitment_chain CHECK (
        (sequence_no = 1 AND previous_commitment_id IS NULL AND origin = 'INITIAL')
        OR (sequence_no > 1 AND previous_commitment_id IS NOT NULL AND origin <> 'INITIAL'
            AND length(trim(reason)) > 0))
);

-- Planning's proposed date for the delivery term's event, append-only. A proposal is not a promise;
-- it keeps the term it was made under and a validity, after which the basis is checked again.
CREATE TABLE IF NOT EXISTS sales_ord.delivery_proposal (
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
    sequence_no INTEGER NOT NULL CHECK (sequence_no > 0),
    planning_round INTEGER NOT NULL CHECK (planning_round > 0),
    planning_evaluation INTEGER NOT NULL CHECK (planning_evaluation >= 0),
    proposed_on DATE NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    delivery_term VARCHAR(3) NOT NULL
        CHECK (delivery_term IN ('EXW', 'FCA', 'CPT', 'CIP', 'DAT', 'DAP', 'DPU', 'DDP',
                                 'FAS', 'FOB', 'CFR', 'CIF')),
    delivery_place VARCHAR(200) NOT NULL CHECK (length(trim(delivery_place)) > 0),
    incoterms_version VARCHAR(20) NOT NULL
        CHECK (incoterms_version IN ('INCOTERMS_2010', 'INCOTERMS_2020')),
    delivery_event VARCHAR(40) NOT NULL
        CHECK (delivery_event IN ('AVAILABLE_FOR_COLLECTION', 'HANDED_TO_CARRIER',
                                  'ALONGSIDE_VESSEL', 'ON_BOARD_VESSEL',
                                  'READY_FOR_UNLOADING_AT_DESTINATION',
                                  'UNLOADED_AT_DESTINATION')),
    note VARCHAR(1000),
    proposed_by UUID NOT NULL,
    proposed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_delivery_proposal_sequence UNIQUE (tenant_id, sales_order_id, sequence_no),
    CONSTRAINT fk_delivery_proposal_order FOREIGN KEY (tenant_id, sales_order_id)
        REFERENCES sales_ord.sales_order (tenant_id, id),
    CONSTRAINT chk_delivery_proposal_validity CHECK (valid_until > proposed_at)
);

-- Every move of an order between flow stages, append-only: who, when and why.
CREATE TABLE IF NOT EXISTS sales_ord.order_flow_event (
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
    from_stage VARCHAR(30) NOT NULL,
    to_stage VARCHAR(30) NOT NULL,
    reason VARCHAR(1000),
    actor_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_order_flow_event_order FOREIGN KEY (tenant_id, sales_order_id)
        REFERENCES sales_ord.sales_order (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_order_flow_event_order
    ON sales_ord.order_flow_event (tenant_id, sales_order_id, occurred_at DESC);

-- Who is responsible for a piece of order work (planning, ship readiness, arrival estimate):
-- the team it is routed to and, once claimed or assigned, the person. One row per order and kind.
CREATE TABLE IF NOT EXISTS sales_ord.order_work_assignment (
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
    work_kind VARCHAR(30) NOT NULL
        CHECK (work_kind IN ('PLANNING', 'SHIP_READINESS', 'ARRIVAL_ESTIMATE')),
    department_code VARCHAR(50) NOT NULL,
    routed_at TIMESTAMPTZ NOT NULL,
    assignee_id UUID,
    assigned_at TIMESTAMPTZ,
    CONSTRAINT fk_order_work_assignment_order FOREIGN KEY (tenant_id, sales_order_id)
        REFERENCES sales_ord.sales_order (tenant_id, id),
    CONSTRAINT chk_order_work_assignment_assignee CHECK ((assignee_id IS NULL) = (assigned_at IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_order_work_assignment_kind
    ON sales_ord.order_work_assignment (tenant_id, sales_order_id, work_kind);
CREATE INDEX IF NOT EXISTS idx_order_work_assignment_queue
    ON sales_ord.order_work_assignment (tenant_id, work_kind, department_code, assignee_id);

-- Every routing, claim, assignment and release of order work, append-only, with its reason.
CREATE TABLE IF NOT EXISTS sales_ord.order_work_assignment_event (
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
    work_kind VARCHAR(30) NOT NULL
        CHECK (work_kind IN ('PLANNING', 'SHIP_READINESS', 'ARRIVAL_ESTIMATE')),
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('ROUTED', 'CLAIMED', 'ASSIGNED', 'RELEASED')),
    department_code VARCHAR(50) NOT NULL,
    from_assignee_id UUID,
    to_assignee_id UUID,
    reason VARCHAR(1000),
    actor_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_order_work_assignment_event_order FOREIGN KEY (tenant_id, sales_order_id)
        REFERENCES sales_ord.sales_order (tenant_id, id),
    CONSTRAINT chk_order_work_assignment_event_reason CHECK (
        event_type NOT IN ('ASSIGNED', 'RELEASED') OR reason IS NOT NULL),
    CONSTRAINT chk_order_work_assignment_event_claim CHECK (
        event_type <> 'CLAIMED' OR (to_assignee_id IS NOT NULL AND to_assignee_id = actor_id))
);
CREATE INDEX IF NOT EXISTS idx_order_work_assignment_event_order
    ON sales_ord.order_work_assignment_event (tenant_id, sales_order_id, occurred_at DESC);

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['sales_ord.line_portion_readiness', 'sales_ord.order_arrival_estimate',
                             'sales_ord.delivery_commitment', 'sales_ord.delivery_proposal',
                             'sales_ord.order_flow_event', 'sales_ord.order_work_assignment',
                             'sales_ord.order_work_assignment_event'] LOOP
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', t);
        EXECUTE format($p$CREATE POLICY rls_tenant_isolation ON %s FOR ALL
            USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
            WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID)$p$, t);
    END LOOP;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        -- The commitment history is append-only for the application.
        GRANT SELECT, INSERT ON sales_ord.delivery_commitment, sales_ord.delivery_proposal,
            sales_ord.order_flow_event, sales_ord.order_work_assignment_event TO fabric_app;
        REVOKE UPDATE, DELETE ON sales_ord.delivery_commitment, sales_ord.delivery_proposal,
            sales_ord.order_flow_event, sales_ord.order_work_assignment_event FROM fabric_app;
        GRANT SELECT, INSERT, UPDATE ON sales_ord.line_portion_readiness,
            sales_ord.order_arrival_estimate, sales_ord.order_work_assignment TO fabric_app;
        REVOKE DELETE ON sales_ord.line_portion_readiness,
            sales_ord.order_arrival_estimate, sales_ord.order_work_assignment FROM fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON sales_ord.line_portion_readiness,
            sales_ord.order_arrival_estimate, sales_ord.order_work_assignment TO fabric_system;
        GRANT SELECT, INSERT, DELETE ON sales_ord.delivery_commitment,
            sales_ord.delivery_proposal, sales_ord.order_flow_event,
            sales_ord.order_work_assignment_event TO fabric_system;
    END IF;
END $$;

COMMENT ON TABLE sales_ord.line_portion_readiness IS
    'Confirmed ready-to-ship date of a cover portion; not a delivery date (SOI A07).';
COMMENT ON TABLE sales_ord.order_arrival_estimate IS
    'Expected arrival at the customer from a carrier or an authorised sourced record (SOI A07-b).';
COMMENT ON TABLE sales_ord.delivery_commitment IS
    'Append-only delivery promises agreed with the buyer; the first promise and every change are kept.';

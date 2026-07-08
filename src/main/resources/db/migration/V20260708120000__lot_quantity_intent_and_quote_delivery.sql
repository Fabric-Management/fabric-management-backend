CREATE TABLE IF NOT EXISTS production.batch_lot_quantity_intent (
    id             uuid PRIMARY KEY,
    uid            varchar(100) UNIQUE,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    created_by     uuid,
    updated_by     uuid,
    tenant_id      uuid NOT NULL,
    is_active      boolean NOT NULL DEFAULT true,
    deleted_at     timestamptz,
    version        bigint NOT NULL DEFAULT 0,

    quote_id       uuid NOT NULL,
    quote_number   varchar(50),
    quote_line_id  uuid NOT NULL,
    marketer_id    uuid,
    marketer_name  varchar(255),
    batch_id       uuid NOT NULL,
    quantity       numeric(15,3) NOT NULL,
    unit           varchar(20) NOT NULL,
    status         varchar(20) NOT NULL,
    expires_at     date,
    released_at    timestamptz,

    CONSTRAINT uq_batch_lot_intent_line_batch
        UNIQUE (tenant_id, quote_line_id, batch_id),
    CONSTRAINT chk_batch_lot_intent_status
        CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT chk_batch_lot_intent_quantity_positive
        CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_batch_lot_intent_tenant_batch_status
    ON production.batch_lot_quantity_intent (tenant_id, batch_id, status);

CREATE INDEX IF NOT EXISTS idx_batch_lot_intent_tenant_line_status
    ON production.batch_lot_quantity_intent (tenant_id, quote_line_id, status);

CREATE INDEX IF NOT EXISTS idx_batch_lot_intent_tenant_expiry_status
    ON production.batch_lot_quantity_intent (tenant_id, expires_at, status);

ALTER TABLE production.batch_lot_quantity_intent ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.batch_lot_quantity_intent FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.batch_lot_quantity_intent;
CREATE POLICY rls_tenant_isolation ON production.batch_lot_quantity_intent
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

DO $$
BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE production.batch_lot_quantity_intent TO fabric_app;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;

DO $$
BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE production.batch_lot_quantity_intent TO fabric_system;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;

ALTER TABLE sales.quote_line
    ADD COLUMN IF NOT EXISTS delivery_status varchar(30),
    ADD COLUMN IF NOT EXISTS delivery_date date,
    ADD COLUMN IF NOT EXISTS delivery_covered boolean;

ALTER TABLE sales.quote_line
    ADD CONSTRAINT chk_quote_line_delivery_status
    CHECK (
        delivery_status IS NULL
        OR delivery_status IN ('FROM_STOCK', 'TO_BE_CONFIRMED', 'FROM_PRODUCTION', 'STOCK_OVERRIDE')
    )
    NOT VALID;

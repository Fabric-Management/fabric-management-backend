CREATE TABLE IF NOT EXISTS production.stock_unit_soft_hold (
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

    quote_line_id  uuid NOT NULL,
    stock_unit_id  uuid NOT NULL,
    status         varchar(20) NOT NULL,
    released_at    timestamptz,

    CONSTRAINT uq_stock_unit_soft_hold_line_piece
        UNIQUE (tenant_id, quote_line_id, stock_unit_id),
    CONSTRAINT chk_stock_unit_soft_hold_status
        CHECK (status IN ('ACTIVE', 'RELEASED'))
);

CREATE INDEX IF NOT EXISTS idx_soft_hold_tenant_piece_status
    ON production.stock_unit_soft_hold (tenant_id, stock_unit_id, status);

CREATE INDEX IF NOT EXISTS idx_soft_hold_tenant_line_status
    ON production.stock_unit_soft_hold (tenant_id, quote_line_id, status);

ALTER TABLE production.stock_unit_soft_hold ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.stock_unit_soft_hold FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.stock_unit_soft_hold;
CREATE POLICY rls_tenant_isolation ON production.stock_unit_soft_hold
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

DO $$
BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE production.stock_unit_soft_hold TO fabric_app;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;

DO $$
BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE production.stock_unit_soft_hold TO fabric_system;
EXCEPTION WHEN undefined_object THEN
    NULL;
END $$;

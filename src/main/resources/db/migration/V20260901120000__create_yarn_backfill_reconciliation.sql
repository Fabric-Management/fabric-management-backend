CREATE TABLE IF NOT EXISTS production.prod_yarn_backfill_reconciliation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    product_id UUID NOT NULL,
    article_id UUID NOT NULL,
    reason VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    candidates JSONB NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_yarn_backfill_product FOREIGN KEY (product_id)
        REFERENCES production.prod_product (id) ON DELETE RESTRICT,
    CONSTRAINT fk_yarn_backfill_article FOREIGN KEY (article_id)
        REFERENCES production.prod_yarn_article (id) ON DELETE RESTRICT,
    CONSTRAINT ck_yarn_backfill_reason CHECK (reason IN ('AMBIGUOUS', 'OVERLENGTH')),
    CONSTRAINT ck_yarn_backfill_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_yarn_backfill_candidates_object CHECK (jsonb_typeof(candidates) = 'object'),
    CONSTRAINT ck_yarn_backfill_candidates_schema CHECK (
        candidates ->> 'schemaVersion' = '1'
        AND jsonb_typeof(candidates -> 'candidates') = 'array'
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_yarn_backfill_open_product
    ON production.prod_yarn_backfill_reconciliation (tenant_id, product_id)
    WHERE status = 'OPEN';

CREATE INDEX IF NOT EXISTS idx_yarn_backfill_tenant_status_created
    ON production.prod_yarn_backfill_reconciliation (tenant_id, status, created_at);

ALTER TABLE production.prod_yarn_backfill_reconciliation ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_yarn_backfill_reconciliation FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_tenant_isolation ON production.prod_yarn_backfill_reconciliation
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

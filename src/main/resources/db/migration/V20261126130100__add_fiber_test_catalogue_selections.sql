CREATE TABLE IF NOT EXISTS production.production_quality_fiber_test_laboratory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    trading_partner_id UUID NOT NULL REFERENCES common_company.common_trading_partner (id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fiber_test_laboratory_tenant_partner UNIQUE (tenant_id, trading_partner_id)
);

ALTER TABLE production.production_quality_fiber_test_laboratory ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_quality_fiber_test_laboratory FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation
    ON production.production_quality_fiber_test_laboratory;
CREATE POLICY rls_tenant_isolation ON production.production_quality_fiber_test_laboratory
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE production.production_quality_fiber_test_result
    ADD COLUMN IF NOT EXISTS laboratory_id UUID,
    ADD COLUMN IF NOT EXISTS test_standard_id UUID,
    ADD COLUMN IF NOT EXISTS test_standard_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS test_standard_revision VARCHAR(100);

DO $$
DECLARE
    result_table CONSTANT regclass := 'production.production_quality_fiber_test_result'::regclass;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = result_table AND conname = 'fk_fiber_test_laboratory'
    ) THEN
        ALTER TABLE production.production_quality_fiber_test_result
            ADD CONSTRAINT fk_fiber_test_laboratory
            FOREIGN KEY (tenant_id, laboratory_id)
            REFERENCES production.production_quality_fiber_test_laboratory
                (tenant_id, trading_partner_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = result_table AND conname = 'fk_fiber_test_standard'
    ) THEN
        ALTER TABLE production.production_quality_fiber_test_result
            ADD CONSTRAINT fk_fiber_test_standard
            FOREIGN KEY (tenant_id, test_standard_id)
            REFERENCES production.prod_fiber_test_standard (tenant_id, id);
    END IF;
END $$;

DO $$ BEGIN
    GRANT SELECT, INSERT, UPDATE ON production.production_quality_fiber_test_laboratory TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE ON production.production_quality_fiber_test_laboratory TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;

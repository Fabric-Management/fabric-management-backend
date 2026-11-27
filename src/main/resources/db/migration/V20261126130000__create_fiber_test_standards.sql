CREATE TABLE IF NOT EXISTS production.prod_fiber_test_standard (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    code VARCHAR(100) NOT NULL CHECK (btrim(code) <> ''),
    name VARCHAR(255) NOT NULL CHECK (btrim(name) <> ''),
    revision VARCHAR(100) NOT NULL DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fiber_test_standard_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fiber_test_standard_code_revision
    ON production.prod_fiber_test_standard (tenant_id, lower(code), lower(revision));

ALTER TABLE production.prod_fiber_test_standard ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_fiber_test_standard FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_fiber_test_standard;
CREATE POLICY rls_tenant_isolation ON production.prod_fiber_test_standard
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

DO $$ BEGIN
    GRANT SELECT, INSERT, UPDATE ON production.prod_fiber_test_standard TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE ON production.prod_fiber_test_standard TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;

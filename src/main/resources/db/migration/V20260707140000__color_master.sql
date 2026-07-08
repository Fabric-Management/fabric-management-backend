CREATE TABLE IF NOT EXISTS production.color (
    id UUID PRIMARY KEY,
    uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    tenant_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    color_hex VARCHAR(7),

    CONSTRAINT uq_color_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_color_hex_format
        CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE INDEX IF NOT EXISTS idx_color_tenant_active
    ON production.color (tenant_id, is_active);

CREATE INDEX IF NOT EXISTS idx_color_tenant_code
    ON production.color (tenant_id, code);

ALTER TABLE production.color ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.color FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.color;
CREATE POLICY rls_tenant_isolation ON production.color
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE production.color TO fabric_app;
  END IF;

  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE production.color TO fabric_system;
  END IF;
END $$;

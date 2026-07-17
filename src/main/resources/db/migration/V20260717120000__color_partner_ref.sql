ALTER TABLE production.color
    DROP CONSTRAINT IF EXISTS uq_color_tenant_id;

ALTER TABLE production.color
    ADD CONSTRAINT uq_color_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS production.color_partner_ref (
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

    color_id UUID NOT NULL,
    partner_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    delta_e_tolerance NUMERIC(4,2),

    CONSTRAINT chk_color_partner_ref_role
        CHECK (role IN ('CUSTOMER', 'SUPPLIER')),
    CONSTRAINT chk_color_partner_ref_tolerance
        CHECK (delta_e_tolerance IS NULL OR delta_e_tolerance > 0),
    CONSTRAINT uq_color_partner_ref_relationship
        UNIQUE (tenant_id, color_id, partner_id, role),
    CONSTRAINT uq_color_partner_ref_child_target
        UNIQUE (tenant_id, id, partner_id, role),
    CONSTRAINT fk_color_partner_ref_color
        FOREIGN KEY (tenant_id, color_id)
        REFERENCES production.color (tenant_id, id)
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS production.color_partner_code (
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

    color_partner_ref_id UUID NOT NULL,
    partner_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    external_code VARCHAR(50) NOT NULL,
    external_code_key VARCHAR(50) NOT NULL,
    external_name VARCHAR(255),
    is_primary BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT fk_color_partner_code_ref
        FOREIGN KEY (tenant_id, color_partner_ref_id, partner_id, role)
        REFERENCES production.color_partner_ref (tenant_id, id, partner_id, role)
        ON DELETE RESTRICT
);

ALTER TABLE production.color_partner_code
    DROP CONSTRAINT IF EXISTS chk_color_partner_code_key,
    DROP CONSTRAINT IF EXISTS chk_color_partner_code_display,
    DROP CONSTRAINT IF EXISTS chk_color_partner_code_key_not_blank,
    DROP CONSTRAINT IF EXISTS chk_color_partner_code_name,
    DROP CONSTRAINT IF EXISTS chk_color_partner_code_role;

ALTER TABLE production.color_partner_code
    ADD CONSTRAINT chk_color_partner_code_key
        CHECK (external_code_key = upper(btrim(external_code))),
    ADD CONSTRAINT chk_color_partner_code_display
        CHECK (external_code = btrim(external_code) AND external_code <> ''),
    ADD CONSTRAINT chk_color_partner_code_key_not_blank
        CHECK (external_code_key <> ''),
    ADD CONSTRAINT chk_color_partner_code_name
        CHECK (
            external_name IS NULL
            OR (external_name = btrim(external_name) AND external_name <> '')
        ),
    ADD CONSTRAINT chk_color_partner_code_role
        CHECK (role IN ('CUSTOMER', 'SUPPLIER'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_color_partner_code_active_primary
    ON production.color_partner_code (tenant_id, color_partner_ref_id)
    WHERE is_active AND is_primary;

CREATE UNIQUE INDEX IF NOT EXISTS uq_color_partner_code_active_lookup
    ON production.color_partner_code (tenant_id, partner_id, role, external_code_key)
    WHERE is_active;

CREATE INDEX IF NOT EXISTS idx_color_partner_ref_color
    ON production.color_partner_ref (tenant_id, color_id);

CREATE INDEX IF NOT EXISTS idx_color_partner_code_ref
    ON production.color_partner_code (tenant_id, color_partner_ref_id);

ALTER TABLE production.color_partner_ref ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.color_partner_ref FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.color_partner_ref;
CREATE POLICY rls_tenant_isolation ON production.color_partner_ref
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE production.color_partner_code ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.color_partner_code FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.color_partner_code;
CREATE POLICY rls_tenant_isolation ON production.color_partner_code
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE
      ON production.color_partner_ref, production.color_partner_code TO fabric_app;
  END IF;

  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE
      ON production.color_partner_ref, production.color_partner_code TO fabric_system;
  END IF;
END $$;

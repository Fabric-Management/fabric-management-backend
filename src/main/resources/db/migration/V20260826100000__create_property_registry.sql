CREATE TABLE IF NOT EXISTS production.prod_property_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    property_key VARCHAR(100) NOT NULL,
    canonical_field_name VARCHAR(100) NOT NULL,
    semantic_role_default VARCHAR(40) NOT NULL,
    dimension VARCHAR(100) NOT NULL,
    data_type VARCHAR(20) NOT NULL,
    unit_family VARCHAR(40) NOT NULL,
    canonical_unit_code VARCHAR(40),
    allowed_unit_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    conversion_policy VARCHAR(100),
    rounding_policy VARCHAR(100),
    nominal_source VARCHAR(100),
    tolerance_source VARCHAR(100),
    description TEXT NOT NULL,
    system_defined BOOLEAN NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_property_definition_tenant_key UNIQUE (tenant_id, property_key),
    CONSTRAINT ck_property_definition_key_namespace CHECK (
        (system_defined AND property_key NOT LIKE 'CUSTOM\_%')
        OR (NOT system_defined AND property_key LIKE 'CUSTOM\_%')
    ),
    CONSTRAINT ck_property_definition_allowed_units_array CHECK (
        jsonb_typeof(allowed_unit_codes) = 'array'
    )
);

CREATE INDEX IF NOT EXISTS idx_property_definition_tenant_family
    ON production.prod_property_definition (tenant_id, unit_family);

ALTER TABLE production.prod_property_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_property_definition FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_property_definition;
CREATE POLICY rls_tenant_isolation ON production.prod_property_definition
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

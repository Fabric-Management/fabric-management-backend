CREATE TABLE IF NOT EXISTS production.prod_yarn_spinning_system (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INTEGER,
    technology_family VARCHAR(30) NOT NULL,
    system_defined BOOLEAN NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_yarn_spinning_system_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_yarn_spinning_system_code CHECK (
        code ~ '^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$'
    ),
    CONSTRAINT ck_yarn_spinning_system_family CHECK (
        technology_family IN ('RING', 'ROTOR', 'AIR_JET', 'FRICTION')
    )
);

CREATE INDEX IF NOT EXISTS idx_yarn_spinning_system_tenant_active
    ON production.prod_yarn_spinning_system (tenant_id, is_active);

CREATE TABLE IF NOT EXISTS production.prod_yarn_end_use (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INTEGER,
    system_defined BOOLEAN NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_yarn_end_use_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_yarn_end_use_code CHECK (
        code ~ '^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$'
    )
);

CREATE INDEX IF NOT EXISTS idx_yarn_end_use_tenant_active
    ON production.prod_yarn_end_use (tenant_id, is_active);

CREATE TABLE IF NOT EXISTS production.prod_yarn_test_method (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INTEGER,
    standard_ref VARCHAR(100),
    instrument VARCHAR(100),
    applicable_property_key VARCHAR(100),
    system_defined BOOLEAN NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_yarn_test_method_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_yarn_test_method_code CHECK (
        code ~ '^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$'
    ),
    CONSTRAINT ck_yarn_test_method_instrument_standard CHECK (
        instrument IS NULL OR standard_ref IS NOT NULL
    ),
    CONSTRAINT fk_yarn_test_method_property_definition
        FOREIGN KEY (tenant_id, applicable_property_key)
        REFERENCES production.prod_property_definition (tenant_id, property_key)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_yarn_test_method_tenant_active
    ON production.prod_yarn_test_method (tenant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_yarn_test_method_tenant_property
    ON production.prod_yarn_test_method (tenant_id, applicable_property_key);

ALTER TABLE production.prod_yarn_spinning_system ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_yarn_spinning_system FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_yarn_spinning_system;
CREATE POLICY rls_tenant_isolation ON production.prod_yarn_spinning_system
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE production.prod_yarn_end_use ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_yarn_end_use FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_yarn_end_use;
CREATE POLICY rls_tenant_isolation ON production.prod_yarn_end_use
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE production.prod_yarn_test_method ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_yarn_test_method FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_yarn_test_method;
CREATE POLICY rls_tenant_isolation ON production.prod_yarn_test_method
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

DO $$
DECLARE
    unknown_codes TEXT;
BEGIN
    IF to_regclass('production.prod_yarn_category') IS NOT NULL THEN
        SELECT string_agg(DISTINCT category_code, ', ' ORDER BY category_code)
          INTO unknown_codes
          FROM production.prod_yarn_category
         WHERE category_code NOT IN (
             'SEWING', 'KNITTING', 'WEAVING', 'EMBROIDERY', 'SPECIALTY'
         );

        IF unknown_codes IS NOT NULL THEN
            RAISE EXCEPTION
                'YARN-1A refuses to drop prod_yarn_category; unknown category codes: %',
                unknown_codes;
        END IF;
    END IF;
END
$$;

DO $$
DECLARE
    unknown_codes TEXT;
BEGIN
    IF to_regclass('production.prod_yarn_attribute') IS NOT NULL THEN
        SELECT string_agg(DISTINCT attribute_code, ', ' ORDER BY attribute_code)
          INTO unknown_codes
          FROM production.prod_yarn_attribute
         WHERE attribute_code NOT IN (
             'COUNT', 'TWIST', 'STRENGTH', 'ELONGATION', 'HAIRINESS', 'EVENNESS'
         );

        IF unknown_codes IS NOT NULL THEN
            RAISE EXCEPTION
                'YARN-1A refuses to drop prod_yarn_attribute; unknown attribute codes: %',
                unknown_codes;
        END IF;
    END IF;
END
$$;

INSERT INTO production.prod_yarn_end_use (
    id, tenant_id, uid, code, name, description, display_order, system_defined,
    is_active, deleted_at, created_at, updated_at, version
)
SELECT
    gen_random_uuid(), legacy.tenant_id, gen_random_uuid()::VARCHAR,
    legacy.category_code, legacy.category_name, legacy.description, legacy.display_order, TRUE,
    legacy.is_active, legacy.deleted_at, legacy.created_at, legacy.updated_at, legacy.version
FROM production.prod_yarn_category legacy
WHERE legacy.category_code IN ('SEWING', 'KNITTING', 'WEAVING', 'EMBROIDERY')
ON CONFLICT (tenant_id, code) DO NOTHING;

DROP TABLE production.prod_yarn_attribute;
DROP TABLE production.prod_yarn_category;

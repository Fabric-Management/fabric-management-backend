CREATE TABLE IF NOT EXISTS production.prod_yarn_article (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    product_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    article_spec_version INTEGER NOT NULL DEFAULT 1,
    original_count_system VARCHAR(20),
    original_count_value NUMERIC(18,6),
    count_basis VARCHAR(20),
    structure_type VARCHAR(30),
    fold_count INTEGER,
    filament_count INTEGER,
    twist_contraction_percent NUMERIC(5,2),
    resultant_linear_density_tex NUMERIC(18,2),
    canonical_designation VARCHAR(160),
    source_designation VARCHAR(255),
    material_form VARCHAR(30),
    spinning_technology_family VARCHAR(30),
    spinning_system_id UUID,
    filament_form VARCHAR(30),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    canonical_key CHAR(64),
    canonical_key_algorithm_version SMALLINT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_yarn_article_product UNIQUE (product_id),
    CONSTRAINT fk_yarn_article_product FOREIGN KEY (product_id)
        REFERENCES production.prod_product (id) ON DELETE RESTRICT,
    CONSTRAINT fk_yarn_article_spinning_system FOREIGN KEY (spinning_system_id)
        REFERENCES production.prod_yarn_spinning_system (id) ON DELETE RESTRICT,
    CONSTRAINT ck_yarn_article_status CHECK (status IN ('DRAFT', 'ACTIVE', 'OBSOLETE')),
    CONSTRAINT ck_yarn_article_count_system CHECK (
        original_count_system IS NULL OR original_count_system IN ('TEX', 'DTEX', 'DENIER', 'NE', 'NM')
    ),
    CONSTRAINT ck_yarn_article_count_basis CHECK (
        count_basis IS NULL OR count_basis IN ('COMPONENT', 'RESULTANT')
    ),
    CONSTRAINT ck_yarn_article_structure_type CHECK (
        structure_type IS NULL OR structure_type IN ('SINGLE', 'PLIED', 'CABLED', 'MULTIPLE_WOUND')
    ),
    CONSTRAINT ck_yarn_article_material_form CHECK (
        material_form IS NULL OR material_form IN ('STAPLE_SPUN', 'CONTINUOUS_FILAMENT')
    ),
    CONSTRAINT ck_yarn_article_spinning_family CHECK (
        spinning_technology_family IS NULL OR spinning_technology_family IN ('RING', 'ROTOR', 'AIR_JET', 'FRICTION')
    ),
    CONSTRAINT ck_yarn_article_filament_form CHECK (
        filament_form IS NULL OR filament_form IN ('FLAT', 'TEXTURED')
    ),
    CONSTRAINT ck_yarn_article_contraction CHECK (
        twist_contraction_percent IS NULL
        OR (twist_contraction_percent >= 0 AND twist_contraction_percent < 100)
    ),
    CONSTRAINT ck_yarn_article_algorithm_version CHECK (canonical_key_algorithm_version = 1)
);

CREATE INDEX IF NOT EXISTS idx_yarn_article_tenant_canonical_key
    ON production.prod_yarn_article (tenant_id, canonical_key)
    WHERE canonical_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS production.prod_yarn_article_composition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    article_id UUID NOT NULL,
    fiber_id UUID NOT NULL,
    fiber_iso_code VARCHAR(10) NOT NULL,
    fiber_name VARCHAR(255) NOT NULL,
    material_source VARCHAR(20),
    percentage NUMERIC(5,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_yarn_article_composition_fiber
        UNIQUE (tenant_id, article_id, fiber_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_yarn_article_composition_article FOREIGN KEY (article_id)
        REFERENCES production.prod_yarn_article (id) ON DELETE RESTRICT,
    CONSTRAINT fk_yarn_article_composition_fiber FOREIGN KEY (fiber_id)
        REFERENCES production.prod_fiber (id) ON DELETE RESTRICT,
    CONSTRAINT ck_yarn_article_composition_percentage CHECK (percentage > 0),
    CONSTRAINT ck_yarn_article_composition_source CHECK (
        material_source IS NULL OR material_source IN ('VIRGIN', 'RECYCLED')
    )
);

CREATE TABLE IF NOT EXISTS production.prod_yarn_article_structure_component (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    article_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL,
    component_index INTEGER NOT NULL,
    layer_role VARCHAR(20),
    component_count_system VARCHAR(20),
    component_count_value NUMERIC(18,6),
    component_linear_density_tex NUMERIC(18,2),
    fiber_id UUID,
    fiber_iso_code VARCHAR(10),
    fiber_name VARCHAR(255),
    material_source VARCHAR(20),
    label VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_yarn_article_component_index
        UNIQUE (tenant_id, article_id, kind, component_index) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_yarn_article_component_article FOREIGN KEY (article_id)
        REFERENCES production.prod_yarn_article (id) ON DELETE RESTRICT,
    CONSTRAINT fk_yarn_article_component_fiber FOREIGN KEY (fiber_id)
        REFERENCES production.prod_fiber (id) ON DELETE RESTRICT,
    CONSTRAINT ck_yarn_article_component_kind CHECK (kind IN ('STRAND', 'LAYER')),
    CONSTRAINT ck_yarn_article_component_index CHECK (component_index >= 1),
    CONSTRAINT ck_yarn_article_layer_role CHECK (
        (kind = 'LAYER' AND layer_role IN ('CORE', 'SHEATH'))
        OR (kind = 'STRAND' AND layer_role IS NULL)
    ),
    CONSTRAINT ck_yarn_article_layer_count_free CHECK (
        kind <> 'LAYER'
        OR (component_count_system IS NULL
            AND component_count_value IS NULL
            AND component_linear_density_tex IS NULL)
    ),
    CONSTRAINT ck_yarn_article_component_count_pair CHECK (
        (component_count_system IS NULL) = (component_count_value IS NULL)
    ),
    CONSTRAINT ck_yarn_article_component_count_system CHECK (
        component_count_system IS NULL OR component_count_system IN ('TEX', 'DTEX', 'DENIER', 'NE', 'NM')
    ),
    CONSTRAINT ck_yarn_article_component_source CHECK (
        material_source IS NULL OR material_source IN ('VIRGIN', 'RECYCLED')
    )
);

CREATE TABLE IF NOT EXISTS production.prod_yarn_article_twist_stage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    article_id UUID NOT NULL,
    stage_type VARCHAR(20) NOT NULL,
    sequence INTEGER NOT NULL,
    direction VARCHAR(10) NOT NULL,
    turns_per_meter NUMERIC(18,2) NOT NULL,
    component_id UUID,
    test_method_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_yarn_article_twist_sequence
        UNIQUE (tenant_id, article_id, sequence) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_yarn_article_twist_article FOREIGN KEY (article_id)
        REFERENCES production.prod_yarn_article (id) ON DELETE RESTRICT,
    CONSTRAINT fk_yarn_article_twist_component FOREIGN KEY (component_id)
        REFERENCES production.prod_yarn_article_structure_component (id) ON DELETE RESTRICT,
    CONSTRAINT fk_yarn_article_twist_test_method FOREIGN KEY (test_method_id)
        REFERENCES production.prod_yarn_test_method (id) ON DELETE RESTRICT,
    CONSTRAINT ck_yarn_article_twist_stage_type CHECK (stage_type IN ('SINGLE', 'PLY', 'CABLE')),
    CONSTRAINT ck_yarn_article_twist_sequence CHECK (sequence >= 1),
    CONSTRAINT ck_yarn_article_twist_direction CHECK (direction IN ('S', 'Z', 'NONE')),
    CONSTRAINT ck_yarn_article_twist_tpm CHECK (turns_per_meter >= 0)
);

CREATE TABLE IF NOT EXISTS production.prod_yarn_article_construction_feature (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    article_id UUID NOT NULL,
    feature VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_yarn_article_feature
        UNIQUE (tenant_id, article_id, feature) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_yarn_article_feature_article FOREIGN KEY (article_id)
        REFERENCES production.prod_yarn_article (id) ON DELETE RESTRICT,
    CONSTRAINT ck_yarn_article_feature CHECK (feature IN ('CORE_SPUN', 'SIRO', 'SLUB', 'COVERED'))
);

CREATE TABLE IF NOT EXISTS production.prod_yarn_article_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    article_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    spec_version_from INTEGER NOT NULL,
    spec_version_to INTEGER NOT NULL,
    payload_schema_version SMALLINT NOT NULL DEFAULT 1,
    spec_after JSONB NOT NULL,
    changed_summary JSONB NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_yarn_article_audit_article FOREIGN KEY (article_id)
        REFERENCES production.prod_yarn_article (id) ON DELETE RESTRICT,
    CONSTRAINT ck_yarn_article_audit_event CHECK (
        event_type IN ('CREATED', 'SPEC_UPDATED', 'METADATA_UPDATED', 'ACTIVATED', 'OBSOLETED')
    ),
    CONSTRAINT ck_yarn_article_audit_payload_version CHECK (payload_schema_version = 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_yarn_article_audit_spec_version
    ON production.prod_yarn_article_audit (tenant_id, article_id, spec_version_to)
    WHERE event_type IN ('CREATED', 'SPEC_UPDATED');

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'prod_yarn_article',
        'prod_yarn_article_composition',
        'prod_yarn_article_structure_component',
        'prod_yarn_article_twist_stage',
        'prod_yarn_article_construction_feature',
        'prod_yarn_article_audit'
    ]
    LOOP
        EXECUTE format('ALTER TABLE production.%I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE production.%I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format('DROP POLICY IF EXISTS rls_tenant_isolation ON production.%I', table_name);
        EXECUTE format(
            'CREATE POLICY rls_tenant_isolation ON production.%I FOR ALL USING '
            || '(tenant_id = current_setting(''app.current_tenant'', true)::uuid) WITH CHECK '
            || '(tenant_id = current_setting(''app.current_tenant'', true)::uuid)',
            table_name
        );
    END LOOP;
END $$;

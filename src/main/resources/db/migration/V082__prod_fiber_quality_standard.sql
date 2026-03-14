-- =====================================================
-- V082: FiberQualityStandard (ISO Code based quality standards)
-- =====================================================
-- Replaces prod_fiber_specification (fiber_id based) with
-- prod_fiber_quality_standard (iso_code_id based).
--
-- Design: Quality standards are defined per ISO code (e.g. CO, PES),
-- not per fiber catalog entry. Enables shared standards across
-- fibers of the same type.
--
-- Unique: (tenant_id, iso_code_id, standard_name)
-- =====================================================

-- Drop old table
DROP TABLE IF EXISTS production.prod_fiber_specification;

-- Create new table
CREATE TABLE IF NOT EXISTS production.prod_fiber_quality_standard (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    uid                     VARCHAR(100)    NOT NULL,

    iso_code_id             UUID            NOT NULL,
    standard_name           VARCHAR(100)   NOT NULL,
    is_default              BOOLEAN         NOT NULL DEFAULT false,

    -- Fineness (micronaire / dtex)
    fineness_min            DOUBLE PRECISION,
    fineness_target         DOUBLE PRECISION,
    fineness_max            DOUBLE PRECISION,

    -- Staple length (mm)
    length_mm_min           DOUBLE PRECISION,
    length_mm_target        DOUBLE PRECISION,
    length_mm_max           DOUBLE PRECISION,

    -- Tenacity / Strength (cN/dtex)
    strength_cnd_tex_min    DOUBLE PRECISION,
    strength_cnd_tex_target  DOUBLE PRECISION,
    strength_cnd_tex_max    DOUBLE PRECISION,

    -- Elongation at break (%)
    elongation_pct_min     DOUBLE PRECISION,
    elongation_pct_target  DOUBLE PRECISION,
    elongation_pct_max     DOUBLE PRECISION,

    -- Moisture / humidity (%)
    moisture_pct_min       DOUBLE PRECISION,
    moisture_pct_target    DOUBLE PRECISION,
    moisture_pct_max       DOUBLE PRECISION,

    -- Trash & neps content (%)
    trash_content_pct_min  DOUBLE PRECISION,
    trash_content_pct_target DOUBLE PRECISION,
    trash_content_pct_max  DOUBLE PRECISION,

    is_active               BOOLEAN         NOT NULL DEFAULT true,
    deleted_at              TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_fiber_quality_standard
        PRIMARY KEY (id),
    CONSTRAINT fk_fiber_quality_standard_iso_code
        FOREIGN KEY (iso_code_id)
        REFERENCES production.prod_fiber_iso_code(id),
    CONSTRAINT uq_fiber_quality_standard_tenant_uid
        UNIQUE (tenant_id, uid),
    CONSTRAINT uq_fiber_quality_standard_tenant_iso_name
        UNIQUE (tenant_id, iso_code_id, standard_name),

    CONSTRAINT ck_fqs_fineness_range
        CHECK (fineness_min IS NULL OR fineness_max IS NULL OR fineness_min <= fineness_max),
    CONSTRAINT ck_fqs_fineness_target
        CHECK (fineness_target IS NULL
            OR (fineness_min IS NULL OR fineness_target >= fineness_min)
           AND (fineness_max IS NULL OR fineness_target <= fineness_max)),

    CONSTRAINT ck_fqs_length_range
        CHECK (length_mm_min IS NULL OR length_mm_max IS NULL OR length_mm_min <= length_mm_max),
    CONSTRAINT ck_fqs_length_target
        CHECK (length_mm_target IS NULL
            OR (length_mm_min IS NULL OR length_mm_target >= length_mm_min)
           AND (length_mm_max IS NULL OR length_mm_target <= length_mm_max)),

    CONSTRAINT ck_fqs_strength_range
        CHECK (strength_cnd_tex_min IS NULL OR strength_cnd_tex_max IS NULL
            OR strength_cnd_tex_min <= strength_cnd_tex_max),
    CONSTRAINT ck_fqs_strength_target
        CHECK (strength_cnd_tex_target IS NULL
            OR (strength_cnd_tex_min IS NULL OR strength_cnd_tex_target >= strength_cnd_tex_min)
           AND (strength_cnd_tex_max IS NULL OR strength_cnd_tex_target <= strength_cnd_tex_max)),

    CONSTRAINT ck_fqs_elongation_range
        CHECK (elongation_pct_min IS NULL OR elongation_pct_max IS NULL
            OR elongation_pct_min <= elongation_pct_max),
    CONSTRAINT ck_fqs_elongation_target
        CHECK (elongation_pct_target IS NULL
            OR (elongation_pct_min IS NULL OR elongation_pct_target >= elongation_pct_min)
           AND (elongation_pct_max IS NULL OR elongation_pct_target <= elongation_pct_max)),

    CONSTRAINT ck_fqs_moisture_range
        CHECK (moisture_pct_min IS NULL OR moisture_pct_max IS NULL
            OR moisture_pct_min <= moisture_pct_max),
    CONSTRAINT ck_fqs_moisture_target
        CHECK (moisture_pct_target IS NULL
            OR (moisture_pct_min IS NULL OR moisture_pct_target >= moisture_pct_min)
           AND (moisture_pct_max IS NULL OR moisture_pct_target <= moisture_pct_max)),

    CONSTRAINT ck_fqs_trash_range
        CHECK (trash_content_pct_min IS NULL OR trash_content_pct_max IS NULL
            OR trash_content_pct_min <= trash_content_pct_max),
    CONSTRAINT ck_fqs_trash_target
        CHECK (trash_content_pct_target IS NULL
            OR (trash_content_pct_min IS NULL OR trash_content_pct_target >= trash_content_pct_min)
           AND (trash_content_pct_max IS NULL OR trash_content_pct_target <= trash_content_pct_max))
);

CREATE INDEX IF NOT EXISTS idx_fiber_quality_standard_tenant
    ON production.prod_fiber_quality_standard(tenant_id);
CREATE INDEX IF NOT EXISTS idx_fiber_quality_standard_iso_code
    ON production.prod_fiber_quality_standard(iso_code_id);
CREATE INDEX IF NOT EXISTS idx_fiber_quality_standard_default
    ON production.prod_fiber_quality_standard(tenant_id, iso_code_id, is_default)
    WHERE is_default = true;

COMMENT ON TABLE production.prod_fiber_quality_standard IS
    'Quality standards (LSL/Target/USL) per ISO code. Each ISO code can have multiple named profiles (Standard, Premium). Replaces fiber_id-based FiberSpecification.';
COMMENT ON COLUMN production.prod_fiber_quality_standard.standard_name IS
    'Profile name: e.g. "Standard", "Premium", "Export"';
COMMENT ON COLUMN production.prod_fiber_quality_standard.is_default IS
    'If true, this is the default standard used for automated QC comparison. Only one default per iso_code per tenant.';

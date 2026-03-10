-- =====================================================
-- V20260306140000: Fiber Specification (Hedef Spesifikasyonlar)
-- =====================================================
-- Stores target quality parameters with min/target/max tolerance
-- bands for each fiber. Enables automated variance calculation
-- when compared to FiberTestResult actuals.
--
-- Design: Separate entity (not columns on prod_fiber) because:
--   1. Each fiber can have multiple spec profiles
--      ("Standard", "Premium Export", "Customer X")
--   2. Min/Target/Max triplets per parameter (6 params × 3 = 18 values)
--   3. Specs can be versioned independently from the fiber catalog
-- =====================================================

CREATE TABLE IF NOT EXISTS production.prod_fiber_specification (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    uid                     VARCHAR(100)    NOT NULL,

    fiber_id                UUID            NOT NULL,
    spec_name               VARCHAR(100)    NOT NULL,
    is_default              BOOLEAN         NOT NULL DEFAULT false,
    test_standard           VARCHAR(100),

    -- Fineness (micronaire / dtex)
    fineness_min            DOUBLE PRECISION,
    fineness_target         DOUBLE PRECISION,
    fineness_max            DOUBLE PRECISION,

    -- Staple length (mm)
    length_min              DOUBLE PRECISION,
    length_target           DOUBLE PRECISION,
    length_max              DOUBLE PRECISION,

    -- Tenacity / Strength (cN/dtex)
    strength_min            DOUBLE PRECISION,
    strength_target         DOUBLE PRECISION,
    strength_max            DOUBLE PRECISION,

    -- Elongation at break (%)
    elongation_min          DOUBLE PRECISION,
    elongation_target       DOUBLE PRECISION,
    elongation_max          DOUBLE PRECISION,

    -- Moisture / humidity (%)
    moisture_min            DOUBLE PRECISION,
    moisture_target         DOUBLE PRECISION,
    moisture_max            DOUBLE PRECISION,

    -- Trash & neps content (%)
    trash_content_min       DOUBLE PRECISION,
    trash_content_target    DOUBLE PRECISION,
    trash_content_max       DOUBLE PRECISION,

    remarks                 TEXT,

    is_active               BOOLEAN         NOT NULL DEFAULT true,
    deleted_at              TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_fiber_specification
        PRIMARY KEY (id),
    CONSTRAINT fk_fiber_spec_fiber
        FOREIGN KEY (fiber_id)
        REFERENCES production.prod_fiber(id),
    CONSTRAINT uq_fiber_spec_tenant_uid
        UNIQUE (tenant_id, uid),
    CONSTRAINT uq_fiber_spec_name
        UNIQUE (tenant_id, fiber_id, spec_name),

    -- Min ≤ Target ≤ Max for each parameter (when all three provided)
    CONSTRAINT ck_spec_fineness_range
        CHECK (fineness_min IS NULL OR fineness_max IS NULL
            OR fineness_min <= fineness_max),
    CONSTRAINT ck_spec_fineness_target
        CHECK (fineness_target IS NULL
            OR (fineness_min IS NULL OR fineness_target >= fineness_min)
           AND (fineness_max IS NULL OR fineness_target <= fineness_max)),

    CONSTRAINT ck_spec_length_range
        CHECK (length_min IS NULL OR length_max IS NULL
            OR length_min <= length_max),
    CONSTRAINT ck_spec_length_target
        CHECK (length_target IS NULL
            OR (length_min IS NULL OR length_target >= length_min)
           AND (length_max IS NULL OR length_target <= length_max)),

    CONSTRAINT ck_spec_strength_range
        CHECK (strength_min IS NULL OR strength_max IS NULL
            OR strength_min <= strength_max),
    CONSTRAINT ck_spec_strength_target
        CHECK (strength_target IS NULL
            OR (strength_min IS NULL OR strength_target >= strength_min)
           AND (strength_max IS NULL OR strength_target <= strength_max)),

    CONSTRAINT ck_spec_elongation_range
        CHECK (elongation_min IS NULL OR elongation_max IS NULL
            OR elongation_min <= elongation_max),
    CONSTRAINT ck_spec_elongation_target
        CHECK (elongation_target IS NULL
            OR (elongation_min IS NULL OR elongation_target >= elongation_min)
           AND (elongation_max IS NULL OR elongation_target <= elongation_max)),

    CONSTRAINT ck_spec_moisture_range
        CHECK (moisture_min IS NULL OR moisture_max IS NULL
            OR moisture_min <= moisture_max),
    CONSTRAINT ck_spec_moisture_target
        CHECK (moisture_target IS NULL
            OR (moisture_min IS NULL OR moisture_target >= moisture_min)
           AND (moisture_max IS NULL OR moisture_target <= moisture_max)),

    CONSTRAINT ck_spec_trash_range
        CHECK (trash_content_min IS NULL OR trash_content_max IS NULL
            OR trash_content_min <= trash_content_max),
    CONSTRAINT ck_spec_trash_target
        CHECK (trash_content_target IS NULL
            OR (trash_content_min IS NULL OR trash_content_target >= trash_content_min)
           AND (trash_content_max IS NULL OR trash_content_target <= trash_content_max))
);

CREATE INDEX IF NOT EXISTS idx_fiber_spec_tenant
    ON production.prod_fiber_specification(tenant_id);
CREATE INDEX IF NOT EXISTS idx_fiber_spec_fiber
    ON production.prod_fiber_specification(fiber_id);
CREATE INDEX IF NOT EXISTS idx_fiber_spec_default
    ON production.prod_fiber_specification(tenant_id, fiber_id, is_default)
    WHERE is_default = true;

COMMENT ON TABLE production.prod_fiber_specification IS
    'Target quality specifications (LSL / Target / USL) per fiber. Each fiber can have multiple named profiles (Standard, Premium, Customer-specific). The is_default flag marks the profile used for automated pass/fail comparison.';
COMMENT ON COLUMN production.prod_fiber_specification.spec_name IS
    'Profile name: e.g. "Standard", "Premium Export", "Zara Requirement"';
COMMENT ON COLUMN production.prod_fiber_specification.is_default IS
    'If true, this is the default spec used for automated QC comparison. Only one default per fiber per tenant.';
COMMENT ON COLUMN production.prod_fiber_specification.test_standard IS
    'Reference test standard: e.g. "ISO 1973", "ASTM D1577", "ISO 5079"';

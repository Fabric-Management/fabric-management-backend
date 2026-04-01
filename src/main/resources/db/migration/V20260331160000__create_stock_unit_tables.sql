-- Sprint 1B: StockUnit + QualityGrade + PackageType
-- Adds: quality_grade, stock_unit, stock_unit_audit_log tables
--       + ALTER on goods_receipt / goods_receipt_item for çeki listesi fields.

-- ─────────────────────────────────────────────────
-- 1. Quality Grade
-- ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS production.quality_grade (
    id               UUID PRIMARY KEY,
    uid              VARCHAR(100) UNIQUE,              -- M1: BaseEntity.uid
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    tenant_id        UUID NOT NULL,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,

    material_type    VARCHAR(20) NOT NULL,
    code             VARCHAR(10) NOT NULL,
    name             VARCHAR(100) NOT NULL,
    rank             INT NOT NULL,
    price_factor     NUMERIC(5,3) NOT NULL DEFAULT 1.000,
    saleable         BOOLEAN NOT NULL DEFAULT true,
    requires_approval BOOLEAN NOT NULL DEFAULT false,
    color_hex        VARCHAR(7),
    is_default       BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT uq_quality_grade_tenant_material_code
        UNIQUE (tenant_id, material_type, code)
);

CREATE INDEX IF NOT EXISTS idx_quality_grade_tenant_material_active
    ON production.quality_grade(tenant_id, material_type, is_active);

-- ─────────────────────────────────────────────────
-- 2. Stock Unit
-- ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS production.stock_unit (
    id                   UUID PRIMARY KEY,
    uid                  VARCHAR(100) UNIQUE,          -- M1: BaseEntity.uid
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    created_by           UUID,
    updated_by           UUID,
    tenant_id            UUID NOT NULL,
    is_active            BOOLEAN NOT NULL DEFAULT true,
    deleted_at           TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0,

    barcode              VARCHAR(50) NOT NULL,
    serial_number        VARCHAR(100),
    batch_id             UUID NOT NULL,

    package_type         VARCHAR(20) NOT NULL,
    material_type        VARCHAR(20) NOT NULL,

    initial_weight       NUMERIC(15,3) NOT NULL,
    current_weight       NUMERIC(15,3) NOT NULL,
    gross_weight         NUMERIC(15,3),
    unit                 VARCHAR(10) NOT NULL,

    location_id          UUID,          -- soft FK → iwm.warehouse_location
    previous_location_id UUID,

    quality_grade_id     UUID,          -- soft FK → production.quality_grade
    previous_grade_id    UUID,

    status               VARCHAR(20) NOT NULL,
    source_type          VARCHAR(30) NOT NULL,
    source_id            UUID NOT NULL,

    flagged              BOOLEAN NOT NULL DEFAULT false,
    flag_reason          VARCHAR(50),
    flag_details         TEXT,

    CONSTRAINT uq_stock_unit_tenant_barcode
        UNIQUE (tenant_id, barcode),

    -- M3: DB-level weight invariant guards (defense-in-depth)
    CONSTRAINT chk_stock_unit_initial_weight_positive
        CHECK (initial_weight > 0),
    CONSTRAINT chk_stock_unit_current_weight_non_negative
        CHECK (current_weight >= 0),
    CONSTRAINT chk_stock_unit_current_lte_initial
        CHECK (current_weight <= initial_weight)
);

CREATE INDEX IF NOT EXISTS idx_stock_unit_tenant_batch_status
    ON production.stock_unit(tenant_id, batch_id, status);

CREATE INDEX IF NOT EXISTS idx_stock_unit_tenant_location_status
    ON production.stock_unit(tenant_id, location_id, status);

CREATE INDEX IF NOT EXISTS idx_stock_unit_tenant_grade
    ON production.stock_unit(tenant_id, quality_grade_id);

CREATE INDEX IF NOT EXISTS idx_stock_unit_barcode
    ON production.stock_unit(barcode);

CREATE INDEX IF NOT EXISTS idx_stock_unit_flagged
    ON production.stock_unit(tenant_id, flagged);

-- ─────────────────────────────────────────────────
-- 3. Stock Unit Audit Log
-- ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS production.stock_unit_audit_log (
    id                UUID PRIMARY KEY,
    uid               VARCHAR(100) UNIQUE,             -- M1: BaseEntity.uid
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    tenant_id         UUID NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT true,
    deleted_at        TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0,

    stock_unit_id     UUID NOT NULL,
    operation_type    VARCHAR(50) NOT NULL,
    field_name        VARCHAR(100),
    old_value         VARCHAR(500),
    new_value         VARCHAR(500),
    actor_id          UUID NOT NULL,
    actor_trust_level INT NOT NULL,
    reason            TEXT
);

CREATE INDEX IF NOT EXISTS idx_su_audit_stock_unit_id
    ON production.stock_unit_audit_log(stock_unit_id);

CREATE INDEX IF NOT EXISTS idx_su_audit_tenant_op
    ON production.stock_unit_audit_log(tenant_id, operation_type);

CREATE INDEX IF NOT EXISTS idx_su_audit_actor
    ON production.stock_unit_audit_log(actor_id);

-- ─────────────────────────────────────────────────
-- 4. Alter: goods_receipt — location + çeki listesi  (M2)
-- ─────────────────────────────────────────────────
ALTER TABLE production.goods_receipt
    ADD COLUMN IF NOT EXISTS default_location_id UUID;

ALTER TABLE production.goods_receipt
    ADD COLUMN IF NOT EXISTS declared_net_weight NUMERIC(15,3);

ALTER TABLE production.goods_receipt
    ADD COLUMN IF NOT EXISTS declared_package_count INTEGER;

ALTER TABLE production.goods_receipt
    ADD COLUMN IF NOT EXISTS weight_variance_percent NUMERIC(5,2);

ALTER TABLE production.goods_receipt
    ADD COLUMN IF NOT EXISTS variance_status VARCHAR(20) DEFAULT 'NOT_CHECKED';

-- ─────────────────────────────────────────────────
-- 5. Alter: goods_receipt_item  (M2)
-- ─────────────────────────────────────────────────
ALTER TABLE production.goods_receipt_item
    ADD COLUMN IF NOT EXISTS package_type VARCHAR(20);

ALTER TABLE production.goods_receipt_item
    ADD COLUMN IF NOT EXISTS declared_net_weight NUMERIC(15,3);

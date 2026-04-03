-- Schema: production
CREATE TABLE IF NOT EXISTS production.production_output_record (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    uid                 VARCHAR(30) NOT NULL,
    work_order_id       UUID,
    work_order_number   VARCHAR(30),
    batch_id            UUID,
    output_material_id  UUID NOT NULL,
    output_material_type VARCHAR(30) NOT NULL,
    unit                VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_item_count    INTEGER NOT NULL DEFAULT 0,
    total_net_weight    NUMERIC(15,3) NOT NULL DEFAULT 0,
    confirmed_at        TIMESTAMPTZ,
    confirmed_by_user_id UUID,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS production.production_output_item (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    uid                 VARCHAR(30) NOT NULL,
    record_id           UUID NOT NULL REFERENCES production.production_output_record(id) ON DELETE CASCADE,
    barcode             VARCHAR(60),
    package_type        VARCHAR(20) NOT NULL,
    net_weight          NUMERIC(15,3) NOT NULL,
    gross_weight        NUMERIC(15,3),
    location_id         UUID,
    sequence_no         INTEGER NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_por_tenant_workorder 
    ON production.production_output_record(tenant_id, work_order_id);
CREATE INDEX IF NOT EXISTS idx_por_tenant_batch 
    ON production.production_output_record(tenant_id, batch_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_poi_tenant_barcode 
    ON production.production_output_item(tenant_id, barcode) 
    WHERE barcode IS NOT NULL AND is_active = true;
CREATE INDEX IF NOT EXISTS idx_poi_record 
    ON production.production_output_item(record_id);

ALTER TABLE production.stock_unit ALTER COLUMN batch_id DROP NOT NULL;

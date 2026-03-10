-- =====================================================
-- V20260306130000: Batch Lineage (Parti Soy Ağacı)
-- =====================================================
-- Tracks parent→child batch relationships for full traceability.
-- Enables "one step back, one step forward" lineage as required by
-- ISO 22005 and EU Textile Regulation 1007/2011.
--
-- Example: Blend Batch "BLEND-001" (40% Linen + 60% Wool) →
--   parent_batch = "LINEN-LOT-42",  consumed_quantity = 400 kg
--   parent_batch = "WOOL-LOT-17",   consumed_quantity = 600 kg
-- =====================================================

CREATE TABLE IF NOT EXISTS production.production_execution_batch_lineage (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    uid                     VARCHAR(100)    NOT NULL,

    parent_batch_id         UUID            NOT NULL,
    child_batch_id          UUID            NOT NULL,

    consumed_quantity       DECIMAL(15,3)   NOT NULL,
    unit                    VARCHAR(20)     NOT NULL,

    consumption_percentage  DECIMAL(5,2),
    consumed_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    process_reference       VARCHAR(255),
    remarks                 TEXT,

    is_active               BOOLEAN         NOT NULL DEFAULT true,
    deleted_at              TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_batch_lineage
        PRIMARY KEY (id),
    CONSTRAINT fk_lineage_parent_batch
        FOREIGN KEY (parent_batch_id)
        REFERENCES production.production_execution_fiber_batch(id),
    CONSTRAINT fk_lineage_child_batch
        FOREIGN KEY (child_batch_id)
        REFERENCES production.production_execution_fiber_batch(id),
    CONSTRAINT uq_lineage_tenant_uid
        UNIQUE (tenant_id, uid),
    CONSTRAINT uq_lineage_parent_child
        UNIQUE (parent_batch_id, child_batch_id),
    CONSTRAINT ck_lineage_no_self_ref
        CHECK (parent_batch_id <> child_batch_id),
    CONSTRAINT ck_lineage_qty_positive
        CHECK (consumed_quantity > 0),
    CONSTRAINT ck_lineage_pct_range
        CHECK (consumption_percentage IS NULL
            OR (consumption_percentage > 0 AND consumption_percentage <= 100))
);

CREATE INDEX IF NOT EXISTS idx_lineage_tenant
    ON production.production_execution_batch_lineage(tenant_id);
CREATE INDEX IF NOT EXISTS idx_lineage_parent
    ON production.production_execution_batch_lineage(parent_batch_id);
CREATE INDEX IF NOT EXISTS idx_lineage_child
    ON production.production_execution_batch_lineage(child_batch_id);
CREATE INDEX IF NOT EXISTS idx_lineage_consumed_at
    ON production.production_execution_batch_lineage(consumed_at);
CREATE INDEX IF NOT EXISTS idx_lineage_tenant_child
    ON production.production_execution_batch_lineage(tenant_id, child_batch_id);
CREATE INDEX IF NOT EXISTS idx_lineage_tenant_parent
    ON production.production_execution_batch_lineage(tenant_id, parent_batch_id);

COMMENT ON TABLE  production.production_execution_batch_lineage IS
    'Tracks parent→child batch relationships for full traceability (Batch Soy Ağacı). Each row represents a specific raw-material lot consumed to produce an output batch.';
COMMENT ON COLUMN production.production_execution_batch_lineage.parent_batch_id IS
    'The input/consumed batch (e.g., raw Linen lot from supplier)';
COMMENT ON COLUMN production.production_execution_batch_lineage.child_batch_id IS
    'The output/produced batch (e.g., Linen-Wool blend batch)';
COMMENT ON COLUMN production.production_execution_batch_lineage.consumed_quantity IS
    'Amount consumed from the parent batch for this production run';
COMMENT ON COLUMN production.production_execution_batch_lineage.consumption_percentage IS
    'Percentage of child batch that came from this parent (nullable, calculated or manual)';
COMMENT ON COLUMN production.production_execution_batch_lineage.consumed_at IS
    'Timestamp when the physical consumption occurred on the production floor';
COMMENT ON COLUMN production.production_execution_batch_lineage.process_reference IS
    'Optional production order / work order reference (iş emri no)';

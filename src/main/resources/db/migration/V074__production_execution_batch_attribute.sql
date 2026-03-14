-- ============================================================================
-- V074: Batch Attribute
-- ============================================================================
-- Links batches to fiber attributes (ORGANIC, RECYCLED, etc.) with a value.
-- References production.prod_fiber_attribute for attribute definitions.
-- ============================================================================

CREATE TABLE IF NOT EXISTS production.production_execution_batch_attribute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,

    batch_id UUID NOT NULL,
    attribute_id UUID NOT NULL,
    value TEXT,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_ba_tenant FOREIGN KEY (tenant_id)
        REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ba_batch FOREIGN KEY (batch_id)
        REFERENCES production.production_execution_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_ba_attribute FOREIGN KEY (attribute_id)
        REFERENCES production.prod_fiber_attribute(id) ON DELETE RESTRICT,
    CONSTRAINT uq_ba_batch_attribute UNIQUE (batch_id, attribute_id)
);

CREATE INDEX idx_ba_tenant ON production.production_execution_batch_attribute(tenant_id);
CREATE INDEX idx_ba_batch ON production.production_execution_batch_attribute(batch_id);
CREATE INDEX idx_ba_attribute ON production.production_execution_batch_attribute(attribute_id);
CREATE INDEX idx_ba_active ON production.production_execution_batch_attribute(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.production_execution_batch_attribute IS
'Batch attribute values (ORGANIC, RECYCLED, etc.) linked to prod_fiber_attribute.';
COMMENT ON COLUMN production.production_execution_batch_attribute.value IS
'Attribute value (text, code, or measurement).';

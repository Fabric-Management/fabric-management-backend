-- Sprint 4: WorkOrder Output

CREATE TABLE IF NOT EXISTS production.work_order_output (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    work_order_id UUID NOT NULL,
    stock_unit_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    barcode VARCHAR(50) NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    material_type VARCHAR(30) NOT NULL,
    output_weight NUMERIC(15,3) NOT NULL CHECK (output_weight > 0),
    unit VARCHAR(20) NOT NULL,
    quality_grade_id UUID,
    produced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    produced_by UUID NOT NULL,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_wo_output_tenant_uid UNIQUE (tenant_id, uid)
);

CREATE INDEX IF NOT EXISTS idx_wo_output_tenant ON production.work_order_output(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wo_output_wo ON production.work_order_output(tenant_id, work_order_id);
CREATE INDEX IF NOT EXISTS idx_wo_output_su ON production.work_order_output(tenant_id, stock_unit_id);
CREATE INDEX IF NOT EXISTS idx_wo_output_batch ON production.work_order_output(tenant_id, batch_id);

ALTER TABLE production.work_order_output ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_wo_output ON production.work_order_output
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

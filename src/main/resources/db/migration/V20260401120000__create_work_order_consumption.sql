-- Sprint 3: WorkOrder ↔ StockUnit Consumption Binding

CREATE TABLE IF NOT EXISTS production.work_order_consumption (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    work_order_id UUID NOT NULL,
    stock_unit_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    barcode VARCHAR(50) NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    material_type VARCHAR(30) NOT NULL,
    consumed_weight NUMERIC(15,3) NOT NULL CHECK (consumed_weight > 0),
    unit VARCHAR(20) NOT NULL,
    quality_grade_id UUID,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consumed_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_wo_consumption_tenant_uid UNIQUE (tenant_id, uid)
);

CREATE INDEX IF NOT EXISTS idx_wo_consumption_tenant ON production.work_order_consumption(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wo_consumption_wo ON production.work_order_consumption(tenant_id, work_order_id);
CREATE INDEX IF NOT EXISTS idx_wo_consumption_su ON production.work_order_consumption(tenant_id, stock_unit_id);
CREATE INDEX IF NOT EXISTS idx_wo_consumption_batch ON production.work_order_consumption(tenant_id, batch_id);

ALTER TABLE production.work_order_consumption ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_wo_consumption ON production.work_order_consumption
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

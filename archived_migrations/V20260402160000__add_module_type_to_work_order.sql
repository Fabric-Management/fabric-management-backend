-- Migration: Add module_type to prod_work_order
ALTER TABLE production.prod_work_order
    ADD COLUMN IF NOT EXISTS module_type VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_work_order_module_type ON production.prod_work_order (tenant_id, module_type) WHERE module_type IS NOT NULL;

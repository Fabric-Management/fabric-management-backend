-- Migration: Add output_material_id to prod_work_order
ALTER TABLE production.prod_work_order
    ADD COLUMN IF NOT EXISTS output_material_id UUID;

CREATE INDEX IF NOT EXISTS idx_work_order_output_material ON production.prod_work_order (output_material_id) WHERE output_material_id IS NOT NULL;

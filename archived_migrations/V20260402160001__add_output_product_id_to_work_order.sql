-- Migration: Add output_product_id to prod_work_order
ALTER TABLE production.prod_work_order
    ADD COLUMN IF NOT EXISTS output_product_id UUID;

CREATE INDEX IF NOT EXISTS idx_work_order_output_product ON production.prod_work_order (output_product_id) WHERE output_product_id IS NOT NULL;

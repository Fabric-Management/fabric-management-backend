-- Sprint 6: Denormalize product_id onto work_order_consumption for cost calculation
-- Without this, multi-product cost lookup requires joining to the batch table at query time.

ALTER TABLE production.work_order_consumption
    ADD COLUMN IF NOT EXISTS product_id UUID;

-- Backfill from the batch table (safe: uses existing batch_id FK)
UPDATE production.work_order_consumption woc
SET product_id = b.product_id
FROM production.production_execution_batch b
WHERE b.id = woc.batch_id
  AND woc.product_id IS NULL;

-- Index for cost engine queries (tenant + product scoped)
CREATE INDEX IF NOT EXISTS idx_wo_consumption_product
    ON production.work_order_consumption (tenant_id, product_id)
    WHERE product_id IS NOT NULL;

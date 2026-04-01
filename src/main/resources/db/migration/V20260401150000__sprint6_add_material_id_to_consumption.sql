-- Sprint 6: Denormalize material_id onto work_order_consumption for cost calculation
-- Without this, multi-material cost lookup requires joining to the batch table at query time.

ALTER TABLE production.work_order_consumption
    ADD COLUMN IF NOT EXISTS material_id UUID;

-- Backfill from the batch table (safe: uses existing batch_id FK)
UPDATE production.work_order_consumption woc
SET material_id = b.material_id
FROM production.production_execution_batch b
WHERE b.id = woc.batch_id
  AND woc.material_id IS NULL;

-- Index for cost engine queries (tenant + material scoped)
CREATE INDEX IF NOT EXISTS idx_wo_consumption_material
    ON production.work_order_consumption (tenant_id, material_id)
    WHERE material_id IS NOT NULL;

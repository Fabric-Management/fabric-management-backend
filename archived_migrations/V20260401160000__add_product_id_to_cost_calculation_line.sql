-- Sprint 7a: Add proper product_id column to cost_calculation_line
-- Replaces the text-based "productId=UUID" tracking in notes field.

ALTER TABLE costing.cost_calculation_line
    ADD COLUMN IF NOT EXISTS product_id UUID;

-- Backfill from notes field (Sprint 6 data)
UPDATE costing.cost_calculation_line
SET product_id = CAST(
    SUBSTRING(notes FROM 'productId=([0-9a-f\-]{36})') AS UUID
)
WHERE notes LIKE 'productId=%'
  AND product_id IS NULL;

-- Index for per-product cost breakdown queries
CREATE INDEX IF NOT EXISTS idx_ccline_product
    ON costing.cost_calculation_line (tenant_id, product_id)
    WHERE product_id IS NOT NULL AND is_active = true;

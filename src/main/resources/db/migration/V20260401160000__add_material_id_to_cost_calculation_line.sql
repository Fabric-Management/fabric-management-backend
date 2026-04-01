-- Sprint 7a: Add proper material_id column to cost_calculation_line
-- Replaces the text-based "materialId=UUID" tracking in notes field.

ALTER TABLE costing.cost_calculation_line
    ADD COLUMN IF NOT EXISTS material_id UUID;

-- Backfill from notes field (Sprint 6 data)
UPDATE costing.cost_calculation_line
SET material_id = CAST(
    SUBSTRING(notes FROM 'materialId=([0-9a-f\-]{36})') AS UUID
)
WHERE notes LIKE 'materialId=%'
  AND material_id IS NULL;

-- Index for per-material cost breakdown queries
CREATE INDEX IF NOT EXISTS idx_ccline_material
    ON costing.cost_calculation_line (tenant_id, material_id)
    WHERE material_id IS NOT NULL AND is_active = true;

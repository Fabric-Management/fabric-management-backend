-- Part 1: Completeness tracking on CostCalculation
ALTER TABLE costing.cost_calculation
  ADD COLUMN IF NOT EXISTS complete BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS missing_items JSONB DEFAULT '[]'::JSONB;

COMMENT ON COLUMN costing.cost_calculation.complete IS
  'False when one or more cost items were skipped due to missing price data. Historic rows default to true — we cannot retroactively determine completeness.';

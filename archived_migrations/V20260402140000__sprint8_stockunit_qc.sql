ALTER TABLE production.production_quality_fiber_test_result
  ADD COLUMN stock_unit_id UUID;

CREATE INDEX IF NOT EXISTS idx_fiber_test_stock_unit 
  ON production.production_quality_fiber_test_result(stock_unit_id) 
  WHERE stock_unit_id IS NOT NULL AND is_active = true;

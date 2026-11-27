-- New fibre test results retain the evaluation made at recording time.
ALTER TABLE production.production_quality_fiber_test_result
  ADD COLUMN IF NOT EXISTS evaluation JSONB;

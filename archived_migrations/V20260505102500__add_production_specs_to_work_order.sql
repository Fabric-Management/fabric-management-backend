-- 1. Normalize existing module_type values
UPDATE production.prod_work_order 
  SET module_type = 'GENERIC' WHERE module_type IS NULL;

-- 2. Add NOT NULL constraint
ALTER TABLE production.prod_work_order 
  ALTER COLUMN module_type SET NOT NULL,
  ALTER COLUMN module_type SET DEFAULT 'GENERIC';

-- 3. Add production_specs JSONB column
ALTER TABLE production.prod_work_order 
  ADD COLUMN IF NOT EXISTS production_specs JSONB NOT NULL DEFAULT '{"specType":"GENERIC"}';

-- 4. CHECK constraint for module_type values
ALTER TABLE production.prod_work_order
  ADD CONSTRAINT ck_wo_module_type
  CHECK (module_type IN ('SPINNING','WEAVING','KNITTING','DYEING','FINISHING','GENERIC'));

-- 5. GIN index for JSONB queries
CREATE INDEX IF NOT EXISTS idx_wo_production_specs 
  ON production.prod_work_order USING gin (production_specs) 
  WHERE is_active = true;

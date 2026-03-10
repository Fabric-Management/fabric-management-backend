-- 1. Rename the main batch table
ALTER TABLE production.production_execution_fiber_batch RENAME TO production_execution_batch;

-- 2. Rename fiber_id to material_id
ALTER TABLE production.production_execution_batch RENAME COLUMN fiber_id TO material_id;

-- 3. Add material_type and attributes columns
ALTER TABLE production.production_execution_batch ADD COLUMN material_type VARCHAR(50) NOT NULL DEFAULT 'FIBER';
ALTER TABLE production.production_execution_batch ADD COLUMN attributes JSONB NOT NULL DEFAULT '{}'::jsonb;

-- 4. Rename the reservation table
ALTER TABLE production.production_execution_fiber_batch_reservation RENAME TO production_execution_batch_reservation;

-- 5. Rename indexes for batch table
ALTER INDEX IF EXISTS production.idx_exec_fiber_batch_tenant_id RENAME TO idx_exec_batch_tenant_id;
ALTER INDEX IF EXISTS production.idx_exec_fiber_batch_fiber_id RENAME TO idx_exec_batch_material_id;
ALTER INDEX IF EXISTS production.idx_exec_fiber_batch_code RENAME TO idx_exec_batch_code;
ALTER INDEX IF EXISTS production.idx_exec_fiber_batch_status RENAME TO idx_exec_batch_status;
ALTER INDEX IF EXISTS production.idx_exec_fiber_batch_warehouse_location RENAME TO idx_exec_batch_warehouse_location;
ALTER INDEX IF EXISTS production.idx_batch_tenant_fiber_status RENAME TO idx_batch_tenant_material_status;

-- 6. Rename constraints (optional but good practice)
-- PK name is stable; FK names may be fk_batch_fiber (V010) or fk_exec_fiber_batch_* (other), and tenant FK may not exist
ALTER TABLE production.production_execution_batch RENAME CONSTRAINT production_execution_fiber_batch_pkey TO production_execution_batch_pkey;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_catalog.pg_constraint c
             JOIN pg_catalog.pg_class t ON c.conrelid = t.oid
             JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid
             WHERE n.nspname = 'production' AND t.relname = 'production_execution_batch' AND c.conname = 'fk_exec_fiber_batch_tenant') THEN
    ALTER TABLE production.production_execution_batch RENAME CONSTRAINT fk_exec_fiber_batch_tenant TO fk_exec_batch_tenant;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_catalog.pg_constraint c
             JOIN pg_catalog.pg_class t ON c.conrelid = t.oid
             JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid
             WHERE n.nspname = 'production' AND t.relname = 'production_execution_batch' AND c.conname = 'fk_exec_fiber_batch_fiber') THEN
    ALTER TABLE production.production_execution_batch RENAME CONSTRAINT fk_exec_fiber_batch_fiber TO fk_exec_batch_material;
  ELSIF EXISTS (SELECT 1 FROM pg_catalog.pg_constraint c
                JOIN pg_catalog.pg_class t ON c.conrelid = t.oid
                JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid
                WHERE n.nspname = 'production' AND t.relname = 'production_execution_batch' AND c.conname = 'fk_batch_fiber') THEN
    ALTER TABLE production.production_execution_batch RENAME CONSTRAINT fk_batch_fiber TO fk_exec_batch_material;
  END IF;
END $$;

-- Update comments
COMMENT ON TABLE production.production_execution_batch IS 'Universal batch table for all physical inventory (Fiber, Yarn, Fabric, etc.)';
COMMENT ON COLUMN production.production_execution_batch.material_id IS 'References the specific material (e.g., prod_fiber.id)';
COMMENT ON COLUMN production.production_execution_batch.material_type IS 'Type of material: FIBER, YARN, FABRIC, etc.';
COMMENT ON COLUMN production.production_execution_batch.attributes IS 'Flexible JSONB attributes specific to the material type';

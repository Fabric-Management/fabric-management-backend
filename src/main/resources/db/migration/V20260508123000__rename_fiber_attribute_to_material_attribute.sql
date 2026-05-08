-- Rename prod_fiber_attribute to prod_material_attribute
ALTER TABLE production.prod_fiber_attribute RENAME TO prod_material_attribute;

-- Rename indexes (original names from V002__FIBER_module.sql)
ALTER INDEX production.idx_fiber_attribute_code RENAME TO idx_material_attr_code;
ALTER INDEX production.idx_fiber_attribute_active RENAME TO idx_material_attr_active;

-- Add material_scope column
ALTER TABLE production.prod_material_attribute ADD COLUMN material_scope VARCHAR(20) DEFAULT 'ALL';

-- Note: The foreign key in production_execution_batch_attribute (fk_ba_attribute) 
-- automatically follows the renamed table in Postgres.

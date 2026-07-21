DROP INDEX IF EXISTS iwm.idx_warehouse_location_tenant_quality_area;

ALTER TABLE iwm.warehouse_location
    DROP COLUMN IF EXISTS is_quality_area;

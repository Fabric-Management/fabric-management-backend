ALTER TABLE iwm.warehouse_location
    ADD COLUMN is_quality_area BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_warehouse_location_tenant_quality_area
    ON iwm.warehouse_location (tenant_id, is_active)
    WHERE is_quality_area = TRUE;

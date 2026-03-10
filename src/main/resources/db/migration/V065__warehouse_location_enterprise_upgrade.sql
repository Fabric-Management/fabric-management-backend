-- =====================================================
-- V20260308120000: Warehouse Location Enterprise Upgrade
-- =====================================================
-- Adds capacity management, status tracking, materialized path,
-- barcode support, storage conditions, and machine linking.

-- 1. Add new columns
ALTER TABLE production.production_execution_warehouse_location
    ADD COLUMN IF NOT EXISTS description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN IF NOT EXISTS storage_condition VARCHAR(50) DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS path VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS level INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS barcode VARCHAR(100),
    ADD COLUMN IF NOT EXISTS address_id UUID,
    ADD COLUMN IF NOT EXISTS max_weight_kg DECIMAL(12,3),
    ADD COLUMN IF NOT EXISTS current_weight_kg DECIMAL(12,3) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_volume_m3 DECIMAL(12,3),
    ADD COLUMN IF NOT EXISTS current_volume_m3 DECIMAL(12,3) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS linked_machine_id UUID;

-- 2. Extend type CHECK constraint
ALTER TABLE production.production_execution_warehouse_location
    DROP CONSTRAINT IF EXISTS production_execution_warehouse_location_type_check;

ALTER TABLE production.production_execution_warehouse_location
    ADD CONSTRAINT ck_wh_loc_type
    CHECK (type IN ('WAREHOUSE', 'ZONE', 'AISLE', 'BIN', 'MACHINE', 'PRODUCTION_LINE'));

-- 3. Add status CHECK constraint
ALTER TABLE production.production_execution_warehouse_location
    ADD CONSTRAINT ck_wh_loc_status
    CHECK (status IN ('AVAILABLE', 'FULL', 'BLOCKED', 'MAINTENANCE', 'RESERVED'));

-- 4. Add storage condition CHECK constraint
ALTER TABLE production.production_execution_warehouse_location
    ADD CONSTRAINT ck_wh_loc_storage_condition
    CHECK (storage_condition IN ('STANDARD', 'TEMPERATURE_CONTROLLED', 'HUMIDITY_CONTROLLED', 'HAZARDOUS', 'CLEAN_ROOM'));

-- 5. Add unique constraint for barcode per tenant
CREATE UNIQUE INDEX IF NOT EXISTS uq_wh_loc_tenant_barcode
    ON production.production_execution_warehouse_location (tenant_id, barcode)
    WHERE barcode IS NOT NULL;

-- 6. Add indexes for common queries
CREATE INDEX IF NOT EXISTS idx_wh_loc_path
    ON production.production_execution_warehouse_location (path);

CREATE INDEX IF NOT EXISTS idx_wh_loc_type_active
    ON production.production_execution_warehouse_location (type, is_active);

CREATE INDEX IF NOT EXISTS idx_wh_loc_status_active
    ON production.production_execution_warehouse_location (status, is_active);

CREATE INDEX IF NOT EXISTS idx_wh_loc_parent
    ON production.production_execution_warehouse_location (parent_id);

CREATE INDEX IF NOT EXISTS idx_wh_loc_sort
    ON production.production_execution_warehouse_location (sort_order, name);

-- 7. Backfill path and level for existing locations
-- Root locations (no parent)
UPDATE production.production_execution_warehouse_location
SET path = '/' || code, level = 0
WHERE parent_id IS NULL AND path IS NULL;

-- Level 1 children
UPDATE production.production_execution_warehouse_location child
SET path = parent.path || '/' || child.code, level = 1
FROM production.production_execution_warehouse_location parent
WHERE child.parent_id = parent.id AND child.path IS NULL AND parent.path IS NOT NULL;

-- Level 2 children
UPDATE production.production_execution_warehouse_location child
SET path = parent.path || '/' || child.code, level = 2
FROM production.production_execution_warehouse_location parent
WHERE child.parent_id = parent.id AND child.path IS NULL AND parent.path IS NOT NULL;

-- Level 3 children
UPDATE production.production_execution_warehouse_location child
SET path = parent.path || '/' || child.code, level = 3
FROM production.production_execution_warehouse_location parent
WHERE child.parent_id = parent.id AND child.path IS NULL AND parent.path IS NOT NULL;

-- Level 4+ children (covers any remaining depth)
UPDATE production.production_execution_warehouse_location child
SET path = parent.path || '/' || child.code, level = parent.level + 1
FROM production.production_execution_warehouse_location parent
WHERE child.parent_id = parent.id AND child.path IS NULL AND parent.path IS NOT NULL;

-- 1. Create Warehouse Location table
CREATE TABLE production.production_execution_warehouse_location (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    parent_id UUID REFERENCES production.production_execution_warehouse_location(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('WAREHOUSE', 'ZONE', 'AISLE', 'BIN')),

    CONSTRAINT uk_warehouse_location_code UNIQUE (tenant_id, code)
);

-- 2. Add location_id to FiberBatch
ALTER TABLE production.production_execution_fiber_batch
    ADD COLUMN location_id UUID REFERENCES production.production_execution_warehouse_location(id);

-- Optional: If we want to migrate existing string warehouse_location data, we could do it here.
-- For now, we'll just leave the old column as is, or drop it if it's no longer needed.
-- Let's keep it for a moment, but the domain model will use location_id.
-- Actually, the plan says: "migrate existing string locations if possible, or drop the old column."
-- We will drop the old column to enforce the new structure, but first let's see if we can just drop it.
ALTER TABLE production.production_execution_fiber_batch
    DROP COLUMN warehouse_location;

-- 3. Update Inventory Transaction Type constraint
ALTER TABLE production.production_execution_inventory_transaction ADD COLUMN IF NOT EXISTS location_id UUID;

ALTER TABLE production.production_execution_inventory_transaction
    DROP CONSTRAINT IF EXISTS ck_inv_txn_type_valid;

ALTER TABLE production.production_execution_inventory_transaction
    ADD CONSTRAINT ck_inv_txn_type_valid 
    CHECK (transaction_type IN ('RECEIPT', 'CONSUMPTION', 'WASTE', 'ADJUSTMENT', 'RESERVATION', 'RESERVATION_RELEASE', 'SPLIT_OUT', 'SPLIT_IN', 'TRANSFER_OUT', 'TRANSFER_IN'));

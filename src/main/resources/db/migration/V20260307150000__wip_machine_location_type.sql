-- WIP Location Strategy: Add MACHINE and PRODUCTION_LINE location types
-- Machines are treated as warehouse locations so that material transferred
-- to a machine remains visible in the system (no "virtual loss").

-- 1. Update warehouse location type constraint to include MACHINE and PRODUCTION_LINE
ALTER TABLE production.production_execution_warehouse_location
    DROP CONSTRAINT IF EXISTS production_execution_warehouse_location_type_check;

ALTER TABLE production.production_execution_warehouse_location
    ADD CONSTRAINT production_execution_warehouse_location_type_check
    CHECK (type IN ('WAREHOUSE', 'ZONE', 'AISLE', 'BIN', 'MACHINE', 'PRODUCTION_LINE'));

-- 2. Update inventory transaction type constraint to include all supported types
ALTER TABLE production.production_execution_inventory_transaction
    DROP CONSTRAINT IF EXISTS ck_inv_txn_type_valid;

ALTER TABLE production.production_execution_inventory_transaction
    ADD CONSTRAINT ck_inv_txn_type_valid
    CHECK (transaction_type IN (
        'RECEIPT', 'CONSUMPTION', 'WASTE', 'ADJUSTMENT',
        'TRANSFER', 'RETURN', 'SAMPLE',
        'RESERVATION', 'RESERVATION_RELEASE',
        'SPLIT_OUT', 'SPLIT_IN',
        'TRANSFER_OUT', 'TRANSFER_IN',
        'QUALITY_TEST'
    ));

-- 3. Update reference type constraint (if any) to include PRODUCTION
ALTER TABLE production.production_execution_inventory_transaction
    DROP CONSTRAINT IF EXISTS ck_inv_txn_ref_type_valid;

-- 4. Add location_id if it's missing (to fix schema validation error)
ALTER TABLE production.production_execution_inventory_transaction ADD COLUMN IF NOT EXISTS location_id UUID;

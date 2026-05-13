ALTER TABLE production.production_execution_warehouse_location
    ADD COLUMN warehouse_type VARCHAR(20)
        CONSTRAINT ck_wh_loc_warehouse_type CHECK (warehouse_type IS NULL OR warehouse_type IN (
            'RAW', 'FINISHED', 'WIP', 'REJECT', 'SAMPLE'
        ));

ALTER TABLE production.production_execution_warehouse_location
    DROP CONSTRAINT ck_wh_loc_type;

ALTER TABLE production.production_execution_warehouse_location
    ADD CONSTRAINT ck_wh_loc_type CHECK (type IN (
        'SITE', 'WAREHOUSE', 'ZONE', 'AISLE', 'BIN', 'MACHINE', 'PRODUCTION_LINE'
    ));

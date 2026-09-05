ALTER TABLE production.production_execution_inventory_transaction
    DROP CONSTRAINT ck_inv_txn_type_valid;

ALTER TABLE production.production_execution_inventory_transaction
    ADD CONSTRAINT ck_inv_txn_type_valid CHECK (
        transaction_type IN (
            'RECEIPT',
            'CONSUMPTION',
            'WASTE',
            'ADJUSTMENT',
            'TRANSFER',
            'RETURN',
            'SAMPLE',
            'RESERVATION',
            'RESERVATION_RELEASE',
            'SPLIT_OUT',
            'SPLIT_IN',
            'TRANSFER_OUT',
            'TRANSFER_IN',
            'QUALITY_TEST',
            'SHIPMENT_DISPATCH',
            'SHIPMENT_RETURN'
        )
    ) NOT VALID;

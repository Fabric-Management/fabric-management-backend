ALTER TABLE production.production_execution_batch
    ADD COLUMN source_type VARCHAR(50)
        CONSTRAINT ck_batch_source_type CHECK (source_type IS NULL OR source_type IN (
            'INTERNAL_PRODUCTION', 'PURCHASE', 'SUBCONTRACT',
            'ADJUSTMENT', 'RETURN', 'INITIAL_STOCK'
        )),
    ADD COLUMN source_id UUID;

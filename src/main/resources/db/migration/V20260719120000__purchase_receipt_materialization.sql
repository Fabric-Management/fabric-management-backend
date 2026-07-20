ALTER TABLE production.goods_receipt
    ADD COLUMN IF NOT EXISTS source_line_id UUID,
    ADD COLUMN IF NOT EXISTS supplier_batch_code VARCHAR(100);

ALTER TABLE production.goods_receipt_item
    ADD COLUMN IF NOT EXISTS length NUMERIC(15,3),
    ADD COLUMN IF NOT EXISTS length_unit VARCHAR(10);

ALTER TABLE production.goods_receipt_item
    ADD CONSTRAINT chk_gri_length_positive
        CHECK (length IS NULL OR length > 0),
    ADD CONSTRAINT chk_gri_length_unit_presence
        CHECK ((length IS NULL) = (length_unit IS NULL));

-- BatchPrimaryMeasureService defines M as the canonical unit for FABRIC batches. The original
-- schema constraint predates length-primary inventory and only admitted weight/piece units.
ALTER TABLE production.production_execution_batch
    DROP CONSTRAINT IF EXISTS chk_batch_unit;

ALTER TABLE production.production_execution_batch
    ADD CONSTRAINT chk_batch_unit CHECK (unit IN ('KG', 'MT', 'PIECE', 'M'));

-- Historic PURCHASE_ORDER receipts pre-date source_line_id. PostgreSQL still enforces this
-- constraint for new/updated rows while deferring validation of those historic rows.
ALTER TABLE production.goods_receipt
    ADD CONSTRAINT chk_gr_purchase_source_line_required
        CHECK (source_type <> 'PURCHASE_ORDER' OR source_line_id IS NOT NULL)
        NOT VALID;

CREATE INDEX IF NOT EXISTS idx_gr_tenant_source_line
    ON production.goods_receipt(tenant_id, source_line_id)
    WHERE source_line_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_batch_purchase_receipt_source
    ON production.production_execution_batch(tenant_id, source_id)
    WHERE source_type = 'PURCHASE';

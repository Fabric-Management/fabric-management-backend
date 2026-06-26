DROP INDEX IF EXISTS production.idx_gri_barcode_active;

CREATE UNIQUE INDEX IF NOT EXISTS idx_gri_barcode_active
    ON production.goods_receipt_item (tenant_id, barcode)
    WHERE is_active = true;

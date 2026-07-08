ALTER TABLE sales.quote_line
    ADD COLUMN IF NOT EXISTS lot_snapshot jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE sales.quote_line
    ADD CONSTRAINT chk_quote_line_lot_snapshot_array
    CHECK (jsonb_typeof(lot_snapshot) = 'array')
    NOT VALID;

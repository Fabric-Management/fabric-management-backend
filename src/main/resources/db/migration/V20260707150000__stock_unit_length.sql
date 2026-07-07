ALTER TABLE production.stock_unit
    ADD COLUMN IF NOT EXISTS length numeric(15,3),
    ADD COLUMN IF NOT EXISTS length_unit varchar(10);

ALTER TABLE production.stock_unit
    ADD CONSTRAINT chk_stock_unit_length_positive
    CHECK (length IS NULL OR length > 0)
    NOT VALID;

ALTER TABLE production.stock_unit
    ADD CONSTRAINT chk_stock_unit_length_unit_presence
    CHECK ((length IS NULL AND length_unit IS NULL) OR (length IS NOT NULL AND length_unit IS NOT NULL))
    NOT VALID;

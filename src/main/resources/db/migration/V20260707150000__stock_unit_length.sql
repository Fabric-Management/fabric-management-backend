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

-- First measured length is separate from a later verified remainder. No guessed backfill.
ALTER TABLE production.stock_unit
    ADD COLUMN initial_length numeric(15,3),
    ADD COLUMN initial_length_unit varchar(10),
    ADD CONSTRAINT chk_stock_unit_initial_length CHECK (
        (initial_length IS NULL AND initial_length_unit IS NULL) OR
        (initial_length IS NOT NULL AND initial_length > 0 AND initial_length_unit IS NOT NULL
         AND initial_length_unit IN ('M', 'CM', 'MM', 'KM', 'YD', 'IN', 'FT'))
    );

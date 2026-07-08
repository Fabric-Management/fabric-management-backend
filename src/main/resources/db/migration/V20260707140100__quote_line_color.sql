ALTER TABLE sales.quote_line
    ADD COLUMN IF NOT EXISTS color_id uuid,
    ADD COLUMN IF NOT EXISTS color_code varchar(50),
    ADD COLUMN IF NOT EXISTS color_name varchar(255),
    ADD COLUMN IF NOT EXISTS color_hex varchar(7);

ALTER TABLE sales.quote_line
    ADD CONSTRAINT chk_quote_line_color_hex_format
    CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$')
    NOT VALID;

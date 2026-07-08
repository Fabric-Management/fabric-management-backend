ALTER TABLE sales.quote_line
    ADD COLUMN IF NOT EXISTS quality_grade_id uuid,
    ADD COLUMN IF NOT EXISTS quality_grade_code varchar(10),
    ADD COLUMN IF NOT EXISTS quality_grade_name varchar(255),
    ADD COLUMN IF NOT EXISTS quality_price_factor numeric(4, 3);

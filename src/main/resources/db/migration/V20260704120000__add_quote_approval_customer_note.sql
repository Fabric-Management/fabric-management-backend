ALTER TABLE sales.quote_approval_token
    ADD COLUMN IF NOT EXISTS customer_note TEXT;

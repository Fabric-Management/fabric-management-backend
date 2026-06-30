ALTER TABLE procurement.purchase_order
    ADD COLUMN IF NOT EXISTS reporting_total_original NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS reporting_total_orig_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS reporting_total_converted NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS reporting_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS reporting_exchange_rate NUMERIC(20,8),
    ADD COLUMN IF NOT EXISTS reporting_rate_date DATE;

ALTER TABLE procurement.supplier_quote
    ADD COLUMN IF NOT EXISTS reporting_total_original NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS reporting_total_orig_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS reporting_total_converted NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS reporting_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS reporting_exchange_rate NUMERIC(20,8),
    ADD COLUMN IF NOT EXISTS reporting_rate_date DATE;

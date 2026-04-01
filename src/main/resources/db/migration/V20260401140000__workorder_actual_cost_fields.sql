-- Add actual cost fields to work_orders
ALTER TABLE production.prod_work_order
    ADD COLUMN IF NOT EXISTS actual_cost NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS actual_cost_currency VARCHAR(10);

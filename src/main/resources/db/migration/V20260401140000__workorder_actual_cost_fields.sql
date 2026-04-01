-- Add actual cost fields to work_orders
ALTER TABLE work_orders
    ADD COLUMN actual_cost NUMERIC(15, 3),
    ADD COLUMN actual_cost_currency VARCHAR(3);

-- Migration: Add sales_order_id and product_code to prod_work_order

ALTER TABLE production.prod_work_order
    ADD COLUMN sales_order_id UUID,
    ADD COLUMN product_code VARCHAR(100);

CREATE INDEX idx_work_order_sales_order_id ON production.prod_work_order (sales_order_id);

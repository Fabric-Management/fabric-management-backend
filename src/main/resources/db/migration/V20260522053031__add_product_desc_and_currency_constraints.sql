-- purchase_order_line: Enforce at least one of product_id or product_desc
-- (sales_order_line already has ck_sol_product_or_desc from V001)
ALTER TABLE procurement.purchase_order_line
ADD CONSTRAINT chk_pol_product_or_desc
CHECK (product_id IS NOT NULL OR product_desc IS NOT NULL);

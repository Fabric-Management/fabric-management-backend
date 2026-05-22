-- Replace the existing chk_pol_product_or_desc to enforce non-empty product_desc
ALTER TABLE procurement.purchase_order_line DROP CONSTRAINT IF EXISTS chk_pol_product_or_desc;
ALTER TABLE procurement.purchase_order_line
ADD CONSTRAINT chk_pol_product_or_desc
CHECK (product_id IS NOT NULL OR (product_desc IS NOT NULL AND BTRIM(product_desc) != ''));

-- Replace the existing ck_sol_product_or_desc to enforce non-empty product_desc
ALTER TABLE sales_ord.sales_order_line DROP CONSTRAINT IF EXISTS ck_sol_product_or_desc;
ALTER TABLE sales_ord.sales_order_line
ADD CONSTRAINT ck_sol_product_or_desc
CHECK (product_id IS NOT NULL OR (product_desc IS NOT NULL AND BTRIM(product_desc) != ''));

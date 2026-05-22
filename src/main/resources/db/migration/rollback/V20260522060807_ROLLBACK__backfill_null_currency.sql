ALTER TABLE procurement.purchase_order_line ALTER COLUMN currency DROP NOT NULL;
ALTER TABLE sales_ord.sales_order_line ALTER COLUMN currency DROP NOT NULL;
ALTER TABLE sales_ord.sales_order ALTER COLUMN currency DROP NOT NULL;

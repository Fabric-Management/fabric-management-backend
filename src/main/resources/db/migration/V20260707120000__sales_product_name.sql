ALTER TABLE sales.sales_product
    ADD COLUMN IF NOT EXISTS product_name varchar(255);

-- procurement.purchase_order_line: backfill NULL currency
UPDATE procurement.purchase_order_line
SET currency = 'TRY'
WHERE currency IS NULL;

-- sales_order_line: backfill NULL currency in batches (production safe)
-- Note: In production, run this in batches:
-- UPDATE sales_ord.sales_order_line SET currency = 'TRY' WHERE id IN (SELECT id FROM sales_ord.sales_order_line WHERE currency IS NULL LIMIT 10000);

UPDATE sales_ord.sales_order_line
SET currency = 'TRY'
WHERE currency IS NULL;

ALTER TABLE sales_ord.sales_order_line
ALTER COLUMN currency SET NOT NULL,
ALTER COLUMN currency SET DEFAULT 'TRY';

-- sales_order: ensure NOT NULL
UPDATE sales_ord.sales_order
SET currency = 'TRY'
WHERE currency IS NULL;

ALTER TABLE sales_ord.sales_order
ALTER COLUMN currency SET NOT NULL;

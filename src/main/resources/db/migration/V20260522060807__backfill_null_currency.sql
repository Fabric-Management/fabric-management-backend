-- procurement.purchase_order_line: backfill NULL currency
UPDATE procurement.purchase_order_line
SET currency = 'TRY'
WHERE currency IS NULL;

-- sales_order_line.currency stays nullable: a line's agreed currency is unknown until it is
-- captured, and an invented default would read as an agreement. Sales orders carry no currency.

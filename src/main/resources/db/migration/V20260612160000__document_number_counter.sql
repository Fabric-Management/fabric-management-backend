-- 1. Counter table
CREATE TABLE IF NOT EXISTS finance.document_number_counter (
    tenant_id UUID NOT NULL,
    series VARCHAR(10) NOT NULL,
    year INTEGER NOT NULL,
    last_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, series, year)
);

-- 2. Seed counters from existing document numbers (legacy format: SF-000123)
INSERT INTO finance.document_number_counter (tenant_id, series, year, last_value)
SELECT tenant_id,
       split_part(invoice_number, '-', 1) AS series,
       EXTRACT(YEAR FROM issue_date)::INTEGER AS year,
       MAX(CAST(split_part(invoice_number, '-', 2) AS BIGINT)) AS last_value
FROM finance.finance_invoice
WHERE invoice_number ~ '^[A-Z]{2,3}-[0-9]+$'  -- legacy format only
GROUP BY tenant_id, split_part(invoice_number, '-', 1), EXTRACT(YEAR FROM issue_date)::INTEGER
ON CONFLICT DO NOTHING;

-- Seed from new-format numbers (SERIES-YYYY-NNNNNN)
INSERT INTO finance.document_number_counter (tenant_id, series, year, last_value)
SELECT tenant_id,
       split_part(invoice_number, '-', 1) AS series,
       CAST(split_part(invoice_number, '-', 2) AS INTEGER) AS year,
       MAX(CAST(split_part(invoice_number, '-', 3) AS BIGINT)) AS last_value
FROM finance.finance_invoice
WHERE invoice_number ~ '^[A-Z]{2,3}-[0-9]{4}-[0-9]+$'  -- new format
GROUP BY tenant_id, split_part(invoice_number, '-', 1), CAST(split_part(invoice_number, '-', 2) AS INTEGER)
ON CONFLICT (tenant_id, series, year) DO UPDATE SET last_value = GREATEST(
    finance.document_number_counter.last_value, EXCLUDED.last_value);

-- Seed from payments (legacy format: PAY-000123)
INSERT INTO finance.document_number_counter (tenant_id, series, year, last_value)
SELECT tenant_id,
       'PAY' AS series,
       EXTRACT(YEAR FROM payment_date)::INTEGER AS year,
       MAX(CAST(split_part(payment_number, '-', 2) AS BIGINT)) AS last_value
FROM finance.finance_payment
WHERE payment_number ~ '^PAY-[0-9]+$'  -- legacy format; backfill LEGACY-* numbers excluded by regex
GROUP BY tenant_id, EXTRACT(YEAR FROM payment_date)::INTEGER
ON CONFLICT (tenant_id, series, year) DO UPDATE SET last_value = GREATEST(
    finance.document_number_counter.last_value, EXCLUDED.last_value);

-- Seed from new-format payments (PAY-YYYY-NNNNNN)
INSERT INTO finance.document_number_counter (tenant_id, series, year, last_value)
SELECT tenant_id,
       split_part(payment_number, '-', 1) AS series,
       CAST(split_part(payment_number, '-', 2) AS INTEGER) AS year,
       MAX(CAST(split_part(payment_number, '-', 3) AS BIGINT)) AS last_value
FROM finance.finance_payment
WHERE payment_number ~ '^PAY-[0-9]{4}-[0-9]+$'
GROUP BY tenant_id, split_part(payment_number, '-', 1), CAST(split_part(payment_number, '-', 2) AS INTEGER)
ON CONFLICT (tenant_id, series, year) DO UPDATE SET last_value = GREATEST(
    finance.document_number_counter.last_value, EXCLUDED.last_value);

-- 3. Drop old sequences (legacy format continues to work, new numbers have year → no collision)
DROP SEQUENCE IF EXISTS finance.invoice_number_seq;
DROP SEQUENCE IF EXISTS finance.payment_number_seq;

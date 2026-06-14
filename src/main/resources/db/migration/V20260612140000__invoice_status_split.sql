-- Phase B: Invoice Status Split

-- Add the new payment_status column with a default
ALTER TABLE finance.finance_invoice
ADD COLUMN payment_status VARCHAR(30) DEFAULT 'UNPAID' NOT NULL;

-- Migrate data
-- Convert PAID status to payment_status = PAID, status = SENT
UPDATE finance.finance_invoice
SET payment_status = 'PAID', status = 'SENT'
WHERE status = 'PAID';

-- Convert PARTIALLY_PAID status to payment_status = PARTIALLY_PAID, status = SENT
UPDATE finance.finance_invoice
SET payment_status = 'PARTIALLY_PAID', status = 'SENT'
WHERE status = 'PARTIALLY_PAID';

-- Convert OVERDUE status to payment_status = UNPAID, status = SENT
UPDATE finance.finance_invoice
SET payment_status = 'UNPAID', status = 'SENT'
WHERE status = 'OVERDUE';

-- If an invoice was DISPUTED but had payment partially made
UPDATE finance.finance_invoice
SET payment_status = 'PARTIALLY_PAID'
WHERE status = 'DISPUTED' AND amount_paid > 0 AND amount_due > 0;

-- Drop default value after migration is complete, if desired, but retaining it is fine.

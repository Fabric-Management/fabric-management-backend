-- 1. CreditNoteApplication table (mirrors finance_payment_allocation pattern)
CREATE TABLE IF NOT EXISTS finance.finance_credit_note_application (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    uid VARCHAR(100),
    credit_note_id UUID NOT NULL REFERENCES finance.finance_invoice(id),
    target_invoice_id UUID NOT NULL REFERENCES finance.finance_invoice(id),
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cna_tenant ON finance.finance_credit_note_application(tenant_id);
CREATE INDEX IF NOT EXISTS idx_cna_credit_note ON finance.finance_credit_note_application(credit_note_id);
CREATE INDEX IF NOT EXISTS idx_cna_target ON finance.finance_credit_note_application(target_invoice_id);

-- 2. Add amount_credited to finance_invoice
ALTER TABLE finance.finance_invoice
    ADD COLUMN IF NOT EXISTS amount_credited NUMERIC(19,4) NOT NULL DEFAULT 0;

-- 3. Recompute amountDue to account for amountCredited (no-op for fresh DBs)
UPDATE finance.finance_invoice
SET amount_due = total_amount - amount_paid - amount_credited
WHERE amount_credited != 0;

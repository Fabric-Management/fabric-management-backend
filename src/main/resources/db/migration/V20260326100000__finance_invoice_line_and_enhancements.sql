-- ============================================================
-- Finance Module Enhancement: InvoiceLine + Invoice improvements
-- ============================================================

-- 1. Invoice enhancements: original_invoice_id, amount_due, amount_paid, external_reference, version
ALTER TABLE finance.finance_invoice
    ADD COLUMN IF NOT EXISTS original_invoice_id UUID,
    ADD COLUMN IF NOT EXISTS amount_paid         NUMERIC(19,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS amount_due           NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS external_reference   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS discount_amount      NUMERIC(19,4) DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_inv_original ON finance.finance_invoice(original_invoice_id);
CREATE INDEX IF NOT EXISTS idx_inv_due_date ON finance.finance_invoice(due_date);

-- 2. DB sequence for race-condition-free invoice number generation (per tenant)
CREATE SEQUENCE IF NOT EXISTS finance.invoice_number_seq
    START WITH 1 INCREMENT BY 1 NO CYCLE;

-- 3. Invoice Line table
CREATE TABLE IF NOT EXISTS finance.finance_invoice_line (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    uid             VARCHAR(80),
    invoice_id      UUID NOT NULL REFERENCES finance.finance_invoice(id) ON DELETE CASCADE,
    line_number     INTEGER NOT NULL,
    description     VARCHAR(500) NOT NULL,
    product_code    VARCHAR(50),
    unit            VARCHAR(20) DEFAULT 'PCS',
    quantity        NUMERIC(19,4) NOT NULL,
    unit_price      NUMERIC(19,4) NOT NULL,
    discount_rate   NUMERIC(5,2) DEFAULT 0,
    tax_rate        NUMERIC(5,2) DEFAULT 0,
    line_subtotal   NUMERIC(19,4) NOT NULL,
    line_tax        NUMERIC(19,4) DEFAULT 0,
    line_discount   NUMERIC(19,4) DEFAULT 0,
    line_total      NUMERIC(19,4) NOT NULL,
    notes           VARCHAR(500),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    created_by      UUID,
    updated_by      UUID
);

CREATE INDEX IF NOT EXISTS idx_invl_invoice ON finance.finance_invoice_line(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invl_tenant ON finance.finance_invoice_line(tenant_id);

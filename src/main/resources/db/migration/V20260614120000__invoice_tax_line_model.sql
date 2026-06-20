-- 1. Add tax_category to existing invoice lines
ALTER TABLE finance.finance_invoice_line
  ADD COLUMN IF NOT EXISTS tax_category VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

-- 2. Tax line breakdown table
CREATE TABLE IF NOT EXISTS finance.finance_invoice_tax_line (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    uid             VARCHAR(80),
    invoice_id      UUID NOT NULL REFERENCES finance.finance_invoice(id) ON DELETE CASCADE,
    tax_category    VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    tax_rate        NUMERIC(5,2) NOT NULL DEFAULT 0,
    taxable_base    NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(19,4) NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_itl_invoice ON finance.finance_invoice_tax_line(invoice_id);
CREATE INDEX IF NOT EXISTS idx_itl_tenant ON finance.finance_invoice_tax_line(tenant_id);
-- Reporting index: rate-based aggregation queries
CREATE INDEX IF NOT EXISTS idx_itl_rate_category 
    ON finance.finance_invoice_tax_line(tenant_id, tax_category, tax_rate);

ALTER TABLE finance.finance_invoice_tax_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.finance_invoice_tax_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON finance.finance_invoice_tax_line;
CREATE POLICY rls_tenant_isolation ON finance.finance_invoice_tax_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- 3. Backfill: generate one STANDARD tax line per existing invoice from header values
INSERT INTO finance.finance_invoice_tax_line 
    (id, tenant_id, invoice_id, tax_category, tax_rate, taxable_base, tax_amount)
SELECT 
    gen_random_uuid(),
    i.tenant_id,
    i.id,
    'STANDARD',
    COALESCE(i.tax_rate, 0),
    COALESCE(i.subtotal, 0) - COALESCE(i.discount_amount, 0),
    COALESCE(i.tax_amount, 0)
FROM finance.finance_invoice i
WHERE i.is_active = true
  AND NOT EXISTS (
    SELECT 1 FROM finance.finance_invoice_tax_line tl WHERE tl.invoice_id = i.id
  );

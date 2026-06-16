ALTER TABLE finance.finance_invoice
    ADD COLUMN IF NOT EXISTS reporting_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS issue_exchange_rate NUMERIC(20, 8),
    ADD COLUMN IF NOT EXISTS issue_exchange_rate_date DATE,
    ADD COLUMN IF NOT EXISTS reporting_total NUMERIC(19, 4);

CREATE TABLE IF NOT EXISTS finance.fx_realization (
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
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    invoice_id UUID NOT NULL REFERENCES finance.finance_invoice(id),
    document_amount NUMERIC(19, 4) NOT NULL,
    document_currency VARCHAR(3) NOT NULL,
    reporting_currency VARCHAR(3) NOT NULL,
    issue_exchange_rate NUMERIC(20, 8) NOT NULL,
    issue_exchange_rate_date DATE NOT NULL,
    settlement_exchange_rate NUMERIC(20, 8) NOT NULL,
    settlement_exchange_rate_date DATE NOT NULL,
    realized_gain_loss NUMERIC(19, 4) NOT NULL,
    realized_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    reversal_of_id UUID REFERENCES finance.fx_realization(id)
);

CREATE INDEX IF NOT EXISTS idx_fxr_tenant ON finance.fx_realization(tenant_id);
CREATE INDEX IF NOT EXISTS idx_fxr_invoice ON finance.fx_realization(invoice_id);
CREATE INDEX IF NOT EXISTS idx_fxr_source ON finance.fx_realization(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_fxr_reversal ON finance.fx_realization(reversal_of_id);

ALTER TABLE finance.fx_realization ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.fx_realization FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rls_tenant_isolation ON finance.fx_realization;
CREATE POLICY rls_tenant_isolation ON finance.fx_realization
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

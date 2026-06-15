CREATE TABLE IF NOT EXISTS finance.financial_period (
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
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    closed_by UUID,
    reopened_at TIMESTAMP WITH TIME ZONE,
    reopened_by UUID,
    CONSTRAINT uk_fin_period_tenant_month UNIQUE (tenant_id, period_year, period_month),
    CONSTRAINT ck_fin_period_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT ck_fin_period_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_fin_period_tenant ON finance.financial_period(tenant_id);
CREATE INDEX IF NOT EXISTS idx_fin_period_status ON finance.financial_period(status);
CREATE INDEX IF NOT EXISTS idx_fin_period_end ON finance.financial_period(end_date);

CREATE TABLE IF NOT EXISTS finance.fx_revaluation (
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
    period_id UUID NOT NULL REFERENCES finance.financial_period(id),
    invoice_id UUID NOT NULL REFERENCES finance.finance_invoice(id),
    entry_type VARCHAR(30) NOT NULL,
    invoice_side VARCHAR(30) NOT NULL,
    as_of_date DATE NOT NULL,
    open_document_amount NUMERIC(19, 4) NOT NULL,
    document_currency VARCHAR(3) NOT NULL,
    reporting_currency VARCHAR(3) NOT NULL,
    issue_exchange_rate NUMERIC(20, 8) NOT NULL,
    issue_exchange_rate_date DATE NOT NULL,
    closing_exchange_rate NUMERIC(20, 8) NOT NULL,
    closing_exchange_rate_date DATE NOT NULL,
    unrealized_gain_loss NUMERIC(19, 4) NOT NULL,
    revalued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    reversal_of_id UUID REFERENCES finance.fx_revaluation(id),
    CONSTRAINT ck_fxrev_entry_type CHECK (entry_type IN ('REVALUATION', 'REVERSAL')),
    CONSTRAINT ck_fxrev_side CHECK (invoice_side IN ('ACCOUNTS_RECEIVABLE', 'ACCOUNTS_PAYABLE'))
);

CREATE INDEX IF NOT EXISTS idx_fxrev_tenant ON finance.fx_revaluation(tenant_id);
CREATE INDEX IF NOT EXISTS idx_fxrev_period ON finance.fx_revaluation(period_id);
CREATE INDEX IF NOT EXISTS idx_fxrev_invoice ON finance.fx_revaluation(invoice_id);
CREATE INDEX IF NOT EXISTS idx_fxrev_reversal ON finance.fx_revaluation(reversal_of_id);
CREATE INDEX IF NOT EXISTS idx_fxrev_period_type ON finance.fx_revaluation(period_id, entry_type);

ALTER TABLE finance.financial_period ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.financial_period FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rls_tenant_isolation ON finance.financial_period;
CREATE POLICY rls_tenant_isolation ON finance.financial_period
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE finance.fx_revaluation ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.fx_revaluation FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rls_tenant_isolation ON finance.fx_revaluation;
CREATE POLICY rls_tenant_isolation ON finance.fx_revaluation
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

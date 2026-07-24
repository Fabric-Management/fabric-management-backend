CREATE TABLE IF NOT EXISTS sales.customer_account_team_member (
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    user_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_account_team_member PRIMARY KEY (customer_id, user_id),
    CONSTRAINT uk_customer_account_team_member
        UNIQUE (tenant_id, customer_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_customer_account_team_lookup
    ON sales.customer_account_team_member(tenant_id, customer_id, is_active, created_at);

COMMENT ON TABLE sales.customer_account_team_member IS
    'Tenant-scoped commercial account-team membership; customer_id and user_id are cross-context soft references.';

ALTER TABLE sales.customer_account_team_member ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.customer_account_team_member FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_tenant_isolation ON sales.customer_account_team_member
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

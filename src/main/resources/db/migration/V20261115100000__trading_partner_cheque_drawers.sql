-- PAYMENT-PLAN-7 R13: a customer's cheque drawers (the company's partners who sign cheques
-- personally, or related companies). The customer itself is always a drawer and is not a row.
-- Rows are never hard-deleted by the application: a drawer is deactivated and leaves the picker.
-- No personal identity or tax numbers are stored.
CREATE TABLE IF NOT EXISTS common_company.trading_partner_cheque_drawer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    partner_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    -- Trimmed, inner spaces collapsed, lower-cased (Locale.ROOT); written by the application.
    normalized_name VARCHAR(200) NOT NULL,
    drawer_type VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cheque_drawer_tenant
        FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cheque_drawer_partner
        FOREIGN KEY (partner_id) REFERENCES common_company.common_trading_partner(id) ON DELETE CASCADE,
    CONSTRAINT ck_cheque_drawer_type CHECK (drawer_type IN ('COMPANY', 'INDIVIDUAL')),
    CONSTRAINT ck_cheque_drawer_name CHECK (btrim(name) <> '' AND normalized_name <> ''),
    -- Inactive rows count too: a name can be reactivated, never added a second time.
    CONSTRAINT uk_cheque_drawer_partner_name UNIQUE (tenant_id, partner_id, normalized_name)
);

CREATE INDEX IF NOT EXISTS idx_cheque_drawer_partner
    ON common_company.trading_partner_cheque_drawer(tenant_id, partner_id);

ALTER TABLE common_company.trading_partner_cheque_drawer ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.trading_partner_cheque_drawer FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.trading_partner_cheque_drawer;
CREATE POLICY rls_tenant_isolation ON common_company.trading_partner_cheque_drawer
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- The application never deletes a drawer; only the tenant purge does (fabric_system).
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE ON common_company.trading_partner_cheque_drawer TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON common_company.trading_partner_cheque_drawer TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;

ALTER TABLE common_company.common_trading_partner
    ADD COLUMN IF NOT EXISTS pending_accounting_review BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS common_company.partner_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    partner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(30),
    whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    role VARCHAR(30) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_partner_contact_tenant
        FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_partner_contact_partner
        FOREIGN KEY (partner_id) REFERENCES common_company.common_trading_partner(id) ON DELETE CASCADE,
    CONSTRAINT ck_partner_contact_role
        CHECK (role IN ('BUYER', 'FINANCE', 'LOGISTICS', 'QUALITY', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_partner_contact_tenant
    ON common_company.partner_contact(tenant_id);
CREATE INDEX IF NOT EXISTS idx_partner_contact_partner
    ON common_company.partner_contact(partner_id);
CREATE INDEX IF NOT EXISTS idx_partner_contact_role
    ON common_company.partner_contact(role);
CREATE UNIQUE INDEX IF NOT EXISTS uk_partner_contact_primary_role
    ON common_company.partner_contact(tenant_id, partner_id, role)
    WHERE is_primary = TRUE AND is_active = TRUE;

ALTER TABLE common_company.partner_contact ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.partner_contact FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.partner_contact;
CREATE POLICY rls_tenant_isolation ON common_company.partner_contact
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE sales.quote_approval_token
    ADD COLUMN IF NOT EXISTS contact_id UUID;

ALTER TABLE sales.quote_approval_token
    DROP CONSTRAINT IF EXISTS fk_quote_approval_token_contact;
ALTER TABLE sales.quote_approval_token
    ADD CONSTRAINT fk_quote_approval_token_contact
        FOREIGN KEY (contact_id) REFERENCES common_company.partner_contact(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_quote_approval_token_contact
    ON sales.quote_approval_token(contact_id)
    WHERE contact_id IS NOT NULL;

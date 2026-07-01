-- IDENTITY-1: platform-level login identity and tenant memberships.
--
-- RLS-ALL EXEMPTION:
-- Authentication is pre-auth and cross-tenant. At login there is no selected tenant context, so
-- login_identity must not carry tenant_id and membership.tenant_id is a plain FK used to choose a
-- tenant after the identity is authenticated. Both tables are deliberately RLS-exempt and must be
-- excluded from future RLS-all sweeps.

CREATE TABLE IF NOT EXISTS common_auth.login_identity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    primary_mfa_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    mfa_secret VARCHAR(64),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    requires_password_reset BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_login_identity_email UNIQUE (email),
    CONSTRAINT chk_login_identity_email_lowercase CHECK (email = lower(btrim(email)) AND btrim(email) <> ''),
    CONSTRAINT chk_login_identity_mfa_type CHECK (primary_mfa_type IN ('NONE', 'TOTP', 'EMAIL', 'SMS', 'WHATSAPP'))
);

CREATE INDEX IF NOT EXISTS idx_login_identity_email ON common_auth.login_identity(email);

COMMENT ON TABLE common_auth.login_identity IS
    'RLS-all exempt platform authentication principal. No tenant_id and no RLS because login is pre-auth/cross-tenant; exclude from future RLS-all sweeps.';
COMMENT ON COLUMN common_auth.login_identity.email IS
    'Global lowercase email login handle.';

CREATE TABLE IF NOT EXISTS common_auth.membership (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login_identity_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_membership_login_identity FOREIGN KEY (login_identity_id)
        REFERENCES common_auth.login_identity(id) ON DELETE CASCADE,
    CONSTRAINT fk_membership_tenant FOREIGN KEY (tenant_id)
        REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id)
        REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT uk_membership_identity_tenant UNIQUE (login_identity_id, tenant_id),
    CONSTRAINT uk_membership_user UNIQUE (user_id),
    CONSTRAINT chk_membership_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX IF NOT EXISTS idx_membership_login_identity ON common_auth.membership(login_identity_id);
CREATE INDEX IF NOT EXISTS idx_membership_tenant ON common_auth.membership(tenant_id);

COMMENT ON TABLE common_auth.membership IS
    'RLS-all exempt identity-to-tenant membership table. tenant_id is a plain FK, not an RLS isolation column, because memberships are read cross-tenant during login; exclude from future RLS-all sweeps.';
COMMENT ON COLUMN common_auth.membership.tenant_id IS
    'Plain FK to common_tenant.common_tenant(id); deliberately not an RLS isolation column.';

ALTER TABLE common_auth.login_identity DISABLE ROW LEVEL SECURITY;
ALTER TABLE common_auth.membership DISABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE common_auth.login_identity TO fabric_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE common_auth.membership TO fabric_app;
  END IF;

  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE common_auth.login_identity TO fabric_system;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE common_auth.membership TO fabric_system;
  END IF;
END $$;

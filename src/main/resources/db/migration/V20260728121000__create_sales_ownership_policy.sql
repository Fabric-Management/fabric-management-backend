CREATE TABLE IF NOT EXISTS sales.ownership_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    default_mode VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    mode_effective_at TIMESTAMPTZ NOT NULL,
    assignment_ladder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    triage_age_threshold_hours INTEGER NOT NULL DEFAULT 24,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ownership_policy_tenant UNIQUE (tenant_id),
    CONSTRAINT chk_ownership_policy_mode
        CHECK (default_mode IN ('REQUIRED', 'OPTIONAL', 'EXEMPT')),
    CONSTRAINT chk_ownership_policy_triage_threshold
        CHECK (triage_age_threshold_hours > 0)
);

INSERT INTO sales.ownership_policy (
    id,
    tenant_id,
    uid,
    default_mode,
    mode_effective_at,
    assignment_ladder_enabled,
    triage_age_threshold_hours,
    created_at,
    updated_at,
    is_active,
    version
)
SELECT
    gen_random_uuid(),
    tenant.id,
    'OWNERSHIP-POLICY-' || tenant.id,
    'REQUIRED',
    clock_timestamp(),
    FALSE,
    24,
    clock_timestamp(),
    clock_timestamp(),
    TRUE,
    0
FROM common_tenant.common_tenant tenant
ON CONFLICT (tenant_id) DO NOTHING;

COMMENT ON TABLE sales.ownership_policy IS
    'Sales-owned tenant policy for commercial assignment and triage behaviour.';

ALTER TABLE sales.ownership_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.ownership_policy FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_tenant_isolation ON sales.ownership_policy
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

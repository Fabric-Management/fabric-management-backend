ALTER TABLE common_tenant.common_tenant
    ADD COLUMN trial_started_at TIMESTAMPTZ,
    ADD COLUMN last_activity_at TIMESTAMPTZ;

ALTER TABLE common_tenant.common_tenant
    DROP CONSTRAINT IF EXISTS chk_tenant_status;

ALTER TABLE common_tenant.common_tenant
    ADD CONSTRAINT chk_tenant_status
    CHECK (status IN ('TRIAL', 'ACTIVE', 'EXPIRED', 'SUSPENDED', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_tenant_trial_lifecycle
    ON common_tenant.common_tenant(status, type, trial_started_at)
    WHERE is_active = TRUE;

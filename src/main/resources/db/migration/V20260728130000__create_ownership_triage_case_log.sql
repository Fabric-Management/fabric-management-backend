CREATE TABLE IF NOT EXISTS sales.ownership_triage_case_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    customer_id UUID NOT NULL,
    gap_started_at TIMESTAMPTZ NOT NULL,
    notification_requested_at TIMESTAMPTZ NOT NULL,
    aging_alert_queued_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_ownership_triage_case
        UNIQUE (tenant_id, customer_id, gap_started_at),
    CONSTRAINT chk_ownership_triage_case_timestamps
        CHECK (
            aging_alert_queued_at IS NULL
            OR aging_alert_queued_at >= notification_requested_at
        )
);

CREATE INDEX IF NOT EXISTS idx_ownership_triage_case_open
    ON sales.ownership_triage_case_log (tenant_id, gap_started_at)
    WHERE resolved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_commercial_assignment_latest_closure
    ON sales.customer_commercial_assignment (tenant_id, customer_id, valid_to DESC)
    WHERE valid_to IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_quote_unassigned_actionable_by_customer
    ON sales.quote (tenant_id, customer_id, created_at)
    WHERE assigned_to_id IS NULL
      AND status IN ('DRAFT', 'EVALUATION', 'PENDING_APPROVAL', 'APPROVED')
      AND is_active = TRUE
      AND deleted_at IS NULL;

COMMENT ON TABLE sales.ownership_triage_case_log IS
    'Notification bookkeeping only; the ownership triage truth is always derived from policy and assignments.';

ALTER TABLE sales.ownership_triage_case_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.ownership_triage_case_log FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_tenant_isolation ON sales.ownership_triage_case_log
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

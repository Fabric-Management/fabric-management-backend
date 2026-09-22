-- FE-ARCH-5b-4: disposable, rebuildable subject projection for the decision queue.
SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS flowboard.decision_subject_projection (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    case_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL,
    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,
    subject_number VARCHAR(100) NOT NULL,
    order_created_by UUID,
    task_id UUID,
    case_state VARCHAR(30) NOT NULL,
    case_revision BIGINT NOT NULL,
    unresolved_line_count INTEGER NOT NULL,
    case_opened_at TIMESTAMPTZ NOT NULL,
    case_closed_at TIMESTAMPTZ,
    evidence_revision BIGINT,
    verdict_code VARCHAR(30) NOT NULL,
    projected_at TIMESTAMPTZ NOT NULL,
    source_event_id UUID,
    CONSTRAINT uq_decision_subject_projection_case UNIQUE (tenant_id, case_id),
    CONSTRAINT fk_decision_subject_projection_task FOREIGN KEY (task_id, tenant_id)
        REFERENCES flowboard.task (id, tenant_id),
    CONSTRAINT chk_decision_subject_projection_kind CHECK (kind IN ('ORDER_COVER')),
    CONSTRAINT chk_decision_subject_projection_type CHECK (subject_type IN ('SALES_ORDER')),
    CONSTRAINT chk_decision_subject_projection_state
        CHECK (case_state IN ('OPEN','PARTIALLY_SETTLED','SETTLED','CANCELLED')),
    CONSTRAINT chk_decision_subject_projection_verdict
        CHECK (verdict_code IN ('ACTIONABLE','EVIDENCE_UNKNOWN','NO_EVIDENCE','CASE_CLOSED')),
    CONSTRAINT chk_decision_subject_projection_revisions
        CHECK (case_revision > 0 AND (evidence_revision IS NULL OR evidence_revision > 0)),
    CONSTRAINT chk_decision_subject_projection_lines CHECK (unresolved_line_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_decision_subject_projection_subject
    ON flowboard.decision_subject_projection (tenant_id, subject_type, subject_id);
CREATE INDEX IF NOT EXISTS idx_decision_subject_projection_task
    ON flowboard.decision_subject_projection (tenant_id, task_id);
CREATE INDEX IF NOT EXISTS idx_decision_subject_projection_state
    ON flowboard.decision_subject_projection (tenant_id, case_state, case_id);

ALTER TABLE flowboard.decision_subject_projection ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.decision_subject_projection FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.decision_subject_projection FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON flowboard.decision_subject_projection TO fabric_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON flowboard.decision_subject_projection TO fabric_system;
    END IF;
END $$;

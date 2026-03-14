-- =====================================================
-- V069: Batch Override Log (PHASE 1 — Task 1.2)
-- =====================================================
-- Audit trail for manual batch status overrides.
-- Records when a user bypasses normal transition rules (e.g. QC gate).
--
-- Use case: Supervisor overrides PENDING_QC → AVAILABLE without test result.
-- =====================================================

CREATE TABLE IF NOT EXISTS production.production_execution_batch_override_log (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL,
    from_status VARCHAR(50) NOT NULL,
    to_status VARCHAR(50) NOT NULL,
    overridden_by UUID NOT NULL,
    reason TEXT NOT NULL,
    overridden_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_batch_override_log PRIMARY KEY (id),
    CONSTRAINT fk_override_log_batch
        FOREIGN KEY (batch_id)
        REFERENCES production.production_execution_batch(id)

);

CREATE INDEX IF NOT EXISTS idx_override_log_batch_id
    ON production.production_execution_batch_override_log(batch_id);

CREATE INDEX IF NOT EXISTS idx_override_log_overridden_at
    ON production.production_execution_batch_override_log(overridden_at);

CREATE INDEX IF NOT EXISTS idx_override_log_overridden_by
    ON production.production_execution_batch_override_log(overridden_by);

COMMENT ON TABLE production.production_execution_batch_override_log IS
    'Audit log for manual batch status overrides. Records reason and actor when normal transition rules are bypassed.';

COMMENT ON COLUMN production.production_execution_batch_override_log.reason IS
    'Mandatory justification for the override (e.g. "Emergency release, QC deferred").';

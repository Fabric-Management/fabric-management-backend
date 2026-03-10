-- ============================================================================
-- V20260306120000 — Fiber Test Result: Quality Gate + Extended Measurements
--
-- Adds moisture, trash/neps, and approval_status to the laboratory test table.
-- These fields are textile-industry essentials for incoming raw material QC.
-- ============================================================================

ALTER TABLE production.production_quality_fiber_test_result
    ADD COLUMN IF NOT EXISTS moisture_percent        DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS trash_content_percent   DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS approval_status         VARCHAR(30) NOT NULL DEFAULT 'PENDING';

COMMENT ON COLUMN production.production_quality_fiber_test_result.moisture_percent
    IS 'Moisture/humidity % — critical for weight-based pricing of raw material';

COMMENT ON COLUMN production.production_quality_fiber_test_result.trash_content_percent
    IS 'Trash & neps content % — affects waste ratio and downstream yarn quality';

COMMENT ON COLUMN production.production_quality_fiber_test_result.approval_status
    IS 'Quality gate: PENDING | APPROVED | REJECTED | CONDITIONAL_ACCEPT';

CREATE INDEX IF NOT EXISTS idx_fiber_test_approval
    ON production.production_quality_fiber_test_result (approval_status);

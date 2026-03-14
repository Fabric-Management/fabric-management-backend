-- ============================================================================
-- V083: Add BATCH_NO_QUALITY_STANDARD to notification type constraint
-- ============================================================================
-- Used when FiberTestResult is added but no FiberQualityStandard exists for
-- the batch's ISO code. Batch stays PENDING_QC; notification prompts manual review.
-- ============================================================================

ALTER TABLE common_communication.common_notification DROP CONSTRAINT IF EXISTS chk_notif_type;

ALTER TABLE common_communication.common_notification
    ADD CONSTRAINT chk_notif_type CHECK (type IN (
        'FIBER_REQUEST_SUBMITTED', 'NEW_TENANT_ONBOARDED',
        'FIBER_REQUEST_APPROVED', 'FIBER_REQUEST_REJECTED',
        'BATCH_QC_COMPLETED', 'BATCH_QUARANTINE', 'BATCH_OVERRIDE_REQUIRED',
        'BATCH_NO_QUALITY_STANDARD'
    ));

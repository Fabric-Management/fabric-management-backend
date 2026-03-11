-- ============================================================================
-- V084: Add quality_standard_id to production_execution_batch
-- ============================================================================
-- Optional FK to FiberQualityStandard. When set, QC auto-eval uses this profile
-- instead of the default for the batch's ISO code. Null = use default profile.
-- ============================================================================

ALTER TABLE production.production_execution_batch
    ADD COLUMN IF NOT EXISTS quality_standard_id UUID;

ALTER TABLE production.production_execution_batch
    ADD CONSTRAINT fk_batch_quality_standard
        FOREIGN KEY (quality_standard_id)
        REFERENCES production.prod_fiber_quality_standard(id);

CREATE INDEX IF NOT EXISTS idx_batch_quality_standard
    ON production.production_execution_batch(quality_standard_id);

COMMENT ON COLUMN production.production_execution_batch.quality_standard_id IS
    'Optional: FiberQualityStandard profile for QC. Null = use default for batch ISO code.';

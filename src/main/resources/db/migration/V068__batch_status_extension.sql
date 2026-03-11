-- =====================================================
-- V068: Batch Status Extension (PHASE 1 — Task 1.1)
-- =====================================================
-- Extends production_execution_batch status values for QC lifecycle.
-- Adds parent_batch_id for batch hierarchy (e.g. split/return tracking).
--
-- New statuses:
--   PENDING_QC   — Received, awaiting quality control
--   QUARANTINE   — Suspicious lot, held for review
--   ON_HOLD      — Manual hold (e.g. dispute, investigation)
--   QC_REJECTED  — Quality rejected, return/destroy flow
--   RETURNED     — Returned to supplier
--   DESTROYED    — Destroyed/write-off (terminal)
--
-- Existing: AVAILABLE, RESERVED, IN_PROGRESS, DEPLETED
-- =====================================================

-- 1. Drop existing status CHECK constraint (if any)
ALTER TABLE production.production_execution_batch
    DROP CONSTRAINT IF EXISTS ck_batch_status_valid;

ALTER TABLE production.production_execution_batch
    DROP CONSTRAINT IF EXISTS production_execution_batch_status_check;

-- 1b. Migrate legacy status values to new enum (NEW/IN_USE from V010)
UPDATE production.production_execution_batch SET status = 'PENDING_QC' WHERE status = 'NEW';
UPDATE production.production_execution_batch SET status = 'IN_PROGRESS' WHERE status = 'IN_USE';

-- 2. Add extended status CHECK constraint
ALTER TABLE production.production_execution_batch
    ADD CONSTRAINT ck_batch_status_valid
    CHECK (status IN (
        'AVAILABLE',
        'RESERVED',
        'IN_PROGRESS',
        'DEPLETED',
        'PENDING_QC',
        'QUARANTINE',
        'ON_HOLD',
        'QC_REJECTED',
        'RETURNED',
        'DESTROYED'
    ));

-- 2b. Set default status for new batches (QC gate: incoming lots start in PENDING_QC)
ALTER TABLE production.production_execution_batch
    ALTER COLUMN status SET DEFAULT 'PENDING_QC';

-- 3. Add parent_batch_id (nullable, self-referential FK)
ALTER TABLE production.production_execution_batch
    ADD COLUMN IF NOT EXISTS parent_batch_id UUID;

-- FK only if not already present (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = 'production'
          AND t.relname = 'production_execution_batch'
          AND c.conname = 'fk_batch_parent_batch'
    ) THEN
        ALTER TABLE production.production_execution_batch
            ADD CONSTRAINT fk_batch_parent_batch
            FOREIGN KEY (parent_batch_id)
            REFERENCES production.production_execution_batch(id);
    END IF;
END $$;

-- 4. Index for parent lookups
CREATE INDEX IF NOT EXISTS idx_batch_parent_batch_id
    ON production.production_execution_batch(parent_batch_id)
    WHERE parent_batch_id IS NOT NULL;

-- 5. Comments
COMMENT ON COLUMN production.production_execution_batch.status IS
    'Batch lifecycle: AVAILABLE/RESERVED/IN_PROGRESS/DEPLETED (production) | PENDING_QC/QUARANTINE/ON_HOLD/QC_REJECTED/RETURNED/DESTROYED (QC & disposition)';

COMMENT ON COLUMN production.production_execution_batch.parent_batch_id IS
    'Optional parent batch (e.g. split-from, return-from). Self-referential FK for batch hierarchy.';

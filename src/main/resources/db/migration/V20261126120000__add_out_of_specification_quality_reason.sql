-- Retain the ledger's existing reason combinations and allow one more manual reason.
ALTER TABLE production.quality_decision
    DROP CONSTRAINT IF EXISTS chk_quality_decision_reason_matrix,
    ADD CONSTRAINT chk_quality_decision_reason_matrix CHECK (
        (origin = 'MANUAL' AND outcome = 'RELEASED' AND reason_code IS NULL)
        OR (origin = 'MANUAL' AND outcome = 'QUARANTINED'
            AND reason_code IN (
                'SUSPECTED_DAMAGE', 'AWAITING_LAB', 'SUPPLIER_DISPUTE',
                'SHADE_CHECK', 'OTHER'
            ))
        OR (origin = 'MANUAL' AND outcome = 'NONCONFORMING'
            AND reason_code IN (
                'DAMAGE', 'STAIN', 'SHADE_VARIATION', 'SHORT_LENGTH',
                'MEASURE_MISMATCH', 'OUT_OF_SPECIFICATION', 'OTHER'
            ))
        OR (origin = 'SYSTEM_RELEASE' AND outcome = 'RELEASED'
            AND reason_code = 'SYSTEM_QC_PASSED')
        OR (origin = 'SYSTEM_QC_EVENT' AND outcome = 'RELEASED'
            AND reason_code = 'SYSTEM_QC_PASSED')
        OR (origin = 'SYSTEM_QC_EVENT' AND outcome = 'NONCONFORMING'
            AND reason_code = 'SYSTEM_QC_REJECTED')
        OR (origin = 'MIGRATION_BACKFILL' AND reason_code = 'MIGRATION_BASELINE')
    );

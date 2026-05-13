-- Snapshot of certification at batch completion (GOTS TC: freeze cert at time of production).
-- Filled when batch status becomes DEPLETED (completed).
ALTER TABLE production.production_execution_batch_certification
    ADD COLUMN IF NOT EXISTS cert_number_at_completion VARCHAR(100),
    ADD COLUMN IF NOT EXISTS valid_until_at_completion DATE;

COMMENT ON COLUMN production.production_execution_batch_certification.cert_number_at_completion IS
'Cert number frozen when batch was completed (DEPLETED). GOTS TC compliance.';
COMMENT ON COLUMN production.production_execution_batch_certification.valid_until_at_completion IS
'Valid-until date frozen when batch was completed (DEPLETED). GOTS TC compliance.';

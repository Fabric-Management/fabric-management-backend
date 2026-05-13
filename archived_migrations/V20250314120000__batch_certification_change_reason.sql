-- Add change_reason to batch certification (audit / GOTS compliance).
-- New records get INITIAL via application; existing rows get DEFAULT.
ALTER TABLE production.production_execution_batch_certification
    ADD COLUMN change_reason VARCHAR(30) NOT NULL DEFAULT 'INITIAL';

COMMENT ON COLUMN production.production_execution_batch_certification.change_reason IS 'Reason for this certification record: INITIAL, CORRECTION, RENEWAL, SCOPE_CHANGE, OTHER';

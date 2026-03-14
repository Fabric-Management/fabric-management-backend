-- Audit: was this record initially filled from autoFill (partner/facility cert)? Set to false when user edits.
ALTER TABLE production.production_execution_batch_certification
    ADD COLUMN IF NOT EXISTS is_auto_filled BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN production.production_execution_batch_certification.is_auto_filled IS
'True if add used autoFill source (partner/facility cert). Set to false on any update for GOTS audit.';

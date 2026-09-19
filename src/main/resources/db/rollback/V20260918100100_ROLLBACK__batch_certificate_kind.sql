BEGIN;
ALTER TABLE production.production_execution_batch_certification
    DROP CONSTRAINT chk_batch_certificate_kind,
    DROP COLUMN certificate_kind;
COMMIT;

-- SALES-REQ-1: distinguish certificate document meaning from its coverage scope.
SET LOCAL lock_timeout = '5s';

ALTER TABLE production.production_execution_batch_certification
    ADD COLUMN IF NOT EXISTS certificate_kind VARCHAR(30);
ALTER TABLE production.production_execution_batch_certification
    ADD CONSTRAINT chk_batch_certificate_kind
        CHECK (certificate_kind IS NULL OR certificate_kind IN ('SCOPE', 'TRANSACTION'));

COMMENT ON COLUMN production.production_execution_batch_certification.certificate_kind IS
    'Document meaning. Null legacy records are unclassified and cannot yield MATCH.';

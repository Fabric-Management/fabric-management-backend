-- Development-only destructive rollback. Export collection evidence first; this removes the S4
-- finance tables, the refund tables, and the reversal links on finance_payment (reversal rows already
-- posted stay behind as ordinary opposite-direction payments; only the links and reason are lost).
DROP TABLE IF EXISTS finance.collection_refund_event;
DROP TABLE IF EXISTS finance.collection_refund;
DROP TABLE IF EXISTS finance.collection_reconciliation_health;
DROP TABLE IF EXISTS finance.collection_source_observation;
DROP TABLE IF EXISTS finance.collection_command_receipt;
DROP TABLE IF EXISTS finance.collection_cheque_event;
DROP TABLE IF EXISTS finance.collection_payment_match;
DROP TABLE IF EXISTS finance.collection_cheque_application;
DROP TABLE IF EXISTS finance.collection_cheque;
DROP TABLE IF EXISTS finance.collection_expectation;
DROP TABLE IF EXISTS finance.collection_obligation;
DROP TABLE IF EXISTS finance.collection_projection;
DROP INDEX IF EXISTS finance.uq_pay_reversed_by;
DROP INDEX IF EXISTS finance.uq_pay_reverses;
ALTER TABLE finance.finance_payment
    DROP CONSTRAINT IF EXISTS ck_pay_reversed_by_other,
    DROP CONSTRAINT IF EXISTS ck_pay_reversal_shape,
    DROP CONSTRAINT IF EXISTS fk_pay_reverses;
ALTER TABLE finance.finance_payment
    DROP COLUMN IF EXISTS reversal_reason,
    DROP COLUMN IF EXISTS reversed_by_payment_id,
    DROP COLUMN IF EXISTS reverses_payment_id;
DROP INDEX IF EXISTS finance.uq_collection_payment_tenant_id;
DROP FUNCTION IF EXISTS finance.guard_collection_immutable_evidence();
DROP FUNCTION IF EXISTS finance.guard_collection_match_evidence();

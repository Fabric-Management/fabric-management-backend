-- SOI-D7 follow-up: customer request evaluations and decisions are append-only facts.
-- V20260927120400 granted fabric_system UPDATE on them; the system role only needs to insert
-- and purge them, like every other append-only ledger.
SET LOCAL lock_timeout = '5s';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        REVOKE UPDATE ON sales_ord.customer_request_evaluation,
            sales_ord.customer_request_decision FROM fabric_system;
    END IF;
END $$;

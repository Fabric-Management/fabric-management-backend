-- Stop profile writers first. Refuse to discard retained order-decision history.
BEGIN;
SET LOCAL row_security = off;
LOCK TABLE sales_ord.requirement_profile_version IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sales_ord.requirement_profile_version) THEN
        RAISE EXCEPTION 'Requirement profile history exists: retain schema and roll back application only';
    END IF;
END $$;

ALTER TABLE sales_ord.sales_order_line
    DROP CONSTRAINT fk_sales_line_current_requirement_profile;
DROP TABLE sales_ord.requirement_profile_version;
DROP FUNCTION sales_ord.reject_requirement_profile_history_mutation();
ALTER TABLE sales_ord.sales_order_line
    DROP CONSTRAINT chk_sales_line_requirement_profile_all_or_none,
    DROP COLUMN requirement_profile_snapshot,
    DROP COLUMN requirement_profile_fingerprint,
    DROP COLUMN requirement_profile_version,
    DROP COLUMN requirement_profile_id;
DROP INDEX sales_ord.uq_sales_order_line_tenant_id;
COMMIT;

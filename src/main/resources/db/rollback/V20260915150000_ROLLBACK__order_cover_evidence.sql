-- Stop all evidence writers first. This rollback refuses to discard retained commercial evidence.
-- If snapshots exist, retain the schema and roll back application code only.
-- A failed transactional migration means deployment stopped, not necessarily an inconsistent
-- schema. Inspect Flyway info/validate and retry migrate in a quiet window after contention clears.
-- Do not run this DROP script or Flyway repair unconditionally on migration lock timeout.
BEGIN;
SET LOCAL row_security = off;
LOCK TABLE sales_ord.order_cover_evidence_stream, sales_ord.order_cover_evidence IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sales_ord.order_cover_evidence) THEN
        RAISE EXCEPTION 'Evidence exists: retain schema and use application-only rollback';
    END IF;
END $$;
DROP TABLE sales_ord.order_cover_evidence;
DROP TABLE sales_ord.order_cover_evidence_stream;
DROP FUNCTION sales_ord.reject_order_cover_evidence_mutation();
DROP INDEX sales_ord.uq_sales_order_evidence_tenant_id;
COMMIT;

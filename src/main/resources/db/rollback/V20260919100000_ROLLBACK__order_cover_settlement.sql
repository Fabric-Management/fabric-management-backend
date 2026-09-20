-- Stop order-cover writers first. Refuse to discard activation, cases, receipts or regime markers.
-- One transaction: a failing statement leaves the schema intact, never half-removed.
-- row_security is switched off for the guard: every table below has FORCE ROW LEVEL SECURITY, and a
-- guard that runs without BYPASSRLS and without a bound tenant would see zero rows and pass blind.
BEGIN;
SET LOCAL row_security = off;
LOCK TABLE sales_ord.order_cover_activation, sales_ord.order_cover_case,
    sales_ord.order_cover_result, sales_ord.sales_order IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM sales_ord.order_cover_activation)
     OR EXISTS (SELECT 1 FROM sales_ord.order_cover_case)
     OR EXISTS (SELECT 1 FROM sales_ord.order_cover_result)
     OR EXISTS (SELECT 1 FROM sales_ord.sales_order WHERE cover_regime IS NOT NULL) THEN
    RAISE EXCEPTION
      'Cannot roll back order-cover settlement while activation, cases, receipts, or regime markers exist';
  END IF;
END $$;

DROP TRIGGER IF EXISTS order_cover_line_result_immutable ON sales_ord.order_cover_line_result;
DROP TRIGGER IF EXISTS order_cover_result_immutable ON sales_ord.order_cover_result;
DROP FUNCTION IF EXISTS sales_ord.reject_order_cover_receipt_mutation();
ALTER TABLE sales_ord.order_cover_evidence_stream DROP CONSTRAINT IF EXISTS fk_cover_stream_real_case;
DROP TABLE IF EXISTS sales_ord.order_cover_line_result;
DROP TABLE IF EXISTS sales_ord.order_cover_case_line;
DROP TABLE IF EXISTS sales_ord.order_cover_result;
DROP INDEX IF EXISTS sales_ord.uq_order_cover_evidence_tenant_id;
DROP TABLE IF EXISTS sales_ord.order_cover_case;
DROP TABLE IF EXISTS sales_ord.order_cover_activation;
DROP INDEX IF EXISTS sales_ord.uq_sales_order_cover_identity;
ALTER TABLE sales_ord.sales_order DROP CONSTRAINT IF EXISTS chk_sales_order_cover_regime;
DROP TRIGGER IF EXISTS sales_order_creation_boundary ON sales_ord.sales_order;
DROP FUNCTION IF EXISTS sales_ord.assign_sales_order_creation_seq();
DROP TRIGGER IF EXISTS sales_order_cover_regime_immutable ON sales_ord.sales_order;
DROP FUNCTION IF EXISTS sales_ord.reject_cover_regime_change();
ALTER TABLE sales_ord.sales_order DROP COLUMN IF EXISTS cover_regime;
ALTER TABLE sales_ord.sales_order DROP COLUMN IF EXISTS creation_seq;
DROP SEQUENCE IF EXISTS sales_ord.sales_order_creation_seq;
COMMIT;

-- Development-only destructive rollback; do not run against retained commercial records.
ALTER TABLE sales_ord.order_payment_plan DROP CONSTRAINT IF EXISTS fk_payment_plan_current_revision;
ALTER TABLE sales_ord.order_payment_plan DROP CONSTRAINT IF EXISTS fk_payment_plan_agreed_revision;
DROP TABLE IF EXISTS sales_ord.order_payment_plan_settlement_ack;
DROP TABLE sales_ord.order_payment_plan_line;
DROP TABLE sales_ord.order_payment_plan_currency;
-- Both tables have deferred references to each other: remove the parent FK before dropping.
ALTER TABLE sales_ord.order_payment_plan_revision DROP CONSTRAINT IF EXISTS fk_payment_revision_plan;
DROP TABLE sales_ord.order_payment_plan;
DROP TABLE sales_ord.order_payment_plan_revision;
ALTER TABLE sales_ord.sales_order DROP COLUMN payment_plan_managed, DROP COLUMN payment_note;
-- PAYMENT_PLAN frozen versions must be preserved or exported before restoring older binaries.

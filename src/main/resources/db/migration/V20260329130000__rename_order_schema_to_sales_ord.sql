-- SQL keyword "order" breaks Hibernate-generated SQL (FROM order.sales_order). Rename schema so
-- identifiers are safe unquoted.
ALTER SCHEMA "order" RENAME TO sales_ord;

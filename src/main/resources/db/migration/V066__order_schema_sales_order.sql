-- ═══════════════════════════════════════════════════════════════════════════
-- V20260308140000: Order schema and sales_order table
-- ═══════════════════════════════════════════════════════════════════════════
-- Aligns DB with Java package order.sales: moves sales order table from
-- logistics.logistics_sales_order to order.sales_order.
--
-- Steps:
-- 1. Create schema "order" (quoted: reserved word in SQL)
-- 2. Move table from logistics to order schema
-- 3. Rename table to sales_order
-- ═══════════════════════════════════════════════════════════════════════════

-- STEP 1: Create order schema
CREATE SCHEMA IF NOT EXISTS "order";

-- STEP 2: Move table from logistics to order (indexes/constraints move with it)
ALTER TABLE logistics.logistics_sales_order SET SCHEMA "order";

-- STEP 3: Rename table to sales_order
ALTER TABLE "order".logistics_sales_order RENAME TO sales_order;

-- Comments on new location (optional; table comment preserved on move, re-apply for clarity)
COMMENT ON TABLE "order".sales_order IS
'Sales orders (order.sales domain). Uses trading_partner_id for customer reference.';

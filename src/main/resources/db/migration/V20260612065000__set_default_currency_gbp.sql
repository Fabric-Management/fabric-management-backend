-- V20260612065000__set_default_currency_gbp.sql
-- Set default currency to GBP for all remaining tables that had TRY default in initial schema

ALTER TABLE costing.price_list ALTER COLUMN currency SET DEFAULT 'GBP';
ALTER TABLE costing.price_list_item ALTER COLUMN currency SET DEFAULT 'GBP';
ALTER TABLE costing.exchange_rate_snapshot ALTER COLUMN base_currency SET DEFAULT 'GBP';
ALTER TABLE costing.cost_history ALTER COLUMN currency SET DEFAULT 'GBP';
ALTER TABLE costing.cost_calculation ALTER COLUMN currency SET DEFAULT 'GBP';
ALTER TABLE costing.cost_calculation_line ALTER COLUMN currency SET DEFAULT 'GBP';
ALTER TABLE sales.sales_product ALTER COLUMN currency SET DEFAULT 'GBP';
ALTER TABLE procurement.supplier_quote ALTER COLUMN currency SET DEFAULT 'GBP';
ALTER TABLE i18n.tenant_locale_config ALTER COLUMN currency SET DEFAULT 'GBP';

-- Initial schema had currency VARCHAR(3) DEFAULT 'TRY' in:
ALTER TABLE finance.finance_invoice ALTER COLUMN currency SET DEFAULT 'GBP';
ALTER TABLE sales_ord.sales_order ALTER COLUMN currency SET DEFAULT 'GBP';
ALTER TABLE logistics.logistics_shipment ALTER COLUMN currency SET DEFAULT 'GBP';

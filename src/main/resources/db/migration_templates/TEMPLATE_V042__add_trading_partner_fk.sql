-- ═══════════════════════════════════════════════════════════════════════════
-- TEMPLATE: V042__add_trading_partner_fk_to_<TABLE>.sql
-- ═══════════════════════════════════════════════════════════════════════════
-- Adds trading_partner_id FK to transactional table for TradingPartner migration.
-- 
-- USAGE: Copy this template and replace:
--   - <SCHEMA> with your schema name (e.g., sales, finance, logistics)
--   - <TABLE> with your table name (e.g., orders, invoices, shipments)
-- 
-- Strategy: Dual FK (company_id + trading_partner_id) during transition
-- Risk: LOW - additive change, backward compatible
-- 
-- Prerequisites:
--   - V039__create_trading_partner_tables.sql (TradingPartner exists)
--   - V040__migrate_company_to_trading_partner.sql (data migrated)
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 1: Add trading_partner_id column (nullable during transition)
-- ═══════════════════════════════════════════════════════════════════════════
ALTER TABLE <SCHEMA>.<TABLE>
  ADD COLUMN IF NOT EXISTS trading_partner_id UUID;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 2: Add FK constraint
-- ═══════════════════════════════════════════════════════════════════════════
-- ON DELETE RESTRICT prevents orphan records
-- During transition, company_id provides fallback
ALTER TABLE <SCHEMA>.<TABLE>
  ADD CONSTRAINT fk_<TABLE>_trading_partner
  FOREIGN KEY (trading_partner_id)
  REFERENCES common_company.common_trading_partner(id) ON DELETE RESTRICT;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 3: Create index for join performance
-- ═══════════════════════════════════════════════════════════════════════════
-- Partial index: only non-null values (saves space during transition)
CREATE INDEX IF NOT EXISTS idx_<TABLE>_trading_partner 
    ON <SCHEMA>.<TABLE>(trading_partner_id)
    WHERE trading_partner_id IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 4: Add documentation comment
-- ═══════════════════════════════════════════════════════════════════════════
COMMENT ON COLUMN <SCHEMA>.<TABLE>.trading_partner_id IS 
'FK to TradingPartner. During transition: nullable, populated from company_id via legacy_company_id mapping. Will become NOT NULL after V043 + cutover.';

-- ═══════════════════════════════════════════════════════════════════════════
-- ROLLBACK (run manually if needed)
-- ═══════════════════════════════════════════════════════════════════════════
-- ALTER TABLE <SCHEMA>.<TABLE> DROP CONSTRAINT IF EXISTS fk_<TABLE>_trading_partner;
-- DROP INDEX IF EXISTS <SCHEMA>.idx_<TABLE>_trading_partner;
-- ALTER TABLE <SCHEMA>.<TABLE> DROP COLUMN IF EXISTS trading_partner_id;

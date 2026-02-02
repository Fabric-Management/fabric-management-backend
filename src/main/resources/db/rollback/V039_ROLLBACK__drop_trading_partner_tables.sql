-- ═══════════════════════════════════════════════════════════════════════════
-- V039 ROLLBACK: Drop Trading Partner Tables
-- ═══════════════════════════════════════════════════════════════════════════
-- USAGE: Run this ONLY if you need to rollback V039 migration.
--        Execute manually - NOT part of Flyway automatic migration.
--
-- WARNING: 
--   - This will DROP all trading partner data
--   - Company table data is NOT affected
--   - Run V040_ROLLBACK first if data migration was applied
--
-- PREREQUISITE: Ensure V040_ROLLBACK has been run first (if applicable)
-- ═══════════════════════════════════════════════════════════════════════════

-- Check for dependent data before dropping
DO $$
DECLARE
    partner_count INT;
    registry_count INT;
BEGIN
    SELECT COUNT(*) INTO partner_count FROM common_company.common_trading_partner;
    SELECT COUNT(*) INTO registry_count FROM common_company.trading_partner_registry;
    
    IF partner_count > 0 OR registry_count > 0 THEN
        RAISE WARNING 'Data will be lost: % trading partners, % registry entries', 
            partner_count, registry_count;
        RAISE WARNING 'Sleeping for 5 seconds - CTRL+C to cancel...';
        PERFORM pg_sleep(5);
    END IF;
END $$;

-- Step 1: Drop view first (depends on tables)
DROP VIEW IF EXISTS common_company.v_partner_legacy_mapping;

-- Step 2: Drop TradingPartner table (depends on registry)
DROP TABLE IF EXISTS common_company.common_trading_partner CASCADE;

-- Step 3: Drop TradingPartnerRegistry table
DROP TABLE IF EXISTS common_company.trading_partner_registry CASCADE;

-- Step 4: Remove from Flyway history (optional - allows re-migration)
-- WARNING: Only run this if you plan to re-run V039
-- DELETE FROM flyway_schema_history WHERE version = '039';

-- Verify cleanup
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables 
               WHERE table_schema = 'common_company' 
               AND table_name IN ('trading_partner_registry', 'common_trading_partner')) THEN
        RAISE EXCEPTION 'Rollback failed: Tables still exist';
    ELSE
        RAISE NOTICE 'V039 Rollback completed successfully';
    END IF;
END $$;

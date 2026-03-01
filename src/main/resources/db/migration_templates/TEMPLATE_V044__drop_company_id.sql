-- ═══════════════════════════════════════════════════════════════════════════
-- TEMPLATE: V044__drop_company_id_from_<TABLE>.sql
-- ═══════════════════════════════════════════════════════════════════════════
-- FINAL CLEANUP: Removes legacy company_id column after migration complete.
-- 
-- ⚠️  WARNING: This migration is IRREVERSIBLE!
-- 
-- PREREQUISITES (all must be TRUE before running):
--   1. ✅ V042 + V043 completed successfully
--   2. ✅ 100% of records have trading_partner_id populated
--   3. ✅ feature.trading-partner.legacy-fallback = false tested in staging
--   4. ✅ All services updated to use trading_partner_id only
--   5. ✅ Entity classes updated (companyId field removed)
--   6. ✅ Database backup exists
--   7. ✅ Deployment rollback plan documented (restore from backup)
-- 
-- Risk: HIGH - requires full system test before deployment
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 1: Pre-flight safety checks
-- ═══════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    unmigrated_count INTEGER;
    total_count INTEGER;
    null_partner_count INTEGER;
BEGIN
    -- Check for unmigrated records
    SELECT COUNT(*) INTO unmigrated_count 
    FROM <SCHEMA>.<TABLE> 
    WHERE trading_partner_id IS NULL AND company_id IS NOT NULL;
    
    IF unmigrated_count > 0 THEN
        RAISE EXCEPTION 
            'ABORT: Cannot drop company_id - % unmigrated records exist. ' ||
            'Run V043 migration first or manually fix these records.',
            unmigrated_count;
    END IF;
    
    -- Check for NULL trading_partner_id where it shouldn't be
    SELECT COUNT(*) INTO null_partner_count
    FROM <SCHEMA>.<TABLE>
    WHERE trading_partner_id IS NULL;
    
    SELECT COUNT(*) INTO total_count
    FROM <SCHEMA>.<TABLE>;
    
    RAISE NOTICE '═══════════════════════════════════════════════════════════';
    RAISE NOTICE 'Pre-flight check for <SCHEMA>.<TABLE>:';
    RAISE NOTICE '  Total rows:              %', total_count;
    RAISE NOTICE '  Rows with partner:       %', total_count - null_partner_count;
    RAISE NOTICE '  Rows without partner:    %', null_partner_count;
    RAISE NOTICE '  Unmigrated (company_id): %', unmigrated_count;
    RAISE NOTICE '═══════════════════════════════════════════════════════════';
    
    IF null_partner_count > 0 THEN
        RAISE WARNING '% rows have NULL trading_partner_id. These will remain NULL after migration.', null_partner_count;
    END IF;
END $$;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 2: Make trading_partner_id NOT NULL (optional but recommended)
-- ═══════════════════════════════════════════════════════════════════════════
-- Uncomment if ALL records should have a trading partner
-- ALTER TABLE <SCHEMA>.<TABLE>
--   ALTER COLUMN trading_partner_id SET NOT NULL;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 3: Drop any existing FK constraint on company_id (if exists)
-- ═══════════════════════════════════════════════════════════════════════════
ALTER TABLE <SCHEMA>.<TABLE>
  DROP CONSTRAINT IF EXISTS fk_<TABLE>_company;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 4: Drop index on company_id (if exists)
-- ═══════════════════════════════════════════════════════════════════════════
DROP INDEX IF EXISTS <SCHEMA>.idx_<TABLE>_company;
DROP INDEX IF EXISTS <SCHEMA>.idx_<TABLE>_company_id;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 5: Drop the legacy column
-- ═══════════════════════════════════════════════════════════════════════════
ALTER TABLE <SCHEMA>.<TABLE>
  DROP COLUMN IF EXISTS company_id;

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 6: Update trading_partner_id index to full (not partial)
-- ═══════════════════════════════════════════════════════════════════════════
-- Drop partial index
DROP INDEX IF EXISTS <SCHEMA>.idx_<TABLE>_trading_partner;

-- Create full index
CREATE INDEX IF NOT EXISTS idx_<TABLE>_trading_partner 
    ON <SCHEMA>.<TABLE>(trading_partner_id);

-- ═══════════════════════════════════════════════════════════════════════════
-- STEP 7: Update table documentation
-- ═══════════════════════════════════════════════════════════════════════════
COMMENT ON TABLE <SCHEMA>.<TABLE> IS 
'<TABLE> table - Trading Partner migration complete (Faz 1.5). Uses trading_partner_id FK to common_trading_partner.';

COMMENT ON COLUMN <SCHEMA>.<TABLE>.trading_partner_id IS 
'FK to TradingPartner (common_company.common_trading_partner). Migrated from legacy company_id.';

-- ═══════════════════════════════════════════════════════════════════════════
-- POST-MIGRATION VERIFICATION
-- ═══════════════════════════════════════════════════════════════════════════

/*
-- Verify column dropped
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_schema = '<SCHEMA>' AND table_name = '<TABLE>'
ORDER BY ordinal_position;

-- Should NOT include 'company_id'
-- Should include 'trading_partner_id'
*/


-- ═══════════════════════════════════════════════════════════════════════════
-- ROLLBACK: NOT POSSIBLE
-- ═══════════════════════════════════════════════════════════════════════════
-- This migration is IRREVERSIBLE. To rollback:
--   1. Restore database from backup
--   2. Redeploy previous application version
--
-- DO NOT attempt to recreate company_id column - data is lost

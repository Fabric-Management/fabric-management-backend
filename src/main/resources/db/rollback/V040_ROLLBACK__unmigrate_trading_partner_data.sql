-- ═══════════════════════════════════════════════════════════════════════════
-- V040 ROLLBACK: Unmigrate Trading Partner Data
-- ═══════════════════════════════════════════════════════════════════════════
-- USAGE: Run this ONLY if you need to rollback V040 data migration.
--        Execute manually BEFORE V039_ROLLBACK.
--
-- SAFE OPERATION:
--   - Only deletes migrated data (created by V040)
--   - Does NOT affect manually created trading partners
--   - Does NOT affect Company table data
--
-- IDENTIFICATION:
--   - Migrated records have legacy_company_id IS NOT NULL
--   - Manually created records have legacy_company_id IS NULL
-- ═══════════════════════════════════════════════════════════════════════════

-- Step 1: Count records to be deleted
DO $$
DECLARE
    migrated_partners INT;
    migrated_registries INT;
    manual_partners INT;
BEGIN
    -- Count migrated (will be deleted)
    SELECT COUNT(*) INTO migrated_partners 
    FROM common_company.common_trading_partner 
    WHERE legacy_company_id IS NOT NULL;
    
    -- Count manual (will be preserved)
    SELECT COUNT(*) INTO manual_partners 
    FROM common_company.common_trading_partner 
    WHERE legacy_company_id IS NULL;
    
    -- Count orphan registries (will be deleted)
    SELECT COUNT(*) INTO migrated_registries 
    FROM common_company.trading_partner_registry r
    WHERE NOT EXISTS (
        SELECT 1 FROM common_company.common_trading_partner tp
        WHERE tp.registry_id = r.id AND tp.legacy_company_id IS NULL
    );
    
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    RAISE NOTICE 'V040 Rollback Preview:';
    RAISE NOTICE '  - Migrated partners to delete:   %', migrated_partners;
    RAISE NOTICE '  - Manual partners (preserved):   %', manual_partners;
    RAISE NOTICE '  - Orphan registries to delete:   %', migrated_registries;
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    
    IF migrated_partners > 0 THEN
        RAISE WARNING 'Sleeping for 5 seconds - CTRL+C to cancel...';
        PERFORM pg_sleep(5);
    END IF;
END $$;

-- Step 2: Delete migrated trading partners (have legacy_company_id)
DELETE FROM common_company.common_trading_partner 
WHERE legacy_company_id IS NOT NULL;

-- Step 3: Delete orphan registries (no remaining trading partners reference them)
-- This is safe because manually created partners will keep their registries
DELETE FROM common_company.trading_partner_registry r
WHERE NOT EXISTS (
    SELECT 1 FROM common_company.common_trading_partner tp
    WHERE tp.registry_id = r.id
);

-- Step 4: Remove from Flyway history (optional)
-- DELETE FROM flyway_schema_history WHERE version = '040';

-- Verify and report
DO $$
DECLARE
    remaining_partners INT;
    remaining_registries INT;
BEGIN
    SELECT COUNT(*) INTO remaining_partners FROM common_company.common_trading_partner;
    SELECT COUNT(*) INTO remaining_registries FROM common_company.trading_partner_registry;
    
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    RAISE NOTICE 'V040 Rollback completed:';
    RAISE NOTICE '  - Remaining partners:    %', remaining_partners;
    RAISE NOTICE '  - Remaining registries:  %', remaining_registries;
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    
    IF remaining_partners > 0 THEN
        RAISE NOTICE 'Manual trading partners were preserved (no legacy_company_id)';
    END IF;
END $$;

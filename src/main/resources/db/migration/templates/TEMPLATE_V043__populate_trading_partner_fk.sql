-- ═══════════════════════════════════════════════════════════════════════════
-- TEMPLATE: V043__populate_trading_partner_fk_for_<TABLE>.sql
-- ═══════════════════════════════════════════════════════════════════════════
-- Populates trading_partner_id from company_id via legacy_company_id mapping.
-- 
-- USAGE: Copy this template and replace:
--   - <SCHEMA> with your schema name (e.g., sales, finance, logistics)
--   - <TABLE> with your table name (e.g., orders, invoices, shipments)
-- 
-- Strategy: UPDATE using TradingPartner.legacy_company_id = <TABLE>.company_id
-- Risk: LOW - no schema change, only data update
-- 
-- IMPORTANT: For large tables (>100K rows), use the batch migration version below
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- OPTION A: Single UPDATE (for small-medium tables < 100K rows)
-- ═══════════════════════════════════════════════════════════════════════════

-- Populate trading_partner_id from legacy_company_id mapping
UPDATE <SCHEMA>.<TABLE> t
SET trading_partner_id = tp.id
FROM common_company.common_trading_partner tp
WHERE tp.tenant_id = t.tenant_id
  AND tp.legacy_company_id = t.company_id
  AND t.trading_partner_id IS NULL;  -- Only unmigrated rows

-- Log migration statistics
DO $$
DECLARE
    total_count INTEGER;
    migrated_count INTEGER;
    unmigrated_count INTEGER;
    migration_pct NUMERIC;
BEGIN
    SELECT COUNT(*) INTO total_count 
    FROM <SCHEMA>.<TABLE>;
    
    SELECT COUNT(*) INTO migrated_count 
    FROM <SCHEMA>.<TABLE> WHERE trading_partner_id IS NOT NULL;
    
    SELECT COUNT(*) INTO unmigrated_count 
    FROM <SCHEMA>.<TABLE> 
    WHERE trading_partner_id IS NULL AND company_id IS NOT NULL;
    
    IF total_count > 0 THEN
        migration_pct := ROUND(migrated_count::numeric / total_count * 100, 2);
    ELSE
        migration_pct := 100;
    END IF;
    
    RAISE NOTICE '═══════════════════════════════════════════════════════════';
    RAISE NOTICE 'Migration Result for <SCHEMA>.<TABLE>:';
    RAISE NOTICE '  Total rows:      %', total_count;
    RAISE NOTICE '  Migrated:        % (%.2f%%)', migrated_count, migration_pct;
    RAISE NOTICE '  Unmigrated:      % (no matching TradingPartner)', unmigrated_count;
    RAISE NOTICE '═══════════════════════════════════════════════════════════';
    
    IF unmigrated_count > 0 THEN
        RAISE WARNING '% rows could not be migrated - company_id has no matching TradingPartner.legacy_company_id', unmigrated_count;
    END IF;
END $$;


-- ═══════════════════════════════════════════════════════════════════════════
-- OPTION B: Batch UPDATE (for large tables > 100K rows)
-- Uncomment and use this instead of OPTION A for large tables
-- ═══════════════════════════════════════════════════════════════════════════

/*
DO $$
DECLARE
    batch_size INTEGER := 10000;
    affected_rows INTEGER;
    total_migrated INTEGER := 0;
    iteration INTEGER := 0;
BEGIN
    RAISE NOTICE 'Starting batch migration for <SCHEMA>.<TABLE> (batch size: %)', batch_size;
    
    LOOP
        iteration := iteration + 1;
        
        UPDATE <SCHEMA>.<TABLE> t
        SET trading_partner_id = tp.id
        FROM common_company.common_trading_partner tp
        WHERE tp.tenant_id = t.tenant_id
          AND tp.legacy_company_id = t.company_id
          AND t.trading_partner_id IS NULL
          AND t.id IN (
              SELECT id FROM <SCHEMA>.<TABLE> 
              WHERE trading_partner_id IS NULL AND company_id IS NOT NULL
              LIMIT batch_size
          );
        
        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        total_migrated := total_migrated + affected_rows;
        
        IF affected_rows > 0 THEN
            RAISE NOTICE 'Iteration %: migrated % rows (total: %)', iteration, affected_rows, total_migrated;
        END IF;
        
        EXIT WHEN affected_rows = 0;
        
        -- Small pause to reduce lock contention (100ms)
        PERFORM pg_sleep(0.1);
    END LOOP;
    
    RAISE NOTICE '═══════════════════════════════════════════════════════════';
    RAISE NOTICE 'Batch migration complete: % rows migrated in % iterations', total_migrated, iteration;
    RAISE NOTICE '═══════════════════════════════════════════════════════════';
END $$;
*/


-- ═══════════════════════════════════════════════════════════════════════════
-- VERIFICATION QUERY (run after migration)
-- ═══════════════════════════════════════════════════════════════════════════

/*
SELECT 
    '<SCHEMA>.<TABLE>' AS table_name,
    COUNT(*) AS total,
    COUNT(trading_partner_id) AS migrated,
    COUNT(*) FILTER (WHERE trading_partner_id IS NULL AND company_id IS NOT NULL) AS unmigrated,
    ROUND(COUNT(trading_partner_id)::numeric / NULLIF(COUNT(*), 0) * 100, 2) AS migration_pct
FROM <SCHEMA>.<TABLE>
WHERE company_id IS NOT NULL;
*/


-- ═══════════════════════════════════════════════════════════════════════════
-- ROLLBACK (run manually if needed)
-- ═══════════════════════════════════════════════════════════════════════════
-- UPDATE <SCHEMA>.<TABLE> SET trading_partner_id = NULL;

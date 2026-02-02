-- ═══════════════════════════════════════════════════════════════════════════
-- V045_ROLLBACK: Drop Tenant Table
-- ═══════════════════════════════════════════════════════════════════════════
-- Drops the common_tenant table and schema.
-- 
-- IMPORTANT: Run V046_ROLLBACK first to restore FK references,
-- then run this to drop the tenant table.
--
-- WARNING: This will delete all tenant data permanently!
-- ═══════════════════════════════════════════════════════════════════════════

-- Drop the table
DROP TABLE IF EXISTS common_tenant.common_tenant CASCADE;

-- Drop the schema
DROP SCHEMA IF EXISTS common_tenant CASCADE;

RAISE NOTICE 'V045 Rollback complete: common_tenant table and schema dropped';

-- ═══════════════════════════════════════════════════════════════════════════
-- END OF ROLLBACK
-- ═══════════════════════════════════════════════════════════════════════════

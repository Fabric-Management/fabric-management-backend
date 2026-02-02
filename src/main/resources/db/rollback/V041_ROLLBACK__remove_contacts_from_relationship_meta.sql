-- ═══════════════════════════════════════════════════════════════════════════
-- V041 ROLLBACK: Remove contacts from relationship_meta
-- ═══════════════════════════════════════════════════════════════════════════
-- Removes the 'contacts' key from relationship_meta JSONB.
-- Original CompanyContact data is NOT affected.
-- ═══════════════════════════════════════════════════════════════════════════

UPDATE common_company.common_trading_partner
SET relationship_meta = relationship_meta - 'contacts',
    updated_at = CURRENT_TIMESTAMP
WHERE relationship_meta ? 'contacts';

-- Report
DO $$
DECLARE
    affected INT;
BEGIN
    GET DIAGNOSTICS affected = ROW_COUNT;
    RAISE NOTICE 'V041 Rollback: Removed contacts from % trading partners', affected;
END $$;

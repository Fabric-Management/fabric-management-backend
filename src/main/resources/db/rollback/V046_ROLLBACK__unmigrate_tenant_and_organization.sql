-- ═══════════════════════════════════════════════════════════════════════════
-- V046_ROLLBACK: Reverse Tenant/Organization Migration
-- ═══════════════════════════════════════════════════════════════════════════
-- CRITICAL: Run this ONLY if V046 migration needs to be completely reversed.
-- This will undo:
--   1. Table rename: common_organization → common_company
--   2. Column renames back to original names
--   3. FK constraint updates
--   4. Junction table renames
--   5. Delete common_tenant data (data preserved in common_company)
--
-- WARNING: This is a DESTRUCTIVE operation. Back up before running!
-- ═══════════════════════════════════════════════════════════════════════════

-- Step 1: Rename junction tables back
ALTER TABLE common_company.common_organization_address 
    RENAME TO common_company_address;
ALTER TABLE common_company.common_company_address 
    RENAME COLUMN organization_id TO company_id;

ALTER TABLE common_company.common_organization_contact 
    RENAME TO common_company_contact;
ALTER TABLE common_company.common_company_contact 
    RENAME COLUMN organization_id TO company_id;

-- Step 2: Rename columns back
ALTER TABLE common_company.common_organization 
    RENAME COLUMN name TO company_name;
ALTER TABLE common_company.common_organization 
    RENAME COLUMN organization_type TO company_type;
ALTER TABLE common_company.common_organization 
    RENAME COLUMN parent_organization_id TO parent_company_id;

-- Step 3: Rename constraints back
ALTER TABLE common_company.common_organization 
    RENAME CONSTRAINT uk_organization_tenant_tax_id TO uk_company_tenant_tax_id;
ALTER TABLE common_company.common_organization 
    RENAME CONSTRAINT fk_organization_parent TO fk_company_parent;
ALTER TABLE common_company.common_organization 
    RENAME CONSTRAINT fk_organization_tenant TO fk_company_tenant;

-- Step 4: Rename indexes back
ALTER INDEX common_company.idx_organization_tenant RENAME TO idx_company_tenant;
ALTER INDEX common_company.idx_organization_type RENAME TO idx_company_type;
ALTER INDEX common_company.idx_organization_tax_id RENAME TO idx_company_tax_id;
ALTER INDEX common_company.idx_organization_active RENAME TO idx_company_active;

-- Step 5: Rename table back
ALTER TABLE common_company.common_organization RENAME TO common_company;

-- Step 6: Drop FK to common_tenant, restore self-reference
ALTER TABLE common_company.common_company
    DROP CONSTRAINT IF EXISTS fk_company_tenant;

-- Step 7: Restore User.company_id
ALTER TABLE common_user.common_user 
    DROP CONSTRAINT IF EXISTS fk_user_organization;
ALTER INDEX common_user.idx_user_organization RENAME TO idx_user_company;
ALTER TABLE common_user.common_user 
    RENAME COLUMN organization_id TO company_id;

-- Restore FK to common_company
ALTER TABLE common_user.common_user
    ADD CONSTRAINT fk_user_company
    FOREIGN KEY (company_id)
    REFERENCES common_company.common_company(id) ON DELETE RESTRICT;

-- Step 8: Restore TradingPartner FKs
ALTER TABLE common_company.common_trading_partner
    DROP CONSTRAINT IF EXISTS fk_tp_tenant;
ALTER TABLE common_company.common_trading_partner
    ADD CONSTRAINT fk_tp_tenant 
    FOREIGN KEY (tenant_id)
    REFERENCES common_company.common_company(id) ON DELETE RESTRICT;

-- Step 9: Restore TradingPartnerRegistry.linked_tenant_id FK
ALTER TABLE common_company.trading_partner_registry
    DROP CONSTRAINT IF EXISTS fk_tpr_linked_tenant;
ALTER TABLE common_company.trading_partner_registry
    ADD CONSTRAINT fk_tpr_linked_tenant
    FOREIGN KEY (linked_tenant_id)
    REFERENCES common_company.common_company(id) ON DELETE SET NULL;

-- Step 10: Delete common_tenant data (data lives in common_company)
-- TRUNCATE common_tenant.common_tenant CASCADE;
-- Or safer: DELETE FROM common_tenant.common_tenant;

-- Step 11: Update comments back
COMMENT ON TABLE common_company.common_company IS 
'Company entity - tenant or partner. tenant_id = company_id for root tenants.';

-- ═══════════════════════════════════════════════════════════════════════════
-- VERIFICATION
-- ═══════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    company_count INTEGER;
    user_orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO company_count FROM common_company.common_company;
    SELECT COUNT(*) INTO user_orphan_count 
    FROM common_user.common_user u
    WHERE NOT EXISTS (
        SELECT 1 FROM common_company.common_company c WHERE c.id = u.company_id
    );
    
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    RAISE NOTICE 'Rollback complete:';
    RAISE NOTICE '  - Companies: %', company_count;
    RAISE NOTICE '  - Orphaned users: %', user_orphan_count;
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    
    IF user_orphan_count > 0 THEN
        RAISE WARNING 'Found % users without matching company!', user_orphan_count;
    END IF;
END $$;

-- ═══════════════════════════════════════════════════════════════════════════
-- END OF ROLLBACK
-- ═══════════════════════════════════════════════════════════════════════════

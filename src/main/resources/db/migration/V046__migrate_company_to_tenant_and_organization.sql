-- ═══════════════════════════════════════════════════════════════════════════
-- V046__migrate_company_to_tenant_and_organization.sql
-- ═══════════════════════════════════════════════════════════════════════════
-- Migrates the tenant_id = company_id pattern to proper Tenant entity.
-- Renames Company → Organization for internal structure.
--
-- Steps:
--   1. Migrate root companies (tenant_id = company_id) to common_tenant
--   2. Add tenant_id FK column to common_company (references common_tenant)
--   3. Rename common_company → common_organization
--   4. Update CompanyType → OrganizationType (remove partner types)
--
-- Risk: MEDIUM - data migration required, but backward compatible
-- ═══════════════════════════════════════════════════════════════════════════

-- ============================================================================
-- STEP 1: Migrate root companies to common_tenant
-- ============================================================================
-- Root companies are identified by: tenant_id = id (self-referencing)
-- These become the Tenant entity entries

INSERT INTO common_tenant.common_tenant (
    id,
    uid,
    slug,
    name,
    billing_email,
    status,
    trial_ends_at,
    subscription_plan,
    settings,
    is_active,
    created_at,
    created_by,
    updated_at,
    updated_by,
    version
)
SELECT
    c.id,                                                          -- id (same as original company)
    c.uid,                                                         -- uid (e.g., ACME-001)
    LOWER(REGEXP_REPLACE(c.uid, '[^a-zA-Z0-9-]', '-', 'g')),      -- slug (from uid)
    c.company_name,                                                -- name
    c.email,                                                       -- billing_email (from deprecated field)
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM common_company.common_subscription s 
            WHERE s.tenant_id = c.id AND s.status = 'ACTIVE'
        ) THEN 'ACTIVE'
        WHEN EXISTS (
            SELECT 1 FROM common_company.common_subscription s 
            WHERE s.tenant_id = c.id AND s.status = 'TRIAL'
        ) THEN 'TRIAL'
        ELSE 'TRIAL'
    END,                                                           -- status (from subscription)
    (
        SELECT MAX(s.trial_ends_at) FROM common_company.common_subscription s 
        WHERE s.tenant_id = c.id AND s.status = 'TRIAL'
    ),                                                             -- trial_ends_at
    NULL,                                                          -- subscription_plan
    jsonb_build_object(
        'timezone', COALESCE(
            CASE 
                WHEN c.country = 'TR' OR c.country = 'Turkey' THEN 'Europe/Istanbul'
                ELSE 'UTC'
            END,
            'UTC'
        ),
        'locale', COALESCE(
            CASE 
                WHEN c.country = 'TR' OR c.country = 'Turkey' THEN 'tr-TR'
                ELSE 'en-US'
            END,
            'en-US'
        ),
        'currency', COALESCE(
            CASE 
                WHEN c.country = 'TR' OR c.country = 'Turkey' THEN 'TRY'
                ELSE 'USD'
            END,
            'USD'
        ),
        'country', NULLIF(c.country, ''),
        'betaFeaturesEnabled', false,
        'aiEnabled', true,
        'emailNotificationsEnabled', true,
        'mfaRequired', false,
        'sessionTimeoutMinutes', 480
    ),                                                             -- settings
    c.is_active,
    c.created_at,
    c.created_by,
    c.updated_at,
    c.updated_by,
    c.version
FROM common_company.common_company c
WHERE c.id = c.tenant_id;  -- Root companies only

-- Log migration
DO $$
DECLARE
    migrated_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO migrated_count FROM common_tenant.common_tenant;
    RAISE NOTICE 'Migrated % root companies to common_tenant', migrated_count;
END $$;

-- ============================================================================
-- STEP 2: Update common_company structure for Organization
-- ============================================================================

-- Add proper FK to tenant (replaces self-referencing hack)
-- For root companies: tenant_id already equals id (which is now in common_tenant)
-- For child companies: tenant_id points to root company id (which is now in common_tenant)

-- Add FK constraint to common_tenant
ALTER TABLE common_company.common_company
    ADD CONSTRAINT fk_company_tenant 
    FOREIGN KEY (tenant_id) 
    REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT;

-- ============================================================================
-- STEP 3: Update CompanyType to OrganizationType
-- ============================================================================
-- Remove partner types (SUPPLIER, SERVICE_PROVIDER, PARTNER, CUSTOMER categories)
-- These are now in TradingPartner.PartnerType

-- First, update any non-tenant types to VERTICAL_MILL (safe default)
-- In a clean system, non-tenant companies should not exist, but this handles edge cases
UPDATE common_company.common_company
SET company_type = 'VERTICAL_MILL'
WHERE company_type NOT IN (
    'SPINNER', 'WEAVER', 'KNITTER', 'DYER_FINISHER', 'VERTICAL_MILL', 'GARMENT_MANUFACTURER'
);

-- Log update
DO $$
DECLARE
    updated_count INTEGER;
BEGIN
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    IF updated_count > 0 THEN
        RAISE NOTICE 'Updated % companies with non-tenant types to VERTICAL_MILL', updated_count;
    END IF;
END $$;

-- ============================================================================
-- STEP 4: Rename table to common_organization
-- ============================================================================
ALTER TABLE common_company.common_company RENAME TO common_organization;

-- Update indexes
ALTER INDEX common_company.idx_company_tenant RENAME TO idx_organization_tenant;
ALTER INDEX common_company.idx_company_type RENAME TO idx_organization_type;
ALTER INDEX common_company.idx_company_tax_id RENAME TO idx_organization_tax_id;
ALTER INDEX common_company.idx_company_active RENAME TO idx_organization_active;

-- Update constraints
ALTER TABLE common_company.common_organization 
    RENAME CONSTRAINT uk_company_tenant_tax_id TO uk_organization_tenant_tax_id;
ALTER TABLE common_company.common_organization 
    RENAME CONSTRAINT fk_company_parent TO fk_organization_parent;
ALTER TABLE common_company.common_organization 
    RENAME CONSTRAINT fk_company_tenant TO fk_organization_tenant;

-- Rename column for clarity
ALTER TABLE common_company.common_organization 
    RENAME COLUMN company_name TO name;
ALTER TABLE common_company.common_organization 
    RENAME COLUMN company_type TO organization_type;
ALTER TABLE common_company.common_organization 
    RENAME COLUMN parent_company_id TO parent_organization_id;

-- ============================================================================
-- STEP 5: Update related junction tables
-- ============================================================================

-- Rename CompanyContact → OrganizationContact
ALTER TABLE common_company.common_company_contact RENAME TO common_organization_contact;
ALTER TABLE common_company.common_organization_contact 
    RENAME COLUMN company_id TO organization_id;

-- Rename CompanyAddress → OrganizationAddress
ALTER TABLE common_company.common_company_address RENAME TO common_organization_address;
ALTER TABLE common_company.common_organization_address 
    RENAME COLUMN company_id TO organization_id;

-- ============================================================================
-- STEP 6: Update comments
-- ============================================================================
COMMENT ON TABLE common_company.common_organization IS 
'Internal organizational structure - departments, hierarchy. Replaces common_company.';

COMMENT ON COLUMN common_company.common_organization.tenant_id IS 
'FK to common_tenant - replaces tenant_id = company_id hack';

COMMENT ON COLUMN common_company.common_organization.organization_type IS 
'Internal organization type: SPINNER, WEAVER, KNITTER, DYER_FINISHER, VERTICAL_MILL, GARMENT_MANUFACTURER';

COMMENT ON COLUMN common_company.common_organization.parent_organization_id IS 
'Parent organization for hierarchy (departments, branches)';

-- ============================================================================
-- STEP 7: Update FK references in User table
-- ============================================================================
-- User.company_id → organization_id (references common_organization)

-- First drop the old FK constraint
ALTER TABLE common_user.common_user 
    DROP CONSTRAINT IF EXISTS fk_user_company;

-- Rename column
ALTER TABLE common_user.common_user 
    RENAME COLUMN company_id TO organization_id;

-- Add new FK constraint to organization
ALTER TABLE common_user.common_user
    ADD CONSTRAINT fk_user_organization
    FOREIGN KEY (organization_id)
    REFERENCES common_company.common_organization(id) ON DELETE RESTRICT;

-- Update index (V003 created idx_user_tenant_company on (tenant_id, company_id); column renamed to organization_id above)
ALTER INDEX common_user.idx_user_tenant_company RENAME TO idx_user_tenant_organization;

COMMENT ON COLUMN common_user.common_user.organization_id IS 
'FK to Organization - the user belongs to this organization within the tenant';

-- ============================================================================
-- STEP 8: Update TradingPartnerRegistry FK to Tenant
-- ============================================================================
-- linked_tenant_id should reference common_tenant, not common_company

-- Drop old FK
ALTER TABLE common_company.trading_partner_registry
    DROP CONSTRAINT IF EXISTS fk_tpr_linked_tenant;

-- Add new FK to common_tenant
ALTER TABLE common_company.trading_partner_registry
    ADD CONSTRAINT fk_tpr_linked_tenant
    FOREIGN KEY (linked_tenant_id)
    REFERENCES common_tenant.common_tenant(id) ON DELETE SET NULL;

COMMENT ON COLUMN common_company.trading_partner_registry.linked_tenant_id IS 
'FK to Tenant - if partner is also a platform tenant, links to their Tenant record';

-- ============================================================================
-- STEP 9: Update TradingPartner FK to Tenant
-- ============================================================================
-- tenant_id should reference common_tenant

-- Drop old FK
ALTER TABLE common_company.common_trading_partner
    DROP CONSTRAINT IF EXISTS fk_tp_tenant;

-- Add new FK to common_tenant
ALTER TABLE common_company.common_trading_partner
    ADD CONSTRAINT fk_tp_tenant
    FOREIGN KEY (tenant_id)
    REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT;

COMMENT ON COLUMN common_company.common_trading_partner.tenant_id IS 
'FK to Tenant - the trading partner relationship belongs to this tenant';

-- ============================================================================
-- STEP 10: Update Department FK
-- ============================================================================
-- Department references organization (was company)

-- Drop old FK if exists
ALTER TABLE common_company.common_department
    DROP CONSTRAINT IF EXISTS fk_department_company;

-- Rename column if needed (check if already renamed)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'common_company' 
        AND table_name = 'common_department' 
        AND column_name = 'company_id'
    ) THEN
        ALTER TABLE common_company.common_department 
            RENAME COLUMN company_id TO organization_id;
    END IF;
END $$;

-- Add new FK to organization
ALTER TABLE common_company.common_department
    ADD CONSTRAINT fk_department_organization
    FOREIGN KEY (organization_id)
    REFERENCES common_company.common_organization(id) ON DELETE CASCADE;

-- ============================================================================
-- STEP 11: Update Role table FK (if exists)
-- ============================================================================
DO $$
BEGIN
    -- Check if common_role table exists and has company_id
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'common_user' 
        AND table_name = 'common_role' 
        AND column_name = 'company_id'
    ) THEN
        -- Drop old FK
        ALTER TABLE common_user.common_role
            DROP CONSTRAINT IF EXISTS fk_role_company;
        
        -- Rename column
        ALTER TABLE common_user.common_role 
            RENAME COLUMN company_id TO organization_id;
        
        -- Add new FK
        ALTER TABLE common_user.common_role
            ADD CONSTRAINT fk_role_organization
            FOREIGN KEY (organization_id)
            REFERENCES common_company.common_organization(id) ON DELETE CASCADE;
    END IF;
END $$;

-- ============================================================================
-- STEP 12: Update other tables with tenant_id FK to reference common_tenant
-- ============================================================================
-- These tables have tenant_id that previously referenced common_company

-- List of schemas and tables to update
DO $$
DECLARE
    r RECORD;
BEGIN
    -- Find all tables with tenant_id FK to common_company.common_company
    -- and update them to reference common_tenant.common_tenant
    FOR r IN 
        SELECT 
            tc.constraint_name,
            tc.table_schema,
            tc.table_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage ccu 
            ON tc.constraint_name = ccu.constraint_name
        WHERE tc.constraint_type = 'FOREIGN KEY'
        AND ccu.table_schema = 'common_company'
        AND ccu.table_name = 'common_organization'
        AND ccu.column_name = 'id'
        AND EXISTS (
            SELECT 1 FROM information_schema.key_column_usage kcu
            WHERE kcu.constraint_name = tc.constraint_name
            AND kcu.column_name = 'tenant_id'
        )
        -- Exclude already updated tables
        AND tc.table_name NOT IN ('common_organization', 'common_trading_partner')
    LOOP
        RAISE NOTICE 'Updating FK % on %.%', r.constraint_name, r.table_schema, r.table_name;
        
        -- Drop old FK
        EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT IF EXISTS %I',
            r.table_schema, r.table_name, r.constraint_name);
        
        -- Add new FK to common_tenant
        EXECUTE format('ALTER TABLE %I.%I ADD CONSTRAINT %I FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT',
            r.table_schema, r.table_name, 'fk_' || r.table_name || '_tenant');
    END LOOP;
END $$;

-- ============================================================================
-- VERIFICATION
-- ============================================================================
DO $$
DECLARE
    tenant_count INTEGER;
    org_count INTEGER;
    orphan_count INTEGER;
    user_orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO tenant_count FROM common_tenant.common_tenant;
    SELECT COUNT(*) INTO org_count FROM common_company.common_organization;
    SELECT COUNT(*) INTO orphan_count 
    FROM common_company.common_organization o
    WHERE NOT EXISTS (
        SELECT 1 FROM common_tenant.common_tenant t WHERE t.id = o.tenant_id
    );
    SELECT COUNT(*) INTO user_orphan_count 
    FROM common_user.common_user u
    WHERE NOT EXISTS (
        SELECT 1 FROM common_company.common_organization o WHERE o.id = u.organization_id
    );
    
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    RAISE NOTICE 'Migration complete:';
    RAISE NOTICE '  - Tenants: %', tenant_count;
    RAISE NOTICE '  - Organizations: %', org_count;
    RAISE NOTICE '  - Orphaned organizations: %', orphan_count;
    RAISE NOTICE '  - Orphaned users: %', user_orphan_count;
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    
    IF orphan_count > 0 THEN
        RAISE WARNING 'Found % organizations without matching tenant!', orphan_count;
    END IF;
    IF user_orphan_count > 0 THEN
        RAISE WARNING 'Found % users without matching organization!', user_orphan_count;
    END IF;
END $$;

-- ═══════════════════════════════════════════════════════════════════════════
-- END OF MIGRATION
-- ═══════════════════════════════════════════════════════════════════════════

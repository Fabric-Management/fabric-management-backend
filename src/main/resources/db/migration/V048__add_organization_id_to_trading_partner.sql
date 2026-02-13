-- ============================================================================
-- V048: Add organization_id to TradingPartner + OrganizationType update
-- ============================================================================
-- Links TradingPartner to a "partner Organization" for user management.
-- When external users need to be created for a trading partner, they are
-- assigned to this partner Organization (type = EXTERNAL_PARTNER).
--
-- Also updates the organization_type CHECK constraint to include the new
-- EXTERNAL_PARTNER type.
-- ============================================================================

-- ============================================================================
-- STEP 1: Add organization_id column to common_trading_partner
-- ============================================================================
ALTER TABLE common_company.common_trading_partner
    ADD COLUMN IF NOT EXISTS organization_id UUID;

-- FK constraint to common_organization
ALTER TABLE common_company.common_trading_partner
    ADD CONSTRAINT fk_tp_organization
    FOREIGN KEY (organization_id)
    REFERENCES common_company.common_organization(id) ON DELETE SET NULL;

-- Index for efficient lookups
CREATE INDEX IF NOT EXISTS idx_tp_organization
    ON common_company.common_trading_partner(organization_id)
    WHERE organization_id IS NOT NULL;

COMMENT ON COLUMN common_company.common_trading_partner.organization_id IS
'Linked partner Organization for user management. Auto-created when TradingPartner is registered. External users for this partner are linked to this Organization.';


-- ============================================================================
-- STEP 2: Update organization_type CHECK constraint (if exists)
-- ============================================================================
-- Drop existing CHECK constraint on organization_type if it exists,
-- and recreate with EXTERNAL_PARTNER included.
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    -- Find and drop any CHECK constraint on organization_type column
    SELECT con.conname INTO constraint_name
    FROM pg_catalog.pg_constraint con
    JOIN pg_catalog.pg_class rel ON rel.oid = con.conrelid
    JOIN pg_catalog.pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE nsp.nspname = 'common_company'
      AND rel.relname = 'common_organization'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) LIKE '%organization_type%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE common_company.common_organization DROP CONSTRAINT %I', constraint_name);
        RAISE NOTICE 'Dropped constraint: %', constraint_name;
    END IF;
END $$;

-- Add updated CHECK constraint including EXTERNAL_PARTNER
ALTER TABLE common_company.common_organization
    ADD CONSTRAINT chk_organization_type CHECK (
        organization_type IN (
            'SPINNER', 'WEAVER', 'KNITTER', 'DYER_FINISHER',
            'VERTICAL_MILL', 'GARMENT_MANUFACTURER', 'EXTERNAL_PARTNER'
        )
    );


-- ============================================================================
-- STEP 3: Backfill organization_id for existing TradingPartners
-- ============================================================================
-- For existing trading partners that don't have an organization_id,
-- create partner organizations and link them.
-- This is done via application code (TradingPartnerService) for new partners,
-- but existing partners need a migration-time backfill.
DO $$
DECLARE
    tp RECORD;
    new_org_id UUID;
    org_uid TEXT;
    effective_tax_id TEXT;
BEGIN
    FOR tp IN
        SELECT ctp.id, ctp.tenant_id, ctp.uid, ctp.custom_name,
               tpr.official_name, tpr.tax_id
        FROM common_company.common_trading_partner ctp
        JOIN common_company.trading_partner_registry tpr ON ctp.registry_id = tpr.id
        WHERE ctp.organization_id IS NULL
          AND ctp.is_active = true
    LOOP
        -- Generate effective tax ID (placeholder for partners without one)
        effective_tax_id := COALESCE(NULLIF(tp.tax_id, ''), 'TP-' || tp.uid);

        -- Check if partner org already exists for this tenant + tax_id
        SELECT id INTO new_org_id
        FROM common_company.common_organization
        WHERE tenant_id = tp.tenant_id
          AND tax_id = effective_tax_id
          AND is_active = true
        LIMIT 1;

        IF new_org_id IS NULL THEN
            -- Create the partner organization
            new_org_id := gen_random_uuid();
            org_uid := 'SYS-000-ORG-' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::text, '-', '') FROM 1 FOR 8));

            INSERT INTO common_company.common_organization (
                id, tenant_id, uid, name, tax_id, organization_type,
                parent_organization_id, is_active, created_at, updated_at, version
            ) VALUES (
                new_org_id,
                tp.tenant_id,
                org_uid,
                COALESCE(tp.custom_name, tp.official_name),
                effective_tax_id,
                'EXTERNAL_PARTNER',
                NULL,
                true,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP,
                0
            );
        END IF;

        -- Link trading partner to the organization
        UPDATE common_company.common_trading_partner
        SET organization_id = new_org_id, updated_at = CURRENT_TIMESTAMP
        WHERE id = tp.id;
    END LOOP;
END $$;


-- ============================================================================
-- STEP 4: Verification
-- ============================================================================
DO $$
DECLARE
    total_partners INTEGER;
    linked_partners INTEGER;
    unlinked_partners INTEGER;
    partner_orgs INTEGER;
BEGIN
    SELECT COUNT(*) INTO total_partners
    FROM common_company.common_trading_partner WHERE is_active = true;

    SELECT COUNT(*) INTO linked_partners
    FROM common_company.common_trading_partner
    WHERE is_active = true AND organization_id IS NOT NULL;

    unlinked_partners := total_partners - linked_partners;

    SELECT COUNT(*) INTO partner_orgs
    FROM common_company.common_organization
    WHERE organization_type = 'EXTERNAL_PARTNER' AND is_active = true;

    RAISE NOTICE 'V048 Migration Summary:';
    RAISE NOTICE '  Total active partners: %', total_partners;
    RAISE NOTICE '  Linked to organization: %', linked_partners;
    RAISE NOTICE '  Unlinked (inactive/error): %', unlinked_partners;
    RAISE NOTICE '  Partner organizations created: %', partner_orgs;
END $$;

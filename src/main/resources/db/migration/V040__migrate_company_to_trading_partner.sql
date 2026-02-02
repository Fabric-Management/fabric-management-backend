-- ═══════════════════════════════════════════════════════════════════════════
-- V040: Migrate Company to TradingPartner
-- ═══════════════════════════════════════════════════════════════════════════
-- Migrates existing partner-type Company records to TradingPartner tables.
-- Preserves legacy_company_id for FK traceability during transition.
--
-- Migration Rules:
-- 1. Companies with same tax_id share a single TradingPartnerRegistry
-- 2. Companies without tax_id get individual registry records (no deduplication)
-- 3. legacy_company_id preserves link for existing FK references
-- 4. Default country is 'TUR' (Turkey) - update if Company has country data
--
-- Partner Type Mapping:
-- - *_SUPPLIER → SUPPLIER
-- - CUSTOMER → CUSTOMER
-- - FASON → FASON
-- - Others (SERVICE_PROVIDER, PARTNER) → SERVICE_PROVIDER
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
DECLARE
    rec RECORD;
    new_registry_id UUID;
    new_tp_id UUID;
    partner_uid VARCHAR(100);
    registry_uid VARCHAR(100);
    null_tax_counter INT := 0;
    migrated_count INT := 0;
    registry_created_count INT := 0;
    registry_reused_count INT := 0;
    skipped_count INT := 0;
    default_country VARCHAR(3) := 'TUR';  -- Default country for migration
BEGIN
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    RAISE NOTICE 'V040: Starting TradingPartner migration from Company...';
    RAISE NOTICE 'Default country: %', default_country;
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    
    -- Partner-type Company'leri bul (CompanyCategory != TENANT)
    FOR rec IN 
        SELECT 
            c.id, 
            c.tenant_id, 
            c.uid, 
            c.company_name, 
            c.tax_id, 
            c.company_type, 
            c.is_active, 
            c.created_at, 
            c.created_by
        FROM common_company.common_company c
        WHERE c.company_type IN (
            -- Suppliers (CompanyCategory.SUPPLIER)
            'FIBER_SUPPLIER', 'YARN_SUPPLIER', 'CHEMICAL_SUPPLIER',
            'CONSUMABLE_SUPPLIER', 'PACKAGING_SUPPLIER', 'MACHINE_SUPPLIER',
            -- Service Providers (CompanyCategory.SERVICE_PROVIDER)
            'LOGISTICS_PROVIDER', 'MAINTENANCE_SERVICE', 'IT_SERVICE_PROVIDER',
            'KITCHEN_SUPPLIER', 'HR_SERVICE_PROVIDER', 'LAB', 'UTILITY_PROVIDER',
            -- Partners (CompanyCategory.PARTNER)
            'FASON', 'AGENT', 'TRADER', 'FINANCE_PARTNER',
            -- Customers (CompanyCategory.CUSTOMER)
            'CUSTOMER'
        )
        ORDER BY c.created_at ASC  -- Process oldest first for consistent UIDs
    LOOP
        new_registry_id := NULL;
        
        -- ═══════════════════════════════════════════════════════════════════
        -- CASE 1: tax_id VAR → Registry'de ara veya oluştur (DEDUPLICATION)
        -- ═══════════════════════════════════════════════════════════════════
        IF rec.tax_id IS NOT NULL AND TRIM(rec.tax_id) != '' THEN
            -- Mevcut registry var mı?
            SELECT id INTO new_registry_id
            FROM common_company.trading_partner_registry
            WHERE tax_id = TRIM(rec.tax_id) AND country = default_country;
            
            IF new_registry_id IS NULL THEN
                -- Yeni registry oluştur (UUID suffix for collision safety)
                new_registry_id := gen_random_uuid();
                registry_uid := 'REG-' || 
                    UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', ''), 1, 8));
                
                INSERT INTO common_company.trading_partner_registry 
                    (id, uid, tax_id, official_name, country, verified_status, created_at, updated_at)
                VALUES 
                    (new_registry_id, 
                     registry_uid, 
                     TRIM(rec.tax_id), 
                     rec.company_name, 
                     default_country, 
                     'UNVERIFIED', 
                     rec.created_at,
                     CURRENT_TIMESTAMP);
                
                registry_created_count := registry_created_count + 1;
                RAISE NOTICE '[Registry] Created: uid=%, tax_id=%, name=%', 
                    registry_uid, rec.tax_id, rec.company_name;
            ELSE
                registry_reused_count := registry_reused_count + 1;
                RAISE NOTICE '[Registry] Reused for tax_id=%', rec.tax_id;
            END IF;
            
        -- ═══════════════════════════════════════════════════════════════════
        -- CASE 2: tax_id YOK → Her biri ayrı registry (NO DEDUPLICATION)
        -- ═══════════════════════════════════════════════════════════════════
        ELSE
            new_registry_id := gen_random_uuid();
            null_tax_counter := null_tax_counter + 1;
            registry_uid := 'REG-NOTAX-' || 
                UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', ''), 1, 8));
            
            INSERT INTO common_company.trading_partner_registry 
                (id, uid, tax_id, official_name, country, verified_status, created_at, updated_at)
            VALUES 
                (new_registry_id, 
                 registry_uid, 
                 NULL, 
                 rec.company_name, 
                 default_country, 
                 'UNVERIFIED', 
                 rec.created_at,
                 CURRENT_TIMESTAMP);
            
            registry_created_count := registry_created_count + 1;
            RAISE NOTICE '[Registry] Created (NO tax_id): uid=%, name=%', 
                registry_uid, rec.company_name;
        END IF;
        
        -- ═══════════════════════════════════════════════════════════════════
        -- TradingPartner oluştur (tenant + registry unique)
        -- ═══════════════════════════════════════════════════════════════════
        IF NOT EXISTS (
            SELECT 1 FROM common_company.common_trading_partner
            WHERE tenant_id = rec.tenant_id AND registry_id = new_registry_id
        ) THEN
            new_tp_id := gen_random_uuid();
            -- UID: TP-{8 char UUID} for uniqueness
            partner_uid := 'TP-' || 
                UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', ''), 1, 12));
            
            INSERT INTO common_company.common_trading_partner
                (id, tenant_id, uid, registry_id, custom_name, partner_type, 
                 status, legacy_company_id, is_active, created_at, created_by, updated_at)
            VALUES
                (new_tp_id, 
                 rec.tenant_id, 
                 partner_uid, 
                 new_registry_id, 
                 rec.company_name,  -- Preserve original name as custom_name
                 -- Partner type mapping
                 CASE 
                     WHEN rec.company_type IN (
                         'FIBER_SUPPLIER', 'YARN_SUPPLIER', 'CHEMICAL_SUPPLIER',
                         'CONSUMABLE_SUPPLIER', 'PACKAGING_SUPPLIER', 'MACHINE_SUPPLIER'
                     ) THEN 'SUPPLIER'
                     WHEN rec.company_type = 'CUSTOMER' THEN 'CUSTOMER'
                     WHEN rec.company_type = 'FASON' THEN 'FASON'
                     ELSE 'SERVICE_PROVIDER'
                 END,
                 'ACTIVE',
                 rec.id,  -- legacy_company_id ← Migration traceability
                 rec.is_active,
                 rec.created_at,
                 rec.created_by,
                 CURRENT_TIMESTAMP);
            
            migrated_count := migrated_count + 1;
            RAISE NOTICE '[Partner] Created: uid=%, type=%, legacy_id=%', 
                partner_uid,
                CASE 
                    WHEN rec.company_type IN (
                        'FIBER_SUPPLIER', 'YARN_SUPPLIER', 'CHEMICAL_SUPPLIER',
                        'CONSUMABLE_SUPPLIER', 'PACKAGING_SUPPLIER', 'MACHINE_SUPPLIER'
                    ) THEN 'SUPPLIER'
                    WHEN rec.company_type = 'CUSTOMER' THEN 'CUSTOMER'
                    WHEN rec.company_type = 'FASON' THEN 'FASON'
                    ELSE 'SERVICE_PROVIDER'
                END,
                rec.id;
        ELSE
            -- Aynı tenant + registry için kayıt zaten var
            -- Bu durumda partner_type BOTH'a upgrade edilebilir (future enhancement)
            skipped_count := skipped_count + 1;
            RAISE NOTICE '[Partner] Skipped: tenant=% already has registry=%', 
                rec.tenant_id, new_registry_id;
        END IF;
    END LOOP;
    
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
    RAISE NOTICE 'Migration Summary:';
    RAISE NOTICE '  - Registry created:  %', registry_created_count;
    RAISE NOTICE '  - Registry reused:   %', registry_reused_count;
    RAISE NOTICE '  - Partners migrated: %', migrated_count;
    RAISE NOTICE '  - Partners skipped:  %', skipped_count;
    RAISE NOTICE '  - NULL tax_id count: %', null_tax_counter;
    RAISE NOTICE '════════════════════════════════════════════════════════════════════════';
END $$;


-- ═══════════════════════════════════════════════════════════════════════════
-- Verification Queries (run manually to verify migration)
-- ═══════════════════════════════════════════════════════════════════════════

-- Count totals
-- SELECT 'trading_partner_registry' AS table_name, COUNT(*) AS count FROM common_company.trading_partner_registry
-- UNION ALL
-- SELECT 'common_trading_partner', COUNT(*) FROM common_company.common_trading_partner;

-- Check deduplication (registries with multiple partners)
-- SELECT r.tax_id, r.official_name, COUNT(tp.id) AS partner_count
-- FROM common_company.trading_partner_registry r
-- JOIN common_company.common_trading_partner tp ON tp.registry_id = r.id
-- WHERE r.tax_id IS NOT NULL
-- GROUP BY r.tax_id, r.official_name
-- HAVING COUNT(tp.id) > 1
-- ORDER BY partner_count DESC;

-- View sample data
-- SELECT * FROM common_company.v_partner_legacy_mapping LIMIT 20;

-- Check legacy mapping coverage
-- SELECT 
--     COUNT(*) AS total_partners,
--     COUNT(legacy_company_id) AS with_legacy_id,
--     COUNT(*) - COUNT(legacy_company_id) AS new_partners
-- FROM common_company.common_trading_partner;

-- ============================================================================
-- R001: Seed Canonical 100% Pure Fiber Entities (Repeatable) — PF4
-- ============================================================================
-- product_id_var: gen_random_uuid() sadece IF product_id_var IS NULL bloğunda
-- is_official_iso = true: Only official ISO 2076 codes (52 records)
--
-- Creates Product + Fiber for each ISO code
-- TEMPLATE_TENANT_ID: canonical platform fibers visible to all tenants
-- via RLS shared-read carve-out. Users create BLENDED fibers using
-- these canonical fiber compositions.
--
-- Repeatable Migration: Runs every time Flyway checks migrations
-- Depends on: V008 (fiber reference tables)
-- ============================================================================

DO $$
DECLARE
    iso_record      RECORD;
    product_id_var UUID;
    category_id_var UUID;
    fiber_counter   INTEGER := 1;
BEGIN
    FOR iso_record IN
        SELECT id, iso_code, fiber_name, fiber_type
        FROM production.prod_fiber_iso_code
        WHERE is_active = true
        AND is_official_iso = true
        ORDER BY id
    LOOP
        -- Get category_id based on fiber_type (required for Fiber entity)
        SELECT id INTO category_id_var
        FROM production.prod_fiber_category
        WHERE category_code = iso_record.fiber_type
        LIMIT 1;

        -- Skip if no matching category (e.g. fiber_type typo or missing)
        IF category_id_var IS NULL THEN
            RAISE WARNING 'Skipping ISO code % (fiber_type=%) - no matching prod_fiber_category',
                iso_record.iso_code, iso_record.fiber_type;
            CONTINUE;
        END IF;

        -- Check if product already exists
        SELECT id INTO product_id_var
        FROM production.prod_product
        WHERE uid = 'SYS-MAT-' || LPAD(fiber_counter::TEXT, 6, '0');

        -- Insert Product if not exists
        IF product_id_var IS NULL THEN
            product_id_var := gen_random_uuid();
            INSERT INTO production.prod_product
                (id, tenant_id, uid, product_type, unit,
                 is_active, created_at, updated_at, version)
            VALUES
                (product_id_var,
                 '00000000-0000-0000-ffff-000000000001',
                 'SYS-MAT-' || LPAD(fiber_counter::TEXT, 6, '0'),
                 'FIBER', 'KG', true,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
        END IF;

        -- Insert or update Fiber (pure fiber: composition = empty JSONB)
        INSERT INTO production.prod_fiber
            (tenant_id, uid, product_id, fiber_category_id,
             fiber_iso_code_id, fiber_name, status, composition,
             is_active, created_at, updated_at, version)
        VALUES
            ('00000000-0000-0000-ffff-000000000001',
             'SYS-FIB-' || LPAD(fiber_counter::TEXT, 6, '0'),
             product_id_var,
             category_id_var,
             iso_record.id,
             iso_record.fiber_name || ' (100%)',
             'ACTIVE', '{}'::jsonb,
             true,
             CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        ON CONFLICT (uid) DO UPDATE SET
            fiber_name        = EXCLUDED.fiber_name,
            status            = EXCLUDED.status,
            fiber_category_id = EXCLUDED.fiber_category_id,
            fiber_iso_code_id = EXCLUDED.fiber_iso_code_id,
            composition       = EXCLUDED.composition,
            updated_at        = CURRENT_TIMESTAMP;
        
        fiber_counter := fiber_counter + 1;
    END LOOP;
END $$;


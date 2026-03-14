-- ============================================================================
-- R001: Seed System 100% Pure Fiber Entities (Repeatable) — Kural 6
-- ============================================================================
-- material_id_var: gen_random_uuid() sadece IF material_id_var IS NULL bloğunda
-- is_official_iso = true: Only official ISO 2076 codes (52 records)
--
-- Creates Material + Fiber for each ISO code
-- SYSTEM_TENANT_ID so all users can access system fibers
-- Users create BLENDED fibers using system fiber compositions
--
-- Repeatable Migration: Runs every time Flyway checks migrations
-- Depends on: V008 (fiber reference tables)
-- ============================================================================

DO $$
DECLARE
    iso_record      RECORD;
    material_id_var UUID;
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

        -- Check if material already exists
        SELECT id INTO material_id_var
        FROM production.prod_material
        WHERE uid = 'SYS-MAT-' || LPAD(fiber_counter::TEXT, 6, '0');

        -- Insert Material if not exists
        IF material_id_var IS NULL THEN
            material_id_var := gen_random_uuid();
            INSERT INTO production.prod_material
                (id, tenant_id, uid, material_type, unit,
                 is_active, created_at, updated_at, version)
            VALUES
                (material_id_var,
                 '00000000-0000-0000-0000-000000000000',
                 'SYS-MAT-' || LPAD(fiber_counter::TEXT, 6, '0'),
                 'FIBER', 'KG', true,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
        END IF;

        -- Insert or update Fiber (pure fiber: composition = empty JSONB)
        INSERT INTO production.prod_fiber
            (tenant_id, uid, material_id, fiber_category_id,
             fiber_iso_code_id, fiber_name, status, composition,
             is_active, created_at, updated_at, version)
        VALUES
            ('00000000-0000-0000-0000-000000000000',
             'SYS-FIB-' || LPAD(fiber_counter::TEXT, 6, '0'),
             material_id_var,
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


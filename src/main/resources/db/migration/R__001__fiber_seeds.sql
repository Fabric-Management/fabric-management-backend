-- ============================================================================
-- R001: Seed System 100% Pure Fiber Entities (Repeatable)
-- ============================================================================
-- Creates Material + Fiber entities for each ISO code
-- SYSTEM_TENANT_ID so all users can access system fibers
-- Users create BLENDED fibers using system fiber compositions
-- 
-- Repeatable Migration: Runs every time Flyway checks migrations
-- If content changes, Flyway will re-execute this script
-- 
-- Last Updated: 2025-11-13
-- ============================================================================

-- Create Material + Fiber entities for each ISO code
DO $$
DECLARE
    iso_record RECORD;
    material_id_var UUID;
    category_id_var UUID;
    fiber_counter INTEGER := 1;
BEGIN
    -- For each ISO code, create a system Material + Fiber entity
    FOR iso_record IN 
        SELECT id, iso_code, fiber_name, fiber_type 
        FROM production.prod_fiber_iso_code 
        WHERE is_active = true
        ORDER BY id
    LOOP
        -- Get category_id based on fiber_type
        SELECT id INTO category_id_var FROM production.prod_fiber_category 
        WHERE category_code = iso_record.fiber_type 
        LIMIT 1;
        
        -- Generate material_id
        material_id_var := gen_random_uuid();
        
        -- Check if material already exists
        SELECT id INTO material_id_var FROM production.prod_material 
        WHERE uid = 'SYS-MAT-' || LPAD(fiber_counter::TEXT, 6, '0');
        
        -- Insert Material if not exists
        IF material_id_var IS NULL THEN
            material_id_var := gen_random_uuid();
            INSERT INTO production.prod_material (
                id,
                tenant_id,
                uid,
                material_type,
                unit,
                is_active,
                created_at,
                updated_at,
                version
            ) VALUES (
                material_id_var,
                '00000000-0000-0000-0000-000000000000',
                'SYS-MAT-' || LPAD(fiber_counter::TEXT, 6, '0'),
                'FIBER',
                'KG',
                true,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP,
                0
            );
        END IF;
        
        -- Insert or update Fiber
        INSERT INTO production.prod_fiber (
            tenant_id,
            uid,
            material_id,
            fiber_category_id,
            fiber_iso_code_id,
            fiber_name,
            fiber_grade,
            status,
            is_active,
            created_at,
            updated_at,
            version
        ) VALUES (
            '00000000-0000-0000-0000-000000000000',
            'SYS-FIB-' || LPAD(fiber_counter::TEXT, 6, '0'),
            material_id_var,
            category_id_var,
            iso_record.id,
            iso_record.fiber_name || ' (100%)',
            'STANDARD',
            'ACTIVE',
            true,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            0
        )
        ON CONFLICT (uid) DO UPDATE SET
            fiber_name = EXCLUDED.fiber_name,
            fiber_grade = EXCLUDED.fiber_grade,
            status = EXCLUDED.status,
            fiber_category_id = EXCLUDED.fiber_category_id,
            fiber_iso_code_id = EXCLUDED.fiber_iso_code_id,
            updated_at = CURRENT_TIMESTAMP;
        
        fiber_counter := fiber_counter + 1;
    END LOOP;
END $$;


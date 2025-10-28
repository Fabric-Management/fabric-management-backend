-- =====================================================
-- SEED: System 100% Pure Fiber Entities
-- =====================================================
-- Create Material + Fiber entities for each ISO code
-- SYSTEM_TENANT_ID so all users can access system fibers
-- Users create BLENDED fibers using system fiber compositions

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
        
        -- Insert Material (minimal structure)
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
        
        -- Insert Fiber
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
            'NEW',
            true,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            0
        );
        
        fiber_counter := fiber_counter + 1;
    END LOOP;
END $$;

COMMENT ON TABLE production.prod_fiber IS 'Includes default 100% pure fibers (SYSTEM_TENANT_ID) accessible by all tenants. Users create BLENDED fibers only.';
COMMENT ON COLUMN production.prod_fiber.tenant_id IS 'SYSTEM_TENANT_ID for default fibers, actual tenant_id for user-created blends';


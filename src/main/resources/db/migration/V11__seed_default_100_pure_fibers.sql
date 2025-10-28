-- =====================================================
-- SEED: Default 100% Pure Fiber Entities
-- =====================================================
-- Create Material + Fiber entities for each ISO code
-- SYSTEM_TENANT_ID so all users can access these default fibers
-- Users can only create BLENDED fibers, not pure 100% fibers

-- Helper function to generate UIDs
DO $$
DECLARE
    iso_code_record RECORD;
    material_id_var UUID;
    fiber_id_var UUID;
    category_id_var UUID;
    fiber_code_counter INTEGER := 1;
BEGIN
    -- For each ISO code, create a default Material + Fiber entity
    FOR iso_code_record IN 
        SELECT id, iso_code, fiber_name, fiber_type, description 
        FROM production.prod_fiber_iso_code 
        WHERE is_active = true
        ORDER BY id
    LOOP
        -- Get category_id based on fiber_type (handle 'TECHNICAL_ADVANCED' mapping)
        SELECT 
            CASE 
                WHEN iso_code_record.fiber_type IN ('NATURAL_PLANT', 'NATURAL_ANIMAL') THEN (SELECT id FROM production.prod_fiber_category WHERE category_code = iso_code_record.fiber_type LIMIT 1)
                WHEN iso_code_record.fiber_type = 'REGENERATED_CELLULOSIC' THEN (SELECT id FROM production.prod_fiber_category WHERE category_code = 'REGENERATED_CELLULOSIC' LIMIT 1)
                WHEN iso_code_record.fiber_type = 'SYNTHETIC_POLYMER' THEN (SELECT id FROM production.prod_fiber_category WHERE category_code = 'SYNTHETIC_POLYMER' LIMIT 1)
                ELSE (SELECT id FROM production.prod_fiber_category WHERE category_code = 'TECHNICAL_ADVANCED' LIMIT 1)
            END 
        INTO category_id_var;
        
        -- Generate material_id
        material_id_var := gen_random_uuid();
        
        -- Insert into prod_material
        INSERT INTO production.prod_material (
            id,
            tenant_id,
            uid,
            material_code,
            material_name,
            material_type,
            unit,
            description,
            is_active,
            created_at,
            updated_at,
            version
        ) VALUES (
            material_id_var,
            '00000000-0000-0000-0000-000000000000',
            'SYS-MAT-' || LPAD(fiber_code_counter::TEXT, 6, '0'),
            'DEFAULT-' || iso_code_record.iso_code,
            'Default ' || iso_code_record.fiber_name || ' Fiber',
            'FIBER',
            'KG',
            'Default 100% ' || iso_code_record.fiber_name || ' fiber available to all tenants',
            true,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            0
        );
        
        -- Insert into prod_fiber
        INSERT INTO production.prod_fiber (
            tenant_id,
            uid,
            material_id,
            fiber_category_id,
            fiber_iso_code_id,
            fiber_code,
            fiber_name,
            fiber_grade,
            fineness,
            length_mm,
            strength_cn_dtex,
            elongation_percent,
            status,
            is_active,
            created_at,
            updated_at,
            version
        ) VALUES (
            '00000000-0000-0000-0000-000000000000',
            'SYS-FIB-' || LPAD(fiber_code_counter::TEXT, 6, '0'),
            material_id_var,
            category_id_var,
            iso_code_record.id,
            'DEFAULT-' || iso_code_record.iso_code,
            'Default ' || iso_code_record.fiber_name || ' (100%)',
            'STANDARD',
            NULL,
            NULL,
            NULL,
            NULL,
            'NEW',
            true,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            0
        );
        
        fiber_code_counter := fiber_code_counter + 1;
    END LOOP;
END $$;

COMMENT ON TABLE production.prod_fiber IS 'Includes default 100% pure fibers (SYSTEM_TENANT_ID) accessible by all tenants. Users create BLENDED fibers only.';
COMMENT ON COLUMN production.prod_fiber.tenant_id IS 'SYSTEM_TENANT_ID for default fibers, actual tenant_id for user-created blends';


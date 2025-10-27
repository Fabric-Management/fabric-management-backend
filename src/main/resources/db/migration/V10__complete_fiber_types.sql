-- =====================================================
-- V10: COMPLETE FIBER TYPES
-- =====================================================
-- Purpose: Add missing fiber types for type safety
-- Date: 2025-10-27
-- =====================================================

-- =====================================================
-- Missing Natural Fibers (Plant-Based)
-- =====================================================

INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00066', 'KAP', 'Kapok', 'NATURAL_PLANT', 'Kapok ağacından dolgu lifi', TRUE, 66),
    ('SYS-000-FISO-00067', 'KEN', 'Kenaf', 'NATURAL_PLANT', 'Hibiscus cannabinus bitkisinden', TRUE, 67),
    ('SYS-000-FISO-00068', 'ROS', 'Roselle', 'NATURAL_PLANT', 'Hibiscus sabdariffa bitkisinden', TRUE, 68);

-- =====================================================
-- Missing Protein-Based Regenerated Fibers
-- =====================================================

INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00069', 'SOY', 'Soy Fiber', 'REGENERATED_PROTEIN', 'Soya proteini', FALSE, 69),
    ('SYS-000-FISO-00070', 'MC', 'Milk Fiber', 'REGENERATED_PROTEIN', 'Süt proteini (Casein)', FALSE, 70),
    ('SYS-000-FISO-00071', 'CHITIN', 'Chitin Fiber', 'REGENERATED_PROTEIN', 'Kabuklu deniz canlılarından', FALSE, 71);

-- =====================================================
-- Missing Advanced/Special Fibers
-- =====================================================

INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00072', 'NF', 'Nanofiber', 'TECHNICAL_ADVANCED', 'Nanoteknolojiyle üretilmiş ultra ince lif', FALSE, 72),
    ('SYS-000-FISO-00073', 'GRF', 'Graphene Fiber', 'TECHNICAL_ADVANCED', 'İletken ve dayanıklı karbon fiber türü', FALSE, 73),
    ('SYS-000-FISO-00074', 'CEF', 'Ceramic Fiber', 'TECHNICAL_ADVANCED', 'Isıya dayanıklı seramik bazlı', FALSE, 74);

-- =====================================================
-- Missing Recycled Fibers (rPP)
-- =====================================================

INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00075', 'rPP', 'Recycled Polypropylene', 'SYNTHETIC_POLYMER', 'Industrial waste', FALSE, 75);

-- =====================================================
-- COMPLETED: V10 - COMPLETE FIBER TYPES
-- =====================================================


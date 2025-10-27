-- =====================================================
-- V8: FIBER REFERENCE TABLES
-- =====================================================
-- Purpose: Read-only reference tables for fiber categories, attributes, certifications
-- Date: 2025-10-27
-- =====================================================

-- Create schema
CREATE SCHEMA IF NOT EXISTS production;

-- =====================================================
-- FIBER REFERENCE TABLES (Platform-Level)
-- =====================================================
-- System-defined fiber categories, attributes, certifications, and ISO codes
-- Accessible by all tenants (SYSTEM_TENANT_ID = '00000000-0000-0000-0000-000000000000')

-- =====================================================
-- Fiber Category (Read-Only Reference)
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_fiber_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) UNIQUE NOT NULL DEFAULT 'SYS-000-FCAT-00000',
    
    category_code VARCHAR(50) UNIQUE NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INTEGER,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_category_code ON production.prod_fiber_category(category_code);
CREATE INDEX idx_fiber_category_active ON production.prod_fiber_category(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.prod_fiber_category IS 'Fiber categories (NATURAL_PLANT, NATURAL_ANIMAL, etc.) - System-defined, tenant-independent';
COMMENT ON COLUMN production.prod_fiber_category.tenant_id IS 'Always SYSTEM_TENANT_ID for platform-level reference data';

-- Seed Categories
INSERT INTO production.prod_fiber_category 
    (tenant_id, uid, category_code, category_name, description, display_order) 
VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00001', 'NATURAL_PLANT', 'Natural Plant Fibers', 'Cotton, Linen, Hemp, Jute, etc.', 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00002', 'NATURAL_ANIMAL', 'Natural Animal Fibers', 'Wool, Silk, Cashmere, Alpaca, etc.', 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00003', 'REGENERATED_CELLULOSIC', 'Regenerated Cellulosic Fibers', 'Viscose, Modal, Lyocell, Acetate, etc.', 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00004', 'REGENERATED_PROTEIN', 'Regenerated Protein Fibers', 'Soy, Milk, Chitin, etc.', 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00005', 'SYNTHETIC_POLYMER', 'Synthetic Polymer Fibers', 'Polyester, Nylon, Polypropylene, etc.', 5),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00006', 'TECHNICAL_ADVANCED', 'Technical & Advanced Fibers', 'Carbon, Aramid, Ceramic, etc.', 6),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00007', 'MINERAL', 'Mineral Fibers', 'Asbestos, Glass Fiber, etc.', 7);

-- =====================================================
-- Fiber Attribute (Read-Only Reference)
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_fiber_attribute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) UNIQUE NOT NULL DEFAULT 'SYS-000-FATR-00000',
    
    attribute_code VARCHAR(50) UNIQUE NOT NULL,
    attribute_name VARCHAR(100) NOT NULL,
    attribute_group VARCHAR(50),
    description TEXT,
    display_order INTEGER,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_attribute_code ON production.prod_fiber_attribute(attribute_code);
CREATE INDEX idx_fiber_attribute_active ON production.prod_fiber_attribute(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.prod_fiber_attribute IS 'Fiber attributes (durable, biodegradable, hydrophobic, etc.) - System-defined, tenant-independent';
COMMENT ON COLUMN production.prod_fiber_attribute.tenant_id IS 'Always SYSTEM_TENANT_ID for platform-level reference data';

-- Seed Attributes
INSERT INTO production.prod_fiber_attribute 
    (tenant_id, uid, attribute_code, attribute_name, attribute_group, description, display_order) 
VALUES
    -- Physical Properties
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00001', 'DURABLE', 'Durable', 'PHYSICAL', 'Long-lasting fiber', 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00002', 'STRONG', 'Strong', 'PHYSICAL', 'High tensile strength', 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00003', 'ELASTIC', 'Elastic', 'PHYSICAL', 'High elongation', 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00004', 'MOISTURE_ABSORBENT', 'Moisture Absorbent', 'PHYSICAL', 'Good water absorption', 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00005', 'WICKING', 'Moisture Wicking', 'PHYSICAL', 'Transports moisture away', 5),
    -- Chemical Properties
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00006', 'HYDROPHOBIC', 'Hydrophobic', 'CHEMICAL', 'Water-repellent', 6),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00007', 'OLEOPHOBIC', 'Oleophobic', 'CHEMICAL', 'Oil-repellent', 7),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00008', 'FLAME_RESISTANT', 'Flame Resistant', 'CHEMICAL', 'Resists ignition', 8),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00009', 'UV_RESISTANT', 'UV Resistant', 'CHEMICAL', 'Resists UV degradation', 9),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00010', 'CHEMICAL_RESISTANT', 'Chemical Resistant', 'CHEMICAL', 'Resists chemical damage', 10),
    -- Environmental Properties
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00011', 'BIODEGRADABLE', 'Biodegradable', 'ENVIRONMENTAL', 'Naturally decomposes', 11),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00012', 'RECYCLABLE', 'Recyclable', 'ENVIRONMENTAL', 'Can be recycled', 12),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00013', 'RENEWABLE', 'Renewable', 'ENVIRONMENTAL', 'From renewable sources', 13),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00014', 'ORGANIC', 'Organic', 'ENVIRONMENTAL', 'Certified organic', 14);

-- =====================================================
-- Fiber Certification (Read-Only Reference)
-- =====================================================
CREATE TABLE IF NOT EXISTS production.prod_fiber_certification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) UNIQUE NOT NULL DEFAULT 'SYS-000-FCER-00000',
    
    certification_code VARCHAR(50) UNIQUE NOT NULL,
    certification_name VARCHAR(100) NOT NULL,
    certifying_body VARCHAR(100),
    description TEXT,
    display_order INTEGER,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_certification_code ON production.prod_fiber_certification(certification_code);
CREATE INDEX idx_fiber_certification_active ON production.prod_fiber_certification(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.prod_fiber_certification IS 'Fiber certifications (GOTS, OEKO-TEX, BCI, etc.) - System-defined, tenant-independent';
COMMENT ON COLUMN production.prod_fiber_certification.tenant_id IS 'Always SYSTEM_TENANT_ID for platform-level reference data';

-- Seed Certifications
INSERT INTO production.prod_fiber_certification 
    (tenant_id, uid, certification_code, certification_name, certifying_body, description, display_order) 
VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00001', 'GOTS', 'Global Organic Textile Standard', 'Global Standard gGmbH', 'Organic textile certification', 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00002', 'BCI', 'Better Cotton Initiative', 'BCI', 'Sustainable cotton', 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00003', 'OEKO_TEX_100', 'OEKO-TEX Standard 100', 'OEKO-TEX', 'Harmful substance testing', 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00004', 'GRS', 'Global Recycled Standard', 'Textile Exchange', 'Recycled content', 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00005', 'FSC', 'Forest Stewardship Council', 'FSC', 'Responsible forest management', 5),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00006', 'OBCS', 'Organic Blended Content Standard', 'Textile Exchange', 'Organic blend certification', 6);

-- =====================================================
-- Fiber ISO Code (Read-Only Reference)
-- =====================================================
-- PLAFORM-LEVEL: All standard 100% fiber types accessible by all tenants

CREATE TABLE IF NOT EXISTS production.prod_fiber_iso_code (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) UNIQUE NOT NULL DEFAULT 'SYS-000-FISO-00000',
    
    iso_code VARCHAR(10) UNIQUE NOT NULL,
    fiber_name VARCHAR(255) NOT NULL,
    fiber_type VARCHAR(100),
    description TEXT,
    is_official_iso BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER,
    
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fiber_iso_code ON production.prod_fiber_iso_code(iso_code);
CREATE INDEX idx_fiber_iso_active ON production.prod_fiber_iso_code(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE production.prod_fiber_iso_code IS 'Fiber ISO 2076 codes - Platform-level, tenant-independent. Standard 100% fiber types accessible by all tenants.';
COMMENT ON COLUMN production.prod_fiber_iso_code.tenant_id IS 'Always SYSTEM_TENANT_ID (00000000-0000-0000-0000-000000000000) for platform-level reference data';
COMMENT ON COLUMN production.prod_fiber_iso_code.iso_code IS 'ISO 2076 official codes (CO, PES, PA, etc.)';

-- Seed ISO Codes (Natural Fibers)
INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00001', 'CO', 'Cotton', 'NATURAL_PLANT', 'Standard abbreviation for cotton', TRUE, 1),
    ('SYS-000-FISO-00002', 'LI', 'Linen', 'NATURAL_PLANT', 'Linen / flax; standard ISO abbreviation', TRUE, 2),
    ('SYS-000-FISO-00003', 'HA', 'Hemp', 'NATURAL_PLANT', 'Hemp; often seen in sustainability reports', TRUE, 3),
    ('SYS-000-FISO-00004', 'JU', 'Jute', 'NATURAL_PLANT', 'Jute; typically used in coarse fabrics', TRUE, 4),
    ('SYS-000-FISO-00005', 'RA', 'Ramie', 'NATURAL_PLANT', 'Bast fiber from nettle family', TRUE, 5),
    ('SYS-000-FISO-00006', 'BA', 'Bamboo', 'NATURAL_PLANT', 'Bamboo (mechanically processed)', TRUE, 6),
    ('SYS-000-FISO-00007', 'CA', 'Coir', 'NATURAL_PLANT', 'Coir fiber from coconut husk', TRUE, 7),
    ('SYS-000-FISO-00008', 'AB', 'Abaca', 'NATURAL_PLANT', 'Manila hemp', TRUE, 8),
    ('SYS-000-FISO-00009', 'SI', 'Sisal', 'NATURAL_PLANT', 'Sisal fiber', TRUE, 9),
    ('SYS-000-FISO-00010', 'PI', 'Pina', 'NATURAL_PLANT', 'Pineapple fiber', TRUE, 10),
    ('SYS-000-FISO-00011', 'NE', 'Nettle', 'NATURAL_PLANT', 'Nettle fiber', TRUE, 11),
    ('SYS-000-FISO-00012', 'HE', 'Hemp', 'NATURAL_PLANT', 'Hemp variant (European mills)', TRUE, 12),
    ('SYS-000-FISO-00013', 'WO', 'Wool', 'NATURAL_ANIMAL', 'Wool (ISO standardized)', TRUE, 13),
    ('SYS-000-FISO-00014', 'WS', 'Cashmere', 'NATURAL_ANIMAL', 'Fine goat hair', TRUE, 14),
    ('SYS-000-FISO-00015', 'WM', 'Mohair', 'NATURAL_ANIMAL', 'Angora goat', TRUE, 15),
    ('SYS-000-FISO-00016', 'WL', 'Alpaca', 'NATURAL_ANIMAL', 'Alpaca fiber', TRUE, 16),
    ('SYS-000-FISO-00017', 'WP', 'Camel Hair', 'NATURAL_ANIMAL', 'Camel hair', TRUE, 17),
    ('SYS-000-FISO-00018', 'WY', 'Yak Hair', 'NATURAL_ANIMAL', 'Yak hair', TRUE, 18),
    ('SYS-000-FISO-00019', 'WG', 'Angora', 'NATURAL_ANIMAL', 'Rabbit fiber', TRUE, 19),
    ('SYS-000-FISO-00020', 'SE', 'Silk', 'NATURAL_ANIMAL', 'Silk from silkworm', TRUE, 20),
    ('SYS-000-FISO-00021', 'SD', 'Spider Silk', 'NATURAL_ANIMAL', 'Spider silk', TRUE, 21),
    ('SYS-000-FISO-00022', 'WQ', 'Vicuna', 'NATURAL_ANIMAL', 'Luxury camelid fiber', TRUE, 22),
    ('SYS-000-FISO-00023', 'WZ', 'Llama', 'NATURAL_ANIMAL', 'Llama fiber', TRUE, 23);

-- Regenerated Cellulosic
INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00024', 'CV', 'Viscose', 'REGENERATED_CELLULOSIC', 'Viscose rayon', TRUE, 24),
    ('SYS-000-FISO-00025', 'CMD', 'Modal', 'REGENERATED_CELLULOSIC', 'High-wet-modulus regenerated cellulose', TRUE, 25),
    ('SYS-000-FISO-00026', 'CLY', 'Lyocell', 'REGENERATED_CELLULOSIC', 'Solvent-spun cellulose (Tencel)', TRUE, 26),
    ('SYS-000-FISO-00027', 'CUP', 'Cupro', 'REGENERATED_CELLULOSIC', 'Cuprammonium regenerated cellulose', TRUE, 27),
    ('SYS-000-FISO-00028', 'ACTA', 'Cellulose Acetate', 'REGENERATED_CELLULOSIC', 'Partially acetylated cellulose', TRUE, 28),
    ('SYS-000-FISO-00029', 'CTA', 'Triacetate', 'REGENERATED_CELLULOSIC', 'Highly acetylated cellulose', TRUE, 29),
    ('SYS-000-FISO-00030', 'BBO', 'Bamboo Viscose', 'REGENERATED_CELLULOSIC', 'Chemically regenerated bamboo', TRUE, 30),
    ('SYS-000-FISO-00031', 'COC', 'Co-Cupro', 'REGENERATED_CELLULOSIC', 'Cupro-lyocell hybrid', TRUE, 31),
    ('SYS-000-FISO-00032', 'CBF', 'Banana Viscose', 'REGENERATED_CELLULOSIC', 'Cellulosic regeneration', TRUE, 32),
    ('SYS-000-FISO-00033', 'SCC', 'SeaCell', 'REGENERATED_CELLULOSIC', 'Seaweed + cellulose composite', TRUE, 33),
    ('SYS-000-FISO-00034', 'COH', 'Hemp Viscose', 'REGENERATED_CELLULOSIC', 'Regenerated hemp cellulose', TRUE, 34);

-- Synthetic Polymers
INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00035', 'PES', 'Polyester', 'SYNTHETIC_POLYMER', 'Polyethylene terephthalate', TRUE, 35),
    ('SYS-000-FISO-00036', 'PA', 'Polyamide (Nylon)', 'SYNTHETIC_POLYMER', 'Nylon family', TRUE, 36),
    ('SYS-000-FISO-00037', 'PAN', 'Polyacrylonitrile', 'SYNTHETIC_POLYMER', 'Acrylic fiber', TRUE, 37),
    ('SYS-000-FISO-00038', 'PP', 'Polypropylene', 'SYNTHETIC_POLYMER', 'Polyolefin', TRUE, 38),
    ('SYS-000-FISO-00039', 'PE', 'Polyethylene', 'SYNTHETIC_POLYMER', 'Polyolefin', TRUE, 39),
    ('SYS-000-FISO-00040', 'PU', 'Polyurethane', 'SYNTHETIC_POLYMER', 'Spandex, Elastane', TRUE, 40),
    ('SYS-000-FISO-00041', 'PTFE', 'Polytetrafluoroethylene', 'SYNTHETIC_POLYMER', 'Teflon', TRUE, 41),
    ('SYS-000-FISO-00042', 'PBI', 'Polybenzimidazole', 'SYNTHETIC_POLYMER', 'Heat-resistant fiber', TRUE, 42),
    ('SYS-000-FISO-00043', 'PPS', 'Polyphenylene Sulfide', 'SYNTHETIC_POLYMER', 'Engineering fiber', TRUE, 43),
    ('SYS-000-FISO-00044', 'PVC', 'Polyvinyl Chloride', 'SYNTHETIC_POLYMER', 'Vinyl fiber', TRUE, 44),
    ('SYS-000-FISO-00045', 'AR', 'Aramid', 'SYNTHETIC_POLYMER', 'Kevlar, Nomex', TRUE, 45),
    ('SYS-000-FISO-00046', 'PLA', 'Polylactic Acid', 'SYNTHETIC_POLYMER', 'Biobased polyester', TRUE, 46),
    ('SYS-000-FISO-00047', 'CF', 'Carbon Fiber', 'SYNTHETIC_POLYMER', 'From PAN or pitch', TRUE, 47),
    ('SYS-000-FISO-00048', 'GF', 'Glass Fiber', 'SYNTHETIC_POLYMER', 'Silicate-based', TRUE, 48),
    ('SYS-000-FISO-00049', 'BF', 'Basalt Fiber', 'SYNTHETIC_POLYMER', 'Volcanic origin', TRUE, 49),
    ('SYS-000-FISO-00050', 'MF', 'Metallic Fiber', 'SYNTHETIC_POLYMER', 'Stainless steel, copper', TRUE, 50),
    ('SYS-000-FISO-00051', 'UHMWPE', 'Ultra-High-Molecular PE', 'SYNTHETIC_POLYMER', 'Dyneema, Spectra', TRUE, 51);

-- Recycled & Biobased (Industry use)
INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00052', 'rPES', 'Recycled Polyester', 'SYNTHETIC_POLYMER', 'rPET', FALSE, 52),
    ('SYS-000-FISO-00053', 'rPA', 'Recycled Polyamide', 'SYNTHETIC_POLYMER', 'ECONYL', FALSE, 53),
    ('SYS-000-FISO-00054', 'rCO', 'Recycled Cotton', 'NATURAL_PLANT', 'Post-consumer cotton waste', FALSE, 54),
    ('SYS-000-FISO-00055', 'rWO', 'Recycled Wool', 'NATURAL_ANIMAL', 'Used wool garments', FALSE, 55),
    ('SYS-000-FISO-00056', 'bPES', 'Bio-Based Polyester', 'SYNTHETIC_POLYMER', 'Biobased variant', FALSE, 56),
    ('SYS-000-FISO-00057', 'bPU', 'Bio-Based PU', 'SYNTHETIC_POLYMER', 'BioSpandex', FALSE, 57),
    ('SYS-000-FISO-00058', 'bPLA', 'Bio-Based PLA', 'SYNTHETIC_POLYMER', 'Biobased variant', FALSE, 58);

-- Missing Natural Plant Fibers
INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00059', 'KAP', 'Kapok', 'NATURAL_PLANT', 'Kapok ağacından dolgu lifi', TRUE, 24),
    ('SYS-000-FISO-00060', 'KEN', 'Kenaf', 'NATURAL_PLANT', 'Hibiscus cannabinus bitkisinden', TRUE, 25),
    ('SYS-000-FISO-00061', 'ROS', 'Roselle', 'NATURAL_PLANT', 'Hibiscus sabdariffa bitkisinden', TRUE, 26);

-- Missing Regenerated Protein Fibers
INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00062', 'SOY', 'Soy Fiber', 'REGENERATED_PROTEIN', 'Soya proteini', FALSE, 35),
    ('SYS-000-FISO-00063', 'MC', 'Milk Fiber', 'REGENERATED_PROTEIN', 'Süt proteini (Casein)', FALSE, 36),
    ('SYS-000-FISO-00064', 'CHITIN', 'Chitin Fiber', 'REGENERATED_PROTEIN', 'Kabuklu deniz canlılarından', FALSE, 37);

-- Industry Codes (Non-ISO)
INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00065', 'EL', 'Elastane', 'SYNTHETIC_POLYMER', 'Spandex / Lycra', FALSE, 52),
    ('SYS-000-FISO-00066', 'MOD', 'Modacrylic', 'SYNTHETIC_POLYMER', 'Modified acrylic copolymer', FALSE, 53),
    ('SYS-000-FISO-00067', 'NY', 'Nylon (shorthand)', 'SYNTHETIC_POLYMER', 'Common shorthand for PA', FALSE, 54),
    ('SYS-000-FISO-00068', 'AC', 'Acrylic (shorthand)', 'SYNTHETIC_POLYMER', 'Common shorthand for PAN', FALSE, 55),
    ('SYS-000-FISO-00069', 'TR', 'Triacetate', 'REGENERATED_CELLULOSIC', 'Triacetate shorthand', FALSE, 56),
    ('SYS-000-FISO-00070', 'CVL', 'Viscose-Lyocell Hybrid', 'REGENERATED_CELLULOSIC', 'Hybrid fiber', FALSE, 57),
    ('SYS-000-FISO-00071', 'ECO', 'Eco Fiber', 'MIXED', 'Generic eco/sustainable tag', FALSE, 58);

-- Missing Advanced/Special Fibers
INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00072', 'NF', 'Nanofiber', 'TECHNICAL_ADVANCED', 'Nanoteknolojiyle üretilmiş ultra ince lif', FALSE, 59),
    ('SYS-000-FISO-00073', 'GRF', 'Graphene Fiber', 'TECHNICAL_ADVANCED', 'İletken ve dayanıklı karbon fiber türü', FALSE, 60),
    ('SYS-000-FISO-00074', 'CEF', 'Ceramic Fiber', 'TECHNICAL_ADVANCED', 'Isıya dayanıklı seramik bazlı', FALSE, 61);

-- Missing Recycled Fibers
INSERT INTO production.prod_fiber_iso_code 
    (uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) 
VALUES
    ('SYS-000-FISO-00075', 'rPP', 'Recycled Polypropylene', 'SYNTHETIC_POLYMER', 'Industrial waste', FALSE, 62);

-- =====================================================

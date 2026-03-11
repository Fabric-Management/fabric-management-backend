-- =====================================================
-- V8: FIBER REFERENCE TABLES
-- =====================================================
-- Purpose: Read-only reference tables for fiber categories, attributes, certifications, ISO codes
-- Aligned with Fiber entity structure (FiberCategory, FiberIsoCode, FiberAttribute, FiberCertification)
-- SYSTEM_TENANT_ID = '00000000-0000-0000-0000-000000000000'
-- =====================================================

CREATE SCHEMA IF NOT EXISTS production;

-- =====================================================
-- prod_fiber_category
-- Kural 5: 8 kategori değişmez
-- NATURAL_PLANT, NATURAL_ANIMAL, REGENERATED_CELLULOSIC,
-- REGENERATED_PROTEIN, SYNTHETIC_POLYMER, TECHNICAL_ADVANCED,
-- MINERAL, MIXED_BLEND
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

-- Kural 7: Display labels in English (short form)
INSERT INTO production.prod_fiber_category (tenant_id, uid, category_code, category_name, description, display_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00001', 'NATURAL_PLANT', 'Natural Plant', 'Cotton, Linen, Hemp, Jute, etc.', 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00002', 'NATURAL_ANIMAL', 'Natural Animal', 'Wool, Silk, Cashmere, Alpaca, etc.', 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00003', 'REGENERATED_CELLULOSIC', 'Regenerated Cellulosic', 'Viscose, Modal, Lyocell, Acetate, etc.', 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00004', 'REGENERATED_PROTEIN', 'Regenerated Protein', 'Soy, Milk, Chitin, etc.', 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00005', 'SYNTHETIC_POLYMER', 'Synthetic Polymer', 'Polyester, Nylon, Polypropylene, etc.', 5),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00006', 'TECHNICAL_ADVANCED', 'Technical & Advanced', 'Carbon, Aramid, Ceramic, etc.', 6),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00007', 'MINERAL', 'Mineral', 'Asbestos, Glass Fiber, etc.', 7),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00008', 'MIXED_BLEND', 'Mixed Blend', 'Blends of different fiber origins (e.g., Cotton + Viscose)', 8);

-- =====================================================
-- prod_fiber_attribute
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

INSERT INTO production.prod_fiber_attribute (tenant_id, uid, attribute_code, attribute_name, attribute_group, description, display_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00001', 'DURABLE', 'Durable', 'PHYSICAL', 'Long-lasting fiber', 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00002', 'STRONG', 'Strong', 'PHYSICAL', 'High tensile strength', 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00003', 'ELASTIC', 'Elastic', 'PHYSICAL', 'High elongation', 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00004', 'MOISTURE_ABSORBENT', 'Moisture Absorbent', 'PHYSICAL', 'Good water absorption', 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00005', 'WICKING', 'Moisture Wicking', 'PHYSICAL', 'Transports moisture away', 5),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00006', 'HYDROPHOBIC', 'Hydrophobic', 'CHEMICAL', 'Water-repellent', 6),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00007', 'OLEOPHOBIC', 'Oleophobic', 'CHEMICAL', 'Oil-repellent', 7),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00008', 'FLAME_RESISTANT', 'Flame Resistant', 'CHEMICAL', 'Resists ignition', 8),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00009', 'UV_RESISTANT', 'UV Resistant', 'CHEMICAL', 'Resists UV degradation', 9),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00010', 'CHEMICAL_RESISTANT', 'Chemical Resistant', 'CHEMICAL', 'Resists chemical damage', 10),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00011', 'BIODEGRADABLE', 'Biodegradable', 'ENVIRONMENTAL', 'Naturally decomposes', 11),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00012', 'RECYCLABLE', 'Recyclable', 'ENVIRONMENTAL', 'Can be recycled', 12),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00013', 'RENEWABLE', 'Renewable', 'ENVIRONMENTAL', 'From renewable sources', 13),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00014', 'ORGANIC', 'Organic', 'ENVIRONMENTAL', 'Certified organic', 14),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00015', 'COMBED', 'Combed', 'PHYSICAL', 'Combed processing for higher quality yarn', 15),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00016', 'CARDED', 'Carded', 'PHYSICAL', 'Carded processing for economy grade products', 16),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00017', 'MERCERIZED', 'Mercerized', 'PHYSICAL', 'Mercerization treatment applied', 17),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00018', 'PRESHRUNK', 'Pre-Shrunk', 'PHYSICAL', 'Pre-shrinking treatment applied', 18),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00019', 'RECYCLED', 'Recycled', 'ENVIRONMENTAL', 'Contains recycled content', 19),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00020', 'BIOBASED', 'Bio-Based', 'ENVIRONMENTAL', 'Derived from biological sources', 20);

-- =====================================================
-- prod_fiber_certification
-- Kural 1: certifying_body VARCHAR(255) (entity uyumlu)
-- =====================================================

CREATE TABLE IF NOT EXISTS production.prod_fiber_certification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    uid VARCHAR(100) UNIQUE NOT NULL DEFAULT 'SYS-000-FCER-00000',
    certification_code VARCHAR(50) UNIQUE NOT NULL,
    certification_name VARCHAR(100) NOT NULL,
    certifying_body VARCHAR(255),
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

-- Kural 4: Toplam 12 kayıt (V077 dahil)
INSERT INTO production.prod_fiber_certification (tenant_id, uid, certification_code, certification_name, certifying_body, description, display_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00001', 'GOTS', 'Global Organic Textile Standard', 'Global Standard gGmbH', 'Organic textile certification', 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00002', 'BCI', 'Better Cotton Initiative', 'BCI', 'Sustainable cotton', 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00003', 'OEKO_TEX_100', 'OEKO-TEX Standard 100', 'OEKO-TEX', 'Harmful substance testing', 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00004', 'GRS', 'Global Recycled Standard', 'Textile Exchange', 'Recycled content', 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00005', 'FSC', 'Forest Stewardship Council', 'FSC', 'Responsible forest management', 5),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00006', 'OBCS', 'Organic Blended Content Standard', 'Textile Exchange', 'Organic blend certification', 6),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00007', 'OEKO_TEX_STEP', 'OEKO-TEX STeP', 'OEKO-TEX Association', 'Sustainable Textile Production certification', 7),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00008', 'BLUESIGN', 'bluesign', 'bluesign technologies ag', 'Chemical, water and energy management standard', 8),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00009', 'FAIR_TRADE', 'Fair Trade Certified', 'Fair Trade USA', 'Fair trade and ethical sourcing certification', 9),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00010', 'COTTON_USA', 'Cotton USA', 'Cotton Council International', 'USA origin and quality assurance standard', 10),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00011', 'SUPIMA', 'Supima', 'Supima Association', 'American Pima cotton quality standard', 11),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00012', 'TENCEL', 'TENCEL™', 'Lenzing AG', 'Lenzing branded Lyocell and Modal fiber certification', 12);

-- =====================================================
-- prod_fiber_iso_code
-- =====================================================

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

COMMENT ON TABLE production.prod_fiber_iso_code IS 'Fiber ISO 2076 codes - Platform-level, tenant-independent. Kural 2: Sadece is_official_iso = TRUE (52 kayıt)';
COMMENT ON COLUMN production.prod_fiber_iso_code.iso_code IS 'ISO 2076 official codes (CO, PES, PA, etc.)';

-- Kural 2: Sadece is_official_iso = TRUE (52 kayıt)

-- NATURAL_PLANT (14): CO, LI, HA, JU, RA, BA, CA, AB, SI, PI, NE, KAP, KEN, ROS
INSERT INTO production.prod_fiber_iso_code (tenant_id, uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00001', 'CO', 'Cotton', 'NATURAL_PLANT', 'Standard abbreviation for cotton', TRUE, 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00002', 'LI', 'Linen', 'NATURAL_PLANT', 'Linen / flax; standard ISO abbreviation', TRUE, 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00003', 'HA', 'Hemp', 'NATURAL_PLANT', 'Hemp; often seen in sustainability reports', TRUE, 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00004', 'JU', 'Jute', 'NATURAL_PLANT', 'Jute; typically used in coarse fabrics', TRUE, 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00005', 'RA', 'Ramie', 'NATURAL_PLANT', 'Bast fiber from nettle family', TRUE, 5),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00006', 'BA', 'Bamboo', 'NATURAL_PLANT', 'Bamboo (mechanically processed)', TRUE, 6),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00007', 'CA', 'Coir', 'NATURAL_PLANT', 'Coir fiber from coconut husk', TRUE, 7),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00008', 'AB', 'Abaca', 'NATURAL_PLANT', 'Manila hemp', TRUE, 8),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00009', 'SI', 'Sisal', 'NATURAL_PLANT', 'Sisal fiber', TRUE, 9),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00010', 'PI', 'Pina', 'NATURAL_PLANT', 'Pineapple fiber', TRUE, 10),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00011', 'NE', 'Nettle', 'NATURAL_PLANT', 'Nettle fiber', TRUE, 11),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00012', 'KAP', 'Kapok', 'NATURAL_PLANT', 'Kapok ağacından dolgu lifi', TRUE, 12),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00013', 'KEN', 'Kenaf', 'NATURAL_PLANT', 'Hibiscus cannabinus bitkisinden', TRUE, 13),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00014', 'ROS', 'Roselle', 'NATURAL_PLANT', 'Hibiscus sabdariffa bitkisinden', TRUE, 14);

-- NATURAL_ANIMAL (10): WO, WS, WM, WL, WP, WY, WG, SE, WQ, WZ
INSERT INTO production.prod_fiber_iso_code (tenant_id, uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00015', 'WO', 'Wool', 'NATURAL_ANIMAL', 'Wool (ISO standardized)', TRUE, 15),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00016', 'WS', 'Cashmere', 'NATURAL_ANIMAL', 'Fine goat hair', TRUE, 16),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00017', 'WM', 'Mohair', 'NATURAL_ANIMAL', 'Angora goat', TRUE, 17),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00018', 'WL', 'Alpaca', 'NATURAL_ANIMAL', 'Alpaca fiber', TRUE, 18),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00019', 'WP', 'Camel Hair', 'NATURAL_ANIMAL', 'Camel hair', TRUE, 19),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00020', 'WY', 'Yak Hair', 'NATURAL_ANIMAL', 'Yak hair', TRUE, 20),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00021', 'WG', 'Angora', 'NATURAL_ANIMAL', 'Rabbit fiber', TRUE, 21),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00022', 'SE', 'Silk', 'NATURAL_ANIMAL', 'Silk from silkworm', TRUE, 22),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00023', 'WQ', 'Vicuna', 'NATURAL_ANIMAL', 'Luxury camelid fiber', TRUE, 23),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00024', 'WZ', 'Llama', 'NATURAL_ANIMAL', 'Llama fiber', TRUE, 24);

-- REGENERATED_CELLULOSIC (11): CV, CMD, CLY, CUP, ACTA, CTA, BBO, COC, CBF, SCC, COH
INSERT INTO production.prod_fiber_iso_code (tenant_id, uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00025', 'CV', 'Viscose', 'REGENERATED_CELLULOSIC', 'Viscose rayon', TRUE, 25),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00026', 'CMD', 'Modal', 'REGENERATED_CELLULOSIC', 'High-wet-modulus regenerated cellulose', TRUE, 26),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00027', 'CLY', 'Lyocell', 'REGENERATED_CELLULOSIC', 'Solvent-spun cellulose (Tencel)', TRUE, 27),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00028', 'CUP', 'Cupro', 'REGENERATED_CELLULOSIC', 'Cuprammonium regenerated cellulose', TRUE, 28),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00029', 'ACTA', 'Cellulose Acetate', 'REGENERATED_CELLULOSIC', 'Partially acetylated cellulose', TRUE, 29),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00030', 'CTA', 'Triacetate', 'REGENERATED_CELLULOSIC', 'Highly acetylated cellulose', TRUE, 30),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00031', 'BBO', 'Bamboo Viscose', 'REGENERATED_CELLULOSIC', 'Chemically regenerated bamboo', TRUE, 31),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00032', 'COC', 'Co-Cupro', 'REGENERATED_CELLULOSIC', 'Cupro-lyocell hybrid', TRUE, 32),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00033', 'CBF', 'Banana Viscose', 'REGENERATED_CELLULOSIC', 'Cellulosic regeneration', TRUE, 33),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00034', 'SCC', 'SeaCell', 'REGENERATED_CELLULOSIC', 'Seaweed + cellulose composite', TRUE, 34),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00035', 'COH', 'Hemp Viscose', 'REGENERATED_CELLULOSIC', 'Regenerated hemp cellulose', TRUE, 35);

-- SYNTHETIC_POLYMER (17): PES, PA, PAN, PP, PE, PU, PTFE, PBI, PPS, PVC, AR, PLA, CF, GF, BF, MF, UHMWPE
INSERT INTO production.prod_fiber_iso_code (tenant_id, uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00036', 'PES', 'Polyester', 'SYNTHETIC_POLYMER', 'Polyethylene terephthalate', TRUE, 36),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00037', 'PA', 'Polyamide (Nylon)', 'SYNTHETIC_POLYMER', 'Nylon family', TRUE, 37),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00038', 'PAN', 'Polyacrylonitrile', 'SYNTHETIC_POLYMER', 'Acrylic fiber', TRUE, 38),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00039', 'PP', 'Polypropylene', 'SYNTHETIC_POLYMER', 'Polyolefin', TRUE, 39),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00040', 'PE', 'Polyethylene', 'SYNTHETIC_POLYMER', 'Polyolefin', TRUE, 40),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00041', 'PU', 'Polyurethane', 'SYNTHETIC_POLYMER', 'Spandex, Elastane', TRUE, 41),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00042', 'PTFE', 'Polytetrafluoroethylene', 'SYNTHETIC_POLYMER', 'Teflon', TRUE, 42),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00043', 'PBI', 'Polybenzimidazole', 'SYNTHETIC_POLYMER', 'Heat-resistant fiber', TRUE, 43),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00044', 'PPS', 'Polyphenylene Sulfide', 'SYNTHETIC_POLYMER', 'Engineering fiber', TRUE, 44),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00045', 'PVC', 'Polyvinyl Chloride', 'SYNTHETIC_POLYMER', 'Vinyl fiber', TRUE, 45),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00046', 'AR', 'Aramid', 'SYNTHETIC_POLYMER', 'Kevlar, Nomex', TRUE, 46),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00047', 'PLA', 'Polylactic Acid', 'SYNTHETIC_POLYMER', 'Biobased polyester', TRUE, 47),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00048', 'CF', 'Carbon Fiber', 'SYNTHETIC_POLYMER', 'From PAN or pitch', TRUE, 48),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00049', 'GF', 'Glass Fiber', 'SYNTHETIC_POLYMER', 'Silicate-based', TRUE, 49),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00050', 'BF', 'Basalt Fiber', 'SYNTHETIC_POLYMER', 'Volcanic origin', TRUE, 50),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00051', 'MF', 'Metallic Fiber', 'SYNTHETIC_POLYMER', 'Stainless steel, copper', TRUE, 51),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00052', 'UHMWPE', 'Ultra-High-Molecular PE', 'SYNTHETIC_POLYMER', 'Dyneema, Spectra', TRUE, 52);

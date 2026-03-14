-- ============================================
-- MODULE: SEEDS
-- FK sırası: 1.common_tenant → 2.common_org/user → 3.fiber ref → 4.yarn ref → 5.hr policy → 6.routing_config
-- Kaynak: V081, V017, V008, V012, V033, V049
-- ============================================

-- ============================================================================
-- 1. common_tenant (V081 — SYSTEM_TENANT_ID)
-- ============================================================================
INSERT INTO common_tenant.common_tenant (
    id, uid, slug, name, billing_email, status, trial_ends_at, subscription_plan, settings,
    is_active, created_at, created_by, updated_at, updated_by, version
) VALUES (
    '00000000-0000-0000-0000-000000000000'::uuid,
    'SYS-000',
    'platform',
    'Platform (System)',
    NULL,
    'ACTIVE',
    NULL,
    NULL,
    '{"timezone":"UTC","locale":"en-US","currency":"USD","betaFeaturesEnabled":false,"aiEnabled":true,"emailNotificationsEnabled":true,"mfaRequired":false,"sessionTimeoutMinutes":480}'::jsonb,
    TRUE,
    CURRENT_TIMESTAMP,
    NULL,
    CURRENT_TIMESTAMP,
    NULL,
    0
)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 2. common_org / user (V017 — Platform System org + platform admin user)
--    Sıra: organization → role → user → contact → user_contact → auth_user
-- ============================================================================
DO $$
DECLARE
    system_tenant_id UUID := '00000000-0000-0000-0000-000000000000'::uuid;
    platform_org_id UUID;
    platform_role_id UUID;
    platform_user_id UUID;
    platform_contact_id UUID;
    platform_auth_id UUID;
    platform_admin_email VARCHAR := 'akkaya64@hotmail.com';
    platform_admin_password_hash VARCHAR := '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
BEGIN
    -- Organization: Platform System (idempotent by uk_organization_tenant_tax_id)
    INSERT INTO common_company.common_organization (
        id, tenant_id, uid, name, tax_id, organization_type, is_active, created_at, updated_at, version
    ) VALUES (
        gen_random_uuid(),
        system_tenant_id,
        'SYS-PLATFORM-001',
        'Platform System',
        'PLATFORM-SYSTEM-001',
        'VERTICAL_MILL',
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    )
    ON CONFLICT (tenant_id, tax_id) DO NOTHING;

    SELECT id INTO platform_org_id FROM common_company.common_organization
    WHERE tenant_id = system_tenant_id AND tax_id = 'PLATFORM-SYSTEM-001' LIMIT 1;
    IF platform_org_id IS NULL THEN RETURN; END IF;

    -- Role: PLATFORM_ADMIN (idempotent by uq_role_tenant_code)
    INSERT INTO common_user.common_role (
        id, tenant_id, uid, role_name, role_code, description, role_scope, is_system_role, is_active, created_at, updated_at, version
    ) VALUES (
        gen_random_uuid(),
        system_tenant_id,
        'SYS-ROLE-0001',
        'Platform Administrator',
        'PLATFORM_ADMIN',
        'Full platform access',
        'INTERNAL',
        TRUE,
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    )
    ON CONFLICT (tenant_id, role_code) DO NOTHING;

    SELECT id INTO platform_role_id FROM common_user.common_role
    WHERE tenant_id = system_tenant_id AND role_code = 'PLATFORM_ADMIN' LIMIT 1;
    IF platform_role_id IS NULL THEN RETURN; END IF;

    -- User: platform admin (skip if already exists by contact)
    IF EXISTS (
        SELECT 1 FROM common_user.common_user u
        JOIN common_user.common_user_contact uc ON u.id = uc.user_id
        JOIN common_communication.common_contact c ON uc.contact_id = c.id
        WHERE u.tenant_id = system_tenant_id AND c.contact_value = platform_admin_email
    ) THEN RETURN; END IF;

    platform_user_id := gen_random_uuid();
    platform_contact_id := gen_random_uuid();
    platform_auth_id := gen_random_uuid();

    INSERT INTO common_user.common_user (
        id, tenant_id, uid, first_name, last_name, organization_id, role_id, user_type, is_active, created_at, updated_at, version
    ) VALUES (
        platform_user_id,
        system_tenant_id,
        'SYS-USER-0001',
        'Platform',
        'Admin',
        platform_org_id,
        platform_role_id,
        'INTERNAL',
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    INSERT INTO common_communication.common_contact (
        id, tenant_id, uid, contact_value, contact_type, is_verified, is_personal, is_active, created_at, updated_at, version
    ) VALUES (
        platform_contact_id,
        system_tenant_id,
        'SYS-CONTACT-0001',
        platform_admin_email,
        'EMAIL',
        TRUE,
        TRUE,
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    INSERT INTO common_user.common_user_contact (
        tenant_id, uid, user_id, contact_id, is_default, is_active, created_at, updated_at, version
    ) VALUES (
        system_tenant_id,
        'SYS-USER-CONTACT-0001',
        platform_user_id,
        platform_contact_id,
        TRUE,
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );

    INSERT INTO common_auth.common_auth_user (
        id, tenant_id, uid, user_id, password_hash, is_verified, is_active, created_at, updated_at, version
    ) VALUES (
        platform_auth_id,
        system_tenant_id,
        'SYS-AUTH-0001',
        platform_user_id,
        platform_admin_password_hash,
        TRUE,
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        0
    );
END $$;

-- ============================================================================
-- 3. Fiber reference (V008 — prod_fiber_category, prod_fiber_attribute, prod_fiber_certification, prod_fiber_iso_code)
-- ============================================================================
INSERT INTO production.prod_fiber_category (tenant_id, uid, category_code, category_name, description, display_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00001', 'NATURAL_PLANT', 'Natural Plant', 'Cotton, Linen, Hemp, Jute, etc.', 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00002', 'NATURAL_ANIMAL', 'Natural Animal', 'Wool, Silk, Cashmere, Alpaca, etc.', 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00003', 'REGENERATED_CELLULOSIC', 'Regenerated Cellulosic', 'Viscose, Modal, Lyocell, Acetate, etc.', 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00004', 'REGENERATED_PROTEIN', 'Regenerated Protein', 'Soy, Milk, Chitin, etc.', 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00005', 'SYNTHETIC_POLYMER', 'Synthetic Polymer', 'Polyester, Nylon, Polypropylene, etc.', 5),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00006', 'TECHNICAL_ADVANCED', 'Technical & Advanced', 'Carbon, Aramid, Ceramic, etc.', 6),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00007', 'MINERAL', 'Mineral', 'Asbestos, Glass Fiber, etc.', 7),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCAT-00008', 'MIXED_BLEND', 'Mixed Blend', 'Blends of different fiber origins', 8)
ON CONFLICT (uid) DO NOTHING;

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
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00015', 'COMBED', 'Combed', 'PHYSICAL', 'Combed processing', 15),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00016', 'CARDED', 'Carded', 'PHYSICAL', 'Carded processing', 16),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00017', 'MERCERIZED', 'Mercerized', 'PHYSICAL', 'Mercerization treatment', 17),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00018', 'PRESHRUNK', 'Pre-Shrunk', 'PHYSICAL', 'Pre-shrinking treatment', 18),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00019', 'RECYCLED', 'Recycled', 'ENVIRONMENTAL', 'Contains recycled content', 19),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FATR-00020', 'BIOBASED', 'Bio-Based', 'ENVIRONMENTAL', 'Derived from biological sources', 20)
ON CONFLICT (uid) DO NOTHING;

INSERT INTO production.prod_fiber_certification (tenant_id, uid, certification_code, certification_name, certifying_body, description, display_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00001', 'GOTS', 'Global Organic Textile Standard', 'Global Standard gGmbH', 'Organic textile certification', 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00002', 'BCI', 'Better Cotton Initiative', 'BCI', 'Sustainable cotton', 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00003', 'OEKO_TEX_100', 'OEKO-TEX Standard 100', 'OEKO-TEX', 'Harmful substance testing', 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00004', 'GRS', 'Global Recycled Standard', 'Textile Exchange', 'Recycled content', 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00005', 'FSC', 'Forest Stewardship Council', 'FSC', 'Responsible forest management', 5),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00006', 'OBCS', 'Organic Blended Content Standard', 'Textile Exchange', 'Organic blend certification', 6),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00007', 'OEKO_TEX_STEP', 'OEKO-TEX STeP', 'OEKO-TEX Association', 'Sustainable Textile Production', 7),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00008', 'BLUESIGN', 'bluesign', 'bluesign technologies ag', 'Chemical, water and energy management', 8),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00009', 'FAIR_TRADE', 'Fair Trade Certified', 'Fair Trade USA', 'Fair trade certification', 9),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00010', 'COTTON_USA', 'Cotton USA', 'Cotton Council International', 'USA origin and quality', 10),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00011', 'SUPIMA', 'Supima', 'Supima Association', 'American Pima cotton', 11),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FCER-00012', 'TENCEL', 'TENCEL™', 'Lenzing AG', 'Lenzing Lyocell/Modal', 12)
ON CONFLICT (uid) DO NOTHING;

INSERT INTO production.prod_fiber_iso_code (tenant_id, uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00001', 'CO', 'Cotton', 'NATURAL_PLANT', 'Standard abbreviation for cotton', TRUE, 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00002', 'LI', 'Linen', 'NATURAL_PLANT', 'Linen / flax', TRUE, 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00003', 'HA', 'Hemp', 'NATURAL_PLANT', 'Hemp', TRUE, 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00004', 'JU', 'Jute', 'NATURAL_PLANT', 'Jute', TRUE, 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00005', 'RA', 'Ramie', 'NATURAL_PLANT', 'Bast fiber', TRUE, 5),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00006', 'BA', 'Bamboo', 'NATURAL_PLANT', 'Bamboo', TRUE, 6),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00007', 'CA', 'Coir', 'NATURAL_PLANT', 'Coconut husk', TRUE, 7),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00008', 'AB', 'Abaca', 'NATURAL_PLANT', 'Manila hemp', TRUE, 8),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00009', 'SI', 'Sisal', 'NATURAL_PLANT', 'Sisal fiber', TRUE, 9),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00010', 'PI', 'Pina', 'NATURAL_PLANT', 'Pineapple fiber', TRUE, 10),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00011', 'NE', 'Nettle', 'NATURAL_PLANT', 'Nettle fiber', TRUE, 11),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00012', 'KAP', 'Kapok', 'NATURAL_PLANT', 'Kapok', TRUE, 12),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00013', 'KEN', 'Kenaf', 'NATURAL_PLANT', 'Kenaf', TRUE, 13),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00014', 'ROS', 'Roselle', 'NATURAL_PLANT', 'Roselle', TRUE, 14),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00015', 'WO', 'Wool', 'NATURAL_ANIMAL', 'Wool', TRUE, 15),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00016', 'WS', 'Cashmere', 'NATURAL_ANIMAL', 'Fine goat hair', TRUE, 16),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00017', 'WM', 'Mohair', 'NATURAL_ANIMAL', 'Angora goat', TRUE, 17),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00018', 'WL', 'Alpaca', 'NATURAL_ANIMAL', 'Alpaca fiber', TRUE, 18),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00019', 'WP', 'Camel Hair', 'NATURAL_ANIMAL', 'Camel hair', TRUE, 19),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00020', 'WY', 'Yak Hair', 'NATURAL_ANIMAL', 'Yak hair', TRUE, 20),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00021', 'WG', 'Angora', 'NATURAL_ANIMAL', 'Rabbit fiber', TRUE, 21),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00022', 'SE', 'Silk', 'NATURAL_ANIMAL', 'Silk', TRUE, 22),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00023', 'WQ', 'Vicuna', 'NATURAL_ANIMAL', 'Vicuna', TRUE, 23),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00024', 'WZ', 'Llama', 'NATURAL_ANIMAL', 'Llama fiber', TRUE, 24),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00025', 'CV', 'Viscose', 'REGENERATED_CELLULOSIC', 'Viscose rayon', TRUE, 25),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00026', 'CMD', 'Modal', 'REGENERATED_CELLULOSIC', 'Modal', TRUE, 26),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00027', 'CLY', 'Lyocell', 'REGENERATED_CELLULOSIC', 'Lyocell (Tencel)', TRUE, 27),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00028', 'CUP', 'Cupro', 'REGENERATED_CELLULOSIC', 'Cupro', TRUE, 28),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00029', 'ACTA', 'Cellulose Acetate', 'REGENERATED_CELLULOSIC', 'Cellulose acetate', TRUE, 29),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00030', 'CTA', 'Triacetate', 'REGENERATED_CELLULOSIC', 'Triacetate', TRUE, 30),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00031', 'BBO', 'Bamboo Viscose', 'REGENERATED_CELLULOSIC', 'Bamboo viscose', TRUE, 31),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00032', 'COC', 'Co-Cupro', 'REGENERATED_CELLULOSIC', 'Cupro-lyocell', TRUE, 32),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00033', 'CBF', 'Banana Viscose', 'REGENERATED_CELLULOSIC', 'Banana viscose', TRUE, 33),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00034', 'SCC', 'SeaCell', 'REGENERATED_CELLULOSIC', 'SeaCell', TRUE, 34),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00035', 'COH', 'Hemp Viscose', 'REGENERATED_CELLULOSIC', 'Hemp viscose', TRUE, 35),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00036', 'PES', 'Polyester', 'SYNTHETIC_POLYMER', 'Polyester', TRUE, 36),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00037', 'PA', 'Polyamide (Nylon)', 'SYNTHETIC_POLYMER', 'Nylon', TRUE, 37),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00038', 'PAN', 'Polyacrylonitrile', 'SYNTHETIC_POLYMER', 'Acrylic', TRUE, 38),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00039', 'PP', 'Polypropylene', 'SYNTHETIC_POLYMER', 'Polypropylene', TRUE, 39),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00040', 'PE', 'Polyethylene', 'SYNTHETIC_POLYMER', 'Polyethylene', TRUE, 40),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00041', 'PU', 'Polyurethane', 'SYNTHETIC_POLYMER', 'Spandex, Elastane', TRUE, 41),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00042', 'PTFE', 'Polytetrafluoroethylene', 'SYNTHETIC_POLYMER', 'Teflon', TRUE, 42),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00043', 'PBI', 'Polybenzimidazole', 'SYNTHETIC_POLYMER', 'PBI', TRUE, 43),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00044', 'PPS', 'Polyphenylene Sulfide', 'SYNTHETIC_POLYMER', 'PPS', TRUE, 44),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00045', 'PVC', 'Polyvinyl Chloride', 'SYNTHETIC_POLYMER', 'PVC', TRUE, 45),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00046', 'AR', 'Aramid', 'SYNTHETIC_POLYMER', 'Aramid', TRUE, 46),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00047', 'PLA', 'Polylactic Acid', 'SYNTHETIC_POLYMER', 'PLA', TRUE, 47),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00048', 'CF', 'Carbon Fiber', 'SYNTHETIC_POLYMER', 'Carbon fiber', TRUE, 48),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00049', 'GF', 'Glass Fiber', 'SYNTHETIC_POLYMER', 'Glass fiber', TRUE, 49),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00050', 'BF', 'Basalt Fiber', 'SYNTHETIC_POLYMER', 'Basalt', TRUE, 50),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00051', 'MF', 'Metallic Fiber', 'SYNTHETIC_POLYMER', 'Metallic', TRUE, 51),
    ('00000000-0000-0000-0000-000000000000', 'SYS-000-FISO-00052', 'UHMWPE', 'Ultra-High-Molecular PE', 'SYNTHETIC_POLYMER', 'UHMWPE', TRUE, 52)
ON CONFLICT (uid) DO NOTHING;

-- ============================================================================
-- 4. Yarn reference (V012)
-- ============================================================================
INSERT INTO production.prod_yarn_category (tenant_id, uid, category_code, category_name, description, display_order)
VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CAT-001', 'SEWING', 'Sewing Yarn', 'Yarn used for sewing operations', 1),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CAT-002', 'KNITTING', 'Knitting Yarn', 'Yarn used for knitting fabric', 2),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CAT-003', 'WEAVING', 'Weaving Yarn', 'Yarn used for weaving fabric on loom', 3),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CAT-004', 'EMBROIDERY', 'Embroidery Yarn', 'Decorative embroidery yarn', 4),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CAT-005', 'SPECIALTY', 'Specialty Yarn', 'Special purpose yarn', 5)
ON CONFLICT (category_code) DO NOTHING;

INSERT INTO production.prod_yarn_attribute (tenant_id, uid, attribute_code, attribute_name, attribute_type, unit)
VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-001', 'COUNT', 'Yarn Count', 'PHYSICAL', 'Ne/Tex'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-002', 'TWIST', 'Twist per Meter', 'PHYSICAL', 'TPM'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-003', 'STRENGTH', 'Tensile Strength', 'MECHANICAL', 'cN/tex'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-004', 'ELONGATION', 'Elongation at Break', 'MECHANICAL', '%'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-005', 'HAIRINESS', 'Yarn Hairiness', 'PHYSICAL', 'H-value'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-ATTR-006', 'EVENNESS', 'Yarn Evenness', 'QUALITY', 'CV%')
ON CONFLICT (attribute_code) DO NOTHING;

INSERT INTO production.prod_yarn_certification (tenant_id, uid, certification_code, certification_name, certifying_body)
VALUES
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CERT-001', 'GOTS', 'Global Organic Textile Standard', 'GOTS'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CERT-002', 'OEKO_TEX', 'Oeko-Tex Standard 100', 'OEKO-TEX'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CERT-003', 'GRS', 'Global Recycled Standard', 'Textile Exchange'),
    ('00000000-0000-0000-0000-000000000000', 'SYS-YARN-CERT-004', 'BSCI', 'Business Social Compliance Initiative', 'BSCI')
ON CONFLICT (certification_code) DO NOTHING;

-- ============================================================================
-- 5. HR policy pack (V033 — GLOBAL-BASE, EU-BASELINE for system tenant)
-- ============================================================================
INSERT INTO human.human_hr_policy_pack (id, tenant_id, uid, pack_code, pack_version, country_code, name, status, payload, inheritance_mode)
VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000000'::uuid, 'SYS-HR-PACK-001', 'GLOBAL-BASE', 1, 'GLOBAL', 'Global Baseline', 'ACTIVE', '{}'::jsonb, 'FULL')
ON CONFLICT (tenant_id, pack_code, pack_version) DO NOTHING;

INSERT INTO human.human_hr_policy_pack (id, tenant_id, uid, pack_code, pack_version, country_code, name, status, payload, inheritance_mode, parent_pack_id, parent_pack_code, region_code)
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000000'::uuid, 'SYS-HR-PACK-002', 'EU-BASELINE', 1, 'EU', 'EU Baseline', 'ACTIVE', '{}'::jsonb, 'PARTIAL',
       p.id, 'GLOBAL-BASE', 'EU'
FROM human.human_hr_policy_pack p
WHERE p.tenant_id = '00000000-0000-0000-0000-000000000000'::uuid AND p.pack_code = 'GLOBAL-BASE'
LIMIT 1
ON CONFLICT (tenant_id, pack_code, pack_version) DO NOTHING;

-- ============================================================================
-- 6. routing_config (V049 — platform-level, tenant_id NULL)
-- ============================================================================
INSERT INTO common_communication.common_routing_config
(id, uid, tenant_id, country_code, primary_channel, fallback_channel, timeout_seconds, is_active, created_at, updated_at, version)
SELECT gen_random_uuid(), 'ROUTE-TR', NULL, 'TR', 'WHATSAPP', 'SMS', 15, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM common_communication.common_routing_config WHERE tenant_id IS NULL AND country_code = 'TR');

INSERT INTO common_communication.common_routing_config
(id, uid, tenant_id, country_code, primary_channel, fallback_channel, timeout_seconds, is_active, created_at, updated_at, version)
SELECT gen_random_uuid(), 'ROUTE-GB', NULL, 'GB', 'EMAIL', NULL, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM common_communication.common_routing_config WHERE tenant_id IS NULL AND country_code = 'GB');

-- [SEEDS] tamamlandı.

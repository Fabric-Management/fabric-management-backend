-- ============================================================================
-- R001: Canonical shared pure fibres (FIBER-CATALOG-1)
-- ============================================================================
-- This file is the single authoritative definition of the canonical pure-fibre catalogue: one
-- row per shared ISO code with its fixed Product id/uid and Fiber id/uid. Identity never depends
-- on row order, display_order, a counter or an id generated at installation time; ordering here
-- is presentation only. SYS-MAT-/SYS-FIB- uids keep the shape existing consumers read, each bound
-- to exactly one explicit entry.
--
-- Flyway re-executes a repeatable migration when its checksum changes (not at every check).
-- Replaying this file inserts nothing that already exists, changes no id, Product<->Fiber link,
-- ISO identity, composition, source, status or user state, and performs no no-op update.
-- A missing ISO/category, or a key bound to different data, aborts the whole publication.
--
-- Adding a catalogue entry: publish its ISO row under the catalogue owner in a new versioned
-- migration, then append ONE row below with new fixed ids. Never reuse or renumber a key.
-- Correcting a fibre display name is an explicit edit of that row's name. The 52 initial
-- abbreviations and is_official_iso values are existing catalogue metadata, not an external
-- ISO certification of the list.
-- ============================================================================

DO $$
DECLARE
    entry RECORD;
BEGIN
    -- Flyway applies repeatables even when a test migrates to an older target. Before the
    -- shared-catalogue migration (V20260928100000) the publication function does not exist yet;
    -- the full chain always creates it before this file runs.
    IF to_regprocedure(
           'production.publish_fiber_catalog_entry(text, uuid, text, uuid, text, text)') IS NULL THEN
        RAISE NOTICE 'FIBER-CATALOG publication skipped: schema predates V20260928100000';
        RETURN;
    END IF;

    FOR entry IN
        SELECT *
          FROM (VALUES
        ('CO', '0f1bd000-0000-4000-8000-000000000001', 'SYS-MAT-000001', '0f1bf1b0-0000-4000-8000-000000000001', 'SYS-FIB-000001', 'Cotton (100%)'),
        ('LI', '0f1bd000-0000-4000-8000-000000000002', 'SYS-MAT-000002', '0f1bf1b0-0000-4000-8000-000000000002', 'SYS-FIB-000002', 'Linen (100%)'),
        ('HA', '0f1bd000-0000-4000-8000-000000000003', 'SYS-MAT-000003', '0f1bf1b0-0000-4000-8000-000000000003', 'SYS-FIB-000003', 'Hemp (100%)'),
        ('JU', '0f1bd000-0000-4000-8000-000000000004', 'SYS-MAT-000004', '0f1bf1b0-0000-4000-8000-000000000004', 'SYS-FIB-000004', 'Jute (100%)'),
        ('RA', '0f1bd000-0000-4000-8000-000000000005', 'SYS-MAT-000005', '0f1bf1b0-0000-4000-8000-000000000005', 'SYS-FIB-000005', 'Ramie (100%)'),
        ('BA', '0f1bd000-0000-4000-8000-000000000006', 'SYS-MAT-000006', '0f1bf1b0-0000-4000-8000-000000000006', 'SYS-FIB-000006', 'Bamboo (100%)'),
        ('CA', '0f1bd000-0000-4000-8000-000000000007', 'SYS-MAT-000007', '0f1bf1b0-0000-4000-8000-000000000007', 'SYS-FIB-000007', 'Coir (100%)'),
        ('AB', '0f1bd000-0000-4000-8000-000000000008', 'SYS-MAT-000008', '0f1bf1b0-0000-4000-8000-000000000008', 'SYS-FIB-000008', 'Abaca (100%)'),
        ('SI', '0f1bd000-0000-4000-8000-000000000009', 'SYS-MAT-000009', '0f1bf1b0-0000-4000-8000-000000000009', 'SYS-FIB-000009', 'Sisal (100%)'),
        ('PI', '0f1bd000-0000-4000-8000-000000000010', 'SYS-MAT-000010', '0f1bf1b0-0000-4000-8000-000000000010', 'SYS-FIB-000010', 'Pina (100%)'),
        ('NE', '0f1bd000-0000-4000-8000-000000000011', 'SYS-MAT-000011', '0f1bf1b0-0000-4000-8000-000000000011', 'SYS-FIB-000011', 'Nettle (100%)'),
        ('KAP', '0f1bd000-0000-4000-8000-000000000012', 'SYS-MAT-000012', '0f1bf1b0-0000-4000-8000-000000000012', 'SYS-FIB-000012', 'Kapok (100%)'),
        ('KEN', '0f1bd000-0000-4000-8000-000000000013', 'SYS-MAT-000013', '0f1bf1b0-0000-4000-8000-000000000013', 'SYS-FIB-000013', 'Kenaf (100%)'),
        ('ROS', '0f1bd000-0000-4000-8000-000000000014', 'SYS-MAT-000014', '0f1bf1b0-0000-4000-8000-000000000014', 'SYS-FIB-000014', 'Roselle (100%)'),
        ('WO', '0f1bd000-0000-4000-8000-000000000015', 'SYS-MAT-000015', '0f1bf1b0-0000-4000-8000-000000000015', 'SYS-FIB-000015', 'Wool (100%)'),
        ('WS', '0f1bd000-0000-4000-8000-000000000016', 'SYS-MAT-000016', '0f1bf1b0-0000-4000-8000-000000000016', 'SYS-FIB-000016', 'Cashmere (100%)'),
        ('WM', '0f1bd000-0000-4000-8000-000000000017', 'SYS-MAT-000017', '0f1bf1b0-0000-4000-8000-000000000017', 'SYS-FIB-000017', 'Mohair (100%)'),
        ('WL', '0f1bd000-0000-4000-8000-000000000018', 'SYS-MAT-000018', '0f1bf1b0-0000-4000-8000-000000000018', 'SYS-FIB-000018', 'Alpaca (100%)'),
        ('WP', '0f1bd000-0000-4000-8000-000000000019', 'SYS-MAT-000019', '0f1bf1b0-0000-4000-8000-000000000019', 'SYS-FIB-000019', 'Camel Hair (100%)'),
        ('WY', '0f1bd000-0000-4000-8000-000000000020', 'SYS-MAT-000020', '0f1bf1b0-0000-4000-8000-000000000020', 'SYS-FIB-000020', 'Yak Hair (100%)'),
        ('WG', '0f1bd000-0000-4000-8000-000000000021', 'SYS-MAT-000021', '0f1bf1b0-0000-4000-8000-000000000021', 'SYS-FIB-000021', 'Angora (100%)'),
        ('SE', '0f1bd000-0000-4000-8000-000000000022', 'SYS-MAT-000022', '0f1bf1b0-0000-4000-8000-000000000022', 'SYS-FIB-000022', 'Silk (100%)'),
        ('WQ', '0f1bd000-0000-4000-8000-000000000023', 'SYS-MAT-000023', '0f1bf1b0-0000-4000-8000-000000000023', 'SYS-FIB-000023', 'Vicuna (100%)'),
        ('WZ', '0f1bd000-0000-4000-8000-000000000024', 'SYS-MAT-000024', '0f1bf1b0-0000-4000-8000-000000000024', 'SYS-FIB-000024', 'Llama (100%)'),
        ('CV', '0f1bd000-0000-4000-8000-000000000025', 'SYS-MAT-000025', '0f1bf1b0-0000-4000-8000-000000000025', 'SYS-FIB-000025', 'Viscose (100%)'),
        ('CMD', '0f1bd000-0000-4000-8000-000000000026', 'SYS-MAT-000026', '0f1bf1b0-0000-4000-8000-000000000026', 'SYS-FIB-000026', 'Modal (100%)'),
        ('CLY', '0f1bd000-0000-4000-8000-000000000027', 'SYS-MAT-000027', '0f1bf1b0-0000-4000-8000-000000000027', 'SYS-FIB-000027', 'Lyocell (100%)'),
        ('CUP', '0f1bd000-0000-4000-8000-000000000028', 'SYS-MAT-000028', '0f1bf1b0-0000-4000-8000-000000000028', 'SYS-FIB-000028', 'Cupro (100%)'),
        ('ACTA', '0f1bd000-0000-4000-8000-000000000029', 'SYS-MAT-000029', '0f1bf1b0-0000-4000-8000-000000000029', 'SYS-FIB-000029', 'Cellulose Acetate (100%)'),
        ('CTA', '0f1bd000-0000-4000-8000-000000000030', 'SYS-MAT-000030', '0f1bf1b0-0000-4000-8000-000000000030', 'SYS-FIB-000030', 'Triacetate (100%)'),
        ('BBO', '0f1bd000-0000-4000-8000-000000000031', 'SYS-MAT-000031', '0f1bf1b0-0000-4000-8000-000000000031', 'SYS-FIB-000031', 'Bamboo Viscose (100%)'),
        ('COC', '0f1bd000-0000-4000-8000-000000000032', 'SYS-MAT-000032', '0f1bf1b0-0000-4000-8000-000000000032', 'SYS-FIB-000032', 'Co-Cupro (100%)'),
        ('CBF', '0f1bd000-0000-4000-8000-000000000033', 'SYS-MAT-000033', '0f1bf1b0-0000-4000-8000-000000000033', 'SYS-FIB-000033', 'Banana Viscose (100%)'),
        ('SCC', '0f1bd000-0000-4000-8000-000000000034', 'SYS-MAT-000034', '0f1bf1b0-0000-4000-8000-000000000034', 'SYS-FIB-000034', 'SeaCell (100%)'),
        ('COH', '0f1bd000-0000-4000-8000-000000000035', 'SYS-MAT-000035', '0f1bf1b0-0000-4000-8000-000000000035', 'SYS-FIB-000035', 'Hemp Viscose (100%)'),
        ('PES', '0f1bd000-0000-4000-8000-000000000036', 'SYS-MAT-000036', '0f1bf1b0-0000-4000-8000-000000000036', 'SYS-FIB-000036', 'Polyester (100%)'),
        ('PA', '0f1bd000-0000-4000-8000-000000000037', 'SYS-MAT-000037', '0f1bf1b0-0000-4000-8000-000000000037', 'SYS-FIB-000037', 'Polyamide (Nylon) (100%)'),
        ('PAN', '0f1bd000-0000-4000-8000-000000000038', 'SYS-MAT-000038', '0f1bf1b0-0000-4000-8000-000000000038', 'SYS-FIB-000038', 'Polyacrylonitrile (100%)'),
        ('PP', '0f1bd000-0000-4000-8000-000000000039', 'SYS-MAT-000039', '0f1bf1b0-0000-4000-8000-000000000039', 'SYS-FIB-000039', 'Polypropylene (100%)'),
        ('PE', '0f1bd000-0000-4000-8000-000000000040', 'SYS-MAT-000040', '0f1bf1b0-0000-4000-8000-000000000040', 'SYS-FIB-000040', 'Polyethylene (100%)'),
        ('PU', '0f1bd000-0000-4000-8000-000000000041', 'SYS-MAT-000041', '0f1bf1b0-0000-4000-8000-000000000041', 'SYS-FIB-000041', 'Polyurethane (100%)'),
        ('PTFE', '0f1bd000-0000-4000-8000-000000000042', 'SYS-MAT-000042', '0f1bf1b0-0000-4000-8000-000000000042', 'SYS-FIB-000042', 'Polytetrafluoroethylene (100%)'),
        ('PBI', '0f1bd000-0000-4000-8000-000000000043', 'SYS-MAT-000043', '0f1bf1b0-0000-4000-8000-000000000043', 'SYS-FIB-000043', 'Polybenzimidazole (100%)'),
        ('PPS', '0f1bd000-0000-4000-8000-000000000044', 'SYS-MAT-000044', '0f1bf1b0-0000-4000-8000-000000000044', 'SYS-FIB-000044', 'Polyphenylene Sulfide (100%)'),
        ('PVC', '0f1bd000-0000-4000-8000-000000000045', 'SYS-MAT-000045', '0f1bf1b0-0000-4000-8000-000000000045', 'SYS-FIB-000045', 'Polyvinyl Chloride (100%)'),
        ('AR', '0f1bd000-0000-4000-8000-000000000046', 'SYS-MAT-000046', '0f1bf1b0-0000-4000-8000-000000000046', 'SYS-FIB-000046', 'Aramid (100%)'),
        ('PLA', '0f1bd000-0000-4000-8000-000000000047', 'SYS-MAT-000047', '0f1bf1b0-0000-4000-8000-000000000047', 'SYS-FIB-000047', 'Polylactic Acid (100%)'),
        ('CF', '0f1bd000-0000-4000-8000-000000000048', 'SYS-MAT-000048', '0f1bf1b0-0000-4000-8000-000000000048', 'SYS-FIB-000048', 'Carbon Fiber (100%)'),
        ('GF', '0f1bd000-0000-4000-8000-000000000049', 'SYS-MAT-000049', '0f1bf1b0-0000-4000-8000-000000000049', 'SYS-FIB-000049', 'Glass Fiber (100%)'),
        ('BF', '0f1bd000-0000-4000-8000-000000000050', 'SYS-MAT-000050', '0f1bf1b0-0000-4000-8000-000000000050', 'SYS-FIB-000050', 'Basalt Fiber (100%)'),
        ('MF', '0f1bd000-0000-4000-8000-000000000051', 'SYS-MAT-000051', '0f1bf1b0-0000-4000-8000-000000000051', 'SYS-FIB-000051', 'Metallic Fiber (100%)'),
        ('UHMWPE', '0f1bd000-0000-4000-8000-000000000052', 'SYS-MAT-000052', '0f1bf1b0-0000-4000-8000-000000000052', 'SYS-FIB-000052', 'Ultra-High-Molecular PE (100%)')
          ) AS catalogue(iso_code, product_id, product_uid, fiber_id, fiber_uid, fiber_name)
    LOOP
        PERFORM production.publish_fiber_catalog_entry(
            entry.iso_code,
            entry.product_id::uuid,
            entry.product_uid,
            entry.fiber_id::uuid,
            entry.fiber_uid,
            entry.fiber_name);
    END LOOP;
END $$;

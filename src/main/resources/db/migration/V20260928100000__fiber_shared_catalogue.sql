-- ============================================================================
-- FIBER-CATALOG-1 (product/fiber): one shared fibre catalogue, tenant-owned blends.
-- ============================================================================
-- The golden template (00000000-0000-0000-ffff-000000000001) is the only catalogue owner.
-- Categories, certification schemes and ISO rows are published once, by that owner, and read
-- by every tenant; tenants never receive copies. Canonical pure fibres and their products are
-- owned by the same owner. Tenants own blends and platform-reviewed source-declared variants.
--
-- Fresh-install chain: V002 seeds the reference rows under the historical system owner with
-- fixed ids, V20260603142000 moves them to the catalogue owner, this migration locks the
-- ownership down, and R__001__fiber_seeds.sql publishes the canonical pure fibres through
-- production.publish_fiber_catalog_entry. Existing development databases that still hold
-- tenant reference copies must be recreated; this migration deliberately does not repair them.
-- ============================================================================

-- ── 1. Shared reference tables accept catalogue-owner rows only ────────────────────────────
ALTER TABLE production.prod_fiber_category
    ADD CONSTRAINT chk_fiber_category_catalog_owner
        CHECK (tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid);

ALTER TABLE production.prod_fiber_certification
    ADD CONSTRAINT chk_fiber_certification_catalog_owner
        CHECK (tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid);

ALTER TABLE production.prod_fiber_iso_code
    ADD CONSTRAINT chk_fiber_iso_code_catalog_owner
        CHECK (tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid),
    ADD CONSTRAINT chk_fiber_iso_code_normalized
        CHECK (iso_code = upper(btrim(iso_code)) AND iso_code <> '');

-- Case-insensitive ISO uniqueness inside the sole catalogue owner, enforced by the database.
CREATE UNIQUE INDEX uq_fiber_iso_code_catalog_code
    ON production.prod_fiber_iso_code (tenant_id, upper(iso_code));

COMMENT ON TABLE production.prod_fiber_iso_code IS
    'Shared ISO fibre codes (FIBER-CATALOG-1): one row per code, owned by the golden template.';
COMMENT ON TABLE production.prod_fiber_category IS
    'Shared fibre categories (FIBER-CATALOG-1): owned by the golden template, never copied.';
COMMENT ON TABLE production.prod_fiber_certification IS
    'Shared certification-scheme dictionary (FIBER-CATALOG-1). A scheme name is not a certificate.';

-- RLS on these tables (V20260611050200) already reads own OR catalogue-owner rows and writes own
-- rows only. With the owner CHECK above, only a session bound to the catalogue owner can write.

-- ── 2. Fibre shape: pure fibres carry one ISO, blends carry none ─────────────────────────────
ALTER TABLE production.prod_fiber
    ALTER COLUMN fiber_iso_code_id DROP NOT NULL;

ALTER TABLE production.prod_fiber
    ADD CONSTRAINT chk_fiber_composition_object
        CHECK (composition IS NULL OR jsonb_typeof(composition) = 'object'),
    ADD CONSTRAINT chk_fiber_iso_matches_kind
        CHECK ((coalesce(composition, '{}'::jsonb) = '{}'::jsonb) = (fiber_iso_code_id IS NOT NULL)),
    -- MIXED_BLEND has a fixed id in V002.
    ADD CONSTRAINT chk_fiber_blend_mixed_category
        CHECK (
            coalesce(composition, '{}'::jsonb) = '{}'::jsonb
            OR fiber_category_id = '0f1bca70-0000-4000-8000-000000000008'::uuid
        ),
    -- The shared catalogue publishes canonical, source-undeclared pure fibres only.
    ADD CONSTRAINT chk_fiber_catalog_owner_canonical
        CHECK (
            tenant_id <> '00000000-0000-0000-ffff-000000000001'::uuid
            OR (coalesce(composition, '{}'::jsonb) = '{}'::jsonb AND material_source IS NULL)
        );

-- Database backstop for tenant-local duplicate blends. jsonb equality ignores key order and
-- compares numbers numerically, so 60 and 60.00 are the same composition.
CREATE UNIQUE INDEX uq_fiber_active_blend_composition
    ON production.prod_fiber (tenant_id, composition)
    WHERE is_active = TRUE
      AND coalesce(composition, '{}'::jsonb) <> '{}'::jsonb;

-- ── 3. Catalogue publication ─────────────────────────────────────────────────────────────────
-- Publishes (or re-verifies) one canonical pure fibre for one shared ISO code. Identity is the
-- explicit (ISO code, Product id, Fiber id, uids) tuple from the checked-in seed definition, never
-- a position, a counter or a generated id. Replays insert nothing and change no identity; only
-- the descriptive fibre name may be corrected. Every inconsistency raises and aborts the whole
-- publication transaction, so a catalogue is never published partially.
CREATE OR REPLACE FUNCTION production.publish_fiber_catalog_entry(
    p_iso_code    TEXT,
    p_product_id  UUID,
    p_product_uid TEXT,
    p_fiber_id    UUID,
    p_fiber_uid   TEXT,
    p_fiber_name  TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    catalog_owner   CONSTANT UUID := '00000000-0000-0000-ffff-000000000001';
    previous_tenant TEXT := current_setting('app.current_tenant', true);
    iso             RECORD;
    category_id     UUID;
    product_row     RECORD;
    fiber_row       RECORD;
BEGIN
    IF p_iso_code IS NULL OR btrim(p_iso_code) = '' OR p_product_id IS NULL OR p_fiber_id IS NULL
       OR p_product_uid IS NULL OR p_fiber_uid IS NULL OR p_fiber_name IS NULL
       OR btrim(p_fiber_name) = '' THEN
        RAISE EXCEPTION 'FIBER-CATALOG publication: every catalogue key is required (iso=%, product=%, fiber=%)',
            p_iso_code, p_product_id, p_fiber_id;
    END IF;

    -- FORCE RLS applies to the table owner too: bind the publication to the catalogue owner.
    PERFORM set_config('app.current_tenant', catalog_owner::text, true);

    SELECT id, iso_code, fiber_type, is_active
      INTO iso
      FROM production.prod_fiber_iso_code
     WHERE tenant_id = catalog_owner
       AND upper(iso_code) = upper(btrim(p_iso_code));
    IF NOT FOUND THEN
        RAISE EXCEPTION 'FIBER-CATALOG publication: ISO code % is not published by the catalogue owner %; publish the ISO row before its canonical fibre',
            p_iso_code, catalog_owner;
    END IF;
    IF NOT iso.is_active THEN
        RAISE EXCEPTION 'FIBER-CATALOG publication: ISO code % is inactive; lifecycle changes are explicit releases',
            iso.iso_code;
    END IF;

    SELECT id
      INTO category_id
      FROM production.prod_fiber_category
     WHERE tenant_id = catalog_owner
       AND category_code = iso.fiber_type
       AND is_active = TRUE;
    IF category_id IS NULL THEN
        RAISE EXCEPTION 'FIBER-CATALOG publication: ISO code % requires active category %, which the catalogue owner does not publish',
            iso.iso_code, iso.fiber_type;
    END IF;

    SELECT id, tenant_id, uid, product_type
      INTO product_row
      FROM production.prod_product
     WHERE id = p_product_id;
    IF FOUND THEN
        IF product_row.tenant_id <> catalog_owner OR product_row.uid <> p_product_uid
           OR product_row.product_type <> 'FIBER' THEN
            RAISE EXCEPTION 'FIBER-CATALOG publication: product % is bound to (tenant=%, uid=%, type=%), not the catalogue entry % / %',
                p_product_id, product_row.tenant_id, product_row.uid, product_row.product_type,
                iso.iso_code, p_product_uid;
        END IF;
    ELSE
        IF EXISTS (SELECT 1 FROM production.prod_product WHERE uid = p_product_uid) THEN
            RAISE EXCEPTION 'FIBER-CATALOG publication: product uid % already identifies another product',
                p_product_uid;
        END IF;
        INSERT INTO production.prod_product
            (id, tenant_id, uid, product_type, unit, is_active, created_at, updated_at, version)
        VALUES
            (p_product_id, catalog_owner, p_product_uid, 'FIBER', 'KG', TRUE,
             CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
    END IF;

    SELECT id, tenant_id, uid, product_id, fiber_iso_code_id, fiber_name
      INTO fiber_row
      FROM production.prod_fiber
     WHERE id = p_fiber_id;
    IF FOUND THEN
        IF fiber_row.tenant_id <> catalog_owner OR fiber_row.uid <> p_fiber_uid
           OR fiber_row.product_id <> p_product_id
           OR fiber_row.fiber_iso_code_id IS DISTINCT FROM iso.id THEN
            RAISE EXCEPTION 'FIBER-CATALOG publication: fibre % is bound to (tenant=%, uid=%, product=%, iso=%); an existing catalogue key cannot move to ISO % / product %',
                p_fiber_id, fiber_row.tenant_id, fiber_row.uid, fiber_row.product_id,
                fiber_row.fiber_iso_code_id, iso.id, p_product_id;
        END IF;
        IF fiber_row.fiber_name IS DISTINCT FROM btrim(p_fiber_name) THEN
            UPDATE production.prod_fiber
               SET fiber_name = btrim(p_fiber_name),
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = p_fiber_id;
        END IF;
    ELSE
        IF EXISTS (SELECT 1 FROM production.prod_fiber
                    WHERE uid = p_fiber_uid OR product_id = p_product_id) THEN
            RAISE EXCEPTION 'FIBER-CATALOG publication: fibre uid % or product % already belongs to another fibre',
                p_fiber_uid, p_product_id;
        END IF;
        INSERT INTO production.prod_fiber
            (id, tenant_id, uid, product_id, fiber_category_id, fiber_iso_code_id, fiber_name,
             status, composition, material_source, is_active, created_at, updated_at, version)
        VALUES
            (p_fiber_id, catalog_owner, p_fiber_uid, p_product_id, category_id, iso.id,
             btrim(p_fiber_name), 'ACTIVE', '{}'::jsonb, NULL, TRUE,
             CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
    END IF;

    -- Restore the caller's binding. An unset binding stays on the catalogue owner until the end
    -- of the (migration) transaction: an empty value would break every uuid-cast RLS policy.
    IF previous_tenant IS NOT NULL AND previous_tenant <> '' THEN
        PERFORM set_config('app.current_tenant', previous_tenant, true);
    END IF;
END;
$$;

COMMENT ON FUNCTION production.publish_fiber_catalog_entry(TEXT, UUID, TEXT, UUID, TEXT, TEXT) IS
    'FIBER-CATALOG-1: publish or re-verify one canonical shared pure fibre from an explicit seed key.';

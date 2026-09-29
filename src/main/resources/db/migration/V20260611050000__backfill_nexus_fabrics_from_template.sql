-- ============================================================================
-- V20260611050000: Backfill nexus-fabrics demo tenant from golden-template
-- ============================================================================
-- Architecture chain: golden-template → nexus-fabrics → playground tenants
--
-- golden-template holds canonical master/reference data.
-- nexus-fabrics is the demo company used as playground clone source.
-- This migration provisions nexus-fabrics with reference data from
-- golden-template so that playground cloning produces complete tenants.
--
-- Fibers (prod_fiber, prod_product) and the fibre reference tables (categories, ISO codes,
-- certification schemes) are NOT copied — they are one shared catalogue read through the
-- catalogue-owner scope (PF4, FIBER-CATALOG-1).
--
-- Idempotent: WHERE NOT EXISTS guards prevent duplicates on re-run or
-- environments where nexus-fabrics already has data.
-- ============================================================================

DO $$
DECLARE
    template_id  UUID := '00000000-0000-0000-ffff-000000000001';
    nexus_id     UUID;
BEGIN
    -- Resolve nexus-fabrics tenant ID dynamically
    SELECT id INTO nexus_id
    FROM common_tenant.common_tenant
    WHERE slug = 'nexus-fabrics'
    LIMIT 1;

    IF nexus_id IS NULL THEN
        RAISE NOTICE 'nexus-fabrics tenant not found — skipping backfill (non-playground environment).';
        RETURN;
    END IF;

    RAISE NOTICE 'Backfilling nexus-fabrics (%) from golden-template (%)', nexus_id, template_id;

    -- 1-3. Fibre categories, certification schemes and ISO codes are NOT copied
    -- (FIBER-CATALOG-1): they are one shared catalogue owned by the golden template and read
    -- by every tenant without a tenant copy.

    -- 4. prod_product_attribute
    INSERT INTO production.prod_product_attribute (id, tenant_id, uid, attribute_code, attribute_name, attribute_group, description, display_order, product_scope, is_active, created_at, updated_at, version)
    SELECT gen_random_uuid(), nexus_id, gen_random_uuid()::varchar,
           attribute_code, attribute_name, attribute_group, description, display_order, product_scope, is_active, now(), now(), 0
    FROM production.prod_product_attribute
    WHERE tenant_id = template_id
      AND NOT EXISTS (
          SELECT 1 FROM production.prod_product_attribute c2
          WHERE c2.tenant_id = nexus_id AND c2.attribute_code = production.prod_product_attribute.attribute_code
      );

    -- 5. prod_yarn_category
    INSERT INTO production.prod_yarn_category (id, tenant_id, uid, category_code, category_name, description, is_active, created_at, updated_at, version)
    SELECT gen_random_uuid(), nexus_id, gen_random_uuid()::varchar,
           category_code, category_name, description, is_active, now(), now(), 0
    FROM production.prod_yarn_category
    WHERE tenant_id = template_id
      AND NOT EXISTS (
          SELECT 1 FROM production.prod_yarn_category c2
          WHERE c2.tenant_id = nexus_id AND c2.category_code = production.prod_yarn_category.category_code
      );

    -- 6. prod_yarn_attribute
    INSERT INTO production.prod_yarn_attribute (id, tenant_id, uid, attribute_code, attribute_name, attribute_type, unit, description, is_active, created_at, updated_at, version)
    SELECT gen_random_uuid(), nexus_id, gen_random_uuid()::varchar,
           attribute_code, attribute_name, attribute_type, unit, description, is_active, now(), now(), 0
    FROM production.prod_yarn_attribute
    WHERE tenant_id = template_id
      AND NOT EXISTS (
          SELECT 1 FROM production.prod_yarn_attribute c2
          WHERE c2.tenant_id = nexus_id AND c2.attribute_code = production.prod_yarn_attribute.attribute_code
      );

    -- 7. prod_yarn_certification
    INSERT INTO production.prod_yarn_certification (id, tenant_id, uid, certification_code, certification_name, certifying_body, description, is_active, created_at, updated_at, version)
    SELECT gen_random_uuid(), nexus_id, gen_random_uuid()::varchar,
           certification_code, certification_name, certifying_body, description, is_active, now(), now(), 0
    FROM production.prod_yarn_certification
    WHERE tenant_id = template_id
      AND NOT EXISTS (
          SELECT 1 FROM production.prod_yarn_certification c2
          WHERE c2.tenant_id = nexus_id AND c2.certification_code = production.prod_yarn_certification.certification_code
      );

    RAISE NOTICE 'Backfill completed for nexus-fabrics.';
END $$;

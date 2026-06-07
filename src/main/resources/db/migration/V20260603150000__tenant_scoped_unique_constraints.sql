-- ========================================================================
-- Tenant-Scoped Unique Constraints Migration
-- ========================================================================
-- This migration drops global UNIQUE constraints on reference tables
-- (which are cloned per tenant) and replaces them with tenant-scoped ones.
-- 
-- Example: UNIQUE (category_code) -> UNIQUE (tenant_id, category_code)

DO $$ 
DECLARE
  rec record;
BEGIN
  -- We loop over tables and their columns that should be tenant-scoped
  FOR rec IN (
    VALUES 
      ('production', 'prod_fiber_category', 'uid'),
      ('production', 'prod_fiber_category', 'category_code'),
      ('production', 'prod_product_attribute', 'uid'),
      ('production', 'prod_product_attribute', 'attribute_code'),
      ('production', 'prod_fiber_certification', 'uid'),
      ('production', 'prod_fiber_certification', 'certification_code'),
      ('production', 'prod_fiber_iso_code', 'uid'),
      ('production', 'prod_fiber_iso_code', 'iso_code'),
      ('production', 'prod_yarn_category', 'uid'),
      ('production', 'prod_yarn_category', 'category_code'),
      ('production', 'prod_yarn_attribute', 'uid'),
      ('production', 'prod_yarn_attribute', 'attribute_code'),
      ('production', 'prod_yarn_certification', 'uid'),
      ('production', 'prod_yarn_certification', 'certification_code'),
      ('human', 'human_hr_policy_pack', 'uid'),
      ('notification', 'notification_template', 'uid'),
      ('i18n', 'translation_key', 'uid'),
      ('i18n', 'translation_value', 'uid'),
      ('i18n', 'supported_locale', 'uid'),
      ('i18n', 'supported_locale', 'code'),
      ('costing', 'cost_item', 'uid'),
      ('costing', 'cost_item', 'code')
  ) LOOP
    -- Drop existing unique constraints on the column
    DECLARE
      constraint_name text;
    BEGIN
      FOR constraint_name IN (
        SELECT tc.constraint_name 
        FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage AS ccu USING (constraint_schema, constraint_name)
        WHERE constraint_type = 'UNIQUE' 
          AND tc.table_schema = rec.column1 
          AND tc.table_name = rec.column2 
          AND ccu.column_name = rec.column3
      ) LOOP
        EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT IF EXISTS %I', rec.column1, rec.column2, constraint_name);
      END LOOP;

      -- Add tenant-scoped unique constraint (only if not already exists)
      -- Naming convention: uq_{table}_{column}_tenant
      BEGIN
        EXECUTE format('ALTER TABLE %I.%I ADD CONSTRAINT %I UNIQUE (tenant_id, %I)', 
                       rec.column1, rec.column2, 'uq_' || rec.column2 || '_' || rec.column3 || '_tenant', rec.column3);
      EXCEPTION WHEN duplicate_table OR duplicate_object THEN
        -- Ignore if already exists
      END;
    END;
  END LOOP;

  -- Handle translation_key's explicitly named constraint uq_translation_key_code
  ALTER TABLE i18n.translation_key DROP CONSTRAINT IF EXISTS uq_translation_key_code;
  BEGIN
    ALTER TABLE i18n.translation_key ADD CONSTRAINT uq_translation_key_code_tenant UNIQUE (tenant_id, key_code);
  EXCEPTION WHEN duplicate_table OR duplicate_object THEN
  END;

  -- Drop unique index on common_routing_config uid
  DROP INDEX IF EXISTS common_communication.idx_rc_uid;
  CREATE UNIQUE INDEX IF NOT EXISTS idx_rc_uid_tenant ON common_communication.common_routing_config(tenant_id, uid) WHERE uid IS NOT NULL;
END $$;

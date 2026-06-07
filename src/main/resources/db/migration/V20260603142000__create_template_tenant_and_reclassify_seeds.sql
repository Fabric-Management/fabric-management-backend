-- T4-Phase4: Create TEMPLATE tenant + reclassify SYSTEM seed data.
--
-- Decision: SYSTEM_TENANT_ID (00000000-...-000000000000) is a deny-by-default sentinel ONLY.
-- All shared reference data (fiber categories, certifications, roles, etc.) moves to
-- the TEMPLATE tenant (00000000-0000-0000-ffff-000000000001) and gets cloned per-tenant
-- during onboarding.
--
-- This eliminates the need for any "system read" RLS exception policies.
--
-- ⚠️ CR-10: MIGRATION ORDERING DEPENDENCY
-- This migration (V142000) moves seed data tenant_id values. The companion migration
-- V20260603150000__tenant_scoped_unique_constraints.sql drops global UNIQUE constraints
-- and replaces them with tenant-scoped (tenant_id, column) pairs. Any migration added
-- between V142000 and V150000 that relies on global uniqueness of uid/code columns
-- in these tables will FAIL. Keep these two migrations logically adjacent.

-- ========================================================================
-- 1. Create TEMPLATE tenant (golden template for cloning)
-- ========================================================================
INSERT INTO common_tenant.common_tenant (
    id, uid, slug, name, type, status, settings,
    is_active, created_at, updated_at, version
) VALUES (
    '00000000-0000-0000-ffff-000000000001'::uuid,
    'TEMPLATE-001',
    'golden-template',
    'Golden Template',
    'TEMPLATE',
    'ACTIVE',
    '{"timezone":"UTC","locale":"en-US","currency":"USD","betaFeaturesEnabled":false,"aiEnabled":true,"emailNotificationsEnabled":true,"mfaRequired":false,"sessionTimeoutMinutes":480}'::jsonb,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
)
ON CONFLICT (id) DO NOTHING;

-- ========================================================================
-- 2. Reclassify: SYSTEM_TENANT_ID → TEMPLATE_TENANT_ID
--    Move seed data from 00000000-...-000000000000 to TEMPLATE tenant.
-- ========================================================================

-- 2a. Production masterdata (fiber)
UPDATE production.prod_fiber_category
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

UPDATE production.prod_product_attribute
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

UPDATE production.prod_fiber_certification
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

UPDATE production.prod_fiber_iso_code
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

-- 2b. Production masterdata (yarn)
UPDATE production.prod_yarn_category
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

UPDATE production.prod_yarn_attribute
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

UPDATE production.prod_yarn_certification
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

-- 2c. Human resources
UPDATE human.human_hr_policy_pack
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

-- 2d. Notification templates
UPDATE notification.notification_template
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

-- 2e. i18n (translation keys, values, supported locales)
UPDATE i18n.translation_key
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

UPDATE i18n.translation_value
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

-- i18n supported_locale was seeded with ...0001 (SYSTEM_ACTOR_ID) — anomaly fix
UPDATE i18n.supported_locale
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000001'::uuid;

-- ========================================================================
-- 3. Fix NULL tenant_id seed data → TEMPLATE tenant
-- ========================================================================

-- Costing cost items (seeded with NULL tenant_id)
UPDATE costing.cost_item
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id IS NULL;

-- Communication routing config (seeded with NULL tenant_id)
UPDATE common_communication.common_routing_config
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id IS NULL;

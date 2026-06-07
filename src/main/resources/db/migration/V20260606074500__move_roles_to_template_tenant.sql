-- Reclassify system roles from SYSTEM tenant to TEMPLATE tenant.
--
-- The R__002 repeatable migration seeds roles under SYSTEM_TENANT_ID (000...000).
-- Per-tenant role model requires these to live under TEMPLATE tenant
-- (000...ffff...001) so they can be cloned during onboarding.
--
-- Also update the repeatable migration to seed under TEMPLATE going forward.

-- Move existing SYSTEM roles → TEMPLATE
UPDATE common_user.common_role
SET tenant_id = '00000000-0000-0000-ffff-000000000001'::uuid
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'::uuid;

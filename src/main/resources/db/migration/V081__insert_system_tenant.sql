-- ═══════════════════════════════════════════════════════════════════════════
-- V081__insert_system_tenant.sql
-- ═══════════════════════════════════════════════════════════════════════════
-- Inserts the system/platform tenant (SYSTEM_TENANT_ID) into common_tenant.
--
-- Required for: common_notification.fk_notif_tenant — platform-level
-- notifications (FIBER_REQUEST_SUBMITTED, NEW_TENANT_ONBOARDED) use
-- tenant_id = SYSTEM_TENANT_ID. Without this row, the FK constraint fails.
--
-- SYSTEM_TENANT_ID = '00000000-0000-0000-0000-000000000000'
-- Used by: TenantContext.SYSTEM_TENANT_ID, fiber reference tables,
-- platform notifications, system roles, etc.
--
-- Risk: LOW — additive, idempotent
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO common_tenant.common_tenant (
    id,
    uid,
    slug,
    name,
    billing_email,
    status,
    trial_ends_at,
    subscription_plan,
    settings,
    is_active,
    created_at,
    created_by,
    updated_at,
    updated_by,
    version
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

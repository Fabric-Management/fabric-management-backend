-- ============================================================
-- FAZ 1: User locale and timezone preference columns
-- ============================================================
-- Adds user-level locale and timezone preferences to common_user.
-- This enables the 3-tier cascade: User -> Tenant -> System Default (EN)
-- Nullable: users that haven't set a preference inherit from tenant settings.
-- ============================================================

ALTER TABLE common_user.common_user
    ADD COLUMN IF NOT EXISTS preferred_locale   VARCHAR(10),
    ADD COLUMN IF NOT EXISTS preferred_timezone VARCHAR(50);

COMMENT ON COLUMN common_user.common_user.preferred_locale   IS
    'User preferred locale tag (e.g. en-US, tr-TR). NULL = inherit from tenant settings.';
COMMENT ON COLUMN common_user.common_user.preferred_timezone IS
    'User preferred IANA timezone (e.g. Europe/Istanbul). NULL = inherit from tenant settings.';

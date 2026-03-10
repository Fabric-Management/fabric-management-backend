-- ============================================================================
-- V067: Rename company_id to organization_id (auth)
-- ============================================================================
-- Completes Company → Organization terminology for common_auth.common_registration_token.
-- Note: common_company_contact and common_company_address were already migrated
-- in V046 (renamed to common_organization_contact/address with organization_id).
-- ============================================================================

-- ============================================================================
-- 1. common_auth.common_registration_token
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'common_auth'
          AND table_name = 'common_registration_token'
          AND column_name = 'company_id'
    ) THEN
        ALTER TABLE common_auth.common_registration_token
            RENAME COLUMN company_id TO organization_id;
        COMMENT ON COLUMN common_auth.common_registration_token.organization_id IS
            'Organization ID - set when token is used (links user to organization).';
    END IF;
END $$;

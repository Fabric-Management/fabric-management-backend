-- ============================================================================
-- V18: AuthUser Contact Integration - New Communication System
-- ============================================================================
-- Adds contact_id to AuthUser for new communication system integration
-- Maintains backward compatibility with deprecated contactValue/contactType
-- Last Updated: 2025-11-01
-- ============================================================================

-- ============================================================================
-- ALTER TABLE: common_auth_user
-- ============================================================================

-- Add contact_id column (nullable initially for migration)
ALTER TABLE common_auth.common_auth_user
ADD COLUMN IF NOT EXISTS contact_id UUID;

-- Create index for contact_id (unique after migration)
CREATE INDEX IF NOT EXISTS idx_auth_user_contact_id ON common_auth.common_auth_user(contact_id);

-- Add foreign key constraint (after migration, set nullable = false)
-- Note: Foreign key is deferred until migration completes
-- ALTER TABLE common_auth.common_auth_user
-- ADD CONSTRAINT fk_auth_user_contact
--     FOREIGN KEY (contact_id)
--     REFERENCES common_communication.common_contact(id)
--     ON DELETE CASCADE;

-- Migration note:
-- After migrating existing AuthUser records to use contactId, set:
-- ALTER TABLE common_auth.common_auth_user ALTER COLUMN contact_id SET NOT NULL;
-- ALTER TABLE common_auth.common_auth_user ADD CONSTRAINT fk_auth_user_contact
--     FOREIGN KEY (contact_id) REFERENCES common_communication.common_contact(id) ON DELETE CASCADE;

COMMENT ON COLUMN common_auth.common_auth_user.contact_id IS 'References Contact entity (new communication system). NULL allowed during migration period.';
COMMENT ON COLUMN common_auth.common_auth_user.contact_value IS 'DEPRECATED: Use contactId and Contact entity instead. Will be removed after migration.';
COMMENT ON COLUMN common_auth.common_auth_user.contact_type IS 'DEPRECATED: Use contactId and Contact entity instead. Will be removed after migration.';


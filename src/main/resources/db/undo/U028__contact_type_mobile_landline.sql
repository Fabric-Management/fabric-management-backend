-- ==========================================================================
-- Undo Migration: U028__contact_type_mobile_landline.sql
-- Purpose       : Roll back MOBILE / LANDLINE migration to legacy PHONE state
-- ==========================================================================

-- Drop new partial index
DROP INDEX IF EXISTS common_communication.idx_contact_mobile_whatsapp;

-- Revert WhatsApp constraint
ALTER TABLE common_communication.common_contact
    DROP CONSTRAINT IF EXISTS chk_contact_whatsapp_mobile;

-- Collapse MOBILE + LANDLINE back into PHONE
UPDATE common_communication.common_contact
   SET contact_type = 'PHONE'
 WHERE contact_type IN ('MOBILE', 'LANDLINE');

-- Restore original contact type constraint
ALTER TABLE common_communication.common_contact
    DROP CONSTRAINT IF EXISTS chk_contact_type;

ALTER TABLE common_communication.common_contact
    ADD CONSTRAINT chk_contact_type
    CHECK (contact_type IN ('EMAIL', 'PHONE', 'PHONE_EXTENSION', 'FAX', 'WEBSITE', 'SOCIAL_MEDIA'));

-- Recreate legacy partial index
CREATE INDEX IF NOT EXISTS idx_contact_phone_whatsapp
    ON common_communication.common_contact (tenant_id)
    WHERE contact_type = 'PHONE' AND is_whatsapp = TRUE;


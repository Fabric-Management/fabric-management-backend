-- ==========================================================================
-- Migration: V028__contact_type_mobile_landline.sql
-- Purpose   : Introduce MOBILE / LANDLINE contact types and guard WhatsApp flag
-- ==========================================================================

-- Drop legacy partial index (will be recreated for MOBILE contacts)
DROP INDEX IF EXISTS common_communication.idx_contact_phone_whatsapp;

-- Migrate existing PHONE contact types to MOBILE / LANDLINE based on pattern
DO $$
DECLARE
    v_mobile BIGINT := 0;
    v_landline BIGINT := 0;
    v_remaining BIGINT := 0;
BEGIN
    -- Treat strict E.164 numbers as MOBILE
    UPDATE common_communication.common_contact
       SET contact_type = 'MOBILE'
     WHERE contact_type = 'PHONE'
       AND contact_value ~ '^\+[1-9][0-9]{7,14}$';
    GET DIAGNOSTICS v_mobile = ROW_COUNT;

    -- Remaining PHONE entries become LANDLINE by default
    UPDATE common_communication.common_contact
       SET contact_type = 'LANDLINE'
     WHERE contact_type = 'PHONE';
    GET DIAGNOSTICS v_landline = ROW_COUNT;

    -- Safety net: ensure no PHONE types remain
    SELECT COUNT(*) INTO v_remaining
      FROM common_communication.common_contact
     WHERE contact_type = 'PHONE';

    IF v_remaining > 0 THEN
        UPDATE common_communication.common_contact
           SET contact_type = 'MOBILE'
         WHERE contact_type = 'PHONE';
        RAISE NOTICE 'Remaining % PHONE contacts defaulted to MOBILE', v_remaining;
    END IF;

    RAISE NOTICE 'Migrated % contacts to MOBILE', v_mobile;
    RAISE NOTICE 'Migrated % contacts to LANDLINE', v_landline;
END $$;

-- Update contact type constraint to include MOBILE / LANDLINE
ALTER TABLE common_communication.common_contact
    DROP CONSTRAINT IF EXISTS chk_contact_type;

ALTER TABLE common_communication.common_contact
    ADD CONSTRAINT chk_contact_type
    CHECK (contact_type IN ('EMAIL', 'MOBILE', 'LANDLINE', 'PHONE_EXTENSION', 'FAX', 'WEBSITE', 'SOCIAL_MEDIA'));

-- Enforce WhatsApp usage only for MOBILE contacts
ALTER TABLE common_communication.common_contact
    DROP CONSTRAINT IF EXISTS chk_contact_whatsapp_mobile;

ALTER TABLE common_communication.common_contact
    ADD CONSTRAINT chk_contact_whatsapp_mobile
    CHECK (is_whatsapp = FALSE OR contact_type = 'MOBILE');

-- Recreate partial index for WhatsApp-enabled mobile contacts
CREATE INDEX IF NOT EXISTS idx_contact_mobile_whatsapp
    ON common_communication.common_contact (tenant_id)
    WHERE contact_type = 'MOBILE' AND is_whatsapp = TRUE;

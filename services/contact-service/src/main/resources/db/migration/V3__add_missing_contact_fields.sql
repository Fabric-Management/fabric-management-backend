-- =====================================================
-- Contact Service Database Migration
-- Version: V3
-- Description: Add missing fields to contacts table
-- =====================================================

-- Add basic identity fields to contacts table
ALTER TABLE contacts
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(200);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_contacts_first_name ON contacts(first_name);
CREATE INDEX IF NOT EXISTS idx_contacts_last_name ON contacts(last_name);
CREATE INDEX IF NOT EXISTS idx_contacts_display_name ON contacts(display_name);

-- Update display_name for existing records
UPDATE contacts 
SET display_name = COALESCE(
    NULLIF(TRIM(CONCAT(COALESCE(first_name, ''), ' ', COALESCE(last_name, ''))), ''),
    'Unknown Contact'
)
WHERE display_name IS NULL;
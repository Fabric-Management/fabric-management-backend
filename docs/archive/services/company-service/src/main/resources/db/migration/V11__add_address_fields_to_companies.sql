-- =============================================================================
-- Migration V11: Add Address Fields to Companies
-- =============================================================================
-- Adds official address fields for company registration
-- Required for tenant onboarding process

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS address_line1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS district VARCHAR(100),
    ADD COLUMN IF NOT EXISTS postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS country VARCHAR(100) NOT NULL DEFAULT 'Turkey';

CREATE INDEX IF NOT EXISTS idx_companies_city ON companies(city) WHERE city IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_companies_country ON companies(country);

COMMENT ON COLUMN companies.address_line1 IS 'Primary address (street, number, neighborhood)';
COMMENT ON COLUMN companies.address_line2 IS 'Secondary address (building, apartment) - optional';
COMMENT ON COLUMN companies.city IS 'City/Province (İl)';
COMMENT ON COLUMN companies.district IS 'District (İlçe) - optional';
COMMENT ON COLUMN companies.postal_code IS 'Postal code';
COMMENT ON COLUMN companies.country IS 'Country - default: Turkey';


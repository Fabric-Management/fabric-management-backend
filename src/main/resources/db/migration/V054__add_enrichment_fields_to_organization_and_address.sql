-- Onboarding enrichment: add company profile fields to Organization
ALTER TABLE common_company.common_organization
  ADD COLUMN IF NOT EXISTS legal_name          VARCHAR(200),
  ADD COLUMN IF NOT EXISTS registration_number VARCHAR(100),
  ADD COLUMN IF NOT EXISTS industry            VARCHAR(100),
  ADD COLUMN IF NOT EXISTS website             VARCHAR(500),
  ADD COLUMN IF NOT EXISTS description         TEXT;

-- Onboarding enrichment: add address line 2 to Address
ALTER TABLE common_communication.common_address
  ADD COLUMN IF NOT EXISTS address_line2       VARCHAR(255);

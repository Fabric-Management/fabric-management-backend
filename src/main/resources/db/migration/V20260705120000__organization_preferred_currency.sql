ALTER TABLE common_company.common_organization
  ADD COLUMN IF NOT EXISTS preferred_currency varchar(3);

ALTER TABLE common_company.common_organization
  DROP CONSTRAINT IF EXISTS chk_common_organization_preferred_currency;

ALTER TABLE common_company.common_organization
  ADD CONSTRAINT chk_common_organization_preferred_currency
  CHECK (preferred_currency IS NULL OR preferred_currency ~ '^[A-Z]{3}$');

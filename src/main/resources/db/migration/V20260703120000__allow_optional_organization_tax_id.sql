-- Tax ID becomes optional at organization creation (IDENTITY-8: founding a
-- second organization from an existing account collects it later in
-- onboarding). UNIQUE (tenant_id, tax_id) is unaffected: Postgres treats
-- NULLs as distinct, so multiple tax-id-less organizations are allowed.
ALTER TABLE common_company.common_organization
  ALTER COLUMN tax_id DROP NOT NULL;

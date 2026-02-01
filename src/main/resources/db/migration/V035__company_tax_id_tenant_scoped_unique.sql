-- ============================================================================
-- V035: Company tax_id - tenant-scoped unique constraint
-- ============================================================================
-- Changes tax_id from global UNIQUE to tenant-scoped UNIQUE(tenant_id, tax_id).
-- This allows different tenants to have companies with the same tax ID
-- (e.g., Tenant A and Tenant B can both add a supplier with tax "1234567890").
--
-- Previously: tax_id was globally unique - only one company in entire DB could
-- have a given tax ID. Service layer expected tenant-scoped uniqueness.
--
-- Now: (tenant_id, tax_id) composite unique - each tenant has own namespace.
-- ============================================================================

-- Drop existing global unique constraint on tax_id
-- PostgreSQL default name: {table}_{column}_key
ALTER TABLE common_company.common_company
    DROP CONSTRAINT IF EXISTS common_company_tax_id_key;

-- Add tenant-scoped composite unique constraint
ALTER TABLE common_company.common_company
    ADD CONSTRAINT uk_company_tenant_tax_id UNIQUE (tenant_id, tax_id);

COMMENT ON CONSTRAINT uk_company_tenant_tax_id ON common_company.common_company
    IS 'Tax ID uniqueness scoped per tenant - different tenants can have same tax ID';

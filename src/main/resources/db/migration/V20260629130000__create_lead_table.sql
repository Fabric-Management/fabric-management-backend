CREATE TABLE IF NOT EXISTS common_company.common_lead (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid VARCHAR(100) NOT NULL UNIQUE,
    company_name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(50) NOT NULL,
    organization_type VARCHAR(50) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    work_email VARCHAR(255) NOT NULL,
    selected_os JSONB NOT NULL DEFAULT '[]'::jsonb,
    signup_intent VARCHAR(50) NOT NULL,
    trial_tenant_id UUID NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID NULL,
    deleted_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_common_lead_trial_tenant
        FOREIGN KEY (trial_tenant_id)
        REFERENCES common_tenant.common_tenant(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_common_lead_work_email
    ON common_company.common_lead (work_email);

CREATE INDEX IF NOT EXISTS idx_common_lead_trial_tenant
    ON common_company.common_lead (trial_tenant_id);

CREATE INDEX IF NOT EXISTS idx_common_lead_created_at
    ON common_company.common_lead (created_at);

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE common_company.common_lead TO fabric_app;
  END IF;

  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE common_company.common_lead TO fabric_system;
  END IF;
END $$;

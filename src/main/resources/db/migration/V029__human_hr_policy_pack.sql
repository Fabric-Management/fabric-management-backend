-- V029: HR Policy Pack tables
-- Purpose: store tenant and country specific HR localization packs with versioning

CREATE TABLE IF NOT EXISTS human.human_hr_policy_pack (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    pack_code VARCHAR(100) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    pack_version INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    payload JSONB,
    checksum VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE human.human_hr_policy_pack IS 'HR localization policy packs per tenant and country.';
COMMENT ON COLUMN human.human_hr_policy_pack.pack_code IS 'Business identifier for the policy pack.';
COMMENT ON COLUMN human.human_hr_policy_pack.country_code IS 'Country code (ISO 3166-1 alpha-2 or GLOBAL).';
COMMENT ON COLUMN human.human_hr_policy_pack.status IS 'Pack lifecycle status: DRAFT, ACTIVE, RETIRED.';
COMMENT ON COLUMN human.human_hr_policy_pack.payload IS 'Serialized configuration payload (JSON).';

CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_policy_pack_code
    ON human.human_hr_policy_pack (tenant_id, pack_code, pack_version);

CREATE INDEX IF NOT EXISTS idx_hr_policy_pack_tenant_country
    ON human.human_hr_policy_pack (tenant_id, country_code, status);

CREATE OR REPLACE FUNCTION human.update_hr_policy_pack_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_hr_policy_pack_updated_at ON human.human_hr_policy_pack;

CREATE TRIGGER trg_hr_policy_pack_updated_at
BEFORE UPDATE ON human.human_hr_policy_pack
FOR EACH ROW
EXECUTE FUNCTION human.update_hr_policy_pack_timestamp();


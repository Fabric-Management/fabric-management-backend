-- ============================================================================
-- V033: HR Policy Pack Hierarchy & Country Mapping
-- ----------------------------------------------------------------------------
-- Adds hierarchical inheritance support for policy packs and introduces
-- country-to-pack mapping table.
-- Last Updated: 2025-11-09
-- ============================================================================

ALTER TABLE human.human_hr_policy_pack
    ADD COLUMN parent_pack_id UUID,
    ADD COLUMN parent_pack_code VARCHAR(100),
    ADD COLUMN region_code VARCHAR(50),
    ADD COLUMN inheritance_mode VARCHAR(20) NOT NULL DEFAULT 'FULL';

ALTER TABLE human.human_hr_policy_pack
    ADD CONSTRAINT fk_hr_policy_pack_parent
        FOREIGN KEY (parent_pack_id) REFERENCES human.human_hr_policy_pack(id);

CREATE INDEX IF NOT EXISTS idx_hr_policy_pack_parent
    ON human.human_hr_policy_pack(parent_pack_id);

CREATE INDEX IF NOT EXISTS idx_hr_policy_pack_region
    ON human.human_hr_policy_pack(tenant_id, region_code);

UPDATE human.human_hr_policy_pack
SET inheritance_mode = 'FULL'
WHERE inheritance_mode IS NULL;

-- Seed baseline rows (idempotent upsert style)
INSERT INTO human.human_hr_policy_pack (id, tenant_id, pack_code, pack_version, country_code, name, status, payload, checksum, inheritance_mode)
SELECT gen_random_uuid(), tenant_id, 'GLOBAL-BASE', 1, 'GLOBAL', 'Global Baseline', 'ACTIVE', '{}', null, 'FULL'
FROM (SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack) t
ON CONFLICT (tenant_id, pack_code, pack_version) DO NOTHING;

INSERT INTO human.human_hr_policy_pack (id, tenant_id, pack_code, pack_version, country_code, name, status, payload, checksum, inheritance_mode, parent_pack_id, parent_pack_code, region_code)
SELECT gen_random_uuid(), tenant_id, 'EU-BASELINE', 1, 'EU', 'EU Baseline', 'ACTIVE', '{}', null, 'PARTIAL',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'GLOBAL-BASE' ORDER BY pack_version DESC LIMIT 1),
    'GLOBAL-BASE', 'EU'
FROM (SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack) t
ON CONFLICT (tenant_id, pack_code, pack_version) DO NOTHING;

-- ============================================================================
-- Helper function ensures child packs reference parent IDs
-- ============================================================================
DO $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT child.id AS child_id, parent.id AS parent_id
        FROM human.human_hr_policy_pack child
        JOIN human.human_hr_policy_pack parent
          ON child.tenant_id = parent.tenant_id
         AND child.parent_pack_code = parent.pack_code
         AND parent.status = 'ACTIVE'
        WHERE child.parent_pack_id IS NULL
    LOOP
        UPDATE human.human_hr_policy_pack
        SET parent_pack_id = rec.parent_id
        WHERE id = rec.child_id;
    END LOOP;
END$$;

-- ============================================================================
-- TABLE: human_hr_country_pack_mapping
-- ============================================================================
CREATE TABLE IF NOT EXISTS human.human_hr_country_pack_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    pack_code VARCHAR(100) NOT NULL,
    pack_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_country_pack UNIQUE (tenant_id, country_code),
    CONSTRAINT fk_country_pack_policy FOREIGN KEY (pack_id)
        REFERENCES human.human_hr_policy_pack(id)
);

CREATE INDEX IF NOT EXISTS idx_country_pack_code
    ON human.human_hr_country_pack_mapping(tenant_id, pack_code);


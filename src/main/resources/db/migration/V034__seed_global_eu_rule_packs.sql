-- ============================================================================
-- V034: Seed Global/EU Rule Packs and Country Mappings
-- ----------------------------------------------------------------------------
-- Inserts baseline data for GLOBAL-BASE, EU-BASELINE, and country-specific
-- packs with inheritance relationships.
-- Last Updated: 2025-11-09
-- ============================================================================

WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_policy_pack (id, tenant_id, pack_code, pack_version, country_code, name, status, payload, checksum, inheritance_mode)
SELECT gen_random_uuid(), t.tenant_id, 'GLOBAL-BASE', 1, 'GLOBAL', 'Global Baseline Pack', 'ACTIVE',
    '{"leave":{"default":{"annualLeaveDays":20,"carryOverDays":5}},"payroll":{"taxBrackets":[],"currency":"USD"}}',
    null, 'FULL'
FROM tenant_ids t
ON CONFLICT (tenant_id, pack_code, pack_version) DO NOTHING;

WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_policy_pack (id, tenant_id, pack_code, pack_version, country_code, name, status, payload, checksum, inheritance_mode, parent_pack_id, parent_pack_code, region_code)
SELECT gen_random_uuid(), t.tenant_id, 'EU-BASELINE', 1, 'EU', 'EU Baseline Pack', 'ACTIVE',
    '{"leave":{"default":{"annualLeaveDays":20,"carryOverDays":10}},"payroll":{"currency":"EUR","socialContributions":{"pension":0.1,"healthcare":0.07,"unemployment":0.03}}}',
    null, 'PARTIAL',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'GLOBAL-BASE' ORDER BY pack_version DESC LIMIT 1),
    'GLOBAL-BASE', 'EU'
FROM tenant_ids t
ON CONFLICT (tenant_id, pack_code, pack_version) DO NOTHING;

-- Country-specific packs inheriting from EU baseline
WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_policy_pack (id, tenant_id, pack_code, pack_version, country_code, name, status, payload, checksum, inheritance_mode, parent_pack_id, parent_pack_code, region_code)
SELECT gen_random_uuid(), t.tenant_id, c.pack_code, 1, c.country_code, c.name, 'ACTIVE',
    c.payload, null, 'PARTIAL',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'EU-BASELINE' ORDER BY pack_version DESC LIMIT 1),
    'EU-BASELINE', 'EU'
FROM tenant_ids t
CROSS JOIN (
    VALUES
        ('FR', 'FR', 'France Localization Pack', '{"leave":{"annual":{"carryOverDays":15}},"payroll":{"currency":"EUR","benefits":{"mealVoucher":9.5}}}'),
        ('DE', 'DE', 'Germany Localization Pack', '{"leave":{"annual":{"carryOverDays":20}},"payroll":{"socialContributions":{"solidarity":0.055}}}'),
        ('IT', 'IT', 'Italy Localization Pack', '{"leave":{"annual":{"thirteenthMonth":true}},"payroll":{"benefits":{"thirteenthSalary":true}}}'),
        ('ES', 'ES', 'Spain Localization Pack', '{"leave":{"annual":{"carryOverDays":12}},"payroll":{"benefits":{"vacationBonus":0.05}}}'),
        ('UK', 'UK', 'United Kingdom Localization Pack', '{"leave":{"annual":{"annualLeaveDays":28,"bankHolidaysIncluded":true}},"payroll":{"currency":"GBP","nationalInsurance":{"employee":0.132,"employer":0.138}}}')
) AS c(country_code, pack_code, name, payload)
ON CONFLICT (tenant_id, pack_code, pack_version) DO NOTHING;

-- Direct country packs inheriting from GLOBAL
WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_policy_pack (id, tenant_id, pack_code, pack_version, country_code, name, status, payload, checksum, inheritance_mode, parent_pack_id, parent_pack_code)
SELECT gen_random_uuid(), t.tenant_id, c.pack_code, 1, c.country_code, c.name, 'ACTIVE',
    c.payload, null, 'PARTIAL',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'GLOBAL-BASE' ORDER BY pack_version DESC LIMIT 1),
    'GLOBAL-BASE'
FROM tenant_ids t
CROSS JOIN (
    VALUES
        ('TR', 'TR', 'Turkey Localization Pack', '{"leave":{"annual":{"probationDays":60}},"payroll":{"currency":"TRY","socialContributions":{"sgkEmployee":0.14,"sgkEmployer":0.205}}}'),
        ('US', 'US', 'United States Localization Pack', '{"leave":{"annual":{"federalHolidaysIncluded":false}},"payroll":{"currency":"USD","federalTax":{"defaultBracket":"2025"}}}')
) AS c(country_code, pack_code, name, payload)
ON CONFLICT (tenant_id, pack_code, pack_version) DO NOTHING;

-- Update parent IDs post insert
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
        WHERE child.parent_pack_id IS NULL
    LOOP
        UPDATE human.human_hr_policy_pack
        SET parent_pack_id = rec.parent_id
        WHERE id = rec.child_id;
    END LOOP;
END$$;

-- Country mapping table entries
WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_country_pack_mapping (id, tenant_id, uid, country_code, pack_code, pack_id)
SELECT gen_random_uuid(), t.tenant_id, concat(t.tenant_id::text,'-TR'), 'TR', 'TR',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'TR' ORDER BY pack_version DESC LIMIT 1)
FROM tenant_ids t
ON CONFLICT (tenant_id, country_code) DO NOTHING;

WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_country_pack_mapping (id, tenant_id, uid, country_code, pack_code, pack_id)
SELECT gen_random_uuid(), t.tenant_id, concat(t.tenant_id::text,'-US'), 'US', 'US',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'US' ORDER BY pack_version DESC LIMIT 1)
FROM tenant_ids t
ON CONFLICT (tenant_id, country_code) DO NOTHING;

WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_country_pack_mapping (id, tenant_id, uid, country_code, pack_code, pack_id)
SELECT gen_random_uuid(), t.tenant_id, concat(t.tenant_id::text,'-UK'), 'UK', 'UK',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'UK' ORDER BY pack_version DESC LIMIT 1)
FROM tenant_ids t
ON CONFLICT (tenant_id, country_code) DO NOTHING;

WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_country_pack_mapping (id, tenant_id, uid, country_code, pack_code, pack_id)
SELECT gen_random_uuid(), t.tenant_id, concat(t.tenant_id::text,'-FR'), 'FR', 'FR',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'FR' ORDER BY pack_version DESC LIMIT 1)
FROM tenant_ids t
ON CONFLICT (tenant_id, country_code) DO NOTHING;

WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_country_pack_mapping (id, tenant_id, uid, country_code, pack_code, pack_id)
SELECT gen_random_uuid(), t.tenant_id, concat(t.tenant_id::text,'-DE'), 'DE', 'DE',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'DE' ORDER BY pack_version DESC LIMIT 1)
FROM tenant_ids t
ON CONFLICT (tenant_id, country_code) DO NOTHING;

WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_country_pack_mapping (id, tenant_id, uid, country_code, pack_code, pack_id)
SELECT gen_random_uuid(), t.tenant_id, concat(t.tenant_id::text,'-ES'), 'ES', 'ES',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'ES' ORDER BY pack_version DESC LIMIT 1)
FROM tenant_ids t
ON CONFLICT (tenant_id, country_code) DO NOTHING;

WITH tenant_ids AS (
    SELECT DISTINCT tenant_id FROM human.human_hr_policy_pack
    UNION
    SELECT '00000000-0000-0000-0000-000000000000'::uuid
)
INSERT INTO human.human_hr_country_pack_mapping (id, tenant_id, uid, country_code, pack_code, pack_id)
SELECT gen_random_uuid(), t.tenant_id, concat(t.tenant_id::text,'-IT'), 'IT', 'IT',
    (SELECT id FROM human.human_hr_policy_pack WHERE tenant_id = t.tenant_id AND pack_code = 'IT' ORDER BY pack_version DESC LIMIT 1)
FROM tenant_ids t
ON CONFLICT (tenant_id, country_code) DO NOTHING;


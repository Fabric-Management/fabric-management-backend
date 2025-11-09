# HR Rule Pack Data Model

## Tables

1. **`human_hr_policy_pack`**

   - PK: `id (UUID)`
   - Natural Key: `(tenant_id, pack_code, pack_version)`
   - Columns: `country_code`, `name`, `description`, `status`, `effective_from`, `effective_to`, `payload (jsonb)`, `checksum`, `parent_pack_id`, `parent_pack_code`, `region_code`, `inheritance_mode`

2. **`human_hr_rule_version`**

   - PK: `id`
   - FK: `policy_pack_id`
   - Columns: `rule_type`, `payload_hash`, `payload`, `created_at`

3. **`human_hr_policy_binding`**

   - PK: `id`
   - Columns: `policy_pack_id`, `policy_interface`, `strategy_bean`, `config_reference`

4. **`human_holiday_calendar`**

   - PK: `id`
   - Columns: `country_code`, `year`, `entries jsonb`

5. **`human_hr_country_pack_mapping`**

   - PK: `id`
   - Natural Key: `(tenant_id, country_code)`
   - Columns: `pack_code`, `pack_id`

6. _(Planned)_ **`human_leave_type_definition`**
   - PK: `id`
   - Columns: `country_code`, `code`, `classification`, `attributes jsonb`

## Effective Dating Rules

- `effective_from` inclusive, `effective_to` exclusive.
- No overlapping ACTIVE rows per `(tenant_id, country_code)`.
- Draft rows may overlap but cannot be activated until conflicts resolved.

## Integrity Constraints

- `status` ∈ {`DRAFT`, `ACTIVE`, `RETIRED`}
- `inheritance_mode` ∈ {`FULL`, `PARTIAL`}
- `pack_version` increments by 1 per `(tenant_id, pack_code)`.
- `checksum` stores SHA-256 of serialized payload for tamper detection.
- Trigger `trg_hr_policy_pack_updated_at` keeps `updated_at` consistent.
- When a pack is published, country mappings referencing its code update `pack_id`.

## Lifecycle

1. **Draft**: Created via admin API, payload validated against JSON schema; parent pack referenced if provided.
2. **Validate**: Run calculators in sandbox using draft payload.
3. **Publish**: Transition to `ACTIVE`, set `effective_from`, archive previous active (`effective_to = publishInstant`), update country mappings, clear caches.
4. **Retire**: Move to `RETIRED` (no longer resolved), keep history for audit and lineage.

## Data Access

- Repository: `HrPolicyPackRepository`, `HrCountryPackMappingRepository`
- Services: `HrPolicyPackService`, `HrPolicyPackResolver`, `HrCountryPackMappingService`
- Cache keys:
  - Active pack: `tenantId::countryCode`
  - Resolved hierarchy: `tenantId::countryCode` and `tenantId::PACK::packCode::packVersion`

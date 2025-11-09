# Compliance & Localization Subdomain

**Responsibilities**

- Manage HR policy packs, localization metadata, and legal document references
- Resolve tenant and employee localization contexts with effective-dated rule sets
- Provide policy registries for downstream subdomains (compliance, leave, payroll)

**Inbound Inputs**

- Admin-authored rule pack drafts and publication commands
- Regulatory updates requiring new versions or country coverage

**Outbound Outputs**

- Events: `hr.policy-pack.published`, `hr.policy-pack.retired`
- Resolved policy bundles served to registries at runtime

**Module Structure**

- `api`: admin endpoints for rule-pack lifecycle and country mappings
- `application`: services for pack resolution, caching, publishing, country mapping
- `domain`: policy pack aggregates, lineage metadata, inheritance enums, localization constants
- `infra`: repositories, cache adapters, audit logging connectors

**Hierarchy Highlights**
- Policy packs support parent/child relationships with inheritance modes (`FULL`, `PARTIAL`).
- Country-to-pack mappings stored in `human_hr_country_pack_mapping`, enabling shared baselines.
- Resolver composes payloads top-down, merging overrides and caching resolved bundles (`tenantId+country`).
- Publishing invalidates affected caches and re-links country mappings to latest pack version.
- Example hierarchy: `GLOBAL-BASE` → `EU-BASELINE` → `UK`, `FR`, `DE`, `ES`, `IT`; non-EU packs (`TR`, `US`) inherit directly from global.

**Admin UI Hooks**
- Listing endpoint supports `countryCode`, `regionCode`, `status` filters for quick drill-down.
- `/internal/hr/policy-packs/{packCode}/lineage` exposes resolved payload + breadcrumb lineage used by admin console.
- Country-pack mapping endpoints power region dashboards and cloning utilities.

# Compliance & Audit Model

## Audit Entities

- `HrRuleAuditLog`
  - Fields: `id`, `tenant_id`, `policy_pack_id`, `pack_version`, `action (PUBLISH|RETIRE)`, `actor_id`, `payload_checksum`, `diff_snapshot` _(stores supplied diff plus lineage/parent metadata)_, `occurred_at`
- `ComplianceRun`
  - Fields: `id`, `tenant_id`, `country_code`, `policy_pack_version`, `context (leave|payroll|employee)`, `started_at`, `completed_at`, `status`
- `ComplianceFinding`
  - Fields: `id`, `run_id`, `severity`, `code`, `message`, `entity_reference`, `resolved_at`

## Logging Requirements

- Capture rule pack snapshot (payload + checksum) at runtime execution.
- Record calculator version (Git SHA or artifact version).
- Store exception stack traces for failed compliance checks.
- Include parent pack code, inheritance mode, and lineage in audit payload.

## Reporting

- Queries:
  - `SELECT * FROM ComplianceFinding WHERE severity = 'HIGH' AND resolved_at IS NULL`
  - `SELECT COUNT(*) FROM ComplianceRun WHERE status = 'FAILED' AND created_at > NOW() - INTERVAL '1 day'`
- Dashboard tiles: unresolved findings, last successful run per country.

## Retention

- Rule pack audit logs: 7 years (regulatory)
- Compliance runs: 3 years
- Findings: until case resolved + 1 year buffer

## Observability

- Metrics: `hr.compliance.run.duration`, `hr.compliance.finding.count`, `hr.audit.snapshot.size`
- Tracing: span attributes include `tenantId`, `countryCode`, `policyPackVersion`, `policyPackLineage`

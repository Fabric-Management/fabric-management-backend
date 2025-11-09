# HR Observability & Performance Budgets

## Metrics
- `hr.localization.policy.resolve.latency` (ms, 95p < 50ms)
- `hr.leave.accrual.time` (ms, 95p < 100ms)
- `hr.payroll.run.duration` (minutes, target < 15)
- `hr.compliance.finding.count` (per run)
- `hr.cache.hit.rate` (target ≥ 95%)
- `hr.policy-pack.publish.duration` (ms, 95p < 500)
- `hr.policy-pack.lineage.request.count` (per minute, watch for spikes after publish)
- `hr.policy-pack.cache.evictions` (count per publish/retire)

## Alerts
- Missing active policy pack for tenant/country (critical)
- Payroll run stuck in `VALIDATING` > 30 minutes
- Leave accrual backlog > 100 pending records
- Cache miss rate > 10% for 2 consecutive intervals
- Policy pack lineage failures > 3 in 5 minutes (signals broken inheritance tree)

## Tracing
- Span attributes: `tenantId`, `countryCode`, `policyPackVersion`, `employeeId` (hashed)
- Propagate trace IDs across orchestrations (hire → compliance → leave)
- Include `operation=policy-pack-admin` for UI-driven lineage/publish flows

## Logging
- Structured JSON with fields: `module`, `action`, `tenantId`, `countryCode`
- PII masked via `PiiMaskingUtil` before logging
- Admin actions log `userId`, `packCode`, `packVersion`, `lineageCodes`


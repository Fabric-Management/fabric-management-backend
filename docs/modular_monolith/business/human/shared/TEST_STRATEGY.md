# HR Testing Strategy

## Layers
- **Unit Tests:** Policy algorithms (`LeavePolicy`, payroll calculations).
- **Contract Tests:** Registry resolution for tenant/country combos.
- **Integration Journeys:** Hire → leave accrual → payroll run.
- **End-to-End:** UI workflows with mock localization packs.

## Fixtures
- Deterministic datasets per country (`TR`, `US`) with fixed timestamps.
- Rule pack JSON stored under `test/resources/hr/policy-packs/`.

## Gating Criteria
- No deployment unless regression suite (unit+contract) green.
- Country enablement requires passing golden dataset comparisons.
- Policy pack publishes require sandbox validation run.


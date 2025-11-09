# Policy Interfaces & Registry Contracts

## Interfaces

- `EmployeeCompliancePolicy`
  - `supports(countryCode)` → boolean
  - `evaluate(employee, context)` → missing-field list
- `LeavePolicy`
  - `supports(countryCode, leaveTypeCode)`
  - `calculateAccrual(request)` → `LeaveAccrualResult`
- `PayrollStrategy` _(placeholder)_
  - `supports(countryCode)`
  - `run(PayrollContext)`
- `ComplianceValidator` _(future)_
  - `supports(countryCode, ruleType)`
  - `validate(payload)` → `ComplianceFinding`
- `HolidayCalendarProvider` _(future)_
  - `supports(countryCode)`
  - `getCalendar(year)` → holiday list

## Registry Behaviour

1. Resolve tenant context via `HrLocalizationService.currentContext()`.
2. Use `HrPolicyPackResolver` to obtain resolved policy pack (hierarchy merged, cached).
3. Normalize country code (uppercase, fallback `GLOBAL-BASE`).
4. Order policies by Spring `@Order` (lower value = higher priority).
5. Return first policy whose `supports(...)` returns `true`.
6. Throw `IllegalStateException` if none match (alerts OnCall).

## Sequence Diagram (simplified)

```
UserService -> EmployeeService : updateProfile()
EmployeeService -> EmployeeCompliancePolicyRegistry : resolve()
EmployeeCompliancePolicyRegistry -> HrPolicyPackResolver : resolve(tenantId,country)
HrPolicyPackResolver -> HrPolicyPackRepository : load pack + parents
HrPolicyPackResolver -> Redis Cache : cache/return merged payload
EmployeeCompliancePolicyRegistry -> PolicyList : supports(country)
Policy -> EmployeeService : evaluate(employee, ctx)
```

## Contract Safeguards

- Registries must be initialized with at least one `GLOBAL` policy.
- Policies must be side-effect free and thread-safe.
- Hierarchical resolution merges parent payloads before invocation; child overrides win.
- Resolved packs cached by `tenantId::countryCode` and `tenantId::PACK::packCode::version`.
- Registries emit structured errors for monitoring (`policy.registry.miss`).

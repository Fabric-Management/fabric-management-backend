# Core Employee Use Cases

## Hire Employee
1. `UserController` receives hire command.
2. `EmployeeService.createOrUpdateEmployee` persists employee record.
3. `EmployeeService.checkAndUpdateCompliance` resolves policy via `EmployeeCompliancePolicyRegistry`.
4. Compliance findings stored on entity (`missingFields`, `hrComplianceStatus`).
5. Domain event `hr.employee.created` published (TODO).

**Acceptance Criteria**
- Employee record persists with tenant context.
- Compliance status computed using policy pack for tenant/country.
- Missing fields logged with WARN severity.

## Update Profile (Admin)
1. Admin submits update.
2. `EmployeeService.createOrUpdateEmployee` applies changes.
3. Compliance re-evaluated; status transitions accordingly.
4. On completion, event `hr.employee.updated` queued.

## Terminate Employee (future)
- Set `terminationDate`, mark active status false, cascade to leave/payroll modules.

## Compliance States
- `COMPLETE` → all recommended fields present.
- `MISSING_RECOMMENDED` → missing from recommended set.
- `MISSING_REQUIRED` (future) → block payroll/leave actions.


# HR RBAC & Endpoint Exposure

| Role | Core Employee | Leave & Attendance | Payroll | Compliance-Localization |
| --- | --- | --- | --- | --- |
| `HR_MANAGER` | Create/Update employee, view compliance alerts | Approve leave, view balances | View payroll summaries | Draft rule packs |
| `HR_ADMIN` | Full access | Publish leave calendars | Publish payroll runs | Publish/retire rule packs |
| `PAYROLL_SPECIALIST` | Read-only employee data | View leave balances impacting payroll | Manage pay runs | Read-only |
| `LINE_MANAGER` | View direct reports | Approve leave requests | No access | No access |
| `EMPLOYEE` | Self-service view | Request leave, view balance | View payslips | No access |

## Endpoint Classification
- `@InternalEndpoint` for admin and inter-service APIs.
- Public HR self-service endpoints exposed through API Gateway with JWT + RBAC.

## Security Schemes
- OAuth2 JWT with scopes:
  - `hr.core.manage`
  - `hr.leave.approve`
  - `hr.payroll.execute`
  - `hr.localization.publish`
- PII masking required for audit logs (`contactValue`, `nationality`, `birthDate`).


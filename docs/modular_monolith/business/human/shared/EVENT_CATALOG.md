# HR Domain Event Catalog

| Event | Publisher | Payload Highlights | Retention | Idempotency Key |
| --- | --- | --- | --- | --- |
| `hr.employee.created` | core-employee | `employeeId`, `tenantId`, `employmentType`, `policyPackVersion` | 90 days | `employeeId` |
| `hr.employee.updated` | core-employee | Delta fields, compliance status | 90 days | `employeeId:updatedAt` |
| `hr.employee.compliance.flagged` | core-employee | Missing fields list, severity | 90 days | `employeeId:lastCheckAt` |
| `hr.leave.requested` | leave-attendance | `leaveRequestId`, `employeeId`, `leaveType`, `dates` | 60 days | `leaveRequestId` |
| `hr.leave.approved` | leave-attendance | `leaveRequestId`, `approverId`, `balanceImpact` | 60 days | `leaveRequestId:approvedAt` |
| `hr.leave.balance.updated` | leave-attendance | `employeeId`, `leaveType`, `newBalance` | 60 days | `employeeId:leaveType:timestamp` |
| `hr.payroll.run.started` | payroll | `payRunId`, `period`, `tenantId` | 120 days | `payRunId` |
| `hr.payroll.run.completed` | payroll | `payRunId`, `status`, `totalNetPay` | 120 days | `payRunId:status` |
| `hr.policy-pack.published` | compliance-localization | `tenantId`, `countryCode`, `packVersion` | 365 days | `tenantId:countryCode:packVersion` |

## Retention Policy
- Kafka topics retained per durations above; archived snapshots stored in S3 (future).

## Idempotency
- Consumers must de-duplicate using provided keys.
- Events include `occurredAt` timestamp (ISO-8601 UTC).


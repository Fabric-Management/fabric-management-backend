# Core Employee Subdomain

**Responsibilities**
- Maintain canonical employee records (one-to-one with platform users)
- Manage employment lifecycle data (hire, update, terminate)
- Track HR compliance state and raise domain events for downstream modules

**Inbound Inputs**
- User onboarding commands (`UserService`, onboarding orchestrations)
- Compliance policy evaluations (`EmployeeCompliancePolicy`)

**Outbound Outputs**
- Employee aggregates and DTOs for consumer services
- Domain events: `hr.employee.created`, `hr.employee.updated`, `hr.employee.compliance.flagged`

**Key Integrations**
- `compliance-localization`: resolves applicable compliance packs
- `leave-attendance`: uses employee context for accrual decisions
- `payroll`: consumes hiring/termination events

**Module Structure**
- `api`: REST controllers (to be introduced)
- `application`: orchestration services (`EmployeeService`)
- `domain`: entities and value objects (`Employee`, `EmergencyContact`)
- `infra`: JPA repositories and adapters


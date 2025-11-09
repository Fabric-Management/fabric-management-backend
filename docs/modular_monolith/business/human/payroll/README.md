# Payroll Subdomain

**Responsibilities**
- Manage pay calendars, pay runs, and gross-to-net computation pipelines
- Apply localized tax, contribution, and benefit policies
- Coordinate payouts with finance integrations while ensuring audit trails

**Inbound Inputs**
- Employment and compensation data from `core-employee` and `compensation-benefits`
- Policy packs from `compliance-localization`
- Accrual data from `leave-attendance`

**Outbound Outputs**
- Events: `hr.payroll.run.started`, `hr.payroll.run.completed`, `hr.payroll.payout.dispatched`
- Net pay summaries and payslip data

**Module Structure**
- `api`: payroll run management endpoints (planned)
- `application`: `PayPeriodService`, `PayRunService`, `PayrollService`, `PayrollStrategyRegistry`, `PayRunAuditService`, `PayrollComplianceService`
- `domain`: `PayPeriod`, `PayRun`, `PayRunEntry`, `PayRunPayout`, `PayRunAuditLog`, enums (`PayPeriodStatus`, `PayRunStatus`, `PayRunEntryType`)
- `strategy`: `PayrollStrategy`, `PayrollContext`, `PayrollResult`, default `GlobalPayrollStrategy`, regional/country strategies (`EuPayrollStrategy`, `UkPayrollStrategy`, future TR/US)
- `infra`: JPA repositories for periods, runs, entries, payouts, audit logs

**Operational Flow**
1. Create pay period (draft → open → lock) per tenant/country.
2. Initialize pay run referencing active HR policy pack (code/version stored on run).
3. Execute payroll via strategy registry (country-aware). Strategy returns net amounts/metadata.
4. Persist payouts and audit log; mark run `VALIDATED` → `COMPLETED`.
5. Publish events (planned) to finance/payout systems.

**Data Sources & Localization**
- Policy packs resolve through `compliance-localization` with Redis caching and hierarchical composition (Global → Region → Country).
- Country code stored on pay periods to select localized strategies.
- `PayrollContext` carries pack metadata, date range, employee list, and merged policy parameters (pack payload + runtime overrides).
- Audit trail captures lifecycle actions in `human_pay_run_audit_log`.


# HR Shared Vocabulary Glossary (v1.0)

| Term                | Definition                                                                          | Source                                                    |
| ------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------- |
| `EmploymentType`    | Canonical employment classification (permanent, contract, intern, etc.).            | `com.fabricmanagement.human.shared.domain.EmploymentType` |
| `LeaveTypeCode`     | Uppercase identifier for leave categories (e.g., `ANNUAL`, `SICK_STATUTORY`).       | `com.fabricmanagement.human.shared.domain.LeaveTypeCode`  |
| `CurrencyCode`      | ISO-4217 currency code used for compensation and payroll.                           | `com.fabricmanagement.human.shared.domain.CurrencyCode`   |
| `employeeNumber`    | Business identifier for employees, generated per tenant (`{TENANT_UID}-EMP-{SEQ}`). | `core-employee`                                           |
| `HrPolicyPack`      | Effective-dated configuration bundle describing localized HR rules.                 | `compliance-localization`                                 |
| `HrPolicyBinding`   | Mapping between policy interface and strategy bean defined within a pack.           | `compliance-localization`                                 |
| `HrRuleVersion`     | Serialized rule payload attached to a policy pack version.                          | `compliance-localization`                                 |
| `LeavePolicy`       | Strategy interface for computing accruals and balances.                             | `leave-attendance`                                        |
| `LeaveBalance`      | Persisted state of available/pending/carry-over days per employee.                  | `human_leave_balance`                                     |
| `HolidayCalendar`   | JSON-encoded list of statutory holidays per tenant/country/year.                    | `human_holiday_calendar`                                  |
| `EU-BASELINE`       | Regional HR policy pack inheriting from `GLOBAL-BASE`, shared by EU countries.      | `compliance-localization`                                 |
| `EuLeavePolicy`     | Baseline EU leave accrual strategy (20 days min, carry-over rules).                 | `leave-attendance`                                        |
| `UkLeavePolicy`     | UK-specific leave policy extending EU rules (statutory 28 days + bank holidays).    | `leave-attendance`                                        |
| `TrLeavePolicy`     | Turkey leave strategy with seniority tiers and probation handling.                  | `leave-attendance`                                        |
| `EuPayrollStrategy` | Shared EU payroll computation (progressive tax, social contributions).              | `human.payroll.strategy`                                  |
| `UkPayrollStrategy` | UK payroll strategy layering PAYE/NI logic atop EU baseline.                        | `human.payroll.strategy`                                  |
| `Fr/De/Es/It PayrollStrategy` | Country-specific overrides extending EU payroll baseline.                 | `human.payroll.strategy`                                  |
| `TrPayrollStrategy` | Turkey payroll strategy with SGK + income tax logic.                                | `human.payroll.strategy`                                  |
| `UsPayrollStrategy` | US payroll strategy applying federal tax brackets and FICA contributions.           | `human.payroll.strategy`                                  |

## Versioning

- **Current version:** 1.0
- **Change process:** updates require ADR plus version bump (semantic: MAJOR.MINOR)
- **Storage:** glossary maintained alongside schema references in `docs/modular_monolith/business/human/shared/`

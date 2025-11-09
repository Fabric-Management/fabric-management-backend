# Leave & Attendance Subdomain

**Responsibilities**
- Maintain leave policies, accrual engines, balances, and calendars
- Orchestrate leave requests, approvals, and compliance validations
- Surface localized holiday data and statutory leave entitlements

**Inbound Inputs**
- Commands from employee self-service and manager portals
- Policy packs from `compliance-localization`

**Outbound Outputs**
- Events: `hr.leave.requested`, `hr.leave.approved`, `hr.leave.balance.updated`
- Accrual results for payroll earnings/deductions

**Module Structure**
- `api`: leave endpoints and DTO mapping (pending)
- `application`: `LeaveService`, `LeaveTypeService`, `LeaveBalanceService`, `LeaveAccrualLogService`, `LeaveOnboardingService`, `HolidayCalendarService`
- `domain`: policy interfaces, accrual value objects, persistence entities (`LeaveType`, `LeaveBalance`, `LeaveAccrualLog`, `HolidayCalendar`)
- `infra`: repositories for leave types, balances, accrual logs, holiday calendars

**Data & Localization**
- Leave definitions stored in `human_leave_type` table with strategy bindings and JSON attributes.
- Balances persisted in `human_leave_balance` with policy pack references.
- Accrual events journaled in `human_leave_accrual_log`.
- Holiday calendars cached via `human_holiday_calendar` and attached to policy requests.
- Active policy packs resolved through `compliance-localization` with Redis caching; hierarchical fallback (country → region → global).
- Strategies:
  - `EuLeavePolicy` handles EU baseline (20-day minimum, carry-over rules).
  - `UkLeavePolicy` extends EU policy with statutory 28-day allowance + bank holidays.
  - `TrLeavePolicy`, `GlobalLeavePolicy` cover non-EU scenarios (probation, seniority tiers).

**Orchestration Highlights**
- Accrual flow fetches leave type → resolves policy pack → runs strategy via `LeavePolicyRegistry`.
- Compliance guard validates leave type visibility against policy pack country.
- Balances updated transactionally and accrual logs persisted for audit.
- Onboarding service seeds zero balances for newly hired employees per country.


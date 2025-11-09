# Leave & Attendance Domain Flow (MVP)

## Flow: Request → Approve → Deduct
1. Employee submits leave request (API pending).
2. `LeaveService` resolves policy pack and strategy via `LeavePolicyRegistry`.
3. Policy calculates accrual/deduction using `LeavePolicyRequest` enriched with holiday calendar + pack metadata.
4. Approval workflow (manager/HR) updates status.
5. Balance updates persisted to `human_leave_balance`; accrual entry saved in `human_leave_accrual_log`; event `hr.leave.balance.updated` emitted.

## Balances & Buckets
- Track balances per `(employeeId, leaveTypeCode, policyVersion)`.
- Buckets: `ACCRUED`, `CARRIED_OVER`, `AWARDED`.
- Carry-over caps driven by policy pack attributes and persisted thresholds (`max_carry_over`).

## Approval Rules Checklist
- Validate minimum notice period.
- Enforce blackout dates (from holiday calendar).
- Check sufficient balance (including future accruals if allowed).
- Determine approval authority (manager vs HR).

## Integrations
- Pull employment type/FTE from `core-employee`.
- Push approved leave to payroll for unpaid days/deductions.
- Reference `human_holiday_calendar` for localized public holidays; results cached per tenant/country.
- Persist policy pack references (code/version) alongside balances for audit trail.


# Payroll Lifecycle (Baseline)

## Stages

1. **Open**: Create pay period (`human_pay_period`) and set status `OPEN`.
2. **Run**: Generate pay run (`human_pay_run`) with associated policy pack metadata.
3. **Execute**: `PayrollService` invokes strategy → populates `human_pay_run_payout` (net amounts) and audit log.
4. **Lock**: Mark pay run `LOCKED`/`COMPLETED`, freeze period if needed.
5. **Payout**: Integrate with payment rails (future) using payout records.
6. **Reconcile**: Compare expected vs actual, emit reports & audit entries.

## Data Inputs

- Compensation data (base salary, allowances) from `compensation-benefits`.
- Leave deductions from `leave-attendance`.
- Tax/benefit policies via HR policy pack payload (`policy_pack_code`, `policy_pack_version`).
- Policy parameters passed to strategies (e.g., currency, thresholds).

## Outputs

- Payslip JSON for UI generation (pending).
- Ledger entries for finance integration (pending).
- Audit log entries (`human_pay_run_audit_log`) with action metadata.
- Net payout records per employee (`human_pay_run_payout`).

## Reconciliation Report Spec

- Employee-level variance table (expected vs. paid).
- Aggregated totals per cost center.
- Exception list (missing bank info, negative net pay).

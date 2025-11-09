# ADR: Leave Domain Persistence & Policy Integration

**Status:** Accepted  
**Date:** 2025-11-09 00:00 UTC+3

## Context

The leave subdomain required durable storage for leave definitions/balances,
policy-driven accrual logic, and hooks into localization/compliance. Previous
implementation only delegated to strategy interfaces without persistence.

## Decision

- Introduced dedicated tables (`human_leave_type`, `human_leave_balance`,
  `human_leave_accrual_log`, `human_holiday_calendar`) for core leave data.
- `LeaveService` orchestrates accrual using `HrPolicyPack` resolution, passes
  policy metadata/holiday calendars into `LeavePolicyRequest`, and persists
  balances/logs atomically.
- Added onboarding/compliance helpers (`LeaveOnboardingService`,
  `LeaveComplianceService`) to align with HR orchestration and auditing.
- Redis-backed policy pack caching reused through localization module.

## Consequences

- Leave accruals are auditable and versioned per policy pack.
- Rule packs remain the single source of strategy selection; carry-over limits
  and attributes stay data-driven.
- Tiered strategies introduced: `EuLeavePolicy` for EU baseline, `UkLeavePolicy`
  for UK-specific statutory rules, `TrLeavePolicy` for seniority-based accruals,
  while `GlobalLeavePolicy` remains the final fallback.
- Additional migrations (V031) required for new tables; rollouts must run DB
  migrations before enabling features.

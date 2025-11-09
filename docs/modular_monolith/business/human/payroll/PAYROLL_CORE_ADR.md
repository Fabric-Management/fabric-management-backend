# ADR: Payroll Core Entities & Strategy Hooks

**Status:** Accepted  
**Date:** 2025-11-09 00:00 UTC+3  

## Context
Payroll needed persisted state for pay periods/runs, net payout tracking, and a
strategy mechanism to support country-specific calculations driven by HR policy
packs. Previous implementation lacked domain entities and orchestration hooks.

## Decision
- Added normalized tables for pay periods, pay runs, run entries, payouts, and audit logs (`V032__payroll_domain_core`).
- Implemented domain models (`PayPeriod`, `PayRun`, `PayRunEntry`, `PayRunPayout`, `PayRunAuditLog`) with status enums.
- Introduced application services: `PayPeriodService`, `PayRunService`, `PayrollService`, `PayrollStrategyRegistry`, `PayRunAuditService`, `PayrollComplianceService`.
- Defined `PayrollStrategy` interface plus default `GlobalPayrollStrategy`; strategies resolve via localization registry using HR policy packs.
- Persist HR policy pack metadata (code/version) on pay runs for audit, caching reused from localization module.

## Consequences
- Payroll runs are now auditable, versioned, and policy-aware.
- Regional hierarchy supported: `EuPayrollStrategy` + country extensions (UK/FR/DE/ES/IT) reuse shared logic; standalone TR/US strategies operate independently.
- Data migrations are required prior to deployment; pay run execution currently stores net payouts only (earnings/deductions extension pending).

## Follow-up
- Emit domain events and integrate payouts with finance adapters.
- Add REST endpoints/UI wiring for new services.


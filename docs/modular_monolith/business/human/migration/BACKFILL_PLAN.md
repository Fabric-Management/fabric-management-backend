# HR Backfill & Migration Plan

## Objectives
- Migrate existing employee records into new core-employee module.
- Initialize global policy pack for tenants lacking localized data.
- Seed opening leave balances from historical data.

## Steps
1. Snapshot current employee/user tables.
2. Backfill `human_employee` ensuring tenant IDs populated.
3. Generate default policy pack (`GLOBAL`, version 1) with baseline rules.
4. Recompute compliance status for all employees (batch job).
5. Import leave balances → `leave_balance` table (planned).

## Validation
- Reconcile employee counts per tenant pre/post migration.
- Ensure employee numbers match previous format.
- Compare leave totals with legacy system (variance < 0.5%).

## Rollback
- Maintain backup of affected tables (pg_dump).
- Provide script to revert to legacy schema if needed.


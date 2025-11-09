# HR Global & Localizable Module ADR

**Status:** Proposed  
**Date:** 2025-11-09 00:00 UTC+3  
**Context Owner:** Human Domain Team

## Decision
Adopt a modular, policy-driven HR architecture inside the `human` bounded context with data-driven rule packs and tenant-aware localization.

## Principles
- Core scope for first release: core-employee, leave-attendance, payroll baseline, compliance-localization.
- Strategy/policy interfaces mediate country-specific behaviour; no hardcoded rules.
- Rule packs and reference data live in PostgreSQL with effective dating and versioning.
- Tenant- and employee-level localization resolved through `TenantContext`.
- Observability, RBAC, and auditability wired from the start.

## Consequences
- Requires dedicated rule-pack lifecycle (draft → publish → retire) and caching policy.
- Increased upfront documentation: per-module READMEs, rule-pack specs, resolution contracts.
- Enforces strict module boundaries and event-driven orchestration across HR flows.

## Non-Goals
- No custom workflow engine (use existing orchestration patterns).
- No embedded scripting/DSL for rules in initial release.
- No vendor-specific payroll integrations in baseline (provide extension points only).

## Next Actions
1. Scaffold subdomains under `human/<module>` with READMEs.
2. Finalize shared HR vocabulary and publish schema doc.
3. Implement tenant-country resolution contract with acceptance tests.
4. Deliver policy registry contracts and rule-pack data model.


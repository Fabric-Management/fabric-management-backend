# Tenant & Country Resolution Contract

**Last updated:** 2025-11-09 00:00 UTC+3  
**Scope:** Determine applicable localization context for HR policies.

## Resolution Order

1. Employee-level override (future state)
2. Legal entity (company site) country
3. Tenant default country (`TenantContext`)
4. Global fallback (`GLOBAL`)

## Runtime Contract

- `TenantContext` persists `tenantId`, `tenantUid`, `tenantCountry`.
- `HrLocalizationService.currentContext()` returns `(tenantId, countryCode)` applying fallback to `GLOBAL`.
- Country codes must be ISO 3166-1 alpha-2 uppercase.

## Acceptance Matrix

| Scenario                           | Employee override | Legal entity | Tenant country | Expected result                   |
| ---------------------------------- | ----------------- | ------------ | -------------- | --------------------------------- |
| Employee assigned to DE, tenant GB | `DE`              | `null`       | `GB`           | `DE` (future implementation hook) |
| Legal entity TR, tenant GB         | `null`            | `TR`         | `GB`           | `TR`                              |
| Tenant default US                  | `null`            | `null`       | `US`           | `US`                              |
| Tenant country unset               | `null`            | `null`       | `null`         | `GLOBAL`                          |

## Non-Functional Requirements

- Updates to tenant country must be idempotent; clearing uses `TenantContext.setCurrentTenantCountry(null)`.
- All reads performed within request scope; context cleared on completion.
- Future extension: Evaluate employee override via `EmployeeLocalizationResolver`.

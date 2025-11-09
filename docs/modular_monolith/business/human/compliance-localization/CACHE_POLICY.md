# HR Localization Cache Policy

## Scope

- Active `HrPolicyPack` per `(tenantId, countryCode)`
- Resolved hierarchical packs per `(tenantId, countryCode)` and `(tenantId, pack_code, pack_version)`
- Country-to-pack mappings
- Holiday calendars per `(countryCode, year)`
- Leave type definitions per `(tenantId, countryCode)`

## Cache Layers

1. **In-memory** (Caffeine) for single-node latency
2. **Distributed** (Redis)
   - Active pack cache key: `tenant:{tenantId}:country:{countryCode}`
   - Resolved pack cache key: `tenant:{tenantId}:country:{countryCode}:resolved` and `tenant:{tenantId}:pack:{packCode}:{packVersion}`

## Invalidation Rules

- Publish new pack → evict active/resolved caches for pack country and all mapped countries.
- Retire pack → evict active/resolved caches referencing pack code.
- Update country mapping → evict resolved cache for affected country.
- Manual invalidation endpoint for emergency rollback.
- TTL safety: 15 minutes with refresh-on-write; stale reads trigger warning metric.

## Failure Modes & Mitigation

| Scenario                  | Effect                                      | Mitigation                                                          |
| ------------------------- | ------------------------------------------- | ------------------------------------------------------------------- |
| Redis unavailable         | Fallback to DB lookups, raise `WARN` metric | Circuit breaker + exponential backoff                               |
| Stale cache after publish | Wrong policies applied                      | Publish flow evicts keys after DB commit; integration test verifies |
| Missing cache entry       | Cold start latency                          | Preload during publish & on service startup                         |

## Observability

- Metrics: `hr.localization.cache.hit`, `hr.localization.cache.miss`, `hr.localization.cache.evictions`
- Logs include tenant + country context
- Alerts when miss rate > 5% over 15 minutes

# 🧾 Fabric Management – Microservice Performance Readiness Checklist

## Overview

Bu checklist, her microservice'in production-ready olduğunu doğrulamak için kullanılır.

**Hedef**: Tüm satırlar ✅ olmalı (minimum %90 compliance)

**Son Güncelleme**: 2025-10-20

---

## Legend

| Symbol | Meaning                |
| ------ | ---------------------- |
| ✅     | Implemented & Verified |
| ⚠️     | Partially Implemented  |
| ❌     | Not Implemented        |
| N/A    | Not Applicable         |
| 🔄     | In Progress            |

---

## Checklist Matrix

| #                                  | Category     | Check Item                                                 | Verification Method                                | API Gateway | User Service | Company Service | Contact Service | Notification Service | Fiber Service | Shared Modules |
| ---------------------------------- | ------------ | ---------------------------------------------------------- | -------------------------------------------------- | ----------- | ------------ | --------------- | --------------- | -------------------- | ------------- | -------------- |
| **1. ENVIRONMENT CONFIG**          |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 1.1                                | Environment  | All config externalized (`${VAR:default}`)                 | Grep for hardcoded values                          | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 1.2                                | Environment  | Profile-based config (local, docker, prod)                 | application-{profile}.yml exists                   | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 1.3                                | Environment  | No hardcoded secrets/URLs/ports                            | Code review + grep                                 | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | ☐              |
| 1.4                                | Environment  | Constants in shared-infrastructure                         | Constants.java usage verified                      | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | ☐              |
| **2. DATABASE (PostgreSQL)**       |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 2.1                                | Database     | HikariCP pool configured                                   | `spring.datasource.hikari.*` in YAML               | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 2.2                                | Database     | Pool size matches load (max 10-20)                         | Verified via metrics                               | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 2.3                                | Database     | `open-in-view: false` set                                  | application.yml verified                           | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 2.4                                | Database     | Hibernate optimized (`show_sql=false`, `ddl-auto=none`)    | application.yml verified                           | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 2.5                                | Database     | Proper indexes (B-Tree, GIN, partial)                      | schema.sql or `\d+ table`                          | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 2.6                                | Database     | PostgreSQL extensions (`pg_trgm`, `pg_stat_statements`)    | `\dx` in psql                                      | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 2.7                                | Database     | Query performance validated (<100ms p95)                   | `EXPLAIN ANALYZE` on slow queries                  | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 2.8                                | Database     | No N+1 queries (`JOIN FETCH` or pagination)                | Hibernate logs checked                             | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 2.9                                | Database     | DTO projections used (MapStruct)                           | Mapper classes present                             | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | ☐              |
| 2.10                               | Database     | Optimistic locking (`@Version`) on entities                | Entity check                                       | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| **3. CACHING (Redis)**             |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 3.1                                | Cache        | Redis connection pool (Lettuce) configured                 | `spring.data.redis.*` in YAML                      | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 3.2                                | Cache        | Cache TTL strategy defined                                 | TTL per cache name                                 | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 3.3                                | Cache        | `@Cacheable`, `@CacheEvict` used correctly                 | Service layer review                               | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 3.4                                | Cache        | Policy cache (ConcurrentHashMap) implemented               | PolicyCache.java present                           | N/A         | ☐            | N/A             | N/A             | N/A                  | N/A           | N/A            |
| 3.5                                | Cache        | Cache hit rate >80%                                        | Prometheus metric verified                         | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 3.6                                | Cache        | Cache invalidation strategy documented                     | Cache eviction on write operations                 | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| **4. MESSAGING (Kafka)**           |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 4.1                                | Kafka        | Producer config optimized (`acks=all`, `idempotence=true`) | application.yml verified                           | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 4.2                                | Kafka        | Consumer config (`auto-commit=false`, manual commit)       | Consumer code review                               | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 4.3                                | Kafka        | Transactional Outbox pattern implemented                   | Outbox entity + scheduler present                  | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 4.4                                | Kafka        | Error handling (`ErrorHandlingDeserializer`)               | Deserializer config checked                        | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 4.5                                | Kafka        | Event types documented & consistent                        | shared-domain event classes                        | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | ☐              |
| 4.6                                | Kafka        | Dead Letter Queue (DLQ) configured                         | DLT topic + handler present                        | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 4.7                                | Kafka        | Async publishing (`@Async` + CompletableFuture)            | Publisher code review                              | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| **5. RESILIENCE (Resilience4j)**   |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 5.1                                | Resilience   | Circuit breaker config per external call                   | `resilience4j.circuitbreaker.*` in YAML            | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 5.2                                | Resilience   | Retry policy (exponential backoff)                         | `resilience4j.retry.*` in YAML                     | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 5.3                                | Resilience   | Timeout (`TimeLimiter`) configured                         | `resilience4j.timelimiter.*` in YAML               | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 5.4                                | Resilience   | Circuit breaker state in Actuator                          | `/actuator/circuitbreakers` accessible             | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 5.5                                | Resilience   | Fallback methods implemented                               | Graceful degradation verified                      | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| **6. INTER-SERVICE COMMUNICATION** |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 6.1                                | Feign        | Feign client interfaces defined                            | `@FeignClient` annotations present                 | N/A         | N/A          | ☐               | N/A             | N/A                  | N/A           | N/A            |
| 6.2                                | Feign        | `@InternalEndpoint` on internal APIs                       | Controller annotations checked                     | N/A         | ☐            | ☐               | ☐               | N/A                  | N/A           | N/A            |
| 6.3                                | Feign        | Internal API key validation (`X-Internal-API-Key`)         | Security filter present                            | N/A         | ☐            | ☐               | ☐               | N/A                  | N/A           | ☐              |
| 6.4                                | Feign        | Correlation ID propagation (`X-Correlation-ID`)            | Feign interceptor configured                       | N/A         | N/A          | ☐               | N/A             | N/A                  | N/A           | ☐              |
| 6.5                                | Feign        | Retry + circuit breaker on Feign clients                   | `@CircuitBreaker` + `@Retry` annotations           | N/A         | N/A          | ☐               | N/A             | N/A                  | N/A           | N/A            |
| 6.6                                | Feign        | BaseFeignClientConfig extended                             | Inheritance verified                               | N/A         | N/A          | ☐               | N/A             | N/A                  | N/A           | ☐              |
| **7. API GATEWAY**                 |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 7.1                                | Gateway      | Redis rate limiter configured                              | Gateway YAML verified                              | ☐           | N/A          | N/A             | N/A             | N/A                  | N/A           | N/A            |
| 7.2                                | Gateway      | `SmartKeyResolver` implemented                             | IP/user/tenant key resolution                      | ☐           | N/A          | N/A             | N/A             | N/A                  | N/A           | N/A            |
| 7.3                                | Gateway      | Circuit breaker fallback configured                        | `fallbackUri` present in routes                    | ☐           | N/A          | N/A             | N/A             | N/A                  | N/A           | N/A            |
| 7.4                                | Gateway      | Request/response filters enabled                           | `AddRequestHeader`, `AddResponseHeader`            | ☐           | N/A          | N/A             | N/A             | N/A                  | N/A           | N/A            |
| 7.5                                | Gateway      | JWT validation filter active                               | Security config verified                           | ☐           | N/A          | N/A             | N/A             | N/A                  | N/A           | N/A            |
| 7.6                                | Gateway      | CORS policy configured                                     | Allowed origins/methods set                        | ☐           | N/A          | N/A             | N/A             | N/A                  | N/A           | N/A            |
| **8. MONITORING & OBSERVABILITY**  |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 8.1                                | Actuator     | Spring Actuator enabled                                    | `/actuator/health` accessible                      | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 8.2                                | Actuator     | Prometheus metrics exposed                                 | `/actuator/prometheus` accessible                  | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 8.3                                | Actuator     | Critical endpoints exposed (health, metrics, prometheus)   | Verified in YAML                                   | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 8.4                                | Prometheus   | Prometheus scraping configured                             | prometheus.yml job present                         | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 8.5                                | Grafana      | Service panel in Grafana dashboard                         | Dashboard verified                                 | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 8.6                                | Grafana      | Key metrics visible (p95 latency, RPS, errors)             | Dashboard panels checked                           | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 8.7                                | Alertmanager | Alert rules defined for service                            | alerts.yml contains service alerts                 | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 8.8                                | Logging      | Correlation ID in logs (`%X{X-Correlation-ID}`)            | Log pattern verified                               | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | ☐              |
| 8.9                                | Logging      | Structured logging (JSON or pattern)                       | Logback config checked                             | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 8.10                               | Docker       | Healthcheck defined in docker-compose                      | Healthcheck block present                          | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| **9. RESOURCE MANAGEMENT**         |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 9.1                                | Docker       | CPU limits set                                             | `deploy.resources.limits.cpus` in docker-compose   | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 9.2                                | Docker       | Memory limits set                                          | `deploy.resources.limits.memory` in docker-compose | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 9.3                                | JVM          | JVM tuning flags applied (`UseG1GC`, heap size)            | `JAVA_OPTS` in docker-compose                      | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 9.4                                | JVM          | Heap size appropriate (Xms=Xmx, <80% container memory)     | Verified                                           | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 9.5                                | Scaling      | Stateless design (no local state)                          | Code review                                        | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 9.6                                | Scaling      | Session in Redis (not in-memory)                           | Spring Session Redis configured                    | ☐           | ☐            | N/A             | N/A             | N/A                  | N/A           | N/A            |
| 9.7                                | Scaling      | Horizontal scaling tested (2+ replicas)                    | Docker scale test performed                        | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| **10. PERFORMANCE TESTING**        |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 10.1                               | Testing      | Integration tests exist                                    | Test classes present                               | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | ☐              |
| 10.2                               | Testing      | Performance test exists (e.g., `*PerfIT.java`)             | Perf test class present                            | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 10.3                               | Testing      | Baseline latency thresholds defined (p95 targets)          | Assertions in perf tests                           | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 10.4                               | Testing      | Warm-up phase in perf tests                                | Test code verified                                 | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 10.5                               | Testing      | Parallel execution tested                                  | Concurrent requests verified                       | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 10.6                               | Testing      | k6 load test scenario (planned or implemented)             | k6 script exists or planned                        | ☐           | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 10.7                               | Testing      | Code coverage >80% (main logic)                            | JaCoCo report verified                             | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | ☐              |
| **11. SECURITY + PERFORMANCE**     |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 11.1                               | Security     | JWT stateless authentication                               | Token parsing verified                             | ☐           | ☐            | ☐               | ☐               | N/A                  | N/A           | ☐              |
| 11.2                               | Security     | Policy decision caching active                             | Cache hit rate >80%                                | N/A         | ☐            | N/A             | N/A             | N/A                  | N/A           | N/A            |
| 11.3                               | Security     | API key validation on internal endpoints                   | `@InternalEndpoint` enforced                       | N/A         | ☐            | ☐               | ☐               | N/A                  | N/A           | ☐              |
| 11.4                               | Security     | Password hashing (BCrypt)                                  | UserService verified                               | N/A         | ☐            | N/A             | N/A             | N/A                  | N/A           | N/A            |
| 11.5                               | Security     | Rate limiting on public endpoints                          | Gateway rate limiter active                        | ☐           | N/A          | N/A             | N/A             | N/A                  | N/A           | N/A            |
| 11.6                               | Security     | HTTPS ready (TLS termination at gateway/LB)                | Production config documented                       | ☐           | N/A          | N/A             | N/A             | N/A                  | N/A           | N/A            |
| **12. ANTI-PATTERNS CHECK**        |              |                                                            |                                                    |             |              |                 |                 |                      |               |                |
| 12.1                               | Anti-Pattern | `open-in-view: false` confirmed                            | YAML verified                                      | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 12.2                               | Anti-Pattern | No N+1 queries (JOIN FETCH or pagination)                  | Hibernate logs clean                               | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 12.3                               | Anti-Pattern | DTO projections used (no entity exposure)                  | Controller returns DTOs only                       | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 12.4                               | Anti-Pattern | Circuit breaker prevents cascade failures                  | Metrics show CLOSED state                          | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 12.5                               | Anti-Pattern | Caching prevents repetitive DB hits                        | Cache hit rate >80%                                | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 12.6                               | Anti-Pattern | No blocking calls in async methods                         | Code review                                        | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |
| 12.7                               | Anti-Pattern | No unbounded thread pools                                  | Executor configs reviewed                          | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | ☐              |
| 12.8                               | Anti-Pattern | No synchronous Kafka publishing in transactions            | @Async on publishers                               | N/A         | ☐            | ☐               | ☐               | ☐                    | ☐             | N/A            |

---

## Summary Statistics

| Service              | Total Checks | ✅ Count | ⚠️ Count | ❌ Count | N/A Count | Compliance % |
| -------------------- | ------------ | -------- | -------- | -------- | --------- | ------------ |
| API Gateway          | -            | -        | -        | -        | -         | -            |
| User Service         | -            | -        | -        | -        | -         | -            |
| Company Service      | -            | -        | -        | -        | -         | -            |
| Contact Service      | -            | -        | -        | -        | -         | -            |
| Notification Service | -            | -        | -        | -        | -         | -            |
| Fiber Service        | -            | -        | -        | -        | -         | -            |
| Shared Modules       | -            | -        | -        | -        | -         | -            |

**Target Compliance**: >90% (excluding N/A)

---

## How to Use This Checklist

### 1. Initial Assessment

```bash
# For each service, go through each check item row by row
# Replace ☐ with ✅, ⚠️, ❌, or N/A based on verification
```

### 2. Verification Commands

**Database Checks**:

```sql
-- Check indexes
\d+ users;
\d+ companies;

-- Check extensions
\dx;

-- Query performance
EXPLAIN ANALYZE SELECT * FROM fibers WHERE code = 'FBR-001';
```

**Config Checks**:

```bash
# Check for hardcoded values
grep -r "localhost" services/*/src/main/resources/
grep -r "5432" services/*/src/main/resources/

# Check environment variables
grep -r "\${" services/*/src/main/resources/application*.yml
```

**Metrics Checks**:

```bash
# Actuator health
curl http://localhost:8081/actuator/health

# Prometheus metrics
curl http://localhost:8081/actuator/prometheus | grep cache_hit

# Circuit breaker state
curl http://localhost:8081/actuator/circuitbreakers
```

**Performance Checks**:

```bash
# Run performance tests
mvn -pl services/fiber-service -Dtest=FiberApiPerfIT test

# Check JaCoCo coverage
mvn clean test jacoco:report
open services/fiber-service/target/site/jacoco/index.html
```

### 3. Update Process

1. **Weekly Review**: Update checklist every Friday
2. **Pre-Release**: 100% checklist before production deploy
3. **New Service**: Run full checklist on new microservices
4. **Post-Incident**: Re-verify failed component's checklist

### 4. Priority Levels

| Priority    | Compliance % | Action                          |
| ----------- | ------------ | ------------------------------- |
| 🔴 Critical | <70%         | BLOCK deployment                |
| 🟡 Warning  | 70-89%       | Document exceptions, plan fixes |
| 🟢 Good     | 90-100%      | Deploy ready                    |

---

## Compliance Exceptions

If a check is intentionally skipped, document here:

| Service | Check # | Reason                            | Approved By | Date       |
| ------- | ------- | --------------------------------- | ----------- | ---------- |
| Example | 3.4     | Policy cache not needed (no RBAC) | Tech Lead   | 2025-10-20 |

---

## Action Items (Auto-Generated from ❌)

After filling out the checklist, list all ❌ items here with owners and deadlines:

| Service | Check # | Item | Owner | Deadline | Status |
| ------- | ------- | ---- | ----- | -------- | ------ |
| -       | -       | -    | -     | -        | -      |

---

## Validation Script

```bash
#!/bin/bash
# Auto-check some items programmatically

echo "🔍 Running automated performance readiness checks..."

# Check 1.1: Hardcoded values
echo "1.1 - Checking for hardcoded localhost..."
grep -r "localhost" services/*/src/main/resources/ && echo "❌ Found hardcoded localhost" || echo "✅ No hardcoded localhost"

# Check 2.3: open-in-view
echo "2.3 - Checking open-in-view setting..."
grep -r "open-in-view: false" services/*/src/main/resources/ && echo "✅ open-in-view disabled" || echo "❌ open-in-view not disabled"

# Check 8.1: Actuator health endpoints
echo "8.1 - Checking Actuator health..."
for port in 8081 8082 8083 8084 8085 8086; do
  curl -s http://localhost:$port/actuator/health > /dev/null && echo "✅ Port $port healthy" || echo "❌ Port $port unreachable"
done

# Check 8.2: Prometheus metrics
echo "8.2 - Checking Prometheus endpoints..."
for port in 8081 8082 8083 8084 8085 8086; do
  curl -s http://localhost:$port/actuator/prometheus | grep -q "jvm_memory" && echo "✅ Port $port metrics exposed" || echo "❌ Port $port metrics missing"
done

echo "✅ Automated checks complete!"
```

**Usage**:

```bash
chmod +x scripts/performance-readiness-check.sh
./scripts/performance-readiness-check.sh
```

---

## Next Steps After Checklist Completion

1. **Generate Compliance Report**:

   ```bash
   # Count symbols per service
   grep "API Gateway" MICROSERVICE_PERFORMANCE_READINESS_CHECKLIST.md | grep -o "✅" | wc -l
   ```

2. **Fix Critical Gaps** (❌ items)

3. **Baseline Performance Test**:

   ```bash
   # Run all perf tests
   mvn test -Dtest="*PerfIT"
   ```

4. **Document Baseline Metrics** → `docs/performance/BASELINE_METRICS.md`

5. **Monitor for 1 Week** → Grafana dashboard review

---

## Related Documentation

- [PERFORMANCE_ARCHITECTURE.md](./PERFORMANCE_ARCHITECTURE.md) - Current performance stack
- [PERFORMANCE_IMPROVEMENT_ANALYSIS.md](./PERFORMANCE_IMPROVEMENT_ANALYSIS.md) - Proposed improvements
- [../TODO/PERFORMANCE_IMPROVEMENTS_ROADMAP.md](../TODO/PERFORMANCE_IMPROVEMENTS_ROADMAP.md) - Roadmap & timeline
- [monitoring/README.md](../../monitoring/README.md) - Monitoring stack guide

---

**Version**: 1.0  
**Last Updated**: 2025-10-20  
**Owner**: Fabric Management Team  
**Review Frequency**: Weekly (every Friday)

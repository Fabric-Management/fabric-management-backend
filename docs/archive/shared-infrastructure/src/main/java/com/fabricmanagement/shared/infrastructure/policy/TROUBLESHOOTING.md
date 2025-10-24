# Policy Framework Troubleshooting

This document provides troubleshooting guidance for common issues encountered with the Policy Framework.

## 🔍 Common Issues

### 1. Policy Not Found

#### Symptoms

- Policy evaluation returns "No policies found"
- Policy creation fails with "Policy not found"
- Policy update fails with "Policy not found"

#### Causes

- Policy name misspelling
- Policy is inactive
- Tenant ID mismatch
- Policy was deleted

#### Solutions

**Check Policy Name**

```java
// Verify policy name spelling
String policyName = "USER_READ_ACCESS"; // Check exact spelling
List<PolicyRegistry.Policy> policies = policyService.getPoliciesByName(policyName);
if (policies.isEmpty()) {
    log.warn("No policies found for: {}", policyName);
}
```

**Check Policy Status**

```java
// Verify policy is active
Optional<PolicyRegistry.Policy> policy = policyService.getPolicy(policyId);
if (policy.isPresent() && !policy.get().isActive()) {
    log.warn("Policy is inactive: {}", policyId);
}
```

**Check Tenant ID**

```java
// Verify tenant ID matches
PolicyContext context = PolicyContext.builder()
    .userId(userId)
    .tenantId(tenantId) // Ensure this matches policy tenant
    .build();
```

### 2. Policy Evaluation Fails

#### Symptoms

- Policy evaluation throws exception
- Policy evaluation returns unexpected result
- Policy evaluation takes too long

#### Causes

- Invalid policy conditions
- Invalid policy rules
- Policy context issues
- Performance problems

#### Solutions

**Validate Policy Conditions**

```java
// Check policy conditions
Map<String, Object> conditions = policy.getConditions();
for (Map.Entry<String, Object> entry : conditions.entrySet()) {
    String key = entry.getKey();
    Object value = entry.getValue();

    if (!isValidConditionKey(key)) {
        log.error("Invalid condition key: {}", key);
    }

    if (!isValidConditionValue(value)) {
        log.error("Invalid condition value: {}", value);
    }
}
```

**Validate Policy Context**

```java
// Check policy context
PolicyContext context = PolicyContext.builder()
    .userId(userId)
    .tenantId(tenantId)
    .permission(permission)
    .resourceType(resourceType)
    .resourceId(resourceId)
    .build();

// Validate required fields
if (context.getUserId() == null) {
    log.error("User ID is required");
}
if (context.getTenantId() == null) {
    log.error("Tenant ID is required");
}
```

**Check Policy Rules**

```java
// Validate policy rules
Map<String, Object> rules = policy.getRules();
for (Map.Entry<String, Object> entry : rules.entrySet()) {
    String key = entry.getKey();
    Object value = entry.getValue();

    if (!isValidRuleKey(key)) {
        log.error("Invalid rule key: {}", key);
    }

    if (!isValidRuleValue(value)) {
        log.error("Invalid rule value: {}", value);
    }
}
```

### 3. Cache Issues

#### Symptoms

- Inconsistent policy evaluation results
- Cache hit ratio is low
- Cache operations fail
- Performance degradation

#### Causes

- Redis connectivity issues
- Cache configuration problems
- Cache key conflicts
- Memory issues

#### Solutions

**Check Redis Connectivity**

```java
@Component
public class RedisHealthCheck {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public boolean isRedisHealthy() {
        try {
            redisTemplate.opsForValue().set("health-check", "ok");
            String value = (String) redisTemplate.opsForValue().get("health-check");
            return "ok".equals(value);
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return false;
        }
    }
}
```

**Check Cache Configuration**

```yaml
# Verify cache configuration
policy:
  cache:
    enabled: true
    policyTtlMinutes: 30
    userPoliciesTtlMinutes: 15
    rolePoliciesTtlMinutes: 60
    tenantPoliciesTtlMinutes: 120
```

**Clear Cache**

```java
// Clear cache if needed
@Autowired
private PolicyCache policyCache;

public void clearPolicyCache() {
    policyCache.clearAllCaches();
    log.info("Policy cache cleared");
}
```

### 4. Performance Issues

#### Symptoms

- Policy evaluation takes > 10ms
- Low throughput
- High memory usage
- High CPU usage

#### Causes

- Complex policy conditions
- Database queries in evaluation
- Inefficient caching
- Resource constraints

#### Solutions

**Optimize Policy Conditions**

```java
// Simplify policy conditions
Map<String, Object> simpleConditions = Map.of(
    "user_id", "{{userId}}",
    "tenant_id", "{{tenantId}}"
);

// Avoid complex conditions
Map<String, Object> complexConditions = Map.of(
    "user_id", "{{userId}}",
    "tenant_id", "{{tenantId}}",
    "role_name", "{{roleName}}",
    "permission", "{{permission}}",
    "resource_type", "{{resourceType}}",
    "resource_id", "{{resourceId}}",
    "ip_address", "{{ipAddress}}",
    "time_range", "{{timeRange}}"
);
```

**Enable Caching**

```yaml
# Enable caching for performance
policy:
  cache:
    enabled: true
    policyTtlMinutes: 30
    userPoliciesTtlMinutes: 15
    rolePoliciesTtlMinutes: 60
    tenantPoliciesTtlMinutes: 120
```

**Monitor Performance**

```java
@Component
public class PolicyPerformanceMonitor {

    private final MeterRegistry meterRegistry;
    private final Timer policyEvaluationTimer;

    public PolicyPerformanceMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.policyEvaluationTimer = Timer.builder("policy.evaluation.duration")
            .register(meterRegistry);
    }

    public void monitorPolicyEvaluation(String policyName, Duration duration) {
        policyEvaluationTimer.record(duration, Tags.of("policy", policyName));

        if (duration.toMillis() > 10) {
            log.warn("Slow policy evaluation: {} took {}ms", policyName, duration.toMillis());
        }
    }
}
```

### 5. Database Issues

#### Symptoms

- Policy operations fail
- Database connection errors
- Slow database queries
- Transaction failures

#### Causes

- Database connectivity issues
- Connection pool exhaustion
- Slow queries
- Transaction deadlocks

#### Solutions

**Check Database Connectivity**

```java
@Component
public class DatabaseHealthCheck {

    @Autowired
    private DataSource dataSource;

    public boolean isDatabaseHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (SQLException e) {
            log.error("Database health check failed", e);
            return false;
        }
    }
}
```

**Optimize Database Queries**

```java
@Repository
public class PolicyRegistryRepository {

    // Use indexed queries
    @Query("SELECT p FROM Policy p WHERE p.tenantId = :tenantId AND p.isActive = true")
    List<PolicyRegistry.Policy> findActivePoliciesByTenant(@Param("tenantId") UUID tenantId);

    // Use batch operations
    @Modifying
    @Query("UPDATE Policy p SET p.isActive = false WHERE p.tenantId = :tenantId")
    int deactivatePoliciesByTenant(@Param("tenantId") UUID tenantId);
}
```

**Configure Connection Pool**

```yaml
# Optimize connection pool
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 6. Configuration Issues

#### Symptoms

- Policy framework not working
- Configuration errors
- Bean creation failures
- Property resolution issues

#### Causes

- Missing configuration properties
- Invalid property values
- Bean definition conflicts
- Classpath issues

#### Solutions

**Check Configuration Properties**

```yaml
# Verify all required properties are set
policy:
  cache:
    enabled: true
    policyTtlMinutes: 30
  evaluation:
    enabled: true
    timeoutMs: 5000
  maintenance:
    enabled: true
    intervalMs: 3600000
  security:
    enabled: true
```

**Validate Property Values**

```java
@Component
@ConfigurationProperties(prefix = "policy")
@Validated
public class PolicyProperties {

    @NotNull
    private Cache cache;

    @NotNull
    private Evaluation evaluation;

    @NotNull
    private Maintenance maintenance;

    @NotNull
    private Security security;

    // Getters and setters
}
```

**Check Bean Definitions**

```java
@Configuration
@EnableConfigurationProperties(PolicyProperties.class)
public class PolicyConfiguration {

    @Bean
    @ConditionalOnProperty(name = "policy.cache.enabled", havingValue = "true")
    public PolicyCache policyCache(RedisTemplate<String, Object> redisTemplate,
                                  PolicyRegistry policyRegistry) {
        return new PolicyCache(redisTemplate, policyRegistry);
    }
}
```

## 🔧 Debugging Tools

### Debug Logging

```yaml
# Enable debug logging
logging:
  level:
    com.fabricmanagement.shared.infrastructure.policy: DEBUG
    org.springframework.cache: DEBUG
    org.springframework.data.redis: DEBUG
```

### Performance Profiling

```java
@Component
public class PolicyProfiler {

    public void profilePolicyEvaluation(String policyName, PolicyContext context) {
        long startTime = System.nanoTime();

        try {
            PolicyDecision decision = policyService.evaluatePolicy(policyName, context);

            long endTime = System.nanoTime();
            long duration = endTime - startTime;

            log.debug("Policy evaluation: {} took {}ns", policyName, duration);

        } catch (Exception e) {
            log.error("Policy evaluation failed: {}", policyName, e);
        }
    }
}
```

### Health Checks

```java
@Component
public class PolicyHealthIndicator implements HealthIndicator {

    @Autowired
    private PolicyRegistry policyRegistry;

    @Autowired
    private PolicyCache policyCache;

    @Override
    public Health health() {
        try {
            // Check policy registry
            List<PolicyRegistry.Policy> policies = policyRegistry.findAll();

            // Check cache
            boolean cacheHealthy = policyCache.getCacheStatistics() != null;

            return Health.up()
                .withDetail("policies", policies.size())
                .withDetail("cache", cacheHealthy)
                .build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

## 📊 Monitoring and Alerting

### Key Metrics to Monitor

1. **Policy Evaluation Metrics**

   - Evaluation count
   - Evaluation duration
   - Success rate
   - Error rate

2. **Cache Metrics**

   - Hit ratio
   - Miss ratio
   - Eviction rate
   - Memory usage

3. **Performance Metrics**
   - Response time
   - Throughput
   - Resource usage
   - Error rate

### Alerting Rules

```yaml
# Example alerting rules
alerts:
  - name: PolicyEvaluationSlow
    condition: policy.evaluation.duration > 10ms
    severity: warning

  - name: PolicyEvaluationFailed
    condition: policy.evaluation.error.rate > 5%
    severity: critical

  - name: CacheHitRatioLow
    condition: policy.cache.hit.ratio < 80%
    severity: warning

  - name: PolicyRegistryDown
    condition: policy.registry.health == down
    severity: critical
```

## 🚨 Emergency Procedures

### Policy Framework Down

1. **Check Health Status**

   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **Check Logs**

   ```bash
   tail -f logs/application.log | grep -i policy
   ```

3. **Restart Service**

   ```bash
   systemctl restart policy-service
   ```

4. **Verify Recovery**
   ```bash
   curl http://localhost:8080/api/v1/policies/health
   ```

### Cache Issues

1. **Clear Cache**

   ```java
   policyCache.clearAllCaches();
   ```

2. **Restart Redis**

   ```bash
   systemctl restart redis
   ```

3. **Verify Cache**
   ```java
   boolean cacheHealthy = policyCache.getCacheStatistics() != null;
   ```

### Database Issues

1. **Check Database**

   ```bash
   psql -h localhost -U policy_user -d policy_db -c "SELECT 1"
   ```

2. **Check Connections**

   ```bash
   netstat -an | grep :5432
   ```

3. **Restart Database**
   ```bash
   systemctl restart postgresql
   ```

## 📞 Support Contacts

### Internal Support

- **Development Team**: dev@fabricmanagement.com
- **Operations Team**: ops@fabricmanagement.com
- **Security Team**: security@fabricmanagement.com

### External Support

- **Spring Support**: https://spring.io/support
- **Redis Support**: https://redis.io/support
- **PostgreSQL Support**: https://www.postgresql.org/support/

## 📚 Additional Resources

### Documentation

- [Policy Framework README](README.md)
- [Policy Framework API](API.md)
- [Policy Framework Examples](EXAMPLES.md)
- [Policy Framework Migration](MIGRATION.md)

### Community

- [GitHub Issues](https://github.com/fabricmanagement/policy-framework/issues)
- [GitHub Discussions](https://github.com/fabricmanagement/policy-framework/discussions)
- [Stack Overflow](https://stackoverflow.com/questions/tagged/policy-framework)

### Training

- [Policy Framework Training](https://fabricmanagement.com/training)
- [Online Courses](https://fabricmanagement.com/courses)
- [Certification Program](https://fabricmanagement.com/certification)

---

**Remember**: When troubleshooting, always check the logs first, then verify configuration, and finally test with minimal examples to isolate the issue.

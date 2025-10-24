# Policy Framework Migration Guide

This guide helps you migrate from existing authorization systems to the Policy Framework.

## 🚀 Migration Overview

The Policy Framework provides a comprehensive authorization system that can replace or complement existing authorization mechanisms. This guide covers migration strategies, step-by-step processes, and best practices.

## 📋 Migration Strategies

### 1. Gradual Migration

**Recommended for production systems**

- Migrate one service at a time
- Run both systems in parallel
- Gradually shift traffic to new system
- Monitor performance and functionality

### 2. Big Bang Migration

**Suitable for new projects or small systems**

- Migrate entire system at once
- Requires comprehensive testing
- Higher risk but faster completion
- Suitable for non-production environments

### 3. Hybrid Approach

**Best of both worlds**

- Keep existing system for critical paths
- Use Policy Framework for new features
- Gradually migrate existing features
- Maintain backward compatibility

## 🔧 Step-by-Step Migration

### Phase 1: Preparation

#### 1.1 Audit Current System

```bash
# Identify current authorization mechanisms
grep -r "hasRole\|hasPermission\|@PreAuthorize" src/
grep -r "SecurityContext\|Authentication" src/
grep -r "authorize\|permitAll" src/
```

#### 1.2 Document Current Policies

Create a mapping of current authorization rules:

```yaml
# Current authorization rules
current_rules:
  - resource: "USER"
    permissions: ["READ", "WRITE", "DELETE"]
    roles: ["ADMIN", "USER"]
    conditions: "user_id == current_user_id"

  - resource: "COMPANY"
    permissions: ["READ", "WRITE"]
    roles: ["ADMIN", "MANAGER"]
    conditions: "tenant_id == current_tenant_id"
```

#### 1.3 Set Up Policy Framework

```xml
<!-- Add dependency -->
<dependency>
    <groupId>com.fabricmanagement</groupId>
    <artifactId>shared-infrastructure</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
# Configure Policy Framework
policy:
  cache:
    enabled: true
    policyTtlMinutes: 30
  evaluation:
    enabled: true
    timeoutMs: 5000
```

### Phase 2: Policy Creation

#### 2.1 Create Base Policies

```java
@Service
public class PolicyMigrationService {

    @Autowired
    private PolicyService policyService;

    public void createBasePolicies() {
        // User access policies
        createUserPolicies();

        // Company access policies
        createCompanyPolicies();

        // Admin policies
        createAdminPolicies();
    }

    private void createUserPolicies() {
        // User read access
        PolicyService.CreatePolicyRequest userReadRequest = PolicyService.CreatePolicyRequest.builder()
            .name("USER_READ_ACCESS")
            .description("Users can read their own data")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(systemTenantId)
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "user_id", "{{userId}}",
                "resource_type", "USER"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(userReadRequest);

        // User write access
        PolicyService.CreatePolicyRequest userWriteRequest = PolicyService.CreatePolicyRequest.builder()
            .name("USER_WRITE_ACCESS")
            .description("Users can write their own data")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(systemTenantId)
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "user_id", "{{userId}}",
                "resource_type", "USER"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(userWriteRequest);
    }
}
```

#### 2.2 Migrate Role-Based Access

```java
// Before: Role-based access
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(UUID userId) {
    // Implementation
}

// After: Policy-based access
public void deleteUser(UUID userId) {
    PolicyContext context = PolicyContext.builder()
        .userId(getCurrentUserId())
        .tenantId(getCurrentTenantId())
        .permission("DELETE")
        .resourceType("USER")
        .resourceId(userId)
        .roleName("ADMIN")
        .build();

    PolicyDecision decision = policyService.evaluatePolicy("USER_DELETE_ACCESS", context);
    if (!decision.isAllowed()) {
        throw new AccessDeniedException("Insufficient permissions");
    }

    // Implementation
}
```

#### 2.3 Migrate Permission Checks

```java
// Before: Permission checks
if (hasPermission("READ", "USER", userId)) {
    // Allow access
}

// After: Policy evaluation
PolicyContext context = PolicyContext.builder()
    .userId(getCurrentUserId())
    .tenantId(getCurrentTenantId())
    .permission("READ")
    .resourceType("USER")
    .resourceId(userId)
    .build();

PolicyDecision decision = policyService.evaluatePolicy("USER_READ_ACCESS", context);
if (decision.isAllowed()) {
    // Allow access
}
```

### Phase 3: Service Integration

#### 3.1 Update Service Layer

```java
@Service
public class UserService {

    @Autowired
    private PolicyService policyService;

    public User getUser(UUID userId) {
        // Check access using Policy Framework
        if (!hasUserAccess(userId, "READ")) {
            throw new AccessDeniedException("Insufficient permissions");
        }

        return userRepository.findById(userId);
    }

    public User updateUser(UUID userId, UserUpdateRequest request) {
        // Check access using Policy Framework
        if (!hasUserAccess(userId, "WRITE")) {
            throw new AccessDeniedException("Insufficient permissions");
        }

        return userRepository.save(updatedUser);
    }

    private boolean hasUserAccess(UUID userId, String permission) {
        PolicyContext context = PolicyContext.builder()
            .userId(getCurrentUserId())
            .tenantId(getCurrentTenantId())
            .permission(permission)
            .resourceType("USER")
            .resourceId(userId)
            .build();

        PolicyDecision decision = policyService.evaluatePolicy("USER_ACCESS", context);
        return decision.isAllowed();
    }
}
```

#### 3.2 Update Controller Layer

```java
@RestController
public class UserController {

    @Autowired
    private PolicyService policyService;

    @GetMapping("/users/{userId}")
    public ResponseEntity<User> getUser(@PathVariable UUID userId) {
        // Policy check
        if (!policyService.hasPermission(
            getCurrentUserId(),
            getCurrentTenantId(),
            "READ",
            "USER",
            userId
        )) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        User user = userService.getUser(userId);
        return ResponseEntity.ok(user);
    }
}
```

### Phase 4: Testing and Validation

#### 4.1 Unit Tests

```java
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = PolicyTestConfiguration.class)
class UserServiceTest {

    @Mock
    private PolicyService policyService;

    @Test
    void shouldAllowUserReadAccess() {
        // Given
        UUID userId = PolicyTestUtils.createTestUUID();
        when(policyService.hasPermission(any(), any(), eq("READ"), eq("USER"), eq(userId)))
            .thenReturn(true);

        // When
        User user = userService.getUser(userId);

        // Then
        assertThat(user).isNotNull();
    }

    @Test
    void shouldDenyUserWriteAccess() {
        // Given
        UUID userId = PolicyTestUtils.createTestUUID();
        when(policyService.hasPermission(any(), any(), eq("WRITE"), eq("USER"), eq(userId)))
            .thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> userService.updateUser(userId, request))
            .isInstanceOf(AccessDeniedException.class);
    }
}
```

#### 4.2 Integration Tests

```java
@SpringBootTest
@TestPropertySource(properties = {
    "policy.cache.enabled=false",
    "policy.evaluation.enabled=true"
})
class PolicyIntegrationTest {

    @Autowired
    private PolicyService policyService;

    @Test
    void shouldEvaluateUserAccessPolicy() {
        // Given
        PolicyContext context = PolicyTestUtils.createTestPolicyContext();

        // When
        PolicyDecision decision = policyService.evaluatePolicy("USER_READ_ACCESS", context);

        // Then
        assertThat(decision.isAllowed()).isTrue();
    }
}
```

### Phase 5: Performance Optimization

#### 5.1 Enable Caching

```yaml
policy:
  cache:
    enabled: true
    policyTtlMinutes: 30
    userPoliciesTtlMinutes: 15
    rolePoliciesTtlMinutes: 60
    tenantPoliciesTtlMinutes: 120
```

#### 5.2 Monitor Performance

```java
@Component
public class PolicyMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer policyEvaluationTimer;
    private final Counter policyEvaluationCounter;

    public PolicyMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.policyEvaluationTimer = Timer.builder("policy.evaluation.duration")
            .register(meterRegistry);
        this.policyEvaluationCounter = Counter.builder("policy.evaluation.count")
            .register(meterRegistry);
    }

    public void recordPolicyEvaluation(Duration duration) {
        policyEvaluationTimer.record(duration);
        policyEvaluationCounter.increment();
    }
}
```

### Phase 6: Cleanup

#### 6.1 Remove Old Code

```bash
# Remove old authorization annotations
find src/ -name "*.java" -exec sed -i '/@PreAuthorize/d' {} \;
find src/ -name "*.java" -exec sed -i '/@Secured/d' {} \;

# Remove old security imports
find src/ -name "*.java" -exec sed -i '/import.*PreAuthorize/d' {} \;
find src/ -name "*.java" -exec sed -i '/import.*Secured/d' {} \;
```

#### 6.2 Update Dependencies

```xml
<!-- Remove old security dependencies -->
<!-- <dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-config</artifactId>
</dependency> -->

<!-- Keep only Policy Framework -->
<dependency>
    <groupId>com.fabricmanagement</groupId>
    <artifactId>shared-infrastructure</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 🔍 Migration Checklist

### Pre-Migration

- [ ] Audit current authorization system
- [ ] Document existing policies and rules
- [ ] Set up Policy Framework
- [ ] Create test environment
- [ ] Plan migration strategy

### During Migration

- [ ] Create base policies
- [ ] Migrate role-based access
- [ ] Migrate permission checks
- [ ] Update service layer
- [ ] Update controller layer
- [ ] Write comprehensive tests
- [ ] Performance testing
- [ ] Security testing

### Post-Migration

- [ ] Monitor performance
- [ ] Verify functionality
- [ ] Clean up old code
- [ ] Update documentation
- [ ] Train team members
- [ ] Set up monitoring

## 🚨 Common Migration Issues

### 1. Performance Degradation

**Symptoms**: Slower response times after migration

**Solutions**:

- Enable policy caching
- Optimize policy conditions
- Monitor cache hit ratios
- Use policy evaluation metrics

### 2. Permission Denials

**Symptoms**: Users losing access to resources

**Solutions**:

- Verify policy conditions
- Check tenant ID matching
- Review policy priorities
- Test policy evaluation

### 3. Cache Issues

**Symptoms**: Inconsistent authorization decisions

**Solutions**:

- Check Redis connectivity
- Verify cache configuration
- Monitor cache statistics
- Clear cache if needed

### 4. Configuration Problems

**Symptoms**: Policies not loading or evaluating

**Solutions**:

- Check configuration properties
- Verify bean definitions
- Review policy definitions
- Test policy creation

## 📊 Migration Metrics

### Key Metrics to Track

1. **Migration Progress**

   - Number of services migrated
   - Percentage of policies migrated
   - Time to complete migration

2. **Performance Metrics**

   - Response time before/after
   - Throughput before/after
   - Cache hit ratios
   - Policy evaluation duration

3. **Quality Metrics**
   - Test coverage
   - Bug reports
   - Security incidents
   - User complaints

## 🎯 Best Practices

### 1. Migration Planning

- Start with non-critical services
- Use feature flags for gradual rollout
- Maintain rollback capability
- Document all changes

### 2. Testing Strategy

- Unit tests for all policy evaluations
- Integration tests for service interactions
- Performance tests for scalability
- Security tests for access control

### 3. Monitoring and Observability

- Set up comprehensive monitoring
- Track policy evaluation metrics
- Monitor cache performance
- Alert on authorization failures

### 4. Documentation

- Update API documentation
- Document policy definitions
- Create troubleshooting guides
- Train team members

## 📞 Support

For migration support:

1. **Documentation**: Review Policy Framework documentation
2. **Examples**: Check migration examples in the repository
3. **Community**: Join the community forum
4. **Issues**: Create GitHub issues for specific problems
5. **Professional Support**: Contact for enterprise support

## 🔄 Rollback Plan

If migration issues occur:

1. **Immediate Rollback**

   - Disable Policy Framework
   - Re-enable old authorization
   - Verify system functionality

2. **Investigation**

   - Analyze migration issues
   - Identify root causes
   - Plan fixes

3. **Re-migration**
   - Fix identified issues
   - Re-test migration
   - Re-apply migration

Remember: Always have a rollback plan and test it before starting migration!

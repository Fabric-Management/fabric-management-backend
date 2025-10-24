# Policy Framework Examples

This document provides comprehensive examples of how to use the Policy Framework in various scenarios.

## 🚀 Quick Start Examples

### Basic Policy Creation

```java
@Service
public class PolicyExampleService {

    @Autowired
    private PolicyService policyService;

    public void createBasicPolicies() {
        // Create user read access policy
        PolicyService.CreatePolicyRequest userReadRequest = PolicyService.CreatePolicyRequest.builder()
            .name("USER_READ_ACCESS")
            .description("Users can read their own data")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
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

        PolicyRegistry.Policy userReadPolicy = policyService.createPolicy(userReadRequest);
        log.info("Created user read policy: {}", userReadPolicy.getId());

        // Create admin full access policy
        PolicyService.CreatePolicyRequest adminRequest = PolicyService.CreatePolicyRequest.builder()
            .name("ADMIN_FULL_ACCESS")
            .description("Admins have full access")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(100)
            .conditions(Map.of(
                "role_name", "ADMIN"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        PolicyRegistry.Policy adminPolicy = policyService.createPolicy(adminRequest);
        log.info("Created admin policy: {}", adminPolicy.getId());
    }
}
```

### Basic Policy Evaluation

```java
@Service
public class PolicyEvaluationExample {

    @Autowired
    private PolicyService policyService;

    public boolean checkUserAccess(UUID userId, UUID tenantId, UUID resourceId) {
        // Create policy context
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .tenantId(tenantId)
            .permission("READ")
            .resourceType("USER")
            .resourceId(resourceId)
            .build();

        // Evaluate policy
        PolicyDecision decision = policyService.evaluatePolicy("USER_READ_ACCESS", context);

        if (decision.isAllowed()) {
            log.info("Access granted for user: {}", userId);
            return true;
        } else {
            log.warn("Access denied for user: {} - {}", userId, decision.getReason());
            return false;
        }
    }

    public boolean checkAdminAccess(UUID userId, UUID tenantId) {
        // Create policy context
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .tenantId(tenantId)
            .roleName("ADMIN")
            .build();

        // Evaluate policy
        PolicyDecision decision = policyService.evaluatePolicy("ADMIN_FULL_ACCESS", context);

        return decision.isAllowed();
    }
}
```

## 🔐 Access Control Examples

### Role-Based Access Control (RBAC)

```java
@Service
public class RoleBasedAccessExample {

    @Autowired
    private PolicyService policyService;

    public void createRoleBasedPolicies() {
        // Super Admin Policy
        PolicyService.CreatePolicyRequest superAdminRequest = PolicyService.CreatePolicyRequest.builder()
            .name("SUPER_ADMIN_ACCESS")
            .description("Super admins have platform-wide access")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(100)
            .conditions(Map.of(
                "role_name", "SUPER_ADMIN",
                "scope", "PLATFORM"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(superAdminRequest);

        // Tenant Admin Policy
        PolicyService.CreatePolicyRequest tenantAdminRequest = PolicyService.CreatePolicyRequest.builder()
            .name("TENANT_ADMIN_ACCESS")
            .description("Tenant admins have tenant-wide access")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(90)
            .conditions(Map.of(
                "role_name", "TENANT_ADMIN",
                "scope", "TENANT"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(tenantAdminRequest);

        // Manager Policy
        PolicyService.CreatePolicyRequest managerRequest = PolicyService.CreatePolicyRequest.builder()
            .name("MANAGER_ACCESS")
            .description("Managers have department access")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(70)
            .conditions(Map.of(
                "role_name", "MANAGER",
                "scope", "DEPARTMENT"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(managerRequest);

        // User Policy
        PolicyService.CreatePolicyRequest userRequest = PolicyService.CreatePolicyRequest.builder()
            .name("USER_ACCESS")
            .description("Users have limited access")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "role_name", "USER",
                "scope", "SELF"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(userRequest);
    }

    public boolean hasRoleAccess(UUID userId, UUID tenantId, String roleName) {
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .tenantId(tenantId)
            .roleName(roleName)
            .build();

        PolicyDecision decision = policyService.evaluatePolicy(roleName + "_ACCESS", context);
        return decision.isAllowed();
    }
}
```

### Resource-Based Access Control

```java
@Service
public class ResourceBasedAccessExample {

    @Autowired
    private PolicyService policyService;

    public void createResourceBasedPolicies() {
        // User Resource Policies
        createUserResourcePolicies();

        // Company Resource Policies
        createCompanyResourcePolicies();

        // Order Resource Policies
        createOrderResourcePolicies();
    }

    private void createUserResourcePolicies() {
        // User Read Policy
        PolicyService.CreatePolicyRequest userReadRequest = PolicyService.CreatePolicyRequest.builder()
            .name("USER_RESOURCE_READ")
            .description("Read access to user resources")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "resource_type", "USER",
                "permission", "READ"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(userReadRequest);

        // User Write Policy
        PolicyService.CreatePolicyRequest userWriteRequest = PolicyService.CreatePolicyRequest.builder()
            .name("USER_RESOURCE_WRITE")
            .description("Write access to user resources")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(60)
            .conditions(Map.of(
                "resource_type", "USER",
                "permission", "WRITE"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(userWriteRequest);

        // User Delete Policy
        PolicyService.CreatePolicyRequest userDeleteRequest = PolicyService.CreatePolicyRequest.builder()
            .name("USER_RESOURCE_DELETE")
            .description("Delete access to user resources")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(70)
            .conditions(Map.of(
                "resource_type", "USER",
                "permission", "DELETE"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(userDeleteRequest);
    }

    private void createCompanyResourcePolicies() {
        // Company Read Policy
        PolicyService.CreatePolicyRequest companyReadRequest = PolicyService.CreatePolicyRequest.builder()
            .name("COMPANY_RESOURCE_READ")
            .description("Read access to company resources")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "resource_type", "COMPANY",
                "permission", "READ"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(companyReadRequest);

        // Company Write Policy
        PolicyService.CreatePolicyRequest companyWriteRequest = PolicyService.CreatePolicyRequest.builder()
            .name("COMPANY_RESOURCE_WRITE")
            .description("Write access to company resources")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(60)
            .conditions(Map.of(
                "resource_type", "COMPANY",
                "permission", "WRITE"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(companyWriteRequest);
    }

    private void createOrderResourcePolicies() {
        // Order Read Policy
        PolicyService.CreatePolicyRequest orderReadRequest = PolicyService.CreatePolicyRequest.builder()
            .name("ORDER_RESOURCE_READ")
            .description("Read access to order resources")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "resource_type", "ORDER",
                "permission", "READ"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(orderReadRequest);

        // Order Write Policy
        PolicyService.CreatePolicyRequest orderWriteRequest = PolicyService.CreatePolicyRequest.builder()
            .name("ORDER_RESOURCE_WRITE")
            .description("Write access to order resources")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(60)
            .conditions(Map.of(
                "resource_type", "ORDER",
                "permission", "WRITE"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(orderWriteRequest);
    }

    public boolean hasResourceAccess(UUID userId, UUID tenantId, String resourceType, String permission, UUID resourceId) {
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .tenantId(tenantId)
            .permission(permission)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .build();

        PolicyDecision decision = policyService.evaluatePolicy(resourceType + "_RESOURCE_" + permission, context);
        return decision.isAllowed();
    }
}
```

## 🔒 Security Policy Examples

### Password Security Policies

```java
@Service
public class PasswordSecurityExample {

    @Autowired
    private PolicyService policyService;

    public void createPasswordSecurityPolicies() {
        // Password Strength Policy
        PolicyService.CreatePolicyRequest passwordStrengthRequest = PolicyService.CreatePolicyRequest.builder()
            .name("PASSWORD_STRENGTH")
            .description("Password strength requirements")
            .type(PolicyRegistry.PolicyType.SECURITY)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(100)
            .conditions(Map.of(
                "resource_type", "PASSWORD"
            ))
            .rules(Map.of(
                "min_length", 8,
                "require_uppercase", true,
                "require_lowercase", true,
                "require_numbers", true,
                "require_special_chars", true,
                "max_length", 128
            ))
            .build();

        policyService.createPolicy(passwordStrengthRequest);

        // Password History Policy
        PolicyService.CreatePolicyRequest passwordHistoryRequest = PolicyService.CreatePolicyRequest.builder()
            .name("PASSWORD_HISTORY")
            .description("Password history requirements")
            .type(PolicyRegistry.PolicyType.SECURITY)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(90)
            .conditions(Map.of(
                "resource_type", "PASSWORD"
            ))
            .rules(Map.of(
                "history_count", 5,
                "prevent_reuse", true
            ))
            .build();

        policyService.createPolicy(passwordHistoryRequest);

        // Password Expiration Policy
        PolicyService.CreatePolicyRequest passwordExpirationRequest = PolicyService.CreatePolicyRequest.builder()
            .name("PASSWORD_EXPIRATION")
            .description("Password expiration requirements")
            .type(PolicyRegistry.PolicyType.SECURITY)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(80)
            .conditions(Map.of(
                "resource_type", "PASSWORD"
            ))
            .rules(Map.of(
                "expiration_days", 90,
                "warning_days", 7
            ))
            .build();

        policyService.createPolicy(passwordExpirationRequest);
    }

    public boolean validatePassword(String password) {
        PolicyContext context = PolicyContext.builder()
            .resourceType("PASSWORD")
            .build();

        PolicyDecision decision = policyService.evaluatePolicy("PASSWORD_STRENGTH", context);
        return decision.isAllowed();
    }
}
```

### Account Lockout Policies

```java
@Service
public class AccountLockoutExample {

    @Autowired
    private PolicyService policyService;

    public void createAccountLockoutPolicies() {
        // Account Lockout Policy
        PolicyService.CreatePolicyRequest lockoutRequest = PolicyService.CreatePolicyRequest.builder()
            .name("ACCOUNT_LOCKOUT")
            .description("Account lockout after failed attempts")
            .type(PolicyRegistry.PolicyType.SECURITY)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(100)
            .conditions(Map.of(
                "resource_type", "LOGIN"
            ))
            .rules(Map.of(
                "max_attempts", 5,
                "lockout_duration_minutes", 30,
                "reset_after_success", true
            ))
            .build();

        policyService.createPolicy(lockoutRequest);

        // Progressive Lockout Policy
        PolicyService.CreatePolicyRequest progressiveLockoutRequest = PolicyService.CreatePolicyRequest.builder()
            .name("PROGRESSIVE_LOCKOUT")
            .description("Progressive lockout for repeated failures")
            .type(PolicyRegistry.PolicyType.SECURITY)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(90)
            .conditions(Map.of(
                "resource_type", "LOGIN"
            ))
            .rules(Map.of(
                "first_lockout_minutes", 5,
                "second_lockout_minutes", 15,
                "third_lockout_minutes", 60,
                "max_lockout_minutes", 1440
            ))
            .build();

        policyService.createPolicy(progressiveLockoutRequest);
    }

    public boolean shouldLockAccount(int failedAttempts) {
        PolicyContext context = PolicyContext.builder()
            .resourceType("LOGIN")
            .build();

        PolicyDecision decision = policyService.evaluatePolicy("ACCOUNT_LOCKOUT", context);
        return decision.isAllowed();
    }
}
```

## 🌐 Multi-Tenant Examples

### Tenant-Specific Policies

```java
@Service
public class MultiTenantExample {

    @Autowired
    private PolicyService policyService;

    public void createTenantSpecificPolicies(UUID tenantId) {
        // Tenant-specific user access policy
        PolicyService.CreatePolicyRequest tenantUserRequest = PolicyService.CreatePolicyRequest.builder()
            .name("TENANT_USER_ACCESS")
            .description("Tenant-specific user access")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(tenantId)
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "tenant_id", tenantId.toString(),
                "resource_type", "USER"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(tenantUserRequest);

        // Tenant-specific company access policy
        PolicyService.CreatePolicyRequest tenantCompanyRequest = PolicyService.CreatePolicyRequest.builder()
            .name("TENANT_COMPANY_ACCESS")
            .description("Tenant-specific company access")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(tenantId)
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "tenant_id", tenantId.toString(),
                "resource_type", "COMPANY"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(tenantCompanyRequest);
    }

    public boolean hasTenantAccess(UUID userId, UUID tenantId, String resourceType) {
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .tenantId(tenantId)
            .resourceType(resourceType)
            .build();

        PolicyDecision decision = policyService.evaluatePolicy("TENANT_" + resourceType + "_ACCESS", context);
        return decision.isAllowed();
    }
}
```

## 📊 Advanced Policy Examples

### Time-Based Policies

```java
@Service
public class TimeBasedPolicyExample {

    @Autowired
    private PolicyService policyService;

    public void createTimeBasedPolicies() {
        // Business Hours Policy
        PolicyService.CreatePolicyRequest businessHoursRequest = PolicyService.CreatePolicyRequest.builder()
            .name("BUSINESS_HOURS_ACCESS")
            .description("Access only during business hours")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "time_range", "BUSINESS_HOURS"
            ))
            .rules(Map.of(
                "start_hour", 9,
                "end_hour", 17,
                "timezone", "UTC"
            ))
            .build();

        policyService.createPolicy(businessHoursRequest);

        // Weekend Access Policy
        PolicyService.CreatePolicyRequest weekendRequest = PolicyService.CreatePolicyRequest.builder()
            .name("WEEKEND_ACCESS")
            .description("Limited access on weekends")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(30)
            .conditions(Map.of(
                "time_range", "WEEKEND"
            ))
            .rules(Map.of(
                "allow", true,
                "restricted_permissions", true
            ))
            .build();

        policyService.createPolicy(weekendRequest);
    }

    public boolean isBusinessHours() {
        PolicyContext context = PolicyContext.builder()
            .build();

        PolicyDecision decision = policyService.evaluatePolicy("BUSINESS_HOURS_ACCESS", context);
        return decision.isAllowed();
    }
}
```

### Location-Based Policies

```java
@Service
public class LocationBasedPolicyExample {

    @Autowired
    private PolicyService policyService;

    public void createLocationBasedPolicies() {
        // Office Access Policy
        PolicyService.CreatePolicyRequest officeRequest = PolicyService.CreatePolicyRequest.builder()
            .name("OFFICE_ACCESS")
            .description("Access from office network")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "ip_range", "192.168.1.0/24"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(officeRequest);

        // Remote Access Policy
        PolicyService.CreatePolicyRequest remoteRequest = PolicyService.CreatePolicyRequest.builder()
            .name("REMOTE_ACCESS")
            .description("Access from remote locations")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(30)
            .conditions(Map.of(
                "ip_range", "EXTERNAL"
            ))
            .rules(Map.of(
                "allow", true,
                "require_vpn", true
            ))
            .build();

        policyService.createPolicy(remoteRequest);
    }

    public boolean hasLocationAccess(String ipAddress) {
        PolicyContext context = PolicyContext.builder()
            .ipAddress(ipAddress)
            .build();

        PolicyDecision decision = policyService.evaluatePolicy("OFFICE_ACCESS", context);
        return decision.isAllowed();
    }
}
```

## 🔄 Policy Management Examples

### Policy Lifecycle Management

```java
@Service
public class PolicyLifecycleExample {

    @Autowired
    private PolicyService policyService;

    public void managePolicyLifecycle() {
        // Create policy
        PolicyService.CreatePolicyRequest request = PolicyService.CreatePolicyRequest.builder()
            .name("LIFECYCLE_POLICY")
            .description("Policy for lifecycle management")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "user_id", "{{userId}}"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .validFrom(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusDays(30))
            .build();

        PolicyRegistry.Policy policy = policyService.createPolicy(request);
        log.info("Created policy: {}", policy.getId());

        // Update policy
        PolicyService.UpdatePolicyRequest updateRequest = PolicyService.UpdatePolicyRequest.builder()
            .description("Updated policy description")
            .priority(60)
            .build();

        PolicyRegistry.Policy updatedPolicy = policyService.updatePolicy(policy.getId(), updateRequest);
        log.info("Updated policy: {}", updatedPolicy.getId());

        // Deactivate policy
        PolicyRegistry.Policy deactivatedPolicy = policyService.deactivatePolicy(policy.getId());
        log.info("Deactivated policy: {}", deactivatedPolicy.getId());

        // Reactivate policy
        PolicyRegistry.Policy reactivatedPolicy = policyService.activatePolicy(policy.getId());
        log.info("Reactivated policy: {}", reactivatedPolicy.getId());

        // Delete policy
        policyService.deletePolicy(policy.getId());
        log.info("Deleted policy: {}", policy.getId());
    }
}
```

### Policy Versioning

```java
@Service
public class PolicyVersioningExample {

    @Autowired
    private PolicyService policyService;

    public void createPolicyVersions() {
        // Version 1.0
        PolicyService.CreatePolicyRequest v1Request = PolicyService.CreatePolicyRequest.builder()
            .name("VERSIONED_POLICY_V1")
            .description("Version 1.0 of the policy")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "user_id", "{{userId}}"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .validFrom(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusDays(30))
            .build();

        PolicyRegistry.Policy v1Policy = policyService.createPolicy(v1Request);
        log.info("Created policy version 1.0: {}", v1Policy.getId());

        // Version 2.0
        PolicyService.CreatePolicyRequest v2Request = PolicyService.CreatePolicyRequest.builder()
            .name("VERSIONED_POLICY_V2")
            .description("Version 2.0 of the policy")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "user_id", "{{userId}}",
                "tenant_id", "{{tenantId}}"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .validFrom(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusDays(30))
            .build();

        PolicyRegistry.Policy v2Policy = policyService.createPolicy(v2Request);
        log.info("Created policy version 2.0: {}", v2Policy.getId());

        // Deactivate old version
        policyService.deactivatePolicy(v1Policy.getId());
        log.info("Deactivated policy version 1.0: {}", v1Policy.getId());
    }
}
```

## 🧪 Testing Examples

### Unit Testing

```java
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = PolicyTestConfiguration.class)
class PolicyExampleTest {

    @Mock
    private PolicyService policyService;

    @Test
    void shouldCreatePolicySuccessfully() {
        // Given
        PolicyService.CreatePolicyRequest request = PolicyTestUtils.createTestCreatePolicyRequest();
        PolicyRegistry.Policy expectedPolicy = PolicyTestUtils.createTestPolicy();

        when(policyService.createPolicy(request)).thenReturn(expectedPolicy);

        // When
        PolicyRegistry.Policy result = policyService.createPolicy(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(request.getName());
    }

    @Test
    void shouldEvaluatePolicySuccessfully() {
        // Given
        String policyName = "TEST_POLICY";
        PolicyContext context = PolicyTestUtils.createTestPolicyContext();
        PolicyDecision expectedDecision = PolicyTestUtils.createTestPolicyDecisionAllowed();

        when(policyService.evaluatePolicy(policyName, context)).thenReturn(expectedDecision);

        // When
        PolicyDecision result = policyService.evaluatePolicy(policyName, context);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isAllowed()).isTrue();
    }
}
```

### Integration Testing

```java
@SpringBootTest
@TestPropertySource(properties = {
    "policy.cache.enabled=false",
    "policy.evaluation.enabled=true"
})
class PolicyIntegrationExampleTest {

    @Autowired
    private PolicyService policyService;

    @Test
    void shouldCreateAndEvaluatePolicy() {
        // Given
        PolicyService.CreatePolicyRequest request = PolicyTestUtils.createTestCreatePolicyRequest();

        // When
        PolicyRegistry.Policy policy = policyService.createPolicy(request);
        PolicyContext context = PolicyTestUtils.createTestPolicyContext();
        PolicyDecision decision = policyService.evaluatePolicy(policy.getName(), context);

        // Then
        assertThat(policy).isNotNull();
        assertThat(decision).isNotNull();
        assertThat(decision.isAllowed()).isTrue();
    }
}
```

## 📚 Best Practices Examples

### Policy Design Best Practices

```java
@Service
public class PolicyDesignBestPractices {

    @Autowired
    private PolicyService policyService;

    public void demonstrateBestPractices() {
        // 1. Use descriptive policy names
        createDescriptivePolicy();

        // 2. Use appropriate priorities
        createPriorityBasedPolicy();

        // 3. Use simple conditions
        createSimpleConditionPolicy();

        // 4. Use clear rules
        createClearRulePolicy();

        // 5. Use proper validation
        createValidatedPolicy();
    }

    private void createDescriptivePolicy() {
        // Good: Descriptive name
        PolicyService.CreatePolicyRequest request = PolicyService.CreatePolicyRequest.builder()
            .name("USER_READ_OWN_PROFILE_ACCESS")
            .description("Users can read their own profile information")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "user_id", "{{userId}}",
                "resource_type", "USER_PROFILE"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(request);
    }

    private void createPriorityBasedPolicy() {
        // Good: Appropriate priority
        PolicyService.CreatePolicyRequest request = PolicyService.CreatePolicyRequest.builder()
            .name("ADMIN_OVERRIDE_ACCESS")
            .description("Admin override for all access")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(100) // High priority for admin
            .conditions(Map.of(
                "role_name", "ADMIN"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(request);
    }

    private void createSimpleConditionPolicy() {
        // Good: Simple conditions
        PolicyService.CreatePolicyRequest request = PolicyService.CreatePolicyRequest.builder()
            .name("SIMPLE_USER_ACCESS")
            .description("Simple user access policy")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "user_id", "{{userId}}"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .build();

        policyService.createPolicy(request);
    }

    private void createClearRulePolicy() {
        // Good: Clear rules
        PolicyService.CreatePolicyRequest request = PolicyService.CreatePolicyRequest.builder()
            .name("CLEAR_RULE_ACCESS")
            .description("Policy with clear rules")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "user_id", "{{userId}}"
            ))
            .rules(Map.of(
                "allow", true,
                "audit", true,
                "log", true
            ))
            .build();

        policyService.createPolicy(request);
    }

    private void createValidatedPolicy() {
        // Good: Proper validation
        PolicyService.CreatePolicyRequest request = PolicyService.CreatePolicyRequest.builder()
            .name("VALIDATED_ACCESS")
            .description("Policy with proper validation")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "user_id", "{{userId}}",
                "tenant_id", "{{tenantId}}"
            ))
            .rules(Map.of(
                "allow", true,
                "validate_input", true,
                "validate_output", true
            ))
            .validFrom(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusDays(30))
            .build();

        policyService.createPolicy(request);
    }
}
```

---

**Remember**: These examples demonstrate various use cases and best practices. Always adapt them to your specific requirements and security needs.

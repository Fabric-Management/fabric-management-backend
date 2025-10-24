package com.fabricmanagement.shared.infrastructure.policy;

import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Policy Test Utils
 * 
 * Utility class for policy testing.
 * Provides test data builders and helper methods for policy tests.
 * 
 * ✅ TEST-READY - Complete test utilities
 * ✅ BUILDER PATTERN - Fluent test data creation
 * ✅ REUSABLE - Common test scenarios
 * ✅ MAINTAINABLE - Easy to update test data
 */
@UtilityClass
public class PolicyTestUtils {

    /**
     * Create test policy
     */
    public static PolicyRegistry.Policy createTestPolicy() {
        return PolicyRegistry.Policy.builder()
            .name("TEST_POLICY")
            .description("Test policy for unit tests")
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
            .validFrom(LocalDateTime.now().minusDays(1))
            .validUntil(LocalDateTime.now().plusDays(1))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Create test policy with custom name
     */
    public static PolicyRegistry.Policy createTestPolicy(String name) {
        return PolicyRegistry.Policy.builder()
            .name(name)
            .description("Test policy for unit tests")
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
            .validFrom(LocalDateTime.now().minusDays(1))
            .validUntil(LocalDateTime.now().plusDays(1))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Create test policy with custom tenant
     */
    public static PolicyRegistry.Policy createTestPolicy(String name, UUID tenantId) {
        return PolicyRegistry.Policy.builder()
            .name(name)
            .description("Test policy for unit tests")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(tenantId)
            .isActive(true)
            .priority(50)
            .conditions(Map.of(
                "user_id", "{{userId}}",
                "tenant_id", "{{tenantId}}"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .validFrom(LocalDateTime.now().minusDays(1))
            .validUntil(LocalDateTime.now().plusDays(1))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Create test policy context
     */
    public static PolicyContext createTestPolicyContext() {
        return PolicyContext.builder()
            .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .permission("READ")
            .resourceType("USER")
            .resourceId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .roleName("USER")
            .ipAddress("192.168.1.1")
            .userAgent("TestAgent/1.0")
            .build();
    }

    /**
     * Create test policy context with custom user
     */
    public static PolicyContext createTestPolicyContext(UUID userId, UUID tenantId) {
        return PolicyContext.builder()
            .userId(userId)
            .tenantId(tenantId)
            .permission("READ")
            .resourceType("USER")
            .resourceId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .roleName("USER")
            .ipAddress("192.168.1.1")
            .userAgent("TestAgent/1.0")
            .build();
    }

    /**
     * Create test policy context with custom permission
     */
    public static PolicyContext createTestPolicyContext(String permission, String resourceType) {
        return PolicyContext.builder()
            .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .permission(permission)
            .resourceType(resourceType)
            .resourceId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .roleName("USER")
            .ipAddress("192.168.1.1")
            .userAgent("TestAgent/1.0")
            .build();
    }

    /**
     * Create test policy decision (allowed)
     */
    public static PolicyDecision createTestPolicyDecisionAllowed() {
        return PolicyDecision.allow("Test policy decision");
    }

    /**
     * Create test policy decision (denied)
     */
    public static PolicyDecision createTestPolicyDecisionDenied() {
        return PolicyDecision.deny("Test policy decision");
    }

    /**
     * Create test policy decision with custom reason
     */
    public static PolicyDecision createTestPolicyDecision(boolean allowed, String reason) {
        return allowed ? PolicyDecision.allow(reason) : PolicyDecision.deny(reason);
    }

    /**
     * Create test create policy request
     */
    public static PolicyService.CreatePolicyRequest createTestCreatePolicyRequest() {
        return PolicyService.CreatePolicyRequest.builder()
            .name("TEST_CREATE_POLICY")
            .description("Test create policy request")
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
            .validFrom(LocalDateTime.now().minusDays(1))
            .validUntil(LocalDateTime.now().plusDays(1))
            .build();
    }

    /**
     * Create test update policy request
     */
    public static PolicyService.UpdatePolicyRequest createTestUpdatePolicyRequest() {
        return PolicyService.UpdatePolicyRequest.builder()
            .name("TEST_UPDATE_POLICY")
            .description("Test update policy request")
            .type(PolicyRegistry.PolicyType.ACCESS)
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .isActive(true)
            .priority(75)
            .conditions(Map.of(
                "user_id", "{{userId}}",
                "tenant_id", "{{tenantId}}"
            ))
            .rules(Map.of(
                "allow", true
            ))
            .validFrom(LocalDateTime.now().minusDays(1))
            .validUntil(LocalDateTime.now().plusDays(1))
            .build();
    }

    /**
     * Create test evaluate policy request
     */
    public static PolicyController.EvaluatePolicyRequest createTestEvaluatePolicyRequest() {
        return PolicyController.EvaluatePolicyRequest.builder()
            .policyName("TEST_POLICY")
            .context(createTestPolicyContext())
            .build();
    }

    /**
     * Create test check permission request
     */
    public static PolicyController.CheckPermissionRequest createTestCheckPermissionRequest() {
        return PolicyController.CheckPermissionRequest.builder()
            .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .permission("READ")
            .resourceType("USER")
            .resourceId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .build();
    }

    /**
     * Create test check role request
     */
    public static PolicyController.CheckRoleRequest createTestCheckRoleRequest() {
        return PolicyController.CheckRoleRequest.builder()
            .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .roleName("USER")
            .build();
    }

    /**
     * Create test UUID
     */
    public static UUID createTestUUID() {
        return UUID.fromString("11111111-1111-1111-1111-111111111111");
    }

    /**
     * Create test tenant UUID
     */
    public static UUID createTestTenantUUID() {
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    /**
     * Create test resource UUID
     */
    public static UUID createTestResourceUUID() {
        return UUID.fromString("22222222-2222-2222-2222-222222222222");
    }
}

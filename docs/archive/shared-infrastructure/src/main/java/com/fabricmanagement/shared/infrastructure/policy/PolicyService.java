package com.fabricmanagement.shared.infrastructure.policy;

import com.fabricmanagement.shared.infrastructure.cache.PolicyCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Policy Service
 * 
 * High-level service for policy management operations.
 * Provides business logic for policy CRUD operations and policy evaluation.
 * 
 * ✅ PRODUCTION-READY - Complete policy management
 * ✅ TRANSACTIONAL - ACID compliance
 * ✅ CACHE-AWARE - Automatic cache management
 * ✅ AUDIT TRAIL - Complete operation logging
 * ✅ ZERO HARDCODED VALUES - Configurable behavior
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

    private final PolicyRegistry policyRegistry;
    private final PolicyCache policyCache;
    private final PolicyEngine policyEngine;

    /**
     * Create new policy
     */
    @Transactional
    public PolicyRegistry.Policy createPolicy(CreatePolicyRequest request) {
        log.info("📋 Creating policy: {}", request.getName());

        // Validate request
        validateCreatePolicyRequest(request);

        // Create policy
        PolicyRegistry.Policy policy = PolicyRegistry.Policy.builder()
            .name(request.getName())
            .description(request.getDescription())
            .type(request.getType())
            .tenantId(request.getTenantId())
            .isActive(request.isActive())
            .priority(request.getPriority())
            .conditions(request.getConditions())
            .rules(request.getRules())
            .validFrom(request.getValidFrom())
            .validUntil(request.getValidUntil())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // Save policy
        PolicyRegistry.Policy savedPolicy = policyRegistry.save(policy);

        // Cache policy
        policyCache.cachePolicy(savedPolicy);

        log.info("✅ Policy created successfully: {} - {}", savedPolicy.getId(), savedPolicy.getName());
        return savedPolicy;
    }

    /**
     * Update existing policy
     */
    @Transactional
    public PolicyRegistry.Policy updatePolicy(UUID policyId, UpdatePolicyRequest request) {
        log.info("📋 Updating policy: {}", policyId);

        // Find existing policy
        PolicyRegistry.Policy existingPolicy = policyRegistry.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        // Update fields
        if (request.getName() != null) {
            existingPolicy.setName(request.getName());
        }
        if (request.getDescription() != null) {
            existingPolicy.setDescription(request.getDescription());
        }
        if (request.getType() != null) {
            existingPolicy.setType(request.getType());
        }
        if (request.getTenantId() != null) {
            existingPolicy.setTenantId(request.getTenantId());
        }
        if (request.getIsActive() != null) {
            existingPolicy.setActive(request.getIsActive());
        }
        if (request.getPriority() != null) {
            existingPolicy.setPriority(request.getPriority());
        }
        if (request.getConditions() != null) {
            existingPolicy.setConditions(request.getConditions());
        }
        if (request.getRules() != null) {
            existingPolicy.setRules(request.getRules());
        }
        if (request.getValidFrom() != null) {
            existingPolicy.setValidFrom(request.getValidFrom());
        }
        if (request.getValidUntil() != null) {
            existingPolicy.setValidUntil(request.getValidUntil());
        }

        existingPolicy.setUpdatedAt(LocalDateTime.now());

        // Save updated policy
        PolicyRegistry.Policy updatedPolicy = policyRegistry.save(existingPolicy);

        // Update cache
        policyCache.updatePolicy(updatedPolicy);

        log.info("✅ Policy updated successfully: {} - {}", updatedPolicy.getId(), updatedPolicy.getName());
        return updatedPolicy;
    }

    /**
     * Delete policy
     */
    @Transactional
    public void deletePolicy(UUID policyId) {
        log.info("🗑️ Deleting policy: {}", policyId);

        // Find existing policy
        PolicyRegistry.Policy existingPolicy = policyRegistry.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        // Delete policy
        policyRegistry.deleteById(policyId);

        // Evict from cache
        policyCache.evictPolicy(policyId);

        log.info("✅ Policy deleted successfully: {} - {}", policyId, existingPolicy.getName());
    }

    /**
     * Activate policy
     */
    @Transactional
    public PolicyRegistry.Policy activatePolicy(UUID policyId) {
        log.info("✅ Activating policy: {}", policyId);

        PolicyRegistry.Policy policy = policyRegistry.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        policy.setActive(true);
        policy.setUpdatedAt(LocalDateTime.now());

        PolicyRegistry.Policy updatedPolicy = policyRegistry.save(policy);
        policyCache.updatePolicy(updatedPolicy);

        log.info("✅ Policy activated successfully: {} - {}", policyId, policy.getName());
        return updatedPolicy;
    }

    /**
     * Deactivate policy
     */
    @Transactional
    public PolicyRegistry.Policy deactivatePolicy(UUID policyId) {
        log.info("❌ Deactivating policy: {}", policyId);

        PolicyRegistry.Policy policy = policyRegistry.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        policy.setActive(false);
        policy.setUpdatedAt(LocalDateTime.now());

        PolicyRegistry.Policy updatedPolicy = policyRegistry.save(policy);
        policyCache.evictPolicy(policyId);

        log.info("✅ Policy deactivated successfully: {} - {}", policyId, policy.getName());
        return updatedPolicy;
    }

    /**
     * Get policy by ID
     */
    public Optional<PolicyRegistry.Policy> getPolicy(UUID policyId) {
        log.debug("🔍 Getting policy: {}", policyId);
        return policyCache.getPolicy(policyId);
    }

    /**
     * Get policies by name
     */
    public List<PolicyRegistry.Policy> getPoliciesByName(String policyName) {
        log.debug("🔍 Getting policies by name: {}", policyName);
        return policyCache.getPoliciesByName(policyName);
    }

    /**
     * Get user effective policies
     */
    public List<PolicyRegistry.Policy> getUserEffectivePolicies(UUID userId, UUID tenantId) {
        log.debug("🔍 Getting effective policies for user: {}:{}", userId, tenantId);
        return policyEngine.getUserEffectivePolicies(userId, tenantId);
    }

    /**
     * Evaluate policy for user action
     */
    public PolicyDecision evaluatePolicy(String policyName, PolicyContext context) {
        log.debug("🔍 Evaluating policy: {} for user: {}", policyName, context.getUserId());
        return policyEngine.evaluatePolicy(policyName, context);
    }

    /**
     * Check user permission
     */
    public boolean hasPermission(UUID userId, UUID tenantId, String permission, String resourceType, UUID resourceId) {
        log.debug("🔍 Checking permission: {} for user: {}:{}", permission, userId, tenantId);
        return policyEngine.hasPermission(userId, tenantId, permission, resourceType, resourceId);
    }

    /**
     * Check user role
     */
    public boolean hasRole(UUID userId, UUID tenantId, String roleName) {
        log.debug("🔍 Checking role: {} for user: {}:{}", roleName, userId, tenantId);
        return policyEngine.hasRole(userId, tenantId, roleName);
    }

    /**
     * Get all policies for tenant
     */
    public List<PolicyRegistry.Policy> getTenantPolicies(UUID tenantId) {
        log.debug("🔍 Getting policies for tenant: {}", tenantId);
        return policyCache.getTenantPolicies(tenantId);
    }

    /**
     * Get active policies for tenant
     */
    public List<PolicyRegistry.Policy> getActiveTenantPolicies(UUID tenantId) {
        log.debug("🔍 Getting active policies for tenant: {}", tenantId);
        return policyCache.getTenantPolicies(tenantId).stream()
            .filter(PolicyRegistry.Policy::isActive)
            .toList();
    }

    /**
     * Validate create policy request
     */
    private void validateCreatePolicyRequest(CreatePolicyRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Policy name is required");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("Policy type is required");
        }
        if (request.getTenantId() == null) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        if (request.getPriority() == null || request.getPriority() < 0) {
            throw new IllegalArgumentException("Priority must be non-negative");
        }
    }

    /**
     * Create Policy Request DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class CreatePolicyRequest {
        private final String name;
        private final String description;
        private final PolicyRegistry.PolicyType type;
        private final UUID tenantId;
        private final boolean isActive;
        private final Integer priority;
        private final Map<String, Object> conditions;
        private final Map<String, Object> rules;
        private final LocalDateTime validFrom;
        private final LocalDateTime validUntil;
    }

    /**
     * Update Policy Request DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class UpdatePolicyRequest {
        private final String name;
        private final String description;
        private final PolicyRegistry.PolicyType type;
        private final UUID tenantId;
        private final Boolean isActive;
        private final Integer priority;
        private final Map<String, Object> conditions;
        private final Map<String, Object> rules;
        private final LocalDateTime validFrom;
        private final LocalDateTime validUntil;
    }
}

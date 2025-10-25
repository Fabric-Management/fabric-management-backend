package com.fabricmanagement.common.platform.policy.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.policy.domain.Policy;
import com.fabricmanagement.common.platform.policy.domain.event.PolicyEvaluatedEvent;
import com.fabricmanagement.common.platform.policy.domain.value.PolicyDecision;
import com.fabricmanagement.common.platform.policy.infra.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Policy Service - Layer 4 Policy Evaluation Engine.
 *
 * <p><b>Amazon IAM-style policy evaluation:</b></p>
 * <ol>
 *   <li>Default DENY (whitelist approach)</li>
 *   <li>Explicit ALLOW required</li>
 *   <li>DENY overrides ALLOW (if both exist, DENY wins)</li>
 *   <li>Priority-based evaluation (higher priority first)</li>
 * </ol>
 *
 * <h2>Evaluation Flow:</h2>
 * <pre>
 * 1. Find all policies for resource + action
 * 2. Evaluate in priority order (high → low)
 * 3. If ANY DENY matches → DENY (immediate return)
 * 4. If ANY ALLOW matches → ALLOW
 * 5. If NO matches → DENY (default)
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * Evaluate policy for given context.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @param resource the resource being accessed
     * @param action the action being performed
     * @param context additional context (roles, department, etc.)
     * @return policy decision
     */
    @Cacheable(value = "policy-decision", key = "#tenantId + ':' + #userId + ':' + #resource + ':' + #action")
    @Transactional(readOnly = true)
    public PolicyDecision evaluate(UUID tenantId, UUID userId, String resource, 
                                   String action, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();
        
        log.debug("[Policy Layer 4] Evaluating: resource={}, action={}, userId={}", 
            resource, action, userId);

        List<Policy> policies = policyRepository.findApplicablePolicies(resource, action);

        if (policies.isEmpty()) {
            log.debug("[Policy Layer 4] No policies found - DEFAULT DENY");
            return logAndPublishDecision(tenantId, userId, resource, action, 
                PolicyDecision.deny("No applicable policies found - default deny"), 
                startTime);
        }

        // Amazon IAM style: Check DENY first
        for (Policy policy : policies) {
            if (policy.isDeny() && matchesConditions(policy, context)) {
                log.warn("[Policy Layer 4] DENY match: policyId={}", policy.getPolicyId());
                return logAndPublishDecision(tenantId, userId, resource, action,
                    PolicyDecision.deny("Explicit DENY policy matched: " + policy.getPolicyId(), 
                        policy.getPolicyId(), List.of()), 
                    startTime);
            }
        }

        // Check ALLOW
        for (Policy policy : policies) {
            if (policy.isAllow() && matchesConditions(policy, context)) {
                log.info("[Policy Layer 4] ALLOW match: policyId={}", policy.getPolicyId());
                return logAndPublishDecision(tenantId, userId, resource, action,
                    PolicyDecision.allow("Policy matched: " + policy.getPolicyId(), 
                        policy.getPolicyId()),
                    startTime);
            }
        }

        // No ALLOW found - default DENY
        log.debug("[Policy Layer 4] No ALLOW match - DEFAULT DENY");
        return logAndPublishDecision(tenantId, userId, resource, action,
            PolicyDecision.deny("No matching ALLOW policy - default deny"),
            startTime);
    }

    /**
     * Check if policy conditions match the given context.
     *
     * @param policy the policy to evaluate
     * @param context the evaluation context
     * @return true if all conditions match
     */
    private boolean matchesConditions(Policy policy, Map<String, Object> context) {
        Map<String, Object> conditions = policy.getConditions();
        
        if (conditions == null || conditions.isEmpty()) {
            return true; // No conditions = always match
        }

        // Check roles
        if (conditions.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> requiredRoles = (List<String>) conditions.get("roles");
            @SuppressWarnings("unchecked")
            List<String> userRoles = (List<String>) context.getOrDefault("roles", List.of());
            
            boolean hasRole = requiredRoles.stream().anyMatch(userRoles::contains);
            if (!hasRole) {
                log.debug("Role check failed: required={}, user={}", requiredRoles, userRoles);
                return false;
            }
        }

        // Check departments
        if (conditions.containsKey("departments")) {
            @SuppressWarnings("unchecked")
            List<String> requiredDepartments = (List<String>) conditions.get("departments");
            String userDepartment = (String) context.get("department");
            
            if (userDepartment == null || !requiredDepartments.contains(userDepartment)) {
                log.debug("Department check failed: required={}, user={}", requiredDepartments, userDepartment);
                return false;
            }
        }

        // Check time range (if specified)
        if (conditions.containsKey("timeRange")) {
            // TODO: Implement time range check
            // For now, always pass
        }

        // All conditions passed
        return true;
    }

    /**
     * Log and publish policy evaluation event.
     */
    private PolicyDecision logAndPublishDecision(UUID tenantId, UUID userId, String resource, 
                                                 String action, PolicyDecision decision, 
                                                 long startTime) {
        long evaluationTime = System.currentTimeMillis() - startTime;
        
        PolicyDecision enrichedDecision = PolicyDecision.builder()
            .allowed(decision.isAllowed())
            .reason(decision.getReason())
            .policyId(decision.getPolicyId())
            .failedConditions(decision.getFailedConditions())
            .evaluatedAt(decision.getEvaluatedAt())
            .evaluationTimeMs(evaluationTime)
            .build();

        eventPublisher.publish(new PolicyEvaluatedEvent(
            tenantId,
            userId,
            resource,
            action,
            decision.isAllowed(),
            decision.getReason(),
            evaluationTime
        ));

        log.info("[Policy Layer 4] Decision: {} in {}ms - {}", 
            decision.isAllowed() ? "ALLOW" : "DENY", evaluationTime, decision.getReason());

        return enrichedDecision;
    }

    /**
     * Create a new policy.
     */
    @Transactional
    public Policy createPolicy(Policy policy) {
        log.info("Creating policy: policyId={}", policy.getPolicyId());

        if (policyRepository.existsByPolicyId(policy.getPolicyId())) {
            throw new IllegalArgumentException("Policy already exists: " + policy.getPolicyId());
        }

        return policyRepository.save(policy);
    }

    /**
     * Get all enabled policies.
     */
    @Transactional(readOnly = true)
    public List<Policy> getAllPolicies() {
        return policyRepository.findByEnabledTrueOrderByPriorityDesc();
    }
}


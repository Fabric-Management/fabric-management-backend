package com.fabricmanagement.shared.infrastructure.policy.engine;

import com.fabricmanagement.shared.domain.policy.PolicyContext;
import com.fabricmanagement.shared.domain.policy.PolicyDecision;
import com.fabricmanagement.shared.domain.policy.PolicyRegistry;
import com.fabricmanagement.shared.infrastructure.constants.SecurityRoles;
import com.fabricmanagement.shared.infrastructure.policy.constants.PolicyConstants;
import com.fabricmanagement.shared.infrastructure.policy.guard.CompanyTypeGuard;
import com.fabricmanagement.shared.infrastructure.policy.guard.PlatformPolicyGuard;
import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyRegistryRepository;
import com.fabricmanagement.shared.infrastructure.policy.resolver.ScopeResolver;
import com.fabricmanagement.shared.infrastructure.policy.resolver.UserGrantResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Policy Engine (PDP - Policy Decision Point)
 * 
 * Core authorization decision engine.
 * Makes ALLOW/DENY decisions based on multiple policy layers.
 * 
 * Decision Flow (First DENY wins):
 * 1. Company Type Guardrails → DENY if violated
 * 2. Platform Policy → DENY if violated
 * 3. User Grants (DENY) → DENY if explicit deny exists
 * 4. Role Default → Check if role has default access
 * 5. User Grants (ALLOW) → Check if explicit allow exists
 * 6. Data Scope → DENY if scope invalid
 * 7. → ALLOW (all checks passed)
 * 
 * Design Principles:
 * - Stateless (no instance state except injected dependencies)
 * - Thread-safe by design
 * - Fail-safe (deny by default)
 * - First DENY wins (security first)
 * - Explainable decisions (reason field)
 * - Fast evaluation (< 50ms target)
 * 
 * Performance:
 * - Evaluation should complete in < 50ms (p95)
 * - Use cache for repeated decisions (PolicyCache)
 * - Async audit logging (non-blocking)
 * 
 * Usage:
 * <pre>
 * PolicyContext context = PolicyContext.builder()
 *     .userId(userId)
 *     .companyId(companyId)
 *     .companyType(CompanyType.INTERNAL)
 *     .endpoint("/api/users/{id}")
 *     .operation(OperationType.WRITE)
 *     .scope(DataScope.COMPANY)
 *     .roles(List.of("ADMIN"))
 *     .correlationId(correlationId)
 *     .build();
 * 
 * PolicyDecision decision = policyEngine.evaluate(context);
 * if (!decision.isAllowed()) {
 *     throw new ForbiddenException(decision.getReason());
 * }
 * </pre>
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Slf4j
@Component
public class PolicyEngine {
    
    private static final String POLICY_VERSION = PolicyConstants.POLICY_VERSION_DEFAULT;
    
    // Required dependencies (always available)
    private final CompanyTypeGuard companyTypeGuard;
    private final ScopeResolver scopeResolver;
    
    // Optional dependencies (may not be available in reactive contexts like Gateway)
    private final PlatformPolicyGuard platformPolicyGuard;
    private final UserGrantResolver userGrantResolver;
    private final PolicyRegistryRepository policyRegistryRepository;
    
    /**
     * Constructor with optional dependencies
     * 
     * @param companyTypeGuard required
     * @param scopeResolver required
     * @param platformPolicyGuard optional (null in Gateway)
     * @param userGrantResolver optional (null in Gateway)
     * @param policyRegistryRepository optional (null in Gateway)
     */
    public PolicyEngine(CompanyTypeGuard companyTypeGuard,
                       ScopeResolver scopeResolver,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) 
                       PlatformPolicyGuard platformPolicyGuard,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) 
                       UserGrantResolver userGrantResolver,
                       @org.springframework.beans.factory.annotation.Autowired(required = false)
                       PolicyRegistryRepository policyRegistryRepository) {
        this.companyTypeGuard = companyTypeGuard;
        this.scopeResolver = scopeResolver;
        this.platformPolicyGuard = platformPolicyGuard;
        this.userGrantResolver = userGrantResolver;
        this.policyRegistryRepository = policyRegistryRepository;
    }
    
    /**
     * Evaluate authorization request
     * 
     * @param context policy context
     * @return policy decision (ALLOW or DENY)
     */
    public PolicyDecision evaluate(PolicyContext context) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Evaluating policy for user: {}, endpoint: {}, operation: {}",
                context.getUserId(), context.getEndpoint(), context.getOperation());
            
            // Step 1: Company Type Guardrails (cannot be overridden)
            String guardrailDenial = companyTypeGuard.checkGuardrails(context);
            if (guardrailDenial != null) {
                log.info("Policy DENIED by company type guardrail: {}", guardrailDenial);
                return createDenyDecision(guardrailDenial, context, startTime);
            }
            
            // Step 2: Platform Policy (optional - skip if not available)
            if (platformPolicyGuard != null) {
                String platformDenial = platformPolicyGuard.checkPlatformPolicy(context);
                if (platformDenial != null) {
                    log.info("Policy DENIED by platform policy: {}", platformDenial);
                    return createDenyDecision(platformDenial, context, startTime);
                }
            }
            
            // Step 3: User-specific DENY grants (optional - skip if not available)
            if (userGrantResolver != null) {
                String userDeny = userGrantResolver.checkUserDeny(context);
                if (userDeny != null) {
                    log.info("Policy DENIED by user explicit deny: {}", userDeny);
                    return createDenyDecision(userDeny, context, startTime);
                }
            }
            
            // Step 4: Role Default Access
            boolean roleAllowed = checkRoleDefaultAccess(context);
            if (!roleAllowed) {
                // Check if user has explicit ALLOW grant (optional - skip if not available)
                boolean hasUserAllow = false;
                if (userGrantResolver != null) {
                    hasUserAllow = userGrantResolver.hasUserAllow(context);
                }
                
                if (hasUserAllow) {
                    log.info("Policy ALLOWED by user explicit allow grant (overrides role restriction)");
                } else {
                    log.info("Policy DENIED - no role default access for user: {}, roles: {}",
                        context.getUserId(), context.getRoles());
                    return createDenyDecision(PolicyConstants.REASON_ROLE, context, startTime);
                }
            }
            
            // Step 5: User-specific ALLOW grants (already checked above if role denied)
            
            // Step 6: Data Scope Validation
            String scopeDenial = scopeResolver.validateScope(context);
            if (scopeDenial != null) {
                log.info("Policy DENIED by scope validation: {}", scopeDenial);
                return createDenyDecision(scopeDenial, context, startTime);
            }
            
            // All checks passed → ALLOW
            log.info("Policy ALLOWED for user: {}, endpoint: {}, operation: {}",
                context.getUserId(), context.getEndpoint(), context.getOperation());
            
            return createAllowDecision("role_default_allowed", context, startTime);
            
        } catch (Exception e) {
            log.error("Error evaluating policy for user: {}, endpoint: {}. Denying by default.",
                context.getUserId(), context.getEndpoint(), e);
            
            // Fail-safe: deny on error
            return createDenyDecision(PolicyConstants.REASON_ERROR, context, startTime);
        }
    }
    
    /**
     * Check if user's role has default access
     * 
     * Checks PolicyRegistry for endpoint-specific role requirements.
     * Falls back to simplified role-based access if registry unavailable.
     * 
     * @param context policy context
     * @return true if role has default access
     */
    private boolean checkRoleDefaultAccess(PolicyContext context) {
        if (context.getRoles() == null || context.getRoles().isEmpty()) {
            return false;
        }
        
        // Step 1: Try PolicyRegistry lookup (if available)
        if (policyRegistryRepository != null && context.getEndpoint() != null && context.getOperation() != null) {
            try {
                Optional<PolicyRegistry> policyOpt = policyRegistryRepository
                    .findByEndpointAndOperationAndActiveTrue(context.getEndpoint(), context.getOperation());
                
                if (policyOpt.isPresent()) {
                    PolicyRegistry policy = policyOpt.get();
                    
                    // Check if user has any of the default roles defined in policy
                    if (policy.getDefaultRoles() != null && !policy.getDefaultRoles().isEmpty()) {
                        boolean hasDefaultRole = context.getRoles().stream()
                            .anyMatch(policy::hasRoleAccess);
                        
                        if (hasDefaultRole) {
                            log.debug("User has default role access from PolicyRegistry for endpoint: {}", 
                                context.getEndpoint());
                            return true;
                        } else {
                            log.debug("User lacks required roles from PolicyRegistry. Required: {}, User has: {}",
                                policy.getDefaultRoles(), context.getRoles());
                            return false;
                        }
                    }
                    
                    // Policy exists but no specific role requirements - allow
                    log.debug("Policy exists with no role restrictions for endpoint: {}", context.getEndpoint());
                    return true;
                }
                
                // No specific policy found - fall through to default logic
                log.debug("No PolicyRegistry entry found for endpoint: {}, operation: {}. Using default logic.",
                    context.getEndpoint(), context.getOperation());
                
            } catch (Exception e) {
                log.error("Error checking PolicyRegistry for endpoint: {}, operation: {}. Falling back to default logic.",
                    context.getEndpoint(), context.getOperation(), e);
                // Fall through to default logic on error
            }
        } else {
            log.debug("PolicyRegistry not available. Using simplified role-based access.");
        }
        
        // Step 2: Fallback - Simplified role-based access (original logic)
        return checkFallbackRoleAccess(context);
    }
    
    /**
     * Fallback role-based access check (when PolicyRegistry unavailable)
     * 
     * @param context policy context
     * @return true if role has default access
     */
    private boolean checkFallbackRoleAccess(PolicyContext context) {
        // ADMIN and SUPER_ADMIN have full access
        if (context.hasAnyRole(SecurityRoles.ADMIN, SecurityRoles.SUPER_ADMIN, SecurityRoles.SYSTEM_ADMIN)) {
            return true;
        }
        
        // MANAGER has most access
        if (context.hasAnyRole(SecurityRoles.MANAGER)) {
            return true;
        }
        
        // USER has limited access
        if (context.hasAnyRole(SecurityRoles.USER)) {
            // Users can READ, but need explicit grants for WRITE/DELETE
            return context.getOperation().isReadOnly();
        }
        
        // No recognized role
        return false;
    }
    
    // =========================================================================
    // DECISION FACTORY METHODS
    // =========================================================================
    
    /**
     * Create ALLOW decision with metrics
     * 
     * @param reason allow reason
     * @param context policy context
     * @param startTime evaluation start time
     * @return allow decision
     */
    private PolicyDecision createAllowDecision(String reason, PolicyContext context, long startTime) {
        long latency = System.currentTimeMillis() - startTime;
        
        log.debug("Policy evaluation completed in {}ms - ALLOW", latency);
        
        return PolicyDecision.allow(
            reason,
            POLICY_VERSION,
            context.getCorrelationId()
        );
    }
    
    /**
     * Create DENY decision with metrics
     * 
     * @param reason deny reason
     * @param context policy context
     * @param startTime evaluation start time
     * @return deny decision
     */
    private PolicyDecision createDenyDecision(String reason, PolicyContext context, long startTime) {
        long latency = System.currentTimeMillis() - startTime;
        
        log.debug("Policy evaluation completed in {}ms - DENY: {}", latency, reason);
        
        return PolicyDecision.deny(
            reason,
            POLICY_VERSION,
            context.getCorrelationId()
        );
    }
    
    // =========================================================================
    // HELPER METHODS (Public for testing)
    // =========================================================================
    
    /**
     * Quick check if operation is allowed for company type
     * (Simplified check without full context)
     * 
     * @param context policy context
     * @return true if likely allowed
     */
    public boolean quickCheck(PolicyContext context) {
        // Quick guardrail check
        String guardrailDenial = companyTypeGuard.checkGuardrails(context);
        if (guardrailDenial != null) {
            return false;
        }
        
        // Quick role check
        return checkRoleDefaultAccess(context);
    }
}


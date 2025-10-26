package com.fabricmanagement.common.platform.policy.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.app.EnhancedSubscriptionService;
import com.fabricmanagement.common.platform.policy.domain.PolicyCheck;
import com.fabricmanagement.common.platform.policy.domain.value.PolicyDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AOP Aspect for @PolicyCheck annotation.
 *
 * <p>Intercepts methods annotated with @PolicyCheck and enforces 4-layer access control:</p>
 * <ol>
 *   <li>Layer 1-3: Subscription check (OS → Feature → Quota)</li>
 *   <li>Layer 4: Policy check (RBAC/ABAC)</li>
 * </ol>
 *
 * <h2>Usage:</h2>
 * <p>Just annotate controller methods:</p>
 * <pre>{@code
 * @PostMapping("/materials")
 * @PolicyCheck(resource="fabric.material", action="create", featureId="production.material.create")
 * public ResponseEntity<?> createMaterial(...) {
 *     // Automatically protected!
 * }
 * }</pre>
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyCheckAspect {

    private final PolicyService policyService;
    private final EnhancedSubscriptionService subscriptionService;

    @Around("@annotation(policyCheck)")
    public Object checkPolicy(ProceedingJoinPoint joinPoint, PolicyCheck policyCheck) throws Throwable {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = TenantContext.getCurrentUserId();

        String resource = policyCheck.resource();
        String action = policyCheck.action();
        String featureId = policyCheck.featureId();
        String quotaType = policyCheck.quotaType();

        log.debug("[PolicyCheckAspect] Checking access: resource={}, action={}, feature={}", 
            resource, action, featureId);

        try {
            // Layer 1-3: Subscription check (if featureId specified)
            if (featureId != null && !featureId.isBlank()) {
                log.debug("[Layer 1-3] Subscription check: featureId={}", featureId);
                subscriptionService.enforceEntitlement(tenantId, featureId, quotaType);
                log.debug("[Layer 1-3] PASS");
            }

            // Layer 4: Policy check
            log.debug("[Layer 4] Policy check: resource={}, action={}", resource, action);
            
            Map<String, Object> context = buildEvaluationContext();
            PolicyDecision decision = policyService.evaluate(tenantId, userId, resource, action, context);

            if (!decision.isAllowed()) {
                log.warn("[Layer 4] Access DENIED: reason={}", decision.getReason());
                throw new AccessDeniedException(decision.getReason());
            }

            log.debug("[Layer 4] PASS");
            log.info("[PolicyCheckAspect] Access GRANTED - All layers passed");

            // Proceed with method execution
            return joinPoint.proceed();

        } catch (AccessDeniedException e) {
            log.warn("[PolicyCheckAspect] Access denied: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[PolicyCheckAspect] Unexpected error during policy check", e);
            throw new AccessDeniedException("Policy evaluation failed: " + e.getMessage());
        }
    }

    /**
     * Build evaluation context from current security context.
     */
    private Map<String, Object> buildEvaluationContext() {
        Map<String, Object> context = new HashMap<>();

        // Get current user from security context
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            // Extract roles
            List<String> roles = authentication.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .toList();
            context.put("roles", roles);

            // Extract department from authentication (set in JWT claims)
            if (authentication.getPrincipal() instanceof String) {
                context.put("department", null);
            }
        }

        return context;
    }
}


package com.fabricmanagement.shared.security.filter;

import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.constants.SecurityConstants;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.shared.infrastructure.exception.ForbiddenException;
import com.fabricmanagement.shared.infrastructure.policy.engine.PolicyEngine;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Policy Validation Filter (Defense-in-Depth) - SHARED
 * 
 * Secondary policy enforcement at service level.
 * Used by ALL microservices (user, company, contact).
 * 
 * Architecture:
 * - Primary enforcement: API Gateway (PolicyEnforcementFilter - reactive)
 * - Secondary enforcement: This filter (defense-in-depth - servlet)
 * 
 * Why Secondary Check?
 * - Defense-in-depth principle
 * - Protection against gateway bypass
 * - Service-level policy enforcement
 * - Fine-grained authorization
 * 
 * When this runs:
 * - After JwtAuthenticationFilter (Order 1)
 * - Before Controller methods
 * 
 * Performance:
 * - Uses PolicyCache (if configured)
 * - Adds ~5-10ms latency
 * - Can be disabled via config
 * 
 * Configuration:
 * - Enable/disable: security.policy.validation.enabled=true
 * - Public paths: Override in service-specific config if needed
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization - Phase 3)
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "security.policy.validation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true  // Enabled by default
)
public class PolicyValidationFilter implements Filter {
    
    private final PolicyEngine policyEngine;
    
    /**
     * Public paths that skip policy validation
     * Override in service-specific config if needed
     */
    private static final List<String> PUBLIC_PATHS = List.of(
        "/actuator",
        "/api/public",
        "/api/v1/public",
        "/health",
        "/metrics"
    );
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();
        
        // Skip public paths
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        
        // Get authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("No authentication for path: {}, skipping policy validation", path);
            chain.doFilter(request, response);
            return;
        }
        
        // Get SecurityContext
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof SecurityContext)) {
            log.debug("Principal not SecurityContext, skipping policy validation: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        SecurityContext securityContext = (SecurityContext) principal;

        // Skip for internal service calls
        if (SecurityConstants.INTERNAL_SERVICE_PRINCIPAL.equals(securityContext.getUserId())) {
            log.debug("Internal service call, skipping policy validation: {}", path);
            chain.doFilter(request, response);
            return;
        }

        try {
            // Build policy context
            PolicyContext policyContext = buildPolicyContext(httpRequest, securityContext);
            
            // Evaluate policy (secondary check)
            PolicyDecision decision = policyEngine.evaluate(policyContext);
            
            if (decision.isDenied()) {
                log.warn("Policy DENIED (secondary) - User: {}, Path: {}, Reason: {}",
                    securityContext.getUserId(), path, decision.getReason());
                throw new ForbiddenException(
                    String.format("Access denied: %s", decision.getReason())
                );
            }
            
            log.debug("Policy ALLOWED (secondary) - User: {}, Path: {}", 
                securityContext.getUserId(), path);
            
            chain.doFilter(request, response);
            
        } catch (ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Policy validation error for path: {}. Denying by default.", path, e);
            throw new ForbiddenException(ServiceConstants.MSG_POLICY_VALIDATION_FAILED);
        }
    }
    
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
    
    private PolicyContext buildPolicyContext(HttpServletRequest request, SecurityContext securityContext) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        
        UUID userId = securityContext.getUserId() != null ? 
            UUID.fromString(securityContext.getUserId()) : null;
        
        // ✅ Production fix: CompanyType fallback
        // If companyType is NULL in JWT (legacy tokens), default to INTERNAL
        // This allows TENANT_ADMIN to perform operations during migration
        CompanyType companyType = securityContext.getCompanyType() != null ?
            securityContext.getCompanyType() : CompanyType.INTERNAL;
        
        return PolicyContext.builder()
            .userId(userId)
            .companyId(securityContext.getTenantId())
            .companyType(companyType)
            .endpoint(path)
            .httpMethod(method)
            .operation(mapOperation(method))
            .scope(inferScope(path))
            .roles(extractRoles(securityContext))
            .correlationId(request.getHeader("X-Correlation-ID"))
            .requestId(request.getHeader("X-Request-ID"))
            .requestIp(request.getRemoteAddr())
            .build();
    }
    
    private OperationType mapOperation(String method) {
        return switch (method.toUpperCase()) {
            case "GET", "HEAD" -> OperationType.READ;
            case "POST", "PUT", "PATCH" -> OperationType.WRITE;
            case "DELETE" -> OperationType.DELETE;
            default -> OperationType.READ;
        };
    }
    
    private DataScope inferScope(String path) {
        if (path.contains("/me") || path.contains("/profile")) {
            return DataScope.SELF;
        }
        if (path.contains("/admin") || path.contains("/system")) {
            return DataScope.GLOBAL;
        }
        return DataScope.COMPANY;
    }
    
    private List<String> extractRoles(SecurityContext securityContext) {
        if (securityContext.getRoles() == null || securityContext.getRoles().length == 0) {
            return List.of();
        }
        
        return Arrays.stream(securityContext.getRoles())
            .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
            .collect(Collectors.toList());
    }
}


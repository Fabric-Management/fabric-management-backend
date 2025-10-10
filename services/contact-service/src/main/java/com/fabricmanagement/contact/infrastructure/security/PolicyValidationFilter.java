package com.fabricmanagement.contact.infrastructure.security;

import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.exception.ForbiddenException;
import com.fabricmanagement.shared.infrastructure.policy.engine.PolicyEngine;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Policy Validation Filter (Defense-in-Depth)
 * 
 * Secondary policy enforcement at Contact Service level.
 * Provides additional security layer beyond API Gateway.
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization - Phase 3)
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class PolicyValidationFilter implements Filter {
    
    private final PolicyEngine policyEngine;
    
    private static final List<String> PUBLIC_PATHS = List.of(
        "/actuator",
        "/api/public"
    );
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();
        
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("No authentication found for path: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof SecurityContext)) {
            log.debug("Principal is not SecurityContext, skipping policy validation: {}", path);
            chain.doFilter(request, response);
            return;
        }
        
        SecurityContext securityContext = (SecurityContext) principal;
        
        try {
            PolicyContext policyContext = buildPolicyContext(httpRequest, securityContext);
            PolicyDecision decision = policyEngine.evaluate(policyContext);
            
            if (decision.isDenied()) {
                log.warn("Policy DENIED (secondary check) - User: {}, Path: {}, Reason: {}",
                    securityContext.getUserId(), path, decision.getReason());
                throw new ForbiddenException(
                    String.format("Access denied: %s", decision.getReason())
                );
            }
            
            log.debug("Policy ALLOWED (secondary check) - User: {}, Path: {}", 
                securityContext.getUserId(), path);
            
            chain.doFilter(request, response);
            
        } catch (ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Policy validation error for path: {}. Denying by default.", path, e);
            throw new ForbiddenException("Policy validation failed");
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
        
        return PolicyContext.builder()
            .userId(userId)
            .companyId(securityContext.getTenantId())
            .companyType(securityContext.getCompanyType())
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


package com.fabricmanagement.gateway.filter;

import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.policy.engine.PolicyEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Policy Enforcement Filter (PEP - Policy Enforcement Point)
 * 
 * Gateway-level authorization filter.
 * Calls PolicyEngine (PDP) to make authorization decisions.
 * 
 * Flow:
 * 1. Extract context from request (JWT headers)
 * 2. Build PolicyContext
 * 3. Call PolicyEngine.evaluate()
 * 4. If DENY → Return 403 Forbidden
 * 5. If ALLOW → Add decision headers and proceed
 * 
 * Order: -50 (after JWT filter -100, before logging 0)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyEnforcementFilter implements GlobalFilter, Ordered {
    
    private final PolicyEngine policyEngine;
    
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_COMPANY_ID = "X-Company-Id";
    
    // Policy decision headers (added to downstream)
    private static final String HEADER_POLICY_DECISION = "X-Policy-Decision";
    private static final String HEADER_POLICY_REASON = "X-Policy-Reason";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    
    // Public paths (skip policy check)
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        "/api/v1/users/auth/",
        "/api/v1/contacts/find-by-value",
        "/actuator/",
        "/fallback/",
        "/gateway/"
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();
        
        // Skip policy check for public endpoints
        if (isPublicEndpoint(path)) {
            log.debug("Public endpoint, skipping policy check: {}", path);
            return chain.filter(exchange);
        }
        
        // Extract headers (added by JwtAuthenticationFilter)
        String userIdStr = request.getHeaders().getFirst(HEADER_USER_ID);
        String tenantIdStr = request.getHeaders().getFirst(HEADER_TENANT_ID);
        String role = request.getHeaders().getFirst(HEADER_USER_ROLE);
        String companyIdStr = request.getHeaders().getFirst(HEADER_COMPANY_ID);
        
        // Validate required headers
        if (userIdStr == null || tenantIdStr == null) {
            log.warn("Missing user/tenant headers for: {}", path);
            return forbiddenResponse(exchange, "missing_security_context");
        }
        
        // Parse UUIDs
        UUID userId;
        UUID tenantId;
        UUID companyId = null;
        try {
            userId = UUID.fromString(userIdStr);
            tenantId = UUID.fromString(tenantIdStr);
            if (companyIdStr != null && !companyIdStr.isEmpty()) {
                companyId = UUID.fromString(companyIdStr);
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in headers: userId={}, tenantId={}, companyId={}", 
                userIdStr, tenantIdStr, companyIdStr);
            return forbiddenResponse(exchange, "invalid_uuid_format");
        }
        
        // Generate correlation ID
        String correlationId = UUID.randomUUID().toString();
        
        // Build PolicyContext
        PolicyContext context = buildPolicyContext(
            userId, tenantId, companyId, role, 
            path, request.getMethod(),
            correlationId
        );
        
        // Call PolicyEngine (blocking call in reactive context)
        return Mono.fromCallable(() -> policyEngine.evaluate(context))
            .subscribeOn(Schedulers.boundedElastic()) // Execute on separate thread pool
            .flatMap(decision -> {
                if (decision.isAllowed()) {
                    // ALLOW - Add headers and proceed
                    log.info("Policy ALLOW - User: {}, Path: {}, Reason: {}", 
                        userId, path, decision.getReason());
                    
                    ServerHttpRequest modifiedRequest = request.mutate()
                        .header(HEADER_POLICY_DECISION, "ALLOW")
                        .header(HEADER_POLICY_REASON, decision.getReason())
                        .header(HEADER_CORRELATION_ID, correlationId)
                        .build();
                    
                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                    
                } else {
                    // DENY - Return 403 Forbidden
                    log.warn("Policy DENY - User: {}, Path: {}, Reason: {}", 
                        userId, path, decision.getReason());
                    
                    return forbiddenResponse(exchange, decision.getReason());
                }
            })
            .onErrorResume(error -> {
                // Fail-safe: deny on error
                log.error("Policy evaluation error for path {}: {}", path, error.getMessage());
                return forbiddenResponse(exchange, "policy_evaluation_error");
            });
    }
    
    /**
     * Build PolicyContext from request
     */
    private PolicyContext buildPolicyContext(UUID userId, UUID tenantId, UUID companyId, String role,
                                            String path, HttpMethod method, String correlationId) {
        // Determine operation from HTTP method
        OperationType operation = mapHttpMethodToOperation(method);
        
        // Infer scope from path (basic logic for now)
        DataScope scope = inferScopeFromPath(path);
        
        // Use actual company ID from JWT (fallback to tenant if not present)
        UUID effectiveCompanyId = companyId != null ? companyId : tenantId;
        
        return PolicyContext.builder()
            .userId(userId)
            .companyId(effectiveCompanyId)
            .companyType(CompanyType.INTERNAL) // TODO: Fetch from Company Service API
            .endpoint(path)
            .httpMethod(method.name())
            .operation(operation)
            .scope(scope)
            .roles(role != null ? List.of(role) : List.of())
            .correlationId(correlationId)
            .requestId(UUID.randomUUID().toString())
            .build();
    }
    
    /**
     * Map HTTP method to OperationType
     */
    private OperationType mapHttpMethodToOperation(HttpMethod method) {
        if (method == null) {
            return OperationType.READ; // Default to least privilege
        }
        
        if (method.equals(HttpMethod.GET)) {
            return OperationType.READ;
        } else if (method.equals(HttpMethod.POST)) {
            return OperationType.WRITE;
        } else if (method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PATCH)) {
            return OperationType.WRITE;
        } else if (method.equals(HttpMethod.DELETE)) {
            return OperationType.DELETE;
        } else {
            return OperationType.READ; // Default to least privilege
        }
    }
    
    /**
     * Infer scope from path pattern
     */
    private DataScope inferScopeFromPath(String path) {
        if (path.contains("/me") || path.contains("/profile")) {
            return DataScope.SELF;
        }
        
        if (path.contains("/admin") || path.contains("/system")) {
            return DataScope.GLOBAL;
        }
        
        // Default to COMPANY scope
        return DataScope.COMPANY;
    }
    
    /**
     * Check if endpoint is public
     */
    private boolean isPublicEndpoint(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
    
    /**
     * Return 403 Forbidden response
     */
    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("X-Policy-Denial-Reason", reason);
        return exchange.getResponse().setComplete();
    }
    
    @Override
    public int getOrder() {
        return -50; // After JWT filter (-100), before logging (0)
    }
}


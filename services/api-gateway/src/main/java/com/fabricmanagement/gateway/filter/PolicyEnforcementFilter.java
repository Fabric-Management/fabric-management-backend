package com.fabricmanagement.gateway.filter;

import com.fabricmanagement.gateway.constants.FilterOrder;
import com.fabricmanagement.gateway.constants.GatewayHeaders;
import com.fabricmanagement.gateway.util.PathMatcher;
import com.fabricmanagement.gateway.util.ResponseHelper;
import com.fabricmanagement.gateway.util.UuidValidator;
import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.policy.engine.PolicyEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

/**
 * Policy Enforcement Filter (PEP - Policy Enforcement Point)
 * 
 * Gateway-level authorization via PolicyEngine.
 * Evaluates requests against policy rules.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyEnforcementFilter implements GlobalFilter, Ordered {
    
    private final PolicyEngine policyEngine;
    private final PathMatcher pathMatcher;
    private final UuidValidator uuidValidator;
    private final ResponseHelper responseHelper;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();
        
        if (pathMatcher.isPublic(path)) {
            return chain.filter(exchange);
        }
        
        String userIdStr = request.getHeaders().getFirst(GatewayHeaders.USER_ID);
        String tenantIdStr = request.getHeaders().getFirst(GatewayHeaders.TENANT_ID);
        
        if (userIdStr == null || tenantIdStr == null) {
            log.warn("Missing security context: {}", path);
            return responseHelper.forbidden(exchange, "missing_security_context");
        }
        
        UUID userId = uuidValidator.parseOrNull(userIdStr);
        UUID tenantId = uuidValidator.parseOrNull(tenantIdStr);
        
        if (userId == null || tenantId == null) {
            return responseHelper.forbidden(exchange, "invalid_uuid_format");
        }
        
        PolicyContext context = buildPolicyContext(request, userId, tenantId);
        
        return evaluatePolicyAsync(context)
            .flatMap(decision -> handleDecision(decision, exchange, chain, request, userId, path))
            .onErrorResume(error -> {
                log.error("Policy evaluation error: {}", error.getMessage());
                return responseHelper.forbidden(exchange, "policy_evaluation_error");
            });
    }
    
    private Mono<PolicyDecision> evaluatePolicyAsync(PolicyContext context) {
        return Mono.fromCallable(() -> policyEngine.evaluate(context))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    private Mono<Void> handleDecision(PolicyDecision decision, ServerWebExchange exchange,
                                      GatewayFilterChain chain, ServerHttpRequest request,
                                      UUID userId, String path) {
        if (decision.isAllowed()) {
            log.info("Policy ALLOW - User: {}, Path: {}", userId, path);
            
            ServerHttpRequest modifiedRequest = request.mutate()
                .header(GatewayHeaders.POLICY_DECISION, "ALLOW")
                .header(GatewayHeaders.POLICY_REASON, decision.getReason())
                .header(GatewayHeaders.CORRELATION_ID, UUID.randomUUID().toString())
                .build();
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } else {
            log.warn("Policy DENY - User: {}, Path: {}, Reason: {}", 
                userId, path, decision.getReason());
            return responseHelper.forbidden(exchange, decision.getReason());
        }
    }
    
    private PolicyContext buildPolicyContext(ServerHttpRequest request, UUID userId, UUID tenantId) {
        String role = request.getHeaders().getFirst(GatewayHeaders.USER_ROLE);
        String companyIdStr = request.getHeaders().getFirst(GatewayHeaders.COMPANY_ID);
        UUID companyId = uuidValidator.parseOrNull(companyIdStr);
        
        String path = request.getPath().toString();
        HttpMethod method = request.getMethod();
        
        return PolicyContext.builder()
            .userId(userId)
            .companyId(companyId != null ? companyId : tenantId)
            .companyType(CompanyType.INTERNAL)
            .endpoint(path)
            .httpMethod(method != null ? method.name() : "GET")
            .operation(mapOperation(method))
            .scope(inferScope(path))
            .roles(role != null ? List.of(role) : List.of())
            .correlationId(UUID.randomUUID().toString())
            .requestId(UUID.randomUUID().toString())
            .build();
    }
    
    private OperationType mapOperation(HttpMethod method) {
        if (method == null) return OperationType.READ;
        
        if (method.equals(HttpMethod.GET)) {
            return OperationType.READ;
        } else if (method.equals(HttpMethod.POST)) {
            return OperationType.WRITE;
        } else if (method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PATCH)) {
            return OperationType.WRITE;
        } else if (method.equals(HttpMethod.DELETE)) {
            return OperationType.DELETE;
        } else {
            return OperationType.READ;
        }
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
    
    @Override
    public int getOrder() {
        return FilterOrder.POLICY_FILTER;
    }
}



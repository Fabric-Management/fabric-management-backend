package com.fabricmanagement.gateway.security;

import com.fabricmanagement.gateway.constants.FilterOrder;
import com.fabricmanagement.gateway.constants.GatewayHeaders;
import com.fabricmanagement.gateway.util.JwtTokenExtractor;
import com.fabricmanagement.gateway.util.PathMatcher;
import com.fabricmanagement.gateway.util.ResponseHelper;
import com.fabricmanagement.gateway.util.UuidValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT Authentication Filter
 * 
 * Validates JWT tokens and extracts security context.
 * Adds tenant/user/role headers for downstream services.
 */
@Component("gatewayJwtFilter")
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final PathMatcher pathMatcher;
    private final JwtTokenExtractor tokenExtractor;
    private final UuidValidator uuidValidator;
    private final ResponseHelper responseHelper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        if (pathMatcher.isPublic(path)) {
            log.debug("Public endpoint: {}", path);
            return chain.filter(exchange);
        }

        String token = tokenExtractor.extract(request);
        if (token == null) {
            log.warn("No JWT token: {}", path);
            return responseHelper.unauthorized(exchange);
        }

        try {
            Claims claims = validateToken(token);
            
            String tenantId = claims.get("tenantId", String.class);
            String userId = claims.getSubject();
            
            if (!validateIds(tenantId, userId)) {
                return responseHelper.unauthorized(exchange);
            }

            ServerHttpRequest modifiedRequest = buildRequestWithHeaders(
                request, tenantId, userId, claims
            );
            
            log.debug("Authenticated: tenant={}, user={}, path={}", tenantId, userId, path);
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return responseHelper.unauthorized(exchange);
        }
    }

    private Claims validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    private boolean validateIds(String tenantId, String userId) {
        if (tenantId == null || userId == null) {
            return false;
        }
        return uuidValidator.isValid(tenantId) && uuidValidator.isValid(userId);
    }

    private ServerHttpRequest buildRequestWithHeaders(
            ServerHttpRequest request, String tenantId, String userId, Claims claims) {

        String role = claims.get("role", String.class);
        String companyId = claims.get("companyId", String.class);

        ServerHttpRequest.Builder builder = request.mutate()
            .header(GatewayHeaders.TENANT_ID, tenantId)
            .header(GatewayHeaders.USER_ID, userId)
            .header(GatewayHeaders.USER_ROLE, role != null ? role : "USER");

        if (companyId != null && !companyId.isEmpty()) {
            builder.header(GatewayHeaders.COMPANY_ID, companyId);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }

        return builder.build();
    }

    @Override
    public int getOrder() {
        return FilterOrder.JWT_FILTER;
    }
}

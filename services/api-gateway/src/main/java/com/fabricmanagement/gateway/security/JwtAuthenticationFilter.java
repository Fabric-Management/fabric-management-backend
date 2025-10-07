package com.fabricmanagement.gateway.security;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * JWT Authentication Filter
 *
 * Global filter for Gateway that validates JWT tokens and extracts tenant/user information.
 * Adds X-Tenant-Id and X-User-Id headers to downstream requests.
 *
 * Order: -100 (executes before other filters)
 *
 * Public endpoints (no authentication required):
 * - /api/v1/users/auth/**
 * - /actuator/**
 * - /fallback/**
 */
@Component("gatewayJwtFilter")
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    
    /**
     * Public paths (BEFORE StripPrefix filter - checking original request path)
     * These paths don't require JWT authentication
     */
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        "/api/v1/users/auth/",        // All auth endpoints
        "/api/v1/contacts/find-by-value",  // Internal contact lookup (for auth)
        "/actuator/health",           // Health check
        "/actuator/info",             // Info endpoint
        "/actuator/prometheus",       // Prometheus metrics
        "/fallback/",                 // Fallback endpoints
        "/gateway/"                   // Gateway management endpoints
    );

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        // Skip authentication for public endpoints
        boolean isPublic = isPublicEndpoint(path);
        log.info("JwtAuthenticationFilter - Path: {} | IsPublic: {} | PUBLIC_PATHS: {}", path, isPublic, PUBLIC_PATHS);

        if (isPublic) {
            log.info("Public endpoint, skipping JWT authentication: {}", path);
            return chain.filter(exchange);
        }

        // Extract JWT token
        String token = extractToken(request);
        if (token == null) {
            log.warn("No JWT token found in request to: {}", path);
            return unauthorizedResponse(exchange);
        }

        try {
            // Validate token and extract claims
            Claims claims = validateTokenAndExtractClaims(token);

            // Extract tenant and user IDs
            String tenantId = claims.get("tenantId", String.class);
            String userId = claims.getSubject(); // 'sub' claim

            if (tenantId == null || userId == null) {
                log.warn("Missing tenantId or userId in JWT claims");
                return unauthorizedResponse(exchange);
            }

            // Add headers to downstream request
            ServerHttpRequest modifiedRequest = request.mutate()
                .header(HEADER_TENANT_ID, tenantId)
                .header(HEADER_USER_ID, userId)
                .build();

            log.debug("Authenticated request: tenant={}, user={}, path={}", tenantId, userId, path);

            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            log.error("JWT validation failed for path {}: {}", path, e.getMessage());
            return unauthorizedResponse(exchange);
        }
    }

    /**
     * Checks if the path is a public endpoint
     */
    private boolean isPublicEndpoint(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Extracts JWT token from Authorization header
     */
    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * Validates JWT token and extracts claims
     */
    private Claims validateTokenAndExtractClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    /**
     * Returns 401 Unauthorized response
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100; // Execute BEFORE other filters (high priority)
    }
}

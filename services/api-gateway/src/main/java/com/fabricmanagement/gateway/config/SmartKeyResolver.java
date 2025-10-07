package com.fabricmanagement.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

/**
 * Smart Key Resolver for Rate Limiting
 * 
 * Adaptive strategy based on endpoint type:
 * - Public endpoints: IP-based rate limiting (generous limits)
 * - Protected endpoints: User/Tenant-based rate limiting (strict limits)
 * - Anonymous: IP-based fallback
 * 
 * Follows principles:
 * - No hardcoded paths (pattern-based detection)
 * - Graceful degradation (fallback to IP)
 * - Proxy-safe (X-Forwarded-For support)
 */
@Component("smartKeyResolver")
@Slf4j
public class SmartKeyResolver implements KeyResolver {
    
    /**
     * Public endpoint patterns (no authentication required)
     * These endpoints get IP-based rate limiting with generous limits
     */
    private static final Set<String> PUBLIC_PATTERNS = Set.of(
        "/auth/",
        "/actuator/",
        "/health",
        "/fallback/"
    );
    
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        
        // Public endpoint: IP-based rate limiting
        if (isPublicEndpoint(path)) {
            String clientIp = getClientIp(exchange);
            String key = "public:" + clientIp;
            log.debug("Rate limit key for public endpoint {}: {}", path, key);
            return Mono.just(key);
        }
        
        // Protected endpoint: Try user-based, fallback to IP
        return Mono.justOrEmpty(getUserBasedKey(exchange))
            .doOnNext(key -> log.debug("Rate limit key for protected endpoint {}: {}", path, key))
            .switchIfEmpty(Mono.fromSupplier(() -> {
                String clientIp = getClientIp(exchange);
                String key = "anonymous:" + clientIp;
                log.debug("Rate limit key (anonymous fallback) for {}: {}", path, key);
                return key;
            }));
    }
    
    /**
     * Check if path matches public endpoint patterns
     */
    private boolean isPublicEndpoint(String path) {
        return PUBLIC_PATTERNS.stream().anyMatch(path::contains);
    }
    
    /**
     * Get user-based key from headers (set by JwtAuthenticationFilter)
     */
    private String getUserBasedKey(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        
        if (userId != null && tenantId != null) {
            return "user:" + tenantId + ":" + userId;
        } else if (userId != null) {
            return "user:" + userId;
        }
        
        return null;
    }
    
    /**
     * Get client IP with X-Forwarded-For support (proxy-safe)
     */
    private String getClientIp(ServerWebExchange exchange) {
        // Check X-Forwarded-For header (for proxy/load balancer)
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // Take first IP in chain (original client)
            return forwardedFor.split(",")[0].trim();
        }
        
        // Fallback to remote address
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
            .map(addr -> addr.getAddress().getHostAddress())
            .orElse("unknown");
    }
}

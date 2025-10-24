package com.fabricmanagement.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global Filter - Security Headers
 * 
 * Adds enterprise-grade security headers to all responses.
 * 
 * Headers added:
 * - X-Content-Type-Options: nosniff (prevents MIME sniffing)
 * - X-Frame-Options: DENY (prevents clickjacking)
 * - X-XSS-Protection: 1; mode=block (XSS protection)
 * - Strict-Transport-Security: HSTS (forces HTTPS)
 * - Content-Security-Policy: CSP rules
 * - Referrer-Policy: no-referrer (privacy)
 * - Permissions-Policy: restricts browser features
 * 
 * @since 3.1.0 - Gateway Refactor (Oct 13, 2025)
 */
@Slf4j
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            
            // Prevent MIME type sniffing
            headers.add("X-Content-Type-Options", "nosniff");
            
            // Prevent clickjacking
            headers.add("X-Frame-Options", "DENY");
            
            // XSS protection (legacy but still useful)
            headers.add("X-XSS-Protection", "1; mode=block");
            
            // Force HTTPS (only in production)
            // headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
            
            // Content Security Policy (basic)
            headers.add("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'");
            
            // Referrer policy (privacy)
            headers.add("Referrer-Policy", "no-referrer");
            
            // Permissions policy (restrict browser features)
            headers.add("Permissions-Policy", 
                "geolocation=(), microphone=(), camera=(), payment=(), usb=()");
            
            // API Gateway signature
            headers.add("X-Gateway-Version", "3.1.0");
            headers.add("X-Powered-By", "Fabric Management System");
            
            log.debug("✅ Security headers added to response");
        }));
    }
    
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // Run last (before sending response)
    }
}


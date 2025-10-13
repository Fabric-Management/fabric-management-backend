package com.fabricmanagement.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global Filter - Correlation ID Management
 * 
 * Ensures every request has a unique correlation ID for distributed tracing.
 * If X-Correlation-ID header exists, uses it; otherwise generates new UUID.
 * 
 * Benefits:
 * - End-to-end request tracking across microservices
 * - Debugging distributed systems
 * - Audit trail correlation
 * 
 * @since 3.1.0 - Gateway Refactor (Oct 13, 2025)
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Get or generate correlation ID
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("Using existing correlation ID: {}", correlationId);
        }
        
        // Get or generate request ID (unique per request, even if same correlation)
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        
        // Add headers to request
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .header(REQUEST_ID_HEADER, requestId)
                .build();
        
        // Add headers to response
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);
        
        // Log request details
        log.info("🔵 Gateway Request | Method: {} | Path: {} | Correlation-ID: {} | Request-ID: {}",
                request.getMethod(),
                request.getPath(),
                correlationId,
                requestId);
        
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // Run first!
    }
}


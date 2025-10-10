package com.fabricmanagement.gateway.filter;

import com.fabricmanagement.gateway.constants.FilterOrder;
import com.fabricmanagement.gateway.constants.GatewayHeaders;
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
 * Request Logging Filter
 * 
 * Logs all incoming requests with structured logging.
 * Adds correlation ID for request tracing.
 */
@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        long startTime = System.currentTimeMillis();
        String correlationId = getOrCreateCorrelationId(request);
        
        ServerHttpRequest modifiedRequest = request.mutate()
            .header(GatewayHeaders.CORRELATION_ID, correlationId)
            .build();
        
        logRequest(request, correlationId);
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build())
            .then(Mono.fromRunnable(() -> 
                logResponse(request, exchange, startTime, correlationId)
            ));
    }
    
    private String getOrCreateCorrelationId(ServerHttpRequest request) {
        String existing = request.getHeaders().getFirst(GatewayHeaders.CORRELATION_ID);
        return existing != null ? existing : UUID.randomUUID().toString();
    }
    
    private void logRequest(ServerHttpRequest request, String correlationId) {
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().toString();
        String remoteAddress = getRemoteAddress(request);
        
        log.info("→ Request: method={}, path={}, remote={}, correlationId={}",
            method, path, remoteAddress, correlationId);
    }
    
    private void logResponse(ServerHttpRequest request, ServerWebExchange exchange, 
                             long startTime, String correlationId) {
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().toString();
        int status = getStatusCode(exchange);
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("← Response: method={}, path={}, status={}, duration={}ms, correlationId={}",
            method, path, status, duration, correlationId);
    }
    
    private String getRemoteAddress(ServerHttpRequest request) {
        var remoteAddr = request.getRemoteAddress();
        return remoteAddr != null ? remoteAddr.getAddress().getHostAddress() : "unknown";
    }
    
    private int getStatusCode(ServerWebExchange exchange) {
        var statusCode = exchange.getResponse().getStatusCode();
        return statusCode != null ? statusCode.value() : 0;
    }

    @Override
    public int getOrder() {
        return FilterOrder.LOGGING_FILTER;
    }
}

package com.fabricmanagement.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Request Logging Filter
 *
 * Logs all incoming requests for monitoring and debugging.
 * Order: 0 (executes after authentication but before routing)
 *
 * Best Practice: Centralized logging for all microservice requests
 */
@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        long startTime = System.currentTimeMillis();

        String method = request.getMethod().name();
        String path = request.getPath().toString();
        String remoteAddress = request.getRemoteAddress() != null
            ? request.getRemoteAddress().getAddress().getHostAddress()
            : "unknown";

        log.info("→ Incoming request: {} {} from {}", method, path, remoteAddress);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 0;

            log.info("← Response: {} {} - Status: {} - Duration: {}ms",
                method, path, statusCode, duration);
        }));
    }

    @Override
    public int getOrder() {
        return 0; // Execute after authentication (-100) but before routing
    }
}

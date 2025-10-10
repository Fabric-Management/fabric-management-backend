package com.fabricmanagement.gateway.util;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Response Helper
 * 
 * Provides consistent HTTP response handling for filters.
 * Centralizes error response logic.
 */
@Component
public class ResponseHelper {
    
    /**
     * Return 401 Unauthorized response
     * 
     * @param exchange ServerWebExchange
     * @return Mono<Void> completed response
     */
    public Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
    
    /**
     * Return 403 Forbidden response
     * 
     * @param exchange ServerWebExchange
     * @return Mono<Void> completed response
     */
    public Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }
    
    /**
     * Return 403 Forbidden response with reason header
     * 
     * @param exchange ServerWebExchange
     * @param reason Denial reason
     * @return Mono<Void> completed response
     */
    public Mono<Void> forbidden(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("X-Policy-Denial-Reason", reason);
        return exchange.getResponse().setComplete();
    }
}


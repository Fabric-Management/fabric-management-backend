package com.fabricmanagement.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gateway Health Controller
 *
 * Provides additional health information specific to API Gateway.
 * Supplements Spring Actuator's /actuator/health endpoint.
 */
@RestController
@RequestMapping("/gateway")
@Slf4j
public class GatewayHealthController {

    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * Gateway-specific health check
     * Public endpoint - no authentication required
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "api-gateway");
        health.put("timestamp", LocalDateTime.now());
        health.put("uptime", getUptime());

        return Mono.just(ResponseEntity.ok(health));
    }

    /**
     * Gateway information endpoint
     * Public endpoint - provides basic gateway info
     */
    @GetMapping("/info")
    public Mono<ResponseEntity<Map<String, Object>>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "Fabric Management API Gateway");
        info.put("version", "1.0.0");
        info.put("description", "Central entry point for all microservices");
        info.put("features", new String[]{
            "JWT Authentication",
            "Rate Limiting",
            "Circuit Breaker",
            "Request Logging",
            "CORS Support"
        });

        return Mono.just(ResponseEntity.ok(info));
    }

    private String getUptime() {
        LocalDateTime now = LocalDateTime.now();
        long seconds = java.time.Duration.between(startTime, now).getSeconds();

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%d hours, %d minutes, %d seconds", hours, minutes, secs);
    }
}

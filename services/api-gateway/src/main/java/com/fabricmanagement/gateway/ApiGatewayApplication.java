package com.fabricmanagement.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application
 *
 * Central entry point for all microservices in the Fabric Management System.
 *
 * Features:
 * - Centralized routing
 * - JWT authentication & authorization
 * - Rate limiting (Redis-based)
 * - Circuit breaker pattern
 * - CORS configuration
 * - Request/Response logging
 *
 * Architecture: Reactive (Spring WebFlux)
 * Port: 8080
 *
 * @see <a href="https://spring.io/projects/spring-cloud-gateway">Spring Cloud Gateway</a>
 */
@SpringBootApplication(scanBasePackages = {
    "com.fabricmanagement.gateway",
    "com.fabricmanagement.shared"
})
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

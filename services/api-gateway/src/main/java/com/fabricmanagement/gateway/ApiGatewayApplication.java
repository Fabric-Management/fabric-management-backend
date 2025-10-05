package com.fabricmanagement.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

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
@SpringBootApplication(
    scanBasePackages = "com.fabricmanagement.gateway",
    exclude = {
        SecurityAutoConfiguration.class,
        ReactiveSecurityAutoConfiguration.class,
        ReactiveUserDetailsServiceAutoConfiguration.class
    }
)
@ComponentScan(
    basePackages = {
        "com.fabricmanagement.gateway",
        "com.fabricmanagement.shared.domain",
        "com.fabricmanagement.shared.application"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.fabricmanagement\\.shared\\.security\\..*"
    )
)
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

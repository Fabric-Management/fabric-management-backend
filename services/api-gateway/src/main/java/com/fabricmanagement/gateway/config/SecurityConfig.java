package com.fabricmanagement.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security Configuration for API Gateway
 * Reactive WebFlux Security Configuration
 * 
 * Note: JWT authentication is handled by JwtAuthenticationFilter (GlobalFilter)
 * This configuration defines which paths are public vs protected.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Security filter chain configuration
     * 
     * Public paths: auth endpoints, health checks, fallbacks
     * Protected paths: all other API endpoints (require JWT)
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Public: Authentication endpoints
                .pathMatchers("/api/v1/users/auth/**").permitAll()
                
                // Public: Internal contact lookup (used by User Service for auth)
                .pathMatchers(HttpMethod.GET, "/api/v1/contacts/find-by-value").permitAll()
                
                // Public: Health and monitoring
                .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                
                // Public: Fallback endpoints
                .pathMatchers("/fallback/**").permitAll()
                
                // Public: Gateway management
                .pathMatchers("/gateway/**").permitAll()
                
                // Protected: All other endpoints require authentication
                // JWT validation is done by JwtAuthenticationFilter
                .anyExchange().authenticated()
            )
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable);

        return http.build();
    }
}

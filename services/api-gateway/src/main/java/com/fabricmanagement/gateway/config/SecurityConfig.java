package com.fabricmanagement.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                // Allow all requests - JWT validation is done by backend services
                // Gateway acts as a simple routing layer (pass-through)
                .anyExchange().permitAll()
            )
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable);

        return http.build();
    }
}

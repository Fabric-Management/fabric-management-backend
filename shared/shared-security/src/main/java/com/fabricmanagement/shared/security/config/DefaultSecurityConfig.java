package com.fabricmanagement.shared.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Default Security Configuration for Microservices
 *
 * Provides sensible defaults for all services:
 * - Stateless session management
 * - CSRF disabled (for REST APIs)
 * - Public actuator endpoints
 * - Public internal service-to-service endpoints
 *
 * Services can override this by providing their own SecurityFilterChain bean.
 * This configuration is only applied if no other SecurityFilterChain bean is defined.
 */
@Configuration
@EnableWebSecurity
public class DefaultSecurityConfig {

    /**
     * Default security filter chain
     * Applies to all services using shared-security module
     * Only activated if no other SecurityFilterChain bean exists
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: Health checks and monitoring
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                
                // Public: Internal service-to-service endpoints
                // These endpoints are called by other microservices
                .requestMatchers(
                    "/api/**/find-by-value",           // Contact lookup
                    "/api/**/owner/**",                // Owner-based queries
                    "/api/**/check-availability"       // Availability checks
                ).permitAll()
                
                // Public: Authentication endpoints
                .requestMatchers(
                    "/api/**/auth/**"                  // Login, register, etc.
                ).permitAll()
                
                // TODO: Protect all other endpoints with JWT
                // For now, permit all for development
                .anyRequest().permitAll()
            );

        return http.build();
    }

    /**
     * Password encoder bean
     * Uses BCrypt hashing algorithm for password encryption
     * Strength: 10 (default)
     */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}


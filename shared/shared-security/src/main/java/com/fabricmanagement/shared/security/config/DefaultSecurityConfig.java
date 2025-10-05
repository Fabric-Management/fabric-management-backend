package com.fabricmanagement.shared.security.config;

import com.fabricmanagement.shared.security.exception.JwtAuthenticationEntryPoint;
import com.fabricmanagement.shared.security.filter.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Default Security Configuration for Microservices
 *
 * Provides sensible defaults for all services:
 * - Stateless session management
 * - CSRF disabled (for REST APIs)
 * - JWT authentication for protected endpoints
 * - Public actuator endpoints
 * - Public authentication endpoints
 * - Public internal service-to-service endpoints
 *
 * Services can override this by providing their own SecurityFilterChain bean.
 * This configuration is only applied if no other SecurityFilterChain bean is defined.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class DefaultSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

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
                    "/**/find-by-value",           // Contact lookup
                    "/**/owner/**",                // Owner-based queries
                    "/**/check-availability"       // Availability checks
                ).permitAll()

                // Public: Authentication endpoints
                .requestMatchers(
                    "/**/auth/**"                  // Login, register, setup-password, check-contact
                ).permitAll()

                // Public: Swagger/OpenAPI (development only - disable in production via config)
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()

                // Protected: All other endpoints require JWT authentication
                .anyRequest().authenticated()
            )
            // Exception handling for authentication failures
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            // Add JWT authentication filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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

    /**
     * ObjectMapper bean for JSON serialization
     * Used by JWT exception handlers
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Dummy UserDetailsService to prevent Spring Boot auto-configuration warnings
     *
     * This bean is required by Spring Security but not actually used in our JWT-based
     * authentication system. All authentication is handled by JwtAuthenticationFilter
     * which extracts user information from JWT tokens.
     *
     * Without this bean, Spring Boot would auto-configure a default UserDetailsService
     * with a random password, causing unnecessary warnings in logs.
     */
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService userDetailsService() {
        // Return an empty in-memory user details manager
        // This will never be used as JWT filter handles all authentication
        return new InMemoryUserDetailsManager();
    }
}


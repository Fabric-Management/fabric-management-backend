package com.fabricmanagement.common.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security Configuration
 * 
 * Development phase: Permissive for rapid development
 * Production: JWT-based authentication with proper authorization
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Profile({"local", "dev"})
    public SecurityFilterChain developmentSecurityFilterChain(HttpSecurity http) throws Exception {
        log.warn("⚠️  DEVELOPMENT MODE - Security is PERMISSIVE");
        
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/api/info").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/dev/**").permitAll()  // Development tools
                .requestMatchers("/api/public/**").permitAll()  // Public signup
                .requestMatchers("/api/auth/**").permitAll()  // Auth endpoints
                .requestMatchers("/api/admin/**").permitAll()  // ⚠️ TEMP: Admin endpoints (will be secured in production)
                .anyRequest().permitAll()  // ⚠️ DEVELOPMENT: Allow all
            )
            .build();
    }

    @Bean
    @Profile("prod")
    public SecurityFilterChain productionSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("🔒 PRODUCTION MODE - Security is ENFORCED");
        
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/api/info").permitAll()
                .requestMatchers("/api/public/**").permitAll()  // Public signup
                .requestMatchers("/api/auth/login", "/api/auth/register/**", "/api/auth/setup-password").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/admin/**").hasRole("PLATFORM_ADMIN")  // ✅ Admin endpoints protected
                .anyRequest().authenticated()
            )
            .build();
    }
}


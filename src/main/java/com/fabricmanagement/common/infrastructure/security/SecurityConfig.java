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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

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
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // ✅ Enable CORS
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
            .cors(cors -> cors.configurationSource(productionCorsConfigurationSource())) // ✅ Enable CORS
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/api/info").permitAll()
                .requestMatchers("/api/public/**").permitAll()  // Public signup
                .requestMatchers("/api/common/companies/types/**").permitAll()  // Company types (for signup forms)
                .requestMatchers("/api/auth/login", "/api/auth/register/**", "/api/auth/setup-password").permitAll()
                .requestMatchers("/api/auth/password-reset/**").permitAll()  // Password reset endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/admin/**").hasRole("PLATFORM_ADMIN")  // ✅ Admin endpoints protected
                .anyRequest().authenticated()
            )
            .build();
    }

    /**
     * CORS Configuration for Development.
     * 
     * <p>Allows common development origins, methods, and headers.</p>
     * <p>⚠️ In production, configure specific origins for security.</p>
     */
    @Bean
    @Profile({"local", "dev"})
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow common development origins
        // Note: Cannot use "*" with allowCredentials(true), so we list common dev origins
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://0.0.0.0:*"
        ));
        
        // Allow all HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        
        // Allow all headers
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials (cookies, auth headers)
        configuration.setAllowCredentials(true);
        
        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);
        
        // Exposed headers that frontend can read
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Total-Count"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        log.info("✅ CORS configured: Development origins allowed (localhost, 127.0.0.1, etc.)");
        
        return source;
    }

    /**
     * CORS Configuration for Production.
     * 
     * <p>Configure specific allowed origins for security.</p>
     */
    @Bean
    @Profile("prod")
    public CorsConfigurationSource productionCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Set specific allowed origins from environment variable
        // Example: CORS_ALLOWED_ORIGINS=https://app.fabricmanagement.com,https://admin.fabricmanagement.com
        String allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            List<String> origins = Arrays.asList(allowedOrigins.split(","));
            configuration.setAllowedOrigins(origins);
            log.info("✅ CORS allowed origins: {}", origins);
        } else {
            // ⚠️ Fallback: Should be configured in production
            log.warn("⚠️ CORS_ALLOWED_ORIGINS not set! Configure in production!");
            // Use pattern matching as fallback (works with credentials)
            configuration.setAllowedOriginPatterns(Arrays.asList("https://*.fabricmanagement.com"));
        }
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Total-Count"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        log.info("✅ CORS configured for production");
        
        return source;
    }
}


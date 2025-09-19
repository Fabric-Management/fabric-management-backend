package com.fabricmanagement.identity.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * JPA Auditing configuration.
 * Provides the current user for audit fields.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return new AuditorAwareImpl();
    }

    /**
     * Implementation of AuditorAware to capture current user.
     */
    public static class AuditorAwareImpl implements AuditorAware<String> {

        @Override
        public Optional<String> getCurrentAuditor() {
            // Try to get from Spring Security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }
            
            // Get username or principal name
            String username = authentication.getName();
            if (username != null && !"anonymousUser".equals(username)) {
                return Optional.of(username);
            }
            
            return Optional.of("system");
        }
    }
}
package com.fabricmanagement.user.infrastructure.config;

import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * JPA Auditing Configuration
 * 
 * Provides the AuditorAware bean for Spring Data JPA auditing
 * Used to populate createdBy and updatedBy fields automatically
 */
@Configuration
public class AuditorConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(ServiceConstants.AUDIT_SYSTEM_USER);
    }
}


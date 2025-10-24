package com.fabricmanagement.fiber.infrastructure.config;

import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
public class AuditorConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(ServiceConstants.AUDIT_SYSTEM_USER);
    }
}


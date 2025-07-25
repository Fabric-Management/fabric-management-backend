package com.fabricmanagement.user_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.fabricmanagement.user_service.repository")
@EnableTransactionManagement
public class JpaConfig {
    // İleride AuditorAware eklenebilir (kim oluşturdu/güncelledi bilgisi için)
}
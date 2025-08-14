package com.fabricmanagement.user.infrastructure.config;

import com.fabricmanagement.common.persistence.config.BaseJpaConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.fabricmanagement.user.infrastructure.adapter.out.persistence.repository")
public class JpaConfig extends BaseJpaConfig {
    // User service specific JPA configurations can be added here
}
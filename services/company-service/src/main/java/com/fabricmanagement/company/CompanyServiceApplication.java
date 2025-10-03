package com.fabricmanagement.company;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Company Service Application
 * 
 * Provides comprehensive company management including:
 * - Company profile and information management
 * - Multi-tenancy support
 * - Company settings and preferences
 * - Company user management
 * - Company billing and subscription management
 * - Integration with Contact Service for company contact management
 * 
 * Architecture: Clean Architecture + CQRS + Event Sourcing
 * Port: 8083
 */
@SpringBootApplication(scanBasePackages = {
    "com.fabricmanagement.company",
    "com.fabricmanagement.shared"
})
@EnableJpaRepositories(basePackages = {
    "com.fabricmanagement.company",
    "com.fabricmanagement.shared"
})
@EnableCaching
@EnableKafka
@EnableAsync
@EnableTransactionManagement
@EnableFeignClients
public class CompanyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanyServiceApplication.class, args);
    }
}

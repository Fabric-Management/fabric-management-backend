package com.fabricmanagement.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * User Service Application
 * 
 * Provides comprehensive user management including:
 * - Authentication and authorization
 * - User profile management
 * - Session management
 * - JWT token handling
 * - User preferences and settings
 * 
 * Architecture: Clean Architecture + CQRS + Event Sourcing
 * Port: 8081
 */
@SpringBootApplication(scanBasePackages = {
    "com.fabricmanagement.user",
    "com.fabricmanagement.shared"
})
@EnableJpaRepositories(basePackages = "com.fabricmanagement.user.infrastructure.repository")
@EnableCaching
@EnableKafka
@EnableAsync
@EnableTransactionManagement
@EnableFeignClients
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

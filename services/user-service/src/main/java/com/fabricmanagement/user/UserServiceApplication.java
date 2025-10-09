package com.fabricmanagement.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
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
@SpringBootApplication(
    scanBasePackages = {
        "com.fabricmanagement.user",
        "com.fabricmanagement.shared.domain",
        "com.fabricmanagement.shared.application",
        "com.fabricmanagement.shared.security"
        // NOTE: shared.infrastructure.policy excluded (Policy management in Company Service)
    },
    exclude = {RedisRepositoriesAutoConfiguration.class}
)
@EnableJpaRepositories(basePackages = "com.fabricmanagement.user.infrastructure.repository")
@EntityScan(basePackages = {
    "com.fabricmanagement.user.domain",
    "com.fabricmanagement.shared.domain"
})
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

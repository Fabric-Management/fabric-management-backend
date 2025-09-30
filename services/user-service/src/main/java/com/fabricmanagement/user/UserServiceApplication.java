package com.fabricmanagement.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
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
@EnableCaching
@EnableKafka
@EnableAsync
@EnableTransactionManagement
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

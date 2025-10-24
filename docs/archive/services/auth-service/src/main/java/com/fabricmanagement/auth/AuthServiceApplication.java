package com.fabricmanagement.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * Auth Service Application
 * 
 * Authentication and Authorization Service for Fabric Management System
 * 
 * Responsibilities:
 * - User authentication (login/logout)
 * - JWT token management
 * - Password management
 * - Session management
 * - Authorization (permissions/roles)
 * - OAuth/SSO integration
 * 
 * Architecture:
 * - Event-driven with Kafka
 * - Outbox pattern for reliable event publishing
 * - Redis for session caching
 * - PostgreSQL for persistent data
 * 
 * Security Features:
 * - JWT-based authentication
 * - Password encryption
 * - Session management
 * - Rate limiting
 * - Audit logging
 */
@SpringBootApplication(
    scanBasePackages = {
        "com.fabricmanagement.auth",
        "com.fabricmanagement.shared"
    },
    exclude = {RedisRepositoriesAutoConfiguration.class}
)
@EnableJpaRepositories(basePackages = {
    "com.fabricmanagement.auth.infrastructure.repository",
    "com.fabricmanagement.auth.infrastructure.outbox",
    "com.fabricmanagement.shared.infrastructure.policy.repository"
})
@EntityScan(basePackages = {
    "com.fabricmanagement.auth.domain",
    "com.fabricmanagement.shared.domain"
})
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableCaching
@EnableKafka
@EnableAsync
@EnableTransactionManagement
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}

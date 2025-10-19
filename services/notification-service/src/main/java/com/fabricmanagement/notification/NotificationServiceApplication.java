package com.fabricmanagement.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Notification Service Application
 * 
 * Multi-tenant notification service with channel fallback pattern.
 * 
 * Features:
 * ✅ Email/SMS/WhatsApp notifications
 * ✅ Tenant-specific credentials (fallback to platform config)
 * ✅ Kafka event-driven architecture
 * ✅ Verification code management
 * ✅ Template-based messaging
 * ✅ Delivery tracking & retry
 * 
 * Architecture:
 * - Listens to Kafka events from user/contact/company services
 * - Tenant config stored in database (encrypted credentials)
 * - Platform fallback config (info@storeandsale.shop, +447553838399)
 * - WhatsApp prioritized over SMS (cost optimization)
 * 
 * @author Fabric Management Team
 * @since 1.0 (Oct 15, 2025)
 */
@Slf4j
@SpringBootApplication(
    scanBasePackages = {
        "com.fabricmanagement.notification",
        "com.fabricmanagement.shared"
    },
    exclude = {RedisRepositoriesAutoConfiguration.class}
)
@EnableCaching
@EnableAsync
@EnableKafka
@EnableJpaAuditing
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = {
    "com.fabricmanagement.notification.infrastructure.repository",
    "com.fabricmanagement.shared.infrastructure.policy.repository"
})
@EntityScan(basePackages = {
    "com.fabricmanagement.notification.domain",
    "com.fabricmanagement.shared.domain.policy"
})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
        log.info("🚀 Notification Service started successfully");
        log.info("📧 Email/SMS/WhatsApp notifications ready");
    }
}


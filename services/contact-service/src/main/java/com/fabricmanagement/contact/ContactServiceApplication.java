package com.fabricmanagement.contact;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Contact Service Application
 * 
 * Provides comprehensive contact information management including:
 * - User contact details (email, phone, address)
 * - Company contact information
 * - Communication preferences
 * - Contact validation and verification
 * 
 * Architecture: Clean Architecture + CQRS + Event Sourcing
 * Port: 8082
 */
@SpringBootApplication(scanBasePackages = {
    "com.fabricmanagement.contact",
    "com.fabricmanagement.shared"
})
@EnableJpaRepositories(basePackages = {
    "com.fabricmanagement.contact",
    "com.fabricmanagement.shared"
})
@EnableCaching
@EnableKafka
@EnableAsync
@EnableTransactionManagement
public class ContactServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContactServiceApplication.class, args);
    }
}

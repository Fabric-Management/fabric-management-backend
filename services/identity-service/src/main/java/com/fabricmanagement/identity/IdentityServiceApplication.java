package com.fabricmanagement.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Identity Service Application
 * Consolidated service for authentication, user management, and user contacts.
 */
@SpringBootApplication(scanBasePackages = {
    "com.fabricmanagement.identity",
    "com.fabricmanagement.common.core",
    "com.fabricmanagement.common.security"
})
@EnableFeignClients
@EnableAsync
@EnableTransactionManagement
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
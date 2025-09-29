package com.fabricmanagement.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Single Responsibility: Application startup only
 * Open/Closed: Can be extended without modification
 * Main application class for Identity Service
 */
@SpringBootApplication
@EnableFeignClients
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
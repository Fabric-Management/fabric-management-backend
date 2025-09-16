package com.fabricmanagement.common.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Main application class for Common Core module.
 * This is used for testing and standalone execution.
 */
@SpringBootApplication
@EnableJpaAuditing
public class CommonCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommonCoreApplication.class, args);
    }
}

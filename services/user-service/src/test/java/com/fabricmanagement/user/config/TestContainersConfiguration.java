package com.fabricmanagement.user.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers Configuration for Integration Tests
 * 
 * Provides containerized PostgreSQL and Kafka for realistic integration testing.
 * 
 * Usage:
 * @Import(TestContainersConfiguration.class)
 * @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
 * 
 * NOTE: This configuration is optional and should be explicitly imported
 * in tests that require real database/messaging infrastructure.
 * Most repository tests use H2 for faster execution.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfiguration {

    /**
     * PostgreSQL container for database integration tests
     * 
     * Bean lifecycle managed by Spring - no manual close needed
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @SuppressWarnings("resource") // Container lifecycle managed by Spring
    public PostgreSQLContainer<?> postgresContainer() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:15-alpine")
        )
                .withDatabaseName("user_service_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true); // Reuse container across test runs for speed
        
        // Set system properties for Spring datasource
        System.setProperty("spring.datasource.url", container.getJdbcUrl());
        System.setProperty("spring.datasource.username", container.getUsername());
        System.setProperty("spring.datasource.password", container.getPassword());
        
        return container;
    }

    /**
     * Kafka container for messaging integration tests
     * 
     * Bean lifecycle managed by Spring - no manual close needed
     * Uses new KafkaContainer from org.testcontainers.kafka package
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @SuppressWarnings("resource") // Container lifecycle managed by Spring
    public KafkaContainer kafkaContainer() {
        KafkaContainer container = new KafkaContainer(
                DockerImageName.parse("apache/kafka:3.9.0")
        )
                .withReuse(true); // Reuse container across test runs for speed
        
        // Set system property for Spring Kafka
        System.setProperty("spring.kafka.bootstrap-servers", container.getBootstrapServers());
        
        return container;
    }
}

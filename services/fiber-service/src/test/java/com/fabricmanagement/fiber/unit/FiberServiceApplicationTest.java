package com.fabricmanagement.fiber.unit;

import com.fabricmanagement.fiber.FiberServiceApplication;
import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyDecisionAuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Spring Boot Application Context Load Test
 * 
 * Verifies that:
 * - Application context loads successfully
 * - All beans are properly configured
 * - No circular dependencies
 * - Configuration is valid
 * 
 * This is a critical production readiness test.
 * If context fails to load, service cannot start in production.
 */
@SpringBootTest(classes = FiberServiceApplication.class)
@Testcontainers
@Import(FiberServiceApplicationTest.TestConfig.class)
@DisplayName("FiberServiceApplication - Context Load Test")
class FiberServiceApplicationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        PolicyDecisionAuditRepository policyDecisionAuditRepository() {
            return mock(PolicyDecisionAuditRepository.class);
        }
    }

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fiber_app_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("deprecation")
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    @SuppressWarnings("deprecation")
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        
        registry.add("jwt.secret", () -> "test-secret-key-for-fiber-service-minimum-256-bits-required-for-hmac-sha-algorithm");
        registry.add("jwt.expiration", () -> "3600000");
        registry.add("jwt.refresh-expiration", () -> "86400000");
    }

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Should load Spring Boot application context successfully")
    void shouldLoadApplicationContext() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should have FiberServiceApplication bean")
    void shouldHaveFiberServiceApplicationBean() {
        assertThat(context.containsBean("fiberServiceApplication")).isTrue();
    }

    @Test
    @DisplayName("Should configure all required infrastructure beans")
    void shouldConfigureInfrastructureBeans() {
        assertThat(context.containsBean("fiberRepository")).isTrue();
        assertThat(context.containsBean("fiberService")).isTrue();
        assertThat(context.containsBean("fiberController")).isTrue();
        assertThat(context.containsBean("fiberEventPublisher")).isTrue();
        assertThat(context.containsBean("fiberMapper")).isTrue();
        assertThat(context.containsBean("fiberEventMapper")).isTrue();
    }

    @Test
    @DisplayName("Should configure security infrastructure")
    void shouldConfigureSecurityBeans() {
        assertThat(context.containsBean("filterChain")).isTrue();
        assertThat(context.containsBean("jwtTokenProvider")).isTrue();
        assertThat(context.containsBean("jwtAuthenticationFilter")).isTrue();
        assertThat(context.containsBean("internalAuthenticationFilter")).isTrue();
    }

    @Test
    @DisplayName("Should configure auditing infrastructure")
    void shouldConfigureAuditingBeans() {
        assertThat(context.containsBean("auditorProvider")).isTrue();
    }

    @Test
    @DisplayName("Should configure caching infrastructure")
    void shouldConfigureCachingBeans() {
        assertThat(context.containsBean("cacheManager")).isTrue();
    }

    @Test
    @DisplayName("Should enable JPA auditing")
    void shouldEnableJpaAuditing() {
        String[] beanNames = context.getBeanNamesForType(
                org.springframework.data.auditing.AuditingHandler.class
        );
        assertThat(beanNames).isNotEmpty();
    }

    @Test
    @DisplayName("Should enable Kafka")
    void shouldEnableKafka() {
        assertThat(context.containsBean("kafkaTemplate")).isTrue();
    }

    @Test
    @DisplayName("Should scan shared packages")
    void shouldScanSharedPackages() {
        assertThat(context.containsBean("policyEngine")).isTrue();
        assertThat(context.containsBean("policyValidationFilter")).isTrue();
    }
}


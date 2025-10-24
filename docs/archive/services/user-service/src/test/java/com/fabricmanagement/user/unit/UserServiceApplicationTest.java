package com.fabricmanagement.user.unit;

import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyDecisionAuditRepository;
import com.fabricmanagement.user.UserServiceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
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
 * - Real infrastructure (PostgreSQL + Kafka) works
 * 
 * This is a critical production readiness test.
 * If context fails to load, service cannot start in production.
 * 
 * ⚠️ GOOGLE/NETFLIX STANDARD: Context load test is MANDATORY
 */
@SpringBootTest(
    classes = UserServiceApplication.class,
    properties = "spring.main.allow-bean-definition-overriding=true"
)
@Testcontainers
@DisplayName("UserServiceApplication - Context Load Test")
class UserServiceApplicationTest {
    
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public PolicyDecisionAuditRepository policyDecisionAuditRepository() {
            return mock(PolicyDecisionAuditRepository.class);
        }
    }
    
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_app_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    @SuppressWarnings("deprecation")
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
    
    @DynamicPropertySource
    @SuppressWarnings("deprecation")
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        
        // JWT
        registry.add("jwt.secret", () -> "test-secret-key-for-user-service-minimum-256-bits-required-for-hmac-sha-algorithm");
        registry.add("jwt.expiration", () -> "3600000");
        registry.add("jwt.refresh-expiration", () -> "86400000");
        
        // Internal API Key (prevents circular placeholder reference)
        registry.add("INTERNAL_API_KEY", () -> "test-internal-api-key-for-user-service");
        
        // Feign clients (mock URLs)
        registry.add("feign.client.contact-service.url", () -> "http://localhost:8082");
        registry.add("feign.client.company-service.url", () -> "http://localhost:8083");
    }
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    @DisplayName("Should load Spring Boot application context successfully")
    void shouldLoadApplicationContext() {
        assertThat(context).isNotNull();
    }
    
    @Test
    @DisplayName("Should have UserServiceApplication bean")
    void shouldHaveUserServiceApplicationBean() {
        assertThat(context.getBean(UserServiceApplication.class)).isNotNull();
    }
    
    @Test
    @DisplayName("Should configure security beans")
    void shouldConfigureSecurityBeans() {
        assertThat(context.getBean("filterChain")).isNotNull();
    }
    
    @Test
    @DisplayName("Should configure JPA auditing")
    void shouldConfigureJpaAuditing() {
        assertThat(context.getBean("auditorProvider")).isNotNull();
    }
    
    @Test
    @DisplayName("Should configure caching beans")
    void shouldConfigureCachingBeans() {
        assertThat(context.getBean("cacheManager")).isNotNull();
    }
    
    @Test
    @DisplayName("Should configure Kafka beans")
    void shouldEnableKafka() {
        assertThat(context.getBean("kafkaTemplate")).isNotNull();
    }
    
    @Test
    @DisplayName("Should configure user service beans")
    void shouldConfigureUserServiceBeans() {
        assertThat(context.getBean("userService")).isNotNull();
        assertThat(context.getBean("authService")).isNotNull();
        assertThat(context.getBean("tenantOnboardingService")).isNotNull();
    }
    
    @Test
    @DisplayName("Should configure shared infrastructure beans")
    void shouldConfigureSharedInfrastructureBeans() {
        assertThat(context.getBean("jwtTokenProvider")).isNotNull();
        assertThat(context.getBean("jwtAuthenticationFilter")).isNotNull();
    }
    
    @Test
    @DisplayName("Should configure repository beans")
    void shouldConfigureRepositoryBeans() {
        assertThat(context.getBean("userRepository")).isNotNull();
        assertThat(context.getBean("outboxEventRepository")).isNotNull();
    }
    
    @Test
    @DisplayName("Should configure Feign clients")
    void shouldConfigureFeignClients() {
        // Verify Feign client interfaces are registered as beans
        // More robust: checks by class type instead of bean name
        assertThat(context.getBean(com.fabricmanagement.user.infrastructure.client.ContactServiceClient.class))
                .isNotNull();
        assertThat(context.getBean(com.fabricmanagement.user.infrastructure.client.CompanyServiceClient.class))
                .isNotNull();
    }
    
    @Test
    @DisplayName("Should configure mappers")
    void shouldConfigureMappers() {
        assertThat(context.getBean("userMapper")).isNotNull();
        assertThat(context.getBean("authMapper")).isNotNull();
        assertThat(context.getBean("userEventMapper")).isNotNull();
    }
}

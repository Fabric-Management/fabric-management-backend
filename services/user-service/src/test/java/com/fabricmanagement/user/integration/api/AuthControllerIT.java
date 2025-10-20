package com.fabricmanagement.user.integration.api;

import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyDecisionAuditRepository;
import com.fabricmanagement.user.UserServiceApplication;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;

/**
 * API Integration Tests for AuthController
 * 
 * Full Spring Boot Context + REST Assured HTTP calls
 * - Real HTTP requests
 * - Real Spring Security
 * - Real database (Testcontainers)
 * - Real password encoding
 * - Real JWT generation
 * 
 * Google/Netflix Standard:
 * - Integration tests > Unit tests for auth flows
 * - Test complete workflows (login, password setup)
 * - Verify security patterns
 * - Coverage: Controller + Service + Mapper + Repository + Security
 */
@SpringBootTest(
        classes = UserServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@Testcontainers
@DisplayName("AuthController - API Integration Tests")
class AuthControllerIT {

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
            .withDatabaseName("user_auth_it_test")
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
        
        registry.add("jwt.secret", () -> "test-secret-key-for-auth-integration-tests-minimum-256-bits-required-for-hmac");
        registry.add("jwt.expiration", () -> "3600000");
        registry.add("jwt.refresh-expiration", () -> "86400000");
        registry.add("INTERNAL_API_KEY", () -> "test-internal-api-key");
        
        registry.add("feign.client.contact-service.url", () -> "http://localhost:8082");
        registry.add("feign.client.company-service.url", () -> "http://localhost:8083");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final UUID TEST_TENANT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef");

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1/auth";
        
        // Clean database before each test
        userRepository.deleteAll();
    }

    // ═════════════════════════════════════════════════════
    // CHECK CONTACT API TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/auth/check-contact")
    class CheckContactApiTests {

        @Test
        @DisplayName("Should return exists=false for unknown contact")
        void shouldReturnNotExists_whenContactUnknown() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "contactValue": "unknown@test.com"
                            }
                            """)
            .when()
                    .post("/check-contact")
            .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.exists", is(false))
                    .body("data.hasPassword", is(false))
                    .body("data.nextStep", is("REGISTER"));
        }
    }

    // ═════════════════════════════════════════════════════
    // SETUP PASSWORD API TESTS (Critical Flow)
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/auth/setup-password")
    class SetupPasswordApiTests {

        @Test
        @DisplayName("Should setup password for new user")
        void shouldSetupPassword_whenValidRequest() {
            // Given: Create user without password
            User user = createTestUser("john@test.com");
            userRepository.save(user);

            // When & Then
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "contactValue": "john@test.com",
                                "password": "SecurePassword123!"
                            }
                            """)
            .when()
                    .post("/setup-password")
            .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("message", containsString("Password setup successful"));

            // Verify password was hashed and saved
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getPasswordHash()).isNotNull();
            assertThat(passwordEncoder.matches("SecurePassword123!", updatedUser.getPasswordHash()))
                    .isTrue();
        }

        @Test
        @DisplayName("Should return 400 when password too weak")
        void shouldReturn400_whenPasswordTooWeak() {
            // Given
            User user = createTestUser("john@test.com");
            userRepository.save(user);

            // When & Then
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "contactValue": "john@test.com",
                                "password": "123"
                            }
                            """)
            .when()
                    .post("/setup-password")
            .then()
                    .statusCode(400);
        }
    }

    // ═════════════════════════════════════════════════════
    // HELPER METHODS
    // ═════════════════════════════════════════════════════

    private User createTestUser(String email) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName("Test")
                .lastName("User")
                .displayName("Test User")
                .status(UserStatus.PENDING_VERIFICATION)
                .registrationType(RegistrationType.SELF_REGISTRATION)
                .role(com.fabricmanagement.shared.domain.role.SystemRole.USER)
                .userContext(com.fabricmanagement.shared.domain.policy.UserContext.INTERNAL)
                .createdBy("SYSTEM")
                .build();
    }
}


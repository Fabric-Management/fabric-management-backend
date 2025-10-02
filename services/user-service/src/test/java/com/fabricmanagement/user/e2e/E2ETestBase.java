package com.fabricmanagement.user.e2e;

import com.fabricmanagement.user.UserServiceApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for E2E tests.
 * 
 * Provides:
 * - In-Memory H2 Database (fast, no Docker required)
 * - Full Spring Boot context
 * - REST Assured configuration
 * - Common test utilities
 * 
 * Note: For production-like testing with real PostgreSQL/Kafka,
 * use TestContainers by setting spring.profiles.active=e2e-containers
 * 
 * Usage:
 * <pre>
 * {@code
 * @Test
 * void testUserRegistration() {
 *     given()
 *         .contentType(ContentType.JSON)
 *         .body(request)
 *     .when()
 *         .post("/api/v1/users")
 *     .then()
 *         .statusCode(201);
 * }
 * }
 * </pre>
 */
@SpringBootTest(
    classes = UserServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
public abstract class E2ETestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Configure H2 in-memory database (no Docker required)
     * 
     * Note: If you want to use TestContainers with real PostgreSQL/Kafka:
     * 1. Uncomment @Testcontainers and @Container annotations
     * 2. Start Docker Desktop
     * 3. Run tests
     */
    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        // H2 In-Memory Database (no Docker required)
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        
        // Flyway (works with H2)
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "false");
        
        // Disable Kafka for tests (or use embedded Kafka)
        registry.add("spring.kafka.enabled", () -> "false");
    }
    
    /* 
     * OPTIONAL: Uncomment below for TestContainers (requires Docker)
     * 
     * @Container
     * protected static final PostgreSQLContainer<?> postgresContainer = 
     *     new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
     *         .withDatabaseName("user_service_test")
     *         .withUsername("test")
     *         .withPassword("test")
     *         .withReuse(true);
     * 
     * @Container
     * protected static final KafkaContainer kafkaContainer = 
     *     new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
     *         .withReuse(true);
     * 
     * @DynamicPropertySource
     * static void configureTestContainers(DynamicPropertyRegistry registry) {
     *     registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
     *     registry.add("spring.datasource.username", postgresContainer::getUsername);
     *     registry.add("spring.datasource.password", postgresContainer::getPassword);
     *     registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
     * }
     */

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    /**
     * Create a base request specification with common headers
     */
    protected RequestSpecification given() {
        return RestAssured.given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON);
    }

    /**
     * Create an authenticated request with JWT token
     */
    protected RequestSpecification givenAuthenticated(String token) {
        return given()
            .header("Authorization", "Bearer " + token);
    }

    /**
     * Create an authenticated request with tenant context
     */
    protected RequestSpecification givenAuthenticatedWithTenant(String token, String tenantId) {
        return givenAuthenticated(token)
            .header("X-Tenant-Id", tenantId);
    }

    // Common test data constants
    protected static final String TEST_TENANT_ID = "tenant-test-001";
    protected static final String TEST_EMAIL = "test@example.com";
    protected static final String TEST_PHONE = "+905551234567";
    protected static final String TEST_PASSWORD = "SecurePass123!";
    protected static final String TEST_FIRST_NAME = "John";
    protected static final String TEST_LAST_NAME = "Doe";
}


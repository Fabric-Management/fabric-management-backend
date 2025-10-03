package com.fabricmanagement.user.e2e;

import com.fabricmanagement.user.UserServiceApplication;
import com.fabricmanagement.user.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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
@Import(TestSecurityConfig.class)
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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        
        // Disable Flyway for E2E tests - use JPA ddl-auto instead
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        
        // Disable Kafka for tests
        registry.add("spring.kafka.enabled", () -> "false");
        
        // Mock environment variables
        registry.add("POSTGRES_HOST", () -> "localhost");
        registry.add("POSTGRES_PORT", () -> "5432");
        registry.add("POSTGRES_DB", () -> "testdb");
        registry.add("POSTGRES_USER", () -> "sa");
        registry.add("POSTGRES_PASSWORD", () -> "");
        registry.add("REDIS_HOST", () -> "localhost");
        registry.add("REDIS_PORT", () -> "6379");
        registry.add("KAFKA_BOOTSTRAP_SERVERS", () -> "localhost:9092");
        registry.add("JWT_SECRET", () -> "test-secret-key-for-testing-only-minimum-256-bits-required-here");
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
     *
     * Note: Authentication is bypassed for testing purposes
     * In production, all endpoints require authentication
     */
    protected RequestSpecification given() {
        return RestAssured.given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .header("X-Tenant-Id", TEST_TENANT_ID); // Mock tenant context for tests
    }

    // Common test data constants
    protected static final String TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440000";
    protected static final String TEST_EMAIL = "test@example.com";
    protected static final String TEST_PHONE = "+905551234567";
    protected static final String TEST_PASSWORD = "SecurePass123!";
    protected static final String TEST_FIRST_NAME = "John";
    protected static final String TEST_LAST_NAME = "Doe";
}


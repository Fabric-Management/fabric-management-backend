package com.fabricmanagement.company.e2e;

import com.fabricmanagement.company.CompanyServiceApplication;
import com.fabricmanagement.company.config.TestSecurityConfig;
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
 */
@SpringBootTest(
    classes = CompanyServiceApplication.class,
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
     */
    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        // H2 In-Memory Database (no Docker required)
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");

        // Flyway (disabled for tests)
        registry.add("spring.flyway.enabled", () -> "false");

        // Disable Kafka for tests
        registry.add("spring.kafka.enabled", () -> "false");

        // Disable cache for tests
        registry.add("spring.cache.type", () -> "none");
    }

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
    protected static final String TEST_COMPANY_NAME = "Test Company Inc";
    protected static final String TEST_LEGAL_NAME = "Test Company Incorporated";
}

package com.fabricmanagement.fiber.integration.api;

import com.fabricmanagement.fiber.FiberServiceApplication;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * API Integration Tests for FiberController
 *
 * Full Spring Boot Context + REST Assured HTTP calls
 * - Real HTTP requests (not MockMvc)
 * - Real Spring Security
 * - Real validation
 * - Real database (Testcontainers)
 *
 * Google/Netflix Standard:
 * - Test like production (real HTTP)
 * - Verify API contracts
 * - Check status codes, headers, response format
 * - API documentation accuracy
 */
@SpringBootTest(
        classes = FiberServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
@DisplayName("FiberController - API Integration Tests")
class FiberControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fiber_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1/fibers";
    }

    // ═════════════════════════════════════════════════════
    // CREATE FIBER API TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/fibers - Create Fiber")
    class CreateFiberApiTests {

        @Test
        @DisplayName("Should create pure fiber with valid request")
        void shouldCreatePureFiber_withValidRequest() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "code": "TEST-001",
                                "name": "Test Fiber",
                                "category": "NATURAL",
                                "originType": "UNKNOWN",
                                "sustainabilityType": "CONVENTIONAL",
                                "reusable": true
                            }
                            """)
            .when()
                    .post()
            .then()
                    .statusCode(201)
                    .contentType(ContentType.JSON)
                    .body("success", is(true))
                    .body("message", containsString("created successfully"))
                    .body("data", notNullValue())
                    .body("data", matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        }

        @Test
        @DisplayName("Should return 400 when code is missing")
        void shouldReturn400_whenCodeMissing() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "name": "Test Fiber",
                                "category": "NATURAL"
                            }
                            """)
            .when()
                    .post()
            .then()
                    .statusCode(400)
                    .body("success", is(false))
                    .body("errorCode", equalTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 409 when fiber code already exists")
        void shouldReturn409_whenCodeAlreadyExists() {
            // Given - Create first fiber
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "code": "DUP-001",
                                "name": "Duplicate Test",
                                "category": "NATURAL",
                                "originType": "UNKNOWN",
                                "sustainabilityType": "CONVENTIONAL"
                            }
                            """)
            .when()
                    .post()
            .then()
                    .statusCode(201);

            // When - Try to create duplicate
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "code": "DUP-001",
                                "name": "Another Duplicate",
                                "category": "SYNTHETIC",
                                "originType": "UNKNOWN",
                                "sustainabilityType": "CONVENTIONAL"
                            }
                            """)
            .when()
                    .post()
            .then()
                    .statusCode(409)
                    .body("success", is(false))
                    .body("errorCode", equalTo("DUPLICATE_RESOURCE"))
                    .body("message", containsString("already exists"));
        }
    }

    // ═════════════════════════════════════════════════════
    // CREATE BLEND FIBER API TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/fibers/blend - Create Blend Fiber")
    class CreateBlendFiberApiTests {

        @Test
        @DisplayName("Should create blend fiber with valid composition")
        void shouldCreateBlendFiber_withValidComposition() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "code": "BLD-TEST-001",
                                "name": "Test Blend 60/40",
                                "components": [
                                    {
                                        "fiberCode": "CO",
                                        "percentage": 60.00,
                                        "sustainabilityType": "CONVENTIONAL"
                                    },
                                    {
                                        "fiberCode": "PES",
                                        "percentage": 40.00,
                                        "sustainabilityType": "CONVENTIONAL"
                                    }
                                ],
                                "originType": "UNKNOWN",
                                "reusable": true
                            }
                            """)
            .when()
                    .post("/blend")
            .then()
                    .statusCode(201)
                    .body("success", is(true))
                    .body("data", notNullValue());
        }

        @Test
        @DisplayName("Should return 400 when composition total is not 100%")
        void shouldReturn400_whenCompositionTotalNot100() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "code": "BLD-999",
                                "name": "Invalid Blend",
                                "components": [
                                    {
                                        "fiberCode": "CO",
                                        "percentage": 60.00
                                    },
                                    {
                                        "fiberCode": "PES",
                                        "percentage": 30.00
                                    }
                                ],
                                "originType": "MIXED"
                            }
                            """)
            .when()
                    .post("/blend")
            .then()
                    .statusCode(400)
                    .body("success", is(false))
                    .body("errorCode", equalTo("INVALID_COMPOSITION"))
                    .body("message", containsString("100"));
        }
    }

    // ═════════════════════════════════════════════════════
    // GET FIBER API TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/fibers/{id} - Get Fiber")
    class GetFiberApiTests {

        @Test
        @DisplayName("Should return fiber when exists")
        void shouldReturnFiber_whenExists() {
            // Given - Create fiber first
            String fiberId = given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "code": "GET-TEST-001",
                                "name": "Get Test Fiber",
                                "category": "NATURAL",
                                "originType": "UNKNOWN",
                                "sustainabilityType": "CONVENTIONAL"
                            }
                            """)
            .when()
                    .post()
            .then()
                    .statusCode(201)
                    .extract().path("data");

            // When & Then - Get fiber
            given()
                    .pathParam("id", fiberId)
            .when()
                    .get("/{id}")
            .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.id", equalTo(fiberId))
                    .body("data.code", equalTo("GET-TEST-001"))
                    .body("data.name", equalTo("Get Test Fiber"))
                    .body("data.category", equalTo("NATURAL"));
        }

        @Test
        @DisplayName("Should return 404 when fiber not found")
        void shouldReturn404_whenFiberNotFound() {
            // Given
            String nonExistentId = UUID.randomUUID().toString();

            // When & Then
            given()
                    .pathParam("id", nonExistentId)
            .when()
                    .get("/{id}")
            .then()
                    .statusCode(404)
                    .body("success", is(false))
                    .body("errorCode", equalTo("NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 400 when ID is not valid UUID")
        void shouldReturn400_whenIdNotValidUUID() {
            given()
                    .pathParam("id", "invalid-uuid")
            .when()
                    .get("/{id}")
            .then()
                    .statusCode(400);
        }
    }

    // ═════════════════════════════════════════════════════
    // GET DEFAULT FIBERS API TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/fibers/default - Get Default Fibers")
    class GetDefaultFibersApiTests {

        @Test
        @DisplayName("Should return default fibers list")
        void shouldReturnDefaultFibersList() {
            given()
            .when()
                    .get("/default")
            .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data", notNullValue())
                    .body("data.size()", greaterThanOrEqualTo(9));  // 9 default fibers seeded
        }

        @Test
        @DisplayName("Should return fibers with isDefault=true flag")
        void shouldReturnFibers_withDefaultFlag() {
            given()
            .when()
                    .get("/default")
            .then()
                    .statusCode(200)
                    .body("data[0].isDefault", is(true))
                    .body("data[0].code", notNullValue())
                    .body("data[0].name", notNullValue());
        }
    }

    // ═════════════════════════════════════════════════════
    // SEARCH API TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/fibers/search - Search Fibers")
    class SearchFibersApiTests {

        @Test
        @DisplayName("Should search fibers by query")
        void shouldSearchFibers_byQuery() {
            given()
                    .queryParam("query", "cotton")
            .when()
                    .get("/search")
            .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data", notNullValue());
        }

        @Test
        @DisplayName("Should return empty array when no matches")
        void shouldReturnEmptyArray_whenNoMatches() {
            given()
                    .queryParam("query", "NONEXISTENT_FIBER_XYZ")
            .when()
                    .get("/search")
            .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data", hasSize(0));
        }
    }
}


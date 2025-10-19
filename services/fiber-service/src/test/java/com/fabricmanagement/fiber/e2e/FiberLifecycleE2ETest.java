package com.fabricmanagement.fiber.e2e;

import com.fabricmanagement.fiber.FiberServiceApplication;
import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyDecisionAuditRepository;
import com.fabricmanagement.shared.security.jwt.JwtTokenProvider;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * End-to-End Tests for Fiber Service
 *
 * Complete User Journeys:
 * - Full Spring Boot application
 * - Real PostgreSQL (Testcontainers)
 * - Real Kafka (Testcontainers)
 * - Real Redis
 * - HTTP calls via REST Assured
 *
 * Amazon/Netflix Standard:
 * - Test complete workflows
 * - Verify cross-component integration
 * - Validate business scenarios
 * - Ensure production readiness
 *
 * Runtime: ~30-60 seconds (full infrastructure startup)
 * Purpose: Verify complete user journeys work end-to-end
 */
@SpringBootTest(
        classes = FiberServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
@DisplayName("Fiber Service E2E Tests - Complete User Journeys")
class FiberLifecycleE2ETest {

    @MockBean
    private PolicyDecisionAuditRepository policyDecisionAuditRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fiber_e2e_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        
        // JWT config for E2E tests (shared-security dependency)
        registry.add("jwt.secret", () -> "test-secret-key-for-fiber-service-minimum-256-bits-required-for-hmac-sha-algorithm");
        registry.add("jwt.expiration", () -> "3600000");
        registry.add("jwt.refresh-expiration", () -> "86400000");
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1/fibers";
        
        // Generate valid JWT token for E2E tests
        UUID testUserId = UUID.randomUUID();
        UUID testTenantId = UUID.randomUUID();
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "TENANT_ADMIN");
        claims.put("email", "test@fabric.com");
        
        String validToken = jwtTokenProvider.generateToken(
            testUserId.toString(),
            testTenantId.toString(),
            claims
        );
        
        // Configure default Authorization header for all requests
        RestAssured.requestSpecification = new RequestSpecBuilder()
            .addHeader("Authorization", "Bearer " + validToken)
            .build();
    }

    // ═════════════════════════════════════════════════════
    // COMPLETE FIBER LIFECYCLE
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Complete fiber lifecycle: Create → Read → Update → Search → Deactivate")
    void shouldCompleteFullFiberLifecycle() {
        
        // ═════════════════════════════════════════════════════
        // STEP 1: Create Pure Fiber
        // ═════════════════════════════════════════════════════
        
        String fiberId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "E2E-CO-001",
                            "name": "E2E Test Cotton",
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
                .body("success", is(true))
                .body("data", notNullValue())
                .extract().path("data");

        // ═════════════════════════════════════════════════════
        // STEP 2: Read Created Fiber
        // ═════════════════════════════════════════════════════
        
        given()
                .pathParam("id", fiberId)
        .when()
                .get("/{id}")
        .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.id", equalTo(fiberId))
                .body("data.code", equalTo("E2E-CO-001"))
                .body("data.name", equalTo("E2E Test Cotton"))
                .body("data.category", equalTo("NATURAL"))
                .body("data.status", equalTo("ACTIVE"));

        // ═════════════════════════════════════════════════════
        // STEP 3: Update Fiber Properties
        // ═════════════════════════════════════════════════════
        
        given()
                .pathParam("id", fiberId)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "sustainabilityType": "ORGANIC",
                            "stapleLength": 35.5,
                            "fineness": 1.8,
                            "tenacity": 2.9,
                            "moistureRegain": 8.0,
                            "color": "RawWhite"
                        }
                        """)
        .when()
                .patch("/{id}")
        .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("updated successfully"));

        // ═════════════════════════════════════════════════════
        // STEP 4: Verify Update Persisted
        // ═════════════════════════════════════════════════════
        
        given()
                .pathParam("id", fiberId)
        .when()
                .get("/{id}")
        .then()
                .statusCode(200)
                .body("data.sustainabilityType", equalTo("ORGANIC"))
                .body("data.property.stapleLength", equalTo(35.5f))
                .body("data.property.fineness", equalTo(1.8f))
                .body("data.property.color", equalTo("RawWhite"));

        // ═════════════════════════════════════════════════════
        // STEP 5: Search Should Find Updated Fiber
        // ═════════════════════════════════════════════════════
        
        given()
                .queryParam("query", "E2E")
        .when()
                .get("/search")
        .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", hasSize(greaterThanOrEqualTo(1)))
                .body("data[0].code", containsString("E2E"));

        // ═════════════════════════════════════════════════════
        // STEP 6: Deactivate Fiber
        // ═════════════════════════════════════════════════════
        
        given()
                .pathParam("id", fiberId)
        .when()
                .delete("/{id}")
        .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", containsString("deactivated successfully"));

        // ═════════════════════════════════════════════════════
        // STEP 7: Verify Deactivation
        // ═════════════════════════════════════════════════════
        
        given()
                .pathParam("id", fiberId)
        .when()
                .get("/{id}")
        .then()
                .statusCode(200)
                .body("data.status", equalTo("INACTIVE"));
    }

    // ═════════════════════════════════════════════════════
    // BLEND FIBER E2E TEST
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should create blend fiber from existing default fibers")
    void shouldCreateBlendFiber_fromExistingDefaults() {
        
        // ═════════════════════════════════════════════════════
        // STEP 1: Verify Default Fibers Exist
        // ═════════════════════════════════════════════════════
        
        given()
        .when()
                .get("/default")
        .then()
                .statusCode(200)
                .body("data.size()", greaterThan(5))
                .body("data.code", hasItems("CO", "PES"));

        // ═════════════════════════════════════════════════════
        // STEP 2: Create Blend from Defaults (CO + PE)
        // ═════════════════════════════════════════════════════
        
        String blendId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "BLD-001",
                            "name": "E2E Cotton/Polyester 60/40",
                            "components": [
                                {
                                    "fiberCode": "CO",
                                    "percentage": 60.00,
                                    "sustainabilityType": "ORGANIC"
                                },
                                {
                                    "fiberCode": "PES",
                                    "percentage": 40.00,
                                    "sustainabilityType": "RECYCLED"
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
                .extract().path("data");

        // ═════════════════════════════════════════════════════
        // STEP 3: Verify Blend Composition
        // ═════════════════════════════════════════════════════
        
        given()
                .pathParam("id", blendId)
        .when()
                .get("/{id}")
        .then()
                .statusCode(200)
                .body("data.code", equalTo("BLD-001"))
                .body("data.compositionType", equalTo("BLEND"))
                .body("data.components.size()", equalTo(2))
                .body("data.components[0].fiberCode", equalTo("CO"))
                .body("data.components[0].percentage", equalTo(60.0f))
                .body("data.components[0].sustainabilityType", equalTo("ORGANIC"))
                .body("data.components[1].fiberCode", equalTo("PES"))
                .body("data.components[1].percentage", equalTo(40.0f))
                .body("data.components[1].sustainabilityType", equalTo("RECYCLED"));

        // ═════════════════════════════════════════════════════
        // STEP 4: Search Should Find Blend
        // ═════════════════════════════════════════════════════
        
        given()
                .queryParam("query", "BLD-001")
        .when()
                .get("/search")
        .then()
                .statusCode(200)
                .body("data.size()", greaterThanOrEqualTo(1))
                .body("data.code", hasItem("BLD-001"));

        // ═════════════════════════════════════════════════════
        // STEP 5: Filter by Category (BLEND)
        // ═════════════════════════════════════════════════════
        
        given()
                .pathParam("category", "BLEND")
        .when()
                .get("/category/{category}")
        .then()
                .statusCode(200)
                .body("data.size()", greaterThanOrEqualTo(1));
    }

    // ═════════════════════════════════════════════════════
    // VALIDATION E2E TEST
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should reject invalid blend composition with proper error messages")
    void shouldRejectInvalidBlend_withProperErrorMessages() {
        
        // ═════════════════════════════════════════════════════
        // TEST 1: Composition Total Not 100%
        // ═════════════════════════════════════════════════════
        
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "BLD-100",
                            "name": "Invalid Blend",
                            "components": [
                                {"fiberCode": "CO", "percentage": 60.00},
                                {"fiberCode": "PES", "percentage": 30.00}
                            ],
                            "originType": "UNKNOWN"
                        }
                        """)
        .when()
                .post("/blend")
        .then()
                .statusCode(400)
                .body("success", is(false))
                .body("errorCode", equalTo("VALIDATION_ERROR"));

        // ═════════════════════════════════════════════════════
        // TEST 2: Non-existent Component Fiber
        // ═════════════════════════════════════════════════════
        
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "BLD-101",
                            "name": "Invalid Fiber Code",
                            "components": [
                                {"fiberCode": "CO", "percentage": 60.00},
                                {"fiberCode": "NONEXISTENT", "percentage": 40.00}
                            ],
                            "originType": "UNKNOWN"
                        }
                        """)
        .when()
                .post("/blend")
        .then()
                .statusCode(404)
                .body("success", is(false))
                .body("errorCode", equalTo("NOT_FOUND"));

        // ═════════════════════════════════════════════════════
        // TEST 3: Less Than 2 Components
        // ═════════════════════════════════════════════════════
        
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "BLD-102",
                            "name": "Single Component",
                            "components": [
                                {"fiberCode": "CO", "percentage": 100.00}
                            ],
                            "originType": "UNKNOWN"
                        }
                        """)
        .when()
                .post("/blend")
        .then()
                .statusCode(400)
                .body("success", is(false))
                .body("errorCode", equalTo("VALIDATION_ERROR"));
    }

    // ═════════════════════════════════════════════════════
    // DEFAULT FIBERS SEEDING E2E TEST
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should have default fibers seeded on startup")
    void shouldHaveDefaultFibersSeeded_onStartup() {
        
        // ═════════════════════════════════════════════════════
        // STEP 1: Get Default Fibers
        // ═════════════════════════════════════════════════════
        
        given()
        .when()
                .get("/default")
        .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.size()", greaterThanOrEqualTo(15))
                .body("data.code", hasItems("CO", "PES", "WO", "SI", "LI", "PA", "VI", "AC", "MD"))
                .body("data[0].isDefault", is(true))
                .body("data[0].status", equalTo("ACTIVE"));

        // ═════════════════════════════════════════════════════
        // STEP 2: Verify Default Fibers are Immutable
        // ═════════════════════════════════════════════════════
        
        String cottonId = given()
                .queryParam("query", "CO")
        .when()
                .get("/search")
        .then()
                .statusCode(200)
                .body("data[0].isDefault", is(true))
                .extract().path("data[0].id");

        // Try to update default fiber (should fail)
        given()
                .pathParam("id", cottonId)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "stapleLength": 35.0,
                            "fineness": 1.5
                        }
                        """)
        .when()
                .patch("/{id}")
        .then()
                .statusCode(403)  // Forbidden - immutable
                .body("success", is(false))
                .body("errorCode", equalTo("FORBIDDEN"))
                .body("message", containsString("Cannot update default fiber"));
    }

    // ═════════════════════════════════════════════════════
    // MULTI-COMPONENT BLEND E2E TEST
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should create complex 3-component blend successfully")
    void shouldCreateThreeComponentBlend() {
        
        String blendId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "BLD-002",
                            "name": "Cotton/Polyester/Wool 50/30/20",
                            "components": [
                                {"fiberCode": "CO", "percentage": 50.00, "sustainabilityType": "ORGANIC"},
                                {"fiberCode": "PES", "percentage": 30.00, "sustainabilityType": "RECYCLED"},
                                {"fiberCode": "WO", "percentage": 20.00, "sustainabilityType": "CONVENTIONAL"}
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
                .extract().path("data");

        // Verify composition
        given()
                .pathParam("id", blendId)
        .when()
                .get("/{id}")
        .then()
                .statusCode(200)
                .body("data.components.size()", equalTo(3))
                .body("data.components.fiberCode", hasItems("CO", "PES", "WO"))
                .body("data.components.percentage", hasItems(50.0f, 30.0f, 20.0f));
    }

    // ═════════════════════════════════════════════════════
    // PAGINATION E2E TEST
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should paginate fibers correctly")
    void shouldPaginateFibers() {
        
        // Given - Create multiple fibers
        for (int i = 1; i <= 15; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body(String.format("""
                            {
                                "code": "PAGE-TEST-%03d",
                                "name": "Pagination Test Fiber %d",
                                "category": "NATURAL",
                                "originType": "UNKNOWN",
                                "sustainabilityType": "CONVENTIONAL"
                            }
                            """, i, i))
            .when()
                    .post()
            .then()
                    .statusCode(201);
        }

        // When & Then - Get first page (size=10)
        given()
                .queryParam("page", 0)
                .queryParam("size", 10)
        .when()
                .get()
        .then()
                .statusCode(200)
                .body("content.size()", lessThanOrEqualTo(10))
                .body("totalElements", greaterThanOrEqualTo(15))
                .body("totalPages", greaterThanOrEqualTo(2));

        // Get second page
        given()
                .queryParam("page", 1)
                .queryParam("size", 10)
        .when()
                .get()
        .then()
                .statusCode(200)
                .body("content.size()", greaterThanOrEqualTo(5));
    }

    // ═════════════════════════════════════════════════════
    // FILTER & SEARCH E2E TEST
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should filter fibers by multiple criteria")
    void shouldFilterFibers_byMultipleCriteria() {
        
        // Given - Create diverse fibers
        given().contentType(ContentType.JSON).body("""
                {"code": "NAT-001", "name": "Natural Fiber 1", "category": "NATURAL",
                 "originType": "UNKNOWN", "sustainabilityType": "ORGANIC"}
                """).post().then().statusCode(201);

        given().contentType(ContentType.JSON).body("""
                {"code": "SYN-001", "name": "Synthetic Fiber 1", "category": "SYNTHETIC",
                 "originType": "UNKNOWN", "sustainabilityType": "CONVENTIONAL"}
                """).post().then().statusCode(201);

        // When & Then - Filter by category
        given()
                .pathParam("category", "NATURAL")
        .when()
                .get("/category/{category}")
        .then()
                .statusCode(200)
                .body("data.category", everyItem(equalTo("NATURAL")));

        // Filter by status - all fibers are ACTIVE by default
        given()
        .when()
                .get()
        .then()
                .statusCode(200)
                .body("content.status", everyItem(equalTo("ACTIVE")));
    }
}


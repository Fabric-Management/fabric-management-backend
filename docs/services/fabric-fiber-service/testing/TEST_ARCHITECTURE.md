# 🧪 FIBER SERVICE - TEST ARCHITECTURE

## Google/Netflix/Amazon Enterprise-Level Testing Strategy

**Version:** 1.0  
**Date:** 2025-10-19  
**Status:** 🔴 PRODUCTION-READY TEST STANDARD  
**Coverage Goal:** 80%+ (Industry standard)

---

## 🎯 TESTING PHILOSOPHY

```
╔══════════════════════════════════════════════════════════════════╗
║                                                                  ║
║  "If you wouldn't stake your bank account on this code,          ║
║   you haven't tested it enough."                                 ║
║                                                                  ║
║  - Google SRE Handbook                                           ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝
```

**Principles:**

- ✅ Test FIRST (TDD - write tests before implementation)
- ✅ Fast feedback (unit tests < 100ms)
- ✅ Real infrastructure (Testcontainers for integration)
- ✅ Production parity (test like production)
- ✅ Flaky test = Zero tolerance
- ✅ Coverage ≥ 80% (Google standard)

---

## 📊 TEST PYRAMID

```
                     ┌─────────────┐
                     │  E2E Tests  │  ← 5% (Full flow, slow)
                     │   (hours)   │
                ┌────┴─────────────┴────┐
                │  Integration Tests     │  ← 15% (Real DB/Kafka, medium)
                │     (minutes)          │
           ┌────┴────────────────────────┴────┐
           │       Unit Tests                 │  ← 80% (Mocked, fast)
           │         (seconds)                │
           └──────────────────────────────────┘

Total Tests: ~200+
Unit: ~160 tests (80%)
Integration: ~30 tests (15%)
E2E: ~10 tests (5%)
```

---

## 🏗️ TEST STRUCTURE

```
fiber-service/src/test/
├── java/com/fabricmanagement/fiber/
│   ├── unit/                          # Unit Tests (80%)
│   │   ├── service/
│   │   │   ├── FiberServiceTest.java
│   │   │   └── FiberSeederTest.java
│   │   ├── mapper/
│   │   │   ├── FiberMapperTest.java
│   │   │   └── FiberEventMapperTest.java
│   │   ├── domain/
│   │   │   └── FiberValidationTest.java
│   │   └── valueobject/
│   │       └── FiberCompositionTest.java
│   │
│   ├── integration/                   # Integration Tests (15%)
│   │   ├── repository/
│   │   │   └── FiberRepositoryIT.java
│   │   ├── api/
│   │   │   └── FiberControllerIT.java
│   │   ├── messaging/
│   │   │   └── FiberEventPublisherIT.java
│   │   └── cache/
│   │       └── FiberCacheIT.java
│   │
│   ├── e2e/                           # E2E Tests (5%)
│   │   ├── FiberLifecycleE2ETest.java
│   │   ├── BlendCreationE2ETest.java
│   │   └── YarnIntegrationE2ETest.java
│   │
│   ├── testconfig/                    # Test Infrastructure
│   │   ├── TestContainersConfig.java
│   │   ├── TestSecurityConfig.java
│   │   └── TestKafkaConfig.java
│   │
│   └── fixtures/                      # Test Data Builders
│       ├── FiberFixtures.java
│       ├── FiberRequestFixtures.java
│       └── FiberEventFixtures.java
│
└── resources/
    ├── application-test.yml
    └── db/
        └── test-data.sql
```

---

## 🧪 UNIT TESTS (80% - Google Style)

### Test Naming Convention

```
Format: should{ExpectedBehavior}_when{Condition}

Examples:
✅ shouldCreatePureFiber_whenValidRequest()
✅ shouldThrowException_whenCompositionTotalNot100()
✅ shouldReturnCachedFiber_whenCalledSecondTime()
✅ shouldPublishEvent_whenFiberCreated()

❌ test1()
❌ testCreateFiber()
❌ fiberCreationTest()
```

### Example: FiberServiceTest.java (TDD - Write FIRST!)

```java
package com.fabricmanagement.fiber.unit.service;

import com.fabricmanagement.fiber.application.service.FiberService;
import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.infrastructure.repository.FiberRepository;
import com.fabricmanagement.fiber.infrastructure.messaging.FiberEventPublisher;
import com.fabricmanagement.shared.domain.exception.DuplicateResourceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.fabricmanagement.fiber.fixtures.FiberFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for FiberService
 *
 * Testing Strategy:
 * - Fast (< 100ms per test)
 * - Isolated (mocked dependencies)
 * - Focused (single behavior per test)
 * - Readable (Given-When-Then pattern)
 *
 * Coverage Goal: 95%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FiberService Unit Tests")
class FiberServiceTest {

    @Mock
    private FiberRepository fiberRepository;

    @Mock
    private FiberEventPublisher eventPublisher;

    @InjectMocks
    private FiberService fiberService;

    // ═════════════════════════════════════════════════════
    // CREATE PURE FIBER TESTS
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should create pure fiber when valid request provided")
    void shouldCreatePureFiber_whenValidRequest() {
        // Given
        var request = createPureFiberRequest("CO", "Cotton");
        var expectedFiber = createPureFiber("CO", "Cotton");

        when(fiberRepository.existsByCode("CO")).thenReturn(false);
        when(fiberRepository.save(any(Fiber.class))).thenReturn(expectedFiber);

        // When
        UUID fiberId = fiberService.createFiber(request);

        // Then
        assertThat(fiberId).isNotNull();
        assertThat(fiberId).isEqualTo(expectedFiber.getId());

        verify(fiberRepository).existsByCode("CO");
        verify(fiberRepository).save(any(Fiber.class));
        verify(eventPublisher).publishFiberDefined(any());
    }

    @Test
    @DisplayName("Should throw exception when fiber code already exists")
    void shouldThrowDuplicateException_whenCodeAlreadyExists() {
        // Given
        var request = createPureFiberRequest("CO", "Cotton");
        when(fiberRepository.existsByCode("CO")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> fiberService.createFiber(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Fiber code already exists");

        verify(fiberRepository, never()).save(any());
        verify(eventPublisher, never()).publishFiberDefined(any());
    }

    // ═════════════════════════════════════════════════════
    // CREATE BLEND FIBER TESTS
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should create blend fiber when composition total is 100%")
    void shouldCreateBlendFiber_whenCompositionValid() {
        // Given
        var request = createBlendFiberRequest("BLD-001", "CO/PE 60/40");
        var components = createComponentList(
                component("CO", 60.0),
                component("PE", 40.0)
        );

        when(fiberRepository.existsByCode("BLD-001")).thenReturn(false);
        when(fiberRepository.existsByCodeAndStatus("CO", ACTIVE)).thenReturn(true);
        when(fiberRepository.existsByCodeAndStatus("PE", ACTIVE)).thenReturn(true);
        when(fiberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        UUID fiberId = fiberService.createBlendFiber(request);

        // Then
        assertThat(fiberId).isNotNull();
        verify(fiberRepository).save(argThat(fiber ->
                fiber.getCompositionType() == CompositionType.BLEND &&
                fiber.getComponents().size() == 2
        ));
        verify(eventPublisher).publishFiberDefined(any());
    }

    @Test
    @DisplayName("Should throw exception when composition total is not 100%")
    void shouldThrowException_whenCompositionTotalNot100() {
        // Given
        var request = createBlendFiberRequest("BLD-002", "Invalid");
        request.setComponents(createComponentList(
                component("CO", 60.0),
                component("PE", 30.0)  // Total = 90% ❌
        ));

        // When & Then
        assertThatThrownBy(() -> fiberService.createBlendFiber(request))
                .isInstanceOf(InvalidCompositionException.class)
                .hasMessageContaining("Total percentage must equal 100");

        verify(fiberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when component fiber not found")
    void shouldThrowException_whenComponentFiberNotFound() {
        // Given
        var request = createBlendFiberRequest("BLD-003", "Invalid");
        when(fiberRepository.existsByCodeAndStatus("CO", ACTIVE)).thenReturn(true);
        when(fiberRepository.existsByCodeAndStatus("XX", ACTIVE)).thenReturn(false);  // Not found

        // When & Then
        assertThatThrownBy(() -> fiberService.createBlendFiber(request))
                .isInstanceOf(FiberNotFoundException.class)
                .hasMessageContaining("Component fiber not found: XX");
    }

    @Test
    @DisplayName("Should throw exception when component fiber is inactive")
    void shouldThrowException_whenComponentFiberInactive() {
        // Given
        var request = createBlendFiberRequest("BLD-004", "Invalid");
        when(fiberRepository.existsByCodeAndStatus("CO", ACTIVE)).thenReturn(false);  // Inactive

        // When & Then
        assertThatThrownBy(() -> fiberService.createBlendFiber(request))
                .isInstanceOf(InactiveFiberException.class)
                .hasMessageContaining("Component fiber is inactive: CO");
    }

    // ═════════════════════════════════════════════════════
    // UPDATE FIBER TESTS
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should update fiber property when valid request")
    void shouldUpdateFiberProperty_whenValidRequest() {
        // Given
        UUID fiberId = UUID.randomUUID();
        var fiber = createPureFiber("CO", "Cotton");
        var request = createUpdatePropertyRequest();

        when(fiberRepository.findById(fiberId)).thenReturn(Optional.of(fiber));
        when(fiberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        fiberService.updateFiberProperty(fiberId, request);

        // Then
        verify(fiberRepository).save(fiber);
        verify(eventPublisher).publishFiberUpdated(any());
    }

    @Test
    @DisplayName("Should throw exception when updating default fiber")
    void shouldThrowException_whenUpdatingDefaultFiber() {
        // Given
        UUID fiberId = UUID.randomUUID();
        var defaultFiber = createDefaultFiber("CO");  // isDefault=TRUE
        var request = createUpdatePropertyRequest();

        when(fiberRepository.findById(fiberId)).thenReturn(Optional.of(defaultFiber));

        // When & Then
        assertThatThrownBy(() -> fiberService.updateFiberProperty(fiberId, request))
                .isInstanceOf(ImmutableFiberException.class)
                .hasMessageContaining("Cannot update default fiber");

        verify(fiberRepository, never()).save(any());
    }

    // ═════════════════════════════════════════════════════
    // CACHE TESTS
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should return cached fiber on second call")
    void shouldReturnCachedFiber_whenCalledTwice() {
        // Given
        UUID fiberId = UUID.randomUUID();
        var fiber = createPureFiber("CO", "Cotton");

        when(fiberRepository.findById(fiberId)).thenReturn(Optional.of(fiber));

        // When
        fiberService.getFiber(fiberId);  // First call
        fiberService.getFiber(fiberId);  // Second call (should use cache)

        // Then
        verify(fiberRepository, times(1)).findById(fiberId);  // Called once only
    }

    // ═════════════════════════════════════════════════════
    // EVENT PUBLISHING TESTS
    // ═════════════════════════════════════════════════════

    @Test
    @DisplayName("Should publish FiberDefined event when fiber created")
    void shouldPublishFiberDefinedEvent_whenFiberCreated() {
        // Given
        var request = createPureFiberRequest("PE", "Polyester");
        var fiber = createPureFiber("PE", "Polyester");

        when(fiberRepository.existsByCode("PE")).thenReturn(false);
        when(fiberRepository.save(any())).thenReturn(fiber);

        // When
        fiberService.createFiber(request);

        // Then
        verify(eventPublisher).publishFiberDefined(argThat(event ->
                event.getFiberId().equals(fiber.getId()) &&
                event.getCode().equals("PE") &&
                event.getCategory() == FiberCategory.SYNTHETIC
        ));
    }

    @Test
    @DisplayName("Should NOT publish event when fiber creation fails")
    void shouldNotPublishEvent_whenCreationFails() {
        // Given
        var request = createPureFiberRequest("CO", "Cotton");
        when(fiberRepository.existsByCode("CO")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> fiberService.createFiber(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(eventPublisher, never()).publishFiberDefined(any());
    }
}
```

---

## 🔬 INTEGRATION TESTS (15% - Netflix Style)

### Testcontainers - Real Infrastructure

```java
package com.fabricmanagement.fiber.integration.repository;

import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.infrastructure.repository.FiberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration Tests for FiberRepository
 *
 * Uses Testcontainers for REAL PostgreSQL instance
 * - No mocks, real database queries
 * - Production parity
 * - Tests actual SQL, constraints, triggers
 *
 * Runtime: ~5 seconds (container startup cached)
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("FiberRepository Integration Tests")
class FiberRepositoryIT {

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

    @Autowired
    private FiberRepository fiberRepository;

    @Test
    @DisplayName("Should save and retrieve fiber with UUID")
    void shouldSaveAndRetrieveFiber_withUUID() {
        // Given
        Fiber fiber = Fiber.builder()
                .code("CO")
                .name("Cotton")
                .category(FiberCategory.NATURAL)
                .compositionType(CompositionType.PURE)
                .status(FiberStatus.ACTIVE)
                .build();

        // When
        Fiber saved = fiberRepository.save(fiber);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isInstanceOf(UUID.class);

        Fiber retrieved = fiberRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getCode()).isEqualTo("CO");
        assertThat(retrieved.getCategory()).isEqualTo(FiberCategory.NATURAL);
    }

    @Test
    @DisplayName("Should enforce unique code constraint")
    void shouldEnforceUniqueCodeConstraint() {
        // Given
        fiberRepository.save(createFiber("CO", "Cotton"));

        // When & Then
        assertThatThrownBy(() -> fiberRepository.save(createFiber("CO", "Duplicate")))
                .hasCauseInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("Should update updated_at trigger automatically")
    void shouldUpdateUpdatedAtTrigger_whenFiberUpdated() throws Exception {
        // Given
        Fiber fiber = fiberRepository.save(createFiber("PE", "Polyester"));
        var originalUpdatedAt = fiber.getUpdatedAt();

        Thread.sleep(1000);  // Wait for timestamp difference

        // When
        fiber.setName("Polyester Updated");
        Fiber updated = fiberRepository.save(fiber);

        // Then
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("Should find fibers by category with index performance")
    void shouldFindByCategoryWithIndex() {
        // Given
        fiberRepository.save(createFiber("CO", "Cotton", FiberCategory.NATURAL));
        fiberRepository.save(createFiber("PE", "Polyester", FiberCategory.SYNTHETIC));
        fiberRepository.save(createFiber("WO", "Wool", FiberCategory.NATURAL));

        // When
        var naturalFibers = fiberRepository.findByCategory(FiberCategory.NATURAL);

        // Then
        assertThat(naturalFibers).hasSize(2);
        assertThat(naturalFibers).extracting(Fiber::getCode)
                .containsExactlyInAnyOrder("CO", "WO");
    }

    @Test
    @DisplayName("Should validate composition total equals 100 in domain logic")
    void shouldValidateCompositionTotal_inDomainLogic() {
        // Given - Blend fiber with invalid total
        var components = List.of(
                createComponent("CO", 60.0),
                createComponent("PE", 30.0)  // Total = 90% ❌
        );

        // When & Then
        assertThatThrownBy(() -> Fiber.validateComposition(components))
                .isInstanceOf(InvalidCompositionException.class)
                .hasMessageContaining("Total percentage must equal 100");
    }
}
```

---

## 🌐 E2E TESTS (5% - Amazon Style)

### Full Flow Testing with Real Services

```java
package com.fabricmanagement.fiber.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * E2E Tests for Fiber Service
 *
 * Full Integration:
 * - Real Spring Boot application
 * - Real PostgreSQL (Testcontainers)
 * - Real Kafka (Testcontainers)
 * - Real Redis
 * - HTTP calls via REST Assured
 *
 * Runtime: ~30 seconds (full startup)
 * Purpose: Verify complete user journeys
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Fiber Service E2E Tests")
class FiberLifecycleE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1/fibers";
    }

    @Test
    @DisplayName("Complete fiber lifecycle: Create → Read → Update → Deactivate")
    void shouldCompleteFullFiberLifecycle() {
        // ═════════════════════════════════════════════════════
        // STEP 1: Create Pure Fiber
        // ═════════════════════════════════════════════════════

        String fiberId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "CO-TEST",
                            "name": "Test Cotton",
                            "category": "NATURAL",
                            "originType": "UNKNOWN",
                            "sustainabilityType": "CONVENTIONAL"
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
        // STEP 2: Read Fiber
        // ═════════════════════════════════════════════════════

        given()
                .pathParam("id", fiberId)
        .when()
                .get("/{id}")
        .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.code", equalTo("CO-TEST"))
                .body("data.category", equalTo("NATURAL"));

        // ═════════════════════════════════════════════════════
        // STEP 3: Update Properties
        // ═════════════════════════════════════════════════════

        given()
                .pathParam("id", fiberId)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "sustainabilityType": "ORGANIC",
                            "stapleLength": 35.5,
                            "fineness": 1.8
                        }
                        """)
        .when()
                .patch("/{id}")
        .then()
                .statusCode(200)
                .body("success", is(true));

        // ═════════════════════════════════════════════════════
        // STEP 4: Verify Update
        // ═════════════════════════════════════════════════════

        given()
                .pathParam("id", fiberId)
        .when()
                .get("/{id}")
        .then()
                .statusCode(200)
                .body("data.sustainabilityType", equalTo("ORGANIC"))
                .body("data.property.stapleLength", equalTo(35.5f));

        // ═════════════════════════════════════════════════════
        // STEP 5: Deactivate
        // ═════════════════════════════════════════════════════

        given()
                .pathParam("id", fiberId)
        .when()
                .delete("/{id}")
        .then()
                .statusCode(200)
                .body("success", is(true));

        // ═════════════════════════════════════════════════════
        // STEP 6: Verify Deactivated
        // ═════════════════════════════════════════════════════

        given()
                .pathParam("id", fiberId)
        .when()
                .get("/{id}")
        .then()
                .statusCode(200)
                .body("data.status", equalTo("INACTIVE"));
    }

    @Test
    @DisplayName("Should create blend fiber from existing pure fibers")
    void shouldCreateBlendFiberFromExistingFibers() {
        // ═════════════════════════════════════════════════════
        // STEP 1: Verify default fibers exist
        // ═════════════════════════════════════════════════════

        given()
        .when()
                .get("/default")
        .then()
                .statusCode(200)
                .body("data.size()", greaterThan(5));

        // ═════════════════════════════════════════════════════
        // STEP 2: Create blend from defaults
        // ═════════════════════════════════════════════════════

        String blendId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "BLD-E2E-001",
                            "name": "Cotton/Polyester 60/40",
                            "components": [
                                {
                                    "fiberCode": "CO",
                                    "percentage": 60.00,
                                    "sustainabilityType": "ORGANIC"
                                },
                                {
                                    "fiberCode": "PE",
                                    "percentage": 40.00,
                                    "sustainabilityType": "RECYCLED"
                                }
                            ],
                            "originType": "MIXED",
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
        // STEP 3: Verify blend composition
        // ═════════════════════════════════════════════════════

        given()
                .pathParam("id", blendId)
        .when()
                .get("/{id}")
        .then()
                .statusCode(200)
                .body("data.compositionType", equalTo("BLEND"))
                .body("data.components.size()", equalTo(2))
                .body("data.components[0].fiberCode", equalTo("CO"))
                .body("data.components[0].percentage", equalTo(60.0f))
                .body("data.components[1].fiberCode", equalTo("PE"))
                .body("data.components[1].percentage", equalTo(40.0f));
    }
}
```

---

## 🎯 TEST FIXTURES (Test Data Builders)

### FiberFixtures.java - Clean Test Data

```java
package com.fabricmanagement.fiber.fixtures;

import com.fabricmanagement.fiber.domain.aggregate.Fiber;
import com.fabricmanagement.fiber.domain.valueobject.*;

import java.util.List;
import java.util.UUID;

/**
 * Test Data Builders for Fiber Domain
 *
 * Pattern: Test Data Builder (Google style)
 * - Readable test data creation
 * - Sensible defaults
 * - Easy customization
 */
public class FiberFixtures {

    public static final UUID GLOBAL_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // ═════════════════════════════════════════════════════
    // PURE FIBER BUILDERS
    // ═════════════════════════════════════════════════════

    public static Fiber createPureFiber(String code, String name) {
        return Fiber.builder()
                .id(UUID.randomUUID())
                .tenantId(GLOBAL_TENANT_ID)
                .code(code)
                .name(name)
                .category(FiberCategory.NATURAL)
                .compositionType(CompositionType.PURE)
                .originType(OriginType.UNKNOWN)
                .sustainabilityType(SustainabilityType.CONVENTIONAL)
                .status(FiberStatus.ACTIVE)
                .isDefault(false)
                .reusable(true)
                .createdBy("TEST_USER")
                .build();
    }

    public static Fiber createDefaultFiber(String code) {
        return createPureFiber(code, code + " Default")
                .toBuilder()
                .isDefault(true)  // Immutable flag
                .createdBy("SYSTEM")
                .build();
    }

    public static Fiber createFiber(String code, String name, FiberCategory category) {
        return createPureFiber(code, name)
                .toBuilder()
                .category(category)
                .build();
    }

    // ═════════════════════════════════════════════════════
    // BLEND FIBER BUILDERS
    // ═════════════════════════════════════════════════════

    public static Fiber createBlendFiber(String code, String name, List<FiberComponent> components) {
        return Fiber.builder()
                .id(UUID.randomUUID())
                .tenantId(GLOBAL_TENANT_ID)
                .code(code)
                .name(name)
                .category(FiberCategory.BLEND)
                .compositionType(CompositionType.BLEND)
                .components(components)
                .originType(OriginType.MIXED)
                .sustainabilityType(SustainabilityType.CONVENTIONAL)
                .status(FiberStatus.ACTIVE)
                .isDefault(false)
                .reusable(true)
                .createdBy("TEST_USER")
                .build();
    }

    // ═════════════════════════════════════════════════════
    // COMPONENT BUILDERS
    // ═════════════════════════════════════════════════════

    public static FiberComponent component(String fiberCode, double percentage) {
        return FiberComponent.builder()
                .fiberCode(fiberCode)
                .percentage(BigDecimal.valueOf(percentage))
                .sustainabilityType(SustainabilityType.CONVENTIONAL)
                .build();
    }

    public static List<FiberComponent> createComponentList(FiberComponent... components) {
        return Arrays.asList(components);
    }

    // ═════════════════════════════════════════════════════
    // PROPERTY BUILDERS
    // ═════════════════════════════════════════════════════

    public static FiberProperty createCottonProperty() {
        return FiberProperty.builder()
                .stapleLength(BigDecimal.valueOf(32.0))  // mm
                .fineness(BigDecimal.valueOf(1.8))       // dtex
                .tenacity(BigDecimal.valueOf(2.8))       // cN/tex
                .moistureRegain(BigDecimal.valueOf(7.5)) // %
                .color("RawWhite")
                .build();
    }
}
```

---

## 📊 TEST COVERAGE REQUIREMENTS

### Google/Netflix Standards

| Layer          | Coverage | Rationale                     |
| -------------- | -------- | ----------------------------- |
| **Service**    | 95%+     | Business logic critical       |
| **Mapper**     | 90%+     | Data transformation critical  |
| **Controller** | 85%+     | API contract critical         |
| **Repository** | 80%+     | Database integration critical |
| **Domain**     | 100%     | Validation rules critical     |

### Coverage Enforcement

```xml
<!-- pom.xml - JaCoCo configuration -->
<execution>
    <id>jacoco-check</id>
    <goals>
        <goal>check</goal>
    </goals>
    <configuration>
        <rules>
            <rule>
                <element>PACKAGE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.80</minimum>  <!-- 80% minimum -->
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

**Build fails if coverage < 80%!** ✅

---

## 🚀 RUNNING TESTS

### Quick Commands

```bash
# Run all tests
mvn test

# Run unit tests only (fast)
mvn test -Dtest=**/*Test

# Run integration tests only
mvn verify -Dtest=**/*IT

# Run E2E tests only
mvn verify -Dtest=**/*E2ETest

# Run with coverage report
mvn clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### CI/CD Pipeline

```yaml
# GitHub Actions / GitLab CI
test:
  stage: test
  script:
    - mvn clean verify
    - mvn jacoco:report
  coverage: "/Total.*?([0-9]{1,3})%/"
  artifacts:
    reports:
      junit: target/surefire-reports/TEST-*.xml
      coverage_report:
        coverage_format: cobertura
        path: target/site/jacoco/jacoco.xml
```

---

## 🎯 TEST QUALITY METRICS

### What Makes Tests "Google/Netflix Level"?

✅ **Fast Feedback**

- Unit tests: < 100ms each
- Total unit suite: < 10 seconds
- Integration: < 30 seconds
- E2E: < 2 minutes

✅ **Deterministic**

- No flaky tests (0% flakiness tolerance)
- No Thread.sleep() in unit tests
- No random data (use fixtures)
- Testcontainers for reproducible integration

✅ **Readable**

- Given-When-Then pattern
- Descriptive test names
- AssertJ fluent assertions
- Self-documenting

✅ **Isolated**

- No shared mutable state
- Each test independent
- Proper setup/teardown
- Parallel execution safe

✅ **Comprehensive**

- Happy path tested
- Edge cases tested
- Error scenarios tested
- Performance verified

---

## 🏆 SUCCESS CRITERIA

```
╔══════════════════════════════════════════════════════════════════╗
║                                                                  ║
║  ✅ ALL TESTS PASSING (0 failures)                              ║
║  ✅ COVERAGE ≥ 80% (enforced)                                   ║
║  ✅ NO FLAKY TESTS (100% deterministic)                         ║
║  ✅ FAST EXECUTION (unit < 10s, all < 2min)                     ║
║  ✅ PRODUCTION PARITY (Testcontainers)                          ║
║                                                                  ║
║  If ANY of these fail → BUILD FAILS                             ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝
```

---

**Test Strategy:** TDD (Test-Driven Development)  
**Coverage Tool:** JaCoCo  
**Assertion Library:** AssertJ  
**Integration:** Testcontainers  
**E2E:** REST Assured

**Quality Bar:** Would you trust this code with your bank account?

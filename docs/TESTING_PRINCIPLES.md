# 🧪 TESTING PRINCIPLES (Global)

Last Updated: 2025-10-20  
Status: ✅ MANDATORY - Apply to all services  
Purpose: Define global testing policy, targets, practices, and documentation standards.

---

## 📖 PHILOSOPHY

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

**Core Principles:**

- ✅ Test FIRST (TDD - write tests before implementation)
- ✅ Fast feedback (unit tests < 100ms each)
- ✅ Real infrastructure (Testcontainers for integration)
- ✅ Production parity (test like production)
- ✅ Flaky test = Zero tolerance
- ✅ Coverage ≥ 80% (enforced by JaCoCo in root pom.xml)

---

## 🎯 QUALITY BAR

- **Minimum coverage:** ≥ 80% (JaCoCo enforced at BUNDLE level in root pom.xml)
- **Layer guidance:**
  - Domain (Validation/Value Objects): 100%
  - Service (Business Logic): ≥ 95%
  - Mapper (DTO ↔ Entity): ≥ 90%
  - Controller (API Contracts): ≥ 85%
  - Repository (DB Integration): ≥ 80%
- **Zero flaky tests:** Tests must be deterministic and isolated
- **Performance targets:**
  - Unit tests: < 100ms each, total suite < 10s
  - Integration tests: < 30s total
  - E2E tests: < 2 minutes total

---

## △ TEST PYRAMID (Target Mix)

```
        E2E Tests   (~5%)  → Full workflows, real infra
       ───────────────────
      Integration (~20%)  → Real DB/Kafka via Testcontainers
     ─────────────────────
    Unit Tests    (~75%)  → Pure logic, fast, isolated
```

---

## ⚙️ TOOLING & FRAMEWORKS

- **JUnit 5:** Test framework
- **AssertJ:** Fluent assertions
- **Mockito:** Unit test doubles
- **Testcontainers:** Real infra (PostgreSQL, Kafka, Redis)
- **REST Assured:** API tests (full HTTP stack)
- **JaCoCo:** Coverage enforcement (configured in root pom.xml)
- **@SpringBootTest:** Full context integration tests
- **@DataJpaTest:** Repository layer tests

---

## 🏗️ STANDARD TEST STRUCTURE

All services MUST follow this standardized structure:

```
{service}/src/test/
├── java/com/fabricmanagement/{service}/
│   ├── unit/                          # Unit Tests (~75%)
│   │   ├── api/
│   │   │   └── {Service}ControllerTest.java
│   │   ├── service/
│   │   │   └── {Service}ServiceTest.java
│   │   ├── mapper/
│   │   │   ├── {Service}MapperTest.java
│   │   │   └── {Service}EventMapperTest.java
│   │   ├── domain/
│   │   │   └── {Service}ValidationTest.java
│   │   └── messaging/
│   │       └── {Service}EventPublisherTest.java
│   │
│   ├── integration/                   # Integration Tests (~20%)
│   │   ├── repository/
│   │   │   └── {Service}RepositoryIT.java
│   │   ├── api/
│   │   │   └── {Service}ControllerIT.java
│   │   ├── messaging/
│   │   │   └── {Service}EventPublisherIT.java
│   │   └── cache/
│   │       └── {Service}CacheIT.java
│   │
│   ├── e2e/                           # E2E Tests (~5%)
│   │   └── {Service}LifecycleE2ETest.java
│   │
│   ├── fixtures/                      # Test Data Builders
│   │   └── {Service}Fixtures.java
│   │
│   └── support/                       # Test Utilities
│       ├── TestSecurityHelper.java
│       └── TestDataGenerator.java
│
└── resources/
    └── application-test.yml (optional, prefer dynamic properties)
```

**Naming Conventions:**

- Unit tests: `*Test.java` (e.g., `FiberServiceTest.java`)
- Integration tests: `*IT.java` (e.g., `FiberRepositoryIT.java`)
- E2E tests: `*E2ETest.java` (e.g., `FiberLifecycleE2ETest.java`)
- Fixtures: `*Fixtures.java` (e.g., `FiberFixtures.java`)

**Test Method Naming:**

```
Format: should{ExpectedBehavior}_when{Condition}

✅ Good Examples:
- shouldCreatePureFiber_whenValidRequest()
- shouldThrowException_whenCompositionTotalNot100()
- shouldReturnCachedFiber_whenCalledSecondTime()
- shouldPublishEvent_whenFiberCreated()

❌ Bad Examples:
- test1()
- testCreateFiber()
- fiberCreationTest()
```

---

## ✅ PRACTICES (Do's)

### Test-Driven Development (TDD)

1. **Write test FIRST** (before implementation)
2. **Test fails initially** (red phase)
3. **Write minimal code** to pass (green phase)
4. **Refactor** (clean up while keeping tests green)
5. **Repeat** for next feature

### AAA Pattern (Arrange-Act-Assert)

```java
@Test
@DisplayName("Should create pure fiber when valid request provided")
void shouldCreatePureFiber_whenValidRequest() {
    // Given (Arrange) - Set up test data
    var request = createPureFiberRequest("CO", "Cotton");
    when(repository.existsByCode("CO")).thenReturn(false);

    // When (Act) - Execute the behavior under test
    UUID fiberId = service.createFiber(request);

    // Then (Assert) - Verify expected outcome
    assertThat(fiberId).isNotNull();
    verify(repository).save(any(Fiber.class));
}
```

### Test Data Builders (Fixtures)

Create reusable, readable test data with sensible defaults:

```java
// ✅ Good - Test Data Builder Pattern
public class FiberFixtures {
    public static Fiber createPureFiber(String code, String name) {
        return Fiber.builder()
                .tenantId(GLOBAL_TENANT_ID)
                .code(code)
                .name(name)
                .category(FiberCategory.NATURAL)
                .status(FiberStatus.ACTIVE)
                // DON'T set .id() - Hibernate manages it!
                .build();
    }
}

// Usage in tests
var fiber = createPureFiber("CO", "Cotton");
```

### Key Practices

- ✅ **Keep unit tests fast:** < 100ms each; full suite < 10s
- ✅ **Use fixtures/builders:** Readable, maintainable test data
- ✅ **Test happy + edge cases:** Both success and failure scenarios
- ✅ **Hermetic tests:** No shared state, no order dependencies
- ✅ **Real infra for integration:** Testcontainers (PostgreSQL, Kafka)
- ✅ **Verify API contracts:** Status codes, headers, payload structure
- ✅ **Use @DisplayName:** Clear test documentation
- ✅ **Parallel execution safe:** Tests can run concurrently

---

## 🚫 ANTI-PATTERNS (Don'ts)

### Critical: Hibernate-Managed Fields

**❌ NEVER manually set fields that Hibernate manages!**

```java
// ❌ WRONG - Manual UUID causes conflicts
public Fiber fromCreateRequest(CreateFiberRequest request) {
    return Fiber.builder()
            .id(UUID.randomUUID())  // ← BUG! Hibernate manages this
            .version(0L)            // ← BUG! Hibernate manages this
            .createdAt(LocalDateTime.now())  // ← BUG! @CreatedDate manages this
            .build();
}

// ✅ CORRECT - Let Hibernate manage lifecycle fields
public Fiber fromCreateRequest(CreateFiberRequest request) {
    return Fiber.builder()
            .tenantId(tenantId)
            .code(request.getCode())
            .name(request.getName())
            .build();
}
```

**Why It's Critical:**

- `@GeneratedValue(strategy = UUID)` → Hibernate controls ID generation
- `@Version` → Optimistic locking, Hibernate increments
- `@CreatedDate` / `@LastModifiedDate` → Spring Data auditing
- Manual setting causes: duplicate keys, version conflicts, audit failures

**Prevention:**

1. ✅ **Integration tests:** Assert `entity.getId()` is null before persist
2. ✅ **Test fixtures:** Never set `.id()`, `.version()`, audit fields
3. ✅ **Code review:** Flag manual UUID/version setting

### General Anti-Patterns

- ❌ **No Thread.sleep:** Use awaitility or deterministic triggers
- ❌ **No external services:** Use Testcontainers for isolation
- ❌ **No flaky timing:** Tests must be 100% deterministic
- ❌ **No over-mocking:** Mock collaborators, not the system under test
- ❌ **No duplicate tests:** Test each concern at the right layer
- ❌ **No random data:** Use fixtures with predictable values
- ❌ **No shared state:** Each test fully isolated
- ❌ **No order dependencies:** Tests run in any order

---

## 🗂️ TEST DOCUMENTATION STANDARD (Per Service)

Each service MUST maintain testing docs under:

```
docs/services/{service}/testing/
├── TEST_ARCHITECTURE.md     # Strategy, structure, examples
├── TEST_SUMMARY.md          # Coverage by layer, test count
├── TEST_RESULTS.md          # Latest execution results
└── TEST_ANTI_PATTERNS.md    # Service-specific gotchas
```

**Documentation Contents:**

- **TEST_ARCHITECTURE.md:**

  - Test philosophy and principles
  - Test pyramid (unit/integration/e2e breakdown)
  - Test structure and organization
  - Example test code for each layer
  - Fixture/builder patterns
  - Running tests locally and in CI

- **TEST_SUMMARY.md:**

  - Test statistics (total count, by type)
  - Coverage by layer (actual percentages)
  - What's tested (scenarios by layer)
  - Runtime performance metrics

- **TEST_RESULTS.md:**

  - Latest `mvn test` output
  - JaCoCo coverage report summary
  - Build status and CI pipeline links

- **TEST_ANTI_PATTERNS.md:**
  - Service-specific bugs prevented
  - Lessons learned
  - Prevention strategies

**Service README:**

Service root `services/{service}/README.md` should:

- Link to testing docs (no duplication)
- Show quick test commands
- Reference canonical examples

**Canonical Example:**

See `docs/services/fabric-fiber-service/testing/` for complete implementation.

---

## 📝 LAYER-SPECIFIC TESTING PATTERNS

### Unit Tests (75% of suite)

**Service Layer Test Example:**

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("FiberService Unit Tests")
class FiberServiceTest {

    @Mock private FiberRepository repository;
    @Mock private FiberEventPublisher eventPublisher;
    @InjectMocks private FiberService service;

    @Test
    @DisplayName("Should create fiber when valid request")
    void shouldCreateFiber_whenValidRequest() {
        // Given
        var request = createPureFiberRequest("CO", "Cotton");
        when(repository.existsByCode("CO")).thenReturn(false);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        UUID fiberId = service.createFiber(request);

        // Then
        assertThat(fiberId).isNotNull();
        verify(repository).save(any(Fiber.class));
        verify(eventPublisher).publishFiberDefined(any());
    }
}
```

**Mapper Layer Test Example:**

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("FiberMapper Unit Tests")
class FiberMapperTest {

    @InjectMocks private FiberMapperImpl mapper;

    @Test
    @DisplayName("Should map request to entity correctly")
    void shouldMapRequestToEntity() {
        // Given
        var request = createPureFiberRequest("CO", "Cotton");

        // When
        Fiber fiber = mapper.fromCreateRequest(request);

        // Then
        assertThat(fiber.getCode()).isEqualTo("CO");
        assertThat(fiber.getName()).isEqualTo("Cotton");
        assertThat(fiber.getId()).isNull(); // Hibernate manages!
    }
}
```

### Integration Tests (20% of suite)

**Repository Integration Test with Testcontainers:**

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@DisplayName("FiberRepository Integration Tests")
class FiberRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private FiberRepository repository;

    @Test
    @DisplayName("Should auto-generate UUID when saving")
    void shouldAutoGenerateUUID() {
        // Given
        Fiber fiber = createPureFiber("CO", "Cotton");
        assertThat(fiber.getId()).isNull(); // Pre-persist check

        // When
        Fiber saved = repository.save(fiber);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isInstanceOf(UUID.class);
    }
}
```

### E2E Tests (5% of suite)

**Full Workflow Test with REST Assured:**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@DisplayName("Fiber Service E2E Tests")
class FiberLifecycleE2ETest {

    @Container static PostgreSQLContainer<?> postgres = ...;
    @Container static KafkaContainer kafka = ...;

    @LocalServerPort private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1/fibers";
    }

    @Test
    @DisplayName("Complete lifecycle: Create → Read → Update → Delete")
    void shouldCompleteFullLifecycle() {
        // Create
        String fiberId = given()
            .contentType(JSON)
            .body(createFiberRequest())
        .when()
            .post()
        .then()
            .statusCode(201)
            .extract().path("data");

        // Read
        given()
            .pathParam("id", fiberId)
        .when()
            .get("/{id}")
        .then()
            .statusCode(200)
            .body("data.code", equalTo("CO"));
    }
}
```

---

## 🧩 CI/CD ENFORCEMENT

### Build Gates

- ✅ **`mvn clean verify`** must pass for PR merge
- ✅ **JaCoCo coverage ≥ 80%** enforced at BUNDLE level
- ✅ **Zero test failures** policy
- ✅ **Zero flaky tests** tolerance

### JaCoCo Configuration

**Root pom.xml defines global coverage enforcement:**

```xml
<!-- pom.xml - Root Project -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco-maven-plugin.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>jacoco-check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Child services MUST activate the plugin (config auto-inherited):**

```xml
<!-- services/{service}/pom.xml -->
<build>
    <plugins>
        <!-- JaCoCo plugin - inherits config from parent -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <!-- NO version, NO executions - inherited from root pom.xml -->
        </plugin>
    </plugins>
</build>
```

**Why?**

- Root `pom.xml` uses `<pluginManagement>` (defines config, PASSIVE)
- Child must use `<plugins>` (activates plugin, ACTIVE)
- Config auto-inherited from parent (ZERO duplication!)

**Applies to:** fiber-service, user-service, company-service, contact-service, notification-service

### CI Pipeline Example

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
  rules:
    - if: coverage < 80%
      when: fail
```

### Test Commands

```bash
# Run all tests with coverage + coverage check (RECOMMENDED)
mvn clean verify

# Run specific service tests with coverage
mvn clean verify -pl services/fiber-service -am

# View coverage report (after mvn verify)
open services/fiber-service/target/site/jacoco/index.html

# Run only tests (no coverage report generation)
mvn clean test

# Run only unit tests (fast)
mvn test -Dtest=**/*Test

# Run only integration tests
mvn test -Dtest=**/*IT

# Run only E2E tests
mvn test -Dtest=**/*E2ETest

# Skip tests (emergency only!)
mvn clean install -DskipTests
```

**Important:**

- Use `mvn verify` for coverage reports (runs `test` + `jacoco:report` + `jacoco:check`)
- Use `mvn test` for fast test-only runs (no coverage report)
- Coverage report generated at: `target/site/jacoco/index.html`

---

## 📋 PR CHECKLIST (Testing)

Before submitting a PR, ensure:

- [ ] **Tests written/updated** for the change
- [ ] **All impacted layers tested** (controller, service, mapper, repository)
- [ ] **Test naming follows convention:** `should{Behavior}_when{Condition}`
- [ ] **Unit tests are fast:** < 100ms each
- [ ] **Integration tests use Testcontainers** (real infra)
- [ ] **E2E tests verify workflows** (if applicable)
- [ ] **API contracts verified** (status codes, headers, payload)
- [ ] **Coverage ≥ 80%** (enforced by JaCoCo)
- [ ] **Layer targets met:** Service 95%, Mapper 90%, Controller 85%
- [ ] **No flaky tests:** All tests deterministic
- [ ] **No Hibernate anti-patterns:** Never set `.id()`, `.version()`, audit fields
- [ ] **Test fixtures updated** (if domain models changed)
- [ ] **Test docs updated** under `docs/services/{service}/testing/`
- [ ] **`mvn clean verify` passes** locally

---

## 🎯 SUCCESS CRITERIA

```
╔══════════════════════════════════════════════════════════════════╗
║                                                                  ║
║  ✅ ALL TESTS PASSING (0 failures)                              ║
║  ✅ COVERAGE ≥ 80% (enforced at BUNDLE level)                   ║
║  ✅ NO FLAKY TESTS (100% deterministic)                         ║
║  ✅ FAST EXECUTION (unit < 10s, integration < 30s)              ║
║  ✅ PRODUCTION PARITY (Testcontainers for real infra)           ║
║  ✅ DOCUMENTED (Test docs updated)                              ║
║                                                                  ║
║  If ANY of these fail → BUILD FAILS                             ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝
```

---

## 🔗 REFERENCES

### Documentation

- **Architecture Principles:** `docs/ARCHITECTURE.md`
- **Documentation Standards:** `docs/DOCUMENTATION_PRINCIPLES.md`
- **Developer Protocol:** `docs/DEVELOPER_PROTOCOL.md`

### Canonical Test Implementation

- **Fiber Service Tests:** `docs/services/fabric-fiber-service/testing/`
  - `TEST_ARCHITECTURE.md` - Complete test strategy & examples
  - `TEST_SUMMARY.md` - Coverage by layer, test scenarios
  - `TEST_ANTI_PATTERNS.md` - Lessons learned, bug prevention

### Code Examples

- **Unit Tests:** `services/fiber-service/src/test/java/.../unit/`
- **Integration Tests:** `services/fiber-service/src/test/java/.../integration/`
- **E2E Tests:** `services/fiber-service/src/test/java/.../e2e/`
- **Test Fixtures:** `services/fiber-service/src/test/java/.../fixtures/`

### Configuration

- **Root JaCoCo Config:** `pom.xml` (global 80% enforcement)
- **Testcontainers:** All integration tests use real PostgreSQL, Kafka
- **CI Pipeline:** `.github/workflows/test.yml` (when implemented)

---

## 🏆 QUALITY STANDARDS

**Google/Amazon/Netflix Enterprise-Level Testing:**

- ✅ **TDD:** Test-Driven Development (write tests first)
- ✅ **AAA Pattern:** Arrange-Act-Assert (Given-When-Then)
- ✅ **Test Pyramid:** 75% unit, 20% integration, 5% E2E
- ✅ **Real Infrastructure:** Testcontainers (production parity)
- ✅ **Fast Feedback:** Unit suite < 10s, full suite < 2 minutes
- ✅ **Zero Flakiness:** 100% deterministic, isolated tests
- ✅ **AssertJ:** Fluent, readable assertions
- ✅ **Coverage Enforcement:** JaCoCo at BUNDLE level (80% minimum)

**Quality Bar:**

> "Would you trust this code with your bank account?"
>
> If the answer is not a confident YES, keep testing!

---

**Enforced By:** Engineering Team  
**Violations:** PR will be blocked until compliant  
**Last Updated:** 2025-10-20  
**Canonical Example:** `docs/services/fabric-fiber-service/testing/`

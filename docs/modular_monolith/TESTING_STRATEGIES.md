# 🧪 TESTING STRATEGIES

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Status:** ✅ Active Development

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Test Pyramid](#test-pyramid)
3. [Unit Tests](#unit-tests)
4. [Integration Tests](#integration-tests)
5. [Module Boundary Tests](#module-boundary-tests)
6. [End-to-End Tests](#end-to-end-tests)
7. [Test Data Builders](#test-data-builders)
8. [Best Practices](#best-practices)

---

## 🎯 OVERVIEW

Fabric Management platformu, **Test Pyramid** stratejisini takip eder. Çok sayıda unit test, orta sayıda integration test ve az sayıda end-to-end test ile yüksek test coverage sağlanır.

### **Test Hedefleri**

| Hedef                         | Target  | Measurement                    |
| ----------------------------- | ------- | ------------------------------ |
| **Overall Coverage**          | > 80%   | JaCoCo                         |
| **Unit Test Coverage**        | > 90%   | Service & Domain layers        |
| **Integration Test Coverage** | > 70%   | Controller & Repository layers |
| **E2E Test Coverage**         | > 50%   | Critical user flows            |
| **Test Execution Time**       | < 5 min | CI/CD pipeline                 |

---

## 📊 TEST PYRAMID

```
        /\
       /  \      E2E Tests (Az sayıda, yavaş, pahalı)
      /────\     - Critical user flows
     /      \    - Full system integration
    /────────\
   /          \  Integration Tests (Orta sayıda, orta hız, orta maliyet)
  /────────────\ - Controller tests
 /              \- Repository tests
/────────────────\ - Module boundary tests

────────────────── Unit Tests (Çok sayıda, hızlı, ucuz)
                   - Service tests
                   - Domain tests
                   - Utility tests
```

### **Test Dağılımı**

| Test Type       | Count | Execution Time | Purpose                          |
| --------------- | ----- | -------------- | -------------------------------- |
| **Unit**        | 70%   | < 1 min        | Business logic, domain logic     |
| **Integration** | 20%   | 1-3 min        | API, Database, Module boundaries |
| **E2E**         | 10%   | 2-5 min        | Critical flows, User journeys    |

---

## 🔬 UNIT TESTS

### **Ne Test Edilir?**

- ✅ **Service layer** business logic
- ✅ **Domain layer** domain logic
- ✅ **Mapper** DTO transformations
- ✅ **Utility classes** helper methods
- ❌ **Repository** (integration test ile test edilir)
- ❌ **Controller** (integration test ile test edilir)

### **Technologies**

- **JUnit 5** - Test framework
- **Mockito** - Mocking framework
- **AssertJ** - Fluent assertions

### **Example: Service Unit Test**

```java
package com.fabricmanagement.production.masterdata.material.app;

import com.fabricmanagement.production.masterdata.material.api.dto.MaterialDto;
import com.fabricmanagement.production.masterdata.material.api.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.domain.Material;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.domain.event.MaterialCreatedEvent;
import com.fabricmanagement.production.masterdata.material.infra.repository.MaterialRepository;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.audit.app.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private MaterialRepository repository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private MaterialService service;

    private UUID tenantId;
    private UUID materialId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        materialId = UUID.randomUUID();
    }

    @Test
    void createMaterial_shouldCreateMaterial_whenValidRequest() {
        // Given
        CreateMaterialRequest request = CreateMaterialRequest.builder()
            .name("Cotton Fiber")
            .type(MaterialType.FIBER)
            .category("Cotton")
            .build();

        Material material = Material.builder()
            .id(materialId)
            .name(request.getName())
            .type(request.getType())
            .category(request.getCategory())
            .isActive(true)
            .build();

        when(repository.save(any(Material.class))).thenReturn(material);

        // When
        MaterialDto result = service.createMaterial(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(materialId);
        assertThat(result.getName()).isEqualTo("Cotton Fiber");
        assertThat(result.getType()).isEqualTo(MaterialType.FIBER);

        verify(repository, times(1)).save(any(Material.class));
        verify(eventPublisher, times(1)).publishEvent(any(MaterialCreatedEvent.class));
        verify(auditService, times(1)).logAction(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void getMaterial_shouldReturnMaterial_whenExists() {
        // Given
        Material material = Material.builder()
            .id(materialId)
            .name("Cotton Fiber")
            .type(MaterialType.FIBER)
            .build();

        when(repository.findByTenantIdAndId(tenantId, materialId)).thenReturn(Optional.of(material));

        // When
        MaterialDto result = service.getMaterial(tenantId, materialId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(materialId);
        assertThat(result.getName()).isEqualTo("Cotton Fiber");

        verify(repository, times(1)).findByTenantIdAndId(tenantId, materialId);
    }

    @Test
    void getMaterial_shouldThrowException_whenNotFound() {
        // Given
        when(repository.findByTenantIdAndId(tenantId, materialId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getMaterial(tenantId, materialId))
            .isInstanceOf(MaterialNotFoundException.class)
            .hasMessageContaining(materialId.toString());

        verify(repository, times(1)).findByTenantIdAndId(tenantId, materialId);
    }
}
```

### **Example: Domain Unit Test**

```java
package com.fabricmanagement.production.masterdata.material.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MaterialTest {

    @Test
    void create_shouldCreateMaterial_withDefaultValues() {
        // When
        Material material = Material.create("Cotton Fiber", MaterialType.FIBER);

        // Then
        assertThat(material).isNotNull();
        assertThat(material.getName()).isEqualTo("Cotton Fiber");
        assertThat(material.getType()).isEqualTo(MaterialType.FIBER);
        assertThat(material.getIsActive()).isTrue();
        assertThat(material.getDomainEvents()).hasSize(1);
        assertThat(material.getDomainEvents().get(0)).isInstanceOf(MaterialCreatedEvent.class);
    }

    @Test
    void update_shouldUpdateName_andPublishEvent() {
        // Given
        Material material = Material.create("Cotton Fiber", MaterialType.FIBER);
        material.clearDomainEvents();

        // When
        material.update("Polyester Fiber");

        // Then
        assertThat(material.getName()).isEqualTo("Polyester Fiber");
        assertThat(material.getDomainEvents()).hasSize(1);
        assertThat(material.getDomainEvents().get(0)).isInstanceOf(MaterialUpdatedEvent.class);
    }

    @Test
    void delete_shouldMarkAsInactive_andPublishEvent() {
        // Given
        Material material = Material.create("Cotton Fiber", MaterialType.FIBER);
        material.clearDomainEvents();

        // When
        material.delete();

        // Then
        assertThat(material.getIsActive()).isFalse();
        assertThat(material.getDomainEvents()).hasSize(1);
        assertThat(material.getDomainEvents().get(0)).isInstanceOf(MaterialDeletedEvent.class);
    }
}
```

---

## 🔌 INTEGRATION TESTS

### **Ne Test Edilir?**

- ✅ **Controller** HTTP endpoints
- ✅ **Repository** Database queries
- ✅ **External services** (mocked)
- ✅ **Security** Authentication & Authorization
- ❌ **Business logic** (unit test ile test edilir)

### **Technologies**

- **Spring Boot Test** - Test framework
- **MockMvc** - HTTP mocking
- **Testcontainers** - Database containers
- **WireMock** - External service mocking

### **Example: Controller Integration Test**

```java
package com.fabricmanagement.production.masterdata.material.api.controller;

import com.fabricmanagement.production.masterdata.material.api.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MaterialControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "test@example.com", roles = {"ADMIN"})
    void createMaterial_shouldReturn200_whenValidRequest() throws Exception {
        // Given
        CreateMaterialRequest request = CreateMaterialRequest.builder()
            .name("Cotton Fiber")
            .type(MaterialType.FIBER)
            .category("Cotton")
            .build();

        // When & Then
        mockMvc.perform(post("/api/production/masterdata/material")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Cotton Fiber"))
            .andExpect(jsonPath("$.data.type").value("FIBER"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"USER"})
    void createMaterial_shouldReturn403_whenInsufficientPermissions() throws Exception {
        // Given
        CreateMaterialRequest request = CreateMaterialRequest.builder()
            .name("Cotton Fiber")
            .type(MaterialType.FIBER)
            .build();

        // When & Then
        mockMvc.perform(post("/api/production/masterdata/material")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void createMaterial_shouldReturn401_whenNotAuthenticated() throws Exception {
        // Given
        CreateMaterialRequest request = CreateMaterialRequest.builder()
            .name("Cotton Fiber")
            .type(MaterialType.FIBER)
            .build();

        // When & Then
        mockMvc.perform(post("/api/production/masterdata/material")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }
}
```

### **Example: Repository Integration Test**

```java
package com.fabricmanagement.production.masterdata.material.infra.repository;

import com.fabricmanagement.production.masterdata.material.domain.Material;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MaterialRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private MaterialRepository repository;

    @Test
    void save_shouldPersistMaterial() {
        // Given
        UUID tenantId = UUID.randomUUID();
        Material material = Material.builder()
            .tenantId(tenantId)
            .name("Cotton Fiber")
            .type(MaterialType.FIBER)
            .isActive(true)
            .build();

        // When
        Material saved = repository.save(material);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Cotton Fiber");
    }

    @Test
    void findByTenantIdAndId_shouldReturnMaterial_whenExists() {
        // Given
        UUID tenantId = UUID.randomUUID();
        Material material = repository.save(Material.builder()
            .tenantId(tenantId)
            .name("Cotton Fiber")
            .type(MaterialType.FIBER)
            .isActive(true)
            .build());

        // When
        Optional<Material> found = repository.findByTenantIdAndId(tenantId, material.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Cotton Fiber");
    }

    @Test
    void findByTenantIdAndType_shouldReturnMaterials_whenTypeMatches() {
        // Given
        UUID tenantId = UUID.randomUUID();
        repository.save(Material.builder()
            .tenantId(tenantId)
            .name("Cotton Fiber")
            .type(MaterialType.FIBER)
            .isActive(true)
            .build());
        repository.save(Material.builder()
            .tenantId(tenantId)
            .name("Polyester Yarn")
            .type(MaterialType.YARN)
            .isActive(true)
            .build());

        // When
        List<Material> fibers = repository.findByTenantIdAndType(tenantId, MaterialType.FIBER);

        // Then
        assertThat(fibers).hasSize(1);
        assertThat(fibers.get(0).getType()).isEqualTo(MaterialType.FIBER);
    }
}
```

---

## 🧩 MODULE BOUNDARY TESTS

### **Ne Test Edilir?**

- ✅ **Module dependencies** Spring Modulith enforcement
- ✅ **Facade contracts** API compatibility
- ✅ **Event publishing** Domain events
- ✅ **Module isolation** No direct dependencies

### **Technologies**

- **Spring Modulith Test** - Module boundary testing
- **ArchUnit** - Architecture testing

### **Example: Module Boundary Test**

```java
package com.fabricmanagement.production;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ProductionModuleBoundaryTest {

    ApplicationModules modules = ApplicationModules.of(FabricManagementApplication.class);

    @Test
    void verifyModuleDependencies() {
        // Verify that production module only depends on common
        modules.verify();
    }

    @Test
    void verifyNoCircularDependencies() {
        // Verify no circular dependencies between modules
        modules.stream().forEach(module -> {
            System.out.println("Module: " + module.getName());
            module.getDependencies().forEach(dep -> {
                System.out.println("  -> " + dep.getName());
            });
        });
    }

    @Test
    void generateModuleDocumentation() {
        // Generate module documentation
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
```

### **Example: ArchUnit Test**

```java
package com.fabricmanagement.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

class ArchitectureTest {

    JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.fabricmanagement");

    @Test
    void servicesShouldNotDependOnControllers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..app..")
            .should().dependOnClassesThat().resideInAPackage("..api.controller..");

        rule.check(importedClasses);
    }

    @Test
    void controllersShouldOnlyDependOnServices() {
        ArchRule rule = classes()
            .that().resideInAPackage("..api.controller..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "..app..",
                "..dto..",
                "..common..",
                "java..",
                "org.springframework.."
            );

        rule.check(importedClasses);
    }

    @Test
    void domainShouldNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infra..");

        rule.check(importedClasses);
    }
}
```

---

## 🌐 END-TO-END TESTS

### **Ne Test Edilir?**

- ✅ **Critical user flows** End-to-end scenarios
- ✅ **Full system integration** All modules
- ✅ **Real database** PostgreSQL
- ✅ **Real message broker** Kafka (optional)

### **Technologies**

- **Spring Boot Test** - Test framework
- **Testcontainers** - Docker containers
- **REST Assured** - API testing

### **Example: E2E Test**

```java
package com.fabricmanagement.e2e;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MaterialFlowE2ETest {

    @LocalServerPort
    private int port;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    private String authToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        // Login and get JWT token
        authToken = given()
            .contentType("application/json")
            .body("""
                {
                    "email": "test@example.com",
                    "password": "password123"
                }
                """)
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .extract()
            .path("data.token");
    }

    @Test
    void completeMateri alFlow_shouldSucceed() {
        // 1. Create material
        String materialId = given()
            .header("Authorization", "Bearer " + authToken)
            .contentType("application/json")
            .body("""
                {
                    "name": "Cotton Fiber",
                    "type": "FIBER",
                    "category": "Cotton"
                }
                """)
        .when()
            .post("/api/production/masterdata/material")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data.name", is("Cotton Fiber"))
            .extract()
            .path("data.id");

        // 2. Get material
        given()
            .header("Authorization", "Bearer " + authToken)
        .when()
            .get("/api/production/masterdata/material/" + materialId)
        .then()
            .statusCode(200)
            .body("data.name", is("Cotton Fiber"));

        // 3. Update material
        given()
            .header("Authorization", "Bearer " + authToken)
            .contentType("application/json")
            .body("""
                {
                    "name": "Polyester Fiber"
                }
                """)
        .when()
            .put("/api/production/masterdata/material/" + materialId)
        .then()
            .statusCode(200)
            .body("data.name", is("Polyester Fiber"));

        // 4. Delete material
        given()
            .header("Authorization", "Bearer " + authToken)
        .when()
            .delete("/api/production/masterdata/material/" + materialId)
        .then()
            .statusCode(200);
    }
}
```

---

## 🛠️ TEST DATA BUILDERS

### **Fluent API for Test Data**

```java
package com.fabricmanagement.production.masterdata.material.test.testdata;

import com.fabricmanagement.production.masterdata.material.domain.Material;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;

import java.math.BigDecimal;
import java.util.UUID;

public class MaterialTestDataBuilder {

    private String name = "Test Material";
    private String description = "Test Description";
    private MaterialType type = MaterialType.FIBER;
    private String category = "Cotton";
    private String supplier = "Test Supplier";
    private BigDecimal unitCost = BigDecimal.valueOf(10.50);
    private String unit = "kg";
    private UUID tenantId = UUID.randomUUID();

    public MaterialTestDataBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public MaterialTestDataBuilder withType(MaterialType type) {
        this.type = type;
        return this;
    }

    public MaterialTestDataBuilder withCategory(String category) {
        this.category = category;
        return this;
    }

    public MaterialTestDataBuilder withTenantId(UUID tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public Material build() {
        Material material = Material.create(name, description, type, category, supplier, unitCost, unit);
        material.setTenantId(tenantId);
        return material;
    }

    // Convenience methods
    public static MaterialTestDataBuilder cottonFiber() {
        return new MaterialTestDataBuilder()
            .withName("Cotton Fiber")
            .withType(MaterialType.FIBER)
            .withCategory("Cotton");
    }

    public static MaterialTestDataBuilder polyesterYarn() {
        return new MaterialTestDataBuilder()
            .withName("Polyester Yarn")
            .withType(MaterialType.YARN)
            .withCategory("Polyester");
    }
}
```

### **Usage in Tests**

```java
@Test
void test() {
    // Given
    Material material = MaterialTestDataBuilder.cottonFiber()
        .withTenantId(tenantId)
        .build();

    // When
    Material saved = repository.save(material);

    // Then
    assertThat(saved).isNotNull();
}
```

---

## ✅ BEST PRACTICES

### **1. Follow AAA Pattern**

```java
@Test
void testName() {
    // Arrange (Given)
    Material material = MaterialTestDataBuilder.cottonFiber().build();

    // Act (When)
    Material saved = repository.save(material);

    // Assert (Then)
    assertThat(saved).isNotNull();
}
```

### **2. Use Descriptive Test Names**

```java
// ✅ Good
@Test
void createMaterial_shouldCreateMaterial_whenValidRequest() {}

// ❌ Bad
@Test
void test1() {}
```

### **3. Test One Thing Per Test**

```java
// ✅ Good
@Test
void createMaterial_shouldCreateMaterial_whenValidRequest() {}

@Test
void createMaterial_shouldThrowException_whenInvalidRequest() {}

// ❌ Bad
@Test
void createMaterial() {
    // Test both success and failure cases
}
```

### **4. Use Test Data Builders**

```java
// ✅ Good
Material material = MaterialTestDataBuilder.cottonFiber().build();

// ❌ Bad
Material material = new Material();
material.setName("Cotton Fiber");
material.setType(MaterialType.FIBER);
// ... many more setters
```

### **5. Clean Up After Tests**

```java
@AfterEach
void tearDown() {
    repository.deleteAll();
}
```

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

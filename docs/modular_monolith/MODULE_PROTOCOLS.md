# 📋 MODULE PROTOCOLS

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Status:** ✅ Active Development

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Module Structure Protocol](#module-structure-protocol)
3. [Naming Conventions](#naming-conventions)
4. [Layer Responsibilities](#layer-responsibilities)
5. [Dependency Rules](#dependency-rules)
6. [Communication Protocols](#communication-protocols)
7. [Event Protocols](#event-protocols)
8. [Testing Protocols](#testing-protocols)

---

## 🎯 OVERVIEW

Bu dokümantasyon, tüm modüllerin uyması gereken standart protokolleri tanımlar. Her modül bu protokollere uyarak geliştirilmelidir.

### **Temel Prensipler**

| Prensip             | Açıklama                                 |
| ------------------- | ---------------------------------------- |
| **Consistency**     | Tüm modüller aynı yapıya sahip olmalı    |
| **Independence**    | Her modül bağımsız geliştirilebilmeli    |
| **Testability**     | Her modül izole olarak test edilebilmeli |
| **Maintainability** | Kod okunabilir ve sürdürülebilir olmalı  |
| **Scalability**     | Modül kolayca büyütülebilmeli            |

---

## 🧱 MODULE STRUCTURE PROTOCOL

### **Standard Module Structure**

Her modül şu yapıya sahip olmalıdır:

```
{module_name}/
├─ {ModuleName}Module.java        # @ApplicationModule tanımı
├─ {feature}/
│  ├─ api/
│  │  ├─ controller/               # External REST API
│  │  │  └─ {Feature}Controller.java
│  │  └─ facade/                   # Internal API
│  │     └─ {Feature}Facade.java
│  ├─ app/
│  │  ├─ command/                  # CQRS Commands
│  │  │  ├─ Create{Feature}Command.java
│  │  │  ├─ Update{Feature}Command.java
│  │  │  └─ Delete{Feature}Command.java
│  │  ├─ query/                    # CQRS Queries
│  │  │  ├─ Get{Feature}Query.java
│  │  │  └─ GetAll{Feature}sQuery.java
│  │  └─ {Feature}Service.java     # Business Logic
│  ├─ domain/
│  │  ├─ {Feature}.java            # Domain Entity
│  │  ├─ {Feature}Type.java        # Value Object (if needed)
│  │  └─ event/
│  │     ├─ {Feature}CreatedEvent.java
│  │     ├─ {Feature}UpdatedEvent.java
│  │     └─ {Feature}DeletedEvent.java
│  ├─ infra/
│  │  ├─ repository/
│  │  │  └─ {Feature}Repository.java
│  │  └─ client/
│  │     ├─ interface/
│  │     │  └─ {External}Client.java
│  │     └─ impl/
│  │        └─ {External}RestClient.java
│  └─ dto/
│     ├─ {Feature}Dto.java
│     ├─ Create{Feature}Request.java
│     └─ Update{Feature}Request.java
└─ test/
   ├─ unit/
   │  ├─ {Feature}ServiceTest.java
   │  └─ {Feature}MapperTest.java
   ├─ integration/
   │  ├─ {Feature}ControllerTest.java
   │  └─ {Feature}RepositoryTest.java
   └─ testdata/
      └─ {Feature}TestDataBuilder.java
```

### **Module Definition**

Her modül kök dizininde bir `Module.java` dosyasına sahip olmalıdır:

```java
package com.fabricmanagement.{module};

import org.springframework.modulith.ApplicationModule;
import org.springframework.context.annotation.Configuration;

/**
 * {Module Name} Module
 *
 * {Module açıklaması}
 */
@ApplicationModule(
    allowedDependencies = {"common"} // Bağımlılıklar
)
@Configuration
public class {Module}Module {

    // Module configuration
    // Cross-cutting concerns
    // Module-level beans
}
```

---

## 📝 NAMING CONVENTIONS

### **Package Naming**

| Layer              | Package Pattern                 | Example                           |
| ------------------ | ------------------------------- | --------------------------------- |
| **Module Root**    | `com.fabricmanagement.{module}` | `com.fabricmanagement.production` |
| **Feature**        | `{module}.{feature}`            | `production.masterdata.material`  |
| **API**            | `{feature}.api.controller`      | `material.api.controller`         |
| **Facade**         | `{feature}.api.facade`          | `material.api.facade`             |
| **Application**    | `{feature}.app`                 | `material.app`                    |
| **Domain**         | `{feature}.domain`              | `material.domain`                 |
| **Infrastructure** | `{feature}.infra`               | `material.infra`                  |
| **DTO**            | `{feature}.dto`                 | `material.dto`                    |

### **Class Naming**

| Type           | Pattern                    | Example                 |
| -------------- | -------------------------- | ----------------------- |
| **Controller** | `{Feature}Controller`      | `MaterialController`    |
| **Facade**     | `{Feature}Facade`          | `MaterialFacade`        |
| **Service**    | `{Feature}Service`         | `MaterialService`       |
| **Entity**     | `{Feature}`                | `Material`              |
| **Repository** | `{Feature}Repository`      | `MaterialRepository`    |
| **DTO**        | `{Feature}Dto`             | `MaterialDto`           |
| **Request**    | `{Action}{Feature}Request` | `CreateMaterialRequest` |
| **Command**    | `{Action}{Feature}Command` | `CreateMaterialCommand` |
| **Query**      | `Get{Feature}Query`        | `GetMaterialQuery`      |
| **Event**      | `{Feature}{Action}Event`   | `MaterialCreatedEvent`  |

### **Method Naming**

| Type          | Pattern                | Example            |
| ------------- | ---------------------- | ------------------ |
| **Create**    | `create{Feature}`      | `createMaterial`   |
| **Read**      | `get{Feature}`         | `getMaterial`      |
| **Update**    | `update{Feature}`      | `updateMaterial`   |
| **Delete**    | `delete{Feature}`      | `deleteMaterial`   |
| **List**      | `getAll{Feature}s`     | `getAllMaterials`  |
| **Search**    | `search{Feature}s`     | `searchMaterials`  |
| **Validate**  | `validate{Feature}`    | `validateMaterial` |
| **Calculate** | `calculate{Something}` | `calculateCost`    |

---

## 🎯 LAYER RESPONSIBILITIES

### **API Layer**

**Sorumluluk:** HTTP endpoint'lerini expose eder

```java
@RestController
@RequestMapping("/api/{module}/{feature}")
@RequiredArgsConstructor
@Slf4j
public class {Feature}Controller {

    private final {Feature}Service service;

    @PolicyCheck(resource="{module}.{feature}.create", action="POST")
    @AuditLog(action="{FEATURE}_CREATE", resource="{feature}")
    @PostMapping
    public ResponseEntity<ApiResponse<{Feature}Dto>> create(@Valid @RequestBody Create{Feature}Request request) {
        log.info("Creating {feature}: {}", request.getName());
        {Feature}Dto created = service.create(request);
        return ResponseEntity.ok(ApiResponse.success(created, "{Feature} created successfully"));
    }
}
```

### **Facade Layer**

**Sorumluluk:** Diğer modüllere internal API sunar

```java
public interface {Feature}Facade {

    /**
     * Get {feature} by ID
     *
     * @param tenantId Tenant ID
     * @param id {Feature} ID
     * @return {Feature} DTO
     */
    Optional<{Feature}Dto> findById(UUID tenantId, UUID id);

    /**
     * Get all {feature}s by tenant
     *
     * @param tenantId Tenant ID
     * @return List of {feature} DTOs
     */
    List<{Feature}Dto> findByTenant(UUID tenantId);
}
```

### **Application Layer**

**Sorumluluk:** Business logic, command/query handling

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class {Feature}Service implements {Feature}Facade {

    private final {Feature}Repository repository;
    private final DomainEventPublisher eventPublisher;
    private final AuditService auditService;

    public {Feature}Dto create(Create{Feature}Request request) {
        // 1. Domain Logic
        {Feature} entity = {Feature}.create(request.getName(), request.getType());

        // 2. Repository Save
        {Feature} saved = repository.save(entity);

        // 3. Event Publishing
        eventPublisher.publishEvent(new {Feature}CreatedEvent(
            TenantContext.getCurrentTenantId(),
            saved.getId(),
            saved.getName()
        ));

        // 4. Audit Logging
        auditService.logAction("{FEATURE}_CREATE", "{feature}", saved.getId().toString(),
            "{Feature} created: " + saved.getName());

        return {Feature}Dto.from(saved);
    }
}
```

### **Domain Layer**

**Sorumluluk:** Domain entities, value objects, domain events

```java
@Entity
@Table(name = "{module}_{feature}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {Feature} extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private {Feature}Type type;

    // Business methods
    public static {Feature} create(String name, {Feature}Type type) {
        {Feature} entity = {Feature}.builder()
            .name(name)
            .type(type)
            .isActive(true)
            .build();

        entity.addDomainEvent(new {Feature}CreatedEvent(entity.getId(), entity.getName()));
        return entity;
    }

    public void update(String name) {
        this.name = name;
        this.addDomainEvent(new {Feature}UpdatedEvent(this.getId(), this.getName()));
    }

    public void delete() {
        this.isActive = false;
        this.addDomainEvent(new {Feature}DeletedEvent(this.getId()));
    }
}
```

### **Infrastructure Layer**

**Sorumluluk:** Data access, external communication

```java
@Repository
public interface {Feature}Repository extends JpaRepository<{Feature}, UUID> {

    List<{Feature}> findByTenantIdAndIsActiveTrue(UUID tenantId);

    Optional<{Feature}> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT f FROM {Feature} f WHERE f.tenantId = :tenantId AND f.type = :type")
    List<{Feature}> findByTenantIdAndType(@Param("tenantId") UUID tenantId, @Param("type") {Feature}Type type);
}
```

### **DTO Layer**

**Sorumluluk:** Data transfer objects

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {Feature}Dto {

    private UUID id;
    private String name;
    private {Feature}Type type;
    private Instant createdAt;
    private Instant updatedAt;

    public static {Feature}Dto from({Feature} entity) {
        return {Feature}Dto.builder()
            .id(entity.getId())
            .name(entity.getName())
            .type(entity.getType())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
```

---

## 🔗 DEPENDENCY RULES

### **Allowed Dependencies**

| From Layer         | To Layer       | Allowed?               |
| ------------------ | -------------- | ---------------------- |
| **Controller**     | Service        | ✅ Yes                 |
| **Controller**     | Repository     | ❌ No                  |
| **Service**        | Repository     | ✅ Yes                 |
| **Service**        | Facade         | ✅ Yes (other modules) |
| **Repository**     | Service        | ❌ No                  |
| **Domain**         | Infrastructure | ❌ No                  |
| **Infrastructure** | Domain         | ✅ Yes                 |

### **Module Dependencies**

| Module          | Can Depend On                          |
| --------------- | -------------------------------------- |
| **common**      | none                                   |
| **production**  | common                                 |
| **logistics**   | common, production                     |
| **finance**     | common, logistics, production          |
| **human**       | common                                 |
| **procurement** | common, finance                        |
| **integration** | common, production, logistics, finance |
| **insight**     | common, production, logistics, finance |

---

## 💬 COMMUNICATION PROTOCOLS

### **1. Facade Pattern (Senkron)**

```java
// Logistics modülü, Production facade'ini kullanır
@RequiredArgsConstructor
public class InventoryService {
    private final MaterialFacade materialFacade;

    public void checkAvailability(UUID materialId) {
        Optional<MaterialDto> material = materialFacade.findById(tenantId, materialId);
        // Use material data
    }
}
```

### **2. Event Pattern (Asenkron)**

```java
// Production modülü event publish eder
eventPublisher.publish(new MaterialCreatedEvent(tenantId, materialId, materialName));

// Logistics modülü event'i dinler
@EventListener
public void handle(MaterialCreatedEvent event) {
    // Create inventory item
}
```

---

## 📢 EVENT PROTOCOLS

### **Event Naming**

| Pattern                  | Example                |
| ------------------------ | ---------------------- |
| `{Feature}{Action}Event` | `MaterialCreatedEvent` |
| `{Feature}{Action}Event` | `MaterialUpdatedEvent` |
| `{Feature}{Action}Event` | `MaterialDeletedEvent` |

### **Event Structure**

```java
@Getter
public class {Feature}{Action}Event extends DomainEvent {

    private final UUID {feature}Id;
    private final String {feature}Name;

    public {Feature}{Action}Event(UUID tenantId, UUID {feature}Id, String {feature}Name) {
        super(tenantId, "{FEATURE}_{ACTION}");
        this.{feature}Id = {feature}Id;
        this.{feature}Name = {feature}Name;
    }
}
```

---

## 🧪 TESTING PROTOCOLS

### **Unit Tests**

```java
@ExtendWith(MockitoExtension.class)
class {Feature}ServiceTest {

    @Mock
    private {Feature}Repository repository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private {Feature}Service service;

    @Test
    void create_shouldCreate{Feature}_whenValidRequest() {
        // Given
        Create{Feature}Request request = new Create{Feature}Request("Test", {Feature}Type.TYPE1);
        {Feature} entity = {Feature}.create(request.getName(), request.getType());
        when(repository.save(any())).thenReturn(entity);

        // When
        {Feature}Dto result = service.create(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test");
        verify(repository).save(any());
        verify(eventPublisher).publishEvent(any({Feature}CreatedEvent.class));
    }
}
```

### **Integration Tests**

```java
@SpringBootTest
@Testcontainers
class {Feature}ControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void create_shouldReturn201_whenValidRequest() throws Exception {
        // Given
        Create{Feature}Request request = new Create{Feature}Request("Test", {Feature}Type.TYPE1);

        // When & Then
        mockMvc.perform(post("/api/{module}/{feature}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Test"));
    }
}
```

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

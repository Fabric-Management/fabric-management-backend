# 📁 Code Structure Guide - Kod Nereye Yazılır?

## 🎯 Özet Tablo

| Ne Yazıyorum?                 | Nereye Yazacağım?             | Örnek Dosya                         |
| ----------------------------- | ----------------------------- | ----------------------------------- |
| **REST API Endpoint**         | `/api/`                       | `UserController.java`               |
| **İş Mantığı**                | `/application/service/`       | `UserService.java`                  |
| **DTO Request**               | `/api/dto/request/`           | `CreateUserRequest.java`            |
| **DTO Response**              | `/api/dto/response/`          | `UserResponse.java`                 |
| **DTO → Entity Mapping**      | `/application/mapper/`        | `UserMapper.java`                   |
| **Entity → Event Mapping**    | `/application/mapper/`        | `UserEventMapper.java`              |
| **Entity (Data)**             | `/domain/aggregate/`          | `User.java`                         |
| **Value Object/Enum**         | `/domain/valueobject/`        | `UserStatus.java`                   |
| **Domain Event**              | `/domain/event/`              | `UserCreatedEvent.java`             |
| **Repository**                | `/infrastructure/repository/` | `UserRepository.java`               |
| **External API Client**       | `/infrastructure/client/`     | `ContactServiceClient.java`         |
| **Kafka Publisher**           | `/infrastructure/messaging/`  | `UserEventPublisher.java`           |
| **Kafka Listener**            | `/infrastructure/messaging/`  | `CompanyEventListener.java`         |
| **Security Infrastructure**   | `/infrastructure/security/`   | `LoginAttemptTracker.java`          |
| **Policy Filter** ⭐          | `/infrastructure/security/`   | `PolicyValidationFilter.java`       |
| **Reactive Audit** ⭐         | `/audit/` (Gateway)           | `ReactivePolicyAuditPublisher.java` |
| **Config (Service-Specific)** | `/infrastructure/config/`     | `FeignClientConfig.java`            |

---

## 📂 Detaylı Klasör Yapısı (2025-10-10 - Production Grade)

```
services/user-service/src/main/
├── java/com/fabricmanagement/user/
│   │
│   ├── UserServiceApplication.java
│   │
│   ├── api/                                    # 🌐 HTTP Layer
│   │   ├── UserController.java                [186 satır] HTTP only, no logic
│   │   ├── AuthController.java                [50 satır]
│   │   └── dto/
│   │       ├── request/                        # All request DTOs
│   │       │   ├── CreateUserRequest.java
│   │       │   ├── UpdateUserRequest.java
│   │       │   ├── LoginRequest.java
│   │       │   └── SetupPasswordRequest.java
│   │       └── response/                       # All response DTOs
│   │           ├── UserResponse.java
│   │           ├── LoginResponse.java
│   │           └── CheckContactResponse.java
│   │
│   ├── application/                            # 🔧 Business Layer
│   │   ├── mapper/                             # ALL mapping logic here
│   │   │   ├── UserMapper.java                [221 satır] DTO ↔ Entity
│   │   │   ├── UserEventMapper.java           [47 satır]  Entity → Event
│   │   │   └── AuthMapper.java                [74 satır]  Auth DTOs
│   │   │
│   │   └── service/                            # Business logic ONLY
│   │       ├── UserService.java               [169 satır] No mapping!
│   │       └── AuthService.java               [211 satır] No mapping!
│   │
│   ├── domain/                                 # 🎯 Domain Layer
│   │   ├── aggregate/
│   │   │   └── User.java                      [99 satır] Pure data holder
│   │   │
│   │   ├── event/
│   │   │   ├── UserCreatedEvent.java
│   │   │   ├── UserUpdatedEvent.java
│   │   │   └── UserDeletedEvent.java
│   │   │
│   │   └── valueobject/
│   │       ├── UserStatus.java                # Enum
│   │       └── RegistrationType.java          # Enum
│   │
│   └── infrastructure/                         # 🏗️ Infrastructure
│       ├── repository/
│       │   └── UserRepository.java
│       │
│       ├── client/
│       │   ├── ContactServiceClient.java
│       │   ├── ContactServiceClientFallback.java
│       │   └── dto/ContactDto.java
│       │
│       ├── messaging/
│       │   ├── UserEventPublisher.java
│       │   ├── CompanyEventListener.java
│       │   ├── ContactEventListener.java
│       │   └── event/                          # External events
│       │       ├── CompanyCreatedEvent.java
│       │       └── ContactVerifiedEvent.java
│       │
│       ├── security/                           # Security infrastructure
│       │   ├── LoginAttemptTracker.java       [108 satır] Redis-based
│       │   └── PolicyValidationFilter.java    [183 satır] ⭐ Defense-in-depth (Phase 3)
│       │
│       ├── audit/
│       │   └── SecurityAuditLogger.java
│       │
│       └── config/                             # Service-specific only
│           ├── FeignClientConfig.java
│           └── KafkaErrorHandlingConfig.java
│
└── resources/
    ├── application.yml
    ├── application-docker.yml
    └── db/migration/
        └── V1__create_user_tables.sql
```

**Key Changes (2025-10-10):**

- ✅ NO validator/ folder (Spring @Valid sufficient)
- ✅ NO helper/ folder (YAGNI - use private methods)
- ✅ NO domain/service/ folder (YAGNI - entity is data holder)
- ✅ Multiple mappers OK (SRP: UserMapper, EventMapper, AuthMapper)
- ✅ Entity = 99 lines (was 408!) - Pure @Getter/@Setter
- ✅ infrastructure/security/ for Redis-based security features
- ✅ **PolicyValidationFilter** for defense-in-depth (Phase 3) ⭐ NEW

---

## 📂 API Gateway Structure (Reactive - Phase 3)

**Special Case:** Gateway is reactive (WebFlux), different structure

```
services/api-gateway/src/main/
├── java/com/fabricmanagement/gateway/
│   │
│   ├── ApiGatewayApplication.java
│   │
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   └── SmartKeyResolver.java          # Rate limiting
│   │
│   ├── constants/                          # Centralized constants
│   │   ├── GatewayHeaders.java            # Header name constants
│   │   ├── GatewayPaths.java              # Public path patterns
│   │   └── FilterOrder.java               # Filter execution order
│   │
│   ├── audit/                              ⭐ NEW (Phase 3)
│   │   └── ReactivePolicyAuditPublisher.java  [89 satır]
│   │                                       # Kafka-only reactive audit
│   │
│   ├── filter/                             # Reactive filters
│   │   ├── PolicyEnforcementFilter.java   [171 satır] Enhanced
│   │   └── RequestLoggingFilter.java      [84 satır]
│   │
│   ├── security/
│   │   └── JwtAuthenticationFilter.java   [129 satır]
│   │
│   ├── util/                               # Helper utilities
│   │   ├── UuidValidator.java             # UUID validation
│   │   ├── PathMatcher.java               # Path matching
│   │   ├── JwtTokenExtractor.java         # Token extraction
│   │   └── ResponseHelper.java            # Response building
│   │
│   └── fallback/
│       └── FallbackController.java         # Circuit breaker fallbacks
│
└── resources/
    ├── application.yml
    └── application-docker.yml
```

**Key Differences from Services:**

| Aspect           | Services (Spring MVC)             | Gateway (WebFlux)                           |
| ---------------- | --------------------------------- | ------------------------------------------- |
| **Filter Type**  | `Filter` (jakarta.servlet)        | `GlobalFilter` (reactive)                   |
| **Request Type** | `HttpServletRequest`              | `ServerWebExchange`                         |
| **Return Type**  | `void`                            | `Mono<Void>`                                |
| **Audit**        | `PolicyAuditService` (DB + Kafka) | `ReactivePolicyAuditPublisher` (Kafka-only) |
| **Database**     | ✅ Has JPA                        | ❌ No database                              |
| **Pattern**      | Blocking                          | Non-blocking                                |

**Where to Put Code:**

| Ne Yazıyorum?      | Nereye?       | Örnek                               |
| ------------------ | ------------- | ----------------------------------- |
| **Reactive Audit** | `/audit/`     | `ReactivePolicyAuditPublisher.java` |
| **Global Filter**  | `/filter/`    | `PolicyEnforcementFilter.java`      |
| **Constants**      | `/constants/` | `GatewayHeaders.java`               |
| **Helpers**        | `/util/`      | `UuidValidator.java`                |

---

## 📝 Örneklerle Açıklama

### 1️⃣ Yeni Feature: Kullanıcı Kayıt

#### A. Request DTO

```java
// 📍 api/dto/request/RegisterUserRequest.java
@Data
@Builder
public class RegisterUserRequest {
    @NotBlank(message = "Email zorunludur")
    @Email(message = "Geçerli bir email giriniz")
    private String email;

    @NotBlank(message = "Şifre zorunludur")
    @Size(min = 8, message = "Şifre en az 8 karakter olmalı")
    private String password;

    @NotBlank(message = "Ad zorunludur")
    private String firstName;

    private String lastName;
}
```

#### B. Controller

```java
// 📍 api/controller/AuthController.java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterUserRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(user, "Kayıt başarılı"));
    }
}
```

#### C. Service (Business Logic ONLY)

```java
// 📍 application/service/UserService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserEventMapper eventMapper;
    private final UserEventPublisher eventPublisher;

    @Transactional
    public UUID createUser(CreateUserRequest request, UUID tenantId, String createdBy) {
        log.info("Creating user: {} for tenant: {}", request.getEmail(), tenantId);

        // Mapping → Mapper's job!
        User user = userMapper.fromCreateRequest(request, tenantId, createdBy);
        user = userRepository.save(user);

        log.info("User created successfully: {}", user.getId());

        // Event building → EventMapper's job!
        eventPublisher.publishUserCreated(
            eventMapper.toCreatedEvent(user, request.getEmail())
        );

        return user.getId();
    }
}
```

#### D. Mapper (DTO → Entity)

```java
// 📍 application/mapper/UserMapper.java
@Component
@RequiredArgsConstructor
public class UserMapper {

    public User fromCreateRequest(CreateUserRequest request, UUID tenantId, String createdBy) {
        return User.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .status(UserStatus.PENDING_VERIFICATION)
                .createdBy(createdBy)
                .build();
    }

    public UserResponse toResponse(User user) {
        // Enrichment with external data (Contact Service)
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }
}
```

#### E. Entity (Pure Data Holder - Anemic Domain)

```java
// 📍 domain/aggregate/User.java
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "password_hash")
    private String passwordHash;

    // NO BUSINESS METHODS! Only @Getter/@Setter (Lombok)
    // Business logic → Service layer
    // Computed properties → Mapper layer
}
```

#### F. Test

```java
// 📍 test/unit/AuthServiceTest.java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Başarılı kullanıcı kaydı")
    void shouldRegisterUserSuccessfully() {
        // Given
        RegisterUserRequest request = RegisterUserRequest.builder()
            .email("test@example.com")
            .password("password123")
            .firstName("John")
            .lastName("Doe")
            .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(createUser());
        when(userMapper.toResponse(any(User.class))).thenReturn(createUserResponse());

        // When
        UserResponse response = authService.register(request);

        // Then
        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
        verify(userRepository).save(any(User.class));
        verify(eventProducer).publishUserRegistered(any());
    }

    @Test
    @DisplayName("Duplicate email ile kayıt başarısız olmalı")
    void shouldFailWhenEmailExists() {
        // Given
        RegisterUserRequest request = createRequest();
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // When & Then
        assertThrows(DuplicateEmailException.class,
            () -> authService.register(request));
    }
}
```

---

## 🚫 Yapmayın!

### ❌ Controller'da İş Mantığı

```java
// YANLIŞ
@PostMapping("/users")
public UserResponse createUser(@RequestBody CreateUserRequest request) {
    // İş mantığı controller'da olmamalı!
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new DuplicateEmailException();
    }
    User user = new User();
    user.setEmail(request.getEmail());
    return userRepository.save(user);
}
```

### ✅ Doğrusu

```java
// DOĞRU
@PostMapping("/users")
public UserResponse createUser(@RequestBody CreateUserRequest request) {
    return userService.createUser(request); // İş mantığı service'de
}
```

### ❌ Entity'yi Direkt Dönme

```java
// YANLIŞ
@GetMapping("/users/{id}")
public User getUser(@PathVariable UUID id) {
    return userRepository.findById(id).orElseThrow();
}
```

### ✅ Doğrusu

```java
// DOĞRU
@GetMapping("/users/{id}")
public UserDTO getUser(@PathVariable UUID id) {
    return userService.getUserById(id); // DTO döner
}
```

---

## ⭐ Yeni Prensipler (2025-10-10 Refactoring)

### 1. **Entity = Pure Data Holder (Anemic Domain)**

```java
// ✅ DOĞRU: Sadece fields
@Entity
@Getter
@Setter
public class User extends BaseEntity {
    private String firstName;
    private String lastName;
    // NO METHODS!
}

// ❌ YANLIŞ: Business methods
public class User {
    public void updateProfile() { ... }
    public String getFullName() { ... }
}
```

**Neden:** Lombok zaten getter/setter sağlıyor, computed properties → Mapper'da!

---

### 2. **Mapping Logic → Mapper (NOT Service)**

```java
// ✅ DOĞRU: Service sadece business logic
@Service
public class UserService {
    public UUID createUser(CreateUserRequest request) {
        User user = userMapper.fromCreateRequest(request, tenantId, createdBy);
        // Business logic here
    }
}

// ❌ YANLIŞ: Service'de mapping
public class UserService {
    public UUID createUser(CreateUserRequest request) {
        User user = User.builder()
            .firstName(request.getFirstName())
            // ... 20 satır mapping! ❌
            .build();
    }
}
```

**Kural:** `.builder()` gördüğünde → Mapper'a taşı!

---

### 3. **Multiple Mappers for SRP**

```java
// ✅ DOĞRU: Her concern için ayrı mapper
UserMapper       → DTO ↔ Entity
UserEventMapper  → Entity → Event
AuthMapper       → Auth DTOs + JWT claims

// ❌ YANLIŞ: Tek giant mapper
UserMapper → Her şey burada (SRP ihlali!)
```

---

### 4. **NO Over-Engineering**

```java
// ❌ YAPMAYIN:
- Validator klasörü → Spring @Valid yeterli
- Helper klasörü → Private method yeterli
- Builder klasörü → Lombok @Builder yeterli

// ✅ YAPIN:
- Spring/Lombok'u kullan
- Private method yaz
- YAGNI prensibi
```

---

### 5. **Infrastructure Concerns → infrastructure/ Layer**

```java
// ✅ DOĞRU:
infrastructure/security/LoginAttemptTracker.java  // Redis kullanıyor

// ❌ YANLIŞ:
application/service/LoginAttemptService.java  // Redis = infrastructure!
```

---

## 💡 İpuçları

1. **Her katman tek sorumluluk**

   - Controller: HTTP only
   - Service: Business logic only
   - Mapper: Mapping only
   - Entity: Data only

2. **DTO request/response ayrımı**

   - api/dto/request/ klasörü
   - api/dto/response/ klasörü

3. **Mapper kullanın**

   - DTO → Entity: Mapper
   - Entity → Event: EventMapper
   - Service'de mapping YOK!

4. **Comment'leri minimize edin**

   - Self-documenting code yazın
   - Sadece WHY açıklayın, WHAT değil

5. **Framework'leri sömürün**
   - Spring: @Valid, @Transactional, PageRequest
   - Lombok: @Getter, @Setter, @Builder
   - Shared: PagedResponse, ValidationConstants

---

## 🆕 Phase 3 Additions (Oct 2025)

### 1. Defense-in-Depth Filter (All Services)

**Where:** `infrastructure/security/PolicyValidationFilter.java`

**Purpose:** Secondary policy enforcement (after Gateway)

**Code Pattern:**

```java
@Component
@Order(2)  // After JwtAuthenticationFilter (Order 1)
@RequiredArgsConstructor
public class PolicyValidationFilter implements Filter {
    private final PolicyEngine policyEngine;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        // 1. Extract SecurityContext
        SecurityContext secCtx = (SecurityContext) authentication.getPrincipal();

        // 2. Build PolicyContext
        PolicyContext policyCtx = buildPolicyContext(httpRequest, secCtx);

        // 3. Evaluate policy (secondary check)
        PolicyDecision decision = policyEngine.evaluate(policyCtx);

        // 4. Deny if needed
        if (decision.isDenied()) {
            throw new ForbiddenException(decision.getReason());
        }

        // 5. Continue
        chain.doFilter(request, response);
    }
}
```

**When to Use:**

- ✅ ALL microservices (user, company, contact)
- ✅ Place: `infrastructure/security/` folder
- ✅ Order: 2 (after JWT filter)

---

### 2. Reactive Audit Publisher (Gateway Only)

**Where:** `audit/ReactivePolicyAuditPublisher.java`

**Purpose:** Non-blocking audit for reactive Gateway

**Code Pattern:**

```java
@Component
@RequiredArgsConstructor
public class ReactivePolicyAuditPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public Mono<Void> publishDecision(PolicyContext context, PolicyDecision decision, long latencyMs) {
        return Mono.fromRunnable(() -> publishSync(context, decision, latencyMs))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(error -> Mono.empty())
            .then();
    }
}
```

**When to Use:**

- ✅ Gateway ONLY (reactive context)
- ✅ No database access needed
- ✅ Kafka-only pattern
- ❌ NOT for services (use PolicyAuditService)

---

### 3. Optional Dependency Pattern

**Where:** Any shared component used in mixed contexts

**Code Pattern:**

```java
@Component
public class PolicyEngine {

    private final PolicyRegistryRepository policyRegistryRepository;  // Optional

    public PolicyEngine(
            CompanyTypeGuard companyTypeGuard,
            @Autowired(required = false) PolicyRegistryRepository policyRegistryRepository) {
        this.policyRegistryRepository = policyRegistryRepository;
    }

    private boolean checkRoles(PolicyContext context) {
        // Try database (if available)
        if (policyRegistryRepository != null) {
            return checkFromDatabase(context);
        }

        // Fallback (if not available)
        return checkFromFallback(context);
    }
}
```

**When to Use:**

- Component used in Gateway (no DB) AND Services (with DB)
- Need graceful degradation
- Single implementation for multiple contexts

---

## 🧩 Shared Modules Yapısı

**DRY Prensibi:** Tüm microservice'ler bu modülleri kullanır - kod tekrarı %0

```
shared/
├── shared-domain/                    # Core Domain
│   ├── base/BaseEntity.java          # JPA audit base
│   ├── exception/                    # Generic exceptions
│   │   ├── UserNotFoundException.java
│   │   ├── ValidationException.java
│   │   └── UnauthorizedException.java
│   ├── event/DomainEvent.java
│   └── policy/UserContext.java       # INTERNAL/CUSTOMER/SUPPLIER
│
├── shared-application/               # Application Shared
│   ├── response/
│   │   ├── ApiResponse.java          # Standard API response
│   │   └── PagedResponse.java        # Pagination response
│   └── context/SecurityContext.java  # User/tenant info
│
├── shared-infrastructure/            # Infrastructure Shared
│   ├── constants/
│   │   ├── ValidationConstants.java  # Email/phone patterns
│   │   └── SecurityRoles.java        # ADMIN, SUPER_ADMIN, etc.
│   ├── security/SecurityContextHolder.java
│   └── config/                       # Default configs
│       └── JpaAuditingConfig.java
│
└── shared-security/                  # Security Shared
    ├── config/DefaultSecurityConfig.java
    ├── jwt/JwtTokenProvider.java
    └── filter/JwtAuthenticationFilter.java
```

**Usage:**

- ✅ Import from shared (don't duplicate)
- ✅ Extend base classes (BaseEntity)
- ✅ Use shared exceptions
- ✅ Use PagedResponse factory methods

---

## 📚 Daha Fazla Bilgi

- [Development Principles](PRINCIPLES.md) - Kodlama standartları
- [Architecture](../ARCHITECTURE.md) - Sistem mimarisi overview
- [AI Assistant Learnings](../AI_ASSISTANT_LEARNINGS.md) - Kodlama prensipleri

---

**Last Updated:** 2025-10-10 (Phase 3 Integration - Defense-in-Depth + Reactive Patterns)  
**Version:** 3.0.0  
**Status:** ✅ Production Ready  
**New Patterns:** Defense-in-Depth Filter, Reactive Audit, Optional Dependencies

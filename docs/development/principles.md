# 📋 Development Principles & Standards

## 🎯 Overview

Bu doküman, Fabric Management System geliştirmesinde uyulması gereken temel prensipleri ve standartları içerir. Tüm geliştiricilerin bu kurallara uyması beklenmektedir.

---

## ⚠️ **CRITICAL PROJECT PRINCIPLES** ⚠️

### 🚫 **NO USERNAME PRINCIPLE** 🚫

```
╔═══════════════════════════════════════════════════════════════════╗
║                                                                   ║
║  ⛔ THIS PROJECT DOES NOT USE USERNAME! ⛔                       ║
║                                                                   ║
║  ❌ NO username field in User entity                             ║
║  ❌ NO username in authentication                                ║
║  ❌ NO username in JWT tokens                                    ║
║  ❌ NO username anywhere in the codebase                         ║
║                                                                   ║
║  ✅ USE: contactValue (email or phone)                           ║
║  ✅ USE: userId (UUID as string in JWT)                          ║
║  ✅ USE: User.getId() for identification                         ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

#### **WHY NO USERNAME?**

**Business Requirement:**

- Users authenticate with **email** or **phone number** (contactValue)
- No separate username registration/management
- Simplifies user experience
- Aligns with modern authentication patterns

**Technical Benefits:**

1. ✅ **Reduced Complexity**: One less field to manage
2. ✅ **Better UX**: Users remember their email/phone
3. ✅ **Security**: UUID-based identification (no enumeration attacks)
4. ✅ **Privacy**: JWT contains UUID, not email/phone
5. ✅ **GDPR Compliant**: No PII in JWT tokens

#### **CORRECT TERMINOLOGY**

| ❌ WRONG             | ✅ CORRECT        | Usage                                      |
| -------------------- | ----------------- | ------------------------------------------ |
| `username`           | `userId`          | JWT 'sub' claim, Spring Security principal |
| `username`           | `contactValue`    | Login request (email/phone)                |
| `extractUsername()`  | `extractUserId()` | JWT token parsing                          |
| `String username`    | `String userId`   | Method parameters                          |
| `user.getUsername()` | `user.getId()`    | Entity identification                      |

#### **CODE EXAMPLES**

**❌ WRONG:**

```java
// NEVER DO THIS!
@Entity
public class User {
    private String username;  // ❌ NO!
}

// Login
public void login(String username, String password) { } // ❌ NO!

// JWT
String username = jwtTokenProvider.extractUsername(token); // ❌ DEPRECATED!
```

**✅ CORRECT:**

```java
// This is the way!
@Entity
public class User {
    private UUID id;  // ✅ Identification via UUID
    // No username field!
}

// Login
public LoginResponse login(String contactValue, String password) { } // ✅ YES!

// JWT
String userId = jwtTokenProvider.extractUserId(token); // ✅ YES!

// JWT Payload
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // ✅ userId (UUID)
  "tenantId": "7c9e6679-7425-40de-963d-42a6ee08cd6c",
  "role": "TENANT_ADMIN"  // ✅ SystemRole enum value
  // NO username field!
}
```

#### **ENFORCEMENT CHECKLIST**

Before committing code, verify:

- [ ] No `username` field in entities
- [ ] No `username` in DTOs (except deprecated UserDto with clear note)
- [ ] No `username` in method parameters
- [ ] No `username` in JWT claims
- [ ] Authentication uses `contactValue`
- [ ] JWT 'sub' claim = `userId` (UUID)
- [ ] Code uses `userId`, not `username`

#### **SEE ALSO**

- 📝 [SECURITY.md](../SECURITY.md) - UUID-based JWT security
- 🔐 [User Service Documentation](../services/user-service.md) - Authentication flow
- 📊 [API Documentation](../api/README.md) - Login endpoint examples

---

## ☕ Spring Boot Coding Principles

### 1. Temel Kod Kalitesi

- ✅ **Okunabilir ve Sade Kod**: Kod self-documenting olmalı
- ✅ **Anlamlı İsimlendirme**: Sınıf, metod ve değişken isimleri açıklayıcı olmalı
- ✅ **Sabitler**: Magic number/string yerine constants kullanılmalı
- ✅ **Tek Sorumluluk**: Her metod tek bir iş yapmalı
- ✅ **Minimal Yorum**: Kod kendini açıklamalı, gereksiz yorum olmamalı
  - Code tells HOW, comments tell WHY
  - Self-documenting code > extensive JavaDoc
  - Example: `SystemRole.TENANT_ADMIN` (isim zaten açıklıyor, yorum gereksiz)

### 2. Mimari Prensipler

#### Katmanlı Mimari

```
Controller → Service → Repository → Database
    ↓           ↓           ↓
   DTO       Domain      Entity
```

#### SOLID Prensipleri

- **S**ingle Responsibility: Her sınıf tek sorumluluk
- **O**pen/Closed: Genişletmeye açık, değişime kapalı
- **L**iskov Substitution: Alt sınıflar üst sınıfların yerine geçebilmeli
- **I**nterface Segregation: Küçük, özel arayüzler
- **D**ependency Inversion: Soyutlamalara bağımlılık

#### Diğer Prensipler

- **KISS**: Keep It Simple, Stupid
- **DRY**: Don't Repeat Yourself
  - Exception: Microservices DTO duplication OK (loose coupling > DRY)
  - See: `microservices_api_standards.md` → DTO Strategy
- **YAGNI**: You Aren't Gonna Need It
  - Balance: Build foundation (data model) but not business logic yet
  - Example: Add `is_platform` field ✅, Add `if (isPlatform)` logic ❌
  - Rule: "Build the foundation, don't paint the house yet"
- **Loose Coupling**: Minimize dependencies between components

### 3. Spring Boot Best Practices

```java
// ✅ Constructor Injection
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
}

// ❌ Field Injection
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
}
```

### 4. Veri Katmanı

```java
// BaseEntity kullanımı
@MappedSuperclass
@Data
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}

// Entity extends BaseEntity
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    private String username;
    private String email;
}
```

### 5. Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(e.getMessage()));
    }
}
```

### 6. API Response Standardization

```java
@Data
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    private LocalDateTime timestamp;
}
```

---

## 🚀 Microservice Development Principles

### ⚡ ORCHESTRATION PATTERN - GOLDEN RULE (NEW - Oct 15, 2025)

```
╔═══════════════════════════════════════════════════════════════════╗
║                                                                   ║
║  ⚡ CRITICAL: ATOMIC OPERATIONS FOR MULTI-STEP FLOWS             ║
║                                                                   ║
║  ❌ NEVER: verify() → setupPassword() → login() (3 HTTP)         ║
║  ✅ ALWAYS: setupPasswordWithVerification() (1 HTTP)             ║
║                                                                   ║
║  Impact: 66% faster, 66% cheaper, 100% better UX                 ║
║                                                                   ║
║  📖 See: docs/development/ORCHESTRATION_PATTERN.md               ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝
```

**Examples in Our Codebase:**

- `TenantOnboardingService.registerTenant()` → Company + User + Contact (1 HTTP)
- `AuthService.setupPasswordWithVerification()` → Verify + Password + Login (1 HTTP)

**Rule:** If frontend needs 2+ related API calls → Create orchestration endpoint!

---

### 1. Service Design

#### Bounded Context

```yaml
user-service:
  responsibilities:
    - User authentication
    - User profile management
    - Role management

company-service:
  responsibilities:
    - Company profiles
    - Company settings
    - Subscription management
```

#### Service Characteristics

- **Small & Independent**: Küçük ve bağımsız deployable
- **Stateless**: State external storage'da tutulmalı
- **API Contract**: Versioned REST APIs
- **Single Responsibility**: Tek bounded context

### 2. Communication Patterns

#### Synchronous Communication

```java
// REST API for queries
@FeignClient(name = "user-service")
public interface UserServiceClient {
    @GetMapping("/api/v1/users/{id}")
    UserDTO getUser(@PathVariable UUID id);
}
```

#### Asynchronous Communication

```java
// Event-driven for commands
@Component
public class UserEventPublisher {
    @Autowired
    private KafkaTemplate<String, DomainEvent> kafkaTemplate;

    public void publishUserCreated(UserCreatedEvent event) {
        kafkaTemplate.send("user-events", event);
    }
}
```

### 3. Data Management

#### Database per Service

```yaml
services:
  user-service:
    database: user_db
    schema: user_schema

  company-service:
    database: company_db
    schema: company_schema
```

#### Eventual Consistency

```java
// Saga pattern for distributed transactions
@Component
public class OrderSaga {
    @SagaOrchestrationStart
    public void handle(CreateOrderCommand command) {
        // Step 1: Reserve inventory
        // Step 2: Process payment
        // Step 3: Create order
    }
}
```

### 4. Security

#### JWT Authentication

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt)
            .build();
    }
}
```

### 5. Observability

#### Structured Logging

```java
@Slf4j
@Service
public class UserService {

    public User createUser(CreateUserRequest request) {
        log.info("Creating user",
            kv("username", request.getUsername()),
            kv("email", request.getEmail()));
        // ...
    }
}
```

#### Metrics

```java
@RestController
@Timed // Micrometer metrics
public class UserController {

    @Counted("user.creation")
    @PostMapping("/users")
    public ResponseEntity<UserDTO> createUser(@RequestBody CreateUserRequest request) {
        // ...
    }
}
```

#### Distributed Tracing

```java
@RestController
public class UserController {

    @NewSpan("create-user")
    @PostMapping("/users")
    public ResponseEntity<UserDTO> createUser(
            @SpanTag("user.email") @RequestBody CreateUserRequest request) {
        // Automatically traced
    }
}
```

### 6. Testing Strategy

#### Unit Tests

```java
@Test
void shouldCreateUser() {
    // Given
    CreateUserRequest request = CreateUserRequest.builder()
        .username("john")
        .email("john@example.com")
        .build();

    // When
    User user = userService.createUser(request);

    // Then
    assertThat(user).isNotNull();
    assertThat(user.getUsername()).isEqualTo("john");
}
```

#### Contract Tests

```java
@PactTestFor(providerName = "user-service")
class UserServiceContractTest {

    @Pact(consumer = "company-service")
    public RequestResponsePact createPact(PactDslWithProvider builder) {
        return builder
            .uponReceiving("get user by id")
            .path("/api/v1/users/123")
            .method("GET")
            .willRespondWith()
            .status(200)
            .body(new PactDslJsonBody()
                .uuid("id")
                .stringType("username")
                .stringType("email"))
            .toPact();
    }
}
```

---

## 📊 Compliance Checklist

### Code Quality

- [ ] No magic numbers/strings
- [ ] Methods < 20 lines
- [ ] Classes < 200 lines
- [ ] Cyclomatic complexity < 10
- [ ] Test coverage > 80%

### Architecture

- [ ] Clear layer separation
- [ ] No circular dependencies
- [ ] DTO for external communication
- [ ] Proper exception handling
- [ ] Standardized API responses

### **🆔 UUID Type Safety (MANDATORY)** ⭐⭐⭐

- [ ] Database columns use UUID type (not VARCHAR)
- [ ] Entity fields are UUID type (not String)
- [ ] Repository parameters are UUID type
- [ ] Service methods use UUID parameters
- [ ] Controller @PathVariable uses UUID type
- [ ] Feign Client parameters are UUID type
- [ ] UUID→String conversion ONLY at boundaries (DTO, Kafka, Logs)
- [ ] No manual UUID manipulation in business logic
- [ ] Tenant ID always from SecurityContext (never from request)

**📚 See:** [DATA_TYPES_STANDARDS.md](DATA_TYPES_STANDARDS.md) - Complete UUID guide

### Microservices

- [ ] Service autonomy
- [ ] Database per service
- [ ] Event-driven communication
- [ ] Circuit breakers implemented
- [ ] Centralized logging

### Security

- [ ] JWT/OAuth2 authentication
- [ ] Role-based authorization
- [ ] Input validation
- [ ] SQL injection prevention
- [ ] XSS protection
- [ ] UUID type safety enforced (prevents ID manipulation)

### DevOps

- [ ] Dockerized services
- [ ] CI/CD pipelines
- [ ] Environment configurations
- [ ] Health checks
- [ ] Graceful shutdown

---

## 🔗 Related Documents

- [Microservices & API Gateway Standards](MICROSERVICES_API_STANDARDS.md) - ⭐⭐⭐ API Gateway ve Controller standartları
- [Data Types Standards](DATA_TYPES_STANDARDS.md) - ⭐⭐⭐ **UUID ve identifier standartları (MANDATORY)**
- [Code Structure Guide](CODE_STRUCTURE_GUIDE.md) - Proje organizasyonu
- [Database Guide](../database/DATABASE_GUIDE.md) - Veritabanı standartları
- [Architecture Guide](../architecture/README.md) - Sistem mimarisi

---

## 📝 Quick Reference

### Naming Conventions

```java
// Classes: PascalCase
public class UserService { }

// Methods: camelCase
public User findUserById(UUID id) { }

// Constants: UPPER_SNAKE_CASE
public static final String DEFAULT_ROLE = "USER";

// Packages: lowercase
package com.fabricmanagement.user.service;
```

### REST API Standards

```
GET    /api/v1/users          - List all
GET    /api/v1/users/{id}     - Get one
POST   /api/v1/users          - Create
PUT    /api/v1/users/{id}     - Update
DELETE /api/v1/users/{id}     - Delete
```

### HTTP Status Codes

- `200 OK` - Successful GET/PUT
- `201 Created` - Successful POST
- `204 No Content` - Successful DELETE
- `400 Bad Request` - Validation error
- `401 Unauthorized` - Authentication required
- `403 Forbidden` - No permission
- `404 Not Found` - Resource not found
- `409 Conflict` - Duplicate resource
- `500 Internal Server Error` - Server error

---

## 🔗 Loose Coupling Principles

### Nedir?

**Loose Coupling**, yazılım bileşenlerinin birbirine minimum bağımlılıkla tasarlanması prensididir. Bir bileşendeki değişiklik, diğer bileşenleri minimum seviyede etkilemelidir.

### Neden Önemli?

- ✅ **Bakım Kolaylığı**: Bir servis değiştiğinde diğerleri etkilenmez
- ✅ **Test Edilebilirlik**: Bileşenler bağımsız test edilebilir
- ✅ **Esneklik**: Teknoloji değişikliği kolay
- ✅ **Ölçeklenebilirlik**: Servisler bağımsız scale edilebilir
- ✅ **Paralel Geliştirme**: Ekipler bağımsız çalışabilir

### 1. Dependency Injection (Constructor Injection)

```java
// ✅ DOĞRU: Loose Coupling with Dependency Injection
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;  // Interface
    private final EmailService emailService;      // Interface
    private final EventPublisher eventPublisher;  // Interface

    public void createUser(CreateUserRequest request) {
        // Dependencies are injected, easily mockable
        User user = userRepository.save(new User());
        emailService.sendWelcomeEmail(user);
        eventPublisher.publish(new UserCreatedEvent(user));
    }
}

// ❌ YANLIŞ: Tight Coupling with new keyword
@Service
public class UserService {
    private final UserRepository userRepository = new UserRepositoryImpl();  // Tight coupling!
    private final EmailService emailService = new SmtpEmailService();        // Hard to test!

    public void createUser(CreateUserRequest request) {
        // Cannot mock dependencies, hard to test
    }
}
```

### 2. Interface Segregation (Program to Interfaces)

```java
// ✅ DOĞRU: Depend on interfaces, not concrete classes
public interface NotificationService {
    void sendNotification(String userId, String message);
}

@Service
public class EmailNotificationService implements NotificationService {
    public void sendNotification(String userId, String message) {
        // Email implementation
    }
}

@Service
public class SmsNotificationService implements NotificationService {
    public void sendNotification(String userId, String message) {
        // SMS implementation
    }
}

@Service
@RequiredArgsConstructor
public class UserService {
    private final NotificationService notificationService;  // Interface!

    public void notifyUser(String userId, String message) {
        notificationService.sendNotification(userId, message);
        // Don't care about implementation details
    }
}

// ❌ YANLIŞ: Depend on concrete implementation
@Service
@RequiredArgsConstructor
public class UserService {
    private final EmailNotificationService emailService;  // Concrete class!

    // Now tightly coupled to email, cannot switch to SMS easily
}
```

### 3. Event-Driven Communication

```java
// ✅ DOĞRU: Loose Coupling through events
@Service
@RequiredArgsConstructor
public class OrderService {
    private final EventPublisher eventPublisher;

    public void createOrder(Order order) {
        orderRepository.save(order);

        // Publish event - don't call other services directly
        eventPublisher.publish(new OrderCreatedEvent(order));
    }
}

@Component
@RequiredArgsConstructor
public class InventoryEventListener {
    // Listens to events independently
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // Update inventory
        inventoryService.reserveStock(event.getOrderId());
    }
}

@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    // Also listens independently - no direct dependency
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // Send notification
        notificationService.notifyCustomer(event.getCustomerId());
    }
}

// ❌ YANLIŞ: Direct service coupling
@Service
@RequiredArgsConstructor
public class OrderService {
    private final InventoryService inventoryService;      // Tight coupling!
    private final NotificationService notificationService; // Tight coupling!
    private final ShippingService shippingService;        // Tight coupling!

    public void createOrder(Order order) {
        orderRepository.save(order);

        // Direct calls create tight coupling
        inventoryService.reserveStock(order);
        notificationService.notifyCustomer(order.getCustomerId());
        shippingService.scheduleDelivery(order);

        // If any of these fail, entire operation fails
        // Adding new listener requires changing this service
    }
}
```

### 4. Feign Clients for Microservices

```java
// ✅ DOĞRU: Interface-based Feign Client
@FeignClient(
    name = "user-service",
    url = "${services.user-service.url}",
    fallback = UserServiceClientFallback.class  // Resilience
)
public interface UserServiceClient {
    @GetMapping("/api/v1/users/{userId}")
    ApiResponse<UserDto> getUser(@PathVariable UUID userId);
}

@Service
@RequiredArgsConstructor
public class CompanyService {
    private final UserServiceClient userServiceClient;  // Interface!

    public CompanyResponse getCompanyWithOwner(UUID companyId) {
        Company company = companyRepository.findById(companyId);
        UserDto owner = userServiceClient.getUser(company.getOwnerId()).getData();

        // Loosely coupled - can switch user-service implementation
        return mapToResponse(company, owner);
    }
}

// ❌ YANLIŞ: Direct HTTP calls
@Service
public class CompanyService {
    private final RestTemplate restTemplate = new RestTemplate();  // Tight coupling!

    public CompanyResponse getCompanyWithOwner(UUID companyId) {
        Company company = companyRepository.findById(companyId);

        // Hard-coded URL, no fallback, hard to test
        String url = "http://localhost:8081/api/v1/users/" + company.getOwnerId();
        UserDto owner = restTemplate.getForObject(url, UserDto.class);

        return mapToResponse(company, owner);
    }
}
```

### 5. Configuration Externalization

```java
// ✅ DOĞRU: Externalized configuration
@Configuration
@ConfigurationProperties(prefix = "app.notification")
@Data
public class NotificationConfig {
    private String emailProvider;    // From application.yml
    private String smsProvider;      // From application.yml
    private int retryAttempts;       // From application.yml
}

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationConfig config;  // Injected

    public void send(String message) {
        // Configuration can change without code change
        if ("sendgrid".equals(config.getEmailProvider())) {
            // Use SendGrid
        } else if ("smtp".equals(config.getEmailProvider())) {
            // Use SMTP
        }
    }
}

// ❌ YANLIŞ: Hard-coded configuration
@Service
public class NotificationService {
    private static final String EMAIL_PROVIDER = "sendgrid";  // Hard-coded!
    private static final String SMS_PROVIDER = "twilio";      // Hard-coded!
    private static final int RETRY_ATTEMPTS = 3;              // Hard-coded!

    // Cannot change without recompiling
}
```

### 6. DTO Pattern for API Contracts

```java
// ✅ DOĞRU: DTO layer separates API from domain
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserMapper userMapper;  // Separate mapping

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        User user = userService.getUser(userId);
        UserResponse response = userMapper.toResponse(user);  // DTO conversion
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

// Domain can change without breaking API contract
@Entity
public class User extends BaseEntity {
    private String firstName;
    private String lastName;
    // Internal structure can change freely
}

// API contract stays stable
@Data
public class UserResponse {
    private String id;
    private String fullName;  // Different from domain structure
    // Stable external contract
}

// ❌ YANLIŞ: Direct entity exposure
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(userRepository.findById(userId));  // Exposing entity!
    }
}
// Now API is tightly coupled to database structure
// Cannot change entity without breaking clients
```

### 7. Shared Modules Strategy

```java
// ✅ DOĞRU: Generic shared components
// shared-domain/exception/ResourceNotFoundException.java
public class ResourceNotFoundException extends DomainException {
    // Generic, reusable by all services
}

// shared-infrastructure/config/DefaultWebConfig.java
@Configuration
@ConditionalOnMissingBean(name = "webConfig")  // Can be overridden!
public class DefaultWebConfig {
    // Default configuration, services can customize
}

// ❌ YANLIŞ: Service-specific logic in shared module
// shared-domain/service/UserService.java  ❌ WRONG!
public class UserService {
    // Business logic in shared module creates tight coupling!
}
```

### 8. Database per Service Pattern

```
✅ DOĞRU: Each service has its own database

user-service:
  database: user_db       # Own database
  schema: user_schema

company-service:
  database: company_db    # Own database
  schema: company_schema

contact-service:
  database: contact_db    # Own database
  schema: contact_schema

Benefits:
- Services can be deployed independently
- Database schema changes don't affect other services
- Technology choice freedom (PostgreSQL, MongoDB, etc.)
- Better fault isolation

❌ YANLIŞ: Shared database

all-services:
  database: shared_db     # All services use same DB
  tables:
    - users             # User service tables
    - companies         # Company service tables
    - contacts          # Contact service tables

Problems:
- Schema changes affect all services
- Cannot deploy services independently
- Database becomes bottleneck
- Tight coupling through database
```

### Loose Coupling Checklist

Before committing code, verify:

- [ ] Dependencies injected via constructor (not `new` keyword)
- [ ] Programming to interfaces (not concrete classes)
- [ ] Events used for service-to-service communication
- [ ] Feign Clients used for synchronous calls
- [ ] Configuration externalized to `application.yml`
- [ ] DTOs used for API contracts (not entities)
- [ ] Shared modules contain only generic code
- [ ] Each service has its own database
- [ ] No direct database access across services
- [ ] Services can be deployed independently

### Loose Coupling Benefits

| Without Loose Coupling              | With Loose Coupling                 |
| ----------------------------------- | ----------------------------------- |
| ❌ Difficult to test (mocking hard) | ✅ Easy to test (mock dependencies) |
| ❌ Changes cascade across services  | ✅ Changes isolated to one service  |
| ❌ Cannot deploy independently      | ✅ Independent deployment           |
| ❌ Technology lock-in               | ✅ Technology freedom               |
| ❌ Parallel development difficult   | ✅ Teams work independently         |
| ❌ Single point of failure          | ✅ Better fault isolation           |

---

## ⚠️ Anti-Patterns to Avoid

### ❌ Don't Do This

```java
// God classes
public class UserService {
    // 50+ methods
    // Multiple responsibilities
}

// Over-complicated validation
public class User {
    public void setEmail(String email) {
        // Don't validate here, use @Valid in controller
    }
}

// Shared mutable state
public class SharedCache {
    public static Map<String, Object> cache = new HashMap<>();
}

// Direct entity exposure
@GetMapping("/users/{id}")
public User getUser(@PathVariable UUID id) {
    return userRepository.findById(id); // Entity exposed!
}
```

### ✅ Do This Instead

```java
// Single responsibility
public class UserAuthenticationService {
    // Only authentication logic
}

// Anemic domain model (OUR PATTERN)
@Entity
@Getter
@Setter
public class User extends BaseEntity {
    private String firstName;
    // Pure data holder, business logic in Service
}

// Immutable shared state
public class CacheConfig {
    private final Map<String, Object> cache = Collections.unmodifiableMap(new HashMap<>());
}

// DTO usage
@GetMapping("/users/{id}")
public UserDTO getUser(@PathVariable UUID id) {
    return userMapper.toDTO(userService.findById(id));
}
```

---

## ⭐ Updated Principles (2025-10-10 Refactoring)

### 🎯 Domain Model Strategy: Hybrid Approach

**Philosophy:** Choose domain model based on complexity, not dogma!

---

#### 📊 Decision Matrix: Anemic vs Rich Domain Model

| Domain Complexity                                         | Model Choice | Reasoning                                   |
| --------------------------------------------------------- | ------------ | ------------------------------------------- |
| **Simple CRUD** (User, Contact, Company)                  | Anemic       | Minimal business logic, Lombok sufficient   |
| **Complex Business Rules** (Order, Invoice, Subscription) | Rich         | State machines, invariants, domain events   |
| **Mix** (has both simple + complex behavior)              | Hybrid       | Simple fields anemic, complex behavior rich |

**Rule:** "Complexity drives choice, not ideology"

---

#### ✅ Pattern 1: Anemic Domain Model (Simple CRUD)

**Use When:** CRUD-heavy, minimal business logic, simple validations

```java
// ✅ CORRECT: Entity = Pure data holder (Lombok-powered)
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class User extends BaseEntity {
    private UUID tenantId;
    private String firstName;
    private String lastName;
    private UserStatus status;

    // NO BUSINESS METHODS!
    // Lombok provides getters/setters
    // Business logic → Service layer
    // Computed properties → Mapper layer
}
```

**Benefits:**

- ✅ Lombok eliminates boilerplate (@Getter/@Setter)
- ✅ Business logic → Service layer (testable, mockable)
- ✅ Computed properties → Mapper layer (SRP)
- ✅ Simpler, cleaner, faster development

**Real Result:** User.java: 408 lines → 99 lines (-76%)

---

#### ✅ Pattern 2: Rich Domain Model (Complex Business Logic)

**Use When:** Complex state transitions, business invariants, domain events

```java
// ✅ CORRECT: Entity = Data + Business behavior
@Entity
@Getter  // Only getter, NO @Setter!
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Order extends BaseEntity {
    private UUID tenantId;
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // ✅ Business methods encapsulate domain logic
    public void submit() {
        validateCanSubmit();
        this.status = OrderStatus.SUBMITTED;
        addDomainEvent(new OrderSubmittedEvent(this));
    }

    public void cancel(String reason) {
        validateCanCancel();
        this.status = OrderStatus.CANCELLED;
        addDomainEvent(new OrderCancelledEvent(this, reason));
    }

    public Money calculateTotal() {
        return items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.ZERO, Money::add);
    }

    public void addItem(Product product, int quantity) {
        validateProduct(product);
        OrderItem item = OrderItem.create(product, quantity);
        items.add(item);
    }

    // ✅ Private validation methods (encapsulation)
    private void validateCanSubmit() {
        if (items.isEmpty())
            throw new EmptyOrderException("Cannot submit empty order");
        if (status != OrderStatus.DRAFT)
            throw new InvalidStatusTransitionException("Can only submit DRAFT orders");
    }

    private void validateCanCancel() {
        if (status == OrderStatus.DELIVERED)
            throw new InvalidStatusTransitionException("Cannot cancel delivered order");
    }
}
```

**Benefits:**

- ✅ Business rules encapsulated in domain
- ✅ Invariants protected (no public setters)
- ✅ State transitions controlled
- ✅ Domain events for side effects
- ✅ Self-documenting business logic

---

#### ✅ Pattern 3: Hybrid Model (Mix of Both)

**Use When:** Entity has both simple fields + complex behavior

```java
@Entity
@Getter
@NoArgsConstructor
@SuperBuilder
public class Subscription extends BaseEntity {
    // Simple fields - anemic style
    private UUID tenantId;
    private UUID customerId;
    private String planName;

    @Setter  // OK for simple field
    private Money monthlyPrice;

    // Complex fields - rich style (no setter)
    private SubscriptionStatus status;
    private LocalDateTime trialEndsAt;
    private LocalDateTime nextBillingDate;

    // ✅ Rich behavior for complex logic
    public void activate() {
        if (status != SubscriptionStatus.PENDING) {
            throw new InvalidStatusException("Can only activate PENDING subscription");
        }
        this.status = SubscriptionStatus.ACTIVE;
        this.nextBillingDate = calculateNextBillingDate();
        addDomainEvent(new SubscriptionActivatedEvent(this));
    }

    public boolean isInTrialPeriod() {
        return trialEndsAt != null && LocalDateTime.now().isBefore(trialEndsAt);
    }

    // ✅ Anemic style for simple updates
    // (called from Service after validation)
    protected void updatePlan(String newPlan, Money newPrice) {
        this.planName = newPlan;
        this.monthlyPrice = newPrice;
    }
}
```

---

#### 🎯 When to Use Which Pattern

| Scenario                        | Pattern    | Example                                          |
| ------------------------------- | ---------- | ------------------------------------------------ |
| Simple CRUD, minimal validation | **Anemic** | User, Contact, Company profiles                  |
| Complex state machine           | **Rich**   | Order (Draft → Submitted → Shipped → Delivered)  |
| Business invariants             | **Rich**   | Invoice (total must equal sum of line items)     |
| Complex calculations            | **Rich**   | Subscription (billing cycles, prorations)        |
| Financial transactions          | **Rich**   | Payment (capture, refund, dispute)               |
| Simple lookups/filters          | **Anemic** | Category, Tag, Label                             |
| Has both simple + complex       | **Hybrid** | Subscription (simple fields + complex lifecycle) |

---

#### ⚠️ Rich Domain Model Rules

When using Rich Domain Model, follow these rules:

1. **NO Public Setters** → Use Builder + business methods

```java
// ❌ WRONG
order.setStatus(SUBMITTED);

// ✅ CORRECT
order.submit();
```

2. **Validation Inside Entity** → Protect invariants

```java
public void addItem(OrderItem item) {
    if (status != DRAFT) throw new InvalidStateException();
    items.add(item);
}
```

3. **Domain Events** → Publish on state changes

```java
public void complete() {
    this.status = COMPLETED;
    addDomainEvent(new OrderCompletedEvent(this));
}
```

4. **Service Layer is Thin** → Orchestration only

```java
// ✅ Service orchestrates, Entity enforces rules
@Service
public class OrderService {
    public void submitOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId);
        order.submit();  // Entity handles logic
        orderRepository.save(order);
    }
}
```

---

#### 🚫 What NOT to Do

```java
// ❌ WRONG: Business logic in service for rich domain
@Service
public class OrderService {
    public void submitOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId);

        // ❌ Validation in service (should be in entity)
        if (order.getItems().isEmpty()) throw new EmptyOrderException();
        if (order.getStatus() != DRAFT) throw new InvalidStatusException();

        // ❌ Direct setter (bypasses encapsulation)
        order.setStatus(SUBMITTED);

        orderRepository.save(order);
    }
}

// ✅ CORRECT: Entity encapsulates logic
order.submit();  // All validation inside
```

---

#### 📋 Current Codebase Status

| Service             | Entities                     | Current Pattern | Future Direction          |
| ------------------- | ---------------------------- | --------------- | ------------------------- |
| **user-service**    | User, UserSession            | Anemic ✅       | Keep anemic (simple CRUD) |
| **contact-service** | Contact, ContactVerification | Anemic ✅       | Keep anemic (simple CRUD) |
| **company-service** | Company, CompanySettings     | Anemic ✅       | Keep anemic (simple CRUD) |
| **order-service**   | Order, OrderItem             | Not yet built   | Use Rich Model 🎯         |
| **billing-service** | Invoice, Subscription        | Not yet built   | Use Rich Model 🎯         |

**Philosophy:** Don't refactor existing anemic domains unless they become complex. Choose pattern for NEW domains based on complexity.

---

### 🗺️ Mapper Separation Pattern

**Adopted:** Multiple focused mappers (SRP)

```java
// ✅ CORRECT: Separate mappers by concern
UserMapper       → DTO ↔ Entity mapping
UserEventMapper  → Entity → Event mapping
AuthMapper       → Auth DTOs + JWT claims

// ❌ WRONG: One giant mapper
UserMapper → All mappings (SRP violation)
```

**Mapper Responsibilities:**

| Mapper          | Input             | Output           | Purpose              |
| --------------- | ----------------- | ---------------- | -------------------- |
| UserMapper      | CreateUserRequest | User             | DTO → Entity         |
| UserMapper      | User              | UserResponse     | Entity → DTO         |
| UserEventMapper | User              | UserCreatedEvent | Entity → Event       |
| AuthMapper      | User + Contact    | LoginResponse    | Complex DTO building |

**Service Layer (NO Mapping):**

```java
@Service
public class UserService {

    @Transactional
    public UUID createUser(CreateUserRequest request, UUID tenantId, String createdBy) {
        // NO MAPPING HERE!
        User user = userMapper.fromCreateRequest(request, tenantId, createdBy);
        user = userRepository.save(user);

        // NO EVENT BUILDING HERE!
        eventPublisher.publishUserCreated(
            eventMapper.toCreatedEvent(user, request.getEmail())
        );

        return user.getId();
    }
}
```

**Rule:** Service sees `.builder()` = Move to Mapper!

---

### 🚫 NO Over-Engineering

**Eliminated Unnecessary Abstractions:**

```java
// ❌ REMOVED: Unnecessary classes
- PasswordValidator    → Spring @Valid + @Pattern sufficient
- ContactValidator     → ValidationConstants already exist
- UserValidator        → Spring @Valid handles it
- PageableBuilder      → PageRequest.of() is simple
- EventBuilder         → Lombok @Builder sufficient
- UserEnricher         → UserMapper already does this

// ✅ KEPT: Only necessary
- UserMapper           → DTO transformation needed
- UserEventMapper      → Event transformation needed
- LoginAttemptTracker  → Redis infrastructure needed
```

**YAGNI Applied:** 6 classes removed (-800 LOC)

---

### 📂 Infrastructure Layer Clarity

**Redis/External Concerns → infrastructure/ Layer:**

```java
// ✅ CORRECT:
infrastructure/security/LoginAttemptTracker.java  // Redis operations
infrastructure/client/ContactServiceClient.java   // HTTP client

// ❌ WRONG:
application/service/LoginAttemptService.java  // Redis = infrastructure!
```

**Rule:** Infrastructure concerns (Redis, HTTP, Kafka) → infrastructure/ layer

---

## 📚 Learning Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Microservices Patterns](https://microservices.io/patterns/)
- [Clean Code by Robert C. Martin](https://www.amazon.com/Clean-Code-Handbook-Software-Craftsmanship/dp/0132350882)
- [Domain-Driven Design](https://www.amazon.com/Domain-Driven-Design-Tackling-Complexity-Software/dp/0321125215)
- [Building Microservices](https://www.amazon.com/Building-Microservices-Designing-Fine-Grained-Systems/dp/1491950358)

---

**Last Updated:** 2025-10-12 (Hybrid Domain Model Strategy - Anemic + Rich patterns)  
**Version:** 2.1

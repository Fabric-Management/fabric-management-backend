# 📋 Development Principles & Standards

## 🎯 Overview

Bu doküman, Fabric Management System geliştirmesinde uyulması gereken temel prensipleri ve standartları içerir. Tüm geliştiricilerin bu kurallara uyması beklenmektedir.

---

## ☕ Spring Boot Coding Principles

### 1. Temel Kod Kalitesi

- ✅ **Okunabilir ve Sade Kod**: Kod self-documenting olmalı
- ✅ **Anlamlı İsimlendirme**: Sınıf, metod ve değişken isimleri açıklayıcı olmalı
- ✅ **Sabitler**: Magic number/string yerine constants kullanılmalı
- ✅ **Tek Sorumluluk**: Her metod tek bir iş yapmalı
- ✅ **Minimal Yorum**: Kod kendini açıklamalı, gereksiz yorum olmamalı

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
- **YAGNI**: You Aren't Gonna Need It

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

### DevOps

- [ ] Dockerized services
- [ ] CI/CD pipelines
- [ ] Environment configurations
- [ ] Health checks
- [ ] Graceful shutdown

---

## 🔗 Related Documents

- [Spring Boot Best Practices Analysis](../analysis/SPRING_BOOT_BEST_PRACTICES_ANALYSIS.md) - Detaylı kod analizi
- [Microservice Development Analysis](../analysis/MICROSERVICE_DEVELOPMENT_ANALYSIS.md) - Mikroservis analizi
- [Architecture Guide](../architecture/README.md) - Sistem mimarisi
- [Database Guide](../database/DATABASE_GUIDE.md) - Veritabanı standartları

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

## ⚠️ Anti-Patterns to Avoid

### ❌ Don't Do This

```java
// God classes
public class UserService {
    // 50+ methods
    // Multiple responsibilities
}

// Anemic domain models
public class User {
    // Only getters/setters
    // No business logic
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

// Rich domain models
public class User {
    public void activate() {
        if (this.status != UserStatus.PENDING) {
            throw new IllegalStateException("User must be pending");
        }
        this.status = UserStatus.ACTIVE;
    }
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

## 📚 Learning Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Microservices Patterns](https://microservices.io/patterns/)
- [Clean Code by Robert C. Martin](https://www.amazon.com/Clean-Code-Handbook-Software-Craftsmanship/dp/0132350882)
- [Domain-Driven Design](https://www.amazon.com/Domain-Driven-Design-Tackling-Complexity-Software/dp/0321125215)
- [Building Microservices](https://www.amazon.com/Building-Microservices-Designing-Fine-Grained-Systems/dp/1491950358)

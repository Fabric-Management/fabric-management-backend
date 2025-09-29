# Common Modules Approach

## 📋 Overview

Common modules, Fabric Management System'de **sadece gerçekten gerekli** ortak bileşenleri içerir. Over-engineering'den kaçınarak, test edilebilirlik ve flexibility'yi koruyan minimalist bir yaklaşım benimser.

## 🎯 Minimalist Common Modules

### **✅ KEEP - Essential Components**

#### **1. BaseEntity**

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    @Version
    private Long version;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;

    // Soft delete methods
    public void markAsDeleted() { this.deleted = Boolean.TRUE; }
    public void restore() { this.deleted = Boolean.FALSE; }
    public boolean isDeleted() { return Boolean.TRUE.equals(this.deleted); }
}
```

**Why Keep:**

- Her entity'de gerçekten var
- Audit trail otomatik
- Soft delete standardı
- UUID primary key

#### **2. ApiResponse**

```java
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private List<String> errors;
    private String errorCode;
    private LocalDateTime timestamp;

    // Static factory methods
    public static <T> ApiResponse<T> success(T data);
    public static <T> ApiResponse<T> success(T data, String message);
    public static <T> ApiResponse<T> error(String message);
    public static <T> ApiResponse<T> validationError(List<String> errors);
}
```

**Why Keep:**

- Response standardizasyonu
- Frontend'de tutarlılık
- Error handling standardı
- API documentation kolaylığı

#### **3. GlobalExceptionHandler**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage(), "ENTITY_NOT_FOUND"));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.validationError(ex.getErrors()));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage(), "BUSINESS_RULE_VIOLATION"));
    }
}
```

**Why Keep:**

- Tek yerden hata yönetimi
- Tutarlı error response
- Logging standardı
- Debug kolaylığı

#### **4. Common Exceptions**

```java
// Domain exceptions
public class EntityNotFoundException extends RuntimeException { }
public class BusinessRuleViolationException extends RuntimeException { }
public class ValidationException extends RuntimeException { }

// Security exceptions
public class AuthenticationException extends RuntimeException { }
public class AuthorizationException extends RuntimeException { }
public class JwtTokenExpiredException extends RuntimeException { }
```

**Why Keep:**

- Tutarlı exception handling
- Error code standardı
- Business logic ayrımı
- Security exception'ları

### **❌ REMOVE - Over-Engineering Components**

#### **1. BaseController**

```java
// ❌ REMOVE - Too restrictive
public abstract class BaseController<D extends BaseDto, ID, S extends BaseService<D, ID>> {
    // Generic CRUD operations
}
```

**Why Remove:**

- Test etmeyi zorlaştırır
- Flexibility'yi azaltır
- Generic type complexity
- Custom endpoint'lerde kısıtlama

#### **2. BaseService**

```java
// ❌ REMOVE - Too abstract
public interface BaseService<D extends BaseDto, ID> {
    D create(D dto);
    D update(ID id, D dto);
    // ...
}
```

**Why Remove:**

- Business logic'i kısıtlar
- Service'ler farklı ihtiyaçlar
- Generic interface complexity
- Test mocking zorluğu

#### **3. BaseRepository**

```java
// ❌ REMOVE - JPA already provides
@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity, ID> extends JpaRepository<T, ID> {
    // Custom methods
}
```

**Why Remove:**

- JPA Repository zaten var
- Custom query'ler daha esnek
- Service-specific ihtiyaçlar
- Over-abstraction

#### **4. BaseDto**

```java
// ❌ REMOVE - Unnecessary inheritance
public abstract class BaseDto {
    private UUID id;
    private LocalDateTime createdAt;
    // ...
}
```

**Why Remove:**

- DTO'lar farklı ihtiyaçlar
- Inheritance complexity
- Validation farklılıkları
- Mapping zorluğu

## 🏗️ Simplified Structure

```
common/
├── common-core/                    # Minimalist core
│   ├── domain/
│   │   ├── base/
│   │   │   └── BaseEntity.java     # ✅ KEEP
│   │   └── exception/
│   │       ├── EntityNotFoundException.java
│   │       ├── BusinessRuleViolationException.java
│   │       └── ValidationException.java
│   ├── application/
│   │   └── dto/
│   │       └── ApiResponse.java    # ✅ KEEP
│   └── infrastructure/
│       └── web/
│           └── exception/
│               └── GlobalExceptionHandler.java  # ✅ KEEP
└── common-security/               # Security essentials
    ├── jwt/
    │   ├── JwtTokenProvider.java
    │   └── JwtAuthenticationFilter.java
    ├── context/
    │   └── SecurityContextUtil.java
    └── exception/
        ├── AuthenticationException.java
        ├── AuthorizationException.java
        └── JwtTokenExpiredException.java
```

## 🎯 Usage Guidelines

### **✅ DO - Best Practices**

#### **1. Entity Development**

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    private String username;
    private String email;
    // BaseEntity'den gelen alanlar otomatik
}
```

#### **2. Controller Development**

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID id) {
        UserResponse user = userService.getUser(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(user, "User created successfully"));
    }
}
```

#### **3. Service Development**

```java
@Service
@Transactional
public class UserService {

    public UserResponse getUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return userMapper.toDto(user);
    }

    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessRuleViolationException("Username already exists");
        }

        User user = userMapper.toEntity(request);
        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }
}
```

#### **4. Repository Development**

```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.deleted = false")
    List<User> findAllActive();

    @Query("SELECT u FROM User u WHERE u.deleted = false AND u.id = :id")
    Optional<User> findActiveById(@Param("id") UUID id);
}
```

### **❌ DON'T - Anti-Patterns**

#### **1. Don't Force Inheritance**

```java
// ❌ DON'T
public class UserService extends BaseService<UserDto, UUID> {
    // Forced to implement generic methods
}

// ✅ DO
public class UserService {
    // Service-specific methods only
}
```

#### **2. Don't Over-Abstract**

```java
// ❌ DON'T
public interface BaseRepository<T extends BaseEntity, ID> extends JpaRepository<T, ID> {
    // Generic methods that may not be needed
}

// ✅ DO
public interface UserRepository extends JpaRepository<User, UUID> {
    // Only methods you actually need
}
```

#### **3. Don't Generic Everything**

```java
// ❌ DON'T
public class BaseController<D, ID, S> {
    // Generic CRUD that may not fit all use cases
}

// ✅ DO
public class UserController {
    // Specific endpoints for user operations
}
```

## 🧪 Testing Benefits

### **✅ Easier Testing**

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldGetUser() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        UserResponse result = userService.getUser(userId);

        // Then
        assertThat(result).isNotNull();
    }
}
```

### **✅ Flexible Mocking**

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @MockBean
    private UserService userService;

    @Test
    void shouldReturnUser() throws Exception {
        // Given
        UserResponse userResponse = new UserResponse();
        when(userService.getUser(any())).thenReturn(userResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").exists());
    }
}
```

## 🚀 Benefits of Minimalist Approach

### **1. Testability**

- Mock'lamak kolay
- Unit test'ler basit
- Integration test'ler esnek

### **2. Flexibility**

- Service'ler kendi ihtiyaçlarına göre geliştirilebilir
- Controller'lar özel endpoint'ler ekleyebilir
- Repository'ler custom query'ler yazabilir

### **3. Maintainability**

- Kod daha anlaşılır
- Debug etmek kolay
- Refactoring riski düşük

### **4. Performance**

- Overhead yok
- Generic type erasure yok
- Compile time optimizasyonları

## 📦 Maven Dependencies

```xml
<!-- Minimalist common-core -->
<dependency>
    <groupId>com.fabricmanagement</groupId>
    <artifactId>common-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Essential security -->
<dependency>
    <groupId>com.fabricmanagement</groupId>
    <artifactId>common-security</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 🎯 Conclusion

Minimalist yaklaşım ile:

- **Test edilebilirlik** artar
- **Flexibility** korunur
- **Over-engineering** önlenir
- **Maintainability** artar
- **Performance** optimize edilir

Sadece gerçekten gerekli common bileşenleri kullanarak, projenin esnekliğini ve test edilebilirliğini koruyabiliriz.

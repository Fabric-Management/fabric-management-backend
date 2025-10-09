# 📁 Code Structure Guide - Kod Nereye Yazılır?

## 🎯 Özet Tablo

| Ne Yazıyorum?               | Nereye Yazacağım?              | Örnek Dosya                  |
| --------------------------- | ------------------------------ | ---------------------------- |
| **REST API Endpoint**       | `/api/controller/`             | `UserController.java`        |
| **İş Mantığı**              | `/application/service/`        | `UserService.java`           |
| **Veritabanı Entity**       | `/domain/entity/`              | `User.java`                  |
| **Veritabanı Sorguları**    | `/infrastructure/persistence/` | `UserRepository.java`        |
| **DTO (Request/Response)**  | `/api/dto/`                    | `UserDTO.java`               |
| **Mapper (Dönüşüm)**        | `/application/mapper/`         | `UserMapper.java`            |
| **Exception**               | `/domain/exception/`           | `UserNotFoundException.java` |
| **Event**                   | `/domain/event/`               | `UserCreatedEvent.java`      |
| **Config**                  | `/infrastructure/config/`      | `SecurityConfig.java`        |
| **External API Client**     | `/infrastructure/client/`      | `EmailServiceClient.java`    |
| **Kafka Producer/Consumer** | `/infrastructure/messaging/`   | `UserEventPublisher.java`    |
| **Utility/Helper**          | `/application/util/`           | `StringUtils.java`           |
| **Constant**                | `/domain/constant/`            | `UserStatus.java`            |
| **Test**                    | `/src/test/java/...`           | `UserServiceTest.java`       |

---

## 📂 Detaylı Klasör Yapısı

```
services/
└── user-service/
    └── src/
        ├── main/
        │   ├── java/com/fabricmanagement/user/
        │   │   ├── 🎯 api/                    # Dış dünyaya açılan katman
        │   │   │   ├── controller/            # REST endpoints
        │   │   │   │   └── UserController.java
        │   │   │   ├── dto/                   # Data transfer objects
        │   │   │   │   ├── request/
        │   │   │   │   │   └── CreateUserRequest.java
        │   │   │   │   └── response/
        │   │   │   │       └── UserResponse.java
        │   │   │   └── filter/                # Security filters
        │   │   │       └── JwtAuthFilter.java
        │   │   │
        │   │   ├── 💼 application/            # İş mantığı katmanı
        │   │   │   ├── service/               # Business logic
        │   │   │   │   ├── UserService.java
        │   │   │   │   └── impl/
        │   │   │   │       └── UserServiceImpl.java
        │   │   │   ├── mapper/                # Entity-DTO mapping
        │   │   │   │   └── UserMapper.java
        │   │   │   ├── validator/             # Business validations
        │   │   │   │   └── UserValidator.java
        │   │   │   └── util/                  # Utilities
        │   │   │       └── PasswordEncoder.java
        │   │   │
        │   │   ├── 🏛️ domain/                 # Domain katmanı
        │   │   │   ├── entity/                # JPA entities
        │   │   │   │   └── User.java
        │   │   │   ├── valueobject/           # Value objects
        │   │   │   │   └── Email.java
        │   │   │   ├── event/                 # Domain events
        │   │   │   │   └── UserCreatedEvent.java
        │   │   │   ├── exception/             # Domain exceptions
        │   │   │   │   └── InvalidEmailException.java
        │   │   │   └── constant/              # Enums & constants
        │   │   │       └── UserStatus.java
        │   │   │
        │   │   └── 🔧 infrastructure/         # Altyapı katmanı
        │   │       ├── persistence/           # Database access
        │   │       │   ├── UserRepository.java
        │   │       │   └── specification/
        │   │       │       └── UserSpecification.java
        │   │       ├── config/                # Configurations
        │   │       │   ├── SecurityConfig.java
        │   │       │   └── KafkaConfig.java
        │   │       ├── client/                # External services
        │   │       │   └── ContactServiceClient.java
        │   │       ├── messaging/             # Message queue
        │   │       │   ├── producer/
        │   │       │   │   └── UserEventProducer.java
        │   │       │   └── consumer/
        │   │       │       └── UserEventConsumer.java
        │   │       └── cache/                 # Cache layer
        │   │           └── UserCacheService.java
        │   │
        │   └── resources/
        │       ├── application.yml            # Configuration
        │       ├── application-local.yml      # Local profile
        │       ├── application-docker.yml     # Docker profile
        │       └── db/migration/              # Flyway migrations
        │           └── V1__create_user_table.sql
        │
        └── test/
            └── java/com/fabricmanagement/user/
                ├── unit/                      # Unit tests
                │   └── UserServiceTest.java
                ├── integration/               # Integration tests
                │   └── UserControllerIT.java
                └── fixture/                   # Test data
                    └── UserFixture.java
```

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

#### C. Service

```java
// 📍 application/service/AuthService.java
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserEventProducer eventProducer;

    public UserResponse register(RegisterUserRequest request) {
        // 1. Validation
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email zaten kayıtlı");
        }

        // 2. Create entity
        User user = User.builder()
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .status(UserStatus.PENDING_VERIFICATION)
            .build();

        // 3. Save
        user = userRepository.save(user);

        // 4. Publish event
        eventProducer.publishUserRegistered(
            new UserRegisteredEvent(user.getId(), user.getEmail())
        );

        // 5. Return response
        return userMapper.toResponse(user);
    }
}
```

#### D. Repository

```java
// 📍 infrastructure/persistence/UserRepository.java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.status = :status")
    List<User> findByStatus(@Param("status") UserStatus status);

    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") UserStatus status);
}
```

#### E. Entity

```java
// 📍 domain/entity/User.java
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserSession> sessions;
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

## 💡 İpuçları

1. **Her katman tek sorumluluk taşımalı**

   - Controller: HTTP request/response
   - Service: İş mantığı
   - Repository: Veri erişimi

2. **DTO kullanın**

   - Entity'leri dışarı açmayın
   - Request/Response için ayrı DTO'lar

3. **Mapper kullanın**

   - MapStruct otomatik mapping sağlar
   - Boilerplate kod azalır

4. **Test yazın**

   - Her service method'u için unit test
   - Her endpoint için integration test

5. **Exception handling**
   - Global exception handler kullanın
   - Anlamlı hata mesajları verin

---

## 📚 Daha Fazla Bilgi

- [Development Principles](PRINCIPLES.md) - Kodlama standartları
- [Quick Start Guide](QUICK_START.md) - Hızlı başlangıç
- [Testing Guide](TESTING_GUIDE.md) - Test yazma kılavuzu
- [API Documentation](../api/README.md) - API referansı

---

**Sorularınız mı var?** Slack: #fabric-dev  
**Last Updated:** 2025-10-09 20:00 UTC+1  
**Version:** 1.0.0  
**Status:** ✅ Active

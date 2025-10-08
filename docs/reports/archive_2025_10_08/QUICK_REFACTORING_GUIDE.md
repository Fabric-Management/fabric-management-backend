# 🚀 Hızlı Refactoring Rehberi

**Hedef:** 2 haftada %40 kod kalitesi iyileştirmesi

---

## 📋 Öncelik Sırası

### 🔴 Hafta 1: Mapper Pattern (Kritik)

#### Problem

```java
// ❌ KÖTÜ: UserService.java içinde 65+ satır mapping logic
private UserResponse mapToResponse(User user) {
    String email = null;
    String phone = null;
    try {
        ApiResponse<List<ContactDto>> response = contactServiceClient...;
        // 30+ satır contact işleme
    }
    return UserResponse.builder()...
}
```

#### Çözüm

```bash
# 1. Mapper sınıfları oluştur
mkdir -p services/user-service/src/main/java/com/fabricmanagement/user/application/mapper
```

**UserMapper.java**

```java
package com.fabricmanagement.user.application.mapper;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final ContactServiceClient contactClient;

    public UserResponse toResponse(User user) {
        ContactInfo contact = fetchContactInfo(user.getId());

        return UserResponse.builder()
            .id(user.getId())
            .tenantId(user.getTenantId())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .email(contact.getEmail())
            .phone(contact.getPhone())
            .status(user.getStatus().name())
            .createdAt(user.getCreatedAt())
            .build();
    }

    public List<UserResponse> toResponseList(List<User> users) {
        // Batch fetch for performance
        Set<UUID> userIds = users.stream()
            .map(User::getId)
            .collect(Collectors.toSet());

        Map<UUID, ContactInfo> contactMap = fetchContactInfoBatch(userIds);

        return users.stream()
            .map(user -> toResponse(user, contactMap.get(user.getId())))
            .collect(Collectors.toList());
    }

    public User toEntity(CreateUserRequest request, UUID tenantId, String createdBy) {
        return User.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .displayName(request.getDisplayName())
            .status(UserStatus.PENDING_VERIFICATION)
            .registrationType(RegistrationType.DIRECT_REGISTRATION)
            .role(request.getRole() != null ? request.getRole() : "USER")
            .createdBy(createdBy)
            .updatedBy(createdBy)
            .deleted(false)
            .version(0L)
            .build();
    }

    private ContactInfo fetchContactInfo(UUID userId) {
        try {
            ApiResponse<List<ContactDto>> response =
                contactClient.getContactsByOwner(userId.toString());

            if (response == null || response.getData() == null) {
                return ContactInfo.empty();
            }

            List<ContactDto> contacts = response.getData();
            String email = contacts.stream()
                .filter(c -> "EMAIL".equals(c.getContactType()))
                .filter(ContactDto::isPrimary)
                .findFirst()
                .map(ContactDto::getContactValue)
                .orElse(null);

            String phone = contacts.stream()
                .filter(c -> "PHONE".equals(c.getContactType()))
                .filter(ContactDto::isPrimary)
                .findFirst()
                .map(ContactDto::getContactValue)
                .orElse(null);

            return new ContactInfo(email, phone);
        } catch (Exception e) {
            log.warn("Failed to fetch contacts for user {}", userId);
            return ContactInfo.empty();
        }
    }

    private Map<UUID, ContactInfo> fetchContactInfoBatch(Set<UUID> userIds) {
        // TODO: Implement batch API
        // For now, fallback to individual calls (will be optimized in Sprint 3)
        return userIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                this::fetchContactInfo
            ));
    }

    @Value
    @AllArgsConstructor
    public static class ContactInfo {
        String email;
        String phone;

        public static ContactInfo empty() {
            return new ContactInfo(null, null);
        }
    }
}
```

**UserService.java (Refactored)**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;  // ← Mapper injection
    private final UserEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId, UUID tenantId) {
        User user = findActiveUserOrThrow(userId, tenantId);
        return userMapper.toResponse(user);  // ← Mapper kullan
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UUID tenantId) {
        List<User> users = userRepository.findByTenantId(tenantId);
        return userMapper.toResponseList(users);  // ← Batch mapper
    }

    @Transactional
    public UUID createUser(CreateUserRequest request, UUID tenantId, String createdBy) {
        User user = userMapper.toEntity(request, tenantId, createdBy);
        user = userRepository.save(user);
        eventPublisher.publishUserCreated(user);
        return user.getId();
    }

    private User findActiveUserOrThrow(UUID userId, UUID tenantId) {
        return userRepository.findById(userId)
            .filter(User::isNotDeleted)
            .filter(u -> u.getTenantId().equals(tenantId))
            .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
```

**Sonuç:**

- ✅ UserService: 370 satır → 120 satır (-67%)
- ✅ Mapper logic ayrıldı
- ✅ Test edilebilirlik arttı

---

### 🔴 Hafta 1: Base Controller Pattern

#### Problem

```java
// ❌ Her controller'da tekrar
UUID tenantId = SecurityContextHolder.getCurrentTenantId();
String userId = SecurityContextHolder.getCurrentUserId();
```

#### Çözüm

**SecurityContext.java**

```java
package com.fabricmanagement.shared.application.context;

@Value
@Builder
public class SecurityContext {
    UUID tenantId;
    String userId;
    String username;
    Set<String> roles;

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
```

**CurrentSecurityContext.java (Annotation)**

```java
package com.fabricmanagement.shared.application.annotation;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentSecurityContext {
}
```

**SecurityContextResolver.java**

```java
package com.fabricmanagement.shared.application.resolver;

@Component
public class SecurityContextResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentSecurityContext.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        return SecurityContext.builder()
            .tenantId(SecurityContextHolder.getCurrentTenantId())
            .userId(SecurityContextHolder.getCurrentUserId())
            .username(SecurityContextHolder.getCurrentUsername())
            .roles(SecurityContextHolder.getCurrentRoles())
            .build();
    }
}
```

**WebConfig.java**

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private SecurityContextResolver securityContextResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(securityContextResolver);
    }
}
```

**UserController.java (Refactored)**

```java
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @PathVariable UUID userId,
            @CurrentSecurityContext SecurityContext ctx) {  // ← Clean injection

        UserResponse user = userService.getUser(userId, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @CurrentSecurityContext SecurityContext ctx) {

        UUID userId = userService.createUser(request, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(userId, "User created successfully"));
    }
}
```

**Sonuç:**

- ✅ Her controller method 2-3 satır kısa
- ✅ Kod tekrarı %90 azaldı
- ✅ Test mock'lama kolay

---

### ⚠️ Hafta 2: Repository Custom Methods

#### Problem

```java
// ❌ Her service'de tekrar
User user = userRepository.findById(userId)
    .filter(u -> !u.isDeleted())
    .filter(u -> u.getTenantId().equals(tenantId))
    .orElseThrow(...);
```

#### Çözüm

**UserRepository.java**

```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds active user by ID and tenant
     */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.tenantId = :tenantId AND u.deleted = false")
    Optional<User> findActiveByIdAndTenantId(
        @Param("id") UUID id,
        @Param("tenantId") UUID tenantId);

    /**
     * Finds all active users by tenant
     */
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.deleted = false")
    List<User> findAllActiveByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Counts active users by tenant
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.tenantId = :tenantId AND u.deleted = false AND u.status = 'ACTIVE'")
    long countActiveUsersByTenant(@Param("tenantId") UUID tenantId);

    /**
     * Checks if user exists (active)
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.id = :id AND u.tenantId = :tenantId AND u.deleted = false")
    boolean existsActiveByIdAndTenantId(
        @Param("id") UUID id,
        @Param("tenantId") UUID tenantId);
}
```

**UserService.java (Refactored)**

```java
@Service
public class UserService {

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId, UUID tenantId) {
        User user = userRepository.findActiveByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public boolean userExists(UUID userId, UUID tenantId) {
        return userRepository.existsActiveByIdAndTenantId(userId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UUID tenantId) {
        List<User> users = userRepository.findAllActiveByTenantId(tenantId);
        return userMapper.toResponseList(users);
    }
}
```

**Sonuç:**

- ✅ Service methodları 3-4 satır kısa
- ✅ Query logic database layer'da
- ✅ Test edilebilirlik arttı

---

### ⚠️ Hafta 2: Exception Standardization

#### Problem

```java
// ❌ Generic RuntimeException
.orElseThrow(() -> new RuntimeException("User not found"));
```

#### Çözüm

**UserNotFoundException.java**

```java
package com.fabricmanagement.shared.domain.exception;

public class UserNotFoundException extends DomainException {
    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId, "USER_NOT_FOUND");
    }
}
```

**CompanyNotFoundException.java**

```java
package com.fabricmanagement.shared.domain.exception;

public class CompanyNotFoundException extends DomainException {
    public CompanyNotFoundException(UUID companyId) {
        super("Company not found: " + companyId, "COMPANY_NOT_FOUND");
    }
}
```

**GlobalExceptionHandler.java**

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(CompanyNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCompanyNotFound(CompanyNotFoundException ex) {
        log.warn("Company not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex) {
        log.error("Domain exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }
}
```

**Kullanım**

```java
// ✅ İYİ
User user = userRepository.findActiveByIdAndTenantId(userId, tenantId)
    .orElseThrow(() -> new UserNotFoundException(userId));

Company company = companyRepository.findActiveByIdAndTenantId(companyId, tenantId)
    .orElseThrow(() -> new CompanyNotFoundException(companyId));
```

---

## 📊 2 Haftalık Sonuç

| Metrik                     | Önce          | Sonra | İyileştirme |
| -------------------------- | ------------- | ----- | ----------- |
| **UserService Satır**      | 370           | 150   | 59% ↓       |
| **Kod Tekrarı**            | %35           | %15   | 57% ↓       |
| **Controller Kod Tekrarı** | Her method'da | Yok   | 95% ↓       |
| **Test Edilebilirlik**     | 5/10          | 9/10  | +80%        |

---

## 🎯 Hızlı Checklist

### Hafta 1

- [ ] UserMapper sınıfı oluştur
- [ ] CompanyMapper sınıfı oluştur
- [ ] SecurityContext + Annotation
- [ ] SecurityContextResolver
- [ ] Controller'ları refactor et

### Hafta 2

- [ ] Repository custom methodlar
- [ ] Exception sınıfları
- [ ] GlobalExceptionHandler
- [ ] UserService refactor
- [ ] CompanyService refactor

---

## 💡 Sonraki Adımlar (Hafta 3-4)

1. **Performance:**

   - Batch API endpoints
   - Redis cache
   - N+1 query fix

2. **CQRS Simplification:**

   - Company Service handler'ları kaldır
   - Basit CRUD pattern

3. **Testing:**
   - Unit tests
   - Integration tests

---

**Not:** Bu refactoring'i incrementally yap. Her değişiklikten sonra test et. Bir seferde her şeyi değiştirme!

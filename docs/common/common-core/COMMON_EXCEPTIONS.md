# Common Exceptions

## 📋 Overview

Common Exceptions, tüm microservice'lerde tutarlı exception handling için kullanılan ortak exception sınıflarıdır. Business logic ayrımı ve error code standardı sağlar.

## 🏗️ Implementation

### **Domain Exceptions**

```java
// Entity not found exception
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Business rule violation exception
public class BusinessRuleViolationException extends RuntimeException {
    public BusinessRuleViolationException(String message) {
        super(message);
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Validation exception
public class ValidationException extends RuntimeException {
    private final List<String> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public ValidationException(List<String> errors) {
        super("Validation failed");
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}

// Core domain exception
public class CoreDomainException extends RuntimeException {
    public CoreDomainException(String message) {
        super(message);
    }

    public CoreDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Domain exception
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Not found exception
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### **Security Exceptions**

```java
// Authentication exception
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Authorization exception
public class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}

// JWT token expired exception
public class JwtTokenExpiredException extends RuntimeException {
    public JwtTokenExpiredException(String message) {
        super(message);
    }

    public JwtTokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}

// JWT token invalid exception
public class JwtTokenInvalidException extends RuntimeException {
    public JwtTokenInvalidException(String message) {
        super(message);
    }

    public JwtTokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Unauthorized exception
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## 🎯 Features

### **1. Domain Exceptions**

- **EntityNotFoundException**: Entity bulunamadığında
- **BusinessRuleViolationException**: İş kuralı ihlalinde
- **ValidationException**: Validation hatalarında
- **CoreDomainException**: Core domain hatalarında
- **DomainException**: Domain hatalarında
- **NotFoundException**: Kaynak bulunamadığında

### **2. Security Exceptions**

- **AuthenticationException**: Kimlik doğrulama hatalarında
- **AuthorizationException**: Yetkilendirme hatalarında
- **JwtTokenExpiredException**: JWT token süresi dolduğunda
- **JwtTokenInvalidException**: JWT token geçersiz olduğunda
- **UnauthorizedException**: Yetkisiz erişimde

## 🔧 Usage

### **Service Implementation**

```java
@Service
@Transactional
public class UserService {

    public UserResponse getUser(UUID userId) {
        User user = userRepository.findActiveById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        return userMapper.toDto(user);
    }

    public UserResponse createUser(CreateUserRequest request) {
        // Validate unique constraints
        if (userRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
            throw new BusinessRuleViolationException("Username already exists");
        }

        if (userRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new BusinessRuleViolationException("Email already exists");
        }

        // Validate business rules
        if (request.getAge() < 18) {
            throw new BusinessRuleViolationException("User must be at least 18 years old");
        }

        User user = userMapper.toEntity(request);
        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }

    public void deleteUser(UUID userId) {
        User user = userRepository.findActiveById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        user.markAsDeleted();
        userRepository.save(user);
    }
}
```

### **Authentication Service**

```java
@Service
@Transactional
public class AuthenticationService {

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameAndDeletedFalse(request.getUsername())
            .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid credentials");
        }

        if (user.isAccountLocked()) {
            throw new AuthenticationException("Account is locked");
        }

        String token = jwtTokenProvider.createToken(user.getId().toString());
        return AuthResponse.builder()
            .accessToken(token)
            .user(mapToUserResponse(user))
            .build();
    }

    public void validateToken(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new JwtTokenInvalidException("Invalid token");
        }
    }
}
```

### **Authorization Service**

```java
@Service
public class AuthorizationService {

    public void checkPermission(UUID userId, String permission) {
        User user = userRepository.findActiveById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.hasPermission(permission)) {
            throw new AuthorizationException("User does not have permission: " + permission);
        }
    }

    public void checkRole(UUID userId, String role) {
        User user = userRepository.findActiveById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.hasRole(role)) {
            throw new AuthorizationException("User does not have role: " + role);
        }
    }
}
```

## 📊 Error Response Examples

### **Entity Not Found**

```json
{
  "success": false,
  "message": "User not found with id: 123e4567-e89b-12d3-a456-426614174000",
  "errorCode": "ENTITY_NOT_FOUND",
  "timestamp": "2024-01-15T10:30:00"
}
```

### **Business Rule Violation**

```json
{
  "success": false,
  "message": "Username already exists",
  "errorCode": "BUSINESS_RULE_VIOLATION",
  "timestamp": "2024-01-15T10:30:00"
}
```

### **Validation Error**

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": ["Username is required", "Email format is invalid"],
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2024-01-15T10:30:00"
}
```

### **Authentication Error**

```json
{
  "success": false,
  "message": "Invalid credentials",
  "errorCode": "AUTHENTICATION_ERROR",
  "timestamp": "2024-01-15T10:30:00"
}
```

### **Authorization Error**

```json
{
  "success": false,
  "message": "User does not have permission: USER_DELETE",
  "errorCode": "AUTHORIZATION_ERROR",
  "timestamp": "2024-01-15T10:30:00"
}
```

## 🧪 Testing Benefits

### **Easy Testing**

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldThrowEntityNotFound() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUser(userId))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("User not found with id: " + userId);
    }

    @Test
    void shouldThrowBusinessRuleViolation() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("existing_user");
        when(userRepository.existsByUsernameAndDeletedFalse("existing_user")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userService.createUser(request))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessage("Username already exists");
    }
}
```

## 🎯 Benefits

1. **Consistent Error Handling**: Tutarlı hata yönetimi
2. **Error Code Standardization**: Hata kodu standardı
3. **Business Logic Separation**: İş mantığı ayrımı
4. **Security Exception Handling**: Güvenlik hata yönetimi
5. **Easy Testing**: Kolay test edilebilirlik
6. **Debugging**: Debug kolaylığı

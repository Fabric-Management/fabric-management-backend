# GlobalExceptionHandler

## 📋 Overview

GlobalExceptionHandler, tüm microservice'lerde merkezi hata yönetimi sağlar. Tutarlı error response formatı, logging ve debug kolaylığı için kullanılır.

## 🏗️ Implementation

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage(), "ENTITY_NOT_FOUND"));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleViolationException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage(), "BUSINESS_RULE_VIOLATION"));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest()
            .body(ApiResponse.validationError(ex.getErrors()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(ex.getMessage(), "AUTHENTICATION_ERROR"));
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorization(AuthorizationException ex) {
        log.warn("Authorization error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(ex.getMessage(), "AUTHORIZATION_ERROR"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());

        log.warn("Validation error: {}", errors);
        return ResponseEntity.badRequest()
            .body(ApiResponse.validationError(errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations()
            .stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.toList());

        log.warn("Constraint violation: {}", errors);
        return ResponseEntity.badRequest()
            .body(ApiResponse.validationError(errors));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ApiResponse.error("Method not supported", "METHOD_NOT_ALLOWED"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(ApiResponse.error("Media type not supported", "UNSUPPORTED_MEDIA_TYPE"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("Internal server error", "INTERNAL_SERVER_ERROR"));
    }
}
```

## 🎯 Features

### **1. Domain Exceptions**

- **EntityNotFoundException**: Entity bulunamadığında
- **BusinessRuleViolationException**: İş kuralı ihlalinde
- **ValidationException**: Validation hatalarında

### **2. Security Exceptions**

- **AuthenticationException**: Kimlik doğrulama hatalarında
- **AuthorizationException**: Yetkilendirme hatalarında

### **3. Spring Exceptions**

- **MethodArgumentNotValidException**: @Valid annotation hatalarında
- **ConstraintViolationException**: Bean validation hatalarında
- **HttpRequestMethodNotSupportedException**: Desteklenmeyen HTTP method
- **HttpMediaTypeNotSupportedException**: Desteklenmeyen media type

### **4. Generic Exception**

- **Exception**: Beklenmeyen hatalar için

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
        if (userRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
            throw new BusinessRuleViolationException("Username already exists");
        }

        if (userRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new BusinessRuleViolationException("Email already exists");
        }

        User user = userMapper.toEntity(request);
        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }
}
```

### **Controller Implementation**

```java
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        UserResponse user = userService.getUser(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(user, "User created successfully"));
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
  "errors": [
    "username: Username is required",
    "email: Email format is invalid"
  ],
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

## 🧪 Testing Benefits

### **Easy Testing**

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @MockBean
    private UserService userService;

    @Test
    void shouldReturnNotFound() throws Exception {
        // Given
        when(userService.getUser(any())).thenThrow(new EntityNotFoundException("User not found"));

        // When & Then
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("User not found"))
            .andExpect(jsonPath("$.errorCode").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void shouldReturnValidationError() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
```

## 🎯 Benefits

1. **Centralized Error Handling**: Merkezi hata yönetimi
2. **Consistent Error Format**: Tutarlı hata formatı
3. **Logging**: Otomatik logging
4. **Debugging**: Debug kolaylığı
5. **Frontend Integration**: Frontend'de kolay entegrasyon
6. **Testing**: Test yazımı kolaylığı

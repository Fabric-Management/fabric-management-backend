# ApiResponse

## 📋 Overview

ApiResponse, tüm REST endpoint'leri için standart response formatı sağlar. Tutarlı API yapısı, error handling ve frontend entegrasyonu için kullanılır.

## 🏗️ Implementation

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private List<String> errors;
    private String errorCode;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Creates a successful response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .build();
    }

    /**
     * Creates a successful response with data and message.
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .message(message)
            .build();
    }

    /**
     * Creates a successful response with only message.
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .build();
    }

    /**
     * Creates an error response with message.
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .build();
    }

    /**
     * Creates an error response with message and error code.
     */
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .build();
    }

    /**
     * Creates an error response with validation errors.
     */
    public static <T> ApiResponse<T> validationError(List<String> errors) {
        return ApiResponse.<T>builder()
            .success(false)
            .message("Validation failed")
            .errors(errors)
            .errorCode("VALIDATION_ERROR")
            .build();
    }
}
```

## 🎯 Features

### **1. Success Response**

- **success**: true
- **data**: Response data
- **message**: Success message
- **timestamp**: Response timestamp

### **2. Error Response**

- **success**: false
- **message**: Error message
- **errorCode**: Error code
- **errors**: Validation errors
- **timestamp**: Response timestamp

### **3. Static Factory Methods**

- **success(T data)**: Data ile başarılı response
- **success(T data, String message)**: Data ve mesaj ile başarılı response
- **success(String message)**: Sadece mesaj ile başarılı response
- **error(String message)**: Hata mesajı ile response
- **error(String message, String errorCode)**: Hata mesajı ve kod ile response
- **validationError(List<String> errors)**: Validation hataları ile response

## 🔧 Usage

### **Controller Implementation**

```java
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(user, "User created successfully"));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        UserResponse user = userService.getUser(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
        @PathVariable UUID userId,
        @Valid @RequestBody UpdateUserRequest request) {
        UserResponse user = userService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success(user, "User updated successfully"));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsers() {
        List<UserResponse> users = userService.getUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }
}
```

### **Error Handling**

```java
@RestController
public class UserController {

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody CreateUserRequest request) {
        try {
            UserResponse user = userService.createUser(request);
            return ResponseEntity.ok(ApiResponse.success(user));
        } catch (ValidationException ex) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.validationError(ex.getErrors()));
        } catch (BusinessRuleViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), "BUSINESS_RULE_VIOLATION"));
        }
    }
}
```

## 📊 Response Examples

### **Success Response**

```json
{
  "success": true,
  "message": "User created successfully",
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "username": "john.doe",
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "status": "ACTIVE"
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

### **Error Response**

```json
{
  "success": false,
  "message": "User not found",
  "errorCode": "ENTITY_NOT_FOUND",
  "timestamp": "2024-01-15T10:30:00"
}
```

### **Validation Error Response**

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": ["Username is required", "Email format is invalid"],
  "errorCode": "VALIDATION_ERROR",
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
    void shouldReturnUser() throws Exception {
        // Given
        UserResponse userResponse = new UserResponse();
        when(userService.getUser(any())).thenReturn(userResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnError() throws Exception {
        // Given
        when(userService.getUser(any())).thenThrow(new EntityNotFoundException("User not found"));

        // When & Then
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("User not found"))
            .andExpect(jsonPath("$.errorCode").value("ENTITY_NOT_FOUND"));
    }
}
```

## 🎯 Benefits

1. **Consistency**: Tutarlı API response formatı
2. **Frontend Integration**: Frontend'de kolay entegrasyon
3. **Error Handling**: Standart hata yönetimi
4. **Documentation**: API dokümantasyonu kolaylığı
5. **Testing**: Test yazımı kolaylığı
6. **Debugging**: Debug kolaylığı

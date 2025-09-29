# SecurityContextUtil

## 📋 Overview

SecurityContextUtil, Spring Security context'inden current user bilgilerini almak için kullanılan yardımcı sınıftır. Tenant isolation ve audit trail için önemlidir.

## 🏗️ Implementation

```java
@Component
public class SecurityContextUtil {

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationException("User not authenticated");
        }

        if (authentication.getPrincipal() instanceof AuthenticatedUser) {
            return (AuthenticatedUser) authentication.getPrincipal();
        }

        throw new AuthenticationException("Invalid authentication principal");
    }

    public UUID getCurrentUserId() {
        AuthenticatedUser user = getCurrentUser();
        if (user.getUserId() == null) {
            throw new AuthenticationException("User ID not found in token");
        }
        return user.getUserId();
    }

    public String getCurrentUsername() {
        AuthenticatedUser user = getCurrentUser();
        return user.getUsername();
    }

    public String getCurrentUserEmail() {
        AuthenticatedUser user = getCurrentUser();
        return user.getEmail();
    }

    public UUID getCurrentTenantId() {
        AuthenticatedUser user = getCurrentUser();
        if (user.getTenantId() == null) {
            throw new AuthenticationException("Tenant ID not found in token");
        }
        return user.getTenantId();
    }

    public String getCurrentUserRole() {
        AuthenticatedUser user = getCurrentUser();
        return user.getRole();
    }

    public List<GrantedAuthority> getCurrentUserAuthorities() {
        AuthenticatedUser user = getCurrentUser();
        return user.getAuthorities();
    }

    public boolean hasRole(String role) {
        AuthenticatedUser user = getCurrentUser();
        return user.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.toUpperCase()));
    }

    public boolean hasAnyRole(String... roles) {
        AuthenticatedUser user = getCurrentUser();
        return user.getAuthorities().stream()
            .anyMatch(authority ->
                Arrays.stream(roles)
                    .anyMatch(role -> authority.getAuthority().equals("ROLE_" + role.toUpperCase()))
            );
    }

    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
```

## 🎯 Features

### **1. User Information**

- **getCurrentUser()**: Current authenticated user
- **getCurrentUserId()**: Current user ID
- **getCurrentUsername()**: Current username
- **getCurrentUserEmail()**: Current user email

### **2. Tenant Information**

- **getCurrentTenantId()**: Current tenant ID
- **Multi-tenancy support**: Tenant isolation

### **3. Role Information**

- **getCurrentUserRole()**: Current user role
- **getCurrentUserAuthorities()**: Current user authorities
- **hasRole(String role)**: Role kontrolü
- **hasAnyRole(String... roles)**: Multiple role kontrolü

### **4. Authentication Status**

- **isAuthenticated()**: Authentication durumu

## 🔧 Usage

### **Service Implementation**

```java
@Service
@Transactional
public class UserService {

    @Autowired
    private SecurityContextUtil securityContextUtil;

    public UserResponse createUser(CreateUserRequest request) {
        // Get current user info from security context
        UUID currentUserId = securityContextUtil.getCurrentUserId();
        UUID tenantId = securityContextUtil.getCurrentTenantId();
        String currentUsername = securityContextUtil.getCurrentUsername();

        // Create user
        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .tenantId(tenantId)
            .createdBy(currentUsername)
            .build();

        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }

    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        // Get current user info
        String currentUsername = securityContextUtil.getCurrentUsername();

        User user = userRepository.findActiveById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Update fields
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setUpdatedBy(currentUsername);

        User updatedUser = userRepository.save(user);
        return userMapper.toDto(updatedUser);
    }

    public void deleteUser(UUID userId) {
        // Check permissions
        if (!securityContextUtil.hasRole("ADMIN")) {
            throw new AuthorizationException("Only admins can delete users");
        }

        User user = userRepository.findActiveById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.markAsDeleted();
        userRepository.save(user);
    }
}
```

### **Controller Implementation**

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private SecurityContextUtil securityContextUtil;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UUID currentUserId = securityContextUtil.getCurrentUserId();
        UserResponse user = userService.getUser(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUserProfile() {
        UUID currentUserId = securityContextUtil.getCurrentUserId();
        String currentUserEmail = securityContextUtil.getCurrentUserEmail();

        UserProfileResponse profile = UserProfileResponse.builder()
            .userId(currentUserId)
            .email(currentUserEmail)
            .role(securityContextUtil.getCurrentUserRole())
            .build();

        return ResponseEntity.ok(ApiResponse.success(profile));
    }
}
```

### **Method Security**

```java
@Service
public class AdminService {

    @Autowired
    private SecurityContextUtil securityContextUtil;

    @PreAuthorize("hasRole('ADMIN')")
    public void adminOnlyMethod() {
        // This method can only be called by users with ADMIN role
        UUID currentUserId = securityContextUtil.getCurrentUserId();
        // Admin logic
    }

    public void checkUserPermissions() {
        if (!securityContextUtil.hasAnyRole("ADMIN", "MANAGER")) {
            throw new AuthorizationException("Insufficient permissions");
        }

        UUID currentUserId = securityContextUtil.getCurrentUserId();
        // Logic for admin or manager
    }
}
```

## 📊 User Information Examples

### **AuthenticatedUser Object**

```java
public class AuthenticatedUser {
    private UUID userId;           // "123e4567-e89b-12d3-a456-426614174000"
    private String username;       // "john.doe"
    private String email;          // "john.doe@example.com"
    private UUID tenantId;        // "456e7890-e89b-12d3-a456-426614174000"
    private String role;           // "USER"
    private List<GrantedAuthority> authorities; // [ROLE_USER]
}
```

### **Usage Examples**

```java
// Get current user ID
UUID userId = securityContextUtil.getCurrentUserId();

// Get current tenant ID
UUID tenantId = securityContextUtil.getCurrentTenantId();

// Check role
boolean isAdmin = securityContextUtil.hasRole("ADMIN");

// Check multiple roles
boolean isAdminOrManager = securityContextUtil.hasAnyRole("ADMIN", "MANAGER");

// Get current user email
String email = securityContextUtil.getCurrentUserEmail();
```

## 🧪 Testing Benefits

### **Easy Testing**

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private SecurityContextUtil securityContextUtil;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateUserWithCurrentUserInfo() {
        // Given
        UUID currentUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String currentUsername = "admin";

        when(securityContextUtil.getCurrentUserId()).thenReturn(currentUserId);
        when(securityContextUtil.getCurrentTenantId()).thenReturn(tenantId);
        when(securityContextUtil.getCurrentUsername()).thenReturn(currentUsername);

        CreateUserRequest request = new CreateUserRequest();

        // When
        UserResponse result = userService.createUser(request);

        // Then
        assertThat(result).isNotNull();
        verify(securityContextUtil).getCurrentUserId();
        verify(securityContextUtil).getCurrentTenantId();
        verify(securityContextUtil).getCurrentUsername();
    }

    @Test
    void shouldCheckPermissions() {
        // Given
        when(securityContextUtil.hasRole("ADMIN")).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> userService.deleteUser(UUID.randomUUID()))
            .isInstanceOf(AuthorizationException.class)
            .hasMessage("Only admins can delete users");
    }
}
```

## 🎯 Benefits

1. **Tenant Isolation**: Tenant bazlı veri izolasyonu
2. **Audit Trail**: Otomatik audit trail
3. **Security**: Güvenli user bilgisi erişimi
4. **Role-based Access**: Role tabanlı erişim kontrolü
5. **Easy Testing**: Kolay test edilebilirlik
6. **Code Consistency**: Tutarlı kod yapısı

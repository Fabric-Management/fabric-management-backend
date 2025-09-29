# JwtTokenProvider

## 📋 Overview

JwtTokenProvider, JWT token oluşturma, doğrulama ve parsing işlemlerini yönetir. Common Security modülünün temel bileşenidir.

## 🏗️ Implementation

```java
@Component
public class JwtTokenProvider {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtProperties jwtProperties;

    public String createToken(String userId) {
        return jwtUtil.generateToken(userId);
    }

    public Authentication getAuthentication(String token) {
        try {
            String userId = jwtUtil.extractUserId(token);
            String tenantIdStr = jwtUtil.extractTenantId(token);
            String role = jwtUtil.extractRole(token);
            String email = jwtUtil.extractEmail(token);

            List<SimpleGrantedAuthority> authorities = Collections.emptyList();
            if (StringUtils.hasText(role)) {
                authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            }

            AuthenticatedUser authenticatedUser = AuthenticatedUser.builder()
                    .userId(StringUtils.hasText(userId) ? UUID.fromString(userId) : null)
                    .username(userId)
                    .email(email)
                    .tenantId(StringUtils.hasText(tenantIdStr) ? UUID.fromString(tenantIdStr) : null)
                    .role(role)
                    .authorities(authorities)
                    .build();

            return new UsernamePasswordAuthenticationToken(authenticatedUser, "", authorities);
        } catch (JwtTokenExpiredException | JwtTokenInvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtTokenInvalidException("Failed to parse JWT token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            String userId = jwtUtil.extractUserId(token);
            return jwtUtil.validateToken(token, userId);
        } catch (JwtTokenExpiredException | JwtTokenInvalidException e) {
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        return jwtUtil.extractUserId(token);
    }

    public String getTenantIdFromToken(String token) {
        return jwtUtil.extractTenantId(token);
    }

    public String getRoleFromToken(String token) {
        return jwtUtil.extractRole(token);
    }

    public String getEmailFromToken(String token) {
        return jwtUtil.extractEmail(token);
    }

    public long getValidity() {
        return jwtProperties.getValidity();
    }
}
```

## 🎯 Features

### **1. Token Creation**

- **createToken(String userId)**: User ID ile token oluşturma
- **JWT Claims**: User ID, tenant ID, role, email bilgileri

### **2. Token Validation**

- **validateToken(String token)**: Token geçerliliğini kontrol etme
- **getAuthentication(String token)**: Token'dan Authentication objesi oluşturma

### **3. Token Parsing**

- **getUserIdFromToken(String token)**: Token'dan user ID alma
- **getTenantIdFromToken(String token)**: Token'dan tenant ID alma
- **getRoleFromToken(String token)**: Token'dan role alma
- **getEmailFromToken(String token)**: Token'dan email alma

### **4. Configuration**

- **getValidity()**: Token geçerlilik süresi

## 🔧 Usage

### **Authentication Service**

```java
@Service
@Transactional
public class AuthenticationService {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public AuthResponse login(LoginRequest request) {
        // Validate user
        User user = userRepository.findByUsernameAndDeletedFalse(request.getUsername())
            .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        // Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid credentials");
        }

        // Generate token
        String token = jwtTokenProvider.createToken(user.getId().toString());

        return AuthResponse.builder()
            .accessToken(token)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getValidity())
            .user(mapToUserResponse(user))
            .build();
    }

    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }

    public String getUserIdFromToken(String token) {
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
```

### **Security Configuration**

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }
}
```

### **Controller Usage**

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
        @RequestHeader("Authorization") String token) {

        String userId = jwtTokenProvider.getUserIdFromToken(token);
        UserResponse user = userService.getUser(UUID.fromString(userId));

        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
```

## 📊 Token Structure

### **JWT Claims**

```json
{
  "sub": "123e4567-e89b-12d3-a456-426614174000",
  "tenantId": "456e7890-e89b-12d3-a456-426614174000",
  "role": "USER",
  "email": "user@example.com",
  "iat": 1642248000,
  "exp": 1642251600
}
```

### **AuthenticatedUser Object**

```java
public class AuthenticatedUser {
    private UUID userId;
    private String username;
    private String email;
    private UUID tenantId;
    private String role;
    private List<GrantedAuthority> authorities;
}
```

## 🧪 Testing Benefits

### **Easy Testing**

```java
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void shouldCreateToken() {
        // Given
        String userId = "123e4567-e89b-12d3-a456-426614174000";
        when(jwtTokenProvider.createToken(userId)).thenReturn("jwt-token");

        // When
        String token = jwtTokenProvider.createToken(userId);

        // Then
        assertThat(token).isEqualTo("jwt-token");
        verify(jwtTokenProvider).createToken(userId);
    }

    @Test
    void shouldValidateToken() {
        // Given
        String token = "valid-jwt-token";
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);

        // When
        boolean isValid = jwtTokenProvider.validateToken(token);

        // Then
        assertThat(isValid).isTrue();
    }
}
```

## 🎯 Benefits

1. **Token Management**: Merkezi token yönetimi
2. **Security**: Güvenli token işlemleri
3. **Multi-tenancy**: Tenant desteği
4. **Role-based Access**: Role tabanlı erişim
5. **Easy Integration**: Kolay entegrasyon
6. **Testing**: Test edilebilirlik

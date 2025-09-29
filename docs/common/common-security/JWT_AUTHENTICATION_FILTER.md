# JwtAuthenticationFilter

## 📋 Overview

JwtAuthenticationFilter, JWT token'ları doğrulayan ve Spring Security context'ine authentication bilgilerini ekleyen filter'dır. Her request için token validation yapar.

## 🏗️ Implementation

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractTokenFromRequest(request);

            if (token != null && jwtTokenProvider.validateToken(token)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } catch (JwtTokenExpiredException e) {
            handleJwtException(response, "Token expired", HttpStatus.UNAUTHORIZED);
        } catch (JwtTokenInvalidException e) {
            handleJwtException(response, "Invalid token", HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            handleJwtException(response, "Authentication failed", HttpStatus.UNAUTHORIZED);
        }
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void handleJwtException(HttpServletResponse response, String message, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> errorResponse = ApiResponse.error(message, "AUTHENTICATION_ERROR");
        String jsonResponse = new ObjectMapper().writeValueAsString(errorResponse);

        response.getWriter().write(jsonResponse);
    }
}
```

## 🎯 Features

### **1. Token Extraction**

- **extractTokenFromRequest()**: Authorization header'dan token çıkarma
- **Bearer token support**: "Bearer " prefix'i ile token alma

### **2. Token Validation**

- **validateToken()**: Token geçerliliğini kontrol etme
- **getAuthentication()**: Token'dan Authentication objesi oluşturma

### **3. Security Context**

- **setAuthentication()**: Security context'e authentication ekleme
- **Stateless authentication**: Session kullanmadan authentication

### **4. Error Handling**

- **JwtTokenExpiredException**: Token süresi dolduğunda
- **JwtTokenInvalidException**: Token geçersiz olduğunda
- **Generic Exception**: Diğer hatalar için

## 🔧 Usage

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
                .requestMatchers("/api/v1/public/**").permitAll()
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
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        // Authentication is automatically set by JwtAuthenticationFilter
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        UserResponse userResponse = userService.getUser(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(userResponse));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUserProfile() {
        // No need to manually extract token, filter handles it
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        UserProfileResponse profile = UserProfileResponse.builder()
            .userId(user.getUserId())
            .email(user.getEmail())
            .role(user.getRole())
            .build();

        return ResponseEntity.ok(ApiResponse.success(profile));
    }
}
```

### **Service Usage**

```java
@Service
@Transactional
public class UserService {

    @Autowired
    private SecurityContextUtil securityContextUtil;

    public UserResponse getCurrentUser() {
        // SecurityContextUtil uses the authentication set by JwtAuthenticationFilter
        UUID currentUserId = securityContextUtil.getCurrentUserId();
        return userService.getUser(currentUserId);
    }

    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        UUID currentUserId = securityContextUtil.getCurrentUserId();
        String currentUsername = securityContextUtil.getCurrentUsername();

        User user = userRepository.findActiveById(currentUserId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setUpdatedBy(currentUsername);

        User updatedUser = userRepository.save(user);
        return userMapper.toDto(updatedUser);
    }
}
```

## 📊 Request Flow

### **1. Request with Valid Token**

```
1. Client sends request with "Authorization: Bearer <token>"
2. JwtAuthenticationFilter extracts token
3. JwtTokenProvider validates token
4. Authentication object is created and set in SecurityContext
5. Request continues to controller
6. Controller can access current user via SecurityContextUtil
```

### **2. Request with Invalid Token**

```
1. Client sends request with invalid token
2. JwtAuthenticationFilter extracts token
3. JwtTokenProvider validation fails
4. JwtTokenInvalidException is thrown
5. Error response is sent to client
6. Request does not reach controller
```

### **3. Request without Token**

```
1. Client sends request without token
2. JwtAuthenticationFilter finds no token
3. No authentication is set
4. Request continues to controller
5. Controller may throw AuthenticationException if needed
```

## 🧪 Testing Benefits

### **Easy Testing**

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void shouldAuthenticateWithValidToken() throws Exception {
        // Given
        String token = "valid-jwt-token";
        AuthenticatedUser user = AuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .username("testuser")
            .build();

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(token)).thenReturn(
            new UsernamePasswordAuthenticationToken(user, "", Collections.emptyList())
        );

        // When & Then
        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldReturnUnauthorizedWithInvalidToken() throws Exception {
        // Given
        String token = "invalid-jwt-token";
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_ERROR"));
    }
}
```

## 🎯 Benefits

1. **Automatic Authentication**: Otomatik authentication
2. **Stateless**: Session kullanmadan authentication
3. **Security**: Güvenli token validation
4. **Error Handling**: Tutarlı hata yönetimi
5. **Easy Integration**: Kolay entegrasyon
6. **Testing**: Test edilebilirlik

# 👨‍💻 Development Guide

## 📋 Overview

This guide provides comprehensive instructions for setting up, developing, and contributing to the Fabric Management System. Follow these guidelines to ensure consistency, quality, and maintainability across the codebase.

## 🚀 Quick Start

### Prerequisites

- **Java 21** or higher
- **Maven 3.9+**
- **Docker** and **Docker Compose**
- **Git**
- **IDE** (IntelliJ IDEA, Eclipse, VS Code)

### Environment Setup

1. **Clone the repository**:

```bash
git clone https://github.com/your-org/fabric-management-backend.git
cd fabric-management-backend
```

2. **Start infrastructure services**:

```bash
docker-compose up -d postgres-db redis kafka
```

3. **Build the project**:

```bash
mvn clean install
```

4. **Run tests**:

```bash
mvn test
```

## 🏗️ Project Structure

```
fabric-management-backend/
├── shared/                          # Shared libraries
│   ├── shared-domain/              # Domain primitives
│   ├── shared-infrastructure/      # Infrastructure utilities
│   ├── shared-application/          # Application utilities
│   └── shared-security/            # Security utilities
├── services/                        # Microservices
│   ├── user-service/               # User management
│   ├── contact-service/            # Contact management
│   ├── company-service/            # Company management
│   └── ...                         # Other services
├── infrastructure/                  # Infrastructure services
│   ├── api-gateway/                # API Gateway
│   ├── service-discovery/          # Service Discovery
│   └── config-server/              # Config Server
├── deployment/                      # Deployment configurations
├── docs/                           # Documentation
└── scripts/                        # Automation scripts
```

## 🎯 Development Standards

### Code Style

We follow **Google Java Style Guide** with some modifications:

```java
// ✅ Good: Clear, descriptive naming
public class UserService {
    private final UserRepository userRepository;

    public UserResponse createUser(CreateUserRequest request) {
        // Implementation
    }
}

// ❌ Bad: Unclear naming
public class UsrSvc {
    private final UsrRepo repo;

    public UsrResp crtUsr(CrtUsrReq req) {
        // Implementation
    }
}
```

### Naming Conventions

| Element       | Convention       | Example                            |
| ------------- | ---------------- | ---------------------------------- |
| **Classes**   | PascalCase       | `UserService`, `CreateUserRequest` |
| **Methods**   | camelCase        | `createUser()`, `findByEmail()`    |
| **Variables** | camelCase        | `userName`, `isActive`             |
| **Constants** | UPPER_SNAKE_CASE | `MAX_RETRY_ATTEMPTS`               |
| **Packages**  | lowercase        | `com.fabricmanagement.user`        |

### File Organization

Each service follows this structure:

```
service-name/
├── src/main/java/com/fabricmanagement/service/
│   ├── ServiceApplication.java
│   ├── domain/                      # Domain layer
│   │   ├── aggregate/              # Aggregate roots
│   │   ├── event/                  # Domain events
│   │   ├── repository/             # Repository interfaces
│   │   └── service/                # Domain services
│   ├── application/                # Application layer
│   │   ├── command/                # Commands
│   │   ├── query/                  # Queries
│   │   ├── handler/                # Command/Query handlers
│   │   ├── service/                # Application services
│   │   └── port/                   # Port interfaces
│   └── infrastructure/             # Infrastructure layer
│       ├── persistence/            # Database
│       ├── web/                    # REST controllers
│       ├── messaging/              # Event handling
│       └── config/                 # Configuration
└── src/test/java/                  # Tests
```

## 🧪 Testing Strategy

### Test Types

1. **Unit Tests** - Test individual components
2. **Integration Tests** - Test service interactions
3. **Contract Tests** - Test API contracts
4. **End-to-End Tests** - Test complete workflows

### Test Structure

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Should create user successfully")
    void shouldCreateUser() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
            .username("john.doe")
            .email("john.doe@company.com")
            .build();

        User savedUser = User.builder()
            .id(UUID.randomUUID())
            .username("john.doe")
            .email("john.doe@company.com")
            .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When
        UserResponse response = userService.createUser(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("john.doe");
        verify(userRepository).save(any(User.class));
    }
}
```

### Test Coverage

- **Minimum Coverage**: 80%
- **Critical Paths**: 95%
- **Domain Logic**: 100%

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=UserServiceTest

# Run integration tests
mvn verify

# Generate coverage report
mvn jacoco:report
```

## 🔧 Development Workflow

### Git Workflow

1. **Create feature branch**:

```bash
git checkout -b feature/user-authentication
```

2. **Make changes and commit**:

```bash
git add .
git commit -m "feat: add user authentication endpoint"
```

3. **Push and create PR**:

```bash
git push origin feature/user-authentication
```

### Commit Message Format

We use **Conventional Commits**:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Types**:

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes
- `refactor`: Code refactoring
- `test`: Adding tests
- `chore`: Maintenance tasks

**Examples**:

```
feat(user): add user registration endpoint
fix(auth): resolve JWT token expiration issue
docs(api): update authentication documentation
```

### Code Review Process

1. **Self-review** your changes
2. **Run tests** and ensure they pass
3. **Create pull request** with clear description
4. **Request review** from team members
5. **Address feedback** and make changes
6. **Merge** after approval

## 🏗️ Architecture Patterns

### Clean Architecture

```mermaid
graph TB
    subgraph "Infrastructure Layer"
        WEB[REST Controllers]
        DB[JPA Repositories]
        MSG[Event Publishers]
    end

    subgraph "Application Layer"
        CMD[Command Handlers]
        QRY[Query Handlers]
        SVC[Application Services]
    end

    subgraph "Domain Layer"
        AGG[Aggregate Roots]
        EVT[Domain Events]
        REPO[Repository Interfaces]
    end

    WEB --> CMD
    WEB --> QRY
    CMD --> AGG
    QRY --> AGG
    AGG --> EVT
    EVT --> MSG
    CMD --> REPO
    QRY --> REPO
    REPO --> DB
```

### CQRS Implementation

```java
// Command
public class CreateUserCommand {
    private String username;
    private String email;
    // getters, setters
}

// Command Handler
@Component
public class CreateUserCommandHandler {

    public UserResponse handle(CreateUserCommand command) {
        // Command handling logic
    }
}

// Query
public class GetUserQuery {
    private UUID userId;
    // getters, setters
}

// Query Handler
@Component
public class GetUserQueryHandler {

    public UserResponse handle(GetUserQuery query) {
        // Query handling logic
    }
}
```

### Event Sourcing

```java
// Domain Event
public class UserCreatedEvent extends DomainEvent {
    private String username;
    private String email;

    public UserCreatedEvent(String username, String email) {
        super();
        this.username = username;
        this.email = email;
    }
}

// Event Handler
@Component
public class UserCreatedEventHandler {

    @EventListener
    public void handle(UserCreatedEvent event) {
        // Handle the event
    }
}
```

## 🔒 Security Guidelines

### Authentication

```java
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('USER')")
public class UserController {

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserResponse> getCurrentUser() {
        // Implementation
    }
}
```

### Input Validation

```java
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    // getters, setters
}
```

### Data Protection

- **Never log sensitive data** (passwords, tokens)
- **Use parameterized queries** to prevent SQL injection
- **Validate all inputs** at API boundaries
- **Implement rate limiting** for public endpoints

## 📊 Performance Guidelines

### Database Optimization

```java
// ✅ Good: Use specific queries
@Query("SELECT u FROM User u WHERE u.email = :email AND u.deleted = false")
Optional<User> findByEmailAndDeletedFalse(@Param("email") String email);

// ❌ Bad: Load all data
List<User> users = userRepository.findAll();
```

### Caching Strategy

```java
@Service
public class UserService {

    @Cacheable(value = "users", key = "#userId")
    public UserResponse getUser(UUID userId) {
        // Implementation
    }

    @CacheEvict(value = "users", key = "#userId")
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        // Implementation
    }
}
```

### Async Processing

```java
@Service
public class NotificationService {

    @Async
    public CompletableFuture<Void> sendEmail(String email, String message) {
        // Async email sending
        return CompletableFuture.completedFuture(null);
    }
}
```

## 🐛 Debugging

### Logging

```java
@Slf4j
@Service
public class UserService {

    public UserResponse createUser(CreateUserRequest request) {
        log.info("Creating user with username: {}", request.getUsername());

        try {
            User user = userRepository.save(user);
            log.info("User created successfully with ID: {}", user.getId());
            return userMapper.toResponse(user);
        } catch (Exception e) {
            log.error("Failed to create user: {}", e.getMessage(), e);
            throw new UserCreationException("Failed to create user", e);
        }
    }
}
```

### Debug Configuration

```yaml
# application-local.yml
logging:
  level:
    com.fabricmanagement: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

## 🔧 IDE Configuration

### IntelliJ IDEA

1. **Install plugins**:

   - Lombok Plugin
   - MapStruct Plugin
   - Spring Boot Plugin

2. **Code style settings**:

   - Import `google-java-style.xml`
   - Enable "Reformat code on save"

3. **Run configurations**:
   - Create Spring Boot run configurations
   - Set VM options: `-Xmx2g -Xms1g`

### VS Code

1. **Install extensions**:

   - Extension Pack for Java
   - Spring Boot Extension Pack
   - Lombok Annotations Support

2. **Settings**:

```json
{
  "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml",
  "java.saveActions.organizeImports": true
}
```

## 📚 Resources

### Documentation

- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [JPA Specification](https://jakarta.ee/specifications/persistence/)

### Tools

- [Postman](https://www.postman.com/) - API testing
- [DBeaver](https://dbeaver.io/) - Database management
- [RedisInsight](https://redis.com/redis-enterprise/redis-insight/) - Redis management

### Learning

- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Domain-Driven Design](https://martinfowler.com/bliki/DomainDrivenDesign.html)
- [CQRS Pattern](https://martinfowler.com/bliki/CQRS.html)

## 🤝 Contributing

### Getting Started

1. **Fork the repository**
2. **Create feature branch**: `git checkout -b feature/amazing-feature`
3. **Make your changes**
4. **Add tests** for new functionality
5. **Run tests**: `mvn test`
6. **Commit changes**: `git commit -m 'feat: add amazing feature'`
7. **Push to branch**: `git push origin feature/amazing-feature`
8. **Open Pull Request**

### Pull Request Guidelines

- **Clear description** of changes
- **Reference issues** if applicable
- **Include tests** for new features
- **Update documentation** if needed
- **Ensure CI passes**

## 📞 Support

- **Slack**: #fabric-management-dev
- **Email**: dev-team@fabricmanagement.com
- **Office Hours**: Tuesday & Thursday 2-4 PM
- **Documentation**: [docs.fabricmanagement.com](https://docs.fabricmanagement.com)

---

_Last updated: 2024-01-XX_
_Version: 1.0.0_

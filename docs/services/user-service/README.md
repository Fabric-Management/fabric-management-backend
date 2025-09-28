# 👤 User Service Documentation

## 📋 Overview

The User Service manages user profiles, preferences, and user-related data in the Fabric Management System. It operates independently while maintaining data consistency through event-driven communication with the Identity Service.

## 🎯 Core Responsibilities

- **User Profile Management**: Create, read, update user profiles
- **User Preferences**: Manage user settings and preferences
- **User Search**: Search and filter users
- **Department Management**: Organize users by departments
- **User Relationships**: Manage user-to-user relationships
- **Profile Synchronization**: Sync with Identity Service events

## 🏗️ Architecture

### **Clean Architecture Layers**

```
┌─────────────────────────────────────────┐
│              Presentation               │
│         (Controllers, DTOs)             │
├─────────────────────────────────────────┤
│              Application                │
│         (Services, Use Cases)           │
├─────────────────────────────────────────┤
│                Domain                   │
│      (Entities, Value Objects,          │
│       Domain Events, Business Logic)   │
├─────────────────────────────────────────┤
│             Infrastructure              │
│    (Repositories, External Services,    │
│     Event Consumers, Configuration)   │
└─────────────────────────────────────────┘
```

## 🔧 Key Components

### **Domain Layer**

- **User Aggregate**: Core user entity with profile data
- **User Profile Domain**: Profile management business logic
- **User Search Domain**: Search and filtering capabilities
- **Domain Events**: User-related domain events

### **Application Layer**

- **UserApplicationService**: Orchestrates user operations
- **UserProfileService**: Manages user profiles
- **UserSearchService**: Handles user search functionality
- **Event Consumers**: Consumes events from Identity Service

### **Infrastructure Layer**

- **JPA Repositories**: Data persistence
- **Kafka Event Consumer**: Event consumption
- **Search Indexing**: User search capabilities
- **Configuration**: Service configuration

## 📡 API Endpoints

### **User Profile Management**

- `GET /api/user/profile/{userId}` - Get user profile
- `PUT /api/user/profile/{userId}` - Update user profile
- `DELETE /api/user/profile/{userId}` - Delete user profile
- `GET /api/user/profiles` - List user profiles

### **User Search**

- `GET /api/user/search` - Search users
- `GET /api/user/search/by-department` - Search by department
- `GET /api/user/search/by-name` - Search by name
- `GET /api/user/search/by-status` - Search by status

### **User Preferences**

- `GET /api/user/preferences/{userId}` - Get user preferences
- `PUT /api/user/preferences/{userId}` - Update user preferences
- `GET /api/user/preferences/{userId}/timezone` - Get timezone
- `PUT /api/user/preferences/{userId}/timezone` - Update timezone

### **Department Management**

- `GET /api/user/departments` - List departments
- `POST /api/user/departments` - Create department
- `PUT /api/user/departments/{id}` - Update department
- `DELETE /api/user/departments/{id}` - Delete department

## 🔄 Event Consumption

### **Consumed Events**

- **UserCreatedEvent**: Creates user profile when user registers
- **UserProfileUpdatedEvent**: Updates user profile data
- **UserSuspendedEvent**: Suspends user account
- **UserReactivatedEvent**: Reactivates user account

### **Event Consumer**

```java
@Component
public class IdentityEventConsumer {
    @KafkaListener(topics = "user.created", groupId = "user-service")
    public void listenUserCreated(UserCreatedEvent event, Acknowledgment acknowledgment) {
        userApplicationService.createUserProfileFromEvent(event);
        acknowledgment.acknowledge();
    }
}
```

### **Event Processing**

```java
// In UserApplicationService
public void createUserProfileFromEvent(UserCreatedEvent event) {
    User user = new User();
    user.setId(event.getUserId());
    user.setTenantId(event.getTenantId());
    user.setUsername(event.getUsername());
    user.setStatus(UserStatus.ACTIVE);

    userRepository.save(user);
}
```

## 📊 Data Model

### **User Entity**

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "department")
    private String department;

    @Column(name = "time_zone")
    private String timeZone;

    @Column(name = "language_preference")
    private String languagePreference;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
}
```

### **User Status Enum**

```java
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    PENDING_VERIFICATION
}
```

## ⚙️ Configuration

### **Application Properties**

```yaml
# Server Configuration
server:
  port: 8082
  servlet:
    context-path: /api/user

# Database Configuration
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:fabric_management}
    username: ${POSTGRES_USER:fabric_user}
    password: ${POSTGRES_PASSWORD:fabric_password}

# Kafka Configuration
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: user-service
      auto-offset-reset: earliest
      enable-auto-commit: false
```

### **Search Configuration**

```yaml
# Search Configuration
search:
  user:
    max-results: 100
    default-page-size: 20
    enable-fuzzy-search: true
    search-fields:
      - firstName
      - lastName
      - displayName
      - jobTitle
      - department
```

## 🧪 Testing

### **Unit Tests**

- Domain logic testing
- Service layer testing
- Repository testing
- Event consumer testing

### **Integration Tests**

- API endpoint testing
- Database integration testing
- Kafka integration testing
- Search functionality testing

### **Test Data**

```java
@Test
public void testCreateUserProfileFromEvent() {
    UserCreatedEvent event = new UserCreatedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "testuser"
    );

    userApplicationService.createUserProfileFromEvent(event);

    User savedUser = userRepository.findByUsername("testuser");
    assertThat(savedUser).isNotNull();
    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
}
```

## 🚀 Deployment

### **Docker Configuration**

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/user-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### **Health Checks**

- Database connectivity
- Kafka connectivity
- Service health status
- Search index health

## 🔍 Monitoring

### **Metrics**

- User profile operations
- Search query performance
- Event consumption rate
- Database query performance
- API response times

### **Logging**

- User operations
- Search queries
- Event processing
- Error logs
- Performance metrics

## 🔧 Troubleshooting

### **Common Issues**

#### **User Profile Not Created**

- Check Kafka connectivity
- Verify event consumption
- Check database connectivity
- Review event processing logs

#### **Search Not Working**

- Check search index status
- Verify search configuration
- Check database indexes
- Review search query logs

#### **Event Processing Errors**

- Check consumer group status
- Verify event deserialization
- Check database transactions
- Review error logs

## 📚 Related Documentation

- [Identity Service Integration](../integration/identity-user-integration.md)
- [User Service API](../user-guides/api/user-service-api.md)
- [Search Configuration](../development/search-configuration.md)
- [Deployment Guide](../deployment/user-service-deployment.md)

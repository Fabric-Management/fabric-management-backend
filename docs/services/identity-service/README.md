# 🔐 Identity Service Documentation

## 📋 Overview

The Identity Service is responsible for authentication, authorization, and user credential management in the Fabric Management System. It implements modern security practices with JWT-based authentication and event-driven communication.

## 🎯 Core Responsibilities

- **Authentication**: User login and credential validation
- **Authorization**: JWT token generation and validation
- **User Registration**: New user account creation
- **Password Management**: Password creation, change, and reset
- **Session Management**: Redis-based session handling
- **Security Features**: 2FA, rate limiting, account lockout

## 🏗️ Architecture

### **Clean Architecture Layers**

```
┌─────────────────────────────────────────┐
│              Presentation               │
│         (Controllers, DTOs)            │
├─────────────────────────────────────────┤
│              Application                │
│         (Services, Use Cases)           │
├─────────────────────────────────────────┤
│                Domain                   │
│      (Entities, Value Objects,         │
│       Domain Events, Business Logic)    │
├─────────────────────────────────────────┤
│             Infrastructure              │
│    (Repositories, External Services,    │
│     Event Publishers, Configuration)   │
└─────────────────────────────────────────┘
```

## 🔧 Key Components

### **Domain Layer**

- **User Aggregate**: Core user entity with business logic
- **Authentication Domain**: Login, registration, password management
- **Security Domain**: JWT, 2FA, rate limiting
- **Domain Events**: UserCreatedEvent, UserProfileUpdatedEvent

### **Application Layer**

- **AuthenticationService**: Handles login, registration, password operations
- **UserService**: Manages user profile operations
- **SecurityService**: Implements security features
- **Event Publishers**: Publishes domain events

### **Infrastructure Layer**

- **JPA Repositories**: Data persistence
- **Redis Session Store**: Session management
- **Kafka Event Publisher**: Event publishing
- **Security Configuration**: Spring Security setup

## 🔐 Security Features

### **Authentication**

- JWT-based stateless authentication
- Refresh token mechanism
- Session management with Redis
- Account lockout after failed attempts

### **Authorization**

- Role-based access control (RBAC)
- Method-level security
- Resource-level permissions
- Tenant isolation

### **Password Security**

- BCrypt password hashing
- Password strength validation
- Password reset functionality
- Password history tracking

### **Two-Factor Authentication**

- TOTP (Time-based One-Time Password)
- QR code generation
- Backup codes
- Recovery mechanisms

## 📡 API Endpoints

### **Authentication**

- `POST /api/identity/auth/login` - User login
- `POST /api/identity/auth/register` - User registration
- `POST /api/identity/auth/refresh` - Refresh JWT token
- `POST /api/identity/auth/logout` - User logout

### **Password Management**

- `POST /api/identity/password/change` - Change password
- `POST /api/identity/password/reset` - Reset password
- `POST /api/identity/password/reset/request` - Request password reset

### **User Management**

- `GET /api/identity/user/profile` - Get user profile
- `PUT /api/identity/user/profile` - Update user profile
- `POST /api/identity/user/2fa/enable` - Enable 2FA
- `POST /api/identity/user/2fa/disable` - Disable 2FA

## 🔄 Event Publishing

### **Published Events**

- **UserCreatedEvent**: When a new user registers
- **UserProfileUpdatedEvent**: When user profile is updated
- **PasswordChangedEvent**: When password is changed
- **TwoFactorEnabledEvent**: When 2FA is enabled

### **Event Publisher**

```java
@Component
public class IdentityEventPublisher {
    public void publishUserCreatedEvent(UserCreatedEvent event) {
        kafkaTemplate.send("user.created", event.getUserId().toString(), event);
    }
}
```

## ⚙️ Configuration

### **Application Properties**

```yaml
# Server Configuration
server:
  port: 8081
  servlet:
    context-path: /api/identity

# Database Configuration
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:fabric_management}
    username: ${POSTGRES_USER:fabric_user}
    password: ${POSTGRES_PASSWORD:fabric_password}

# Redis Configuration
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}

# Kafka Configuration
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      client-id: identity-service
```

### **Security Configuration**

```yaml
# JWT Configuration
jwt:
  secret: ${JWT_SECRET:your-secret-key}
  expiration: 86400000 # 24 hours
  refresh-expiration: 604800000 # 7 days

# Security Configuration
security:
  password:
    min-length: 8
    require-uppercase: true
    require-lowercase: true
    require-numbers: true
    require-special-chars: true
  account:
    max-login-attempts: 5
    lockout-duration: 300000 # 5 minutes
```

## 🧪 Testing

### **Unit Tests**

- Domain logic testing
- Service layer testing
- Repository testing
- Event publisher testing

### **Integration Tests**

- API endpoint testing
- Database integration testing
- Kafka integration testing
- Security testing

### **Test Configuration**

```yaml
# Test Profile
spring:
  profiles:
    active: test
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
```

## 🚀 Deployment

### **Docker Configuration**

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/identity-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### **Health Checks**

- Database connectivity
- Redis connectivity
- Kafka connectivity
- Service health status

## 🔍 Monitoring

### **Metrics**

- Authentication success/failure rates
- JWT token generation rate
- Password reset requests
- 2FA usage statistics
- Event publishing metrics

### **Logging**

- Authentication attempts
- Security events
- Error logs
- Performance metrics

## 📚 Related Documentation

- [User Service Integration](../integration/identity-user-integration.md)
- [Security Architecture](../architecture/security/README.md)
- [API Documentation](../user-guides/api/identity-service-api.md)
- [Deployment Guide](../deployment/identity-service-deployment.md)

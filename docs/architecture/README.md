# 🏗️ System Architecture

## 📋 Overview

The Fabric Management System follows modern microservice architecture principles with Domain-Driven Design (DDD), Clean Architecture, and Event-Driven patterns. This document provides a comprehensive overview of the system architecture, design decisions, and implementation patterns.

## 🎯 Architecture Principles

### Core Principles

1. **Domain-Driven Design (DDD)** - Business logic organized around domain concepts
2. **Clean Architecture** - Separation of concerns with clear boundaries
3. **Event-Driven Architecture** - Loose coupling through events
4. **CQRS Pattern** - Command Query Responsibility Segregation
5. **Microservice Architecture** - Independent, scalable services
6. **Multi-tenancy** - Isolated tenant data and configurations

### Design Goals

- **Scalability** - Handle millions of requests
- **Reliability** - Fault-tolerant and resilient
- **Maintainability** - Easy to understand and modify
- **Performance** - Optimized for speed and efficiency
- **Security** - Enterprise-grade security
- **Observability** - Full monitoring and tracing

## 🏗️ System Architecture

### High-Level Architecture

```mermaid
graph TB
    subgraph "Client Layer"
        WEB[Web Application<br/>React/Angular]
        MOB[Mobile App<br/>iOS/Android]
        API[API Clients<br/>Third Party]
    end

    subgraph "API Gateway Layer"
        GW[API Gateway<br/>Spring Cloud Gateway<br/>Port: 8080]
    end

    subgraph "Core Services Layer"
        US[User Service<br/>Port: 8081<br/>✅ JPA Entities<br/>✅ Builder Pattern<br/>✅ Immutable Updates]
        CS[Contact Service<br/>Port: 8082]
        COS[Company Service<br/>Port: 8083]
        NS[Notification Service<br/>Port: 8084]
    end

    subgraph "Business Services Layer"
        HS[HR Service<br/>Port: 8085]
        IS[Inventory Service<br/>Port: 8086]
        PS[Procurement Service<br/>Port: 8087]
        OS[Order Service<br/>Port: 8088]
        LS[Logistics Service<br/>Port: 8089]
        PRS[Production Service<br/>Port: 8090]
    end

    subgraph "Financial Services Layer"
        FS[Financial Service<br/>Port: 8091]
        PAY[Payment Service<br/>Port: 8092]
        BS[Billing Service<br/>Port: 8093]
    end

    subgraph "Analytics Layer"
        AS[Analytics Service<br/>Port: 8094]
    end

    subgraph "Infrastructure Layer"
        DB[(PostgreSQL<br/>Port: 5432)]
        CACHE[(Redis<br/>Port: 6379)]
        MSG[Kafka<br/>Port: 9092]
        ES[Event Store<br/>Port: 2113]
    end

    WEB --> GW
    MOB --> GW
    API --> GW
    GW --> US
    GW --> CS
    GW --> COS
    GW --> NS
    GW --> HS
    GW --> IS
    GW --> PS
    GW --> OS
    GW --> LS
    GW --> PRS
    GW --> FS
    GW --> PAY
    GW --> BS
    GW --> AS

    US --> DB
    CS --> DB
    COS --> DB
    NS --> CACHE
    HS --> DB
    IS --> DB
    PS --> DB
    OS --> DB
    LS --> DB
    PRS --> DB
    FS --> DB
    PAY --> DB
    BS --> DB
    AS --> DB

    US --> MSG
    CS --> MSG
    COS --> MSG
    NS --> MSG
    HS --> MSG
    IS --> MSG
    PS --> MSG
    OS --> MSG
    LS --> MSG
    PRS --> MSG
    FS --> MSG
    PAY --> MSG
    BS --> MSG
    AS --> MSG
```

## 🎯 Service Architecture

### Service Design Pattern

Each microservice follows the **Clean Architecture** pattern with clear separation of concerns:

```mermaid
graph TB
    subgraph "Infrastructure Layer"
        WEB[REST Controllers]
        DB[JPA Repositories]
        MSG[Event Publishers/Consumers]
        EXT[External Service Clients]
    end

    subgraph "Application Layer"
        CMD[Command Handlers]
        QRY[Query Handlers]
        SVC[Application Services]
        PORT[Port Interfaces]
    end

    subgraph "Domain Layer"
        AGG[Aggregate Roots]
        EVT[Domain Events]
        REPO[Repository Interfaces]
        DOM[Domain Services]
    end

    WEB --> CMD
    WEB --> QRY
    CMD --> AGG
    QRY --> AGG
    AGG --> EVT
    EVT --> MSG
    CMD --> PORT
    QRY --> PORT
    PORT --> REPO
    REPO --> DB
```

### Service Responsibilities

#### Core Services

| Service                  | Port | Responsibilities                                  |
| ------------------------ | ---- | ------------------------------------------------- |
| **User Service**         | 8081 | Authentication, user profiles, session management |
| **Contact Service**      | 8082 | Contact information, communication preferences    |
| **Company Service**      | 8083 | Company management, multi-tenancy                 |
| **Notification Service** | 8084 | Email, SMS, push notifications                    |

#### Business Services

| Service                 | Port | Responsibilities                           |
| ----------------------- | ---- | ------------------------------------------ |
| **HR Service**          | 8085 | Human resources, payroll, leave management |
| **Inventory Service**   | 8086 | Stock management, product catalog, pricing |
| **Procurement Service** | 8087 | Purchase orders, supplier management       |
| **Order Service**       | 8088 | Order processing, fulfillment              |
| **Logistics Service**   | 8089 | Shipping, delivery tracking                |
| **Production Service**  | 8090 | Manufacturing, production planning         |

#### Financial Services

| Service               | Port | Responsibilities              |
| --------------------- | ---- | ----------------------------- |
| **Financial Service** | 8091 | Accounting, financial records |
| **Payment Service**   | 8092 | Payment processing, gateways  |
| **Billing Service**   | 8093 | Billing management, invoicing |

#### Analytics Service

| Service               | Port | Responsibilities                              |
| --------------------- | ---- | --------------------------------------------- |
| **Analytics Service** | 8094 | Business intelligence, reporting, AI insights |

## 🏗️ Service Architecture Details

### User Service Architecture

The User Service implements a sophisticated domain model with modern architectural patterns:

#### Domain Model

```mermaid
graph TB
    subgraph "User Service Domain"
        USER[User Aggregate Root<br/>✅ JPA Entity<br/>✅ Builder Pattern<br/>✅ Immutable Updates]
        CONTACT[UserContact Entity<br/>✅ Multi-contact Support<br/>✅ Verification Status]
        TOKEN[PasswordResetToken Entity<br/>✅ Security Constraints<br/>✅ Time-based Expiry]

        USER --> CONTACT
        USER --> TOKEN
    end

    subgraph "Domain Events"
        CREATED[UserCreatedEvent]
        UPDATED[UserUpdatedEvent]
        DELETED[UserDeletedEvent]
        RESET[PasswordResetRequestedEvent]
    end

    USER --> CREATED
    USER --> UPDATED
    USER --> DELETED
    USER --> RESET
```

#### Key Architectural Improvements

**1. JPA Entity Implementation**

```java
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;  // String for database compatibility

    @OneToMany(mappedBy = "userId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserContact> contacts;  // Proper JPA mapping

    @Transient
    private final List<Object> domainEvents = new ArrayList<>();  // Non-persistent events
}
```

**2. Builder Pattern for Immutability**

```java
// ✅ Correct: Immutable builder pattern
public static User createWithContactVerification(String contactValue, String contactType,
                                                String firstName, String lastName,
                                                String passwordHash, String userType) {
    return User.builder()
        .firstName(firstName)
        .lastName(lastName)
        .status(UserStatus.PENDING_VERIFICATION)
        .registrationType(RegistrationType.DIRECT_REGISTRATION)
        .contacts(new ArrayList<>())
        .build();
}
```

**3. Thread-Safe Updates**

```java
// ✅ Correct: Index-based updates (thread-safe)
for (int i = 0; i < this.contacts.size(); i++) {
    UserContact contact = this.contacts.get(i);
    if (contact.getContactValue().equals(contactValue)) {
        UserContact verifiedContact = UserContact.builder()
            .id(contact.getId())
            .userId(contact.getUserId())
            .contactValue(contact.getContactValue())
            .contactType(contact.getContactType())
            .isVerified(true)
            .isPrimary(contact.isPrimary())
            .verifiedAt(LocalDateTime.now())
            .build();
        this.contacts.set(i, verifiedContact);
        break;
    }
}
```

#### Test Architecture

```mermaid
graph TB
    subgraph "Test Coverage"
        UNIT[Unit Tests<br/>✅ Domain Logic: 100%<br/>✅ Business Rules: 100%<br/>✅ Value Objects: 100%]
        INTEGRATION[Integration Tests<br/>✅ Repository: 95%<br/>✅ Database Ops: 100%<br/>✅ Security: 100%]
    end

    subgraph "Test Types"
        AGGREGATE[UserTest<br/>38 tests]
        VALUE[UserContactTest<br/>18 tests]
        TOKEN[PasswordResetTokenTest<br/>26 tests]
        REPO[UserRepositoryIntegrationTest<br/>25 tests]
        TOKEN_REPO[PasswordResetTokenRepositoryIntegrationTest<br/>15 tests]
    end

    UNIT --> AGGREGATE
    UNIT --> VALUE
    UNIT --> TOKEN
    INTEGRATION --> REPO
    INTEGRATION --> TOKEN_REPO
```

## 🔄 Data Flow Architecture

### Event-Driven Communication

```mermaid
sequenceDiagram
    participant Client
    participant Gateway
    participant UserService
    participant OrderService
    participant NotificationService
    participant Kafka

    Client->>Gateway: Create Order Request
    Gateway->>UserService: Validate User
    UserService-->>Gateway: User Valid
    Gateway->>OrderService: Create Order
    OrderService->>OrderService: Process Order
    OrderService->>Kafka: Publish OrderCreated Event
    Kafka->>NotificationService: OrderCreated Event
    NotificationService->>NotificationService: Send Notification
    OrderService-->>Gateway: Order Created
    Gateway-->>Client: Order Response
```

### CQRS Pattern Implementation

```mermaid
graph LR
    subgraph "Command Side"
        CMD[Command]
        CH[Command Handler]
        AGG[Aggregate]
        EVT[Domain Event]
    end

    subgraph "Query Side"
        QRY[Query]
        QH[Query Handler]
        VIEW[Read Model]
    end

    subgraph "Event Store"
        ES[Event Store]
        PROJ[Projection]
    end

    CMD --> CH
    CH --> AGG
    AGG --> EVT
    EVT --> ES
    ES --> PROJ
    PROJ --> VIEW
    QRY --> QH
    QH --> VIEW
```

## 🎯 Exception Handling Architecture

### Conditional Exception Handler Pattern

The system uses Spring Boot's `@ConditionalOnMissingBean` pattern to provide flexible exception handling while maintaining microservices autonomy.

```mermaid
graph TB
    subgraph "Exception Handling Strategy"
        SHARED[Shared GlobalExceptionHandler<br/>@ConditionalOnMissingBean<br/>Default for all services]

        subgraph "Service-Specific Handlers"
            USER[UserServiceExceptionHandler<br/>@Component serviceExceptionHandler<br/>Custom authentication errors]
            COMPANY[CompanyExceptionHandler<br/>@Component serviceExceptionHandler<br/>Custom business rules]
        end

        subgraph "Services Without Custom Handlers"
            CONTACT[Contact Service<br/>Uses Shared Handler]
        end
    end

    SHARED -.->|Auto-disabled when<br/>custom handler present| USER
    SHARED -.->|Auto-disabled when<br/>custom handler present| COMPANY
    SHARED -->|Auto-enabled when<br/>no custom handler| CONTACT
```

### Exception Handler Configuration

| Service             | Handler Type  | Bean Name                 | Status     |
| ------------------- | ------------- | ------------------------- | ---------- |
| **user-service**    | Custom        | `serviceExceptionHandler` | ✅ Active  |
| **company-service** | Custom        | `serviceExceptionHandler` | ✅ Active  |
| **contact-service** | Shared        | `globalExceptionHandler`  | ✅ Active  |
| **Future services** | Auto (Shared) | `globalExceptionHandler`  | ✅ Default |

### Benefits

- ✅ **Microservices Autonomy** - Each service chooses its exception handling strategy
- ✅ **Zero Coupling** - No inheritance between service and shared handlers
- ✅ **Convention over Configuration** - Automatic, declarative configuration
- ✅ **Flexibility** - Services can add/remove custom handlers without affecting others

For detailed implementation guide, see [Bean Conflict Resolution](../troubleshooting/BEAN_CONFLICT_RESOLUTION.md).

---

## 🛡️ Security Architecture

### Authentication & Authorization

```mermaid
graph TB
    subgraph "Client"
        APP[Application]
        TOKEN[JWT Token]
    end

    subgraph "API Gateway"
        GW[Gateway]
        AUTH[Auth Filter]
    end

    subgraph "User Service"
        US[User Service]
        JWT[JWT Provider]
        RBAC[RBAC]
    end

    APP --> TOKEN
    TOKEN --> GW
    GW --> AUTH
    AUTH --> JWT
    JWT --> US
    US --> RBAC
```

### Security Layers

1. **API Gateway** - Rate limiting, authentication
2. **JWT Tokens** - Stateless authentication
3. **RBAC** - Role-based access control
4. **OAuth2** - External authentication
5. **Audit Logging** - Security event tracking

## 📊 Observability Architecture

### Monitoring Stack

```mermaid
graph TB
    subgraph "Applications"
        SVC1[Service 1]
        SVC2[Service 2]
        SVC3[Service 3]
    end

    subgraph "Metrics"
        MICRO[Micrometer]
        PROM[Prometheus]
        GRAF[Grafana]
    end

    subgraph "Tracing"
        JAEGER[Jaeger]
        OTEL[OpenTelemetry]
    end

    subgraph "Logging"
        LOG[Application Logs]
        ELK[ELK Stack]
    end

    SVC1 --> MICRO
    SVC2 --> MICRO
    SVC3 --> MICRO
    MICRO --> PROM
    PROM --> GRAF

    SVC1 --> OTEL
    SVC2 --> OTEL
    SVC3 --> OTEL
    OTEL --> JAEGER

    SVC1 --> LOG
    SVC2 --> LOG
    SVC3 --> LOG
    LOG --> ELK
```

## 🚀 Deployment Architecture

### Container Orchestration

```mermaid
graph TB
    subgraph "Kubernetes Cluster"
        subgraph "Namespace: fabric-management"
            subgraph "Core Services"
                US_POD[User Service Pod]
                CS_POD[Contact Service Pod]
                COS_POD[Company Service Pod]
                NS_POD[Notification Service Pod]
            end

            subgraph "Business Services"
                HS_POD[HR Service Pod]
                IS_POD[Inventory Service Pod]
                OS_POD[Order Service Pod]
                LS_POD[Logistics Service Pod]
            end

            subgraph "Infrastructure"
                DB_POD[PostgreSQL Pod]
                CACHE_POD[Redis Pod]
                MSG_POD[Kafka Pod]
            end
        end
    end

    subgraph "External"
        LB[Load Balancer]
        DNS[DNS]
    end

    DNS --> LB
    LB --> US_POD
    LB --> CS_POD
    LB --> COS_POD
    LB --> NS_POD
    LB --> HS_POD
    LB --> IS_POD
    LB --> OS_POD
    LB --> LS_POD
```

## 🔧 Technology Decisions

### Why These Technologies?

| Technology            | Purpose          | Justification                        |
| --------------------- | ---------------- | ------------------------------------ |
| **Java 21**           | Runtime          | Latest LTS, performance improvements |
| **Spring Boot 3.5.5** | Framework        | Mature, ecosystem, community         |
| **PostgreSQL 16**     | Database         | ACID compliance, JSON support        |
| **Redis 7**           | Cache            | High performance, data structures    |
| **Kafka 3.5.1**       | Messaging        | High throughput, durability          |
| **Docker**            | Containerization | Portability, consistency             |
| **Kubernetes**        | Orchestration    | Scalability, self-healing            |

## 📈 Scalability Considerations

### Horizontal Scaling

- **Stateless Services** - Easy horizontal scaling
- **Database Sharding** - Tenant-based sharding
- **Caching Strategy** - Multi-level caching
- **Load Balancing** - Round-robin, least connections

### Performance Optimization

- **Connection Pooling** - Database connections
- **Async Processing** - Non-blocking operations
- **CQRS** - Read/write optimization
- **Event Sourcing** - Audit trail, replay capability

## 🔮 Future Considerations

### Planned Enhancements

1. **GraphQL API** - Flexible data querying
2. **gRPC** - High-performance service communication
3. **Service Mesh** - Istio integration
4. **AI/ML Integration** - Predictive analytics
5. **Edge Computing** - CDN integration

### Migration Strategy

- **Gradual Migration** - Service by service
- **Blue-Green Deployment** - Zero downtime
- **Feature Flags** - Controlled rollouts
- **A/B Testing** - Performance validation

---

## 📚 Related Documentation

- [API Documentation](../api/) - REST API specifications
- [Development Guide](../development/) - Setup and coding standards
- [Deployment Guide](../deployment/) - Production deployment
- [Testing Guide](../testing/) - Testing strategies

---

_Last updated: 2024-01-XX_
_Version: 1.0.0_

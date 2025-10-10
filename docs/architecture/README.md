# 🏗️ System Architecture

**Version:** 3.0 (October 10, 2025)  
**Status:** ✅ Major Refactoring Completed  
**Purpose:** Complete system architecture documentation

---

## 📖 Main Architecture Document

For complete and detailed architecture information, see:

### ⭐ **[ARCHITECTURE.md](../ARCHITECTURE.md)** - Complete Architecture Guide

This document contains:

- 🎯 Generic Microservice Template
- 🧩 Shared Modules Structure
- 📊 Layer Responsibilities
- 🔀 Shared vs Service-Specific Guidelines
- 📝 Error Message Management
- 🔧 Refactoring Guides
- 📈 Implementation Checklist

---

## 🎯 Quick Navigation

### Core Architecture Concepts

| Topic                  | Location                                                                                | Description                     |
| ---------------------- | --------------------------------------------------------------------------------------- | ------------------------------- |
| **Service Structure**  | [ARCHITECTURE.md](../ARCHITECTURE.md#-generic-microservice-template)                    | Standard microservice template  |
| **Shared Modules**     | [ARCHITECTURE.md](../ARCHITECTURE.md#-shared-modules-yapısı)                            | How to use shared modules       |
| **Layer Separation**   | [ARCHITECTURE.md](../ARCHITECTURE.md#-katman-sorumlulukları)                            | Controller, Service, Repository |
| **Loose Coupling**     | [../development/PRINCIPLES.md](../development/PRINCIPLES.md#-loose-coupling-principles) | Microservice coupling patterns  |
| **Exception Handling** | [ARCHITECTURE.md](../ARCHITECTURE.md#-exception-handling-architecture)                  | Conditional handler pattern     |

### Recent Major Changes (v3.0 - Oct 10, 2025)

For detailed refactoring information:
📖 **[ARCHITECTURE_REFACTORING_OCT_10_2025.md](../reports/2025-Q4/october/ARCHITECTURE_REFACTORING_OCT_10_2025.md)**

Key improvements:

1. ✅ **Loose Coupling** - Removed facade controllers (CompanyContactController, CompanyUserController)
2. ✅ **Database Cleanup** - Removed 6 over-engineered tables (43% reduction)
3. ✅ **Event Sourcing** - Removed CompanyEventStore, using Outbox Pattern only
4. ✅ **Resilience** - Added Feign Client + Resilience4j (circuit breaker + fallback)
5. ✅ **Constants** - Centralized all hardcoded values

---

## 🏛️ Architecture Principles

### Core Design Principles

1. **Domain-Driven Design (DDD)** - Business logic organized around domain concepts
2. **Clean Architecture** - Clear separation of concerns with defined boundaries
3. **Loose Coupling** - Minimize dependencies between services and components
4. **Event-Driven Architecture** - Asynchronous communication via events
5. **CQRS Pattern** - Command Query Responsibility Segregation
6. **Microservice Architecture** - Independent, scalable services
7. **Multi-tenancy** - Isolated tenant data and configurations

### Design Goals

- ✅ **Scalability** - Handle millions of requests
- ✅ **Reliability** - Fault-tolerant and resilient
- ✅ **Maintainability** - Easy to understand and modify
- ✅ **Performance** - Optimized for speed and efficiency
- ✅ **Security** - Enterprise-grade security
- ✅ **Observability** - Full monitoring and tracing

---

## 🏗️ System Components

### Microservices (Current Implementation)

| Service             | Port | Status        | Responsibilities                                  |
| ------------------- | ---- | ------------- | ------------------------------------------------- |
| **User Service**    | 8081 | ✅ Production | Authentication, user profiles, session management |
| **Contact Service** | 8082 | ✅ Production | Contact information, communication preferences    |
| **Company Service** | 8083 | ✅ Production | Company management, multi-tenancy                 |

### Infrastructure

| Component       | Port | Technology           | Purpose                            |
| --------------- | ---- | -------------------- | ---------------------------------- |
| **API Gateway** | 8080 | Spring Cloud Gateway | Central entry point, routing, auth |
| **PostgreSQL**  | 5433 | PostgreSQL 15        | Primary database                   |
| **Redis**       | 6379 | Redis 7              | Caching, rate limiting             |
| **Kafka**       | 9092 | Apache Kafka 3.5     | Event streaming, async messaging   |

---

## 📐 Architecture Patterns

### Clean Architecture Layers

```
┌─────────────────────────────────────────────┐
│         API Layer (Controllers)              │
│  - REST endpoints                            │
│  - Request/Response DTOs                     │
│  - Input validation                          │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│     Application Layer (Services)             │
│  - Business logic                            │
│  - Use cases orchestration                   │
│  - DTO ↔ Entity mapping                      │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│      Domain Layer (Entities)                 │
│  - Domain models                             │
│  - Business rules                            │
│  - Domain events                             │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  Infrastructure Layer (Persistence)          │
│  - JPA repositories                          │
│  - External service clients                  │
│  - Message publishers/consumers              │
└─────────────────────────────────────────────┘
```

### Loose Coupling Implementation

| Pattern                     | Implementation       | Benefit                              |
| --------------------------- | -------------------- | ------------------------------------ |
| **Event-Driven**            | Kafka messaging      | Services don't know about each other |
| **Interface-Based Clients** | Feign with fallbacks | Easy to mock, switch implementations |
| **Database per Service**    | Separate schemas     | Independent evolution                |
| **API Gateway**             | Single entry point   | Clients don't know service locations |
| **Shared Modules**          | Conditional config   | Services can override defaults       |

---

## 🔄 Data Flow Patterns

### Synchronous Communication (Feign + Resilience4j)

```
Company Service → Feign Client → User Service
                      ↓ (if fails)
                  Fallback Response
```

**Use Cases:**

- Real-time data retrieval
- Immediate validation
- Critical operations requiring confirmation

### Asynchronous Communication (Kafka Events)

```
User Service → Kafka Topic → [Multiple Listeners]
                              ↓         ↓         ↓
                          Analytics  Notification  Email
```

**Use Cases:**

- Event notifications
- Audit logging
- Eventually consistent updates

---

## 📚 Detailed Documentation

### For Implementation Details

| Need                             | Document                                                     | Section                         |
| -------------------------------- | ------------------------------------------------------------ | ------------------------------- |
| How to structure a new service?  | [ARCHITECTURE.md](../ARCHITECTURE.md)                        | Generic Microservice Template   |
| Where to put shared code?        | [ARCHITECTURE.md](../ARCHITECTURE.md)                        | Shared vs Service-Specific      |
| How to handle exceptions?        | [ARCHITECTURE.md](../ARCHITECTURE.md)                        | Exception Handling Architecture |
| How to implement loose coupling? | [../development/PRINCIPLES.md](../development/PRINCIPLES.md) | Loose Coupling Principles       |
| What are the coding standards?   | [../development/PRINCIPLES.md](../development/PRINCIPLES.md) | SOLID, DRY, KISS, YAGNI         |

### For Specific Topics

- **API Standards**: [MICROSERVICES_API_STANDARDS.md](../development/MICROSERVICES_API_STANDARDS.md)
- **Data Types**: [DATA_TYPES_STANDARDS.md](../development/DATA_TYPES_STANDARDS.md)
- **Security**: [SECURITY.md](../SECURITY.md)
- **Deployment**: [deployment/README.md](../deployment/README.md)

---

## 🔧 Technology Stack

| Category       | Technology   | Version | Purpose               |
| -------------- | ------------ | ------- | --------------------- |
| **Runtime**    | Java         | 21 LTS  | Backend language      |
| **Framework**  | Spring Boot  | 3.5.5   | Application framework |
| **Database**   | PostgreSQL   | 15      | Primary database      |
| **Cache**      | Redis        | 7       | Caching layer         |
| **Messaging**  | Apache Kafka | 3.5.1   | Event streaming       |
| **Container**  | Docker       | Latest  | Containerization      |
| **Build Tool** | Maven        | 3.9+    | Dependency management |

---

## 📈 Architecture Evolution

### v1.0 → v2.0 (September 2025)

- ✅ Established microservice boundaries
- ✅ Implemented Clean Architecture
- ✅ Added event-driven communication

### v2.0 → v3.0 (October 2025)

- ✅ Removed over-engineering (facade controllers)
- ✅ Database cleanup (43% reduction)
- ✅ Enhanced resilience (Feign + Resilience4j)
- ✅ Centralized constants
- ✅ Improved loose coupling

### Future Enhancements

- 🔮 GraphQL API layer
- 🔮 gRPC for high-performance service communication
- 🔮 Service Mesh (Istio)
- 🔮 AI/ML integration for analytics

---

## 📞 Architecture Support

### Getting Help

- **Architecture Questions**: #fabric-architecture on Slack
- **Design Review**: Schedule with Tech Lead
- **Office Hours**: Wednesday 10 AM - 12 PM

### Contributing

- Read [ARCHITECTURE.md](../ARCHITECTURE.md) completely
- Propose changes via ADR (Architecture Decision Record)
- Discuss in architecture review meeting
- Update documentation after approval

---

**Maintained By:** Backend Team  
**Last Updated:** 2025-10-10  
**Version:** 3.0  
**Status:** ✅ Active - See [ARCHITECTURE.md](../ARCHITECTURE.md) for complete details

# 🏗️ Fabric Management - System Architecture

**Version:** 2.1  
**Last Updated:** 2025-10-10 (User-Service Refactoring Complete)  
**Status:** ✅ Production Ready

---

## 📋 Quick Navigation

| What You Need               | Documentation                                                                        |
| --------------------------- | ------------------------------------------------------------------------------------ |
| 🚀 **Get Started**          | [GETTING_STARTED.md](development/GETTING_STARTED.md)                                 |
| 📁 **Code Structure**       | [CODE_STRUCTURE_GUIDE.md](development/CODE_STRUCTURE_GUIDE.md)                       |
| 📖 **Coding Principles**    | [PRINCIPLES.md](development/PRINCIPLES.md)                                           |
| 🌐 **API Standards**        | [MICROSERVICES_API_STANDARDS.md](development/MICROSERVICES_API_STANDARDS.md)         |
| 🔢 **Data Types**           | [DATA_TYPES_STANDARDS.md](development/DATA_TYPES_STANDARDS.md)                       |
| 🔐 **Policy Authorization** | [POLICY_AUTHORIZATION_PRINCIPLES.md](development/POLICY_AUTHORIZATION_PRINCIPLES.md) |
| 🤖 **AI Coding Rules**      | [AI_ASSISTANT_LEARNINGS.md](AI_ASSISTANT_LEARNINGS.md)                               |
| 🔧 **Services**             | [services/README.md](services/README.md)                                             |

---

## 🎯 System Overview

### Architecture Type

- **Microservices** - Spring Boot based
- **Event-Driven** - Kafka messaging
- **API Gateway** - Routing + Auth + Rate limiting
- **Multi-tenant** - Tenant isolation at all layers

### Core Services

1. **user-service** - User management & authentication
2. **company-service** - Company & relationships
3. **contact-service** - Contact information & verification
4. **api-gateway** - Entry point for all requests

### Shared Modules

- **shared-domain** - Base entities, exceptions, events
- **shared-application** - Responses, context
- **shared-infrastructure** - Constants, security, config
- **shared-security** - JWT, authentication filters

**Details:** [CODE_STRUCTURE_GUIDE.md](development/CODE_STRUCTURE_GUIDE.md)

---

## 🏆 Recent Updates (2025-10-10)

### User-Service Refactoring

- ✅ **Entity:** 408 → 99 lines (-76%) - Pure data holder
- ✅ **UserService:** 320 → 140 lines (-56%) - No mapping logic
- ✅ **Total:** -700+ lines removed
- ✅ **Mappers:** 3 focused mappers (SRP applied)
- ✅ **Pattern:** Anemic Domain Model adopted

**Details:** [AI_ASSISTANT_LEARNINGS.md](AI_ASSISTANT_LEARNINGS.md)

---

## 📊 Architecture Quality Score

| Principle                 | Score         |
| ------------------------- | ------------- |
| **Single Responsibility** | 9.5/10        |
| **DRY**                   | 9/10          |
| **KISS**                  | 9/10          |
| **YAGNI**                 | 9/10          |
| **Clean Code**            | 9.5/10        |
| **Overall**               | **9.2/10** 🏆 |

---

## 🎯 Key Principles

### 1. Anemic Domain Model

- Entity = Data holder ONLY
- No business methods in entities
- Use @Getter/@Setter (Lombok)

### 2. Mapper Separation

- UserMapper → DTO ↔ Entity
- EventMapper → Entity → Event
- NO mapping in Service layer

### 3. Layer Responsibilities

- **Controller:** HTTP only
- **Service:** Business logic only
- **Mapper:** Mapping only
- **Entity:** Data only

### 4. NO Over-Engineering

- NO validator/ folder (Spring @Valid)
- NO helper/ folder (private methods)
- USE Spring/Lombok/Shared modules

**Full details:** [PRINCIPLES.md](development/PRINCIPLES.md)

---

## 📂 Standard Service Structure

```
{service}-service/
├── api/              # HTTP Layer
├── application/      # Business Layer
│   ├── mapper/       # All mapping
│   └── service/      # Business logic
├── domain/           # Domain Layer
│   ├── aggregate/    # Entities (data holders)
│   ├── event/        # Domain events
│   └── valueobject/  # Enums, VOs
└── infrastructure/   # Infrastructure
    ├── repository/
    ├── client/
    ├── messaging/
    ├── security/
    └── config/
```

**Detailed structure:** [CODE_STRUCTURE_GUIDE.md](development/CODE_STRUCTURE_GUIDE.md)

---

## 🔗 Documentation Index

### Development

- [Getting Started](development/GETTING_STARTED.md) - Quick start guide
- [Code Structure](development/CODE_STRUCTURE_GUIDE.md) - Where code goes
- [Principles](development/PRINCIPLES.md) - Coding standards
- [API Standards](development/MICROSERVICES_API_STANDARDS.md) - REST API guidelines
- [Data Types](development/DATA_TYPES_STANDARDS.md) - Type safety rules
- [Code Migration](development/CODE_MIGRATION_GUIDE.md) - Refactoring guide

### Authorization

- [Policy Principles](development/POLICY_AUTHORIZATION_PRINCIPLES.md) - Complete guide
- [Policy Quick Start](development/POLICY_AUTHORIZATION_QUICK_START.md) - Implementation
- [Policy Overview](development/POLICY_AUTHORIZATION.md) - Summary

### Services

- [User Service](services/user-service.md)
- [Company Service](services/company-service.md)
- [Contact Service](services/contact-service.md)
- [API Gateway](services/api-gateway.md)

### Deployment

- [Deployment Guide](deployment/DEPLOYMENT_GUIDE.md)
- [Environment Management](deployment/ENVIRONMENT_MANAGEMENT_BEST_PRACTICES.md)
- [New Service Integration](deployment/NEW_SERVICE_INTEGRATION_GUIDE.md)

### Troubleshooting

- [Common Issues](troubleshooting/COMMON_ISSUES_AND_SOLUTIONS.md)
- [Bean Conflicts](troubleshooting/BEAN_CONFLICT_RESOLUTION.md)
- [Flyway Checksum](troubleshooting/FLYWAY_CHECKSUM_MISMATCH.md)

---

**Last Updated:** 2025-10-10 (Simplified to Navigation Index)  
**Version:** 2.1  
**Status:** ✅ Production Ready

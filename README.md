# 🏭 Fabric Management System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Architecture](https://img.shields.io/badge/Architecture-v3.0-blue.svg)](docs/ARCHITECTURE.md)

> **Modern, maintainable, and production-ready fabric management system**

## 🎉 Latest Update: Architecture v3.0 (Oct 10, 2025)

**Major architecture refactoring completed!**

- ✅ Loose Coupling (removed facade controllers)
- ✅ Database Cleanup (43% reduction)
- ✅ Feign + Resilience4j (circuit breaker + fallback)
- ✅ Centralized Constants (no hardcoded values)

👉 [View Full Changelog](docs/reports/2025-Q4/october/ARCHITECTURE_REFACTORING_OCT_10_2025.md)

---

## ⚠️ **CRITICAL: NO USERNAME IN THIS PROJECT** ⚠️

```
╔════════════════════════════════════════════════════════════╗
║                                                            ║
║  🚫 THIS PROJECT DOES NOT USE USERNAME!                   ║
║                                                            ║
║  ✅ Authentication: contactValue (email/phone)            ║
║  ✅ Identification: userId (UUID)                          ║
║  ✅ JWT 'sub' claim: userId (UUID string)                 ║
║                                                            ║
║  📖 See: docs/development/PRINCIPLES.md                   ║
║          → "NO USERNAME PRINCIPLE"                         ║
║                                                            ║
╚════════════════════════════════════════════════════════════╝
```

**Quick Reference:**

- 🔐 **Login**: Use `contactValue` (email or phone), NOT `username`
- 🆔 **User ID**: Always UUID, never username
- 🎫 **JWT**: Contains `userId` (UUID), NOT `username` or email

---

## 📋 Overview

Fabric Management System is a comprehensive platform for fabric manufacturing, inventory management, and order processing built with Spring Boot best practices and clean architecture principles.

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose
- PostgreSQL 15+ (via Docker)

### Installation

```bash
# Clone repository
git clone https://github.com/your-org/fabric-management-backend.git
cd fabric-management-backend

# Start with Docker
make deploy

# Or manually
docker-compose up -d
mvn clean install
mvn spring-boot:run
```

## 📁 Project Structure

```
fabric-management-backend/
├── docs/                    # 📚 All documentation
├── services/                # 🚀 Microservices (user, company, contact, api-gateway)
├── shared/                  # 🔗 Shared modules (domain, infrastructure, security)
├── scripts/                 # 🛠️ Utility scripts
├── monitoring/              # 📊 Prometheus & Grafana config
└── docker-compose.yml       # 🐳 Docker infrastructure
```

**📖 Complete structure:** [docs/development/CODE_STRUCTURE_GUIDE.md](docs/development/CODE_STRUCTURE_GUIDE.md)

## 📊 Current Status

### ✅ Recently Completed (October 2025)

#### 🔐 Policy Authorization System (Phase 3 Complete - Oct 10, 2025)

**Core Infrastructure:**

- ✅ **Policy Engine (PDP)**: Centralized authorization decisions + PolicyRegistry lookup
- ✅ **Policy Audit Service**: DB logging + Kafka event publishing
- ✅ **Policy Cache**: In-memory caching (Redis-ready)

**Gateway Integration:**

- ✅ **Policy Enforcement (PEP)**: PolicyEnforcementFilter with audit logging
- ✅ **Reactive Audit Publisher**: Kafka-only audit for reactive Gateway
- ✅ **Latency Tracking**: Performance monitoring

**Microservices Integration:**

- ✅ **Defense-in-Depth**: PolicyValidationFilter in all 3 services
- ✅ **Gateway Bypass Protection**: Secondary policy checks
- ✅ **Company Type Guardrails**: INTERNAL/CUSTOMER/SUPPLIER/SUBCONTRACTOR

**Policy Management:**

- ✅ **User Grants API**: Advanced Settings (UserPermission CRUD)
- ✅ **PolicyRegistry**: Database-driven policy configuration
- ✅ **Audit Dashboard**: Admin API for policy decision analysis

**📊 Metrics:**

- ✅ **Coverage:** 95% (Gateway + 3 Services integrated)
- ✅ **Defense Layers:** 2 (Gateway + Service level)
- ✅ **Audit Coverage:** 100% (all decisions logged to Kafka)
- ✅ **Tests:** 41 unit/integration tests (100% pass rate)
- ✅ **Latency Impact:** +7ms average (negligible)

**📖 Full Report:** [POLICY_INTEGRATION_COMPLETE_REPORT.md](POLICY_INTEGRATION_COMPLETE_REPORT.md)

#### Security & Infrastructure

- ✅ **API Gateway Security**: JWT authentication and authorization
- ✅ **Rate Limiting**: Endpoint-specific request throttling
- ✅ **Brute Force Protection**: Redis-based login attempt tracking
- ✅ **Custom Exception Handling**: Domain-specific exceptions
- ✅ **Security Audit Logging**: SIEM-ready structured logs
- ✅ **Response Time Masking**: Timing attack prevention
- ✅ **Password Validation**: Contact verification + status checks
- ✅ **Configuration Management**: No hardcoded values

### 🎯 Security Score

| Category               | Score      |
| ---------------------- | ---------- |
| API Gateway Security   | 9/10       |
| Rate Limiting          | 9/10       |
| Authentication Flow    | 9/10       |
| Exception Handling     | 9/10       |
| Brute Force Protection | 9/10       |
| **Overall**            | **8.8/10** |

See [SECURITY.md](docs/SECURITY.md) for detailed security documentation.

## 🛠️ Technology Stack

- **Backend**: Java 21, Spring Boot 3.5.5
- **Database**: PostgreSQL 15, Flyway migrations
- **Caching**: Redis 7
- **Messaging**: Apache Kafka
- **Containerization**: Docker, Docker Compose
- **Build**: Maven

## 🏛️ Architecture Principles

Our microservice architecture is built on solid software engineering principles:

- ✅ **Domain-Driven Design (DDD)** - Business logic organized around domain concepts
- ✅ **Clean Architecture** - Clear separation of concerns with defined boundaries
- ✅ **Loose Coupling** - Minimal dependencies between services and components
  - Event-driven communication via Kafka
  - Interface-based Feign clients with fallbacks
  - Database per service pattern
  - DTO layer separates API contracts from domain models
- ✅ **SOLID Principles** - Object-oriented design principles
- ✅ **CQRS Pattern** - Command Query Responsibility Segregation
- ✅ **Event Sourcing** - Audit trail and state replay capability

**See:** [Architecture Guide](docs/architecture/README.md) | [Development Principles](docs/development/principles.md)

## 📚 Documentation

| Document                                                                                        | Description                                                |
| ----------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| [🤖 AI Assistant Learnings](docs/AI_ASSISTANT_LEARNINGS.md)                                     | **AI: READ FIRST!** - Lessons learned & working principles |
| [⭐⭐⭐ Microservices & API Gateway Standards](docs/development/MICROSERVICES_API_STANDARDS.md) | **CRITICAL** - Controller routing & API Gateway patterns   |
| [🔐 Security Documentation](docs/SECURITY.md)                                                   | Complete security guide and best practices                 |
| [🔢 Data Types Standards](docs/development/DATA_TYPES_STANDARDS.md)                             | UUID and identifier usage standards                        |
| [👤 User Service](docs/services/user-service.md)                                                | User service documentation with security features          |
| [🚪 API Gateway Setup](docs/deployment/API_GATEWAY_SETUP.md)                                    | Gateway configuration with rate limiting                   |
| [Spring Boot Best Practices Analysis](docs/analysis/SPRING_BOOT_BEST_PRACTICES_ANALYSIS.md)     | Code quality and best practices analysis                   |
| [Microservice Development Analysis](docs/analysis/MICROSERVICE_DEVELOPMENT_ANALYSIS.md)         | Microservice architecture analysis                         |
| [Architecture Guide](docs/architecture/README.md)                                               | System architecture overview                               |
| [API Documentation](docs/api/README.md)                                                         | REST API specifications                                    |
| [Deployment Guide](docs/deployment/README.md)                                                   | Production deployment instructions                         |
| [Development Guide](docs/development/README.md)                                                 | Development setup and guidelines                           |

## 🔧 Available Commands

```bash
# Build & Deploy
make build          # Build all services
make deploy         # Deploy with Docker
make down           # Stop all services
make restart        # Restart services

# Development
make test           # Run tests
make logs           # View logs
make status         # Check service status
make health         # Health check

# Database
make db-migrate     # Run migrations
make db-backup      # Backup database
make db-shell       # Open PostgreSQL shell

# Cleanup
make clean          # Clean build artifacts
make prune          # Docker system prune
```

## 🏗️ Services

| Service         | Port | Description                        | Status        |
| --------------- | ---- | ---------------------------------- | ------------- |
| API Gateway     | 8080 | Central entry point with JWT auth  | ✅ Production |
| User Service    | 8081 | User management and authentication | ✅ Production |
| Contact Service | 8082 | Contact information management     | ✅ Production |
| Company Service | 8083 | Company and tenant management      | ✅ Production |
| PostgreSQL      | 5433 | Primary database                   | ✅ Ready      |
| Redis           | 6379 | Caching + Rate limiting            | ✅ Ready      |
| Kafka           | 9092 | Event streaming                    | ✅ Ready      |

## 🔐 Security Features

✅ **Production-ready security implemented:**

### Authentication & Authorization

- **JWT Authentication**: Token-based authentication with Gateway-level validation
- **Policy Authorization System** (NEW): PEP/PDP architecture for fine-grained access control
- **Company Type Guardrails**: Role-based access per company type (INTERNAL/CUSTOMER/SUPPLIER)
- **User Grants**: Endpoint-level permissions (Advanced Settings)
- **Data Scope Validation**: SELF/COMPANY/CROSS_COMPANY/GLOBAL

### Attack Prevention

- **Rate Limiting**: Endpoint-specific throttling (5-50 req/min based on sensitivity)
- **Brute Force Protection**: 5 failed attempts → 15 min account lockout
- **Response Time Masking**: 200ms minimum response (timing attack prevention)
- **Password Security**: BCrypt hashing, strong password requirements

### Audit & Compliance

- **Policy Audit Trail**: Immutable log of all authorization decisions
- **Security Event Logging**: Structured logging (SIEM-ready)
- **Correlation ID Tracing**: Distributed request tracking
- **Explainable Decisions**: Every DENY includes reason

### Code Security

- **Custom Exceptions**: 8 domain-specific exceptions with proper HTTP status codes
- **UUID Type Safety**: 100% compliance, no String IDs
- **Contact Verification**: Required before password setup

See [SECURITY.md](docs/SECURITY.md) and [Policy Authorization Docs](docs/development/POLICY_AUTHORIZATION_README.md) for details.

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# Integration tests
mvn verify
```

Current test coverage: **15%** (Target: 80%)

## 📈 Performance Metrics

| Metric              | Current | Target |
| ------------------- | ------- | ------ |
| Startup Time        | 30s     | 5s     |
| Memory Usage        | 1GB     | 256MB  |
| Response Time (p95) | 500ms   | 100ms  |
| Test Coverage       | 15%     | 80%    |

## 🤝 Contributing

1. Follow Spring Boot best practices
2. Write tests for new features
3. Update documentation
4. Use conventional commits

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For issues and questions:

- Check [documentation](docs/)
- Create an issue on GitHub
- Contact the development team

---

**Version:** 2.0.0  
**Last Updated:** 2025-10-09 14:52 UTC+1  
**Status:** ✅ Production Ready - Policy Authorization System Live

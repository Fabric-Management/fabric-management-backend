# 🏢 Company Service

Fabric Management System'in şirket yönetimi ve multi-tenancy mikroservisi.

## 🚀 Quick Start

```bash
# Run locally
cd services/company-service
mvn spring-boot:run

# With Docker
docker-compose up company-service

# Access
curl http://localhost:8083/actuator/health
```

## ⚡ Key Features

- ✅ Company CRUD operations
- ✅ Multi-tenancy (tenant isolation)
- ✅ Company status management (ACTIVE, INACTIVE, SUSPENDED)
- ✅ Subscription plan management
- ✅ Company settings & preferences
- ✅ Department management
- ✅ Event sourcing with Outbox Pattern

## 🏗️ Architecture

**Clean Architecture + CQRS Pattern:**

- **API**: Controllers, DTOs
- **Application**: Services, Mappers, Command/Query handlers
- **Domain**: Company aggregate, Events, Value Objects
- **Infrastructure**: Repositories, Feign clients, Messaging

**📖 Detaylı mimari:** [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md)

## 🔗 Service Integration

- **User Service**: Feign client for company users
- **Contact Service**: Feign client for company contacts
- **Resilience4j**: Circuit breaker + fallback mechanisms

## ⚙️ Configuration

```yaml
# application.yml key settings
server:
  port: 8083

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/company_db
```

## 🔗 Service Dependencies

- **User Service**: Company-user relationships (Feign)
- **Contact Service**: Company contact info (Feign)
- **PostgreSQL**: Primary database (company_db schema)
- **Redis**: Company data caching
- **Kafka**: Event publishing (CompanyCreatedEvent, etc.)

## 🧪 Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify
```

## 🐛 Troubleshooting

| Issue                     | Solution                         |
| ------------------------- | -------------------------------- |
| Port 8083 already in use  | `lsof -i :8083` and kill process |
| Feign client timeout      | Check target service health      |
| Database connection error | Check PostgreSQL is running      |

## 📚 Documentation

- **API Documentation**: [docs/api/README.md](../../docs/api/README.md)
- **Architecture**: [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md)
- **Troubleshooting**: [docs/troubleshooting/README.md](../../docs/troubleshooting/README.md)

---

**Port:** 8083  
**Database:** company_db  
**Status:** ✅ Production

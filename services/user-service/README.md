# 👤 User Service

Fabric Management System'in kullanıcı yönetimi ve authentication mikroservisi.

## 🚀 Quick Start

```bash
# Run locally
cd services/user-service
mvn spring-boot:run

# With Docker
docker-compose up user-service

# Access
curl http://localhost:8081/actuator/health
```

## ⚡ Key Features

- ✅ User authentication (contactValue: email/phone, NO username!)
- ✅ JWT token generation & validation
- ✅ Password management (BCrypt hashing)
- ✅ Multi-contact support (email + phone per user)
- ✅ Password reset flow with tokens
- ✅ User status management (ACTIVE, INACTIVE, PENDING_VERIFICATION)
- ✅ Multi-tenancy support

## 🏗️ Architecture

**Clean Architecture** with 4 layers:

- **API**: Controllers, DTOs
- **Application**: Services, Mappers
- **Domain**: User entity, domain logic
- **Infrastructure**: JPA repositories, Feign clients

**📖 Complete architecture:** [docs/services/user-service.md](../../docs/services/user-service.md)

## 🔐 Authentication

**NO USERNAME!** This service uses:

- `contactValue` (email or phone) for login
- `userId` (UUID) for identification
- JWT with `userId` in 'sub' claim

See: [NO USERNAME PRINCIPLE](../../docs/development/PRINCIPLES.md#-no-username-principle)

## ⚙️ Configuration

```yaml
# application.yml key settings
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/user_db
```

## 🔗 Service Dependencies

- **Contact Service**: Feign client for contact information
- **PostgreSQL**: Primary database (user_db schema)
- **Redis**: Session caching
- **Kafka**: Event publishing (UserCreatedEvent, etc.)

## 🧪 Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# With coverage
mvn clean verify jacoco:report
```

## 🐛 Troubleshooting

| Issue                     | Solution                                                                                  |
| ------------------------- | ----------------------------------------------------------------------------------------- |
| Port 8081 already in use  | `lsof -i :8081` and kill process                                                          |
| Database connection error | Check PostgreSQL is running                                                               |
| Bean conflict error       | See [BEAN_CONFLICT_RESOLUTION.md](../../docs/troubleshooting/BEAN_CONFLICT_RESOLUTION.md) |

## 📚 Documentation

- **Full Service Docs**: [docs/services/user-service.md](../../docs/services/user-service.md)
- **API Documentation**: [docs/api/README.md](../../docs/api/README.md)
- **Architecture**: [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md)

---

**Port:** 8081  
**Database:** user_db  
**Status:** ✅ Production

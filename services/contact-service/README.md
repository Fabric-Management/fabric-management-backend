# 📞 Contact Service

Fabric Management System'in iletişim bilgileri yönetimi mikroservisi.

## 🚀 Quick Start

```bash
# Run locally
cd services/contact-service
mvn spring-boot:run

# With Docker
docker-compose up contact-service

# Access
curl http://localhost:8082/actuator/health
```

## ⚡ Key Features

- ✅ Contact information CRUD (emails, phones, addresses)
- ✅ Multi-contact per user/company
- ✅ Contact verification (email/phone)
- ✅ Primary contact designation
- ✅ Contact history tracking
- ✅ Multi-tenancy support

## 🏗️ Architecture

**Clean Architecture** with 4 layers:

- **API**: Controllers, DTOs
- **Application**: Services, Mappers
- **Domain**: Contact entity, domain logic
- **Infrastructure**: JPA repositories

**📖 Complete architecture:** [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md)

## ⚙️ Configuration

```yaml
# application.yml key settings
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/contact_db
```

## 🔗 Service Dependencies

- **PostgreSQL**: Primary database (contact_db schema)
- **Redis**: Contact caching
- **Kafka**: Event publishing (ContactCreatedEvent, etc.)

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
| Port 8082 already in use  | `lsof -i :8082` and kill process |
| Database connection error | Check PostgreSQL is running      |

## 📚 Documentation

- **API Documentation**: [docs/api/README.md](../../docs/api/README.md)
- **Architecture**: [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md)
- **Troubleshooting**: [docs/troubleshooting/README.md](../../docs/troubleshooting/README.md)

---

**Port:** 8082  
**Database:** contact_db  
**Status:** ✅ Production

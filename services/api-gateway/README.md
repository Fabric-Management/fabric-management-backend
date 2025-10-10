# 🚪 API Gateway

Central entry point for all Fabric Management System microservices.

## 🚀 Quick Start

```bash
# Run locally
cd services/api-gateway
mvn spring-boot:run

# With Docker
docker-compose up api-gateway

# Access
curl http://localhost:8080/actuator/health
```

## ⚡ Key Features

- ✅ **Centralized Routing** - Single entry point for all services
- ✅ **JWT Authentication** - Token validation at gateway level
- ✅ **Rate Limiting** - Redis-based request throttling
- ✅ **Circuit Breaker** - Resilience4j fault tolerance
- ✅ **Policy Authorization** - PEP (Policy Enforcement Point)
- ✅ **CORS Support** - Cross-origin resource sharing
- ✅ **Request Logging** - Comprehensive audit trail

## 🎯 Routes

| Path Pattern           | Target Service  | Port |
| ---------------------- | --------------- | ---- |
| `/api/v1/users/**`     | user-service    | 8081 |
| `/api/v1/contacts/**`  | contact-service | 8082 |
| `/api/v1/companies/**` | company-service | 8083 |

**Service-Aware Pattern:** Gateway does NOT strip prefix.  
**📖 Complete routing guide:** [docs/development/MICROSERVICES_API_STANDARDS.md](../../docs/development/MICROSERVICES_API_STANDARDS.md)

## 🔐 Security

- **JWT Validation**: Every request except public endpoints
- **Policy Enforcement**: PDP (Policy Decision Point) integration
- **Rate Limiting**: 100 req/min general, 5 req/min authentication

**📖 Security architecture:** [docs/SECURITY.md](../../docs/SECURITY.md)

## ⚙️ Configuration

```yaml
# application.yml key settings
server:
  port: 8080

spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/v1/users/**
```

## 🧪 Testing

```bash
# Unit tests
mvn test

# Test routes
curl http://localhost:8080/api/v1/users/health
```

## 🐛 Troubleshooting

| Issue                    | Solution                          |
| ------------------------ | --------------------------------- |
| Port 8080 already in use | `lsof -i :8080` and kill process  |
| Service routing fails    | Check target service is running   |
| JWT validation fails     | Check JWT secret matches services |

## 📚 Documentation

- **API Standards**: [docs/development/MICROSERVICES_API_STANDARDS.md](../../docs/development/MICROSERVICES_API_STANDARDS.md)
- **Architecture**: [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md)
- **Security**: [docs/SECURITY.md](../../docs/SECURITY.md)

---

**Port:** 8080  
**Technology:** Spring Cloud Gateway  
**Status:** ✅ Production

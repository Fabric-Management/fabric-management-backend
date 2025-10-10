# 🚪 API Gateway - Complete Documentation

**Version:** 2.0  
**Last Updated:** October 10, 2025  
**Port:** 8080  
**Technology:** Spring Cloud Gateway  
**Status:** ✅ Production

---

## 📋 Overview

API Gateway serves as the single entry point for all Fabric Management System microservices, providing centralized routing, authentication, rate limiting, and policy enforcement.

### Core Responsibilities

- Centralized request routing to microservices
- JWT authentication & validation
- Policy-based authorization (PEP - Policy Enforcement Point)
- Rate limiting (Redis-based)
- Circuit breaker & fallback mechanisms
- CORS configuration
- Request/response logging
- Distributed tracing

---

## 🏗️ Architecture

### Gateway Flow

```
Client Request
    ↓
API Gateway (8080)
    ↓
[Pre-Filters]
├─ JwtAuthenticationFilter    # JWT validation
├─ PolicyEnforcementFilter     # Authorization
└─ RateLimitingFilter          # Throttling
    ↓
[Routing]
├─ /api/v1/users/**      → User Service (8081)
├─ /api/v1/contacts/**   → Contact Service (8082)
└─ /api/v1/companies/**  → Company Service (8083)
    ↓
[Post-Filters]
└─ ResponseLoggingFilter       # Audit logging
    ↓
Client Response
```

### Routing Strategy: Service-Aware Pattern

**No prefix stripping!** All services use full paths.

```yaml
# Gateway Configuration
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/v1/users/**
          # NO StripPrefix filter!
```

**📖 Complete routing guide:** [docs/development/MICROSERVICES_API_STANDARDS.md](../development/MICROSERVICES_API_STANDARDS.md)

---

## 🔐 Security Architecture

### 1. JWT Authentication

```
Request → Extract JWT Token → Validate Signature → Extract Claims
                                      ↓
                                  Valid/Invalid
                                      ↓
                            Allow/Reject Request
```

**Implementation:** `JwtAuthenticationFilter`  
**Claims:** `userId`, `tenantId`, `companyId`, `roles`

### 2. Policy Authorization (PEP)

```
Request → Policy Enforcement Filter → Call PDP
                                         ↓
                                  ALLOW/DENY decision
                                         ↓
                              Execute/Block Request
```

**Implementation:** `PolicyEnforcementFilter`  
**PDP:** Policy Decision Point in shared-infrastructure

### 3. Rate Limiting

| Endpoint Pattern        | Limit       | Implementation |
| ----------------------- | ----------- | -------------- |
| `/api/v1/users/auth/**` | 5 req/min   | Redis-based    |
| `/api/v1/**` (general)  | 100 req/min | Redis-based    |
| File uploads            | 10 req/min  | Redis-based    |

**Implementation:** `RateLimitingFilter` with Redis backend

---

## 🔄 Resilience Patterns

### Circuit Breaker

```java
@CircuitBreaker(name = "user-service", fallbackMethod = "userServiceFallback")
public Mono<ResponseEntity> routeToUserService(ServerWebExchange exchange) {
    // Route to user service
}
```

**Configuration:**

- Failure threshold: 50%
- Wait duration: 60 seconds
- Sliding window: 10 requests

### Fallback Mechanisms

- Return cached response if available
- Return degraded service message
- Log circuit open events

---

## 📊 Filters & Processing Order

### Pre-Filters (Executed Before Routing)

1. **JwtAuthenticationFilter** (Order: -100)

   - Validates JWT token
   - Extracts SecurityContext
   - Sets authentication in reactor context

2. **PolicyEnforcementFilter** (Order: -90)

   - Calls Policy Decision Point (PDP)
   - Enforces ALLOW/DENY decisions
   - Logs policy audit trail

3. **RateLimitingFilter** (Order: -80)
   - Checks request rate
   - Returns 429 Too Many Requests if exceeded

### Post-Filters (Executed After Routing)

4. **ResponseLoggingFilter** (Order: 100)
   - Logs response status
   - Audit trail
   - Performance metrics

---

## 🎯 Routing Configuration

### Current Routes

```yaml
routes:
  - id: user-service
    uri: http://user-service:8081
    predicates:
      - Path=/api/v1/users/**
    filters:
      - name: CircuitBreaker
        args:
          name: user-service-cb
          fallbackUri: forward:/fallback/user-service

  - id: company-service
    uri: http://company-service:8083
    predicates:
      - Path=/api/v1/companies/**

  - id: contact-service
    uri: http://contact-service:8082
    predicates:
      - Path=/api/v1/contacts/**
```

### Service Discovery Integration

**Future Enhancement:** Consul/Eureka integration planned

---

## 📈 Performance

### Response Times

| Operation      | Target | Actual | Status       |
| -------------- | ------ | ------ | ------------ |
| JWT Validation | <50ms  | ~30ms  | ✅ Good      |
| Routing        | <100ms | ~60ms  | ✅ Good      |
| Policy Check   | <100ms | ~40ms  | ✅ Excellent |

### Caching

- JWT validation results: 5 minutes
- Policy decisions: 10 minutes
- Service health status: 30 seconds

---

## 🧪 Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Test routing
curl -H "Authorization: Bearer TOKEN" http://localhost:8080/api/v1/users
```

---

## 🐛 Common Issues

| Issue                       | Solution                                             |
| --------------------------- | ---------------------------------------------------- |
| Routing fails               | Check target service is running: `docker-compose ps` |
| JWT validation fails        | Ensure JWT_SECRET matches across services            |
| Rate limit not working      | Check Redis connection                               |
| Circuit breaker always open | Check service health endpoints                       |

**📖 Complete troubleshooting:** [docs/troubleshooting/COMMON_ISSUES_AND_SOLUTIONS.md](../troubleshooting/COMMON_ISSUES_AND_SOLUTIONS.md)

---

## 📚 Related Documentation

- **System Architecture**: [docs/ARCHITECTURE.md](../ARCHITECTURE.md)
- **API Standards**: [docs/development/MICROSERVICES_API_STANDARDS.md](../development/MICROSERVICES_API_STANDARDS.md)
- **Security**: [docs/SECURITY.md](../SECURITY.md)
- **Deployment**: [docs/deployment/API_GATEWAY_SETUP.md](../deployment/API_GATEWAY_SETUP.md)

---

**Maintained By:** Backend Team  
**Last Updated:** 2025-10-10  
**Status:** ✅ Production - Central entry point for all services

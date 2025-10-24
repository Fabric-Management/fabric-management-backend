# 🔐 AUTH-SERVICE COMMUNICATION PATTERN

**Version:** 1.0 • **Status:** Production-Ready Design  
**Applies to:** Auth-Service (Authentication & Authorization)  
**Pattern:** Centralized Auth Hub with Event-Driven Notifications

---

## 📋 MANIFESTO COMPLIANCE

✅ **ZERO HARDCODED VALUES** — All URLs, topics, secrets via environment variables  
✅ **ZERO OVER ENGINEERING** — Simple, focused authentication logic  
✅ **PRODUCTION-READY** — Google/Amazon/Netflix level reliability  
✅ **ORCHESTRATION + CHOREOGRAPHY** — Auth orchestration + event choreography  
✅ **CLEAN CODE** — SOLID, DRY, YAGNI, KISS, SRP principles  
✅ **EVENT-DRIVEN NATIVE** — OutboxEvent pattern for all notifications  
✅ **SHARED-FIRST** — Leverage shared modules for consistency

---

## 🎯 AUTH-SERVICE ROLE

**Distributed Authentication & Authorization Hub**

- **Authentication:** Login, logout, token management
- **Authorization:** Role/permission validation
- **Security:** Account locking, password policies
- **Audit:** Security event logging
- **Integration:** Services authenticate through auth-service (NOT all operations)

**⚠️ ANTI-PATTERN AVOIDANCE:**

- **NOT a Single Point of Failure (SPOF)** - Services can operate independently
- **NOT a monolithic auth gateway** - Distributed responsibility
- **Event-driven first** - Prefer async communication over sync

---

## 🔄 COMMUNICATION PATTERNS

### **1. SYNCHRONOUS COMMUNICATION (Feign)**

#### **1.1 User-Service** 👤

```yaml
Purpose: User profile data, role assignments
Pattern: InternalEndpoint + JWT propagation (minimal sync calls)
Endpoints:
  - GET /api/v1/users/{userId} - User profile (cached)
  - POST /api/v1/users/{userId}/roles - Role assignment (event-driven)
Security: JWT context only (no Internal API Key for service-to-service)
Anti-Pattern: Avoid frequent sync calls - use events instead
```

#### **1.2 Contact-Service** 📞

```yaml
Purpose: Contact verification, ContactType enum
Pattern: Event-driven first, sync only when necessary
Endpoints:
  - GET /api/v1/contacts/{contactId} - Contact verification (cached)
Security: JWT context only
Anti-Pattern: Avoid sync verification - use ContactVerificationEvent
```

#### **1.3 Company-Service** 🏢

```yaml
Purpose: Tenant validation, company context
Pattern: Event-driven first, sync only for critical operations
Endpoints:
  - GET /api/v1/companies/{companyId} - Tenant validation (cached)
Security: JWT context only
Anti-Pattern: Avoid frequent tenant checks - use events
```

#### **1.4 API-Gateway** 🌐

```yaml
Purpose: JWT validation, authentication delegation
Pattern: Public endpoint + Internal API Key (gateway-specific)
Endpoints:
  - POST /api/v1/auth/validate - JWT validation
  - POST /api/v1/auth/refresh - Token refresh
Security: Internal API Key (gateway only)
Note: Only API-Gateway uses Internal API Key
```

### **2. ASYNCHRONOUS COMMUNICATION (Kafka)**

#### **2.1 Consolidated Security Events** 🔐

```yaml
Purpose: Unified security event handling
Pattern: Single SecurityEvent with eventType differentiation
Topic: security-events (single topic for all security events)
Event Structure:
  eventType: USER_LOGIN | USER_LOGOUT | ACCOUNT_LOCKED | PASSWORD_CHANGED | ROLE_CHANGED
  tenantId: UUID
  userId: UUID
  payload: Event-specific data
  occurredAt: Timestamp
  traceId: Correlation ID
Consumers: Notification-Service, Audit-Service, Analytics-Service
```

#### **2.2 Event-Driven Service Updates** 🔄

```yaml
Purpose: Service state synchronization without tight coupling
Pattern: Event-driven updates (not sync calls)
Events:
  - UserStatusChangedEvent → Auth-Service updates user status
  - ContactVerificationEvent → Auth-Service updates verification status
  - TenantSecurityEvent → Auth-Service updates security settings
Anti-Pattern: Avoid sync calls for status updates
```

---

## 🏗️ ARCHITECTURE PATTERN

### **Distributed Auth Hub Pattern (Anti-SPOF)**

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   API-Gateway   │────│  Auth-Service   │    │  User-Service   │
│                 │    │                 │    │                 │
│ JWT Validation  │    │ Authentication  │    │ User Profiles   │
│ Internal API Key│    │ Event Producer  │    │ Event Consumer  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Contact-Service │    │Company-Service  │    │Notification-Svc│
│                 │    │                 │    │                 │
│ Event Consumer  │    │ Event Consumer  │    │ Event Consumer  │
│ Independent     │    │ Independent     │    │ Independent     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### **Event-Driven Security Pattern (No SPOF)**

```
Auth Action → OutboxEvent → Kafka → All Services
     │              │           │
     ▼              ▼           ▼
  Security      Guaranteed   Event-Driven
  Validation    Delivery     Notifications
     │              │           │
     ▼              ▼           ▼
  Services      Services     Services
  Independent   Independent  Independent
```

### **Token Revocation Pattern**

```
Token Revocation → Redis Blacklist → JWT Validation Check
     │                    │                    │
     ▼                    ▼                    ▼
  Immediate         Distributed          All Services
  Invalidation      Cache Update         Check Blacklist
```

---

## 📊 COMMUNICATION MATRIX

| Service                  | Sync (Feign)        | Async (Kafka)          | Purpose            | Priority  | SPOF Risk |
| ------------------------ | ------------------- | ---------------------- | ------------------ | --------- | --------- |
| **User-Service**         | ✅ Profile (cached) | ✅ Status Events       | User Management    | 🔴 HIGH   | 🟢 LOW    |
| **Contact-Service**      | ✅ Verification     | ✅ Verification Events | Contact Validation | 🔴 HIGH   | 🟢 LOW    |
| **Company-Service**      | ✅ Tenant (cached)  | ✅ Security Events     | Tenant Context     | 🔴 HIGH   | 🟢 LOW    |
| **API-Gateway**          | ✅ JWT Validation   | ❌ N/A                 | Authentication     | 🔴 HIGH   | 🟡 MEDIUM |
| **Notification-Service** | ❌ N/A              | ✅ Security Events     | Security Alerts    | 🔴 HIGH   | 🟢 LOW    |
| **Fiber-Service**        | ❌ N/A              | ✅ Security Events     | Fiber Security     | 🟡 MEDIUM | 🟢 LOW    |
| **Order-Service**        | ❌ N/A              | ✅ Security Events     | Order Security     | 🟡 MEDIUM | 🟢 LOW    |
| **Task-Service**         | ❌ N/A              | ✅ Security Events     | Task Security      | 🟡 MEDIUM | 🟢 LOW    |

**SPOF Risk Assessment:**

- 🟢 LOW: Service can operate independently
- 🟡 MEDIUM: Service has some dependencies but can degrade gracefully
- 🔴 HIGH: Service is critical dependency (avoid this)

---

## 🔧 IMPLEMENTATION PATTERNS

### **1. Consolidated Security Event Pattern**

```java
// Single SecurityEvent base class
public class SecurityEvent {
    private String eventType; // USER_LOGIN, ACCOUNT_LOCKED, etc.
    private UUID tenantId;
    private UUID userId;
    private Object payload;
    private LocalDateTime occurredAt;
    private String traceId;
}

// Event publishing
@Transactional
public AuthUser registerUser(...) {
    AuthUser user = authUserRepository.save(newUser);

    // Single consolidated event
    SecurityEvent event = SecurityEvent.builder()
        .eventType("USER_REGISTRATION")
        .tenantId(user.getTenantId())
        .userId(user.getId())
        .payload(userRegistrationData)
        .occurredAt(LocalDateTime.now())
        .traceId(UUID.randomUUID().toString())
        .build();

    outboxEventRepository.save(event);
    return user;
}
```

### **2. Token Revocation with Redis Blacklist**

```java
@Service
public class TokenRevocationService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void revokeToken(String token) {
        // Add to blacklist with TTL
        String tokenHash = hashToken(token);
        redisTemplate.opsForValue().set(
            "blacklist:" + tokenHash,
            "revoked",
            Duration.ofHours(24)
        );
    }

    public boolean isTokenRevoked(String token) {
        String tokenHash = hashToken(token);
        return redisTemplate.hasKey("blacklist:" + tokenHash);
    }
}
```

### **3. Event-Driven Service Updates (Anti-SPOF)**

```java
// Auth-Service consumes events from other services
@KafkaListener(topics = "user-events")
public void handleUserStatusChanged(UserStatusChangedEvent event) {
    // Update local user status without sync call
    authUserRepository.updateUserStatus(
        event.getUserId(),
        event.getIsActive()
    );
}

// No direct sync calls to User-Service for status updates
```

### **4. Circuit Breaker with Graceful Degradation**

```java
@FeignClient(name = "user-service", configuration = BaseFeignClientConfig.class)
public interface UserServiceClient {

    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserFallback")
    @GetMapping("/api/v1/users/{userId}")
    ApiResponse<UserResponse> getUser(@PathVariable UUID userId);

    // Fallback method - return cached data
    default ApiResponse<UserResponse> getUserFallback(UUID userId, Exception ex) {
        UserResponse cachedUser = userCacheService.getCachedUser(userId);
        return ApiResponse.success(cachedUser, "Data from cache");
    }
}
```

---

## 🛡️ SECURITY PATTERNS

### **1. Multi-Layer Security (Simplified)**

- **API Gateway:** JWT validation, rate limiting, Internal API Key
- **Auth-Service:** JWT context propagation (no Internal API Key for service-to-service)
- **Services:** JWT context propagation only

### **2. Event Security (PII Protection)**

- **PII Masking:** No sensitive data in events (passwords, personal info)
- **Tenant Isolation:** All events include tenantId
- **Audit Trail:** All security events logged with retention policy
- **Data Classification:** Public, Internal, Confidential, Restricted

### **3. Token Management (Production-Ready)**

- **JWT Tokens:** Short-lived access tokens (15 minutes)
- **Refresh Tokens:** Long-lived refresh tokens (7 days)
- **Token Revocation:** Redis blacklist with TTL
- **Token Rotation:** Automatic refresh token rotation
- **Session Management:** Distributed session store (Redis)

### **4. Audit Trail & Retention**

```yaml
Security Audit Logs: 1 year retention
Activity Logs: 90 days retention
Error Logs: 30 days retention
Archival: S3 + ElasticSearch
Compliance: GDPR, SOX, PCI-DSS ready
```

---

## 📈 PERFORMANCE PATTERNS

### **1. Intelligent Caching Strategy**

```yaml
User Profiles: Redis cache (5 minutes TTL, version-based invalidation)
JWT Validation: In-memory cache (1 minute TTL)
Role Permissions: Redis cache (10 minutes TTL, event-based invalidation)
Token Blacklist: Redis cache (24 hours TTL)
Company Settings: Redis cache (30 minutes TTL)
```

### **2. Event-Based Cache Invalidation**

```java
@KafkaListener(topics = "user-events")
public void handleUserRoleChanged(UserRoleChangedEvent event) {
    // Invalidate role cache for specific user
    cacheService.evict("user-roles:" + event.getUserId());

    // Update local cache
    updateLocalRoleCache(event.getUserId(), event.getNewRole());
}
```

### **3. Circuit Breaker with Intelligent Fallback**

```yaml
User-Service: 50% failure threshold, cached data fallback
Contact-Service: 50% failure threshold, cached data fallback
Company-Service: 50% failure threshold, cached data fallback
Fallback Strategy: Return cached data or graceful degradation
```

### **4. Batch Operations & Async Processing**

```yaml
Role Assignments: Batch endpoint for multiple users
Token Validation: Batch validation endpoint
Event Publishing: Batch outbox processing (every 100ms)
Database Operations: Batch inserts/updates
```

### **5. Database Optimization**

```yaml
Connection Pooling: HikariCP with optimal settings
Query Optimization: Indexed queries, prepared statements
Read Replicas: Read-only operations on replicas
Partitioning: Tenant-based partitioning for large datasets
```

---

## 🔍 MONITORING & OBSERVABILITY

### **1. Comprehensive Metrics**

```yaml
Authentication Metrics:
  - Success/failure rates by tenant
  - Token validation latency (p95/p99)
  - Account lockout frequency
  - Password reset requests

Service Communication Metrics:
  - Feign client response times
  - Circuit breaker states
  - Event publishing success rates
  - Cache hit/miss ratios

Business Metrics:
  - Active users per tenant
  - Role assignment frequency
  - Security event patterns
  - Token usage patterns
```

### **2. Distributed Tracing (OpenTelemetry)**

```yaml
Tracing Stack:
  - OpenTelemetry SDK
  - Jaeger for trace collection
  - Grafana Tempo for trace storage
  - Grafana for visualization

Trace Context:
  - Correlation ID propagation
  - Span context across services
  - Custom span attributes
  - Error tracking with stack traces
```

### **3. Structured Logging**

```yaml
Log Levels:
  - ERROR: System failures, security breaches
  - WARN: Performance issues, degraded service
  - INFO: Business events, user actions
  - DEBUG: Detailed execution flow

Log Format:
  - JSON structured logs
  - Correlation ID in every log
  - Tenant ID for multi-tenancy
  - User ID for audit trail
```

### **4. Intelligent Alerting**

```yaml
Critical Alerts:
  - High authentication failure rates (>10%)
  - Account lockout spikes (>50 in 5 minutes)
  - Token validation failures (>5% in 1 minute)
  - Circuit breaker open state

Warning Alerts:
  - Slow response times (p95 > 2 seconds)
  - Cache miss rate increase (>20%)
  - Event publishing delays (>30 seconds)
  - Database connection pool exhaustion
```

### **5. Health Checks & SLA Monitoring**

```yaml
Health Endpoints:
  - /health: Basic service health
  - /health/readiness: Ready to serve traffic
  - /health/liveness: Service is alive
  - /health/dependencies: External service status

SLA Targets:
  - Authentication: 99.9% availability
  - Token Validation: <100ms p95 latency
  - Event Publishing: <1s end-to-end
  - Cache Hit Rate: >90
```

---

## 🧪 TESTING PATTERNS

### **1. Test Pyramid (Comprehensive Coverage)**

```yaml
Unit Tests (70%):
  - Authentication logic
  - JWT token generation/validation
  - Event creation and publishing
  - Security policies and validations
  - Cache operations
  - Business logic validation

Integration Tests (20%):
  - Feign client communication
  - OutboxEvent publishing
  - Database transactions
  - Kafka event flow
  - Redis cache operations
  - Circuit breaker behavior

E2E Tests (10%):
  - Complete authentication flow
  - Multi-service security scenarios
  - Token refresh flow
  - Account lockout scenarios
  - Performance under load
```

### **2. Contract Testing (Consumer-Driven)**

```yaml
Contract Testing Stack:
  - Pact for consumer-driven contracts
  - Spring Cloud Contract for provider contracts
  - Contract versioning and compatibility

Contract Coverage:
  - User-Service API contracts
  - Contact-Service API contracts
  - Company-Service API contracts
  - Event schema contracts
  - Kafka message contracts
```

### **3. Performance Testing**

```yaml
Load Testing:
  - k6 for load testing
  - JMeter for complex scenarios
  - Gatling for high-performance testing

Performance Targets:
  - Authentication: <100ms p95
  - Token Validation: <50ms p95
  - Event Publishing: <500ms p95
  - Cache Operations: <10ms p95

Stress Testing:
  - Circuit breaker thresholds
  - Database connection limits
  - Memory usage under load
  - Event processing capacity
```

### **4. Security Testing**

```yaml
Security Test Types:
  - OWASP ZAP for vulnerability scanning
  - JWT token manipulation tests
  - SQL injection prevention
  - XSS protection validation
  - Rate limiting verification

Penetration Testing:
  - Authentication bypass attempts
  - Token forgery attempts
  - Privilege escalation tests
  - Data leakage prevention
```

### **5. Chaos Engineering**

```yaml
Chaos Experiments:
  - Service failure simulation
  - Network latency injection
  - Database connection failures
  - Cache service outages
  - Event processing delays

Resilience Validation:
  - Circuit breaker activation
  - Fallback mechanism testing
  - Graceful degradation
  - Recovery time measurement
```

---

## 📋 IMPLEMENTATION CHECKLIST

### **Phase 1: Core Authentication** ✅

- [x] User registration/login
- [x] JWT token management
- [x] Password policies
- [x] Account locking
- [x] Redis token blacklist
- [x] Token revocation

### **Phase 2: Service Integration** 🔄

- [x] User-Service communication (minimal sync)
- [x] Contact-Service communication (event-driven)
- [x] Company-Service communication (cached)
- [x] API-Gateway integration
- [x] Circuit breaker implementation
- [x] Graceful degradation

### **Phase 3: Event-Driven Architecture** 🔄

- [x] Consolidated SecurityEvent pattern
- [x] OutboxEvent pattern implementation
- [x] Event-driven service updates
- [x] Notification-Service integration
- [x] Audit trail with retention policy
- [x] PII protection in events

### **Phase 4: Advanced Features** ⏳

- [ ] Multi-factor authentication (MFA)
- [ ] OAuth2 integration
- [ ] Advanced role management
- [ ] Security analytics dashboard
- [ ] Automated threat detection
- [ ] Compliance reporting

### **Phase 5: Production Optimization** ⏳

- [ ] Debezium CDC migration
- [ ] Advanced caching strategies
- [ ] Performance tuning
- [ ] Chaos engineering implementation
- [ ] Contract testing automation
- [ ] Security penetration testing

---

## 🎯 SUCCESS CRITERIA

✅ **Anti-SPOF Architecture** - Services operate independently  
✅ **Event-Driven First** - Minimal sync calls, maximum async communication  
✅ **Consolidated Events** - Single SecurityEvent pattern  
✅ **Token Revocation** - Redis blacklist with TTL  
✅ **Circuit Breaker Protection** - Graceful degradation on all calls  
✅ **Comprehensive Security** - PII protection, audit trail, retention policy  
✅ **Production-Ready Reliability** - 99.9% availability target  
✅ **Observability** - Distributed tracing, structured logging, intelligent alerting  
✅ **Contract Testing** - Consumer-driven contracts with Pact  
✅ **Performance** - <100ms p95 authentication latency  
✅ **Compliance** - GDPR, SOX, PCI-DSS ready

---

## 🚀 FUTURE EVOLUTION

### **Short Term (3-6 months)**

- Debezium CDC migration for outbox pattern
- Advanced caching with version-based invalidation
- Contract testing automation
- Security analytics dashboard

### **Medium Term (6-12 months)**

- Multi-factor authentication (MFA)
- OAuth2 integration
- Advanced threat detection
- Compliance reporting automation

### **Long Term (12+ months)**

- AI-powered security analytics
- Zero-trust architecture
- Advanced fraud detection
- Automated compliance monitoring

---

**Last Updated:** 2025-10-22  
**Maintained By:** Fabric Management Team  
**Version:** 2.0.0 (Production-Ready, Anti-SPOF)

---

_"Security is not a product, but a process. Auth-service is the guardian of that process."_

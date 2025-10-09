# 🔐 Security Documentation

> **Last Updated:** October 2025  
> **Security Level:** Production-Ready

## 📋 Table of Contents

- [Overview](#overview)
- [Authentication Flow](#authentication-flow)
- [Security Features](#security-features)
- [Configuration](#configuration)
- [Best Practices](#best-practices)
- [Security Checklist](#security-checklist)

---

## 🎯 Overview

Fabric Management System implements enterprise-grade security features including:

- ✅ JWT-based authentication
- ✅ API Gateway security with route-level protection
- ✅ Rate limiting (endpoint-specific)
- ✅ Brute force protection (Redis-based)
- ✅ Response time masking (timing attack prevention)
- ✅ Custom exception handling
- ✅ Security audit logging
- ✅ Contact verification requirements

---

## 🔄 Authentication Flow

### 1. Check Contact

```
POST /api/v1/users/auth/check-contact
```

**Purpose:** Determines if user exists and has password set

**Request:**

```json
{
  "contactValue": "user@example.com"
}
```

**Response:**

```json
{
  "exists": true,
  "hasPassword": true,
  "userId": "uuid",
  "message": "Please enter your password"
}
```

**Security Features:**

- ✅ Response time masking (200ms minimum)
- ✅ Rate limiting: 10 req/min
- ✅ Generic error messages (no user enumeration)

---

### 2. Setup Password (First Time)

```
POST /api/v1/users/auth/setup-password
```

**Purpose:** Create password for new user

**Request:**

```json
{
  "contactValue": "user@example.com",
  "password": "SecurePass123!@#"
}
```

**Validations:**

- ✅ Contact must exist
- ✅ Contact must be verified
- ✅ User status: PENDING_VERIFICATION or ACTIVE
- ✅ Password not already set
- ✅ Password complexity requirements

**Password Requirements:**

- Minimum 8 characters
- At least 1 uppercase letter
- At least 1 lowercase letter
- At least 1 number
- At least 1 special character (@$!%\*?&)

**Rate Limiting:** 3 req/min (very strict)

---

### 3. Login

```
POST /api/v1/users/auth/login
```

**Purpose:** Authenticate user and generate JWT tokens

**Request:**

```json
{
  "contactValue": "user@example.com",
  "password": "SecurePass123!@#"
}
```

**Response:**

```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "userId": "uuid",
  "tenantId": "tenant-uuid",
  "firstName": "John",
  "lastName": "Doe",
  "email": "user@example.com",
  "role": "USER"
}
```

**Security Features:**

- ✅ Account lockout (5 attempts → 15 min lockout)
- ✅ Redis-based attempt tracking
- ✅ Generic error messages ("Invalid credentials")
- ✅ Security audit logging
- ✅ Rate limiting: 5 req/min

---

## 🛡️ Security Features

### 1. API Gateway Security

**File:** `services/api-gateway/src/main/java/com/fabricmanagement/gateway/config/SecurityConfig.java`

**Public Endpoints:**

```java
/api/v1/users/auth/**           // Authentication
/api/v1/contacts/find-by-value  // Internal contact lookup
/actuator/health                // Health checks
/actuator/info
/actuator/prometheus
```

**Protected Endpoints:**
All other endpoints require valid JWT token in `Authorization: Bearer <token>` header.

**JWT Validation:**

- Signature verification (HS256)
- Expiration check
- Tenant ID and User ID extraction
- Headers added to downstream requests:
  - `X-Tenant-Id`
  - `X-User-Id`

---

### 2. Rate Limiting

**Configuration:** `services/api-gateway/src/main/resources/application.yml`

| Endpoint                  | Rate Limit | Burst Capacity | Purpose                      |
| ------------------------- | ---------- | -------------- | ---------------------------- |
| `/auth/check-contact`     | 10/min     | 15             | Prevent enumeration          |
| `/auth/login`             | 5/min      | 10             | Brute force prevention       |
| `/auth/setup-password`    | 3/min      | 5              | One-time operation           |
| `/contacts/find-by-value` | 5/min      | 10             | Internal endpoint protection |
| Other auth endpoints      | 20/min     | 30             | General protection           |

**Implementation:** Redis-based using Spring Cloud Gateway RequestRateLimiter

---

### 3. Login Attempt Tracking

**Service:** `LoginAttemptService.java`

**Configuration:**

```yaml
security:
  login-attempt:
    max-attempts: 5 # Configurable per environment
    lockout-duration-minutes: 15 # Account lockout duration
```

**Redis Keys:**

```
login:attempts:{contactValue}  → attempt counter (TTL: 15 min)
login:lockout:{contactValue}   → lockout timestamp (TTL: 15 min)
```

**Flow:**

1. Failed login → Increment counter
2. 5 failed attempts → Account locked for 15 minutes
3. Successful login → Counter cleared
4. After 15 minutes → Auto-unlock

---

### 4. Response Time Masking

**Purpose:** Prevent timing attacks and user enumeration

**Configuration:**

```yaml
security:
  response-time-masking:
    min-response-time-ms: 200 # Minimum response time
```

**Implementation:**

- All `/check-contact` responses take minimum 200ms
- Prevents attackers from determining user existence by response time

**Example:**

```java
// Contact exists: actual 50ms → padded to 200ms
// Contact doesn't exist: actual 10ms → padded to 200ms
// Attacker cannot distinguish between cases
```

---

### 5. Custom Exception Handling

**File:** `services/user-service/src/main/java/com/fabricmanagement/user/api/GlobalExceptionHandler.java`

**Exception Hierarchy:**

```
DomainException (base)
├── ContactNotFoundException
├── UserNotFoundException
├── InvalidPasswordException
├── AccountLockedException
├── ContactNotVerifiedException
├── InvalidUserStatusException
└── PasswordAlreadySetException
```

**Benefits:**

- Consistent error responses
- Proper HTTP status codes
- Error code standardization
- Security-conscious messages (no sensitive data leakage)

---

### 6. Security Audit Logging

**Service:** `SecurityAuditLogger.java`

**Logged Events:**

```
[SECURITY_AUDIT] event=LOGIN_SUCCESS contactValue=use*** userId=uuid tenantId=tid timestamp=...
[SECURITY_AUDIT] event=LOGIN_FAILED contactValue=use*** reason=Invalid password timestamp=...
[SECURITY_AUDIT] event=ACCOUNT_LOCKED contactValue=use*** attempts=5 lockoutDuration=15 timestamp=...
[SECURITY_AUDIT] event=PASSWORD_SETUP contactValue=use*** userId=uuid timestamp=...
```

**Features:**

- Structured logging (SIEM-ready)
- Contact value masking (privacy)
- Timestamp tracking
- Detailed reason codes

**Integration:**

- Logs written to standard output
- Can be ingested by ELK, Splunk, etc.
- Monitoring/alerting ready

---

## ⚙️ Configuration

### Environment Variables

**File:** `.env` or `application.yml`

```bash
# JWT Configuration
JWT_SECRET=your-secret-key-base64-encoded
JWT_EXPIRATION=3600000           # 1 hour (production: 15 min recommended)
JWT_REFRESH_EXPIRATION=86400000  # 24 hours

# Security Configuration
SECURITY_LOGIN_ATTEMPT_MAX_ATTEMPTS=5
SECURITY_LOGIN_ATTEMPT_LOCKOUT_DURATION_MINUTES=15
SECURITY_RESPONSE_TIME_MASKING_MIN_MS=200

# Redis (for rate limiting & attempt tracking)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
```

### Application Configuration

**File:** `services/user-service/src/main/resources/application.yml`

```yaml
security:
  login-attempt:
    max-attempts: 5
    lockout-duration-minutes: 15
  response-time-masking:
    min-response-time-ms: 200

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:3600000}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:86400000}
  algorithm: HS256
  issuer: fabric-management-system
  audience: fabric-api
```

---

## 🎯 Best Practices

### 1. Password Management

✅ **DO:**

- Enforce strong password requirements
- Use BCrypt for password hashing (strength: 10)
- Never log or display passwords
- Implement password expiration policies (future)
- Support password reset via verified contacts

❌ **DON'T:**

- Store plain text passwords
- Send passwords via email
- Use weak hashing algorithms (MD5, SHA1)
- Allow common passwords ("password123")

---

### 2. JWT Token Management

✅ **DO:**

- Use short-lived access tokens (15 min - 1 hour)
- Implement refresh tokens (7 days max)
- Store tokens securely (HttpOnly cookies for web)
- Validate tokens on every request
- Include minimal claims (avoid sensitive data)

❌ **DON'T:**

- Store tokens in localStorage (XSS risk)
- Use predictable token secrets
- Include sensitive data in JWT claims
- Allow tokens without expiration
- Use same secret across environments

---

### 3. API Security

✅ **DO:**

- Implement rate limiting on all public endpoints
- Use HTTPS in production (TLS 1.2+)
- Validate all input data
- Sanitize error messages (no stack traces to client)
- Log security events

❌ **DON'T:**

- Expose internal error details
- Trust client-side validation alone
- Allow unlimited API calls
- Return different errors for enumeration attacks
- Disable CORS in production

---

### 4. Monitoring & Alerting

✅ **DO:**

- Monitor failed login attempts
- Alert on account lockouts
- Track API rate limit hits
- Log security events to SIEM
- Review audit logs regularly

❌ **DON'T:**

- Ignore security logs
- Skip alerting configuration
- Wait for incidents to check logs
- Store logs indefinitely without rotation

---

## ✅ Security Checklist

### Development

- [ ] All endpoints have proper authentication
- [ ] Input validation on all endpoints
- [ ] Custom exceptions for error handling
- [ ] No hardcoded credentials
- [ ] Secrets in environment variables
- [ ] Security tests written

### Deployment

- [ ] HTTPS enabled (TLS 1.2+)
- [ ] JWT secret changed from default
- [ ] Redis password configured
- [ ] Rate limiting enabled
- [ ] CORS properly configured
- [ ] Audit logging enabled

### Production

- [ ] Security monitoring active
- [ ] Alerts configured
- [ ] Regular security audits scheduled
- [ ] Incident response plan ready
- [ ] Backup/recovery tested
- [ ] Compliance requirements met

---

## 🚨 Security Incident Response

### 1. Suspected Brute Force Attack

```bash
# Check Redis for locked accounts
redis-cli KEYS "login:lockout:*"

# Check failed attempt counts
redis-cli GET "login:attempts:user@example.com"

# Manual unlock (if needed)
redis-cli DEL "login:lockout:user@example.com"
redis-cli DEL "login:attempts:user@example.com"
```

### 2. Suspicious Login Activity

```bash
# Search audit logs
grep "LOGIN_FAILED" logs/user-service.log | tail -100

# Check for account lockouts
grep "ACCOUNT_LOCKED" logs/user-service.log

# Review by IP (if tracked)
grep "LOGIN" logs/api-gateway.log | grep "suspicious-ip"
```

### 3. JWT Token Compromise

```bash
# Immediate actions:
1. Rotate JWT secret in all environments
2. Invalidate all existing tokens
3. Force user re-authentication
4. Review audit logs for suspicious activity
5. Notify affected users
```

---

## 📊 Security Metrics

### Key Metrics to Monitor

1. **Failed Login Rate**

   - Threshold: > 10% of total logins
   - Action: Investigate potential attack

2. **Account Lockout Rate**

   - Threshold: > 5 accounts/hour
   - Action: Check for coordinated attack

3. **API Rate Limit Hits**

   - Threshold: > 100 hits/hour
   - Action: Review source IPs, consider blocking

4. **JWT Validation Failures**
   - Threshold: > 1% of requests
   - Action: Check for token manipulation attempts

---

## 🛡️ Defense in Depth: UUID Validation

### Why UUID Validation Matters

**Security Risk:** JWT tokens contain `tenantId` and `userId` as strings. Without validation:

- ❌ Malformed UUIDs can reach downstream services
- ❌ DOS attacks via exception flooding
- ❌ Log pollution and information leakage
- ❌ Potential SQL injection vectors (if mishandled)

### Multi-Layer UUID Protection

#### Layer 1: API Gateway (First Line of Defense) ✅

```java
// api-gateway/.../JwtAuthenticationFilter.java
String tenantId = claims.get("tenantId", String.class);
String userId = claims.getSubject();

// SECURITY: Validate UUID format BEFORE downstream propagation
if (!isValidUuid(tenantId) || !isValidUuid(userId)) {
    log.error("Invalid UUID format in JWT");
    return unauthorizedResponse(exchange); // 401 - STOP HERE!
}
```

**Benefits:**

- 🛡️ Blocks malformed data at entry point
- 🚀 Reduces load on downstream services
- 📊 Clear audit trail at gateway level
- ⚡ Fast fail without service invocation

#### Layer 2: Shared-Security Filter (Second Line) ✅

```java
// shared-security/.../JwtAuthenticationFilter.java
String userId = jwtTokenProvider.extractUserId(token);
String tenantId = jwtTokenProvider.extractTenantId(token);

// SECURITY: Validate before setting authentication context
if (!isValidUuid(userId) || !isValidUuid(tenantId)) {
    log.error("Invalid UUID format, skipping authentication");
    filterChain.doFilter(request, response); // Continue without auth
    return;
}
```

**Benefits:**

- 🛡️ Protection for direct service calls (bypassing gateway)
- 🔐 Prevents malformed UUIDs in security context
- ✅ Consistent validation across all services

#### Layer 3: SecurityContextHolder (Third Line) ✅

```java
// shared-infrastructure/.../SecurityContextHolder.java
public static UUID getCurrentTenantId() {
    try {
        return UUID.fromString((String) tenantIdClaim);
    } catch (IllegalArgumentException e) {
        log.error("Invalid tenant ID format: {}", tenantIdClaim);
        throw new UnauthorizedException("Invalid tenant ID in token");
    }
}
```

**Benefits:**

- 🛡️ Final safety net before database operations
- 📝 Structured exception with clear error message
- 🔒 Type-safe UUID conversion

### UUID Validation Best Practices

1. **Validate Early**: Check at API Gateway before downstream propagation
2. **Fail Fast**: Return 401/403 immediately on invalid format
3. **Log Clearly**: Security logs should capture validation failures
4. **No Exceptions in Prod**: Validation should prevent exceptions, not rely on them
5. **Consistent Format**: Always use standard UUID format (RFC 4122)

### Why String in JWT?

**Question:** "Is using String for tenantId/userId normal? Does it create security risk?"

**Answer:**

- ✅ **Normal and Required**: JWT specification requires JSON-compatible types → UUID must be string
- ✅ **Not Inherently Risky**: Risk comes from lack of validation, not string type
- ✅ **Proper Flow**: `UUID → String (JWT) → Validate → UUID (app)`
- ❌ **Risky Without Validation**: Malformed strings can cause downstream issues

**Key Principle:**

> "Trust, but verify. Accept strings from JWT, but validate UUID format before use."

---

## 🔗 Related Documentation

- [User Service Documentation](services/user-service.md)
- [API Gateway Setup](deployment/API_GATEWAY_SETUP.md)
- [Development Principles](development/PRINCIPLES.md)
- [Environment Management](deployment/ENVIRONMENT_MANAGEMENT_BEST_PRACTICES.md)

---

---

**Last Updated:** 2025-10-09 20:15 UTC+1  
**Version:** 1.0.0  
**Status:** ✅ Production Ready  
**Security Contact:** security@fabricmanagement.com  
**Last Security Audit:** October 2025  
**Next Scheduled Audit:** January 2026

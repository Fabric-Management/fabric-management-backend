# 🔐 Environment Variables Guide

**Purpose:** Centralized configuration through `.env` file  
**Priority:** NO HARDCODED VALUES - Single source of truth  
**Last Updated:** 2025-10-13 (v3.1.0 - API Gateway Dynamic Configuration)

---

## 🎯 Quick Start

```bash
# Create .env from template
make setup

# Edit .env with your values
nano .env

# Deploy
make deploy
```

---

## 📋 Environment Variable Pattern

### ✅ CORRECT Pattern (NO Hardcoded Values)

```yaml
# docker-compose.yml
USER_SERVICE_URL: ${USER_SERVICE_URL:-http://user-service:8081}
#                  ^^^^^^^^^^^^^^^^^ from .env
#                                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^ default for Docker
```

**How it works:**

1. **Docker:** If not set in .env → Uses default (`http://user-service:8081`)
2. **Local:** If set in .env → Uses that value (`http://localhost:8081`)

---

## 🐳 Docker vs Local Development

### Docker Environment (Production-like)

**Network:** Internal Docker bridge network  
**Service Discovery:** DNS-based (service names)

```yaml
# Services communicate via service names
USER_SERVICE_URL=http://user-service:8081       # ✅ Docker internal
COMPANY_SERVICE_URL=http://company-service:8083 # ✅ Docker internal
CONTACT_SERVICE_URL=http://contact-service:8082 # ✅ Docker internal
```

**When to use:** Docker Compose deployment

### Local Development

**Network:** Host machine (localhost)  
**Service Discovery:** Port-based

```bash
# .env for LOCAL development (comment out for Docker!)
# USER_SERVICE_URL=http://localhost:8081
# COMPANY_SERVICE_URL=http://localhost:8083
# CONTACT_SERVICE_URL=http://localhost:8082
```

**When to use:** Running services directly (IntelliJ, mvn spring-boot:run)

---

## ⚠️ IMPORTANT: Docker Deployment

**When running with Docker Compose, COMMENT OUT service URLs in .env:**

```bash
# .env file for Docker deployment

# ❌ DON'T SET THESE (let docker-compose use defaults)
# USER_SERVICE_URL=http://localhost:8081
# COMPANY_SERVICE_URL=http://localhost:8083
# CONTACT_SERVICE_URL=http://localhost:8082

# ✅ DO SET THESE
POSTGRES_PASSWORD=your_secure_password
REDIS_PASSWORD=your_redis_password
JWT_SECRET=your_jwt_secret
```

**Why?** Docker needs service names (`user-service`), not `localhost`

---

## 📊 Variable Categories

### 1. Database (PostgreSQL)

```bash
POSTGRES_HOST=localhost              # Docker: postgres
POSTGRES_PORT=5433                   # External port
POSTGRES_DB=fabric_management
POSTGRES_USER=fabric_user
POSTGRES_PASSWORD=change_me          # ⚠️ REQUIRED

# Connection Pool
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
DB_CONNECTION_TIMEOUT=30000
```

### 2. Cache (Redis)

```bash
REDIS_HOST=localhost                 # Docker: redis
REDIS_PORT=6379
REDIS_PASSWORD=change_me             # ⚠️ REQUIRED for Docker

# Connection Pool
REDIS_POOL_MAX_ACTIVE=8
REDIS_POOL_MAX_IDLE=8
REDIS_POOL_MIN_IDLE=2
```

### 3. Message Broker (Kafka)

```bash
KAFKA_PORT=9092
KAFKA_BOOTSTRAP_SERVERS=localhost:9092    # Docker: kafka:9093
```

### 4. Security (JWT)

```bash
JWT_SECRET=your_secret_here          # ⚠️ REQUIRED - Generate secure value
JWT_EXPIRATION=3600000               # 1 hour
JWT_REFRESH_EXPIRATION=86400000      # 24 hours
JWT_ALGORITHM=HS256
JWT_ISSUER=fabric-management-system
JWT_AUDIENCE=fabric-api
```

### 5. Microservices (Ports)

```bash
API_GATEWAY_PORT=8080
USER_SERVICE_PORT=8081
CONTACT_SERVICE_PORT=8082
COMPANY_SERVICE_PORT=8083
```

### 6. Monitoring (JMX)

```bash
USER_SERVICE_JMX_PORT=9011
CONTACT_SERVICE_JMX_PORT=9012
COMPANY_SERVICE_JMX_PORT=9013
```

### 7. Frontend Integration (CORS)

```bash
# Comma-separated frontend URLs
# Local development
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:4200

# Production (override in .env)
# CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
```

**Note:** Multiple origins separated by comma, no spaces!

---

## 🔒 Security Best Practices

### 1. Strong Passwords

```bash
# ❌ WEAK
POSTGRES_PASSWORD=password123

# ✅ STRONG (use password generator)
POSTGRES_PASSWORD=kJ8$mP2#nQ9@vL5!wR7
```

### 2. JWT Secret Generation

```bash
# Generate secure 256-bit secret (64 hex characters)
openssl rand -base64 64

# Or use online generator (save in .env)
JWT_SECRET=EgUbMz7OOW7+57Ehxr2jUc7mFlEQPrs/XZirqOjhI4O...
```

### 3. Never Commit `.env`

```bash
# Already in .gitignore
.env
.env.local
.env.*.local
```

---

## 🎯 Default Values in Docker Compose

```yaml
# All service URLs have Docker-friendly defaults
USER_SERVICE_URL: ${USER_SERVICE_URL:-http://user-service:8081}
COMPANY_SERVICE_URL: ${COMPANY_SERVICE_URL:-http://company-service:8083}
CONTACT_SERVICE_URL: ${CONTACT_SERVICE_URL:-http://contact-service:8082}

# Hosts
USER_SERVICE_HOST: ${USER_SERVICE_HOST:-user-service}
COMPANY_SERVICE_HOST: ${COMPANY_SERVICE_HOST:-company-service}
CONTACT_SERVICE_HOST: ${CONTACT_SERVICE_HOST:-contact-service}
```

**Benefit:** Works out-of-box with Docker, customizable for local dev

---

## 📝 Complete .env Template

```bash
# =============================================================================
# FABRIC MANAGEMENT SYSTEM - ENVIRONMENT CONFIGURATION
# =============================================================================

# =============================================================================
# DATABASE - PostgreSQL
# =============================================================================
POSTGRES_HOST=localhost
POSTGRES_PORT=5433
POSTGRES_DB=fabric_management
POSTGRES_USER=fabric_user
POSTGRES_PASSWORD=change_me_use_strong_password

DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
DB_CONNECTION_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
DB_MAX_LIFETIME=1800000

# =============================================================================
# CACHE - Redis
# =============================================================================
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=change_me_strong_password

REDIS_POOL_MAX_ACTIVE=8
REDIS_POOL_MAX_IDLE=8
REDIS_POOL_MIN_IDLE=2
REDIS_CONNECTION_TIMEOUT=2000
REDIS_COMMAND_TIMEOUT=3000
CACHE_TTL=300000

# =============================================================================
# MESSAGE BROKER - Kafka
# =============================================================================
KAFKA_PORT=9092
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# =============================================================================
# API GATEWAY & MICROSERVICES
# =============================================================================
API_GATEWAY_PORT=8080
USER_SERVICE_PORT=8081
CONTACT_SERVICE_PORT=8082
COMPANY_SERVICE_PORT=8083

# Service URLs (COMMENT OUT for Docker deployment!)
# USER_SERVICE_URL=http://localhost:8081
# COMPANY_SERVICE_URL=http://localhost:8083
# CONTACT_SERVICE_URL=http://localhost:8082

# =============================================================================
# API GATEWAY - PERFORMANCE TUNING (v3.1.0) 🆕
# =============================================================================
# See: docs/deployment/PERFORMANCE_TUNING_GUIDE.md

# Service Timeouts (Formula: P95 × 1.5 to 2.0)
USER_SERVICE_TIMEOUT=15s          # Onboarding heavy (estimated P95: 7s)
COMPANY_SERVICE_TIMEOUT=10s       # Database operations (estimated P95: 5s)
CONTACT_SERVICE_TIMEOUT=7s        # Simple CRUD (estimated P95: 3s)
GATEWAY_DEFAULT_TIMEOUT=15s       # Fallback timeout

# Rate Limiting - Public Endpoints (Anti-abuse)
GATEWAY_RATE_LOGIN_REPLENISH=5
GATEWAY_RATE_LOGIN_BURST=10
GATEWAY_RATE_ONBOARDING_REPLENISH=5
GATEWAY_RATE_ONBOARDING_BURST=10
GATEWAY_RATE_SETUP_PASSWORD_REPLENISH=3
GATEWAY_RATE_SETUP_PASSWORD_BURST=5

# Rate Limiting - Protected Endpoints
GATEWAY_RATE_PROTECTED_REPLENISH=50
GATEWAY_RATE_PROTECTED_BURST=100

# Circuit Breaker (Don't be too aggressive!)
GATEWAY_CB_FAILURE_THRESHOLD=50       # 50% failure rate opens circuit
GATEWAY_CB_SLOW_CALL_DURATION=8s      # P95 × 1.2 (above normal slow)
GATEWAY_CB_WAIT_DURATION=30s          # Circuit open duration

# Retry Configuration
GATEWAY_RETRY_PUBLIC_INITIAL=100ms    # Public routes (anti-abuse)
GATEWAY_RETRY_PROTECTED_INITIAL=50ms  # Protected routes (faster)
GATEWAY_RETRY_MAX_BACKOFF=500ms       # Keep under 1s for UX

# =============================================================================
# SECURITY - JWT
# =============================================================================
JWT_SECRET=generate_your_own_secure_secret_here
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=86400000
JWT_ALGORITHM=HS256
JWT_ISSUER=fabric-management-system
JWT_AUDIENCE=fabric-api

# =============================================================================
# MONITORING - JMX
# =============================================================================
USER_SERVICE_JMX_PORT=9011
CONTACT_SERVICE_JMX_PORT=9012
COMPANY_SERVICE_JMX_PORT=9013

# =============================================================================
# LOGGING
# =============================================================================
LOG_LEVEL=INFO
```

---

## 🔍 Troubleshooting

### Problem: "Connection refused" in Docker

**Symptom:**

```
Connection refused executing POST http://localhost:8083/api/v1/companies
```

**Cause:** Service URLs set to `localhost` in `.env`

**Fix:**

```bash
# Comment out service URLs in .env
# USER_SERVICE_URL=http://localhost:8081  # ← Comment this
```

### Problem: Services can't find each other

**Symptom:**

```
Unknown host: user-service
```

**Cause:** Wrong Docker network or service name

**Fix:**

```bash
# Check services are in same network
docker compose ps

# Verify network
docker network inspect fabric-network
```

---

## 📚 Related Documentation

- [Docker Compose](../../docker-compose.yml) - Service definitions
- [Makefile](../../Makefile) - Quick commands
- [README](../../README.md) - Main documentation
- **[Performance Tuning Guide](PERFORMANCE_TUNING_GUIDE.md)** 🆕 - How to tune timeouts based on metrics

---

## 🆕 v3.1.0 Updates (Oct 13, 2025)

### New Performance Tuning Variables

API Gateway now supports dynamic configuration for:

- ✅ Service-specific timeouts (based on P95 metrics)
- ✅ Rate limiting per endpoint type
- ✅ Circuit breaker thresholds
- ✅ Retry backoff strategies

**See:** `docs/deployment/PERFORMANCE_TUNING_GUIDE.md` for measurement and tuning strategies.

**Quick Example:**

```bash
# Measure P95 in production
P95_ONBOARDING=8s

# Calculate timeout (P95 × 1.5)
TIMEOUT = 8s × 1.5 = 12s

# Set environment variable
USER_SERVICE_TIMEOUT=12s
```

---

**Document Owner:** DevOps Team  
**Review Frequency:** Monthly  
**Next Review:** 2025-11-13

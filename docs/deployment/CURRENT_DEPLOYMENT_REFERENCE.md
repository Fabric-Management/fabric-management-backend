# 📚 CURRENT DEPLOYMENT REFERENCE GUIDE

## 📅 Last Updated: 02 October 2025

## 🎯 Purpose: Complete reference for current deployment architecture

---

## 🏗️ CURRENT ARCHITECTURE

### System Components

```
┌─────────────────────────────────────────────────────┐
│                   FABRIC MANAGEMENT                  │
├───────────────┬────────────┬───────────┬────────────┤
│ User Service  │ Contact    │ Company   │            │
│ (Port: 8081)  │ Service    │ Service   │ PostgreSQL │
│               │ (Port:8082)│(Port:8083)│ (Port:5433)│
├───────────────┴────────────┴───────────┴────────────┤
│                  Shared Modules                      │
│  (Domain, Application, Infrastructure, Security)     │
├───────────────────────────────────────────────────────┤
│              Infrastructure Services                  │
│    Redis(6379) | Kafka(9092) | Zookeeper(2181)      │
└───────────────────────────────────────────────────────┘
```

---

## 📁 PROJECT STRUCTURE

### Current File Organization

```
fabric-management-backend/
├── .env                          # Active environment variables
├── .env.example                  # Template with all variables
├── .env.backup                   # Backup of previous config
├── Dockerfile.service            # Universal parametric Dockerfile
├── docker-compose.yml            # Infrastructure only
├── docker-compose-complete.yml   # Full stack deployment
├── pom.xml                       # Parent POM
├── scripts/
│   ├── docker-entrypoint.sh     # Optimized entrypoint
│   ├── init-db.sql              # Database initialization
│   ├── deploy.sh                # Deployment script
│   └── run-migrations.sh        # Flyway migrations
├── services/
│   ├── user-service/
│   │   ├── pom.xml
│   │   ├── Dockerfile.old       # TO BE REMOVED
│   │   └── src/main/resources/
│   │       ├── application.yml
│   │       ├── application-docker.yml
│   │       └── db/migration/
│   │           └── V1__create_user_tables.sql
│   ├── contact-service/
│   │   └── (same structure)
│   └── company-service/
│       └── (same structure)
└── shared/
    ├── shared-domain/
    ├── shared-application/
    ├── shared-infrastructure/
    └── shared-security/
```

---

## 🔧 ENVIRONMENT VARIABLES

### Complete Variable List (.env)

#### 1. Environment Settings

```bash
SPRING_PROFILES_ACTIVE=local       # local|docker|production
```

#### 2. Database Configuration

```bash
POSTGRES_HOST=localhost            # postgres (for Docker)
POSTGRES_PORT=5433                 # 5432 internal
POSTGRES_DB=fabric_management      # Database name
POSTGRES_USER=fabric_user          # Database user
POSTGRES_PASSWORD=***              # Strong password required
```

#### 3. Cache Configuration

```bash
REDIS_HOST=localhost               # redis (for Docker)
REDIS_PORT=6379                    # Standard Redis port
REDIS_PASSWORD=***                 # Optional but recommended
```

#### 4. Message Broker

```bash
ZOOKEEPER_PORT=2181                # Zookeeper port
KAFKA_HOST=localhost               # kafka (for Docker)
KAFKA_PORT=9092                    # External port
KAFKA_ADVERTISED_HOST=localhost    # Public hostname
KAFKA_BOOTSTRAP_SERVERS=localhost:9092  # Full connection string
```

#### 5. Microservices

```bash
# Service Ports
USER_SERVICE_PORT=8081
CONTACT_SERVICE_PORT=8082
COMPANY_SERVICE_PORT=8083

# Inter-service Communication
USER_SERVICE_HOST=localhost        # user-service (for Docker)
CONTACT_SERVICE_HOST=localhost     # contact-service (for Docker)
COMPANY_SERVICE_HOST=localhost     # company-service (for Docker)

# Full URLs
USER_SERVICE_URL=http://localhost:8081
CONTACT_SERVICE_URL=http://localhost:8082
COMPANY_SERVICE_URL=http://localhost:8083
```

#### 6. Security

```bash
JWT_SECRET=***                     # 256-bit base64 encoded
JWT_EXPIRATION=86400000           # 24 hours
JWT_REFRESH_EXPIRATION=604800000  # 7 days
```

#### 7. Logging & JVM

```bash
LOG_LEVEL=INFO                    # DEBUG|INFO|WARN|ERROR
# JAVA_OPTS=-Xmx512m -Xms256m     # Optional JVM settings
```

---

## 🐳 DOCKER CONFIGURATION

### 1. Universal Dockerfile (Dockerfile.service)

**Features:**

- Parametric build with SERVICE_NAME and SERVICE_PORT
- Multi-stage build (build + runtime)
- Non-root user (fabricuser:1001)
- Health check included
- Optimized JVM settings

**Build Command:**

```bash
docker build -f Dockerfile.service \
  --build-arg SERVICE_NAME=user-service \
  --build-arg SERVICE_PORT=8081 \
  -t fabric-user-service:latest .
```

### 2. Docker Compose Files

#### docker-compose.yml (Infrastructure Only)

- PostgreSQL 15 Alpine
- Redis 7 Alpine
- Kafka + Zookeeper
- Health checks configured
- No hardcoded values (uses .env)

#### docker-compose-complete.yml (Full Stack)

- All infrastructure services
- All 3 microservices
- Service dependencies defined
- Docker network isolation
- Resource limits applied

### 3. Docker Entrypoint Script

**Features:**

- Dependency waiting (PostgreSQL, Redis, Kafka)
- JVM optimization (50% RAM, G1GC)
- No JMX (security)
- Graceful startup
- Color-coded output

---

## 📝 APPLICATION CONFIGURATION

### Configuration Hierarchy

```
1. Environment Variables (highest priority)
   ↓
2. application-{profile}.yml
   ↓
3. application.yml
   ↓
4. Code defaults (lowest priority)
```

### Key Configuration Principles

#### 1. No Hardcoded Defaults

```yaml
# ❌ OLD (with defaults)
url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5433}

# ✅ NEW (fail-fast)
url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}
```

#### 2. Profile-Specific Settings

- `application.yml` - Base configuration
- `application-docker.yml` - Docker overrides
- `application-production.yml` - Production settings

#### 3. Security Settings

```yaml
flyway:
  clean-disabled: true # Always true in production
  repair-on-migrate: false # Manual repair only

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

---

## 🚀 DEPLOYMENT COMMANDS

### Local Development

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Run services locally
cd services/user-service
mvn spring-boot:run

# 3. Check health
curl http://localhost:8081/actuator/health
```

### Docker Development

```bash
# 1. Build and start everything
docker-compose -f docker-compose-complete.yml up --build -d

# 2. View logs
docker-compose -f docker-compose-complete.yml logs -f

# 3. Stop everything
docker-compose -f docker-compose-complete.yml down
```

### Production Deployment

```bash
# 1. Set production environment
export SPRING_PROFILES_ACTIVE=production

# 2. Build images
docker-compose -f docker-compose-complete.yml build

# 3. Deploy with scaling
docker-compose -f docker-compose-complete.yml up -d --scale user-service=2

# 4. Health check all services
for port in 8081 8082 8083; do
  curl http://localhost:$port/actuator/health
done
```

---

## 🔍 HEALTH CHECKS & MONITORING

### Service Health Endpoints

```
User Service:    http://localhost:8081/actuator/health
Contact Service: http://localhost:8082/actuator/health
Company Service: http://localhost:8083/actuator/health
```

### Metrics Endpoints

```
Prometheus: http://localhost:{port}/actuator/prometheus
Metrics:    http://localhost:{port}/actuator/metrics
```

### Database Health Check

```bash
docker exec fabric-postgres pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}
```

### Redis Health Check

```bash
docker exec fabric-redis redis-cli ping
```

### Kafka Health Check

```bash
docker exec fabric-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
```

---

## 🛠️ MAINTENANCE TASKS

### Database Migrations

```bash
# Run Flyway migrations
cd services/user-service
mvn flyway:migrate

# Check migration status
mvn flyway:info
```

### Clear Cache

```bash
# Clear Redis cache
docker exec fabric-redis redis-cli FLUSHALL
```

### View Logs

```bash
# All services
docker-compose -f docker-compose-complete.yml logs

# Specific service
docker-compose -f docker-compose-complete.yml logs user-service

# Follow logs
docker-compose -f docker-compose-complete.yml logs -f
```

### Backup Database

```bash
# Create backup
docker exec fabric-postgres pg_dump -U ${POSTGRES_USER} ${POSTGRES_DB} > backup.sql

# Restore backup
docker exec -i fabric-postgres psql -U ${POSTGRES_USER} ${POSTGRES_DB} < backup.sql
```

---

## ⚠️ IMPORTANT NOTES

### Security Considerations

1. **Never commit .env to Git** - Use .env.example as template
2. **Change all default passwords** before production
3. **Use strong JWT secret** - Generate with: `openssl rand -base64 64`
4. **Enable HTTPS** in production (use reverse proxy)
5. **Restrict database access** - Use firewall rules

### Performance Optimization

1. **JVM Settings**: 50% RAM allocation (safe for containers)
2. **Connection Pools**: HikariCP with 10 max connections
3. **Redis Timeout**: 2000ms
4. **Kafka Batch Size**: 16KB
5. **Log Level**: INFO in production (not DEBUG)

### Known Issues & Solutions

1. **Port conflicts**: Change ports in .env if needed
2. **Memory issues**: Adjust JAVA_OPTS in .env
3. **Slow startup**: Check dependency health checks
4. **Network issues**: Ensure Docker network is created

---

## 📊 APPLIED PRINCIPLES

### ✅ KISS (Keep It Simple)

- Single .env file for all config
- One parametric Dockerfile
- Clear separation of concerns

### ✅ DRY (Don't Repeat Yourself)

- No hardcoded values
- Shared configurations
- Reusable components

### ✅ YAGNI (You Aren't Gonna Need It)

- No monitoring stack (removed)
- No JMX (security risk)
- Minimal dependencies

### ✅ Fail-Fast

- No default values in configs
- Explicit environment variables
- Early error detection

### ✅ 12-Factor App

- Config in environment
- Stateless services
- Port binding
- Disposability

---

## 🔄 VERSION HISTORY

| Date       | Version | Changes                                  |
| ---------- | ------- | ---------------------------------------- |
| 02.10.2025 | 2.0     | Complete refactoring, removed monitoring |
| 02.10.2025 | 1.9     | Hardcoded values removed                 |
| 02.10.2025 | 1.8     | .env structure improved                  |
| 01.10.2025 | 1.0     | Initial deployment setup                 |

---

## 📚 RELATED DOCUMENTATION

- [Environment Management Best Practices](./ENVIRONMENT_MANAGEMENT_BEST_PRACTICES.md)
- [Deployment Guide](./DEPLOYMENT_GUIDE.md)
- [Development Principles](../development/PRINCIPLES.md)
- [Project Structure](../PROJECT_STRUCTURE.md)

---

**Last Updated:** 2025-10-09 20:15 UTC+1  
**Version:** 2.0  
**Status:** ✅ Current & Accurate  
**Maintained By:** Development Team

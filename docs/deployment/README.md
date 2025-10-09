# 📚 Deployment Documentation

## Overview

This directory contains all deployment-related documentation for the Fabric Management System.

---

## 📁 Documentation Structure

### Core References

| Document                                                                               | Purpose                                 | Status     |
| -------------------------------------------------------------------------------------- | --------------------------------------- | ---------- |
| [CURRENT_DEPLOYMENT_REFERENCE.md](./CURRENT_DEPLOYMENT_REFERENCE.md)                   | **Complete deployment reference guide** | ✅ CURRENT |
| [ENVIRONMENT_MANAGEMENT_BEST_PRACTICES.md](./ENVIRONMENT_MANAGEMENT_BEST_PRACTICES.md) | Environment variables best practices    | ✅ UPDATED |
| [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md)                                           | Step-by-step deployment instructions    | ✅ ACTIVE  |
| [NEW_SERVICE_INTEGRATION_GUIDE.md](./NEW_SERVICE_INTEGRATION_GUIDE.md)                 | **New microservice integration guide**  | ✅ NEW     |

### Configuration Guides

| Document                                                   | Purpose                   | Status       |
| ---------------------------------------------------------- | ------------------------- | ------------ |
| [API_GATEWAY_SETUP.md](./API_GATEWAY_SETUP.md)             | API Gateway configuration | ✅ ACTIVE    |
| [SERVICE_DISCOVERY_SETUP.md](./SERVICE_DISCOVERY_SETUP.md) | Service discovery setup   | 🔄 FUTURE    |
| [DEPLOYMENT_IMPROVEMENTS.md](./DEPLOYMENT_IMPROVEMENTS.md) | Improvement roadmap       | 📋 REFERENCE |

---

## 🚀 Quick Start

### 1. Environment Setup

```bash
# Copy and configure environment
cp .env.example .env
vim .env  # Update with your values
```

### 2. Local Development

```bash
# Start infrastructure only
docker-compose up -d

# Check status
docker-compose ps
```

### 3. Full Stack Deployment

```bash
# Deploy everything
docker-compose -f docker-compose-complete.yml up -d

# Verify health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

---

## 📋 Current Architecture (October 2025)

### System Components

- **3 Microservices**: User, Contact, Company
- **Database**: PostgreSQL 15
- **Cache**: Redis 7
- **Message Broker**: Apache Kafka
- **Build**: Maven + Docker
- **Java**: JDK 21

### Key Features

- ✅ Parametric Dockerfile (DRY principle)
- ✅ Environment-based configuration
- ✅ No hardcoded values
- ✅ Health checks configured
- ✅ Security hardened
- ✅ Production ready

### Applied Principles

- **KISS**: Simple configuration
- **DRY**: No code duplication
- **YAGNI**: No unnecessary complexity
- **12-Factor**: Environment-based config
- **Fail-Fast**: No default values

---

## 🔧 Configuration Files

### Environment Files

```
.env                 # Active configuration (not in Git)
.env.example         # Template with all variables
```

### Docker Files

```
Dockerfile.service           # Universal parametric Dockerfile
docker-compose.yml          # Infrastructure services
docker-compose-complete.yml # Full stack deployment
```

### Scripts

```
scripts/docker-entrypoint.sh  # Optimized entrypoint
scripts/init-db.sql           # Database initialization
scripts/deploy.sh             # Deployment automation
```

---

## 📊 Environment Variables

### Required Variables

```bash
# Database
POSTGRES_HOST
POSTGRES_PORT
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD

# Cache
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD

# Message Broker
KAFKA_HOST
KAFKA_PORT
KAFKA_ADVERTISED_HOST
KAFKA_BOOTSTRAP_SERVERS

# Security
JWT_SECRET
JWT_EXPIRATION
JWT_REFRESH_EXPIRATION

# Services
USER_SERVICE_PORT
CONTACT_SERVICE_PORT
COMPANY_SERVICE_PORT
```

See [CURRENT_DEPLOYMENT_REFERENCE.md](./CURRENT_DEPLOYMENT_REFERENCE.md) for complete list.

---

## 🔍 Health & Monitoring

### Health Endpoints

- User Service: `http://localhost:8081/actuator/health`
- Contact Service: `http://localhost:8082/actuator/health`
- Company Service: `http://localhost:8083/actuator/health`

### Metrics

- Prometheus: `/actuator/prometheus`
- Metrics: `/actuator/metrics`

---

## 🛠️ Maintenance

### Common Tasks

```bash
# View logs
docker-compose logs -f

# Restart services
docker-compose restart

# Clear cache
docker exec fabric-redis redis-cli FLUSHALL

# Run migrations
mvn flyway:migrate

# Backup database
docker exec fabric-postgres pg_dump -U ${POSTGRES_USER} ${POSTGRES_DB} > backup.sql
```

---

## ⚠️ Important Notes

### Security

- Never commit `.env` to Git
- Change all default passwords
- Use strong JWT secrets
- Enable HTTPS in production

### Performance

- JVM: 50% RAM allocation
- Connection pools: 10 max
- Log level: INFO in production

### Troubleshooting

- Check health endpoints first
- Review docker-compose logs
- Verify environment variables
- Check network connectivity

---

## 📚 Additional Resources

### Internal Documentation

- [Development Principles](../development/PRINCIPLES.md)
- [Project Structure](../PROJECT_STRUCTURE.md)
- [Developer Handbook](../DEVELOPER_HANDBOOK.md)

### External Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Docker Documentation](https://docs.docker.com/)
- [12-Factor App](https://12factor.net/)

---

## 🔄 Version History

| Date       | Version | Changes                            |
| ---------- | ------- | ---------------------------------- |
| 02.10.2025 | 2.0     | Complete refactoring, cleaned up   |
| 02.10.2025 | 1.5     | Environment variables standardized |
| 01.10.2025 | 1.0     | Initial deployment setup           |

---

**Last Updated:** 2025-10-09 20:15 UTC+1  
**Version:** 2.0  
**Status:** ✅ Current & Maintained  
**Contact:** Development Team

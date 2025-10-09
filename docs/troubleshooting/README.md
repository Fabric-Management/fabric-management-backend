# 🔧 Troubleshooting Guide

This directory contains detailed troubleshooting guides for common issues encountered in the Fabric Management System.

## 📋 Available Guides

### ✅ Resolved Issues

1. **[Flyway Checksum Mismatch](./FLYWAY_CHECKSUM_MISMATCH.md)**

   - **Issue:** Services failing with Flyway validation errors
   - **Root Cause:** Multiple services sharing same Flyway schema history table
   - **Solution:** Separate Flyway history tables per microservice
   - **Status:** ✅ Resolved (October 6, 2025)

2. **[Bean Conflict Resolution](./BEAN_CONFLICT_RESOLUTION.md)**
   - **Issue:** Spring Bean conflict - GlobalExceptionHandler
   - **Root Cause:** Multiple `@RestControllerAdvice` beans with same name
   - **Solution:** @ConditionalOnMissingBean pattern for flexible exception handling
   - **Status:** ✅ Resolved (October 7, 2025)

---

## 🔍 Quick Issue Reference

### Startup Issues

| Symptom                              | Likely Cause             | Guide                                                     |
| ------------------------------------ | ------------------------ | --------------------------------------------------------- |
| `ConflictingBeanDefinitionException` | Bean name conflict       | [Bean Conflict Resolution](./BEAN_CONFLICT_RESOLUTION.md) |
| `FlywayValidateException`            | Flyway checksum mismatch | [Flyway Checksum Mismatch](./FLYWAY_CHECKSUM_MISMATCH.md) |
| Service won't start                  | Check Docker logs        | See below                                                 |
| Port already in use                  | Another service running  | See below                                                 |

### Database Issues

| Symptom                  | Likely Cause            | Guide                                                     |
| ------------------------ | ----------------------- | --------------------------------------------------------- |
| Connection refused       | Database not ready      | Wait for health check                                     |
| Migration checksum error | Modified migration file | [Flyway Checksum Mismatch](./FLYWAY_CHECKSUM_MISMATCH.md) |
| Table already exists     | Database not clean      | Run `docker compose down -v`                              |

### Kafka Issues

| Symptom                | Likely Cause           | Solution           |
| ---------------------- | ---------------------- | ------------------ |
| `LEADER_NOT_AVAILABLE` | Topics not created yet | Wait ~30 seconds   |
| Consumer not receiving | Topic doesn't exist    | Check Kafka logs   |
| Producer timeout       | Kafka not ready        | Check Kafka health |

---

## 🚀 Common Solutions

### Full System Reset

```bash
# Stop all containers
docker compose down

# Remove all volumes (WARNING: Deletes all data)
docker compose down -v

# Rebuild everything
docker compose build --no-cache

# Start fresh
docker compose up -d

# Check health
docker compose ps
```

### Service-Specific Reset

```bash
# Rebuild single service
docker compose build --no-cache user-service

# Restart single service
docker compose restart user-service

# View logs
docker compose logs user-service -f
```

### Check Service Health

```bash
# Check all services
docker compose ps

# Check specific service
docker compose ps user-service

# View health check logs
docker compose logs user-service | grep -i health
```

---

## 🐛 Debugging Tips

### View Logs

```bash
# Follow logs for all services
docker compose logs -f

# Follow logs for specific service
docker compose logs -f user-service

# View last 50 lines
docker compose logs user-service | tail -50

# View first 150 lines (startup)
docker compose logs user-service | head -150

# Search for errors
docker compose logs user-service | grep -i error

# Search for exceptions
docker compose logs user-service | grep -i exception
```

### Check Service Details

```bash
# Inspect service
docker compose exec user-service bash

# Check environment variables
docker compose exec user-service env

# Check Java version
docker compose exec user-service java -version

# Check service health endpoint
curl http://localhost:8081/actuator/health
```

### Database Debugging

```bash
# Connect to PostgreSQL
docker compose exec postgres psql -U fabric_user -d fabric_management

# List all tables
\dt

# List Flyway history tables
\dt *flyway*

# Check specific table
SELECT * FROM user_flyway_schema_history;

# Exit
\q
```

### Kafka Debugging

```bash
# List topics
docker compose exec kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list

# Check consumer groups
docker compose exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --list

# View topic messages
docker compose exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic contact.created \
  --from-beginning
```

---

## 📊 Health Check Reference

### Expected Healthy State

```bash
$ docker compose ps

NAME                     STATUS
fabric-api-gateway       Up X minutes (healthy)
fabric-company-service   Up X minutes (healthy)
fabric-contact-service   Up X minutes (healthy)
fabric-kafka             Up X minutes (healthy)
fabric-postgres          Up X minutes (healthy)
fabric-redis             Up X minutes (healthy)
fabric-user-service      Up X minutes (healthy)
fabric-zookeeper         Up X minutes (healthy)
```

### Service Ports

| Service         | Port | Health Check                                             |
| --------------- | ---- | -------------------------------------------------------- |
| API Gateway     | 8080 | `curl http://localhost:8080/actuator/health`             |
| User Service    | 8081 | `curl http://localhost:8081/actuator/health`             |
| Contact Service | 8082 | `curl http://localhost:8082/actuator/health`             |
| Company Service | 8083 | `curl http://localhost:8083/actuator/health`             |
| PostgreSQL      | 5432 | `docker compose exec postgres pg_isready`                |
| Redis           | 6379 | `docker compose exec redis redis-cli ping`               |
| Kafka           | 9092 | `docker compose exec kafka kafka-broker-api-versions.sh` |

---

## 🆘 When to Create a New Troubleshooting Guide

Create a new guide when:

- ✅ Issue takes >30 minutes to debug
- ✅ Issue is likely to recur
- ✅ Root cause is non-obvious
- ✅ Solution involves multiple steps
- ✅ Affects multiple developers

### Guide Template

```markdown
# [Issue Name] - [Status]

## Problem Description

[Clear description of the issue and symptoms]

## Root Cause Analysis

[Why the issue occurred]

## Solution

[Step-by-step solution]

## Prevention

[How to avoid this issue in the future]

## Status

[Resolved/In Progress/Known Issue]
```

---

## 📚 Related Documentation

- [Architecture Guide](../architecture/) - System design and patterns
- [Development Guide](../development/) - Setup and standards
- [Deployment Guide](../deployment/) - Production deployment
- [Database Guide](../database/) - Database schema and migrations

---

## 🤝 Contributing

Found a new issue? Solved a tricky problem?

1. Document it following the template above
2. Add it to this README
3. Submit a PR
4. Help your fellow developers! 🎉

---

**Last Updated:** 2025-10-09 20:15 UTC+1  
**Version:** 1.0.0  
**Status:** ✅ Active  
**Maintainer:** Development Team

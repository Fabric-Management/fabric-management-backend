# 🔧 Common Issues & Solutions

**Last Updated:** October 10, 2025  
**Status:** ✅ Active  
**Purpose:** Quick reference for common problems and their solutions

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

## 🐛 Debugging Commands

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

## 🔍 Quick Issue Reference

### Startup Issues

| Symptom                              | Likely Cause             | Guide                                                     |
| ------------------------------------ | ------------------------ | --------------------------------------------------------- |
| `ConflictingBeanDefinitionException` | Bean name conflict       | [Bean Conflict Resolution](./BEAN_CONFLICT_RESOLUTION.md) |
| `FlywayValidateException`            | Flyway checksum mismatch | [Flyway Checksum Mismatch](./FLYWAY_CHECKSUM_MISMATCH.md) |
| Service won't start                  | Check Docker logs        | Use `docker compose logs <service>`                       |
| Port already in use                  | Another service running  | Use `lsof -i :<port>` to find and kill process            |

### Database Issues

| Symptom                  | Likely Cause            | Solution                                                  |
| ------------------------ | ----------------------- | --------------------------------------------------------- |
| Connection refused       | Database not ready      | Wait for health check or restart PostgreSQL               |
| Migration checksum error | Modified migration file | [Flyway Checksum Mismatch](./FLYWAY_CHECKSUM_MISMATCH.md) |
| Table already exists     | Database not clean      | Run `docker compose down -v`                              |

### Kafka Issues

| Symptom                | Likely Cause           | Solution                                          |
| ---------------------- | ---------------------- | ------------------------------------------------- |
| `LEADER_NOT_AVAILABLE` | Topics not created yet | Wait ~30 seconds for Kafka initialization         |
| Consumer not receiving | Topic doesn't exist    | Check Kafka logs and topic list                   |
| Producer timeout       | Kafka not ready        | Check Kafka health with `docker compose ps kafka` |

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

### Service Ports & Health Checks

| Service             | Port | Health Check Command                                                                       |
| ------------------- | ---- | ------------------------------------------------------------------------------------------ |
| **API Gateway**     | 8080 | `curl http://localhost:8080/actuator/health`                                               |
| **User Service**    | 8081 | `curl http://localhost:8081/actuator/health`                                               |
| **Contact Service** | 8082 | `curl http://localhost:8082/actuator/health`                                               |
| **Company Service** | 8083 | `curl http://localhost:8083/actuator/health`                                               |
| **PostgreSQL**      | 5432 | `docker compose exec postgres pg_isready`                                                  |
| **Redis**           | 6379 | `docker compose exec redis redis-cli ping`                                                 |
| **Kafka**           | 9092 | `docker compose exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092` |

---

## 🆘 When to Create a New Troubleshooting Guide

Create a new dedicated guide when:

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

## Date

[YYYY-MM-DD]
```

---

## 🔗 Related Documentation

- [BEAN_CONFLICT_RESOLUTION.md](./BEAN_CONFLICT_RESOLUTION.md) - Spring Bean conflicts
- [FLYWAY_CHECKSUM_MISMATCH.md](./FLYWAY_CHECKSUM_MISMATCH.md) - Flyway migration issues
- [Architecture Guide](../architecture/README.md) - System design
- [Deployment Guide](../deployment/README.md) - Production deployment

---

**Maintained By:** Development Team  
**Last Updated:** 2025-10-10  
**Version:** 1.0  
**Status:** ✅ Active - Regularly updated with new issues

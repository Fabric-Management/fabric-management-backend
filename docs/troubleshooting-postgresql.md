# PostgreSQL Connection Troubleshooting Guide

## Overview
This guide documents common PostgreSQL connection issues encountered when running microservices with Docker and their solutions.

## Common Issues and Solutions

### Issue 1: Database Does Not Exist
**Error**: `FATAL: database "fabric_management_db" does not exist`

**Cause**: Application trying to connect to PostgreSQL instance that doesn't have the required database.

**Solution**:
```bash
# Check if database exists in Docker PostgreSQL
docker exec -it fabric_postgres psql -U postgres -c "\l"

# Create database if missing
docker exec -it fabric_postgres psql -U postgres -c "CREATE DATABASE fabric_management_db;"
```

### Issue 2: Port Conflict Between Multiple PostgreSQL Instances
**Error**: Connection refused or connecting to wrong PostgreSQL instance

**Cause**: Both Homebrew PostgreSQL and Docker PostgreSQL running on port 5432.

**Detection**:
```bash
# Check what's using port 5432
lsof -i :5432

# Check running services
brew services list | grep postgres
```

**Solution**:
```bash
# Stop Homebrew PostgreSQL
brew services stop postgresql@14

# Verify only Docker PostgreSQL is running
lsof -i :5432
```

### Issue 3: User Authentication Failed
**Error**: `FATAL: role "fabric_user" is not permitted to log in`

**Solution**:
```bash
# Connect to PostgreSQL
docker exec -it fabric_postgres psql -U postgres -d fabric_management_db

# Grant login permission
ALTER USER fabric_user WITH LOGIN;
GRANT ALL PRIVILEGES ON DATABASE fabric_management_db TO fabric_user;
```

### Issue 4: Port Already in Use
**Error**: `Port 8081 was already in use`

**Solution**:
```bash
# Find process using the port
lsof -i :8081

# Kill the process
kill -9 $(lsof -t -i:8081)
```

## Complete Setup Process

### 1. Start Docker Services
```bash
cd docker
docker-compose up -d
```

### 2. Ensure Only Docker PostgreSQL is Running
```bash
# Stop any local PostgreSQL
brew services stop postgresql@14

# Verify
lsof -i :5432
```

### 3. Configure Application Properties
```properties
# service/user-service/src/main/resources/application.properties
server.port=8081
spring.application.name=user-service

spring.datasource.url=jdbc:postgresql://localhost:5432/fabric_management_db?currentSchema=user_service
spring.datasource.username=postgres
spring.datasource.password=postgres_admin

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

### 4. Start Microservices
```bash
# Clear ports if needed
kill -9 $(lsof -t -i:8081) 2>/dev/null

# Start service
cd service/user-service
./mvnw spring-boot:run
```

## Verification Steps

### 1. Check Database Connection
```bash
# Test PostgreSQL connection
psql -h localhost -p 5432 -U postgres -d fabric_management_db
```

### 2. Check Service Health
```bash
# After service starts
curl http://localhost:8081/actuator/health
```

### 3. Monitor Logs
```bash
# Docker PostgreSQL logs
docker logs fabric_postgres

# Application logs will show:
# - HikariPool-1 - Start completed
# - Started UserServiceApplication
```

## Quick Reference Script

Create a `start-services.sh` script:

```bash
#!/bin/bash

echo "🚀 Starting Fabric Management Services..."

# Stop conflicting services
echo "📦 Stopping Homebrew PostgreSQL..."
brew services stop postgresql@14 2>/dev/null

# Start Docker services
echo "🐳 Starting Docker services..."
cd docker && docker-compose up -d

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL..."
sleep 5

# Clear ports
echo "🧹 Clearing ports..."
kill -9 $(lsof -t -i:8081) 2>/dev/null
kill -9 $(lsof -t -i:8082) 2>/dev/null

# Start microservices
echo "🎯 Starting User Service..."
cd ../service/user-service
./mvnw spring-boot:run &

echo "✅ Services started successfully!"
```

## Environment Information

- **Java Version**: 21
- **Spring Boot Version**: 3.5.3
- **PostgreSQL Version**: 15.13 (Docker)
- **Docker Compose Version**: 3.8

## Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

---

*Last Updated: July 2025*
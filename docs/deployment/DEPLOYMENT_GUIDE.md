# 🚀 Fabric Management System - Deployment Guide

## 📋 Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Deployment Options](#deployment-options)
4. [Configuration](#configuration)
5. [Monitoring](#monitoring)
6. [Troubleshooting](#troubleshooting)
7. [Production Checklist](#production-checklist)

---

## 🔧 Prerequisites

### System Requirements

- **OS:** Linux/macOS/Windows with WSL2
- **RAM:** Minimum 8GB, Recommended 16GB
- **Disk:** Minimum 20GB free space
- **CPU:** 4+ cores recommended

### Software Requirements

- **Docker:** 20.10+ ([Install](https://docs.docker.com/get-docker/))
- **Docker Compose:** 2.0+ ([Install](https://docs.docker.com/compose/install/))
- **Make:** (optional, for convenience commands)
- **Java 21:** (only for local development)
- **Maven 3.9+:** (only for local development)

### Verify Installation

```bash
docker --version
docker-compose --version
make --version  # optional
```

---

## ⚡ Quick Start

### 1. Clone Repository

```bash
git clone <repository-url>
cd fabric-management-backend
```

### 2. Setup Environment

```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your values
nano .env
```

**Important Variables to Update:**

- `POSTGRES_PASSWORD`: Change from default
- `JWT_SECRET`: Use strong secret (already generated)
- `REDIS_PASSWORD`: Set if using production

### 3. Deploy Infrastructure

```bash
# Using Make
make deploy-infra

# Or using Docker Compose directly
docker-compose up -d
```

### 4. Deploy Services

```bash
# Using Make
make deploy

# Or using Docker Compose directly
docker-compose -f docker-compose-complete.yml up -d
```

### 5. Verify Deployment

```bash
# Check service health
make health

# Check container status
make status

# View logs
make logs
```

---

## 🎯 Deployment Options

### Option 1: Infrastructure Only

Deploy only databases, cache, and message broker:

```bash
make deploy-infra
```

**Services Started:**

- PostgreSQL (port 5433)
- Redis (port 6379)
- Kafka (port 9092)
- Zookeeper (port 2181)

### Option 2: Complete System

Deploy infrastructure + all microservices:

```bash
make deploy
```

**Services Started:**

- All infrastructure services
- User Service (port 8081)
- Contact Service (port 8082)
- Company Service (port 8083)

### Option 3: Specific Service Only

Deploy or restart a specific service:

```bash
make deploy-service SERVICE=user-service
make restart-service SERVICE=contact-service
```

### Option 4: With Monitoring

Deploy system with Prometheus and Grafana:

```bash
# Deploy main system
make deploy

# Deploy monitoring stack
docker-compose -f docker-compose.monitoring.yml up -d
```

**Monitoring Access:**

- **Prometheus:** http://localhost:9090
- **Grafana:** http://localhost:3000 (admin/admin)

---

## ⚙️ Configuration

### Environment Variables

#### Database Configuration

```bash
POSTGRES_HOST=localhost           # For local dev
POSTGRES_HOST=postgres            # For Docker
POSTGRES_PORT=5433                # External port
POSTGRES_DB=fabric_management
POSTGRES_USER=fabric_user
POSTGRES_PASSWORD=your_secure_password
```

#### Service Ports

```bash
USER_SERVICE_PORT=8081
CONTACT_SERVICE_PORT=8082
COMPANY_SERVICE_PORT=8083
```

#### JVM Configuration

```bash
JAVA_OPTS=-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC
```

### Resource Limits

Each microservice has default limits:

- **Memory:** 1024MB limit, 512MB reservation
- **CPU:** 1.0 core limit, 0.5 core reservation

Adjust in `docker-compose-complete.yml`:

```yaml
deploy:
  resources:
    limits:
      memory: 2048M # Increase if needed
      cpus: "2.0"
```

---

## 📊 Monitoring

### Health Checks

**Check all services:**

```bash
make health
```

**Individual service health:**

```bash
curl http://localhost:8081/api/v1/users/actuator/health
curl http://localhost:8082/api/v1/contacts/actuator/health
curl http://localhost:8083/api/v1/companies/actuator/health
```

### Prometheus Metrics

Access metrics for each service:

```bash
curl http://localhost:8081/api/v1/users/actuator/prometheus
curl http://localhost:8082/api/v1/contacts/actuator/prometheus
curl http://localhost:8083/api/v1/companies/actuator/prometheus
```

### Grafana Dashboards

1. Access Grafana: http://localhost:3000
2. Login: `admin` / `admin`
3. Add Prometheus datasource:
   - URL: `http://prometheus:9090`
   - Access: `Server (default)`
4. Import dashboards (IDs):
   - **Spring Boot 2.1 Statistics:** 10280
   - **JVM (Micrometer):** 4701
   - **PostgreSQL Database:** 9628

### JMX Monitoring

JMX ports exposed:

- User Service: `localhost:9011`
- Contact Service: `localhost:9012`
- Company Service: `localhost:9013`

Connect with JConsole or VisualVM:

```bash
jconsole localhost:9011
```

---

## 🔍 Troubleshooting

### Common Issues

#### 1. Port Already in Use

```bash
# Check what's using the port
lsof -i :8081

# Kill process
kill -9 <PID>

# Or change port in .env
USER_SERVICE_PORT=8091
```

#### 2. Container Fails to Start

```bash
# Check logs
docker logs fabric-user-service

# Check resource usage
docker stats

# Increase memory limits if OOM
```

#### 3. Database Connection Failed

```bash
# Check if PostgreSQL is ready
docker exec fabric-postgres pg_isready

# Check connection
docker exec fabric-postgres psql -U fabric_user -d fabric_management -c "SELECT 1"

# Restart database
docker-compose restart postgres
```

#### 4. Kafka Connection Issues

```bash
# Check Kafka is ready
docker exec fabric-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Check Zookeeper
docker exec fabric-zookeeper zkServer.sh status

# Restart Kafka stack
docker-compose restart zookeeper kafka
```

### Debug Mode

Enable debug logging:

```bash
# In .env
LOG_LEVEL=DEBUG

# Restart services
make restart
```

### View Detailed Logs

```bash
# All services
make logs

# Specific service
make logs-service SERVICE=user-service

# Last 100 lines
docker logs fabric-user-service --tail 100

# Follow logs
docker logs -f fabric-user-service
```

---

## ✅ Production Checklist

### Before Deployment

- [ ] Review and update `.env` file
- [ ] Change all default passwords
- [ ] Generate strong JWT secret (minimum 256-bit)
- [ ] Configure proper resource limits
- [ ] Set up SSL/TLS certificates
- [ ] Configure firewall rules
- [ ] Set up backup procedures
- [ ] Configure log rotation
- [ ] Set up monitoring and alerting
- [ ] Review security settings

### Security Checklist

- [ ] Database password is strong (16+ chars)
- [ ] JWT secret is cryptographically secure
- [ ] Redis password is set (if exposed)
- [ ] Actuator endpoints are secured
- [ ] Docker containers run as non-root
- [ ] Sensitive ports not exposed publicly
- [ ] Environment variables not in Git
- [ ] SSL/TLS enabled for external connections
- [ ] Regular security updates scheduled

### Performance Checklist

- [ ] JVM memory limits configured
- [ ] CPU limits appropriate
- [ ] Database connection pool sized
- [ ] Redis cache TTL configured
- [ ] Kafka topics created with proper partitions
- [ ] Load testing completed
- [ ] Monitoring dashboards set up
- [ ] Log aggregation configured

### Backup Checklist

- [ ] Database backup automated
- [ ] Backup retention policy defined
- [ ] Restore procedure documented and tested
- [ ] Configuration files backed up
- [ ] Docker volumes backed up

---

## 📚 Additional Commands

### Database Management

**Backup database:**

```bash
make db-backup
```

**Restore database:**

```bash
make db-restore FILE=backup-20241001.sql
```

**Access database shell:**

```bash
make db-shell
```

### Container Management

**View container stats:**

```bash
docker stats

# Or specific container
docker stats fabric-user-service
```

**Execute command in container:**

```bash
docker exec -it fabric-user-service sh
```

**View container logs:**

```bash
docker logs fabric-user-service --since 1h
```

### Cleanup

**Stop all services:**

```bash
make down
```

**Stop and remove volumes:**

```bash
make down-clean
```

**Clean Docker system:**

```bash
make prune
```

**Remove all images:**

```bash
make clean-docker
```

---

## 🆘 Getting Help

### Log Files

- Application logs: Inside containers
- Docker logs: `docker logs <container-name>`
- System logs: `/var/log/`

### Useful Commands

```bash
# Show all running containers
make ps

# Check service status
make status

# Health check all services
make health

# View real-time logs
make logs
```

### Support Resources

- **Documentation:** `/docs` directory
- **Architecture:** `docs/architecture/README.md`
- **API Docs:** `docs/api/README.md`

---

## 🚀 Next Steps

After successful deployment:

1. **Access Swagger UI:**

   - User Service: http://localhost:8081/api/v1/users/swagger-ui.html
   - Contact Service: http://localhost:8082/api/v1/contacts/swagger-ui.html
   - Company Service: http://localhost:8083/api/v1/companies/swagger-ui.html

2. **Set up monitoring dashboards in Grafana**

3. **Configure alerting rules in Prometheus**

4. **Review logs for any warnings**

5. **Run integration tests**

6. **Set up CI/CD pipeline**

---

**Last Updated:** October 1, 2025  
**Version:** 1.0.0  
**Maintainer:** DevOps Team

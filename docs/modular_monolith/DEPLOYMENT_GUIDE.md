# 🚀 DEPLOYMENT GUIDE

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Status:** ✅ Active Development

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development](#local-development)
4. [Docker Deployment](#docker-deployment)
5. [Kubernetes Deployment](#kubernetes-deployment)
6. [Environment Configuration](#environment-configuration)
7. [Database Migration](#database-migration)
8. [Monitoring & Logging](#monitoring--logging)
9. [Rollback Strategy](#rollback-strategy)

---

## 🎯 OVERVIEW

Fabric Management platformu, **Blue-Green Deployment** stratejisi ile deploy edilir. Zero-downtime deployment ve hızlı rollback garantisi sağlanır.

### **Deployment Environments**

| Environment    | Purpose               | Infrastructure        |
| -------------- | --------------------- | --------------------- |
| **Local**      | Development & Testing | Docker Compose        |
| **Staging**    | Integration & UAT     | Kubernetes (Minikube) |
| **Production** | Live System           | Kubernetes (AWS EKS)  |

---

## 📦 PREREQUISITES

### **Required Software**

| Software       | Version         | Purpose          |
| -------------- | --------------- | ---------------- |
| **Java**       | 17+             | Runtime          |
| **Maven**      | 3.8+            | Build tool       |
| **Docker**     | 20.10+          | Containerization |
| **Kubernetes** | 1.25+           | Orchestration    |
| **PostgreSQL** | 15+             | Database         |
| **Redis**      | 7+              | Cache            |
| **Kafka**      | 3.5+ (Optional) | Event streaming  |

### **Installation**

```bash
# Java
brew install openjdk@17

# Maven
brew install maven

# Docker
brew install docker

# Kubernetes
brew install kubectl
brew install minikube

# PostgreSQL
brew install postgresql@15

# Redis
brew install redis
```

---

## 💻 LOCAL DEVELOPMENT

### **1. Clone Repository**

```bash
git clone https://github.com/fabric-management/fabric-management-backend.git
cd fabric-management-backend
```

### **2. Start Infrastructure**

```bash
# Start PostgreSQL, Redis, Kafka
docker-compose up -d

# Verify services
docker-compose ps
```

### **3. Configure Environment**

```bash
# Copy environment template
cp .env.example .env

# Edit environment variables
vim .env
```

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=fabricmanagement
DB_USER=postgres
DB_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# JWT
JWT_SECRET=your-secret-key
JWT_EXPIRATION=86400000

# Server
SERVER_PORT=8080
```

### **4. Build Application**

```bash
# Clean and build
mvn clean install

# Skip tests
mvn clean install -DskipTests
```

### **5. Run Application**

```bash
# Run with Maven
mvn spring-boot:run

# Or run JAR
java -jar target/fabric-management-1.0.0.jar
```

### **6. Verify Application**

```bash
# Health check
curl http://localhost:8080/actuator/health

# API check
curl http://localhost:8080/api/production/materials
```

---

## 🐳 DOCKER DEPLOYMENT

### **1. Build Docker Image**

```dockerfile
# Dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy JAR
COPY target/fabric-management-1.0.0.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build image
docker build -t fabric-management:1.0.0 .

# Tag image
docker tag fabric-management:1.0.0 fabric-management:latest
```

### **2. Docker Compose**

```yaml
# docker-compose.yml
version: "3.8"

services:
  # Application
  app:
    image: fabric-management:latest
    container_name: fabric-management-app
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_NAME=fabricmanagement
      - DB_USER=postgres
      - DB_PASSWORD=postgres
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    depends_on:
      - postgres
      - redis
      - kafka
    networks:
      - fabric-network
    restart: unless-stopped

  # PostgreSQL
  postgres:
    image: postgres:15
    container_name: fabric-postgres
    environment:
      - POSTGRES_DB=fabricmanagement
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - fabric-network
    restart: unless-stopped

  # Redis
  redis:
    image: redis:7
    container_name: fabric-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - fabric-network
    restart: unless-stopped

  # Kafka
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: fabric-kafka
    ports:
      - "9092:9092"
    environment:
      - KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181
      - KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
      - KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1
    depends_on:
      - zookeeper
    networks:
      - fabric-network
    restart: unless-stopped

  # Zookeeper
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: fabric-zookeeper
    environment:
      - ZOOKEEPER_CLIENT_PORT=2181
    networks:
      - fabric-network
    restart: unless-stopped

volumes:
  postgres-data:
  redis-data:

networks:
  fabric-network:
    driver: bridge
```

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop all services
docker-compose down
```

---

## ☸️ KUBERNETES DEPLOYMENT

### **1. Namespace**

```yaml
# k8s/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: fabric-management
```

```bash
kubectl apply -f k8s/namespace.yaml
```

### **2. ConfigMap**

```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fabric-config
  namespace: fabric-management
data:
  SERVER_PORT: "8080"
  DB_HOST: "postgres-service"
  DB_PORT: "5432"
  DB_NAME: "fabricmanagement"
  REDIS_HOST: "redis-service"
  REDIS_PORT: "6379"
  KAFKA_BOOTSTRAP_SERVERS: "kafka-service:9092"
```

```bash
kubectl apply -f k8s/configmap.yaml
```

### **3. Secret**

```yaml
# k8s/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: fabric-secret
  namespace: fabric-management
type: Opaque
data:
  DB_USER: cG9zdGdyZXM= # postgres (base64)
  DB_PASSWORD: cG9zdGdyZXM= # postgres (base64)
  JWT_SECRET: eW91ci1zZWNyZXQta2V5 # your-secret-key (base64)
```

```bash
kubectl apply -f k8s/secret.yaml
```

### **4. Deployment**

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fabric-management
  namespace: fabric-management
spec:
  replicas: 3
  selector:
    matchLabels:
      app: fabric-management
  template:
    metadata:
      labels:
        app: fabric-management
    spec:
      containers:
        - name: fabric-management
          image: fabric-management:1.0.0
          ports:
            - containerPort: 8080
          env:
            - name: SERVER_PORT
              valueFrom:
                configMapKeyRef:
                  name: fabric-config
                  key: SERVER_PORT
            - name: DB_HOST
              valueFrom:
                configMapKeyRef:
                  name: fabric-config
                  key: DB_HOST
            - name: DB_PORT
              valueFrom:
                configMapKeyRef:
                  name: fabric-config
                  key: DB_PORT
            - name: DB_NAME
              valueFrom:
                configMapKeyRef:
                  name: fabric-config
                  key: DB_NAME
            - name: DB_USER
              valueFrom:
                secretKeyRef:
                  name: fabric-secret
                  key: DB_USER
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: fabric-secret
                  key: DB_PASSWORD
            - name: REDIS_HOST
              valueFrom:
                configMapKeyRef:
                  name: fabric-config
                  key: REDIS_HOST
            - name: REDIS_PORT
              valueFrom:
                configMapKeyRef:
                  name: fabric-config
                  key: REDIS_PORT
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: fabric-secret
                  key: JWT_SECRET
          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "2000m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
```

```bash
kubectl apply -f k8s/deployment.yaml
```

### **5. Service**

```yaml
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: fabric-management-service
  namespace: fabric-management
spec:
  type: LoadBalancer
  selector:
    app: fabric-management
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
```

```bash
kubectl apply -f k8s/service.yaml
```

### **6. Ingress**

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: fabric-management-ingress
  namespace: fabric-management
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts:
        - api.fabricmanagement.com
      secretName: fabric-tls
  rules:
    - host: api.fabricmanagement.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: fabric-management-service
                port:
                  number: 80
```

```bash
kubectl apply -f k8s/ingress.yaml
```

### **7. HorizontalPodAutoscaler**

```yaml
# k8s/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: fabric-management-hpa
  namespace: fabric-management
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: fabric-management
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

```bash
kubectl apply -f k8s/hpa.yaml
```

---

## ⚙️ ENVIRONMENT CONFIGURATION

### **Environment Variables**

| Variable                  | Required | Default  | Description         |
| ------------------------- | -------- | -------- | ------------------- |
| `SERVER_PORT`             | No       | 8080     | Application port    |
| `DB_HOST`                 | Yes      | -        | PostgreSQL host     |
| `DB_PORT`                 | Yes      | 5432     | PostgreSQL port     |
| `DB_NAME`                 | Yes      | -        | Database name       |
| `DB_USER`                 | Yes      | -        | Database user       |
| `DB_PASSWORD`             | Yes      | -        | Database password   |
| `REDIS_HOST`              | Yes      | -        | Redis host          |
| `REDIS_PORT`              | Yes      | 6379     | Redis port          |
| `KAFKA_BOOTSTRAP_SERVERS` | No       | -        | Kafka servers       |
| `JWT_SECRET`              | Yes      | -        | JWT secret key      |
| `JWT_EXPIRATION`          | No       | 86400000 | JWT expiration (ms) |

### **Spring Profiles**

```bash
# Local development
java -jar app.jar --spring.profiles.active=local

# Staging
java -jar app.jar --spring.profiles.active=staging

# Production
java -jar app.jar --spring.profiles.active=prod
```

---

## 🗄️ DATABASE MIGRATION

### **Flyway Migration**

```sql
-- src/main/resources/db/migration/V1__common_auth_init.sql
CREATE SCHEMA IF NOT EXISTS common_auth;

CREATE TABLE common_auth.auth_user (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Enable Row-Level Security
ALTER TABLE common_auth.auth_user ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON common_auth.auth_user
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);
```

### **Migration Commands**

```bash
# Run migrations
mvn flyway:migrate

# Validate migrations
mvn flyway:validate

# Clean database (⚠️ DANGEROUS)
mvn flyway:clean

# Show migration info
mvn flyway:info
```

---

## 📊 MONITORING & LOGGING

### **Prometheus Metrics**

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: "fabric-management"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["localhost:8080"]
```

### **Grafana Dashboard**

```json
{
  "dashboard": {
    "title": "Fabric Management",
    "panels": [
      {
        "title": "Request Rate",
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count[5m])"
          }
        ]
      },
      {
        "title": "Response Time",
        "targets": [
          {
            "expr": "http_server_requests_seconds_sum / http_server_requests_seconds_count"
          }
        ]
      }
    ]
  }
}
```

### **Logging Configuration**

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.fabricmanagement: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/fabric-management.log
    max-size: 10MB
    max-history: 30
```

---

## 🔄 ROLLBACK STRATEGY

### **Blue-Green Deployment**

```bash
# 1. Deploy new version (Green)
kubectl apply -f k8s/deployment-green.yaml

# 2. Wait for green pods to be ready
kubectl wait --for=condition=ready pod -l app=fabric-management-green

# 3. Switch traffic to green
kubectl patch service fabric-management-service -p '{"spec":{"selector":{"app":"fabric-management-green"}}}'

# 4. Monitor green deployment
kubectl logs -f -l app=fabric-management-green

# 5. If successful, delete blue deployment
kubectl delete deployment fabric-management-blue

# 6. If failed, rollback to blue
kubectl patch service fabric-management-service -p '{"spec":{"selector":{"app":"fabric-management-blue"}}}'
```

### **Kubernetes Rollback**

```bash
# View rollout history
kubectl rollout history deployment/fabric-management

# Rollback to previous version
kubectl rollout undo deployment/fabric-management

# Rollback to specific revision
kubectl rollout undo deployment/fabric-management --to-revision=2

# Check rollout status
kubectl rollout status deployment/fabric-management
```

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

# Company Service Kubernetes Deployment

## 📋 Overview

Company Service için Kubernetes deployment manifest'leri ve configuration dosyaları.

## 🚀 Deployment Manifests

### **Company Service Deployment**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: company-service
  namespace: fabric-management
  labels:
    app: company-service
    version: v1.0.0
spec:
  replicas: 2
  selector:
    matchLabels:
      app: company-service
  template:
    metadata:
      labels:
        app: company-service
        version: v1.0.0
    spec:
      containers:
        - name: company-service
          image: fabric-management/company-service:latest
          ports:
            - containerPort: 8084
              name: http
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: SERVER_PORT
              value: "8084"
            - name: POSTGRES_HOST
              value: "postgres-core"
            - name: POSTGRES_PORT
              value: "5432"
            - name: POSTGRES_DB
              value: "company_db"
            - name: POSTGRES_USERNAME
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: username
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: password
            - name: REDIS_HOST
              value: "redis"
            - name: REDIS_PORT
              value: "6379"
            - name: IDENTITY_SERVICE_URL
              value: "http://identity-service:8081"
            - name: CONTACT_SERVICE_URL
              value: "http://contact-service:8083"
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: jwt-secret
                  key: secret
            - name: GOOGLE_MAPS_API_KEY
              valueFrom:
                secretKeyRef:
                  name: google-maps-secret
                  key: api-key
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8084
            initialDelaySeconds: 60
            periodSeconds: 30
            timeoutSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8084
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          volumeMounts:
            - name: config-volume
              mountPath: /app/config
              readOnly: true
      volumes:
        - name: config-volume
          configMap:
            name: company-service-config
      imagePullSecrets:
        - name: fabric-management-registry-secret
```

### **Company Service Service**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: company-service
  namespace: fabric-management
  labels:
    app: company-service
spec:
  type: ClusterIP
  ports:
    - port: 8084
      targetPort: 8084
      protocol: TCP
      name: http
  selector:
    app: company-service
```

### **Company Service ConfigMap**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: company-service-config
  namespace: fabric-management
data:
  application.yml: |
    server:
      port: 8084

    spring:
      application:
        name: company-service
      
      datasource:
        url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}
        username: ${POSTGRES_USERNAME}
        password: ${POSTGRES_PASSWORD}
        driver-class-name: org.postgresql.Driver
        hikari:
          maximum-pool-size: 20
          minimum-idle: 5
          connection-timeout: 30000
          idle-timeout: 600000
          max-lifetime: 1800000
      
      jpa:
        hibernate:
          ddl-auto: validate
        show-sql: false
        properties:
          hibernate:
            dialect: org.hibernate.dialect.PostgreSQLDialect
            format_sql: true
      
      flyway:
        enabled: true
        locations: classpath:db/migration
        baseline-on-migrate: true
      
      data:
        redis:
          host: ${REDIS_HOST}
          port: ${REDIS_PORT}
          timeout: 2000ms
          lettuce:
            pool:
              max-active: 8
              max-idle: 8
              min-idle: 0

    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus
      endpoint:
        health:
          show-details: always
          probes:
            enabled: true

    logging:
      level:
        com.fabricmanagement.company: INFO
        org.springframework.security: WARN
      pattern:
        console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
        file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
      file:
        name: /app/logs/company-service.log
        max-size: 100MB
        max-history: 30
```

### **Company Service Ingress**

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: company-service-ingress
  namespace: fabric-management
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/rate-limit: "100"
    nginx.ingress.kubernetes.io/rate-limit-window: "1m"
spec:
  tls:
    - hosts:
        - api.fabricmanagement.com
      secretName: fabric-management-tls
  rules:
    - host: api.fabricmanagement.com
      http:
        paths:
          - path: /api/v1/companies
            pathType: Prefix
            backend:
              service:
                name: company-service
                port:
                  number: 8084
```

## 🔧 Database Migration

### **Company Service Database Schema**

```sql
-- Companies table
CREATE TABLE companies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    legal_name VARCHAR(200),
    tax_number VARCHAR(50),
    registration_number VARCHAR(50),
    company_type VARCHAR(50),
    industry VARCHAR(100),
    website VARCHAR(200),
    description TEXT,
    logo_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE
);

-- Company settings table
CREATE TABLE company_settings (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    timezone VARCHAR(50) DEFAULT 'UTC',
    language VARCHAR(10) DEFAULT 'en',
    date_format VARCHAR(20) DEFAULT 'MM/dd/yyyy',
    time_format VARCHAR(10) DEFAULT '12h',
    fiscal_year_start DATE,
    business_hours JSONB,
    notification_preferences JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,

    CONSTRAINT fk_company_settings_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

-- Company locations table
CREATE TABLE company_locations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    location_name VARCHAR(100),
    address_type VARCHAR(20) DEFAULT 'HEADQUARTERS',
    street VARCHAR(200),
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(100),
    is_primary BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,

    CONSTRAINT fk_company_location_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

-- Indexes
CREATE INDEX idx_companies_tenant_id ON companies(tenant_id);
CREATE INDEX idx_companies_company_name ON companies(company_name);
CREATE INDEX idx_companies_tax_number ON companies(tax_number);
CREATE INDEX idx_companies_status ON companies(status);
CREATE INDEX idx_companies_deleted ON companies(deleted);

CREATE INDEX idx_company_settings_company_id ON company_settings(company_id);
CREATE INDEX idx_company_settings_deleted ON company_settings(deleted);

CREATE INDEX idx_company_locations_company_id ON company_locations(company_id);
CREATE INDEX idx_company_locations_is_primary ON company_locations(is_primary);
CREATE INDEX idx_company_locations_is_active ON company_locations(is_active);
CREATE INDEX idx_company_locations_deleted ON company_locations(deleted);
```

## 📊 Monitoring & Observability

### **Prometheus ServiceMonitor**

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: company-service-monitor
  namespace: fabric-management
  labels:
    app: company-service
spec:
  selector:
    matchLabels:
      app: company-service
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
      scrapeTimeout: 10s
```

### **Grafana Dashboard**

```json
{
  "dashboard": {
    "title": "Company Service Dashboard",
    "panels": [
      {
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_requests_total{job=\"company-service\"}[5m])",
            "legendFormat": "{{method}} {{endpoint}}"
          }
        ]
      },
      {
        "title": "Response Time",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{job=\"company-service\"}[5m]))",
            "legendFormat": "95th percentile"
          }
        ]
      },
      {
        "title": "Error Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_requests_total{job=\"company-service\",status=~\"5..\"}[5m])",
            "legendFormat": "5xx errors"
          }
        ]
      },
      {
        "title": "Database Connections",
        "type": "graph",
        "targets": [
          {
            "expr": "hikaricp_connections_active{job=\"company-service\"}",
            "legendFormat": "Active connections"
          }
        ]
      }
    ]
  }
}
```

## 🔒 Security Configuration

### **Network Policies**

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: company-service-netpol
  namespace: fabric-management
spec:
  podSelector:
    matchLabels:
      app: company-service
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: fabric-management
        - podSelector:
            matchLabels:
              app: api-gateway
      ports:
        - protocol: TCP
          port: 8084
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: postgres-core
      ports:
        - protocol: TCP
          port: 5432
    - to:
        - podSelector:
            matchLabels:
              app: redis
      ports:
        - protocol: TCP
          port: 6379
    - to:
        - podSelector:
            matchLabels:
              app: identity-service
      ports:
        - protocol: TCP
          port: 8081
    - to:
        - podSelector:
            matchLabels:
              app: contact-service
      ports:
        - protocol: TCP
          port: 8083
```

## 🚀 Deployment Commands

### **Deploy Company Service**

```bash
# Apply all manifests
kubectl apply -f company-service-deployment.yaml
kubectl apply -f company-service-service.yaml
kubectl apply -f company-service-configmap.yaml
kubectl apply -f company-service-ingress.yaml

# Check deployment status
kubectl get pods -n fabric-management -l app=company-service
kubectl get svc -n fabric-management -l app=company-service

# Check logs
kubectl logs -n fabric-management -l app=company-service -f
```

### **Rolling Update**

```bash
# Update image
kubectl set image deployment/company-service company-service=fabric-management/company-service:v1.1.0 -n fabric-management

# Check rollout status
kubectl rollout status deployment/company-service -n fabric-management

# Rollback if needed
kubectl rollout undo deployment/company-service -n fabric-management
```

## 📈 Performance Tuning

### **Resource Optimization**

```yaml
# High-performance configuration
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "1000m"

# JVM tuning
env:
  - name: JAVA_OPTS
    value: "-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### **Database Connection Pool**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
```

## 🔧 Troubleshooting

### **Common Issues**

1. **Database Connection Issues**

   ```bash
   kubectl logs -n fabric-management -l app=company-service | grep -i "database\|connection"
   ```

2. **Memory Issues**

   ```bash
   kubectl top pods -n fabric-management -l app=company-service
   ```

3. **Health Check Failures**
   ```bash
   kubectl describe pod -n fabric-management -l app=company-service
   ```

### **Debug Commands**

```bash
# Get pod details
kubectl describe pod -n fabric-management <pod-name>

# Check service endpoints
kubectl get endpoints -n fabric-management company-service

# Test connectivity
kubectl exec -n fabric-management -it <pod-name> -- curl localhost:8084/actuator/health
```

---

**Last Updated**: 2024-01-15  
**Version**: 1.0.0  
**Maintainer**: DevOps Team

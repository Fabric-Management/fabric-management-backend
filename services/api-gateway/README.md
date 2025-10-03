# 🚪 API Gateway

Central entry point for Fabric Management System microservices.

## 📋 Overview

The API Gateway provides:
- ✅ **Centralized Routing** - Single entry point for all services
- 🔒 **JWT Authentication** - Centralized token validation
- 🚦 **Rate Limiting** - Redis-based request throttling
- ⚡ **Circuit Breaker** - Resilience4j fault tolerance
- 🌍 **CORS Support** - Cross-origin resource sharing
- 📊 **Request Logging** - Comprehensive request/response logging
- 🔄 **Retry Logic** - Automatic retry for failed requests

## 🏗️ Architecture

```
Client → API Gateway (8080) → Microservices
                ↓
            [Filters]
        - JWT Authentication
        - Request Logging
        - Rate Limiting
        - Circuit Breaker
                ↓
            [Routing]
        - User Service (8081)
        - Company Service (8083)
        - Contact Service (8082)
```

## 🚀 Quick Start

### Local Development

```bash
# Run with Maven
cd services/api-gateway
mvn spring-boot:run

# Access
curl http://localhost:8080/gateway/health
```

### Docker

```bash
# Build and run with Docker Compose
docker-compose -f docker-compose-complete.yml up -d api-gateway

# Check logs
docker logs -f fabric-api-gateway
```

## 📡 Endpoints

### Public Endpoints (No Auth Required)

| Endpoint | Description |
|----------|-------------|
| `GET /gateway/health` | Gateway health check |
| `GET /gateway/info` | Gateway information |
| `GET /actuator/health` | Spring Actuator health |
| `POST /api/v1/users/auth/**` | Authentication endpoints |

### Protected Endpoints (Auth Required)

All other endpoints require `Authorization: Bearer <token>` header.

## 🔒 Authentication

Gateway extracts JWT token and adds headers to downstream requests:

```
Authorization: Bearer <token>
    ↓
Gateway validates JWT
    ↓
X-Tenant-Id: <tenant-uuid>
X-User-Id: <user-uuid>
    ↓
Downstream Service
```

## 🚦 Rate Limiting

Redis-based rate limiting per route:
- **Replenish Rate**: 50 requests/second
- **Burst Capacity**: 100 requests

## ⚡ Circuit Breaker

Resilience4j configuration:
- **Failure Threshold**: 50%
- **Wait Duration**: 30s
- **Sliding Window**: 100 calls

## 📊 Monitoring

### Health Checks

```bash
# Gateway health
curl http://localhost:8080/gateway/health

# Actuator health
curl http://localhost:8080/actuator/health

# Circuit breaker status
curl http://localhost:8080/actuator/circuitbreakers
```

### Metrics

```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Gateway routes
curl http://localhost:8080/actuator/gateway/routes
```

## 🔧 Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | JWT signing secret | Required |
| `REDIS_HOST` | Redis host | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `USER_SERVICE_URL` | User service URL | http://localhost:8081 |
| `COMPANY_SERVICE_URL` | Company service URL | http://localhost:8083 |
| `CONTACT_SERVICE_URL` | Contact service URL | http://localhost:8082 |

## 🐛 Troubleshooting

### Issue: 401 Unauthorized

**Solution**: Check JWT token is valid and not expired.

```bash
# Test with valid token
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/users
```

### Issue: 503 Service Unavailable

**Solution**: Check if downstream service is running and healthy.

```bash
# Check service health
curl http://localhost:8081/api/v1/users/actuator/health
```

### Issue: 429 Too Many Requests

**Solution**: Rate limit exceeded. Wait and retry.

## 📚 Related Documentation

- [API Gateway Setup Guide](../../docs/deployment/API_GATEWAY_SETUP.md)
- [Security Configuration](../../docs/security/SECURITY.md)
- [Deployment Guide](../../docs/deployment/DEPLOYMENT.md)

## 🏷️ Version

**Version**: 1.0.0
**Last Updated**: 2025-10-03

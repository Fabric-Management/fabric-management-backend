# 🚪 API Gateway Setup Guide

## Overview

Spring Cloud Gateway ile merkezi API Gateway kurulumu.

---

## 🎯 API Gateway Nedir?

API Gateway, tüm microservice'ler için tek giriş noktası sağlar:

- ✅ Merkezi routing
- ✅ Load balancing
- ✅ Authentication & Authorization
- ✅ Rate limiting
- ✅ Request/Response transformation
- ✅ Monitoring & Logging

---

## 🚀 API Gateway Kurulumu

### 1. Gateway Modülü Oluşturma

```bash
cd fabric-management-backend
mkdir -p services/api-gateway/src/main/java/com/fabricmanagement/gateway
mkdir -p services/api-gateway/src/main/resources
```

### 2. pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.fabricmanagement</groupId>
        <artifactId>fabric-management-backend</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>api-gateway</artifactId>
    <name>API Gateway</name>
    <description>Central API Gateway</description>

    <dependencies>
        <!-- Spring Cloud Gateway -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- Eureka Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Resilience4j -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
        </dependency>

        <!-- Redis Rate Limiter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <!-- Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 3. Application Class

```java
package com.fabricmanagement.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

### 4. application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true

      routes:
        # User Service Routes
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
          filters:
            - name: CircuitBreaker
              args:
                name: userServiceCircuitBreaker
                fallbackUri: forward:/fallback/users
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                redis-rate-limiter.requestedTokens: 1
            - name: Retry
              args:
                retries: 3
                methods: GET
                backoff:
                  firstBackoff: 50ms
                  maxBackoff: 500ms

        # Company Service Routes
        - id: company-service
          uri: lb://company-service
          predicates:
            - Path=/api/v1/companies/**
          filters:
            - name: CircuitBreaker
              args:
                name: companyServiceCircuitBreaker
                fallbackUri: forward:/fallback/companies
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20

        # Contact Service Routes
        - id: contact-service
          uri: lb://contact-service
          predicates:
            - Path=/api/v1/contacts/**
          filters:
            - name: CircuitBreaker
              args:
                name: contactServiceCircuitBreaker
                fallbackUri: forward:/fallback/contacts
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20

      # Global CORS Configuration
      globalcors:
        cors-configurations:
          "[/**]":
            allowed-origins: "*"
            allowed-methods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowed-headers: "*"
            allow-credentials: true

      # Default filters for all routes
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
        - AddRequestHeader=X-Gateway-Request, Gateway-Request
        - AddResponseHeader=X-Gateway-Response, Gateway-Response

  # Redis Configuration for Rate Limiting
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD:}
    timeout: 2000ms

# Eureka Configuration
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${server.port}

# Resilience4j Configuration
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        wait-duration-in-open-state: 10s
        failure-rate-threshold: 50
        event-consumer-buffer-size: 10
    instances:
      userServiceCircuitBreaker:
        base-config: default
      companyServiceCircuitBreaker:
        base-config: default
      contactServiceCircuitBreaker:
        base-config: default

  timelimiter:
    configs:
      default:
        timeout-duration: 5s

# Actuator Configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,gateway
  endpoint:
    health:
      show-details: always
    gateway:
      enabled: true

# Logging
logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    reactor.netty: INFO
```

---

## 🔒 Security Configuration

> **Status:** ✅ PRODUCTION READY (Updated October 2025)

### Security Overview

API Gateway implements multi-layered security:

1. **JWT Authentication** - Token validation for protected endpoints
2. **Rate Limiting** - Endpoint-specific request throttling
3. **Circuit Breaker** - Service failure protection
4. **Security Headers** - Standard HTTP security headers

### JWT Authentication Filter

**File:** `services/api-gateway/src/main/java/com/fabricmanagement/gateway/security/JwtAuthenticationFilter.java`

**Features:**

- ✅ Full JWT validation (signature, expiration, claims)
- ✅ Public/Protected endpoint separation
- ✅ X-Tenant-Id and X-User-Id header injection
- ✅ Reactive (WebFlux) implementation
- ✅ Order: -100 (high priority)

**Public Endpoints:**

```java
/api/v1/users/auth/**           // Authentication endpoints
/api/v1/contacts/find-by-value  // Internal contact lookup
/actuator/health                // Health checks
/actuator/info
/actuator/prometheus
/fallback/**                    // Fallback endpoints
```

**Protected Endpoints:**
All other API endpoints require `Authorization: Bearer <token>` header.

**Implementation:**

```java
@Component("gatewayJwtFilter")
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();

        // Skip authentication for public endpoints
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Extract and validate JWT token
        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return unauthorizedResponse(exchange);
        }

        // Validate token using jjwt library
        Claims claims = validateTokenAndExtractClaims(token);

        // Add tenant and user ID headers to downstream requests
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
            .header("X-Tenant-Id", claims.get("tenantId", String.class))
            .header("X-User-Id", claims.getSubject())
            .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    @Override
    public int getOrder() {
        return -100; // Execute BEFORE other filters
    }
}
```

### Security Configuration

**File:** `services/api-gateway/src/main/java/com/fabricmanagement/gateway/config/SecurityConfig.java`

**Implementation:**

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Public: Authentication endpoints
                .pathMatchers("/api/v1/users/auth/**").permitAll()

                // Public: Internal contact lookup
                .pathMatchers(HttpMethod.GET, "/api/v1/contacts/find-by-value").permitAll()

                // Public: Health and monitoring
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()

                // Protected: All other endpoints require authentication
                .anyExchange().authenticated()
            )
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable);

        return http.build();
    }
}
```

### Fallback Controller

```java
package com.fabricmanagement.gateway.fallback;

import com.fabricmanagement.shared.application.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Void>> userServiceFallback() {
        log.error("User Service is unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                    "User Service is temporarily unavailable. Please try again later.",
                    "SERVICE_UNAVAILABLE"
                ));
    }

    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<Void>> companyServiceFallback() {
        log.error("Company Service is unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                    "Company Service is temporarily unavailable. Please try again later.",
                    "SERVICE_UNAVAILABLE"
                ));
    }

    @GetMapping("/contacts")
    public ResponseEntity<ApiResponse<Void>> contactServiceFallback() {
        log.error("Contact Service is unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                    "Contact Service is temporarily unavailable. Please try again later.",
                    "SERVICE_UNAVAILABLE"
                ));
    }
}
```

---

## 🐳 Docker Compose Entegrasyonu

```yaml
services:
  redis:
    image: redis:7-alpine
    container_name: redis
    ports:
      - "6379:6379"
    networks:
      - fabric-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3

  api-gateway:
    build:
      context: ./services/api-gateway
      dockerfile: Dockerfile
    container_name: api-gateway
    ports:
      - "8080:8080"
    depends_on:
      eureka-server:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
    networks:
      - fabric-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

---

## ✅ Test Etme

### 1. Başlatma Sırası

```bash
# 1. Redis
docker run -d -p 6379:6379 redis:7-alpine

# 2. Eureka Server
cd services/eureka-server && mvn spring-boot:run

# 3. Services
cd services/user-service && mvn spring-boot:run
cd services/company-service && mvn spring-boot:run
cd services/contact-service && mvn spring-boot:run

# 4. API Gateway
cd services/api-gateway && mvn spring-boot:run
```

### 2. Routing Test

```bash
# User Service üzerinden
curl http://localhost:8080/api/v1/users

# Company Service üzerinden
curl http://localhost:8080/api/v1/companies

# Contact Service üzerinden
curl http://localhost:8080/api/v1/contacts
```

### 3. Circuit Breaker Test

```bash
# Service'i durdur
# User Service'i kapat

# Fallback test et
curl http://localhost:8080/api/v1/users
# Response: "User Service is temporarily unavailable"
```

### 4. Rate Limiting Configuration (NEW - October 2025)

**Endpoint-Specific Rate Limits:**

| Endpoint                            | Rate Limit | Burst Capacity | Purpose                       |
| ----------------------------------- | ---------- | -------------- | ----------------------------- |
| `/api/v1/users/auth/check-contact`  | 10/min     | 15             | Email enumeration prevention  |
| `/api/v1/users/auth/login`          | 5/min      | 10             | Brute force prevention        |
| `/api/v1/users/auth/setup-password` | 3/min      | 5              | One-time operation protection |
| `/api/v1/contacts/find-by-value`    | 5/min      | 10             | Internal endpoint protection  |
| Other auth endpoints                | 20/min     | 30             | General protection            |
| Protected endpoints                 | 50/min     | 100            | Standard rate limit           |

**Configuration Example:**

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Auth Check Contact (Aggressive Rate Limiting)
        - id: user-service-auth-check
          uri: ${USER_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/v1/users/auth/check-contact
          filters:
            - StripPrefix=3
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@smartKeyResolver}"
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 15
                redis-rate-limiter.requestedTokens: 1

        # Login (Strict Rate Limiting)
        - id: user-service-auth-login
          uri: ${USER_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/v1/users/auth/login
          filters:
            - StripPrefix=3
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@smartKeyResolver}"
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
```

**Key Resolver (Smart IP/User-based):**

```java
@Bean
public KeyResolver smartKeyResolver() {
    return exchange -> {
        // Use X-User-Id if authenticated, otherwise use IP
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId != null) {
            return Mono.just(userId);
        }

        // Fallback to IP address for unauthenticated requests
        String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return Mono.just(ip);
    };
}
```

### 5. Rate Limiting Test

```bash
# Test login rate limit (5 req/min)
# 6th request should return 429 Too Many Requests
for i in {1..25}; do
  curl http://localhost:8080/api/v1/users
done
# 21. request'ten sonra 429 Too Many Requests almalısınız
```

---

## 📊 Monitoring

### Gateway Actuator Endpoints

```bash
# Health
curl http://localhost:8080/actuator/health

# Gateway Routes
curl http://localhost:8080/actuator/gateway/routes

# Circuit Breaker Status
curl http://localhost:8080/actuator/circuitbreakers
```

### Metrics

```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

---

## 🔧 Advanced Configuration

### Custom Route Predicates

```yaml
- id: user-service-premium
  uri: lb://user-service
  predicates:
    - Path=/api/v1/users/**
    - Header=X-Subscription-Type, premium
  filters:
    - PrefixPath=/premium
```

### Request Transformation

```yaml
filters:
  - RewritePath=/api/v1/(?<segment>.*), /$\{segment}
  - AddRequestParameter=source, gateway
```

### Response Caching

```yaml
filters:
  - name: LocalResponseCache
    args:
      size: 100MB
      timeToLive: 30s
```

---

**Last Updated:** 2025-10-09 20:15 UTC+1  
**Version:** 1.0.0  
**Status:** ✅ Active

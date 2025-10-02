# 🔍 Service Discovery Setup Guide

## Overview

Bu dokuman, Fabric Management System için Service Discovery (Eureka) kurulumunu anlatır.

---

## 🎯 Service Discovery Nedir?

Service Discovery, microservice'lerin birbirini dinamik olarak bulmasını sağlar:

- Hardcoded URL'ler yerine service name kullanımı
- Automatic load balancing
- Health check ve monitoring
- Dynamic scaling desteği

---

## 🚀 Eureka Server Kurulumu

### 1. Eureka Server Modülü Oluşturma

```bash
cd fabric-management-backend
mkdir -p services/eureka-server/src/main/java/com/fabricmanagement/eureka
mkdir -p services/eureka-server/src/main/resources
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

    <artifactId>eureka-server</artifactId>
    <name>Eureka Server</name>
    <description>Service Discovery Server</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 3. Application Class

```java
package com.fabricmanagement.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

### 4. application.yml

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
  server:
    enable-self-preservation: false
    eviction-interval-timer-in-ms: 15000

logging:
  level:
    com.netflix.eureka: INFO
    com.netflix.discovery: INFO
```

---

## 🔧 Client Configuration

### User Service Configuration

**pom.xml'e ekle:**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

**application.yml güncellemesi:**

```yaml
spring:
  application:
    name: user-service

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${server.port}
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
```

**Application class'a ekle:**

```java
@SpringBootApplication
@EnableDiscoveryClient  // Ekle
public class UserServiceApplication {
    // ...
}
```

### Company Service Configuration

Aynı şekilde Company Service için de yapılandır:

```yaml
spring:
  application:
    name: company-service

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${server.port}
```

### Contact Service Configuration

```yaml
spring:
  application:
    name: contact-service

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${server.port}
```

---

## 🌐 Feign Client Güncellemesi

### Öncesi (Hardcoded URL):

```java
@FeignClient(name = "user-service", url = "http://localhost:8081")
public interface UserServiceClient {
    // ...
}
```

### Sonrası (Service Discovery):

```java
@FeignClient(name = "user-service")  // URL kaldırıldı
public interface UserServiceClient {
    @GetMapping("/api/v1/users/{userId}")
    ApiResponse<UserResponse> getUser(@PathVariable UUID userId);
}
```

---

## 🐳 Docker Compose Entegrasyonu

**docker-compose.yml'e ekle:**

```yaml
services:
  eureka-server:
    build:
      context: ./services/eureka-server
      dockerfile: Dockerfile
    container_name: eureka-server
    ports:
      - "8761:8761"
    networks:
      - fabric-network
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  user-service:
    build:
      context: ./services/user-service
      dockerfile: Dockerfile
    container_name: user-service
    ports:
      - "8081:8081"
    depends_on:
      eureka-server:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/
    networks:
      - fabric-network
```

---

## ✅ Test Etme

### 1. Eureka Server'ı Başlat

```bash
cd services/eureka-server
mvn spring-boot:run
```

Tarayıcıda aç: http://localhost:8761

### 2. Service'leri Başlat

```bash
# Terminal 1
cd services/user-service
mvn spring-boot:run

# Terminal 2
cd services/company-service
mvn spring-boot:run

# Terminal 3
cd services/contact-service
mvn spring-boot:run
```

### 3. Eureka Dashboard'u Kontrol Et

http://localhost:8761 adresinde şunları göreceksiniz:

- USER-SERVICE (1 instance)
- COMPANY-SERVICE (1 instance)
- CONTACT-SERVICE (1 instance)

### 4. Service Communication Test

```bash
# User Service'den Company Service'e çağrı yapacak endpoint test et
curl http://localhost:8081/api/v1/users/company/{companyId}
```

---

## 🔒 Production Considerations

### Security

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://${EUREKA_USERNAME}:${EUREKA_PASSWORD}@eureka-server:8761/eureka/
```

### High Availability

```yaml
# Eureka Server 1
eureka:
  instance:
    hostname: eureka-server-1
  client:
    service-url:
      defaultZone: http://eureka-server-2:8761/eureka/

# Eureka Server 2
eureka:
  instance:
    hostname: eureka-server-2
  client:
    service-url:
      defaultZone: http://eureka-server-1:8761/eureka/
```

### Health Checks

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

---

## 📊 Monitoring

### Actuator Endpoints

- Health: `http://localhost:8081/actuator/health`
- Info: `http://localhost:8081/actuator/info`
- Metrics: `http://localhost:8081/actuator/metrics`

### Eureka Dashboard

- URL: `http://localhost:8761`
- Registered instances
- Instance status
- Metadata

---

## 🐛 Troubleshooting

### Service Kayıt Olmuyor

```yaml
# application.yml'de kontrol et
eureka:
  client:
    enabled: true # Aktif olduğundan emin ol
```

### Connection Refused

```bash
# Eureka Server çalışıyor mu?
curl http://localhost:8761/actuator/health

# Network erişimi var mı?
docker network inspect fabric-network
```

### Instance Duplicate

```yaml
# Unique instance ID kullan
eureka:
  instance:
    instance-id: ${spring.application.name}:${random.value}
```

---

**Son Güncelleme:** October 2, 2025
**Versiyon:** 1.0.0

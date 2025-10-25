# 🚀 QUICK START GUIDE

**Last Updated:** 2025-10-25

---

## ⚡ Hızlı Başlangıç (2 Adım)

### 1️⃣ Docker PostgreSQL Başlat

```bash
cd /Users/user/Coding/fabric-management/fabric-management-backend

# PostgreSQL & Redis başlat
docker-compose up -d postgres redis

# Kontrol et
docker-compose ps
```

**Beklenen Çıktı:**

```
NAME              STATUS                    PORTS
fabric-postgres   Up (healthy)             0.0.0.0:5433->5432/tcp
fabric-redis      Up (healthy)             0.0.0.0:6379->6379/tcp
```

### 2️⃣ Spring Boot Uygulaması Başlat

**IntelliJ IDEA:**

- `FabricManagementApplication.java` → Run ▶️

**Terminal:**

```bash
./mvnw spring-boot:run
```

**Beklenen Çıktı:**

```
✅ Flyway migration: V1...V6 applied
✅ HikariPool-1 - Start completed
✅ Tomcat started on port(s): 8080 (http)
✅ Started FabricManagementApplication
```

---

## 🧪 Test Endpoints

```bash
# Health check
curl http://localhost:8080/api/health

# Info
curl http://localhost:8080/api/info

# Swagger UI
open http://localhost:8080/swagger-ui.html

# Actuator
curl http://localhost:8080/actuator/health
```

---

## 🐛 Troubleshooting

### Problem: "Connection refused"

**Çözüm:** Docker PostgreSQL başlat

```bash
docker-compose up -d postgres redis
```

### Problem: "Port 8080 already in use"

**Çözüm:**

```bash
lsof -i :8080
kill -9 <PID>
```

### Problem: "Flyway migration failed"

**Çözüm:** Database temizle ve tekrar başlat

```bash
docker-compose down -v
docker-compose up -d postgres redis
```

---

**Detaylı test rehberi:** `TESTING_GUIDE.md`

# 🧪 Test Guide - Fabric Management System

Bu rehber, Modular Monolith yapıya geçiş sonrası ilk testleri nasıl yapacağınızı gösterir.

---

## ✅ Ön Gereksinimler

- ✅ Java 21 (JDK)
- ✅ Docker & Docker Compose
- ✅ IntelliJ IDEA veya Eclipse IDE
- ✅ Git

---

## 📋 Test Adımları

### 1️⃣ **Docker ile PostgreSQL Başlat**

```bash
# Terminal'de proje root dizininde
cd /Users/user/Coding/fabric-management/fabric-management-backend

# PostgreSQL ve Redis'i başlat (sadece gerekli servisleri)
docker-compose up -d postgres redis
```

**Kontrol:**

```bash
# Servisler çalışıyor mu?
docker-compose ps

# PostgreSQL loglarını kontrol et
docker-compose logs postgres

# PostgreSQL'e bağlan (opsiyonel)
docker exec -it fabric-management-backend-postgres-1 psql -U postgres -d fabric_management_db
```

---

### 2️⃣ **IntelliJ IDEA'da Projeyi Aç**

1. IntelliJ IDEA'yı aç
2. `File` → `Open`
3. `/Users/user/Coding/fabric-management/fabric-management-backend` klasörünü seç
4. `pom.xml` dosyasını Maven projesi olarak aç
5. Maven dependencies indirin:
   - Sağ tarafta `Maven` tab → `Reload All Maven Projects` (🔄 ikonu)

**Beklenen Durum:**

- ✅ Tüm dependencies indirildi
- ✅ Compile hataları yok
- ✅ `FabricManagementApplication.java` main class olarak tanınıyor

---

### 3️⃣ **Database Migration (Flyway) - Şimdilik Devre Dışı**

Henüz migration dosyaları oluşturmadık, bu yüzden geçici olarak devre dışı bırakacağız:

**application-local.yml** dosyasına ekle:

```yaml
spring:
  flyway:
    enabled: false # Geçici olarak devre dışı
  jpa:
    hibernate:
      ddl-auto: create # Geçici olarak tabloları otomatik oluştur
```

**NOT:** Migration dosyalarını oluşturduktan sonra `flyway.enabled: true` ve `ddl-auto: validate` yapacağız.

---

### 4️⃣ **Spring Boot Uygulamasını Başlat**

#### **IDE'den Başlatma (Önerilen):**

1. `FabricManagementApplication.java` dosyasını aç
2. `main` methodunun yanındaki **▶️ Run** butonuna tıkla
3. Veya `Run` → `Run 'FabricManagementApplication'`

#### **Terminal'den Başlatma (Alternatif):**

```bash
# Maven wrapper ile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Veya normal Maven ile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Beklenen Log Çıktısı:**

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.5)

2025-01-27 14:30:00 INFO  c.f.FabricManagementApplication - Starting FabricManagementApplication
2025-01-27 14:30:01 INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port(s): 8080 (http)
2025-01-27 14:30:01 INFO  c.f.FabricManagementApplication - Started FabricManagementApplication in 3.5 seconds
```

---

### 5️⃣ **Health Check Endpoints Test**

Uygulama başladıktan sonra tarayıcıda veya terminal'de test edin:

#### **Browser Test:**

1. http://localhost:8080/api/health
2. http://localhost:8080/api/info
3. http://localhost:8080/actuator/health
4. http://localhost:8080/swagger-ui.html

#### **cURL Test:**

```bash
# Health endpoint
curl http://localhost:8080/api/health

# Beklenen Response:
# {
#   "status": "UP",
#   "service": "Fabric Management System",
#   "architecture": "Modular Monolith",
#   "timestamp": "2025-01-27T11:30:00Z",
#   "version": "1.0.0"
# }

# Info endpoint
curl http://localhost:8080/api/info

# Actuator health
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics
```

---

### 6️⃣ **Database Connection Test**

PostgreSQL connection'ı test etmek için IDE'den:

1. IntelliJ IDEA → `Database` tab
2. `+` → `Data Source` → `PostgreSQL`
3. Bilgiler:
   - Host: `localhost`
   - Port: `5432`
   - Database: `fabric_management_db`
   - User: `postgres`
   - Password: `postgres`
4. `Test Connection` → Başarılı olmalı

---

## 🎯 Başarı Kriterleri

✅ Docker PostgreSQL & Redis çalışıyor  
✅ Maven dependencies yüklendi  
✅ Spring Boot uygulaması başladı (port 8080)  
✅ `/api/health` endpoint 200 OK dönüyor  
✅ `/api/info` endpoint modül bilgilerini gösteriyor  
✅ `/actuator/health` endpoint UP dönüyor  
✅ Swagger UI açılıyor (`/swagger-ui.html`)  
✅ Database connection başarılı  
✅ Loglarda hata yok

---

## 🐛 Troubleshooting

### Problem: Port 8080 already in use

**Çözüm:**

```bash
# Port'u kullanan process'i bul
lsof -i :8080

# Kill et
kill -9 <PID>

# Veya farklı port kullan
# application-local.yml:
server:
  port: 8081
```

### Problem: Database connection refused

**Çözüm:**

```bash
# PostgreSQL çalışıyor mu kontrol et
docker-compose ps postgres

# Logları kontrol et
docker-compose logs postgres

# Restart et
docker-compose restart postgres
```

### Problem: Maven dependencies inmiyor

**Çözüm:**

```bash
# Maven cache temizle
./mvnw clean install -U

# IntelliJ'de:
# File → Invalidate Caches → Invalidate and Restart
```

---

## 📊 Next Steps

Testler başarılı olduktan sonra:

1. ✅ Persistence layer tamamla (BaseEntity test et)
2. ✅ Events layer ekle (DomainEvent, EventPublisher)
3. ✅ Web layer ekle (ApiResponse, ExceptionHandler)
4. ✅ Database migrations oluştur (Flyway)
5. ✅ Company/User/Auth modüllerini geliştir

---

## 🔍 Useful Commands

```bash
# Tüm Docker servislerini başlat
docker-compose up -d

# Sadece database
docker-compose up -d postgres

# Logları takip et
docker-compose logs -f

# Servisleri durdur
docker-compose down

# Volumes ile birlikte temizle
docker-compose down -v

# Spring Boot hot reload için
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.devtools.restart.enabled=true"
```

---

**Happy Testing! 🚀**

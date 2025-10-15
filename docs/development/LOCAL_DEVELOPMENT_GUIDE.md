# 🚀 Local Development Guide - FAST ITERATION

**Version:** 1.0  
**Date:** October 14, 2025  
**Purpose:** Hızlı kod değişiklikleri için local development

---

## 🎯 Problem: Docker Build Çok Yavaş!

```bash
❌ Her kod değişikliğinde:
docker-compose build user-service  # 5-10 dakika!
docker-compose up -d               # 2-3 dakika!
# TOPLAM: 7-13 dakika! 😱
```

## ✅ Çözüm: Hybrid Development Mode

```
┌─────────────────────────────────────────────────┐
│  Infrastructure (Docker) - Bir kez başlat       │
│  ✅ PostgreSQL, Redis, Kafka                    │
│  ✅ API Gateway (değişmiyorsa)                  │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│  Microservices (Maven) - Hızlı restart!         │
│  🚀 mvn spring-boot:run (5-10 saniye!)          │
│  🔄 Kod değişti → Ctrl+C → Restart (10s)        │
└─────────────────────────────────────────────────┘
```

**Zaman Kazancı:** 7 dakika → 10 saniye = **42x DAHA HIZLI!** 🚀

---

## ⚡ QUICK START (5 Dakika!)

**Hemen başlamak istiyorsan:**

```bash
# 1. Infrastructure (Docker - 1 kez)
docker-compose up -d postgres redis kafka zookeeper

# 2. API Gateway (Terminal 1)
mvn spring-boot:run -pl services/api-gateway -Dspring-boot.run.profiles=local

# 3. User Service (Terminal 2)
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local

# 4. Contact Service (Terminal 3)
mvn spring-boot:run -pl services/contact-service -Dspring-boot.run.profiles=local

# 5. Company Service (Terminal 4)
mvn spring-boot:run -pl services/company-service -Dspring-boot.run.profiles=local

# 6. Test (Postman veya curl)
POST http://localhost:8080/api/v1/public/onboarding/register
```

**✅ HAZIR!** API Gateway üzerinden tüm endpoint'leri test edebilirsin! 🎉

---

## 📋 ADIM ADIM SETUP (Detaylı)

### **1. Infrastructure Başlat (Sadece 1 kez)**

```bash
# PostgreSQL + Redis + Kafka + API Gateway
docker-compose up -d postgres redis kafka zookeeper api-gateway

# Durumları kontrol et
docker-compose ps
```

**✅ Beklenen Çıktı:**

```
fabric-postgres      Up (healthy)
fabric-redis         Up (healthy)
fabric-kafka         Up (healthy)
fabric-zookeeper     Up (healthy)
fabric-api-gateway   Up (healthy)
```

---

### **2. Servisler'i Local Çalıştır (Hepsi Maven'le!)**

> ✅ **TÜM SERVİSLERİ** (Gateway dahil) Maven'le çalıştırabilirsin!  
> 🎯 **POSTMAN'den API Gateway üzerinden test edersin!**

#### **Terminal 1: API Gateway (Zorunlu - Routing için)**

```bash
cd /Users/user/Coding/fabric-management/fabric-management-backend

# API Gateway başlat (localhost:8080)
mvn spring-boot:run -pl services/api-gateway -Dspring-boot.run.profiles=local
```

#### **Terminal 2: User Service**

```bash
# User Service başlat (localhost:8081)
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local
```

#### **Terminal 3: Contact Service**

```bash
# Contact Service başlat (localhost:8082)
mvn spring-boot:run -pl services/contact-service -Dspring-boot.run.profiles=local
```

#### **Terminal 4: Company Service**

```bash
# Company Service başlat (localhost:8083)
mvn spring-boot:run -pl services/company-service -Dspring-boot.run.profiles=local
```

**✅ Başlatma Sırası:**

1. **Infrastructure (Docker)** → PostgreSQL, Redis, Kafka
2. **API Gateway (Maven)** → Routing katmanı
3. **Microservices (Maven)** → İstediğin sırada (paralel başlayabilir!)

**🎯 POSTMAN Testi:**

```bash
# ✅ API Gateway üzerinden test et:
POST http://localhost:8080/api/v1/public/onboarding/register

# ✅ Gateway, microservisleri localhost:8081/8082/8083'te bulur!
```

---

## 🔧 Environment Variables (Opsiyonel)

**Önceden Set Etmek İsterseniz:**

```bash
# ~/.bashrc veya ~/.zshrc'ye ekle:
export INTERNAL_API_KEY="local-dev-internal-key-2024"
export CONTACT_SERVICE_URL="http://localhost:8082"
export COMPANY_SERVICE_URL="http://localhost:8083"
export USER_SERVICE_URL="http://localhost:8081"

# Reload shell
source ~/.bashrc
```

**⚠️ VEYA:** Hiçbir şey yapma! application-local.yml'de **tüm defaults var!** ✅

---

## ⚡ HIZLI GELIŞTIRME WORKFLOW

### **Senaryo: User Service'de Kod Değişikliği**

```bash
# 1. Kod değiştir (IDE'de)
# 2. Terminal'de Ctrl+C (servis durdur - 1s)
# 3. Tekrar başlat:
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local
# 4. Ready in: 10-15 saniye! 🚀
```

**Docker ile Karşılaştırma:**

```
Docker:  Kod değiştir → Build (8 min) → Up (2 min) = 10 dakika ❌
Maven:   Kod değiştir → Restart (10s) = 10 saniye ✅

HIZLANMA: 60x DAHA HIZLI! 🚀
```

---

## 🌐 Servis URL'leri (Local Dev)

| Servis              | Docker Container      | Local Maven           | Açıklama                     |
| ------------------- | --------------------- | --------------------- | ---------------------------- |
| **API Gateway**     | http://localhost:8080 | N/A                   | Docker'da (değişmez genelde) |
| **User Service**    | http://localhost:8081 | http://localhost:8081 | Maven'le çalıştırıyorsan     |
| **Contact Service** | http://localhost:8082 | http://localhost:8082 | Maven'le çalıştırıyorsan     |
| **Company Service** | http://localhost:8083 | http://localhost:8083 | Maven'le çalıştırıyorsan     |

---

## 🔍 Hangi Servisi Local Çalıştırmalıyım?

### **Senaryoya Göre:**

#### **Senaryo 1: Sadece User Service Geliştiriyorum** 🎯

**En Hızlı! (10 saniye restart)**

```bash
# 1. Infrastructure + Diğer Servisler (Docker - 1 kez)
docker-compose up -d postgres redis kafka zookeeper contact-service company-service

# 2. API Gateway (Maven - Terminal 1)
mvn spring-boot:run -pl services/api-gateway -Dspring-boot.run.profiles=local

# 3. User Service (Maven - Terminal 2)
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local

# 4. Test via API Gateway
POST http://localhost:8080/api/v1/public/onboarding/register
```

**✅ Avantaj:** User Service'de kod değişti → Ctrl+C → Restart (10s!)

---

#### **Senaryo 2: Tüm Mikroservisler Local (Full Control)** 🚀

**Tam Kontrol! (Hepsini debug edebilirsin)**

```bash
# 1. Sadece Infrastructure (Docker)
docker-compose up -d postgres redis kafka zookeeper

# 2. API Gateway (Maven - Terminal 1)
mvn spring-boot:run -pl services/api-gateway -Dspring-boot.run.profiles=local

# 3. User Service (Maven - Terminal 2)
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local

# 4. Contact Service (Maven - Terminal 3)
mvn spring-boot:run -pl services/contact-service -Dspring-boot.run.profiles=local

# 5. Company Service (Maven - Terminal 4)
mvn spring-boot:run -pl services/company-service -Dspring-boot.run.profiles=local

# 6. Test via API Gateway
POST http://localhost:8080/api/v1/public/onboarding/register
```

**✅ Avantaj:** Tüm servislerde breakpoint koyabilirsin! IDE debug mode!

---

#### **Senaryo 3: Hybrid - API Gateway Docker'da** 🔀

**Gateway değişmeyecekse (routing stable)**

```bash
# 1. Infrastructure + API Gateway (Docker)
docker-compose up -d postgres redis kafka zookeeper api-gateway

# 2. Mikroservisler (Maven)
# Terminal 1:
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local

# Terminal 2:
mvn spring-boot:run -pl services/contact-service -Dspring-boot.run.profiles=local

# Terminal 3:
mvn spring-boot:run -pl services/company-service -Dspring-boot.run.profiles=local

# 3. Test via Docker API Gateway
POST http://localhost:8080/api/v1/public/onboarding/register
```

**⚠️ DİKKAT:** Docker API Gateway, localhost'taki Maven servisleri **BULAMAZ!**

- Docker network: `user-service:8081` ❌ (Maven'de bu host yok)
- Host network: `localhost:8081` ✅ (ama Docker'dan ulaşılamaz)

**ÇÖZÜM:** API Gateway de Maven'le çalıştır! (Senaryo 2)

---

#### **Senaryo 4: Full Docker (Gün Sonu Test)** 🐳

**Production-like test**

```bash
# Tüm değişiklikleri commit et, sonra:
mvn clean package -DskipTests
docker-compose build  # Sadece değişenleri build eder
docker-compose up -d

# Test
POST http://localhost:8080/api/v1/public/onboarding/register
```

**✅ Ne Zaman:** Gün sonu, merge öncesi, production deploy öncesi

---

## 🎯 application-local.yml Özellikleri

### ✅ **TAM YETKİNLİKTE:**

```yaml
# ✅ Database: localhost:5433 (Docker'daki PostgreSQL)
# ✅ Redis: localhost:6379 (Docker'daki Redis)
# ✅ Kafka: localhost:9092 (Docker'daki Kafka)
# ✅ Service URLs: localhost defaults (CONTACT_SERVICE_URL, etc.)
# ✅ INTERNAL_API_KEY: "local-dev-internal-key-2024"
# ✅ JWT Secret: Hard-coded (local dev için OK)
# ✅ Resilience4j: application.yml'den inherit eder
# ✅ Logging: DEBUG mode (detaylı log)
```

### **Hiçbir Env Variable Set Etmeden Çalışır!** ✅

```bash
# Direkt çalışır (ZERO setup):
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local
```

---

## 🧪 Test Workflow

### **1. Unit Test (Çok Hızlı)**

```bash
# Tek servis test
mvn test -pl services/user-service

# Hızlı: 30-60 saniye
```

### **2. Integration Test (Local)**

```bash
# Infrastructure başlat
docker-compose up -d postgres redis kafka

# Servisi local çalıştır
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local

# Postman ile test et
POST http://localhost:8080/api/v1/public/onboarding/register
```

### **3. Full E2E Test (Docker)**

```bash
# Gün sonunda - tüm servisler Docker'da
docker-compose build
docker-compose up -d
```

---

## 🔥 Hot Reload (Opsiyonel)

### **Spring Boot DevTools ile Otomatik Restart:**

**pom.xml'e ekle:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

**IntelliJ IDEA:**

```
1. Settings → Build → Compiler → Build project automatically ✅
2. Settings → Advanced → Allow auto-make to start... ✅
3. Kod değişir → Otomatik restart! (3-5s) 🚀
```

---

## 🐛 Troubleshooting

### **Problem: Port Already in Use**

```bash
# Servis zaten Docker'da çalışıyor
docker-compose stop user-service

# Şimdi Maven'le başlat
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local
```

### **Problem: Database Connection Failed**

```bash
# PostgreSQL Docker'da çalışıyor mu?
docker-compose ps postgres

# Eğer down ise:
docker-compose up -d postgres
```

### **Problem: Feign Client Connection Refused**

```bash
# Hedef servis çalışıyor mu?
curl http://localhost:8082/actuator/health

# Eğer down ise, 2 seçenek:
# Opsiyon 1: Docker'da başlat
docker-compose up -d contact-service

# Opsiyon 2: Maven'le başlat (başka terminal)
mvn spring-boot:run -pl services/contact-service -Dspring-boot.run.profiles=local
```

---

## 📊 Performans Karşılaştırması

| Operasyon           | Docker     | Maven Local  | Kazanç     |
| ------------------- | ---------- | ------------ | ---------- |
| **İlk Başlatma**    | 3-5 dakika | 10-15 saniye | 18x        |
| **Kod Değişikliği** | 10 dakika  | 10 saniye    | **60x** 🚀 |
| **Unit Test**       | 5 dakika   | 30 saniye    | 10x        |
| **Hot Reload**      | ❌ Yok     | 3-5 saniye   | ♾️         |

**Günlük Kazanç (10 kod değişikliği):**

- Docker: 10 × 10 dakika = **100 dakika** (1.7 saat) ❌
- Maven: 10 × 10 saniye = **100 saniye** (1.7 dakika) ✅
- **KAZANÇ: 1.5 SAAT / GÜN!** 🎉

---

## 🎯 Önerilen Workflow

### **Sabah (Günlük Setup):**

```bash
# 1. Infrastructure başlat (5 dakika, günde 1 kez)
docker-compose up -d postgres redis kafka zookeeper api-gateway

# 2. Diğer servisleri Docker'da tut (değişmeyecekse)
docker-compose up -d contact-service company-service
```

### **Gün Boyunca (Kod Geliştirme):**

```bash
# User Service geliştiriyorsan:
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local

# Kod değişti → Ctrl+C → Yeniden başlat (10s)
```

### **Akşam (Final Test):**

```bash
# Tüm değişiklikleri test et
mvn clean package -DskipTests
docker-compose build user-service  # Sadece değişeni build et
docker-compose up -d
```

---

## ✅ Checklist: Local Dev Ready?

- [x] ✅ PostgreSQL Docker'da çalışıyor (`docker-compose ps postgres`)
- [x] ✅ Redis Docker'da çalışıyor (`docker-compose ps redis`)
- [x] ✅ Kafka Docker'da çalışıyor (`docker-compose ps kafka`)
- [x] ✅ API Gateway Docker'da çalışıyor (opsiyonel)
- [x] ✅ application-local.yml mevcut (tüm servislerde)
- [x] ✅ INTERNAL_API_KEY default değeri var
- [x] ✅ Service URL'leri localhost defaults var
- [x] ✅ Maven installed (`mvn --version`)
- [x] ✅ Java 21 installed (`java --version`)

**Hepsi ✅ ise HAZIRSIN!** 🚀

---

## 💡 Pro Tips

### **1. Sadece Değişen Servis Docker Build Et**

```bash
# ❌ YAVAŞ: Tüm servisleri build et
docker-compose build

# ✅ HIZLI: Sadece user-service build et
docker-compose build user-service
docker-compose up -d user-service
```

### **2. Paralel Terminal Kullan**

```bash
# Terminal 1: User Service logs
mvn spring-boot:run -pl services/user-service -Dspring-boot.run.profiles=local

# Terminal 2: Contact Service logs
mvn spring-boot:run -pl services/contact-service -Dspring-boot.run.profiles=local

# Terminal 3: Test execution
curl -X POST http://localhost:8080/api/v1/public/onboarding/register ...
```

### **3. IntelliJ Run Configuration**

```
Name: User Service (Local)
Main class: com.fabricmanagement.user.UserServiceApplication
VM options: -Dspring.profiles.active=local
Working directory: $MODULE_DIR$
Use classpath of module: user-service

✅ Kaydet → Run button ile 1 tık başlatma! 🎯
```

### **4. Log Filtering**

```bash
# Sadece kendi kodunu gör
mvn spring-boot:run ... | grep "com.fabricmanagement"

# Hataları gör
mvn spring-boot:run ... | grep -i "error\|exception\|failed"
```

---

## 🎓 Best Practices

### **DO ✅**

- Infrastructure hep Docker'da tut (PostgreSQL, Redis, Kafka)
- Geliştirdiğin servisi Maven'le çalıştır
- Diğer servisleri Docker'da tut (dependency olarak)
- Gün sonunda final test Docker'da yap

### **DON'T ❌**

- Tüm servisleri Maven'le çalıştırma (port conflict!)
- PostgreSQL'i local install etme (Docker yeterli)
- Her küçük değişiklikte Docker build yapma
- application-local.yml'e production secret koyma

---

## 📚 İlgili Dokümantasyon

- [GETTING_STARTED.md](GETTING_STARTED.md) - Temel setup
- [microservices_api_standards.md](microservices_api_standards.md) - API standartları
- [ENVIRONMENT_VARIABLES.md](../deployment/ENVIRONMENT_VARIABLES.md) - Env var guide

---

## 🆘 Yardım

**Sorun mu var?**

1. application-local.yml'yi kontrol et (defaults doğru mu?)
2. Infrastructure healthy mi? (`docker-compose ps`)
3. Port conflict var mı? (`lsof -i :8081`)

**Hala çözülmedi mi?**
→ [troubleshooting/COMMON_ISSUES_AND_SOLUTIONS.md](../troubleshooting/COMMON_ISSUES_AND_SOLUTIONS.md)

---

**HAPPY FAST CODING!** 🚀💨

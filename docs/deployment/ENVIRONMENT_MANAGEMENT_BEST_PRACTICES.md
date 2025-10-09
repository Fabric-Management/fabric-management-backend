# Environment Management Best Practices

## 📋 İçindekiler

1. [Mevcut Durum](#mevcut-durum)
2. [Temel Prensipler](#temel-prensipler)
3. [Gelecek İyileştirmeler](#gelecek-iyileştirmeler)
4. [Güvenlik Önerileri](#güvenlik-önerileri)
5. [Ortam Bazlı Yapılandırma](#ortam-bazlı-yapılandırma)
6. [CI/CD Entegrasyonu](#cicd-entegrasyonu)

---

## 🎯 Mevcut Durum

### Yapılandırma Dosyaları

Projemizde environment değişkenleri için aşağıdaki yapı kullanılmaktadır:

```
.
├── .env                    # Aktif environment değişkenleri (Git'e eklenmez)
├── .env.example           # Template dosyası (Git'e eklenir)
├── docker-compose.yml     # Altyapı servisleri için
└── docker-compose-complete.yml  # Tüm mikroservisler ile
```

### Environment Değişkenleri Kullanımı

Docker Compose dosyalarında environment değişkenleri şu formatta kullanılır:

```yaml
environment:
  POSTGRES_USER: ${POSTGRES_USER:-fabric_user}
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-fabric_password}
```

**Format:** `${VARIABLE_NAME:-default_value}`

- `.env` dosyasında tanımlıysa o değeri kullanır
- Tanımlı değilse default değeri kullanır

---

## 🔐 Temel Prensipler

### 1. 12-Factor App Metodolojisi

Environment configuration'ı [12-Factor App](https://12factor.net/) prensiplerine göre yönetiyoruz:

- ✅ **Separation of Config from Code:** Konfigürasyon koddan ayrı
- ✅ **Environment Parity:** Tüm ortamlar aynı yapıyı kullanır
- ✅ **No Secrets in Code:** Hassas bilgiler kodda değil, environment'ta

### 2. Güvenlik İlkeleri

- ❌ **Asla `.env` dosyasını Git'e eklemeyin**
- ✅ `.env.example` kullanarak template sağlayın
- ✅ Hassas bilgileri şifreleyin veya secret manager kullanın
- ✅ Production ortamında güçlü şifreler kullanın

### 3. Dokümantasyon

- Her environment değişkeni `.env.example`'da açıklanmalı
- Değişkenlerin alabileceği değerler belirtilmeli
- Varsayılan değerler güvenli olmalı (production için değil)

---

## 🚀 Gelecek İyileştirmeler

### Öncelik 1: Ortam Bazlı Konfigürasyon

#### Amaç

Farklı ortamlar (development, staging, production) için ayrı environment dosyaları oluşturmak.

#### Yapılacaklar

**1. Ortam bazlı dosyalar oluştur:**

```bash
.env.development    # Yerel geliştirme
.env.staging        # Test ortamı
.env.production     # Production ortamı
```

**2. Docker Compose profiles kullan:**

```yaml
services:
  user-service:
    profiles: ["development", "production"]
    # ...
```

**3. Profil seçimi:**

```bash
# Development
docker-compose --profile development up

# Production
docker-compose --profile production up
```

#### Faydaları

- Ortamlar arası geçiş kolaylaşır
- Her ortam için özel ayarlar
- Hata riski azalır

#### Tahmini Süre

- **Uygulama:** 2-3 saat
- **Test:** 1 saat
- **Dokümantasyon:** 30 dakika

---

### Öncelik 2: Secret Management Entegrasyonu

#### Amaç

Hassas bilgileri (şifreler, API keyleri) güvenli bir şekilde yönetmek.

#### Önerilen Çözümler

**A. HashiCorp Vault (Self-hosted)**

```yaml
# docker-compose.yml
services:
  vault:
    image: vault:latest
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: myroot
    ports:
      - "8200:8200"
```

**장점:**

- Merkezi secret yönetimi
- Encryption at rest
- Dynamic secrets
- Audit logging

**Uygulama Adımları:**

1. Vault containerını ekle
2. Spring Cloud Vault entegrasyonu
3. Application.yml'de vault configuration
4. Secrets'i Vault'a taşı

**Tahmini Maliyet:** Ücretsiz (self-hosted)
**Süre:** 1-2 gün

---

**B. AWS Secrets Manager (Cloud)**

```java
@Configuration
public class AwsSecretsConfig {
    @Bean
    public SecretsManagerClient secretsManager() {
        return SecretsManagerClient.builder()
            .region(Region.EU_WEST_1)
            .build();
    }
}
```

**장점:**

- Managed service
- AWS ekosistemi entegrasyonu
- Otomatik rotation
- Fine-grained access control

**Uygulama Adımları:**

1. AWS Secrets Manager'da secret oluştur
2. Spring Cloud AWS entegrasyonu
3. IAM permissions ayarla
4. Application'da secrets çek

**Tahmini Maliyet:** ~$0.40/secret/month + API calls
**Süre:** 4-6 saat

---

**C. Docker Secrets (Docker Swarm)**

```yaml
# docker-compose.yml
secrets:
  db_password:
    external: true

services:
  postgres:
    secrets:
      - db_password
    environment:
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
```

**장점:**

- Docker native çözüm
- Basit kullanım
- Ücretsiz

**Dezavantajları:**

- Sadece Swarm mode
- Sınırlı özellikler

---

### Öncelik 3: Configuration Validation

#### Amaç

Uygulama başlamadan environment değişkenlerini validate etmek.

#### Uygulama

**1. Spring Boot'ta validation:**

```java
@Configuration
@Validated
public class DatabaseConfig {

    @NotNull(message = "POSTGRES_HOST must be set")
    @Value("${POSTGRES_HOST}")
    private String host;

    @Min(value = 1024, message = "POSTGRES_PORT must be >= 1024")
    @Max(value = 65535, message = "POSTGRES_PORT must be <= 65535")
    @Value("${POSTGRES_PORT}")
    private Integer port;

    @NotBlank(message = "POSTGRES_PASSWORD cannot be empty")
    @Size(min = 12, message = "POSTGRES_PASSWORD must be at least 12 characters")
    @Value("${POSTGRES_PASSWORD}")
    private String password;
}
```

**2. Startup script ile validation:**

```bash
#!/bin/bash
# validate-env.sh

required_vars=(
    "POSTGRES_HOST"
    "POSTGRES_DB"
    "POSTGRES_USER"
    "POSTGRES_PASSWORD"
    "REDIS_HOST"
    "KAFKA_BOOTSTRAP_SERVERS"
)

for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "ERROR: $var is not set"
        exit 1
    fi
done

echo "✅ All required environment variables are set"
```

#### Faydaları

- Erken hata yakalama
- Açıklayıcı hata mesajları
- Production sorunlarını önler

**Süre:** 3-4 saat

---

### Öncelik 4: Environment Variable Encryption

#### Amaç

`.env` dosyasındaki hassas bilgileri şifrelemek.

#### Önerilen Araçlar

**A. git-crypt**

```bash
# Kurulum
brew install git-crypt

# GPG key ile init
git-crypt init

# .env dosyasını şifrele
echo ".env filter=git-crypt diff=git-crypt" >> .gitattributes
git-crypt add .env

# Artık .env commit edilebilir (şifrelenmiş olarak)
```

**장점:**

- Transparent encryption/decryption
- Git workflow'a entegre
- Team collaboration kolay

---

**B. SOPS (Secrets OPerationS)**

```bash
# Kurulum
brew install sops

# .env dosyasını şifrele
sops -e .env > .env.encrypted

# Deşifrele
sops -d .env.encrypted > .env
```

**장점:**

- Multiple key management (GPG, AWS KMS, GCP KMS, Azure Key Vault)
- YAML/JSON/ENV format desteği
- Partial encryption (sadece value'lar)

---

**C. Ansible Vault**

```bash
# Şifrele
ansible-vault encrypt .env

# Deşifrele
ansible-vault decrypt .env

# Düzenle (şifrelenmiş olarak)
ansible-vault edit .env
```

**장점:**

- Ansible ile entegre
- Role-based access
- Basit kullanım

**Öneri:** git-crypt (küçük takımlar için) veya SOPS (cloud entegrasyonu için)

---

### Öncelik 5: Dynamic Configuration

#### Amaç

Runtime'da configuration değişikliği yapabilmek (restart olmadan).

#### Spring Cloud Config Entegrasyonu

**1. Config Server:**

```yaml
# config-server/docker-compose.yml
services:
  config-server:
    image: hyness/spring-cloud-config-server
    ports:
      - "8888:8888"
    environment:
      SPRING_CLOUD_CONFIG_SERVER_GIT_URI: https://github.com/yourorg/config-repo
    volumes:
      - ./config-repo:/config
```

**2. Client entegrasyonu:**

```yaml
# application.yml
spring:
  cloud:
    config:
      uri: http://config-server:8888
      fail-fast: true
```

**3. Refresh endpoint:**

```bash
# Configuration'ı yeniden yükle
curl -X POST http://localhost:8081/actuator/refresh
```

#### Faydaları

- Centralized configuration
- Restart gerektirmez
- Feature flag yönetimi
- A/B testing

**Süre:** 2-3 gün

---

## 🔒 Güvenlik Önerileri

### Production Checklist

- [ ] Tüm default şifreler değiştirildi
- [ ] JWT secret key 256-bit ve güçlü
- [ ] Database şifreleri 16+ karakter, karmaşık
- [ ] `.env` dosyası `.gitignore`'da
- [ ] Production credentials sadece production sunucusunda
- [ ] Secrets rotation politikası uygulandı
- [ ] Access logs aktif
- [ ] Encryption at rest aktif (database, secrets)
- [ ] TLS/SSL sertifikaları geçerli

### Şifre Gereksinimleri

**Development:**

- Minimum 8 karakter
- Büyük/küçük harf + rakam

**Staging:**

- Minimum 12 karakter
- Büyük/küçük harf + rakam + özel karakter

**Production:**

- Minimum 16 karakter
- Büyük/küçük harf + rakam + özel karakter
- Düzenli rotation (90 gün)
- Password manager ile yönetim

### Secrets Rotation

```bash
# Otomatik rotation scripti
#!/bin/bash
# rotate-secrets.sh

# Yeni şifre oluştur
NEW_PASSWORD=$(openssl rand -base64 32)

# Database'de güncelle
psql -c "ALTER USER fabric_user WITH PASSWORD '$NEW_PASSWORD';"

# Secrets manager'da güncelle
aws secretsmanager update-secret \
    --secret-id fabric-db-password \
    --secret-string "$NEW_PASSWORD"

# Servisleri restart et (yeni şifreyi alsınlar)
docker-compose restart
```

---

## 🌍 Ortam Bazlı Yapılandırma

### Development Environment

```bash
# .env.development
SPRING_PROFILES_ACTIVE=development
POSTGRES_HOST=localhost
POSTGRES_PORT=5433
LOG_LEVEL=DEBUG

# Debug özellikleri aktif
SPRING_DEVTOOLS_ENABLED=true
FLYWAY_CLEAN_DISABLED=false
```

**Özellikler:**

- Detaylı logging
- Hot reload aktif
- Database clean izni var
- Mock services kullanılabilir

---

### Staging Environment

```bash
# .env.staging
SPRING_PROFILES_ACTIVE=staging
POSTGRES_HOST=staging-db.internal
POSTGRES_PORT=5432
LOG_LEVEL=INFO

# Production benzeri ama izole
FLYWAY_CLEAN_DISABLED=true
```

**Özellikler:**

- Production benzeri setup
- Test için izole
- Real services
- Performans testleri

---

### Production Environment

```bash
# .env.production
SPRING_PROFILES_ACTIVE=production
POSTGRES_HOST=prod-db.internal
POSTGRES_PORT=5432
LOG_LEVEL=WARN

# Maximum güvenlik
FLYWAY_CLEAN_DISABLED=true
ACTUATOR_ENDPOINTS_SENSITIVE=true
```

**Özellikler:**

- Minimum logging
- Maximum güvenlik
- High availability
- Monitoring aktif
- Backup/recovery

---

## 🔄 CI/CD Entegrasyonu

### GitHub Actions Örneği

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v2

      - name: Setup Environment
        run: |
          echo "POSTGRES_USER=${{ secrets.POSTGRES_USER }}" >> .env
          echo "POSTGRES_PASSWORD=${{ secrets.POSTGRES_PASSWORD }}" >> .env
          echo "JWT_SECRET=${{ secrets.JWT_SECRET }}" >> .env

      - name: Validate Environment
        run: ./scripts/validate-env.sh

      - name: Deploy
        run: docker-compose up -d
```

### Environment Variables Hierarchy

```
1. System Environment (highest priority)
   ↓
2. .env file
   ↓
3. docker-compose.yml environment
   ↓
4. application.yml defaults (lowest priority)
```

---

## 📊 Monitoring & Alerting

### Önerilen İyileştirmeler

**1. Configuration Changes Tracking**

```java
@EventListener(EnvironmentChangeEvent.class)
public void onEnvironmentChange(EnvironmentChangeEvent event) {
    log.info("Configuration changed: {}", event.getKeys());
    // Metrics'e gönder
    metricsService.recordConfigChange(event);
}
```

**2. Secret Expiration Alerts**

```bash
# Alert scripti
#!/bin/bash
SECRET_AGE_DAYS=$(( ($(date +%s) - $(stat -f %B .env)) / 86400 ))

if [ $SECRET_AGE_DAYS -gt 90 ]; then
    echo "⚠️  Secrets are $SECRET_AGE_DAYS days old. Rotation recommended!"
    # Slack webhook çağır
fi
```

**3. Environment Drift Detection**

```python
# Ortamlar arasındaki farkları tespit et
import difflib

dev_env = parse_env('.env.development')
prod_env = parse_env('.env.production')

diff = difflib.unified_diff(dev_env, prod_env)
if diff:
    alert("Environment drift detected!")
```

---

## 📚 Kaynaklar ve Referanslar

### Dokümantasyon

- [12-Factor App](https://12factor.net/)
- [Spring Boot External Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Docker Compose Environment Variables](https://docs.docker.com/compose/environment-variables/)

### Araçlar

- [HashiCorp Vault](https://www.vaultproject.io/)
- [AWS Secrets Manager](https://aws.amazon.com/secrets-manager/)
- [SOPS](https://github.com/mozilla/sops)
- [git-crypt](https://github.com/AGWA/git-crypt)

### Best Practices

- [OWASP Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [Cloud Security Alliance Guidelines](https://cloudsecurityalliance.org/)

---

## 🎯 Uygulama Roadmap

### Q1 2025 (Öncelik: Yüksek)

- ✅ `.env` ve `.env.example` yapısı oluşturuldu
- ✅ Docker Compose `.env` entegrasyonu tamamlandı
- ✅ `.gitignore` güncellendi
- ⏳ Ortam bazlı configuration dosyaları (.env.development, .env.production)
- ⏳ Environment validation scripti

### Q2 2025 (Öncelik: Orta)

- ⏳ Secret Management çözümü seçimi ve implementasyonu
- ⏳ Configuration validation (Spring Boot)
- ⏳ CI/CD pipeline'da environment yönetimi
- ⏳ Secrets rotation politikası ve otomasyonu

### Q3 2025 (Öncelik: Düşük)

- ⏳ Spring Cloud Config entegrasyonu
- ⏳ Dynamic configuration
- ⏳ Environment drift monitoring
- ⏳ Comprehensive documentation

---

## 📝 Notlar

### Güncel Durum (01.10.2025)

- ✅ Temel `.env` yapısı kuruldu
- ✅ Docker Compose dosyaları güncellendi
- ✅ Template (`.env.example`) oluşturuldu
- ⚠️ Eski `.env` dosyası yedeklendi (tarih damgalı)

### Bilinen Sorunlar

- Henüz secret management yok (şifreler plain text)
- Ortam bazlı ayrım yok (tek `.env` dosyası)
- Validation mekanizması yok

### Sonraki Adımlar

1. Ortam bazlı dosyalar oluştur
2. Validation scripti yaz
3. Secret management çözümü araştır
4. CI/CD entegrasyonu planla

---

**Last Updated:** 2025-10-09 20:15 UTC+1  
**Version:** 1.0  
**Status:** ✅ Active  
**Prepared By:** AI Assistant  
**Project:** Fabric Management System

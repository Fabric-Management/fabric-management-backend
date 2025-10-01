# Company Service

Fabric Management System'in şirket yönetimi mikroservisi.

## 🎯 Özellikler

### Temel Fonksiyonlar

- ✅ Şirket CRUD işlemleri (Create, Read, Update, Delete)
- ✅ Multi-tenancy desteği
- ✅ Şirket durumu yönetimi (Active, Inactive, Suspended, Deleted)
- ✅ Şirket ayarları ve tercihleri yönetimi
- ✅ Abonelik planı yönetimi
- ✅ Şirket-kullanıcı ilişki yönetimi

### Mimari Özellikler

- ✅ **Clean Architecture** - Katmanlı mimari
- ✅ **CQRS Pattern** - Command/Query ayrımı
- ✅ **Event Sourcing** - Domain event'leri
- ✅ **Domain-Driven Design** - Aggregate, Value Object, Domain Event
- ✅ **Multi-tenancy** - Tenant isolation
- ✅ **Caching** - Redis cache desteği
- ✅ **Event Publishing** - Kafka entegrasyonu

### Servis Entegrasyonları

- ✅ **Contact Service** - Şirket iletişim bilgileri için
- ✅ **User Service** - Şirket kullanıcıları için

## 📐 Mimari

```
company-service/
├── api/                              # REST Controllers
│   ├── CompanyController            # Ana şirket endpoint'leri
│   ├── CompanyContactController     # Şirket iletişim endpoint'leri
│   ├── CompanyUserController        # Şirket kullanıcı endpoint'leri
│   └── GlobalExceptionHandler       # Global hata yönetimi
│
├── application/                      # Uygulama katmanı
│   ├── command/                     # CQRS Command'ları
│   │   ├── CreateCompanyCommand
│   │   ├── UpdateCompanyCommand
│   │   ├── DeleteCompanyCommand
│   │   ├── UpdateCompanySettingsCommand
│   │   ├── UpdateSubscriptionCommand
│   │   ├── ActivateCompanyCommand
│   │   └── DeactivateCompanyCommand
│   │
│   ├── command/handler/             # Command Handler'lar
│   │   ├── CreateCompanyCommandHandler
│   │   ├── UpdateCompanyCommandHandler
│   │   └── ...
│   │
│   ├── query/                       # CQRS Query'leri
│   │   ├── GetCompanyQuery
│   │   ├── ListCompaniesQuery
│   │   ├── SearchCompaniesQuery
│   │   └── GetCompaniesByStatusQuery
│   │
│   ├── query/handler/               # Query Handler'lar
│   │   ├── GetCompanyQueryHandler
│   │   ├── ListCompaniesQueryHandler
│   │   └── ...
│   │
│   ├── dto/                         # Data Transfer Objects
│   │   ├── CreateCompanyRequest
│   │   ├── UpdateCompanyRequest
│   │   ├── CompanyResponse
│   │   └── ...
│   │
│   └── service/                     # Application Services
│       ├── CompanyService           # Ana service
│       ├── CompanyContactService    # Contact entegrasyonu
│       └── CompanyUserService       # User entegrasyonu
│
├── domain/                           # Domain katmanı
│   ├── aggregate/
│   │   └── Company                  # Company Aggregate Root
│   │
│   ├── event/                       # Domain Events
│   │   ├── CompanyCreatedEvent
│   │   ├── CompanyUpdatedEvent
│   │   └── CompanyDeletedEvent
│   │
│   ├── valueobject/                 # Value Objects
│   │   ├── CompanyName
│   │   ├── CompanyStatus
│   │   ├── CompanyType
│   │   └── Industry
│   │
│   └── exception/                   # Domain Exceptions
│       ├── CompanyNotFoundException
│       ├── CompanyAlreadyExistsException
│       ├── UnauthorizedCompanyAccessException
│       └── MaxUsersLimitException
│
└── infrastructure/                   # Altyapı katmanı
    ├── repository/
    │   └── CompanyRepository        # JPA Repository
    │
    ├── client/                      # Feign Clients
    │   ├── ContactServiceClient
    │   ├── UserServiceClient
    │   └── dto/
    │
    ├── messaging/                   # Kafka
    │   ├── CompanyEventPublisher
    │   └── DomainEventPublisher
    │
    ├── persistence/
    │   └── CompanyEventStore        # Event Store
    │
    ├── security/                    # Security & Multi-tenancy
    │   ├── TenantContext
    │   └── TenantInterceptor
    │
    └── config/                      # Configuration
        ├── WebConfig
        └── SecurityConfig
```

## 🚀 API Endpoints

### Şirket Yönetimi

#### Şirket Oluştur

```http
POST /api/companies
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Acme Corporation",
  "legalName": "Acme Corporation Ltd.",
  "taxId": "1234567890",
  "registrationNumber": "REG123456",
  "type": "CORPORATION",
  "industry": "TECHNOLOGY",
  "description": "Technology company",
  "website": "https://acme.com",
  "logoUrl": "https://acme.com/logo.png"
}
```

#### Şirket Listele

```http
GET /api/companies
Authorization: Bearer {token}
```

#### Şirket Detayı

```http
GET /api/companies/{companyId}
Authorization: Bearer {token}
```

#### Şirket Ara

```http
GET /api/companies/search?q=acme
Authorization: Bearer {token}
```

#### Duruma Göre Şirketler

```http
GET /api/companies/by-status/ACTIVE
Authorization: Bearer {token}
```

#### Şirket Güncelle

```http
PUT /api/companies/{companyId}
Authorization: Bearer {token}
Content-Type: application/json

{
  "legalName": "Updated Legal Name",
  "description": "Updated description",
  "website": "https://updated.com"
}
```

#### Şirket Sil

```http
DELETE /api/companies/{companyId}
Authorization: Bearer {token}
```

#### Şirket Aktif Et

```http
PUT /api/companies/{companyId}/activate
Authorization: Bearer {token}
```

#### Şirket Pasif Et

```http
PUT /api/companies/{companyId}/deactivate
Authorization: Bearer {token}
```

### Ayarlar ve Abonelik

#### Ayarları Güncelle

```http
PUT /api/companies/{companyId}/settings
Authorization: Bearer {token}
Content-Type: application/json

{
  "settings": {
    "theme": "dark",
    "notifications": true
  }
}
```

#### Abonelik Güncelle

```http
PUT /api/companies/{companyId}/subscription
Authorization: Bearer {token}
Content-Type: application/json

{
  "plan": "PREMIUM",
  "maxUsers": 50,
  "endDate": "2025-12-31T23:59:59"
}
```

### Şirket İletişim Bilgileri

#### İletişim Bilgisi Ekle

```http
POST /api/companies/{companyId}/contacts
Authorization: Bearer {token}
Content-Type: application/json

{
  "contactValue": "info@company.com",
  "contactType": "EMAIL",
  "isPrimary": true
}
```

#### İletişim Bilgilerini Listele

```http
GET /api/companies/{companyId}/contacts
Authorization: Bearer {token}
```

#### Doğrulanmış İletişim Bilgileri

```http
GET /api/companies/{companyId}/contacts/verified
Authorization: Bearer {token}
```

#### Birincil İletişim Bilgisi

```http
GET /api/companies/{companyId}/contacts/primary
Authorization: Bearer {token}
```

### Şirket Kullanıcıları

#### Kullanıcıları Listele

```http
GET /api/companies/{companyId}/users
Authorization: Bearer {token}
```

#### Kullanıcı Sayısı

```http
GET /api/companies/{companyId}/users/count
Authorization: Bearer {token}
```

#### Kullanıcı Ekle

```http
POST /api/companies/{companyId}/users/{userId}
Authorization: Bearer {token}
```

#### Kullanıcı Çıkar

```http
DELETE /api/companies/{companyId}/users/{userId}
Authorization: Bearer {token}
```

#### Kullanıcı Sayısını Senkronize Et

```http
POST /api/companies/{companyId}/users/sync
Authorization: Bearer {token}
```

## 🗄️ Veritabanı

### Ana Tablolar

#### companies

- id (UUID, PK)
- tenant_id (UUID)
- name (VARCHAR)
- legal_name (VARCHAR)
- tax_id (VARCHAR)
- registration_number (VARCHAR)
- type (ENUM)
- industry (ENUM)
- status (ENUM)
- description (TEXT)
- website (VARCHAR)
- logo_url (VARCHAR)
- settings (JSONB)
- preferences (JSONB)
- subscription_start_date (TIMESTAMP)
- subscription_end_date (TIMESTAMP)
- subscription_plan (VARCHAR)
- is_active (BOOLEAN)
- max_users (INTEGER)
- current_users (INTEGER)
- created_at, updated_at, created_by, updated_by, version, deleted

#### company_events

- id (UUID, PK)
- company_id (UUID, FK)
- event_type (VARCHAR)
- event_data (JSONB)
- event_version (INTEGER)
- created_at (TIMESTAMP)

#### company_users

- id (UUID, PK)
- company_id (UUID, FK)
- user_id (UUID)
- role (VARCHAR)
- joined_at (TIMESTAMP)
- is_active (BOOLEAN)

#### company_settings

- id (UUID, PK)
- company_id (UUID, FK)
- setting_key (VARCHAR)
- setting_value (JSONB)
- created_at, updated_at

## 🔧 Konfigürasyon

### application.yml

```yaml
server:
  port: 8083

spring:
  application:
    name: company-service
  datasource:
    url: jdbc:postgresql://localhost:5433/fabric_management
    username: fabric_user
    password: fabric_password
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration

# External Services
contact-service:
  url: http://localhost:8082

user-service:
  url: http://localhost:8081

# Redis Cache
spring:
  data:
    redis:
      host: localhost
      port: 6379

# Kafka
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      client-id: company-service
```

## 🔐 Security & Multi-tenancy

### Tenant Isolation

Her istek için tenant ID kontrolü yapılır:

- HTTP Header: `X-Tenant-ID`
- JWT Token içinden
- Security Context'ten

### Authorization

- `ROLE_ADMIN` - Tüm işlemler
- `ROLE_COMPANY_MANAGER` - Şirket yönetimi
- `ROLE_USER` - Okuma yetkisi

## 📊 Event Sourcing

### Domain Events

- **CompanyCreatedEvent** - Şirket oluşturulduğunda
- **CompanyUpdatedEvent** - Şirket güncellendiğinde
- **CompanyDeletedEvent** - Şirket silindiğinde

### Event Publishing

Tüm event'ler Kafka'ya publish edilir:

- Topic: `company-events`
- Key: `companyId`
- Value: Event JSON

### Event Store

Event'ler `company_events` tablosunda saklanır.

## 🚦 Çalıştırma

### Gereksinimler

- Java 17+
- PostgreSQL 14+
- Redis 7+
- Kafka 3+

### Local Çalıştırma

```bash
# PostgreSQL başlat
docker-compose up -d postgres

# Redis başlat
docker-compose up -d redis

# Kafka başlat
docker-compose up -d kafka

# Service'i çalıştır
./mvnw spring-boot:run
```

### Docker ile Çalıştırma

```bash
docker-compose up company-service
```

## 📈 Monitoring

### Health Check

```http
GET /actuator/health
```

### Metrics

```http
GET /actuator/metrics
GET /actuator/prometheus
```

### Swagger UI

```
http://localhost:8083/swagger-ui.html
```

## 🧪 Test

```bash
# Unit testleri çalıştır
./mvnw test

# Integration testleri çalıştır
./mvnw verify
```

## 📝 Changelog

### v1.0.0 (2025-10-01)

- ✅ Temel CRUD işlemleri
- ✅ CQRS pattern implementasyonu
- ✅ Event sourcing implementasyonu
- ✅ Contact Service entegrasyonu
- ✅ User Service entegrasyonu
- ✅ Multi-tenancy desteği
- ✅ Caching mekanizması
- ✅ Global exception handling
- ✅ Security konfigürasyonu
- ✅ Swagger documentation

## 🤝 Bağımlılıklar

- **Contact Service** (Port 8082) - Şirket iletişim bilgileri
- **User Service** (Port 8081) - Şirket kullanıcıları
- **PostgreSQL** - Ana veritabanı
- **Redis** - Cache
- **Kafka** - Event streaming

## 📞 İletişim

Sorularınız için lütfen proje ekibi ile iletişime geçin.

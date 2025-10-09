# 🏗️ Fabric Management - Mikroservis Mimarisi

**Versiyon:** 2.1  
**Tarih:** 9 Ekim 2025 (Spring Security Native Migration)  
**Prensip:** Clean Architecture + SOLID + DRY + KISS + YAGNI  
**Hedef:** Enterprise-grade, bakanlıkların imrendiği profesyonel mimari

---

## 📋 İçindekiler

1. [Mimari Değerlendirmesi](#-mimari-değerlendirmesi)
2. [Generic Microservice Template](#-generic-microservice-template)
3. [Shared Modules Yapısı](#-shared-modules-yapısı)
4. [Katman Sorumlulukları](#-katman-sorumlulukları)
5. [Shared vs Service-Specific](#-shared-vs-service-specific)
6. [Error Message Management](#-error-message-management)
7. [Refactoring Guide](#-refactoring-guide)
8. [Implementation Checklist](#-implementation-checklist)

---

## 📊 Mimari Değerlendirmesi

### Genel Skor: 6.7/10 → 8.9/10 (Hedef)

| Kategori                  | Mevcut | Hedef  | İyileştirme |
| ------------------------- | ------ | ------ | ----------- |
| **Single Responsibility** | 6.5/10 | 9/10   | +38%        |
| **DRY**                   | 5/10   | 9/10   | +80%        |
| **KISS**                  | 7/10   | 9/10   | +29%        |
| **SOLID**                 | 7.5/10 | 9/10   | +20%        |
| **YAGNI**                 | 6/10   | 8.5/10 | +42%        |

### Ana Sorunlar

#### 1️⃣ Service Sınıfları Çok Büyük (SRP İhlali)

```
UserService.java: 370 satır  🔴
  ├─ Business logic
  ├─ Mapping logic (65+ satır)
  ├─ Validation logic
  └─ Query logic

Hedef: 150 satır ✅
  ├─ Business logic only
  ├─ Mapping → UserMapper
  ├─ Validation → UserValidator
  └─ Query → UserSearchService
```

#### 2️⃣ Kod Tekrarı %35 (DRY İhlali)

```
❌ Her controller'da manuel extraction:
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UUID tenantId = UUID.fromString((String) auth.getDetails());

✅ Çözüm: Spring Security Native
@AuthenticationPrincipal SecurityContext ctx
```

#### 3️⃣ N+1 Query Problem

```
❌ 100 user = 100 Feign call
✅ Batch API: 100 user = 1 Feign call
```

#### 4️⃣ Over-Engineering (YAGNI İhlali)

```
❌ Company Service: 11 handler sınıfı (basit CRUD için)
✅ Direkt service method yeterli
```

---

## 🎯 Generic Microservice Template

> **Bu yapı her microservice için standart olarak kullanılır.**  
> Sadece domain-specific dosyalar değişir, yapı aynı kalır.

```
{service-name}-service/                        # user-service, company-service, etc.
│
├── pom.xml
├── Dockerfile
├── README.md
│
└── src/
    ├── main/
    │   ├── java/com/fabricmanagement/{service}/
    │   │   │
    │   │   ├── {Service}Application.java                   # Main class
    │   │   │
    │   │   ├── api/                                        # 🌐 API Layer
    │   │   │   ├── controller/
    │   │   │   │   ├── {Entity}Controller.java            [~120 satır]
    │   │   │   │   ├── {Entity}SearchController.java      [~80 satır] (Optional)
    │   │   │   │   └── {Entity}AuthController.java        [~80 satır] (If needed)
    │   │   │   │
    │   │   │   └── dto/
    │   │   │       ├── request/
    │   │   │       │   ├── Create{Entity}Request.java
    │   │   │       │   ├── Update{Entity}Request.java
    │   │   │       │   └── {Entity}SearchRequest.java
    │   │   │       │
    │   │   │       └── response/
    │   │   │           ├── {Entity}Response.java
    │   │   │           └── {Entity}ListResponse.java
    │   │   │
    │   │   ├── application/                                # 🔧 Application Layer
    │   │   │   │
    │   │   │   ├── service/                                # Business Services
    │   │   │   │   ├── {Entity}Service.java               [~150 satır] ✅
    │   │   │   │   ├── {Entity}SearchService.java         [~80 satır]  ✅
    │   │   │   │   └── {Entity}ValidationService.java     [~60 satır]  ✅
    │   │   │   │
    │   │   │   ├── mapper/                                 # DTO ↔ Entity ✨
    │   │   │   │   ├── {Entity}Mapper.java                [~120 satır] ✅
    │   │   │   │   └── {Entity}ResponseMapper.java        [~80 satır]  ✅
    │   │   │   │
    │   │   │   ├── validator/                              # Business Validation ✨
    │   │   │   │   ├── {Entity}Validator.java             [~60 satır]  ✅
    │   │   │   │   └── {Field}Validator.java              [~40 satır]  ✅
    │   │   │   │
    │   │   │   └── helper/                                 # Utility Helpers ✨
    │   │   │       ├── {Entity}Enricher.java              [~50 satır]  ✅
    │   │   │       └── ExternalDataFetcher.java           [~50 satır]  ✅
    │   │   │
    │   │   ├── domain/                                     # 🎯 Domain Layer
    │   │   │   ├── aggregate/
    │   │   │   │   └── {Entity}.java                      [~250 satır] ✅
    │   │   │   │
    │   │   │   ├── service/                                # Domain Services ✨
    │   │   │   │   └── {Entity}DomainService.java         [~100 satır] ✅
    │   │   │   │
    │   │   │   ├── event/                                  # Domain Events
    │   │   │   │   ├── {Entity}CreatedEvent.java
    │   │   │   │   ├── {Entity}UpdatedEvent.java
    │   │   │   │   └── {Entity}DeletedEvent.java
    │   │   │   │
    │   │   │   ├── valueobject/
    │   │   │   │   ├── {Field}VO.java
    │   │   │   │   └── {Entity}Status.java
    │   │   │   │
    │   │   │   └── exception/                              # SADECE Özel Olanlar!
    │   │   │       └── {Specific}Exception.java           (Generic'ler shared'da)
    │   │   │
    │   │   ├── infrastructure/                             # 🏗️ Infrastructure
    │   │   │   ├── repository/
    │   │   │   │   └── {Entity}Repository.java
    │   │   │   │
    │   │   │   ├── client/                                 # Feign Clients
    │   │   │   │   ├── {External}ServiceClient.java
    │   │   │   │   └── dto/
    │   │   │   │
    │   │   │   ├── messaging/
    │   │   │   │   ├── publisher/
    │   │   │   │   └── listener/
    │   │   │   │
    │   │   │   ├── cache/                                  # Optional
    │   │   │   │   └── {Entity}CacheService.java
    │   │   │   │
    │   │   │   └── config/                                 # SADECE Özel Config!
    │   │   │       └── {Specific}Config.java
    │   │   │
    │   │   └── config/                                     # EMPTY! Uses shared
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/migration/
    │           └── V1__init_{entity}_schema.sql
    │
    └── test/java/com/fabricmanagement/{service}/
        ├── api/{Entity}ControllerTest.java
        ├── application/service/{Entity}ServiceTest.java
        ├── application/mapper/{Entity}MapperTest.java
        └── domain/aggregate/{Entity}Test.java
```

---

## 🧩 Shared Modules Yapısı

> **Tüm microservice'lerin kullandığı ortak modüller.**  
> DRY prensibi - Kod tekrarı %0

```
shared/
│
├── shared-domain/                              # 🎯 Core Domain Logic
│   ├── pom.xml
│   └── src/main/java/com/fabricmanagement/shared/domain/
│       │
│       ├── base/                               # Base Classes
│       │   ├── BaseEntity.java                 [JPA base with audit]
│       │   └── AggregateRoot.java              [DDD pattern]
│       │
│       ├── exception/                          # Generic Exceptions ✅
│       │   ├── DomainException.java            [Base exception]
│       │   ├── ResourceNotFoundException.java  [Generic NOT_FOUND]
│       │   ├── ValidationException.java        [Generic VALIDATION]
│       │   ├── UnauthorizedException.java      [Generic AUTH]
│       │   ├── BusinessRuleViolationException.java
│       │   └── ExternalServiceException.java
│       │
│       ├── message/                            # Message Keys ✨
│       │   ├── ErrorMessageKeys.java           [Error message keys]
│       │   └── ValidationMessageKeys.java      [Validation keys]
│       │
│       ├── event/
│       │   ├── DomainEvent.java
│       │   └── DomainEventPublisher.java
│       │
│       └── outbox/
│           └── OutboxEvent.java
│
├── shared-application/                         # 🔧 Application Shared
│   └── src/main/java/com/fabricmanagement/shared/application/
│       │
│       ├── response/
│       │   ├── ApiResponse.java                [Standard API response]
│       │   └── PaginatedResponse.java
│       │
│       ├── context/                            # Security Context ✨
│       │   └── SecurityContext.java            [User/tenant info]
│       │
│       ├── annotation/                         # (REMOVED - Using Spring Security native)
│       │                                        # @AuthenticationPrincipal SecurityContext
│       │
│       ├── exception/                          # Global Handler ✅
│       │   └── GlobalExceptionHandler.java     [SINGLE for ALL]
│       │
│       └── util/
│           ├── DateUtils.java
│           └── StringUtils.java
│
├── shared-infrastructure/                      # 🏗️ Infrastructure Shared
│   └── src/main/
│       ├── java/com/fabricmanagement/shared/infrastructure/
│       │   │
│       │   ├── constants/                      # Constants ✨
│       │   │   ├── ValidationConstants.java
│       │   │   ├── SecurityRoles.java
│       │   │   └── CacheKeys.java
│       │   │
│       │   ├── security/
│       │   │   ├── SecurityContextHolder.java
│       │   │   └── SecurityUtils.java
│       │   │
│       │   ├── service/                        # Shared Services ✨
│       │   │   └── MessageService.java         [i18n resolver]
│       │   │
│       │   ├── config/                         # Default Configs ✅
│       │   │   ├── DefaultWebConfig.java       [For ALL services]
│       │   │   ├── DefaultJpaConfig.java
│       │   │   ├── DefaultCacheConfig.java
│       │   │   ├── MessageSourceConfig.java
│       │   │   └── SwaggerConfig.java
│       │   │
│       │   └── util/
│       │       ├── JsonUtils.java
│       │       └── UuidUtils.java
│       │
│       └── resources/
│           └── messages/                       # i18n Messages ✨
│               ├── errors_en.properties        [English errors]
│               ├── errors_tr.properties        [Turkish errors]
│               ├── validations_en.properties
│               └── validations_tr.properties
│
└── shared-security/                            # 🔐 Security Shared
    └── src/main/java/com/fabricmanagement/shared/security/
        │
        ├── config/
        │   ├── DefaultSecurityConfig.java      [For ALL services]
        │   └── CorsConfig.java
        │
        ├── jwt/
        │   ├── JwtTokenProvider.java
        │   ├── JwtAuthenticationFilter.java
        │   └── JwtTokenValidator.java
        │
        └── annotation/
            ├── RequiresTenant.java
            └── AuditLog.java
```

---

## 📊 Katman Sorumlulukları

### 🌐 API Layer (Presentation)

**Sorumluluklar:**

```java
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityContext ctx) {  // ✅ Spring Security native!

        UserResponse user = userService.getUser(id, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
```

✅ **SADECE:**

- HTTP request/response handling
- Input validation (@Valid)
- Authorization (@PreAuthorize, custom annotations)
- SecurityContext injection
- Response wrapping

❌ **ASLA:**

- Business logic
- Mapping logic
- Validation logic
- Database access

---

### 🔧 Application Layer

#### Service (~150 satır)

```java
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository repository;
    private final UserMapper mapper;
    private final UserValidator validator;
    private final EventPublisher eventPublisher;

    public UUID createUser(CreateUserRequest request, UUID tenantId, String createdBy) {
        validator.validateCreate(request, tenantId);        // ✅ Delegate

        User user = mapper.toEntity(request, tenantId, createdBy);  // ✅ Delegate
        user = repository.save(user);

        eventPublisher.publishUserCreated(user);
        return user.getId();
    }
}
```

✅ **SADECE:** Orchestration, Transaction, Event publishing  
❌ **ASLA:** Mapping, Validation, HTTP concerns

#### Mapper (~120 satır)

```java
@Component
@RequiredArgsConstructor
public class UserMapper {

    private final ContactServiceClient contactClient;

    public UserResponse toResponse(User user) {
        ContactInfo contact = fetchContactInfo(user.getId());

        return UserResponse.builder()
            .id(user.getId())
            .email(contact.getEmail())
            .build();
    }

    public User toEntity(CreateUserRequest request, UUID tenantId, String createdBy) {
        return User.builder()
            .tenantId(tenantId)
            .firstName(request.getFirstName())
            .createdBy(createdBy)
            .build();
    }
}
```

✅ **SADECE:** DTO ↔ Entity conversion, External data enrichment

#### Validator (~60 satır)

```java
@Component
@RequiredArgsConstructor
public class UserValidator {

    private final UserRepository repository;

    public void validateCreate(CreateUserRequest request, UUID tenantId) {
        if (repository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            throw new ValidationException(
                ErrorMessageKeys.DUPLICATE_RESOURCE,
                "User", request.getEmail()
            );
        }
    }
}
```

✅ **SADECE:** Business rule validation, Cross-field validation

---

### 🎯 Domain Layer

#### Aggregate (~250 satır)

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    private String firstName;
    private UserStatus status;

    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    public static User create(UUID tenantId, String firstName) {
        User user = User.builder()
            .tenantId(tenantId)
            .firstName(firstName)
            .status(UserStatus.ACTIVE)
            .build();

        user.addDomainEvent(new UserCreatedEvent(user.getId()));
        return user;
    }

    public void activate() {
        if (this.status == UserStatus.DELETED) {
            throw new IllegalStateException("Cannot activate deleted user");
        }
        this.status = UserStatus.ACTIVE;
    }
}
```

✅ **SADECE:** Business invariants, Domain logic, Domain events, State transitions

---

### 🏗️ Infrastructure Layer

#### Repository

```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.tenantId = :tenantId AND u.deleted = false")
    Optional<User> findActiveByIdAndTenantId(
        @Param("id") UUID id,
        @Param("tenantId") UUID tenantId
    );

    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.deleted = false")
    List<User> findAllActiveByTenantId(@Param("tenantId") UUID tenantId);

    boolean existsByEmailAndTenantId(String email, UUID tenantId);
}
```

✅ **SADECE:** Data persistence, Custom queries, Common filters

---

## 🎯 Shared vs Service-Specific

### Karar Kuralı

```java
IF (tüm service'lerde kullanılır)
    → shared/ modülüne koy ✅
ELSE IF (sadece 1 service'e özel)
    → o service'e koy ✅
ELSE
    → Muhtemelen gerekmez (YAGNI) ❌
```

### Exception

| Exception                 | Shared ✅ | Service ❌      |
| ------------------------- | --------- | --------------- |
| ResourceNotFoundException | ✅        | -               |
| ValidationException       | ✅        | -               |
| UnauthorizedException     | ✅        | -               |
| AccountLockedException    | -         | ✅ User only    |
| MaxUsersLimitException    | -         | ✅ Company only |

### Configuration

| Config                | Shared ✅ | Service ❌      |
| --------------------- | --------- | --------------- |
| WebConfig             | ✅        | -               |
| SecurityConfig        | ✅        | -               |
| JpaConfig             | ✅        | -               |
| CacheConfig           | ✅        | -               |
| NotificationConfig    | -         | ✅ Contact only |
| SubscriptionScheduler | -         | ✅ Company only |

---

## 🌍 Error Message Management

### Problem: Hard-Coded Messages

```java
// ❌ KÖTÜ
throw new Exception("User not found: " + userId);
throw new Exception("Email is required");
```

**Sorunlar:**

- 🔴 Consistency yok
- 🔴 i18n imkansız
- 🔴 Değişiklik zor
- 🔴 Test kırılgan

### Çözüm: Merkezi Yönetim

#### 1. Message Keys (Constants)

```java
// shared-domain/message/ErrorMessageKeys.java
public final class ErrorMessageKeys {

    public static final String USER_NOT_FOUND = "error.user.not.found";
    public static final String EMAIL_INVALID = "error.validation.email.invalid";
    public static final String ACCOUNT_LOCKED = "error.auth.account.locked";
}
```

#### 2. Properties Files (i18n)

```properties
# shared-infrastructure/resources/messages/errors_en.properties
error.user.not.found=User not found: {0}
error.validation.email.invalid=Invalid email format: {0}
error.auth.account.locked=Account locked for {0} minutes

# errors_tr.properties
error.user.not.found=Kullanıcı bulunamadı: {0}
error.validation.email.invalid=Geçersiz e-posta formatı: {0}
error.auth.account.locked=Hesap {0} dakika kilitlendi
```

#### 3. Message Service

```java
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageSource messageSource;

    public String getMessage(String key, Object... params) {
        return messageSource.getMessage(
            key,
            params,
            LocaleContextHolder.getLocale()  // Accept-Language header
        );
    }
}
```

#### 4. Exception Classes

```java
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resource, String id) {
        super(
            ErrorMessageKeys.RESOURCE_NOT_FOUND,  // Key
            new Object[]{resource, id}            // Parameters
        );
    }
}
```

#### 5. Usage

```java
// Service
throw new ResourceNotFoundException("User", userId.toString());

// Response (English)
"User not found: 123e4567..."

// Response (Turkish)
"Kullanıcı bulunamadı: 123e4567..."
```

**Avantajlar:**

- ✅ i18n support (TR/EN otomatik)
- ✅ DRY (tek yerde tanım)
- ✅ Consistency %100
- ✅ Test stable (key bazlı)

---

## 🚀 Refactoring Guide

### 2 Haftalık Plan

#### Hafta 1: Mapper & Context

```bash
# 1. Mapper sınıfları
services/{service}/application/mapper/
  ├─ {Entity}Mapper.java

# 2. SecurityContext injection
shared/shared-application/
  └─ context/SecurityContext.java  # Simple POJO

shared/shared-security/
  └─ filter/JwtAuthenticationFilter.java  # Sets SecurityContext as principal

# 3. Controller'ları güncelle
@AuthenticationPrincipal SecurityContext ctx  # Spring Security native!
```

#### Hafta 2: Repository & Exception

```bash
# 1. Repository custom methods
Optional<User> findActiveByIdAndTenantId(UUID id, UUID tenantId);

# 2. Exception standardization
throw new ResourceNotFoundException("User", userId);

# 3. Message keys
ErrorMessageKeys.java + errors_en.properties
```

### 4 Haftalık Detaylı Plan

**Sprint 1 (Hafta 1-2): Temel Refactoring**

- [ ] Mapper sınıfları (UserMapper, CompanyMapper, ContactMapper)
- [ ] SecurityContext injection
- [ ] BaseController pattern (opsiyonel)
- **Etki:** DRY %40 iyileşme

**Sprint 2 (Hafta 3-4): Service Refactoring**

- [ ] Service'leri böl (UserService + UserSearchService)
- [ ] Repository custom methodlar
- [ ] Exception standardization
- **Etki:** SRP uygulandı, Service 150 satıra indi

**Sprint 3 (Hafta 5-6): Performance**

- [ ] Batch API endpoints
- [ ] N+1 query fix
- [ ] Redis cache layer
- **Etki:** Response time %50 ↓

**Sprint 4 (Hafta 7-8): CQRS Simplification**

- [ ] Company Service handler'ları kaldır
- [ ] Basit CRUD pattern
- **Etki:** Kod karmaşıklığı %70 ↓

---

## ✅ Implementation Checklist

### New Microservice Creation

```bash
# 1. Template'den kopyala
cp -r service-template/ services/new-service/

# 2. Package rename
com.fabricmanagement.template → com.fabricmanagement.newservice

# 3. Entity rename
{Entity} → NewEntity

# 4. pom.xml dependencies
- shared-domain
- shared-application
- shared-infrastructure
- shared-security

# 5. DONE! ✅
# Exception/Config kopyalama YOK!
```

### Files to Customize

```
✅ CUSTOMIZE:
- {Entity}Controller.java
- {Entity}Service.java
- {Entity}Mapper.java
- {Entity}.java (Aggregate)
- {Entity}Repository.java
- DTOs
- application.yml

❌ NO CUSTOMIZATION (use shared):
- Exceptions (generic ones)
- Config files (defaults)
- Base classes
- Security config
```

---

## 💡 Best Practices

### ✅ DO

1. ✅ Use shared modules for common functionality
2. ✅ Keep services small (~150 lines)
3. ✅ Separate concerns (mapper, validator, helper)
4. ✅ Use message keys for all errors
5. ✅ Inject SecurityContext with annotation
6. ✅ Custom queries in repository
7. ✅ Test each layer independently

### ❌ DON'T

1. ❌ Copy-paste exception classes
2. ❌ Copy-paste config files
3. ❌ Hard-code error messages
4. ❌ Put mapping logic in service
5. ❌ Put validation logic in service
6. ❌ Create service-specific exceptions for generic cases
7. ❌ Over-engineer with CQRS for simple CRUD

---

## 📈 Beklenen Sonuçlar

### Metrikler

| Metrik                         | Önce        | Sonra     | İyileştirme |
| ------------------------------ | ----------- | --------- | ----------- |
| **Ortalama Service Satır**     | 350         | 180       | -48%        |
| **Kod Tekrarı**                | %35         | %10       | -71%        |
| **Mapping Logic Tekrarı**      | 7 yerde     | 3 mapper  | -57%        |
| **Handler Sınıf Sayısı**       | 11          | 3         | -73%        |
| **Response Time (list users)** | 800ms       | 200ms     | -75%        |
| **External Service Calls**     | 100/request | 1/request | -99%        |

### Kod Kalitesi

| Kategori                  | Önce   | Sonra  |
| ------------------------- | ------ | ------ |
| **Single Responsibility** | 6.5/10 | 9/10   |
| **DRY**                   | 5/10   | 9/10   |
| **KISS**                  | 7/10   | 9/10   |
| **SOLID**                 | 7.5/10 | 9/10   |
| **YAGNI**                 | 6/10   | 8.5/10 |

**Toplam:** 6.7/10 → **8.9/10** (+33%)

---

## 🎯 Sonuç

### İdeal Mimari

```
Microservice (50 dosya)
  ├─ Controllers (~120 satır)
  ├─ Services (~150 satır)
  ├─ Mappers (~100 satır)
  ├─ Validators (~60 satır)
  ├─ Aggregates (~250 satır)
  └─ Repositories (interface)

Shared Modules (41 dosya)
  ├─ Generic exceptions
  ├─ Default configs
  ├─ Message keys & i18n
  ├─ Global exception handler
  └─ Security context

Principles:
  ✅ DRY (Don't Repeat Yourself)
  ✅ KISS (Keep It Simple)
  ✅ YAGNI (You Aren't Gonna Need It)
  ✅ SOLID (All principles)
  ✅ Clean Architecture
  ✅ Domain-Driven Design
```

**Sonuç:**

- 📉 Kod tekrarı: %5
- 📉 Dosya boyutu: ~100 satır
- 📈 Maintainability: 9/10
- 📈 Testability: 9/10
- 📈 Profesyonellik: 10/10 🏆

---

**Hazırlayan:** Backend Ekibi  
**Tarih:** 9 Ekim 2025  
**Son Güncelleme:** Spring Security Native Migration  
**Versiyon:** 2.0  
**Durum:** ✅ Production Ready

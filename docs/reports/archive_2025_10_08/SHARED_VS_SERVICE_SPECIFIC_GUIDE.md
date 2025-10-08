# 🎯 Shared vs Service-Specific: Over-Engineering'den Kaçınma Rehberi

**Tarih:** 8 Ekim 2025  
**Prensip:** YAGNI (You Aren't Gonna Need It) + DRY (Don't Repeat Yourself)

---

## ❓ Soru: Her Microservice'de Exception ve Config Dosyaları Gerekli mi?

**Kısa Cevap:** ❌ HAYIR - Bu klasik over-engineering tuzağı!

**Doğru Yaklaşım:**

- ✅ **Shared** → Ortak davranışlar
- ✅ **Service-Specific** → Sadece gerçekten farklı olan durumlar

---

## 📊 Exception Handling: Shared vs Service-Specific

### ❌ YANLIŞ (Over-Engineering)

```
services/
├── user-service/
│   └── exception/
│       ├── UserServiceExceptionHandler.java      ❌ TEKRAR
│       ├── UserNotFoundException.java            ❌ TEKRAR
│       ├── ValidationException.java              ❌ TEKRAR
│       └── UnauthorizedException.java            ❌ TEKRAR
│
├── company-service/
│   └── exception/
│       ├── CompanyServiceExceptionHandler.java   ❌ TEKRAR
│       ├── CompanyNotFoundException.java         ❌ TEKRAR
│       ├── ValidationException.java              ❌ TEKRAR
│       └── UnauthorizedException.java            ❌ TEKRAR
│
└── contact-service/
    └── exception/
        ├── ContactServiceExceptionHandler.java   ❌ TEKRAR
        ├── ContactNotFoundException.java         ❌ TEKRAR
        ├── ValidationException.java              ❌ TEKRAR
        └── UnauthorizedException.java            ❌ TEKRAR
```

**Sorunlar:**

- 🔴 Aynı kod 3 kere tekrar
- 🔴 Değişiklik her yerde yapılmalı
- 🔴 Test burden 3x
- 🔴 Maintenance nightmare

---

### ✅ DOĞRU (YAGNI + DRY)

```
shared/shared-domain/
└── exception/
    ├── DomainException.java                  ✅ Base exception
    ├── ResourceNotFoundException.java        ✅ Generic
    ├── ValidationException.java              ✅ Generic
    ├── UnauthorizedException.java            ✅ Generic
    ├── BusinessRuleViolationException.java   ✅ Generic
    └── ExternalServiceException.java         ✅ Generic

shared/shared-application/
└── exception/
    └── GlobalExceptionHandler.java           ✅ SHARED handler

services/user-service/
└── exception/
    ├── (YOK - shared kullanır)               ✅ Clean
    └── AccountLockedException.java           ✅ Service-specific only!

services/company-service/
└── exception/
    ├── (YOK - shared kullanır)               ✅ Clean
    └── MaxUsersLimitException.java           ✅ Service-specific only!
```

**Faydalar:**

- ✅ DRY - Tek yerde tanım
- ✅ Consistency - Tüm service'ler aynı davranış
- ✅ Easy maintenance
- ✅ Sadece gerçekten özel durumlar service'de

---

## 🎯 Exception Strategy - Pratik Uygulama

### 1️⃣ Shared Domain Exceptions (shared-domain)

```java
// ✅ Generic base exceptions
shared/shared-domain/exception/

├── DomainException.java                     // Base exception
│   @Getter
│   public abstract class DomainException extends RuntimeException {
│       private final String errorCode;
│       public DomainException(String message, String errorCode) {
│           super(message);
│           this.errorCode = errorCode;
│       }
│   }
│
├── ResourceNotFoundException.java           // Generic NOT FOUND
│   public class ResourceNotFoundException extends DomainException {
│       public ResourceNotFoundException(String resource, String id) {
│           super(resource + " not found: " + id, "RESOURCE_NOT_FOUND");
│       }
│   }
│
├── ValidationException.java                 // Generic VALIDATION
│   public class ValidationException extends DomainException {
│       public ValidationException(String message) {
│           super(message, "VALIDATION_ERROR");
│       }
│   }
│
├── UnauthorizedException.java               // Generic UNAUTHORIZED
│   public class UnauthorizedException extends DomainException {
│       public UnauthorizedException(String message) {
│           super(message, "UNAUTHORIZED");
│       }
│   }
│
└── BusinessRuleViolationException.java      // Generic BUSINESS RULE
    public class BusinessRuleViolationException extends DomainException {
        public BusinessRuleViolationException(String message) {
            super(message, "BUSINESS_RULE_VIOLATION");
        }
    }
```

### 2️⃣ Shared Global Exception Handler (shared-application)

```java
// ✅ SHARED - Tüm service'ler kullanır
shared/shared-application/exception/GlobalExceptionHandler.java

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(
            UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(
            BusinessRuleViolationException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR"));
    }
}
```

### 3️⃣ Service-Specific Exceptions (Sadece Özel Durumlar)

```java
// ✅ User Service - Sadece service'e özel exception
services/user-service/exception/

└── AccountLockedException.java              // User-specific!
    public class AccountLockedException extends DomainException {
        public AccountLockedException(String contact, Duration lockDuration) {
            super(String.format("Account locked for %s minutes due to too many failed attempts",
                  lockDuration.toMinutes()),
                  "ACCOUNT_LOCKED");
        }
    }

// ✅ Company Service - Sadece service'e özel exception
services/company-service/exception/

└── MaxUsersLimitException.java              // Company-specific!
    public class MaxUsersLimitException extends DomainException {
        public MaxUsersLimitException(int current, int max) {
            super(String.format("Cannot add user. Current: %d, Max: %d", current, max),
                  "MAX_USERS_LIMIT_EXCEEDED");
        }
    }
```

### 4️⃣ Service'de Kullanım

```java
// ✅ User Service - Shared exception kullanır
@Service
public class UserService {

    public UserResponse getUser(UUID userId, UUID tenantId) {
        User user = userRepository.findActiveByIdAndTenantId(userId, tenantId)
            .orElseThrow(() ->
                new ResourceNotFoundException("User", userId.toString()));
                // ↑ SHARED exception kullanılıyor!
        return mapper.toResponse(user);
    }

    public void login(LoginRequest request) {
        if (loginAttemptService.isLocked(request.getContact())) {
            throw new AccountLockedException(request.getContact(),
                Duration.ofMinutes(15));
            // ↑ SERVICE-SPECIFIC exception!
        }
    }
}

// ✅ Company Service - Shared exception kullanır
@Service
public class CompanyService {

    public CompanyResponse getCompany(UUID companyId, UUID tenantId) {
        Company company = repository.findActiveByIdAndTenantId(companyId, tenantId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Company", companyId.toString()));
                // ↑ SHARED exception kullanılıyor!
        return mapper.toResponse(company);
    }

    public void addUserToCompany(UUID companyId, UUID userId) {
        Company company = findCompany(companyId);
        if (company.getCurrentUsers() >= company.getMaxUsers()) {
            throw new MaxUsersLimitException(
                company.getCurrentUsers(),
                company.getMaxUsers());
            // ↑ SERVICE-SPECIFIC exception!
        }
    }
}
```

---

## ⚙️ Configuration Files: Shared vs Service-Specific

### ❌ YANLIŞ (Over-Engineering)

```
services/
├── user-service/
│   └── config/
│       ├── WebConfig.java                    ❌ TEKRAR
│       ├── SecurityConfig.java               ❌ TEKRAR
│       ├── JpaConfig.java                    ❌ TEKRAR
│       ├── CacheConfig.java                  ❌ TEKRAR
│       └── SwaggerConfig.java                ❌ TEKRAR
│
├── company-service/
│   └── config/
│       ├── WebConfig.java                    ❌ TEKRAR (aynı kod!)
│       ├── SecurityConfig.java               ❌ TEKRAR (aynı kod!)
│       ├── JpaConfig.java                    ❌ TEKRAR (aynı kod!)
│       ├── CacheConfig.java                  ❌ TEKRAR (aynı kod!)
│       └── SwaggerConfig.java                ❌ TEKRAR (aynı kod!)
│
└── contact-service/
    └── config/
        ├── WebConfig.java                    ❌ TEKRAR (aynı kod!)
        ├── SecurityConfig.java               ❌ TEKRAR (aynı kod!)
        ├── JpaConfig.java                    ❌ TEKRAR (aynı kod!)
        ├── CacheConfig.java                  ❌ TEKRAR (aynı kod!)
        └── SwaggerConfig.java                ❌ TEKRAR (aynı kod!)
```

**Sorunlar:**

- 🔴 Aynı config 3 yerde
- 🔴 Security update → 3 yerde değişiklik
- 🔴 Copy-paste errors
- 🔴 Inconsistent behavior riski

---

### ✅ DOĞRU (YAGNI + DRY)

```
shared/shared-infrastructure/
└── config/
    ├── DefaultWebConfig.java                 ✅ SHARED default
    ├── DefaultJpaConfig.java                 ✅ SHARED default
    ├── DefaultCacheConfig.java               ✅ SHARED default
    └── DefaultSwaggerConfig.java             ✅ SHARED default

shared/shared-security/
└── config/
    └── DefaultSecurityConfig.java            ✅ SHARED default

services/user-service/
└── config/
    └── (YOK - defaults kullanır)             ✅ Clean!

services/company-service/
└── config/
    └── CompanySpecificConfig.java            ✅ Sadece farklı olanlar!
        // Sadece company-specific overrides

services/contact-service/
└── config/
    └── NotificationConfig.java               ✅ Sadece contact-specific!
        // SMS/Email provider config
```

---

## 🎯 Configuration Strategy - Pratik Uygulama

### 1️⃣ Shared Default Configs

```java
// ✅ shared-infrastructure/config/DefaultWebConfig.java
@Configuration
public class DefaultWebConfig implements WebMvcConfigurer {

    @Autowired(required = false)
    private List<HandlerMethodArgumentResolver> customResolvers;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // SecurityContext resolver
        resolvers.add(new SecurityContextResolver());

        // Custom resolvers from services
        if (customResolvers != null) {
            resolvers.addAll(customResolvers);
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}

// ✅ shared-infrastructure/config/DefaultJpaConfig.java
@Configuration
@EnableJpaAuditing
public class DefaultJpaConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getCurrentUserId());
    }
}

// ✅ shared-infrastructure/config/DefaultCacheConfig.java
@Configuration
@EnableCaching
public class DefaultCacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer())
            );
    }
}
```

### 2️⃣ Service-Specific Config (Sadece Farklılıklar)

```java
// ✅ contact-service/config/NotificationConfig.java
// Contact service'e özel - SMS/Email provider config
@Configuration
public class NotificationConfig {

    @Value("${notification.sms.provider}")
    private String smsProvider;

    @Value("${notification.email.provider}")
    private String emailProvider;

    @Bean
    public SmsNotificationService smsService() {
        // Contact-specific SMS configuration
        return new SmsNotificationService(smsProvider);
    }

    @Bean
    public EmailNotificationService emailService() {
        // Contact-specific Email configuration
        return new EmailNotificationService(emailProvider);
    }
}

// ✅ company-service/config/CompanySpecificConfig.java
// Company service'e özel - Subscription management
@Configuration
public class CompanySpecificConfig {

    @Bean
    public SubscriptionScheduler subscriptionScheduler() {
        // Company-specific scheduled task
        return new SubscriptionScheduler();
    }
}

// ✅ user-service/config/
// BOŞŞ! Hiçbir service-specific config yok
// Sadece shared config'ler kullanılıyor ✅
```

### 3️⃣ Override Pattern (İhtiyaç Olursa)

```java
// ✅ Eğer bir service default'u override etmek isterse:
@Configuration
public class UserServiceWebConfig extends DefaultWebConfig {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // Call parent
        super.addArgumentResolvers(resolvers);

        // Add service-specific resolver
        resolvers.add(new UserSpecificResolver());
    }
}
```

---

## 📊 Karar Matrisi: Shared mi, Service-Specific mi?

### Exception Handling

| Exception Type                       | Shared ✅ | Service-Specific ❌ | Neden                     |
| ------------------------------------ | --------- | ------------------- | ------------------------- |
| `ResourceNotFoundException`          | ✅        | -                   | Her service'de kullanılır |
| `ValidationException`                | ✅        | -                   | Generic validation        |
| `UnauthorizedException`              | ✅        | -                   | Security common           |
| `DomainException` (base)             | ✅        | -                   | Base class                |
| `AccountLockedException`             | -         | ✅ User only        | User service özel         |
| `MaxUsersLimitException`             | -         | ✅ Company only     | Company service özel      |
| `ContactVerificationFailedException` | -         | ✅ Contact only     | Contact service özel      |

### Configuration Files

| Config Type             | Shared ✅ | Service-Specific ❌ | Neden                  |
| ----------------------- | --------- | ------------------- | ---------------------- |
| `WebConfig`             | ✅        | -                   | CORS, resolvers same   |
| `SecurityConfig`        | ✅        | -                   | Security rules same    |
| `JpaConfig`             | ✅        | -                   | JPA settings same      |
| `CacheConfig`           | ✅        | -                   | Redis settings same    |
| `SwaggerConfig`         | ✅        | -                   | API docs pattern same  |
| `NotificationConfig`    | -         | ✅ Contact only     | SMS/Email providers    |
| `SubscriptionScheduler` | -         | ✅ Company only     | Company-specific cron  |
| `LoginAttemptConfig`    | -         | ✅ User only        | User-specific security |

---

## 🎯 Best Practices

### ✅ DO (Yapılması Gerekenler)

1. **Start with Shared**

   ```java
   // İlk önce shared'da tanımla
   shared/shared-domain/exception/ResourceNotFoundException.java
   ```

2. **Move to Service-Specific Only When Needed**

   ```java
   // Sadece gerçekten farklıysa service'e taşı
   services/user-service/exception/AccountLockedException.java
   ```

3. **Use Inheritance for Customization**

   ```java
   // Override pattern kullan
   public class UserWebConfig extends DefaultWebConfig {
       // Only customizations
   }
   ```

4. **Document Why It's Service-Specific**
   ```java
   /**
    * AccountLockedException - USER SERVICE SPECIFIC
    *
    * This exception is specific to user authentication flow.
    * Other services don't have account lockout mechanism.
    */
   public class AccountLockedException extends DomainException {
       // ...
   }
   ```

### ❌ DON'T (Yapılmaması Gerekenler)

1. **Don't Copy-Paste Config Files**

   ```java
   // ❌ YANLIŞ
   // Her service'de aynı WebConfig
   ```

2. **Don't Create Service-Specific Exceptions for Generic Cases**

   ```java
   // ❌ YANLIŞ
   public class UserNotFoundException extends DomainException { }
   public class CompanyNotFoundException extends DomainException { }
   public class ContactNotFoundException extends DomainException { }

   // ✅ DOĞRU
   public class ResourceNotFoundException extends DomainException {
       public ResourceNotFoundException(String resource, String id) {
           super(resource + " not found: " + id, "RESOURCE_NOT_FOUND");
       }
   }
   // Usage: throw new ResourceNotFoundException("User", userId);
   ```

3. **Don't Create Config for "Might Need It Later"**

   ```java
   // ❌ YANLIŞ - YAGNI violation!
   @Configuration
   public class UserServiceCacheConfig {
       // Same as default... why???
   }
   ```

4. **Don't Duplicate Global Exception Handler**

   ```java
   // ❌ YANLIŞ
   // Her service'de aynı @RestControllerAdvice

   // ✅ DOĞRU
   // Shared'da tek GlobalExceptionHandler
   ```

---

## 🎯 Önerilen Yapı (Minimal Over-Engineering)

### Exception Hierarchy

```
shared/shared-domain/exception/
├── DomainException.java                      ✅ Base
├── ResourceNotFoundException.java            ✅ Generic
├── ValidationException.java                  ✅ Generic
├── UnauthorizedException.java                ✅ Generic
├── BusinessRuleViolationException.java       ✅ Generic
└── ExternalServiceException.java             ✅ Generic

shared/shared-application/exception/
└── GlobalExceptionHandler.java               ✅ SINGLE handler for all!

services/user-service/exception/
└── AccountLockedException.java               ✅ User-specific only

services/company-service/exception/
└── MaxUsersLimitException.java               ✅ Company-specific only

services/contact-service/exception/
└── (EMPTY - uses shared only)                ✅ No custom exceptions!
```

### Configuration Hierarchy

```
shared/shared-infrastructure/config/
├── DefaultWebConfig.java                     ✅ All services use
├── DefaultJpaConfig.java                     ✅ All services use
├── DefaultCacheConfig.java                   ✅ All services use
└── DefaultSwaggerConfig.java                 ✅ All services use

shared/shared-security/config/
└── DefaultSecurityConfig.java                ✅ All services use

services/user-service/config/
└── (EMPTY - uses defaults)                   ✅ No custom config!

services/company-service/config/
└── SubscriptionSchedulerConfig.java          ✅ Company-specific only

services/contact-service/config/
└── NotificationProviderConfig.java           ✅ Contact-specific only
```

---

## 📈 Metrics: Over-Engineering vs Clean

### ❌ Over-Engineering (Önce)

```
Config Files: 15 dosya (5 x 3 service)
Exception Files: 21 dosya (7 x 3 service)
Code Duplication: %80
Maintenance Burden: Çok yüksek
```

### ✅ Clean Architecture (Sonra)

```
Shared Config Files: 5 dosya
Service-Specific Config: 2 dosya (sadece gerekli)
Shared Exceptions: 6 dosya
Service-Specific Exceptions: 2 dosya (sadece özel)
Code Duplication: %5
Maintenance Burden: Düşük
```

**İyileştirme:**

- 📉 Dosya sayısı: 36 → 15 (-58%)
- 📉 Kod tekrarı: %80 → %5 (-94%)
- 📈 Maintainability: 3x better

---

## 💡 Sonuç: Over-Engineering'den Kaçınma

### ✅ Golden Rule

```
IF (behavior is same across all services):
    → Put in SHARED module ✅
ELSE IF (behavior is unique to ONE service):
    → Put in SERVICE module ✅
ELSE:
    → You probably don't need it yet (YAGNI) ✅
```

### 🎯 Praktik Uygulama

**Yeni Exception Eklerken:**

1. ❓ Bu exception başka service'lerde de kullanılabilir mi?
   - ✅ EVET → shared/shared-domain/exception/
   - ❌ HAYIR → services/{service-name}/exception/

**Yeni Config Eklerken:**

1. ❓ Bu config tüm service'ler için aynı mı?

   - ✅ EVET → shared/shared-infrastructure/config/
   - ❌ HAYIR → services/{service-name}/config/

2. ❓ Bu config'e gerçekten şu an ihtiyaç var mı?
   - ✅ EVET → Ekle
   - ❌ HAYIR → YAGNI! Ekleme.

### 📊 Final Answer

**Her microservice'de exception ve config dosyaları olmalı mı?**

**CEVAP:** ❌ HAYIR - Bu over-engineering!

**Doğrusu:**

- ✅ Shared → Generic, common exceptions ve configs
- ✅ Service-Specific → Sadece gerçekten özel olanlar
- ✅ YAGNI → "Might need it later" için ekleme!

---

**Hazırlayan:** AI Kod Mimarı  
**Prensip:** YAGNI + DRY > Over-Engineering  
**Motto:** "The best code is no code at all" 🎯

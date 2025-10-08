# 🌍 Merkezi Hata Mesajı Yönetimi Rehberi

**Tarih:** 8 Ekim 2025  
**Prensip:** DRY + i18n + Consistency

---

## ❓ Problem: Hard-Coded Hata Mesajları

### ❌ Mevcut Durum (Kötü)

```java
// ❌ user-service
throw new ResourceNotFoundException("User not found: " + userId);
throw new ValidationException("Email is required");
throw new ValidationException("Email format is invalid");
throw new AccountLockedException("Account locked due to too many failed attempts");

// ❌ company-service
throw new ResourceNotFoundException("Company not found: " + companyId);
throw new ValidationException("Company name is required");
throw new ValidationException("Company name must be unique");

// ❌ contact-service
throw new ResourceNotFoundException("Contact not found: " + contactId);
throw new ValidationException("Contact value is required");
throw new ValidationException("Invalid phone number format");
```

**Sorunlar:**

🔴 **Kod içinde dağınık** - Mesajları bulmak zor  
🔴 **Consistency yok** - "is required" vs "required" vs "cannot be empty"  
🔴 **i18n imkansız** - Türkçe/İngilizce destek yok  
🔴 **Değişiklik zor** - Her yeri aramak gerek  
🔴 **Test zor** - Mesaj değişirse test patlar  
🔴 **Magic string** - Typo riski

---

## ✅ Çözüm: 3 Katmanlı Merkezi Yönetim

### 1️⃣ Shared Message Keys (Constants)

```
shared/shared-domain/
└── message/
    ├── ErrorMessageKeys.java           ✅ Message key constants
    └── ValidationMessageKeys.java      ✅ Validation key constants
```

### 2️⃣ Message Properties Files

```
shared/shared-infrastructure/
└── resources/
    └── messages/
        ├── errors_en.properties        ✅ English messages
        ├── errors_tr.properties        ✅ Turkish messages
        └── validations_en.properties   ✅ Validation messages
```

### 3️⃣ Message Service (Helper)

```
shared/shared-infrastructure/
└── service/
    └── MessageService.java             ✅ Message resolver
```

---

## 🏗️ Implementation: Adım Adım

### 📝 Step 1: Message Key Constants

```java
// ✅ shared-domain/message/ErrorMessageKeys.java
package com.fabricmanagement.shared.domain.message;

/**
 * Centralized Error Message Keys
 *
 * All error messages are defined here as constants.
 * Actual messages are in properties files for i18n support.
 */
public final class ErrorMessageKeys {

    private ErrorMessageKeys() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== Resource Not Found ==========
    public static final String RESOURCE_NOT_FOUND = "error.resource.not.found";
    public static final String USER_NOT_FOUND = "error.user.not.found";
    public static final String COMPANY_NOT_FOUND = "error.company.not.found";
    public static final String CONTACT_NOT_FOUND = "error.contact.not.found";

    // ========== Validation Errors ==========
    public static final String FIELD_REQUIRED = "error.validation.field.required";
    public static final String FIELD_INVALID = "error.validation.field.invalid";
    public static final String EMAIL_INVALID = "error.validation.email.invalid";
    public static final String PHONE_INVALID = "error.validation.phone.invalid";
    public static final String PASSWORD_WEAK = "error.validation.password.weak";

    // ========== Business Rules ==========
    public static final String DUPLICATE_RESOURCE = "error.business.duplicate";
    public static final String INSUFFICIENT_PERMISSION = "error.business.permission";
    public static final String INVALID_STATE = "error.business.invalid.state";

    // ========== Authentication ==========
    public static final String INVALID_CREDENTIALS = "error.auth.invalid.credentials";
    public static final String ACCOUNT_LOCKED = "error.auth.account.locked";
    public static final String TOKEN_EXPIRED = "error.auth.token.expired";
    public static final String PASSWORD_NOT_SET = "error.auth.password.not.set";

    // ========== Authorization ==========
    public static final String UNAUTHORIZED = "error.authz.unauthorized";
    public static final String FORBIDDEN = "error.authz.forbidden";
    public static final String TENANT_MISMATCH = "error.authz.tenant.mismatch";

    // ========== External Service ==========
    public static final String SERVICE_UNAVAILABLE = "error.external.service.unavailable";
    public static final String TIMEOUT = "error.external.timeout";

    // ========== User Service Specific ==========
    public static final String MAX_LOGIN_ATTEMPTS = "error.user.max.login.attempts";
    public static final String PASSWORD_ALREADY_SET = "error.user.password.already.set";
    public static final String CONTACT_NOT_VERIFIED = "error.user.contact.not.verified";

    // ========== Company Service Specific ==========
    public static final String MAX_USERS_LIMIT = "error.company.max.users.limit";
    public static final String SUBSCRIPTION_EXPIRED = "error.company.subscription.expired";
    public static final String COMPANY_INACTIVE = "error.company.inactive";

    // ========== Contact Service Specific ==========
    public static final String CONTACT_ALREADY_EXISTS = "error.contact.already.exists";
    public static final String VERIFICATION_CODE_INVALID = "error.contact.verification.code.invalid";
    public static final String VERIFICATION_CODE_EXPIRED = "error.contact.verification.code.expired";
}
```

### 📝 Step 2: Message Properties Files

```properties
# ✅ shared-infrastructure/resources/messages/errors_en.properties

# ========== Resource Not Found ==========
error.resource.not.found={0} not found: {1}
error.user.not.found=User not found: {0}
error.company.not.found=Company not found: {0}
error.contact.not.found=Contact not found: {0}

# ========== Validation Errors ==========
error.validation.field.required={0} is required
error.validation.field.invalid={0} is invalid
error.validation.email.invalid=Invalid email format: {0}
error.validation.phone.invalid=Invalid phone number format: {0}
error.validation.password.weak=Password must contain at least 8 characters, one uppercase, one lowercase, one digit, and one special character

# ========== Business Rules ==========
error.business.duplicate={0} already exists: {1}
error.business.permission=Insufficient permissions to perform this operation
error.business.invalid.state=Invalid state transition from {0} to {1}

# ========== Authentication ==========
error.auth.invalid.credentials=Invalid credentials. Please check your email/phone and password
error.auth.account.locked=Account locked for {0} minutes due to too many failed login attempts
error.auth.token.expired=Authentication token has expired. Please login again
error.auth.password.not.set=Password not set. Please setup your password first

# ========== Authorization ==========
error.authz.unauthorized=You are not authorized to access this resource
error.authz.forbidden=Access forbidden. You don't have the required role: {0}
error.authz.tenant.mismatch=Resource does not belong to your organization

# ========== External Service ==========
error.external.service.unavailable={0} service is currently unavailable. Please try again later
error.external.timeout=Request timeout while connecting to {0} service

# ========== User Service Specific ==========
error.user.max.login.attempts=Maximum login attempts exceeded. Account locked for {0} minutes
error.user.password.already.set=Password already set for this account
error.user.contact.not.verified=Contact {0} is not verified. Please verify before proceeding

# ========== Company Service Specific ==========
error.company.max.users.limit=Cannot add user. Maximum users limit reached: {0}/{1}
error.company.subscription.expired=Company subscription expired on {0}
error.company.inactive=Company is inactive. Please contact administrator

# ========== Contact Service Specific ==========
error.contact.already.exists=Contact already exists: {0}
error.contact.verification.code.invalid=Invalid verification code
error.contact.verification.code.expired=Verification code expired. Please request a new code
```

```properties
# ✅ shared-infrastructure/resources/messages/errors_tr.properties

# ========== Resource Not Found ==========
error.resource.not.found={0} bulunamadı: {1}
error.user.not.found=Kullanıcı bulunamadı: {0}
error.company.not.found=Firma bulunamadı: {0}
error.contact.not.found=İletişim bulunamadı: {0}

# ========== Validation Errors ==========
error.validation.field.required={0} zorunludur
error.validation.field.invalid={0} geçersizdir
error.validation.email.invalid=Geçersiz e-posta formatı: {0}
error.validation.phone.invalid=Geçersiz telefon numarası formatı: {0}
error.validation.password.weak=Şifre en az 8 karakter, bir büyük harf, bir küçük harf, bir rakam ve bir özel karakter içermelidir

# ========== Business Rules ==========
error.business.duplicate={0} zaten mevcut: {1}
error.business.permission=Bu işlemi gerçekleştirmek için yeterli yetkiniz yok
error.business.invalid.state={0} durumundan {1} durumuna geçersiz geçiş

# ========== Authentication ==========
error.auth.invalid.credentials=Geçersiz kimlik bilgileri. Lütfen e-posta/telefon ve şifrenizi kontrol edin
error.auth.account.locked=Çok fazla başarısız giriş denemesi nedeniyle hesap {0} dakika süreyle kilitlendi
error.auth.token.expired=Kimlik doğrulama token'ı süresi doldu. Lütfen tekrar giriş yapın
error.auth.password.not.set=Şifre ayarlanmamış. Lütfen önce şifrenizi oluşturun

# ========== Authorization ==========
error.authz.unauthorized=Bu kaynağa erişim yetkiniz yok
error.authz.forbidden=Erişim yasak. Gerekli role sahip değilsiniz: {0}
error.authz.tenant.mismatch=Kaynak organizasyonunuza ait değil

# ========== External Service ==========
error.external.service.unavailable={0} servisi şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyin
error.external.timeout={0} servisine bağlanırken zaman aşımı

# ========== User Service Specific ==========
error.user.max.login.attempts=Maksimum giriş denemesi aşıldı. Hesap {0} dakika süreyle kilitlendi
error.user.password.already.set=Bu hesap için şifre zaten ayarlanmış
error.user.contact.not.verified={0} iletişim bilgisi doğrulanmamış. Lütfen devam etmeden önce doğrulayın

# ========== Company Service Specific ==========
error.company.max.users.limit=Kullanıcı eklenemiyor. Maksimum kullanıcı limitine ulaşıldı: {0}/{1}
error.company.subscription.expired=Firma aboneliği {0} tarihinde sona erdi
error.company.inactive=Firma aktif değil. Lütfen yönetici ile iletişime geçin

# ========== Contact Service Specific ==========
error.contact.already.exists=İletişim zaten mevcut: {0}
error.contact.verification.code.invalid=Geçersiz doğrulama kodu
error.contact.verification.code.expired=Doğrulama kodu süresi doldu. Lütfen yeni bir kod isteyin
```

### 📝 Step 3: Message Service (Helper)

```java
// ✅ shared-infrastructure/service/MessageService.java
package com.fabricmanagement.shared.infrastructure.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Centralized Message Service
 *
 * Resolves error messages from properties files with i18n support.
 * All services use this to get consistent error messages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageSource messageSource;

    /**
     * Get message by key with current locale
     */
    public String getMessage(String key) {
        return getMessage(key, null, LocaleContextHolder.getLocale());
    }

    /**
     * Get message by key with parameters and current locale
     */
    public String getMessage(String key, Object... params) {
        return getMessage(key, params, LocaleContextHolder.getLocale());
    }

    /**
     * Get message by key with parameters and specific locale
     */
    public String getMessage(String key, Object[] params, Locale locale) {
        try {
            return messageSource.getMessage(key, params, locale);
        } catch (Exception e) {
            log.error("Error resolving message for key: {}", key, e);
            return key; // Fallback to key if message not found
        }
    }

    /**
     * Get message with default message if key not found
     */
    public String getMessageOrDefault(String key, String defaultMessage, Object... params) {
        try {
            return messageSource.getMessage(key, params, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            log.warn("Message not found for key: {}. Using default: {}", key, defaultMessage);
            return defaultMessage;
        }
    }
}
```

### 📝 Step 4: MessageSource Configuration

```java
// ✅ shared-infrastructure/config/MessageSourceConfig.java
package com.fabricmanagement.shared.infrastructure.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

/**
 * Message Source Configuration for i18n
 */
@Configuration
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames(
            "messages/errors",      // errors_en.properties, errors_tr.properties
            "messages/validations"  // validations_en.properties, validations_tr.properties
        );
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setUseCodeAsDefaultMessage(true); // Fallback to key if not found
        messageSource.setCacheSeconds(3600); // Cache for 1 hour
        return messageSource;
    }

    @Bean
    public AcceptHeaderLocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setSupportedLocales(java.util.Arrays.asList(
            Locale.ENGLISH,
            new Locale("tr") // Turkish
        ));
        return resolver;
    }
}
```

---

## 🎯 Kullanım Örnekleri

### 1️⃣ Exception Classes (Updated)

```java
// ✅ shared-domain/exception/ResourceNotFoundException.java
package com.fabricmanagement.shared.domain.exception;

import com.fabricmanagement.shared.domain.message.ErrorMessageKeys;
import lombok.Getter;

@Getter
public class ResourceNotFoundException extends DomainException {

    private final String messageKey;
    private final Object[] messageParams;

    public ResourceNotFoundException(String resource, String id) {
        super(ErrorMessageKeys.RESOURCE_NOT_FOUND, new Object[]{resource, id});
        this.messageKey = ErrorMessageKeys.RESOURCE_NOT_FOUND;
        this.messageParams = new Object[]{resource, id};
    }

    // Constructor overloading
    protected ResourceNotFoundException(String messageKey, Object... params) {
        super(messageKey, params);
        this.messageKey = messageKey;
        this.messageParams = params;
    }
}

// ✅ shared-domain/exception/DomainException.java (Base)
package com.fabricmanagement.shared.domain.exception;

import lombok.Getter;

@Getter
public abstract class DomainException extends RuntimeException {

    private final String messageKey;
    private final Object[] messageParams;
    private final String errorCode;

    protected DomainException(String messageKey, Object[] messageParams) {
        super(messageKey); // Temporary message (will be resolved by handler)
        this.messageKey = messageKey;
        this.messageParams = messageParams;
        this.errorCode = deriveErrorCode(messageKey);
    }

    protected DomainException(String messageKey, Object[] messageParams, String errorCode) {
        super(messageKey);
        this.messageKey = messageKey;
        this.messageParams = messageParams;
        this.errorCode = errorCode;
    }

    private String deriveErrorCode(String messageKey) {
        // error.user.not.found → USER_NOT_FOUND
        return messageKey.replace("error.", "")
            .replace(".", "_")
            .toUpperCase();
    }
}

// ✅ user-service/exception/AccountLockedException.java
package com.fabricmanagement.user.exception;

import com.fabricmanagement.shared.domain.exception.DomainException;
import com.fabricmanagement.shared.domain.message.ErrorMessageKeys;

public class AccountLockedException extends DomainException {

    public AccountLockedException(String contact, int lockMinutes) {
        super(ErrorMessageKeys.ACCOUNT_LOCKED,
              new Object[]{lockMinutes});
    }
}

// ✅ company-service/exception/MaxUsersLimitException.java
package com.fabricmanagement.company.exception;

import com.fabricmanagement.shared.domain.exception.DomainException;
import com.fabricmanagement.shared.domain.message.ErrorMessageKeys;

public class MaxUsersLimitException extends DomainException {

    public MaxUsersLimitException(int currentUsers, int maxUsers) {
        super(ErrorMessageKeys.MAX_USERS_LIMIT,
              new Object[]{currentUsers, maxUsers});
    }
}
```

### 2️⃣ Global Exception Handler (Updated)

```java
// ✅ shared-application/exception/GlobalExceptionHandler.java
package com.fabricmanagement.shared.application.exception;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.exception.DomainException;
import com.fabricmanagement.shared.infrastructure.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global Exception Handler with i18n support
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final MessageService messageService;

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex) {
        // Resolve message from properties file based on current locale
        String localizedMessage = messageService.getMessage(
            ex.getMessageKey(),
            ex.getMessageParams()
        );

        log.warn("Domain exception: {} - {}", ex.getErrorCode(), localizedMessage);

        HttpStatus status = determineHttpStatus(ex);

        return ResponseEntity.status(status)
            .body(ApiResponse.error(localizedMessage, ex.getErrorCode()));
    }

    private HttpStatus determineHttpStatus(DomainException ex) {
        String errorCode = ex.getErrorCode();

        // Map error codes to HTTP status
        if (errorCode.contains("NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        } else if (errorCode.contains("VALIDATION") || errorCode.contains("INVALID")) {
            return HttpStatus.BAD_REQUEST;
        } else if (errorCode.contains("UNAUTHORIZED") || errorCode.contains("AUTH")) {
            return HttpStatus.UNAUTHORIZED;
        } else if (errorCode.contains("FORBIDDEN") || errorCode.contains("PERMISSION")) {
            return HttpStatus.FORBIDDEN;
        } else if (errorCode.contains("DUPLICATE") || errorCode.contains("ALREADY_EXISTS")) {
            return HttpStatus.CONFLICT;
        }

        return HttpStatus.BAD_REQUEST;
    }
}
```

### 3️⃣ Service Layer Usage

```java
// ✅ UserService.java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserResponse getUser(UUID userId, UUID tenantId) {
        User user = userRepository.findActiveByIdAndTenantId(userId, tenantId)
            .orElseThrow(() ->
                new ResourceNotFoundException("User", userId.toString()));
            // Message: "User not found: {uuid}" (English)
            // Message: "Kullanıcı bulunamadı: {uuid}" (Turkish)

        return userMapper.toResponse(user);
    }
}

// ✅ AuthService.java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final LoginAttemptService loginAttemptService;

    public void login(LoginRequest request) {
        if (loginAttemptService.isLocked(request.getContact())) {
            throw new AccountLockedException(
                request.getContact(),
                loginAttemptService.getLockDurationMinutes()
            );
            // Message: "Account locked for 15 minutes..." (English)
            // Message: "Hesap 15 dakika süreyle kilitlendi..." (Turkish)
        }
    }
}

// ✅ CompanyService.java
@Service
@RequiredArgsConstructor
public class CompanyService {

    public void addUserToCompany(UUID companyId, UUID userId) {
        Company company = findCompany(companyId);

        if (company.getCurrentUsers() >= company.getMaxUsers()) {
            throw new MaxUsersLimitException(
                company.getCurrentUsers(),
                company.getMaxUsers()
            );
            // Message: "Cannot add user. Current: 50, Max: 50" (English)
            // Message: "Kullanıcı eklenemiyor. Mevcut: 50, Max: 50" (Turkish)
        }
    }
}
```

### 4️⃣ Client Usage (Frontend)

```http
### English Request
GET /api/v1/users/123e4567-e89b-12d3-a456-426614174000
Accept-Language: en
Authorization: Bearer {token}

HTTP/1.1 404 Not Found
{
  "success": false,
  "message": "User not found: 123e4567-e89b-12d3-a456-426614174000",
  "errorCode": "RESOURCE_NOT_FOUND",
  "timestamp": "2025-10-08T10:30:00"
}

### Turkish Request
GET /api/v1/users/123e4567-e89b-12d3-a456-426614174000
Accept-Language: tr
Authorization: Bearer {token}

HTTP/1.1 404 Not Found
{
  "success": false,
  "message": "Kullanıcı bulunamadı: 123e4567-e89b-12d3-a456-426614174000",
  "errorCode": "RESOURCE_NOT_FOUND",
  "timestamp": "2025-10-08T10:30:00"
}
```

---

## 📊 Dosya Yapısı

```
shared/
├── shared-domain/
│   └── message/
│       ├── ErrorMessageKeys.java           ✅ Message key constants
│       └── ValidationMessageKeys.java      ✅ Validation keys
│
├── shared-infrastructure/
│   ├── resources/
│   │   └── messages/
│   │       ├── errors_en.properties        ✅ English error messages
│   │       ├── errors_tr.properties        ✅ Turkish error messages
│   │       ├── validations_en.properties   ✅ English validation messages
│   │       └── validations_tr.properties   ✅ Turkish validation messages
│   │
│   ├── config/
│   │   └── MessageSourceConfig.java        ✅ MessageSource config
│   │
│   └── service/
│       └── MessageService.java             ✅ Message resolver

services/
├── user-service/
│   └── (Uses shared messages)              ✅ No hard-coded messages
├── company-service/
│   └── (Uses shared messages)              ✅ No hard-coded messages
└── contact-service/
    └── (Uses shared messages)              ✅ No hard-coded messages
```

---

## 🎯 Avantajlar

### ✅ Centralization

- Tüm mesajlar tek yerde
- Değişiklik tek yerden
- Consistency garantili

### ✅ i18n Support

- Çoklu dil desteği kolay
- Accept-Language header ile otomatik
- Yeni dil eklemek basit

### ✅ DRY Principle

- Mesaj tekrarı yok
- Properties file'da tek tanım
- Code duplication %0

### ✅ Maintainability

- Mesaj değişikliği kolay
- Test kırılmaz (key değişmediği sürece)
- Consistency kontrolü kolay

### ✅ Testability

```java
@Test
void testUserNotFound() {
    // Key test eder, mesaj değil
    assertThrows(ResourceNotFoundException.class, () -> {
        service.getUser(invalidId, tenantId);
    });
}
```

---

## 📈 Migration Strategy

### Phase 1: Setup Infrastructure

```
1. Create ErrorMessageKeys.java
2. Create errors_en.properties
3. Create MessageService.java
4. Update GlobalExceptionHandler
```

### Phase 2: Migrate Exceptions

```
1. Update DomainException base class
2. Update all exception constructors
3. Remove hard-coded messages
```

### Phase 3: Add i18n Support

```
1. Create errors_tr.properties
2. Configure MessageSource
3. Test with different locales
```

### Phase 4: Expand

```
1. Add validation messages
2. Add success messages
3. Add info messages
```

---

## 💡 Best Practices

### ✅ DO

1. **Use Hierarchical Keys**

   ```properties
   error.user.not.found
   error.company.not.found
   error.validation.email.invalid
   ```

2. **Use Placeholders**

   ```properties
   error.resource.not.found={0} not found: {1}
   # Usage: getMessage(key, "User", "123")
   ```

3. **Keep Keys Short but Descriptive**

   ```properties
   error.auth.invalid.credentials      ✅ Good
   err.authentication.invalid.creds    ❌ Too cryptic
   ```

4. **Group Related Messages**
   ```properties
   # ========== Authentication ==========
   error.auth.invalid.credentials=...
   error.auth.account.locked=...
   ```

### ❌ DON'T

1. **Don't Hard-Code Messages**

   ```java
   ❌ throw new Exception("User not found");
   ✅ throw new ResourceNotFoundException("User", userId);
   ```

2. **Don't Duplicate Messages**

   ```properties
   ❌ error.user.not.found=User not found
   ❌ error.user.does.not.exist=User not found
   ✅ error.resource.not.found={0} not found: {1}
   ```

3. **Don't Mix Languages in Code**
   ```java
   ❌ throw new Exception("Kullanıcı bulunamadı");
   ✅ Use message keys + properties
   ```

---

## 🎯 Sonuç

**Soru:** Hata mesajlarını tek yerden yönetmeli miyiz?

**Cevap:** ✅ **KESINLIKLE EVET!**

### Faydalar

| Özellik          | Hard-Coded ❌           | Merkezi Yönetim ✅ |
| ---------------- | ----------------------- | ------------------ |
| **i18n Desteği** | İmkansız                | Kolay              |
| **Consistency**  | %40                     | %100               |
| **Değişiklik**   | Tüm kod taranır         | Tek dosya          |
| **Test**         | Mesaj değişince kırılır | Key bazlı stabil   |
| **Maintenance**  | Zor                     | Kolay              |

### Implementation

```
1️⃣ ErrorMessageKeys.java         → Key constants
2️⃣ errors_{lang}.properties      → i18n messages
3️⃣ MessageService.java           → Message resolver
4️⃣ GlobalExceptionHandler        → Uses MessageService
5️⃣ Exception classes             → Uses keys
```

**Sonuç:** Profesyonel, maintainable, i18n-ready error handling! 🎯

---

**Hazırlayan:** AI Kod Mimarı  
**Prensip:** Centralization + i18n + DRY  
**Motto:** "One place to rule them all" 🌍

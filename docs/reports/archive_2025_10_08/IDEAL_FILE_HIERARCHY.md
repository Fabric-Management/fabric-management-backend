# 🗂️ İdeal Dosya Hiyerarşisi - Refactoring Öncesi ve Sonrası

**Tarih:** 8 Ekim 2025  
**Amaç:** Clean Architecture + SOLID + DRY + KISS prensiplerine uygun dosya yapısı

---

## 📊 Mevcut vs Hedef Karşılaştırması

| Metrik                                 | Mevcut    | Hedef     | İyileştirme                   |
| -------------------------------------- | --------- | --------- | ----------------------------- |
| **Toplam Dosya Sayısı (User Service)** | 47        | 62        | +32% (Separation of Concerns) |
| **Ortalama Dosya Boyutu**              | 250 satır | 120 satır | -52%                          |
| **Service Layer Dosya Sayısı**         | 3         | 8         | +167% (SRP)                   |
| **Yardımcı Sınıf Sayısı**              | 0         | 12        | +∞ (DRY)                      |

---

## 🏗️ 1. USER SERVICE - Hedef Mimari

### 📁 Tam Dosya Yapısı

```
services/user-service/
├── pom.xml
├── Dockerfile
├── README.md
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/fabricmanagement/user/
│   │   │       │
│   │   │       ├── UserServiceApplication.java
│   │   │       │
│   │   │       ├── api/                                    # 🌐 HTTP/REST Layer
│   │   │       │   ├── controller/
│   │   │       │   │   ├── UserController.java             [120 satır ✅]
│   │   │       │   │   ├── AuthController.java             [80 satır ✅]
│   │   │       │   │   └── PasswordResetController.java    [60 satır ✅]
│   │   │       │   │
│   │   │       │   ├── dto/
│   │   │       │   │   ├── request/
│   │   │       │   │   │   ├── CreateUserRequest.java
│   │   │       │   │   │   ├── UpdateUserRequest.java
│   │   │       │   │   │   ├── LoginRequest.java
│   │   │       │   │   │   ├── SetupPasswordRequest.java
│   │   │       │   │   │   ├── CheckContactRequest.java
│   │   │       │   │   │   └── UserSearchRequest.java      [YENİ ✨]
│   │   │       │   │   │
│   │   │       │   │   └── response/
│   │   │       │   │       ├── UserResponse.java
│   │   │       │   │       ├── LoginResponse.java
│   │   │       │   │       ├── CheckContactResponse.java
│   │   │       │   │       └── UserListResponse.java       [YENİ ✨]
│   │   │       │   │
│   │   │       │   └── exception/
│   │   │       │       └── UserServiceExceptionHandler.java [185 satır]
│   │   │       │
│   │   │       ├── application/                            # 🔧 Application Layer
│   │   │       │   │
│   │   │       │   ├── service/
│   │   │       │   │   ├── UserService.java                [150 satır ✅ Önce: 370]
│   │   │       │   │   ├── UserSearchService.java          [80 satır ✅ YENİ]
│   │   │       │   │   ├── AuthService.java                [250 satır ✅]
│   │   │       │   │   ├── LoginAttemptService.java        [164 satır ✅]
│   │   │       │   │   └── PasswordResetService.java       [120 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   ├── mapper/                             # 🗺️ DTO ↔ Entity Mapping [YENİ ✨]
│   │   │       │   │   ├── UserMapper.java                 [120 satır ✅ YENİ]
│   │   │       │   │   ├── UserResponseMapper.java         [80 satır ✅ YENİ]
│   │   │       │   │   └── AuthMapper.java                 [40 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   ├── validator/                          # ✅ Business Validation [YENİ ✨]
│   │   │       │   │   ├── UserValidator.java              [60 satır ✅ YENİ]
│   │   │       │   │   ├── PasswordValidator.java          [40 satır ✅ YENİ]
│   │   │       │   │   └── ContactValidator.java           [30 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   ├── helper/                             # 🛠️ Application Helpers [YENİ ✨]
│   │   │       │   │   ├── ContactInfoFetcher.java         [50 satır ✅ YENİ]
│   │   │       │   │   └── UserEnricher.java               [40 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   └── command/                            # 📝 Commands (CQRS - optional)
│   │   │       │       ├── RequestPasswordResetCommand.java
│   │   │       │       ├── ResetPasswordCommand.java
│   │   │       │       └── VerifyResetCodeCommand.java
│   │   │       │
│   │   │       ├── domain/                                 # 🎯 Domain Layer (Core Business)
│   │   │       │   │
│   │   │       │   ├── aggregate/
│   │   │       │   │   └── User.java                       [250 satır ✅ Önce: 342]
│   │   │       │   │
│   │   │       │   ├── service/                            # 🎲 Domain Services [YENİ ✨]
│   │   │       │   │   ├── UserDomainService.java          [100 satır ✅ YENİ]
│   │   │       │   │   └── PasswordDomainService.java      [60 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   ├── event/
│   │   │       │   │   ├── UserCreatedEvent.java
│   │   │       │   │   ├── UserUpdatedEvent.java
│   │   │       │   │   ├── UserDeletedEvent.java
│   │   │       │   │   ├── PasswordResetRequestedEvent.java
│   │   │       │   │   └── PasswordResetCompletedEvent.java
│   │   │       │   │
│   │   │       │   └── valueobject/
│   │   │       │       ├── PasswordResetToken.java
│   │   │       │       ├── RegistrationType.java
│   │   │       │       └── UserStatus.java
│   │   │       │
│   │   │       ├── infrastructure/                         # 🏗️ Infrastructure Layer
│   │   │       │   │
│   │   │       │   ├── repository/
│   │   │       │   │   ├── UserRepository.java             [Enhanced with custom queries ✅]
│   │   │       │   │   └── PasswordResetTokenRepository.java
│   │   │       │   │
│   │   │       │   ├── client/                             # 🌐 External Service Clients
│   │   │       │   │   ├── ContactServiceClient.java
│   │   │       │   │   ├── ContactServiceClientImpl.java   [YENİ ✨ - Fallback logic]
│   │   │       │   │   └── dto/
│   │   │       │   │       ├── ContactDto.java
│   │   │       │   │       └── CreateContactDto.java
│   │   │       │   │
│   │   │       │   ├── messaging/                          # 📨 Event Publishing/Listening
│   │   │       │   │   ├── publisher/
│   │   │       │   │   │   └── UserEventPublisher.java
│   │   │       │   │   │
│   │   │       │   │   ├── listener/
│   │   │       │   │   │   ├── CompanyEventListener.java
│   │   │       │   │   │   └── ContactEventListener.java
│   │   │       │   │   │
│   │   │       │   │   └── event/
│   │   │       │   │       ├── CompanyCreatedEvent.java
│   │   │       │   │       ├── CompanyDeletedEvent.java
│   │   │       │   │       ├── CompanyUpdatedEvent.java
│   │   │       │   │       ├── ContactCreatedEvent.java
│   │   │       │   │       ├── ContactDeletedEvent.java
│   │   │       │   │       └── ContactVerifiedEvent.java
│   │   │       │   │
│   │   │       │   ├── audit/
│   │   │       │   │   └── SecurityAuditLogger.java
│   │   │       │   │
│   │   │       │   ├── cache/                              # 💾 Cache Layer [YENİ ✨]
│   │   │       │   │   ├── UserCacheService.java           [YENİ ✨]
│   │   │       │   │   └── ContactCacheService.java        [YENİ ✨]
│   │   │       │   │
│   │   │       │   └── config/
│   │   │       │       ├── FeignClientConfig.java
│   │   │       │       ├── KafkaErrorHandlingConfig.java
│   │   │       │       └── CacheConfig.java                [YENİ ✨]
│   │   │       │
│   │   │       └── config/                                 # ⚙️ Service Configuration
│   │   │           ├── WebConfig.java                      [YENİ ✨]
│   │   │           ├── SecurityConfig.java
│   │   │           └── JpaConfig.java                      [YENİ ✨]
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/
│   │           └── migration/
│   │               └── V1__init_user_schema.sql
│   │
│   └── test/
│       └── java/
│           └── com/fabricmanagement/user/
│               ├── api/
│               │   └── UserControllerTest.java              [YENİ ✨]
│               ├── application/
│               │   ├── service/
│               │   │   ├── UserServiceTest.java             [YENİ ✨]
│               │   │   └── AuthServiceTest.java             [YENİ ✨]
│               │   └── mapper/
│               │       └── UserMapperTest.java              [YENİ ✨]
│               └── domain/
│                   └── aggregate/
│                       └── UserTest.java                    [YENİ ✨]
```

---

## 🏗️ 2. COMPANY SERVICE - Hedef Mimari

```
services/company-service/
├── pom.xml
├── Dockerfile
├── README.md
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/fabricmanagement/company/
│   │   │       │
│   │   │       ├── CompanyServiceApplication.java
│   │   │       │
│   │   │       ├── api/                                    # 🌐 HTTP/REST Layer
│   │   │       │   ├── controller/
│   │   │       │   │   ├── CompanyController.java          [180 satır ✅]
│   │   │       │   │   ├── CompanyUserController.java      [90 satır ✅]
│   │   │       │   │   └── CompanyContactController.java   [89 satır ✅]
│   │   │       │   │
│   │   │       │   ├── dto/
│   │   │       │   │   ├── request/
│   │   │       │   │   │   ├── CreateCompanyRequest.java
│   │   │       │   │   │   ├── UpdateCompanyRequest.java
│   │   │       │   │   │   ├── UpdateCompanySettingsRequest.java
│   │   │       │   │   │   ├── UpdateSubscriptionRequest.java
│   │   │       │   │   │   ├── AddUserToCompanyRequest.java
│   │   │       │   │   │   └── AddContactToCompanyRequest.java
│   │   │       │   │   │
│   │   │       │   │   └── response/
│   │   │       │   │       ├── CompanyResponse.java
│   │   │       │   │       └── CompanyListResponse.java    [YENİ ✨]
│   │   │       │   │
│   │   │       │   └── exception/
│   │   │       │       └── CompanyExceptionHandler.java
│   │   │       │
│   │   │       ├── application/                            # 🔧 Application Layer
│   │   │       │   │
│   │   │       │   ├── service/
│   │   │       │   │   ├── CompanyService.java             [150 satır ✅ Önce: 269]
│   │   │       │   │   ├── CompanyUserService.java         [171 satır ✅]
│   │   │       │   │   ├── CompanyContactService.java      [161 satır ✅]
│   │   │       │   │   ├── CompanySearchService.java       [80 satır ✅ YENİ]
│   │   │       │   │   └── CompanySubscriptionService.java [120 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   ├── mapper/                             # 🗺️ Mappers [YENİ ✨]
│   │   │       │   │   ├── CompanyMapper.java              [100 satır ✅ YENİ]
│   │   │       │   │   ├── CompanyUserMapper.java          [60 satır ✅ YENİ]
│   │   │       │   │   └── CompanyContactMapper.java       [50 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   ├── validator/                          # ✅ Validators [YENİ ✨]
│   │   │       │   │   ├── CompanyValidator.java           [70 satır ✅ YENİ]
│   │   │       │   │   └── SubscriptionValidator.java      [50 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   ├── command/                            # 📝 Complex Commands Only
│   │   │       │   │   └── handler/
│   │   │       │   │       ├── RenewSubscriptionHandler.java [YENİ ✨]
│   │   │       │   │       └── BulkUserImportHandler.java    [YENİ ✨]
│   │   │       │   │
│   │   │       │   └── query/                              # 🔍 Complex Queries Only
│   │   │       │       └── handler/
│   │   │       │           └── CompanyAnalyticsQueryHandler.java [YENİ ✨]
│   │   │       │
│   │   │       ├── domain/                                 # 🎯 Domain Layer
│   │   │       │   │
│   │   │       │   ├── aggregate/
│   │   │       │   │   └── Company.java                    [280 satır ✅ Önce: 368]
│   │   │       │   │
│   │   │       │   ├── service/                            # 🎲 Domain Services [YENİ ✨]
│   │   │       │   │   └── CompanyDomainService.java       [80 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   ├── event/
│   │   │       │   │   ├── CompanyCreatedEvent.java
│   │   │       │   │   ├── CompanyUpdatedEvent.java
│   │   │       │   │   └── CompanyDeletedEvent.java
│   │   │       │   │
│   │   │       │   ├── valueobject/
│   │   │       │   │   ├── CompanyName.java
│   │   │       │   │   ├── CompanyStatus.java
│   │   │       │   │   ├── CompanyType.java
│   │   │       │   │   ├── CompanyUser.java
│   │   │       │   │   └── Industry.java
│   │   │       │   │
│   │   │       │   └── exception/
│   │   │       │       ├── CompanyAlreadyExistsException.java
│   │   │       │       ├── CompanyNotFoundException.java
│   │   │       │       ├── MaxUsersLimitException.java
│   │   │       │       └── UnauthorizedCompanyAccessException.java
│   │   │       │
│   │   │       ├── infrastructure/                         # 🏗️ Infrastructure Layer
│   │   │       │   │
│   │   │       │   ├── repository/
│   │   │       │   │   ├── CompanyRepository.java          [Enhanced ✅]
│   │   │       │   │   ├── CompanyUserRepository.java
│   │   │       │   │   └── CompanyEventStore.java
│   │   │       │   │
│   │   │       │   ├── client/
│   │   │       │   │   ├── UserServiceClient.java
│   │   │       │   │   ├── ContactServiceClient.java
│   │   │       │   │   └── dto/
│   │   │       │   │       ├── UserDto.java
│   │   │       │   │       └── ContactDto.java
│   │   │       │   │
│   │   │       │   ├── messaging/
│   │   │       │   │   ├── CompanyEventPublisher.java
│   │   │       │   │   └── CompanyDomainEventPublisher.java
│   │   │       │   │
│   │   │       │   ├── cache/                              # 💾 Cache [YENİ ✨]
│   │   │       │   │   └── CompanyCacheService.java        [YENİ ✨]
│   │   │       │   │
│   │   │       │   ├── security/
│   │   │       │   │   ├── TenantContext.java
│   │   │       │   │   └── TenantInterceptor.java
│   │   │       │   │
│   │   │       │   └── config/
│   │   │       │       └── WebConfig.java
│   │   │       │
│   │   │       └── config/
│   │   │
│   │   └── resources/
│   │       └── application.yml
│   │
│   └── test/
│       └── java/
│           └── com/fabricmanagement/company/
│               ├── api/
│               │   └── CompanyControllerTest.java           [YENİ ✨]
│               ├── application/
│               │   └── service/
│               │       └── CompanyServiceTest.java          [YENİ ✨]
│               └── domain/
│                   └── aggregate/
│                       └── CompanyTest.java                 [YENİ ✨]
```

---

## 🏗️ 3. CONTACT SERVICE - Hedef Mimari

```
services/contact-service/
├── pom.xml
├── Dockerfile
├── README.md
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/fabricmanagement/contact/
│   │   │       │
│   │   │       ├── ContactServiceApplication.java
│   │   │       │
│   │   │       ├── api/                                    # 🌐 HTTP/REST Layer
│   │   │       │   ├── controller/
│   │   │       │   │   └── ContactController.java          [200 satır ✅ Önce: 289]
│   │   │       │   │
│   │   │       │   ├── dto/
│   │   │       │   │   ├── request/
│   │   │       │   │   │   ├── CreateContactRequest.java
│   │   │       │   │   │   ├── UpdateContactRequest.java
│   │   │       │   │   │   ├── VerifyContactRequest.java
│   │   │       │   │   │   └── CheckContactAvailabilityRequest.java
│   │   │       │   │   │
│   │   │       │   │   └── response/
│   │   │       │   │       ├── ContactResponse.java
│   │   │       │   │       └── ContactAvailabilityResponse.java
│   │   │       │   │
│   │   │       │   └── exception/
│   │   │       │       └── ContactExceptionHandler.java    [YENİ ✨]
│   │   │       │
│   │   │       ├── application/                            # 🔧 Application Layer
│   │   │       │   │
│   │   │       │   ├── service/
│   │   │       │   │   ├── ContactService.java             [180 satır ✅ Önce: 356]
│   │   │       │   │   ├── ContactVerificationService.java [100 satır ✅ YENİ]
│   │   │       │   │   └── NotificationService.java        [123 satır ✅]
│   │   │       │   │
│   │   │       │   ├── mapper/                             # 🗺️ Mappers [YENİ ✨]
│   │   │       │   │   └── ContactMapper.java              [80 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   ├── validator/                          # ✅ Validators [YENİ ✨]
│   │   │       │   │   ├── ContactValidator.java           [60 satır ✅ YENİ]
│   │   │       │   │   └── PhoneValidator.java             [40 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   └── helper/                             # 🛠️ Helpers [YENİ ✨]
│   │   │       │       └── VerificationCodeGenerator.java  [30 satır ✅ YENİ]
│   │   │       │
│   │   │       ├── domain/                                 # 🎯 Domain Layer
│   │   │       │   │
│   │   │       │   ├── aggregate/
│   │   │       │   │   └── Contact.java                    [200 satır ✅ Önce: 232]
│   │   │       │   │
│   │   │       │   ├── service/                            # 🎲 Domain Services [YENİ ✨]
│   │   │       │   │   └── ContactDomainService.java       [60 satır ✅ YENİ]
│   │   │       │   │
│   │   │       │   ├── event/
│   │   │       │   │   ├── ContactCreatedEvent.java
│   │   │       │   │   ├── ContactUpdatedEvent.java
│   │   │       │   │   ├── ContactDeletedEvent.java
│   │   │       │   │   └── ContactVerifiedEvent.java
│   │   │       │   │
│   │   │       │   └── valueobject/
│   │   │       │       ├── ContactType.java
│   │   │       │       ├── PhoneNumber.java
│   │   │       │       └── Address.java
│   │   │       │
│   │   │       ├── infrastructure/                         # 🏗️ Infrastructure Layer
│   │   │       │   │
│   │   │       │   ├── repository/
│   │   │       │   │   └── ContactRepository.java          [Enhanced ✅]
│   │   │       │   │
│   │   │       │   ├── notification/                       # 📧 External Services [YENİ ✨]
│   │   │       │   │   ├── EmailNotificationService.java   [YENİ ✨]
│   │   │       │   │   └── SmsNotificationService.java     [YENİ ✨]
│   │   │       │   │
│   │   │       │   ├── cache/                              # 💾 Cache [YENİ ✨]
│   │   │       │   │   └── ContactCacheService.java        [YENİ ✨]
│   │   │       │   │
│   │   │       │   └── config/
│   │   │       │       └── NotificationConfig.java
│   │   │       │
│   │   │       └── config/
│   │   │
│   │   └── resources/
│   │       └── application.yml
│   │
│   └── test/
│       └── java/
│           └── com/fabricmanagement/contact/
│               └── ... (test structure)
```

---

## 🏗️ 4. SHARED MODULES - Hedef Mimari

### 📁 shared-application/

```
shared/shared-application/
├── pom.xml
│
└── src/
    └── main/
        └── java/
            └── com/fabricmanagement/shared/application/
                │
                ├── response/
                │   ├── ApiResponse.java
                │   └── PaginatedResponse.java              [YENİ ✨]
                │
                ├── context/                                # 🔐 Security Context [YENİ ✨]
                │   └── SecurityContext.java                [YENİ ✨]
                │
                ├── annotation/                             # 📝 Custom Annotations [YENİ ✨]
                │   ├── CurrentSecurityContext.java         [YENİ ✨]
                │   ├── AdminOnly.java                      [YENİ ✨]
                │   ├── AdminOrManager.java                 [YENİ ✨]
                │   └── Authenticated.java                  [YENİ ✨]
                │
                ├── resolver/                               # 🔧 Argument Resolvers [YENİ ✨]
                │   └── SecurityContextResolver.java        [YENİ ✨]
                │
                ├── controller/                             # 🎮 Base Controllers [YENİ ✨]
                │   └── BaseController.java                 [YENİ ✨ - Optional]
                │
                └── util/                                   # 🛠️ Utilities
                    ├── DateUtils.java                      [YENİ ✨]
                    └── StringUtils.java                    [YENİ ✨]
```

### 📁 shared-domain/

```
shared/shared-domain/
├── pom.xml
│
└── src/
    └── main/
        └── java/
            └── com/fabricmanagement/shared/domain/
                │
                ├── base/
                │   ├── BaseEntity.java                     [131 satır ✅]
                │   └── AggregateRoot.java                  [YENİ ✨]
                │
                ├── exception/
                │   ├── DomainException.java
                │   ├── UserNotFoundException.java
                │   ├── CompanyNotFoundException.java
                │   ├── ContactNotFoundException.java       [YENİ ✨]
                │   ├── InvalidPasswordException.java
                │   ├── PasswordAlreadySetException.java
                │   ├── ContactNotVerifiedException.java
                │   ├── InvalidUserStatusException.java
                │   ├── AccountLockedException.java
                │   └── ValidationException.java            [YENİ ✨]
                │
                ├── event/
                │   ├── DomainEvent.java
                │   └── DomainEventPublisher.java
                │
                └── outbox/
                    └── OutboxEvent.java
```

### 📁 shared-infrastructure/

```
shared/shared-infrastructure/
├── pom.xml
│
└── src/
    └── main/
        └── java/
            └── com/fabricmanagement/shared/infrastructure/
                │
                ├── constants/
                │   ├── ValidationConstants.java            [43 satır ✅]
                │   ├── SecurityRoles.java                  [YENİ ✨]
                │   └── CacheKeys.java                      [YENİ ✨]
                │
                ├── security/
                │   ├── SecurityContextHolder.java
                │   └── SecurityUtils.java                  [YENİ ✨]
                │
                ├── config/
                │   ├── JpaAuditingConfig.java
                │   ├── RedisConfig.java                    [YENİ ✨]
                │   └── WebMvcConfig.java                   [YENİ ✨]
                │
                └── util/
                    ├── JsonUtils.java                      [YENİ ✨]
                    └── UuidUtils.java                      [YENİ ✨]
```

### 📁 shared-security/

```
shared/shared-security/
├── pom.xml
│
└── src/
    └── main/
        └── java/
            └── com/fabricmanagement/shared/security/
                │
                ├── config/
                │   ├── DefaultSecurityConfig.java          [142 satır]
                │   └── CorsConfig.java                     [YENİ ✨]
                │
                ├── jwt/
                │   ├── JwtTokenProvider.java
                │   ├── JwtAuthenticationFilter.java        [YENİ ✨]
                │   └── JwtTokenValidator.java              [YENİ ✨]
                │
                └── annotation/
                    ├── RequiresTenant.java                 [YENİ ✨]
                    └── AuditLog.java                       [YENİ ✨]
```

---

## 🏗️ 5. API GATEWAY - Hedef Mimari

```
services/api-gateway/
├── pom.xml
├── Dockerfile
├── README.md
│
└── src/
    └── main/
        ├── java/
        │   └── com/fabricmanagement/gateway/
        │       │
        │       ├── GatewayApplication.java
        │       │
        │       ├── config/
        │       │   ├── GatewayConfig.java
        │       │   ├── CorsConfig.java                     [YENİ ✨]
        │       │   ├── RateLimitConfig.java                [YENİ ✨]
        │       │   └── SmartKeyResolver.java               [102 satır]
        │       │
        │       ├── security/
        │       │   ├── JwtAuthenticationFilter.java        [156 satır]
        │       │   └── SecurityConfig.java
        │       │
        │       ├── filter/                                 # 🔍 Custom Filters [YENİ ✨]
        │       │   ├── LoggingFilter.java                  [YENİ ✨]
        │       │   ├── TenantFilter.java                   [YENİ ✨]
        │       │   └── RequestValidationFilter.java        [YENİ ✨]
        │       │
        │       └── exception/
        │           └── GlobalExceptionHandler.java         [YENİ ✨]
        │
        └── resources/
            └── application.yml
```

---

## 📊 Katman Sorumlulukları ve Dosya İlişkileri

### 🌐 API Layer (Controller)

**Dosyalar:**

- `*Controller.java`

**Sorumluluklar:**

- ✅ HTTP request/response handling
- ✅ Input validation (@Valid)
- ✅ Authorization (@PreAuthorize, custom annotations)
- ✅ DTO conversion (delegates to Service)
- ✅ Response wrapping (ApiResponse)

**Bağımlılıklar:**

```
Controller
  ↓
  ├─→ Service (business logic)
  └─→ SecurityContext (injection)
```

### 🔧 Application Layer

#### Service

**Dosyalar:**

- `*Service.java`
- `*SearchService.java`
- `*SubscriptionService.java`

**Sorumluluklar:**

- ✅ Business logic orchestration
- ✅ Transaction management (@Transactional)
- ✅ Event publishing
- ✅ External service coordination

**Bağımlılıklar:**

```
Service
  ↓
  ├─→ Repository (data access)
  ├─→ Mapper (DTO ↔ Entity)
  ├─→ Validator (business rules)
  ├─→ EventPublisher (events)
  └─→ ExternalClient (feign)
```

#### Mapper

**Dosyalar:**

- `*Mapper.java`

**Sorumluluklar:**

- ✅ DTO → Entity conversion
- ✅ Entity → DTO conversion
- ✅ List mapping with batch optimization
- ✅ External service data fetching (for enrichment)

**Bağımlılıklar:**

```
Mapper
  ↓
  └─→ ExternalClient (optional, for enrichment)
```

#### Validator

**Dosyalar:**

- `*Validator.java`

**Sorumluluklar:**

- ✅ Business rule validation
- ✅ Complex validation logic
- ✅ Cross-field validation
- ✅ External validation (API calls)

**Bağımlılıklar:**

```
Validator
  ↓
  ├─→ Repository (existence checks)
  └─→ ExternalClient (optional)
```

### 🎯 Domain Layer

#### Aggregate

**Dosyalar:**

- `User.java`, `Company.java`, `Contact.java`

**Sorumluluklar:**

- ✅ Business invariants
- ✅ Domain logic
- ✅ Domain event generation
- ✅ Entity lifecycle

**Bağımlılıklar:**

```
Aggregate
  ↓
  ├─→ ValueObject
  └─→ DomainEvent
```

#### Domain Service

**Dosyalar:**

- `*DomainService.java`

**Sorumluluklar:**

- ✅ Cross-aggregate business logic
- ✅ Complex domain operations
- ✅ Domain calculations

**Bağımlılıklar:**

```
DomainService
  ↓
  └─→ Aggregate (domain entities)
```

### 🏗️ Infrastructure Layer

#### Repository

**Dosyalar:**

- `*Repository.java`

**Sorumluluklar:**

- ✅ Data persistence
- ✅ Custom queries (@Query)
- ✅ Common filters (tenant, deleted, etc.)

**Bağımlılıklar:**

```
Repository
  ↓
  └─→ JpaRepository (Spring Data)
```

---

## 📈 Refactoring Checklist

### ✅ Hafta 1 - Mapper Pattern

- [ ] `UserMapper.java` oluştur
- [ ] `CompanyMapper.java` oluştur
- [ ] `ContactMapper.java` oluştur
- [ ] Service'lerden mapping logic'i çıkar
- [ ] Mapper'ları test et

### ✅ Hafta 1 - Security Context Injection

- [ ] `SecurityContext.java` oluştur
- [ ] `@CurrentSecurityContext` annotation oluştur
- [ ] `SecurityContextResolver.java` oluştur
- [ ] `WebMvcConfig` ekle
- [ ] Controller'ları refactor et

### ✅ Hafta 2 - Repository Enhancement

- [ ] Custom query methodları ekle
- [ ] `findActiveByIdAndTenantId` methodları
- [ ] `existsActive*` methodları
- [ ] Service'lerden filter logic'i çıkar

### ✅ Hafta 2 - Exception Standardization

- [ ] Domain exception sınıfları oluştur
- [ ] `GlobalExceptionHandler` güncelle
- [ ] Service'lerdeki RuntimeException'ları değiştir

### ✅ Hafta 3 - Service Refactoring

- [ ] `UserSearchService` oluştur
- [ ] `ContactVerificationService` oluştur
- [ ] `CompanySearchService` oluştur
- [ ] Service'leri böl (SRP)

### ✅ Hafta 4 - CQRS Simplification

- [ ] Company Service handler'ları kaldır
- [ ] Basit CRUD için direkt service pattern
- [ ] Sadece complex işler için command/query pattern

---

## 🎯 Sonuç

**Hedef Mimari Özellikleri:**

✅ **Single Responsibility** - Her dosya tek bir sorumluluğa sahip  
✅ **DRY** - Kod tekrarı minimum  
✅ **KISS** - Basit ve anlaşılır  
✅ **SOLID** - Tüm prensipler uygulanmış  
✅ **YAGNI** - Gereksiz abstraction yok  
✅ **Test Edilebilir** - Her katman bağımsız test edilebilir  
✅ **Bakımı Kolay** - Yeni geliştirici 1 gün içinde anlayabilir

**Dosya Sayıları:**

| Service         | Önce | Sonra | Artış | Neden                               |
| --------------- | ---- | ----- | ----- | ----------------------------------- |
| User Service    | 47   | 62    | +32%  | Mapper, Validator, Helper, Tests    |
| Company Service | 67   | 58    | -13%  | Handler'lar kaldırıldı              |
| Contact Service | 21   | 35    | +67%  | Mapper, Validator, Services bölündü |

**Not:** Dosya sayısı artışı kötü değil! Her dosya daha küçük, daha odaklanmış ve test edilebilir.

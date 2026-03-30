# Platform ↔ Human ↔ Tenant Decoupling Refactoring Plan

**Tarih:** 2026-03-30
**Versiyon:** 1.0
**Kapsam:** platform/user → human/core/employee bağımlılığının çözülmesi, multi-tenancy coupling analizi
**Tahmini Süre:** 3-4 Sprint (her Sprint 2 hafta)

---

## 1. Mevcut Durum Analizi

### 1.1 Tespit Edilen Coupling Noktaları

**18 coupling noktası** tespit edildi. Tümü **tek yönlü** (platform → human):

| # | Dosya | Bağımlılık Türü | Şiddet |
|---|-------|-----------------|--------|
| 1 | `platform/user/app/UserQueryService.java` | `EmployeeService.getEmployeeByUserId()` — her query'de enrichment | **GÜÇLÜ** |
| 2 | `platform/user/app/UserQueryService.java` | `EmployeeService.getEmployeesByUserIds()` — batch enrichment | **GÜÇLÜ** |
| 3 | `platform/user/app/UserService.java` | `EmployeeService` injection | **GÜÇLÜ** |
| 4 | `platform/user/app/UserCreationService.java` | `EmployeeService.createOrUpdateEmployee()` — User oluşturulunca Employee da yaratılıyor | **GÜÇLÜ** |
| 5 | `platform/user/app/UserProfileService.java` | `EmployeeService` + `Employee` entity + `EmergencyContact` | **GÜÇLÜ** |
| 6 | `platform/user/app/UserOnboardingService.java` | `EmployeeService.getEmployeeByUserId()` — onboarding enrichment | **GÜÇLÜ** |
| 7 | `platform/auth/app/PasswordSetupService.java` | `EmployeeService` — auth response'a Employee verisi ekleniyor | **GÜÇLÜ** |
| 8 | `platform/admin/app/PlatformAdminService.java` | `EmployeeService` — cross-tenant admin sorguları | **GÜÇLÜ** |
| 9 | `platform/user/dto/UserDto.java` | `Employee` entity direct import — `from(User, Employee)` mapping | **GÜÇLÜ** |
| 10 | `platform/user/dto/CreateInternalUserRequest.java` | `Gender`, `Title` enum import | **ORTA** |
| 11 | `platform/user/dto/UpdateUserProfileRequest.java` | `Gender`, `Title` enum import | **ORTA** |
| 12 | `platform/user/api/controller/UserController.java` | `EmployeeService` injection | **ORTA** |
| 13 | `platform/user/app/EmployeeTerminationEventListener.java` | `EmployeeTerminatedEvent` — event listener | **ZAYIF** |
| 14 | `platform/user/app/UserCacheInvalidationService.java` | `EmployeeUpdatedEvent` — cache invalidation | **ZAYIF** |
| 15 | `platform/ai/app/AIFunctionCaller.java` | `FiberFacade`, `MaterialFacade`, `FiberRepository` — AI function calling | **AYRI SORUN** |

### 1.2 Coupling'in Kök Nedeni

Temel sorun **User-Employee Identity Duality** (Kullanıcı-Çalışan Kimlik İkiliği):

```
┌──────────────────────────────────────────────────────────┐
│  Şu anki durum: UserDto = User + Employee                │
│                                                          │
│  platform/user/UserDto                                   │
│  ├── id, firstName, lastName (User entity'den)           │
│  ├── role, department (User entity'den)                  │
│  └── title, gender, hireDate, employeeNumber (Employee)  │ ← COUPLING
│                                                          │
│  Her user query'si → EmployeeService.get() çağırıyor     │
│  UserCreation → Employee.create() tetikliyor             │
│  UserProfile update → Employee.update() tetikliyor       │
└──────────────────────────────────────────────────────────┘
```

Bu duality mantıklı bir iş kuralı: "İç kullanıcı (INTERNAL) aynı zamanda çalışandır ve API yanıtlarında HR bilgileri birleşik gösterilmelidir." Ancak implementasyon açısından DDD'nin **Bounded Context** prensibini ihlal ediyor.

### 1.3 Multi-Tenancy Coupling Durumu

Multi-tenancy coupling'i **kabul edilebilir** seviyede:

- `TenantContext` → `common/infrastructure` içinde, tüm modüller buraya bağımlı (bu doğru)
- `BaseEntity.tenantId` → `@PrePersist` ile otomatik set ediliyor
- Repository'ler → explicit `findByTenantIdAndX()` pattern'ı (implicit filter yok)
- **RLS (Row Level Security) yok** → Tenant izolasyonu tamamen application layer'da

**Risk:** RLS yokluğu, developer'ın `tenantId` filtresi koymayı unutması durumunda cross-tenant data leakage yaratabilir. Ancak bu ayrı bir sorun olup bu refactoring planının kapsamı dışındadır.

---

## 2. Refactoring Stratejisi: "Employee Projection Port" Pattern

### 2.1 Hedef Mimari

```
┌──────────────────────────────────────────────────────────────────────┐
│                        HEDEF MİMARİ                                  │
│                                                                      │
│  ┌─────────────────────────┐     ┌──────────────────────────────┐   │
│  │  platform/user          │     │  human/core/employee         │   │
│  │                         │     │                              │   │
│  │  UserQueryService       │     │  EmployeeService             │   │
│  │    ↓ kullanır           │     │    ↑ implements              │   │
│  │  EmployeeProjectionPort │────→│  EmployeeProjectionAdapter   │   │
│  │  (interface, domain/)   │     │  (app/adapter/)              │   │
│  │                         │     │                              │   │
│  │  UserDto                │     │                              │   │
│  │    ↓ kullanır           │     │                              │   │
│  │  EmployeeSnapshot       │     │                              │   │
│  │  (record, domain/)      │     │                              │   │
│  └─────────────────────────┘     └──────────────────────────────┘   │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  Shared Kernel: common/infrastructure/identity/             │    │
│  │  ├── Gender enum                                            │    │
│  │  ├── Title enum                                             │    │
│  │  └── EmergencyContactData record                            │    │
│  └─────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.2 Neden Bu Strateji?

Üç alternatif değerlendirildi:

| Strateji | Artı | Eksi | Karar |
|----------|------|------|-------|
| **A: Tam event-driven** (eventual consistency) | En loose coupling | Karmaşık, data tutarsızlığı riski, User query'si anında Employee gerektirir | ❌ Riskli |
| **B: Shared Kernel + Port/Adapter** | Minimal değişiklik, type-safe, senkron | Shared kernel genişleyebilir | ✅ **SEÇİLDİ** |
| **C: API Gateway aggregation** | Modüller tamamen bağımsız | Microservice pattern'ı, monolit için overkill | ❌ Uygun değil |

**Strateji B** seçildi çünkü:
1. User API yanıtlarında Employee verisi **senkron** olarak gerekli (eventual consistency uygun değil)
2. Port/Adapter pattern zaten projede mevcut (FlowBoard → StockQueryPort)
3. Shared Kernel, DDD'de birlikte evrimleşen kavramlar için kabul edilen bir pattern

---

## 3. Sprint Bazlı Uygulama Planı

### Sprint 1: Shared Kernel + Port Interface (Temel Hazırlık)

#### Adım 1.1 — Shared Kernel Oluştur

Shared enum'ları ve data record'larını `common` modülüne taşı.

**Yeni dosyalar:**

```
common/infrastructure/identity/
├── Gender.java          (human/core/employee/domain/Gender.java'dan taşınacak)
├── Title.java           (human/core/employee/domain/Title.java'dan taşınacak)
└── EmergencyContactData.java  (yeni immutable record)
```

**`EmergencyContactData.java`** (yeni):
```java
package com.fabricmanagement.common.infrastructure.identity;

/**
 * Immutable shared kernel record for emergency contact data.
 * Used by both platform/user and human/employee bounded contexts.
 */
public record EmergencyContactData(
    String name,
    String phone,
    String relationship
) {
    public static EmergencyContactData empty() {
        return new EmergencyContactData(null, null, null);
    }

    public boolean isEmpty() {
        return name == null && phone == null && relationship == null;
    }
}
```

**Migration adımları:**
1. `Gender` enum'ını `common/infrastructure/identity/` altına kopyala
2. `Title` enum'ını `common/infrastructure/identity/` altına kopyala
3. `human/core/employee/domain/Gender.java` → import'u yeni konuma yönlendir (ya da eski dosyayı common'a delegate et)
4. `human/core/employee/domain/Title.java` → aynı şekilde
5. Tüm import'ları güncelle (IDE refactor)

**Etkilenen dosyalar:**
- `platform/user/dto/CreateInternalUserRequest.java` (satır 3-4)
- `platform/user/dto/UpdateUserProfileRequest.java` (satır 3-4)
- `human/core/employee/domain/Employee.java`
- `human/core/employee/app/EmployeeService.java`

#### Adım 1.2 — EmployeeSnapshot Record Oluştur

Platform modülünün Employee entity'sine değil, onun bir projection'ına bağımlı olmasını sağla.

**Yeni dosya:** `platform/user/domain/EmployeeSnapshot.java`

```java
package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only projection of Employee data for User enrichment.
 * Breaks direct dependency on human module's Employee entity.
 *
 * <p>This is a Hexagonal Architecture "output port" data structure —
 * platform/user defines what it needs, human/employee provides it.
 */
public record EmployeeSnapshot(
    UUID userId,
    Title title,
    Gender gender,
    LocalDate birthDate,
    String nationality,
    String employeeNumber,
    LocalDate hireDate,
    EmergencyContactData emergencyContact
) {
    /**
     * Factory for "no employee" case — avoids null checks in callers.
     */
    public static EmployeeSnapshot absent() {
        return new EmployeeSnapshot(null, null, null, null, null, null, null, null);
    }

    public boolean isPresent() {
        return userId != null;
    }
}
```

#### Adım 1.3 — EmployeeProjectionPort Interface Oluştur

**Yeni dosya:** `platform/user/domain/port/EmployeeProjectionPort.java`

```java
package com.fabricmanagement.platform.user.domain.port;

import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port for employee data projection.
 *
 * <p>Platform/user defines this interface (what it needs).
 * Human/employee implements it (how to provide it).
 * This follows the Dependency Inversion Principle:
 * high-level module (platform) does not depend on low-level module (human).
 *
 * <p>Contract: implementations must handle TenantContext internally.
 */
public interface EmployeeProjectionPort {

    /**
     * Get employee snapshot for a single user.
     * Uses current TenantContext.
     */
    Optional<EmployeeSnapshot> findByUserId(UUID userId);

    /**
     * Get employee snapshot with explicit tenant (for cross-tenant admin queries).
     */
    Optional<EmployeeSnapshot> findByUserId(UUID tenantId, UUID userId);

    /**
     * Batch fetch for list enrichment. Returns userId → EmployeeSnapshot map.
     * Missing employees are simply absent from the map.
     */
    Map<UUID, EmployeeSnapshot> findByUserIds(UUID tenantId, Collection<UUID> userIds);
}
```

#### Adım 1.4 — EmployeeCreationPort Interface Oluştur

User oluşturulduğunda Employee yaratma işlemi için:

**Yeni dosya:** `platform/user/domain/port/EmployeeCreationPort.java`

```java
package com.fabricmanagement.platform.user.domain.port;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output port for employee lifecycle operations triggered by user management.
 *
 * <p>Platform/user orchestrates user+employee creation.
 * Human/employee handles the actual HR record management.
 */
public interface EmployeeCreationPort {

    /**
     * Create or update employee record for a user.
     * Returns snapshot of the created/updated employee.
     */
    EmployeeSnapshot createOrUpdate(
        UUID userId,
        Title title,
        Gender gender,
        LocalDate birthDate,
        String nationality,
        String employeeNumber,
        LocalDate hireDate,
        EmergencyContactData emergencyContact
    );

    /**
     * Generate next employee number for current tenant.
     */
    String generateEmployeeNumber();
}
```

**Sprint 1 ArchUnit Testi:**
```java
@Test
@DisplayName("Port interfaces must not import from human module")
void portsShouldNotImportHuman() {
    noClasses()
        .that().resideInAPackage("com.fabricmanagement.platform.user.domain.port..")
        .should().dependOnClassesThat()
        .resideInAPackage("com.fabricmanagement.human..")
        .check(allClasses);
}
```

---

### Sprint 2: Adapter Implementasyonları + Service Migration

#### Adım 2.1 — EmployeeProjectionAdapter Oluştur (human modülünde)

**Yeni dosya:** `human/core/employee/app/adapter/EmployeeProjectionAdapter.java`

```java
package com.fabricmanagement.human.core.employee.infra.adapter;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.core.employee.domain.Employee;
import com.fabricmanagement.human.core.employee.infra.repository.EmployeeRepository;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EmployeeProjectionAdapter implements EmployeeProjectionPort {

    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeSnapshot> findByUserId(UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return employeeRepository.findByTenantIdAndUserId(tenantId, userId)
            .map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeSnapshot> findByUserId(UUID tenantId, UUID userId) {
        return employeeRepository.findByTenantIdAndUserId(tenantId, userId)
            .map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, EmployeeSnapshot> findByUserIds(UUID tenantId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        return employeeRepository.findByTenantIdAndUserIdIn(tenantId, userIds).stream()
            .collect(Collectors.toMap(Employee::getUserId, this::toSnapshot));
    }

    private EmployeeSnapshot toSnapshot(Employee e) {
        return new EmployeeSnapshot(
            e.getUserId(),
            e.getTitle(),    // Title enum artık common'da
            e.getGender(),   // Gender enum artık common'da
            e.getBirthDate(),
            e.getNationality(),
            e.getEmployeeNumber(),
            e.getHireDate(),
            e.getEmergencyContact() != null
                ? new EmergencyContactData(
                    e.getEmergencyContact().getName(),
                    e.getEmergencyContact().getPhone(),
                    e.getEmergencyContact().getRelationship())
                : null
        );
    }
}
```

#### Adım 2.2 — EmployeeCreationAdapter Oluştur (human modülünde)

**Yeni dosya:** `human/core/employee/app/adapter/EmployeeCreationAdapter.java`

```java
package com.fabricmanagement.human.core.employee.infra.adapter;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import com.fabricmanagement.human.core.employee.app.EmployeeService;
import com.fabricmanagement.human.core.employee.domain.EmergencyContact;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import com.fabricmanagement.platform.user.domain.port.EmployeeCreationPort;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmployeeCreationAdapter implements EmployeeCreationPort {

    private final EmployeeService employeeService;

    @Override
    public EmployeeSnapshot createOrUpdate(
            UUID userId, Title title, Gender gender,
            LocalDate birthDate, String nationality,
            String employeeNumber, LocalDate hireDate,
            EmergencyContactData emergencyContact) {

        EmergencyContact ec = emergencyContact != null && !emergencyContact.isEmpty()
            ? EmergencyContact.builder()
                .name(emergencyContact.name())
                .phone(emergencyContact.phone())
                .relationship(emergencyContact.relationship())
                .build()
            : null;

        var employee = employeeService.createOrUpdateEmployee(
            userId, title, gender, birthDate,
            nationality, employeeNumber, hireDate, ec);

        return new EmployeeSnapshot(
            employee.getUserId(), employee.getTitle(), employee.getGender(),
            employee.getBirthDate(), employee.getNationality(),
            employee.getEmployeeNumber(), employee.getHireDate(),
            emergencyContact);
    }

    @Override
    public String generateEmployeeNumber() {
        return employeeService.generateEmployeeNumber();
    }
}
```

#### Adım 2.3 — UserQueryService Refactoring

**Dosya:** `platform/user/app/UserQueryService.java`

**Değişiklik:**

```diff
- import com.fabricmanagement.human.core.employee.app.EmployeeService;
- import com.fabricmanagement.human.core.employee.domain.Employee;
+ import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
+ import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;

  @Service
  @RequiredArgsConstructor
  @Slf4j
  public class UserQueryService {

    private final UserRepository userRepository;
-   private final EmployeeService employeeService;
+   private final EmployeeProjectionPort employeeProjectionPort;
    private final UserWorkLocationService userWorkLocationService;

    @Transactional(readOnly = true)
    public Optional<UserDto> findById(UUID tenantId, UUID userId) {
      return userRepository
          .findByTenantIdAndId(tenantId, userId)
          .map(user -> {
-             UserDto dto = UserDto.from(user,
-                 employeeService.getEmployeeByUserId(userId).orElse(null));
+             UserDto dto = UserDto.from(user,
+                 employeeProjectionPort.findByUserId(userId).orElse(null));
              // ... workLocationLabel enrichment stays same
              return dto;
          });
    }

    private List<UserDto> enrichWithEmployeeData(UUID tenantId, List<User> users) {
      // ...
      List<UUID> userIds = users.stream().map(User::getId).toList();
-     Map<UUID, Employee> employeeMap = employeeService.getEmployeesByUserIds(tenantId, userIds);
+     Map<UUID, EmployeeSnapshot> employeeMap =
+         employeeProjectionPort.findByUserIds(tenantId, userIds);
      // ...
    }
  }
```

#### Adım 2.4 — UserDto Refactoring

**Dosya:** `platform/user/dto/UserDto.java`

```diff
- import com.fabricmanagement.human.core.employee.domain.Employee;
+ import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;

  public class UserDto {
    // ... fields stay same ...

-   public static UserDto from(User user, Employee employee) {
+   public static UserDto from(User user, EmployeeSnapshot employee) {
      // ... builder stays same until employee block ...

-     if (employee != null) {
+     if (employee != null && employee.isPresent()) {
        builder
            .isEmployee(true)
-           .title(employee.getTitle() != null ? employee.getTitle().name() : null)
-           .gender(employee.getGender() != null ? employee.getGender().name() : null)
+           .title(employee.title() != null ? employee.title().name() : null)
+           .gender(employee.gender() != null ? employee.gender().name() : null)
-           .birthDate(employee.getBirthDate())
-           .nationality(employee.getNationality())
-           .employeeNumber(employee.getEmployeeNumber())
-           .hireDate(employee.getHireDate());
+           .birthDate(employee.birthDate())
+           .nationality(employee.nationality())
+           .employeeNumber(employee.employeeNumber())
+           .hireDate(employee.hireDate());

-       if (employee.getEmergencyContact() != null && !employee.getEmergencyContact().isEmpty()) {
+       if (employee.emergencyContact() != null && !employee.emergencyContact().isEmpty()) {
          builder.emergencyContact(
              EmergencyContactDto.builder()
-                 .name(employee.getEmergencyContact().getName())
-                 .phone(employee.getEmergencyContact().getPhone())
-                 .relationship(employee.getEmergencyContact().getRelationship())
+                 .name(employee.emergencyContact().name())
+                 .phone(employee.emergencyContact().phone())
+                 .relationship(employee.emergencyContact().relationship())
                  .build());
        }
      }
    }
  }
```

#### Adım 2.5 — Diğer Service'lerin Migration'ı

Aynı pattern'ı aşağıdaki dosyalara uygula:

| Dosya | EmployeeService → | Değişiklik |
|-------|-------------------|------------|
| `UserCreationService.java` | `EmployeeCreationPort` | `createOrUpdate()` çağrısı |
| `UserProfileService.java` | `EmployeeCreationPort` + `EmployeeProjectionPort` | Update + read |
| `UserOnboardingService.java` | `EmployeeProjectionPort` | Sadece read |
| `UserService.java` | `EmployeeProjectionPort` | Sadece read |
| `PasswordSetupService.java` | `EmployeeProjectionPort` | Sadece read |
| `PlatformAdminService.java` | `EmployeeProjectionPort` | Cross-tenant read |
| `UserController.java` | Injection kaldır | Controller'dan doğrudan EmployeeService kullanımını kaldır |

---

### Sprint 3: Event Decoupling + AI Module Fix

#### Adım 3.1 — Event Import'larını Shared Event'e Taşı

Şu an `EmployeeTerminationEventListener` doğrudan `human` modülünün event class'ını import ediyor. İki seçenek var:

**Seçenek A (Önerilen): Event class'ları olduğu yerde kalsın, import kabul edilsin**

Event'ler zaten DDD'de bounded context'ler arası iletişim aracıdır. Bir modülün başka bir modülün **event'ini dinlemesi** normal ve kabul edilebilir bir pattern'dır. Bu, service injection'dan farklıdır çünkü:
- Event publisher, listener'ı bilmez (loose coupling)
- Listener sadece event payload'ına bağımlıdır (tek nokta)

Bu yaklaşımda `EmployeeTerminationEventListener.java` ve `UserCacheInvalidationService.java` **olduğu gibi kalır**.

**Seçenek B: Shared event package**

Eğer sıfır import istiyorsan:
```
common/infrastructure/events/employee/
├── EmployeeTerminatedEvent.java
└── EmployeeUpdatedEvent.java
```
Ancak bu, event'lerin domain'den ayrılması demektir ki DDD puristleri buna karşıdır.

**Öneri:** Seçenek A. Event listening kabul edilebilir coupling'dir.

#### Adım 3.2 — AI Module Decoupling (Ayrı Sorun)

`platform/ai/app/AIFunctionCaller.java` doğrudan `production` modülüne bağımlı:
- `FiberFacade`, `FiberRepository`, `FiberCategory`
- `MaterialFacade`, `MaterialType`, `CreateMaterialRequest`

Bu, platform → domain yönünde ters bağımlılık yaratıyor.

**Çözüm: AI Tool Registry Pattern**

```java
// common/infrastructure/ai/AIToolProvider.java
public interface AIToolProvider {
    String toolName();
    String description();
    Object execute(Map<String, Object> parameters);
}

// production/masterdata/fiber/app/adapter/FiberAIToolProvider.java
@Component
public class FiberAIToolProvider implements AIToolProvider {
    private final FiberFacade fiberFacade;

    @Override
    public String toolName() { return "search_fibers"; }

    @Override
    public Object execute(Map<String, Object> params) {
        // fiber search logic
    }
}

// platform/ai/app/AIFunctionCaller.java
@Component
public class AIFunctionCaller {
    private final List<AIToolProvider> toolProviders; // Spring auto-discovers
    // No more direct production imports!
}
```

---

### Sprint 4: ArchUnit Enforcement + Cleanup

#### Adım 4.1 — Yeni ArchUnit Kuralları Ekle

**Dosya:** `ConstitutionArchTest.java`'ya eklenecek:

```java
@Nested
@DisplayName("Article 11 — Platform/Human Decoupling")
class PlatformHumanDecouplingTests {

    @Test
    @DisplayName("Rule 11.3: platform/user must not import human module directly")
    void platformUserShouldNotImportHumanDirectly() {
        noClasses()
            .that().resideInAPackage("com.fabricmanagement.platform.user..")
            .and().resideOutsideOfPackage("..app.listener..") // event listeners exempt
            .should().dependOnClassesThat()
            .resideInAPackage("com.fabricmanagement.human..")
            .as("Rule 11.3: platform/user must use ports, not direct human imports");

        rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 11.4: platform/ai must not import domain modules directly")
    void platformAiShouldNotImportDomainModules() {
        noClasses()
            .that().resideInAPackage("com.fabricmanagement.platform.ai..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.fabricmanagement.production..",
                "com.fabricmanagement.sales..",
                "com.fabricmanagement.human..")
            .as("Rule 11.4: platform/ai must use AIToolProvider, not direct imports");

        rule.check(allClasses);
    }
}
```

#### Adım 4.2 — Eski Import'ları Temizle

IDE'de "Find Usages" ile human modülüne kalan tüm platform referanslarını bul ve kaldır. Hedef:

```
platform/ → human/ import sayısı: 18 → 2 (sadece event listener'lar)
```

#### Adım 4.3 — Dokümantasyon Güncelle

`AGENTS.md` (Backend Architecture Constitution) güncelle:
- Madde 11'e "Port/Adapter pattern for cross-module data access" kuralı ekle
- Shared Kernel (`common/infrastructure/identity/`) dokümante et
- AI Tool Registry pattern'ını dokümante et

---

## 4. Dosya Bazlı Değişiklik Özeti

### Yeni Dosyalar (8)

| # | Dosya Yolu | Modül | Amaç |
|---|-----------|-------|------|
| 1 | `common/infrastructure/identity/Gender.java` | common | Shared enum |
| 2 | `common/infrastructure/identity/Title.java` | common | Shared enum |
| 3 | `common/infrastructure/identity/EmergencyContactData.java` | common | Shared record |
| 4 | `platform/user/domain/EmployeeSnapshot.java` | platform | Projection record |
| 5 | `platform/user/domain/port/EmployeeProjectionPort.java` | platform | Read port |
| 6 | `platform/user/domain/port/EmployeeCreationPort.java` | platform | Write port |
| 7 | `human/core/employee/app/adapter/EmployeeProjectionAdapter.java` | human | Read adapter |
| 8 | `human/core/employee/app/adapter/EmployeeCreationAdapter.java` | human | Write adapter |

### Değiştirilecek Dosyalar (13)

| # | Dosya | Değişiklik |
|---|-------|-----------|
| 1 | `platform/user/app/UserQueryService.java` | EmployeeService → EmployeeProjectionPort |
| 2 | `platform/user/app/UserService.java` | EmployeeService → EmployeeProjectionPort |
| 3 | `platform/user/app/UserCreationService.java` | EmployeeService → EmployeeCreationPort |
| 4 | `platform/user/app/UserProfileService.java` | EmployeeService → her iki Port |
| 5 | `platform/user/app/UserOnboardingService.java` | EmployeeService → EmployeeProjectionPort |
| 6 | `platform/user/dto/UserDto.java` | Employee → EmployeeSnapshot |
| 7 | `platform/user/dto/CreateInternalUserRequest.java` | Import path güncellemesi |
| 8 | `platform/user/dto/UpdateUserProfileRequest.java` | Import path güncellemesi |
| 9 | `platform/user/api/controller/UserController.java` | EmployeeService injection kaldır |
| 10 | `platform/auth/app/PasswordSetupService.java` | EmployeeService → EmployeeProjectionPort |
| 11 | `platform/admin/app/PlatformAdminService.java` | EmployeeService → EmployeeProjectionPort |
| 12 | `human/core/employee/domain/Gender.java` | common'a delegate veya kaldır |
| 13 | `human/core/employee/domain/Title.java` | common'a delegate veya kaldır |

### Dokunulmayacak Dosyalar (2) — Bilinçli Karar

| # | Dosya | Neden |
|---|-------|-------|
| 1 | `platform/user/app/EmployeeTerminationEventListener.java` | Event listening kabul edilebilir coupling |
| 2 | `platform/user/app/UserCacheInvalidationService.java` | Event listening kabul edilebilir coupling |

---

## 5. Risk Analizi ve Mitigation

| Risk | Olasılık | Etki | Mitigation |
|------|----------|------|------------|
| Performans kaybı (extra mapping layer) | Düşük | Düşük | EmployeeSnapshot immutable record, GC-friendly. Batch query zaten mevcut. |
| Shared kernel şişmesi | Orta | Orta | Gender, Title, EmergencyContactData ile sınırla. Her ekleme için PR review zorunlu. |
| Adapter'da bug (mapping hatası) | Orta | Yüksek | `EmployeeProjectionAdapterTest` unit testi zorunlu. Field-by-field assertion. |
| Migration sırasında API kırılması | Düşük | Yüksek | UserDto.from() method signature değişiyor → tüm caller'ları kontrol et. İç internal method olduğu için dış API etkilenmez. |
| ArchUnit testleri kırılması | Kesin | Düşük | Freeze + Enforce stratejisi ile yeni kuralları önce frozen olarak ekle, sonra enforce et. |

---

## 6. Test Planı

### 6.1 Unit Tests (Her Sprint)

```
EmployeeSnapshotTest              → Record equality, absent(), isPresent()
EmployeeProjectionAdapterTest     → Mapping doğruluğu, null handling
EmployeeCreationAdapterTest       → Delegation doğruluğu
UserQueryServiceTest              → Port mock'ları ile enrichment
UserDtoTest                       → from(User, EmployeeSnapshot) mapping
```

### 6.2 Integration Tests (Sprint 2 sonu)

```
UserCreationIntegrationTest       → Internal user + Employee oluşturma end-to-end
UserQueryIntegrationTest          → User + Employee enrichment Testcontainers ile
TenantIsolationTest               → Tenant A employee, Tenant B user → boş enrichment
```

### 6.3 Architecture Tests (Sprint 4)

```
ConstitutionArchTest              → Yeni Rule 11.3, 11.4
ModernJavaArchTest                → Port interface'lerin domain'de olduğunu doğrula
```

---

## 7. Öğrenme Notları

Bu refactoring'in DDD perspektifinden öğrettiği kavramlar:

1. **Shared Kernel**: İki bounded context'in birlikte sahiplendiği, birlikte değiştirdiği küçük bir kod parçası. Gender ve Title enum'ları bunun klasik örneği — her iki context'te de aynı anlama geliyorlar.

2. **Anti-Corruption Layer vs Port/Adapter**: ACL, dış bir sistemin modelini kendi modeline çevirir. Port/Adapter ise kendi modülünün ihtiyacını tanımlar ve dışarıdaki modülün bunu implement etmesini bekler. Burada Port/Adapter kullandık çünkü iki modül de bizim kontrolümüzde.

3. **Projection vs Entity**: Entity mutable, lifecycle'ı var, aggregate root olabilir. Projection (EmployeeSnapshot) immutable, read-only, ve sadece belirli bir use-case için gerekli alanları taşır. Bu ayrım, modüller arası veri paylaşımında coupling'i minimize eder.

4. **Event Listening ≠ Coupling**: Bir modülün başka bir modülün event'ini dinlemesi, o modüle bağımlı olması değildir. Publisher listener'ı bilmez. Bu, service injection'dan kategorik olarak farklıdır.

5. **Pragmatizm > Purizm**: Tam event-driven (eventual consistency) yaklaşımı daha "temiz" olurdu ama User API yanıtında Employee verisi senkron gerektiği için Port/Adapter pragmatik olarak daha doğru.

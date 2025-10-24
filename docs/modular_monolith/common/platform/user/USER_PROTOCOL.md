# 👤 USER MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/platform/user`  
**Dependencies:** `common/infrastructure/persistence`, `common/infrastructure/events`, `common/platform/company`

---

## 🎯 MODULE PURPOSE

User module, **Kullanıcı Yönetimi** işlemlerini gerçekleştirir.

### **Core Responsibilities**

- ✅ **User CRUD** - Create, Read, Update, Delete
- ✅ **User Profile** - Profile management
- ✅ **User-Company Relationship** - Company ve department assignment
- ✅ **User Roles** - Role management
- ✅ **User Permissions** - Permission assignment
- ✅ **User Status** - Active/Inactive management

---

## 🧱 MODULE STRUCTURE

```
user/
├─ api/
│  ├─ controller/
│  │  └─ UserController.java
│  └─ facade/
│     └─ UserFacade.java
├─ app/
│  └─ UserService.java
├─ domain/
│  ├─ User.java                     # UUID + tenant_id + uid
│  ├─ UserRole.java
│  ├─ UserPermission.java
│  └─ event/
│     ├─ UserCreatedEvent.java
│     ├─ UserUpdatedEvent.java
│     └─ UserDeactivatedEvent.java
├─ infra/
│  └─ repository/
│     ├─ UserRepository.java
│     ├─ UserRoleRepository.java
│     └─ UserPermissionRepository.java
└─ dto/
   ├─ UserDto.java
   ├─ CreateUserRequest.java
   └─ UpdateUserRequest.java
```

---

## 📋 DOMAIN MODELS

### **User Entity**

```java
@Entity
@Table(name = "common_user", schema = "common_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    // Identity (from BaseEntity)
    // - UUID id
    // - UUID tenantId
    // - String uid (e.g., "ACME-001-USER-00042")

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String displayName; // Auto-generated: firstName + lastName

    @Column(nullable = false, unique = true)
    private String contactValue; // Email or Phone

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactType contactType; // EMAIL, PHONE

    @Column(nullable = false)
    private UUID companyId; // Company relationship

    @Column
    private String department; // production, planning, finance, etc.

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column
    private Instant lastActiveAt;

    // Business methods
    public static User create(String firstName, String lastName, String contactValue,
                             ContactType contactType, UUID companyId) {
        return User.builder()
            .firstName(firstName)
            .lastName(lastName)
            .displayName(firstName + " " + lastName)
            .contactValue(contactValue)
            .contactType(contactType)
            .companyId(companyId)
            .isActive(true)
            .build();
    }

    public void updateProfile(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = firstName + " " + lastName;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}
```

---

## 🔗 ENDPOINTS

| Endpoint                         | Method | Purpose          | Auth Required          |
| -------------------------------- | ------ | ---------------- | ---------------------- |
| `/api/common/users`              | GET    | List users       | ✅ Yes                 |
| `/api/common/users/{id}`         | GET    | Get user by ID   | ✅ Yes                 |
| `/api/common/users`              | POST   | Create user      | ✅ Yes (ADMIN)         |
| `/api/common/users/{id}`         | PUT    | Update user      | ✅ Yes (ADMIN or self) |
| `/api/common/users/{id}`         | DELETE | Deactivate user  | ✅ Yes (ADMIN)         |
| `/api/common/users/{id}/profile` | GET    | Get user profile | ✅ Yes                 |
| `/api/common/users/{id}/profile` | PUT    | Update profile   | ✅ Yes (self)          |

---

## 🔄 EVENTS

| Event                  | Trigger          | Listeners                        |
| ---------------------- | ---------------- | -------------------------------- |
| `UserCreatedEvent`     | User created     | Audit, Analytics, Notification   |
| `UserUpdatedEvent`     | User updated     | Audit, Analytics                 |
| `UserDeactivatedEvent` | User deactivated | Audit, Policy (invalidate cache) |

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

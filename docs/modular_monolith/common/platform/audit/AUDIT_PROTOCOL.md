# 📊 AUDIT MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/platform/audit`  
**Dependencies:** `common/infrastructure/persistence`, `common/infrastructure/events`

---

## 🎯 MODULE PURPOSE

Audit module, **Comprehensive Audit Trail** sağlar. Tüm kritik işlemler loglanır.

### **Core Responsibilities**

- ✅ **Action Logging** - User actions
- ✅ **Policy Decisions** - Policy evaluation results
- ✅ **Data Changes** - Before/After tracking
- ✅ **Security Events** - Login, logout, failed attempts
- ✅ **System Events** - Configuration changes
- ✅ **Audit Reports** - Compliance reports

---

## 🧱 MODULE STRUCTURE

```
audit/
├─ api/
│  ├─ controller/
│  │  └─ AuditController.java
│  └─ facade/
│     └─ AuditFacade.java
├─ app/
│  └─ AuditService.java
├─ domain/
│  ├─ AuditLog.java                 # UUID + tenant_id + uid
│  └─ event/
│     └─ AuditLogCreatedEvent.java
├─ infra/
│  └─ repository/
│     └─ AuditLogRepository.java
└─ dto/
   ├─ AuditLogDto.java
   └─ CreateAuditLogRequest.java
```

---

## 📋 DOMAIN MODELS

### **AuditLog Entity**

```java
@Entity
@Table(name = "common_audit_log", schema = "common_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String userUid; // Human-readable user ID

    @Column(nullable = false)
    private String action; // CREATE, UPDATE, DELETE, READ, LOGIN, LOGOUT, etc.

    @Column(nullable = false)
    private String resource; // material, user, invoice, etc.

    @Column
    private String resourceId; // UUID of affected resource

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String oldValue; // Before change (JSON)

    @Column(columnDefinition = "TEXT")
    private String newValue; // After change (JSON)

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditSeverity severity; // INFO, WARNING, ERROR, CRITICAL

    @Column(nullable = false)
    private Instant timestamp;
}
```

---

## 🔗 ENDPOINTS

| Endpoint                                     | Method | Purpose              | Auth Required  |
| -------------------------------------------- | ------ | -------------------- | -------------- |
| `/api/common/audit/logs`                     | GET    | List audit logs      | ✅ Yes (ADMIN) |
| `/api/common/audit/logs/{id}`                | GET    | Get audit log by ID  | ✅ Yes (ADMIN) |
| `/api/common/audit/logs/user/{userId}`       | GET    | Get logs by user     | ✅ Yes (ADMIN) |
| `/api/common/audit/logs/resource/{resource}` | GET    | Get logs by resource | ✅ Yes (ADMIN) |
| `/api/common/audit/reports/compliance`       | GET    | Compliance report    | ✅ Yes (ADMIN) |

---

## 🔄 EVENTS

| Event                  | Trigger           | Listeners             |
| ---------------------- | ----------------- | --------------------- |
| `AuditLogCreatedEvent` | Audit log created | Analytics, Monitoring |

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

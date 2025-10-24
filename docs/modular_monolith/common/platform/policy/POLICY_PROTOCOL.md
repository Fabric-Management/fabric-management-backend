# 📜 POLICY MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/platform/policy`  
**Dependencies:** `common/infrastructure/persistence`, `common/infrastructure/events`, `common/platform/company`, `common/platform/user`

---

## 🎯 MODULE PURPOSE

Policy module, **5-Layer Policy Engine** ile kimlik ve yetki kontro lünü sağlar.

### **Core Responsibilities**

- ✅ **Policy Evaluation** - 5-layer policy engine
- ✅ **OS Subscription Check** - Layer 1
- ✅ **Tenant Policy Check** - Layer 2
- ✅ **Company/Department Check** - Layer 3
- ✅ **User/Role Check** - Layer 4
- ✅ **Condition Check** - Layer 5
- ✅ **Policy Caching** - Redis-based caching
- ✅ **Policy Audit** - Decision logging

---

## 🔐 POLICY LAYERS

```
Layer 1: OS SUBSCRIPTION → Tenant hangi OS'lere abone?
Layer 2: TENANT → Tenant-level restrictions
Layer 3: COMPANY → Department & hierarchy
Layer 4: USER → Role & permissions
Layer 5: CONDITIONS → Time, field, business rules
```

---

## 📋 DOMAIN MODELS

### **Policy Entity**

```java
@Entity
@Table(name = "common_policy", schema = "common_policy")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Policy extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String policyId; // e.g., "fabric.yarn.create"

    @Column(nullable = false)
    private String resource; // e.g., "fabric.yarn"

    @Column(nullable = false)
    private String action; // e.g., "create", "read", "update", "delete"

    @Column(nullable = false)
    private Integer priority; // Higher priority evaluated first

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyEffect effect; // ALLOW, DENY

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(columnDefinition = "JSONB")
    private Map<String, Object> conditions; // All policy conditions
}
```

### **PolicyDecision**

```java
@Data
@Builder
public class PolicyDecision {

    private boolean allowed;
    private String reason;
    private List<String> failedConditions;
    private String policyId;
    private Instant evaluatedAt;
    private Long evaluationTimeMs;

    public static PolicyDecision allow(String reason) {
        return PolicyDecision.builder()
            .allowed(true)
            .reason(reason)
            .failedConditions(Collections.emptyList())
            .evaluatedAt(Instant.now())
            .build();
    }

    public static PolicyDecision deny(String reason, List<String> failedConditions) {
        return PolicyDecision.builder()
            .allowed(false)
            .reason(reason)
            .failedConditions(failedConditions)
            .evaluatedAt(Instant.now())
            .build();
    }
}
```

---

## 🔗 ENDPOINTS

| Endpoint                        | Method | Purpose                | Auth Required  |
| ------------------------------- | ------ | ---------------------- | -------------- |
| `/api/common/policies`          | GET    | List policies          | ✅ Yes (ADMIN) |
| `/api/common/policies/{id}`     | GET    | Get policy by ID       | ✅ Yes (ADMIN) |
| `/api/common/policies`          | POST   | Create policy          | ✅ Yes (ADMIN) |
| `/api/common/policies/{id}`     | PUT    | Update policy          | ✅ Yes (ADMIN) |
| `/api/common/policies/{id}`     | DELETE | Delete policy          | ✅ Yes (ADMIN) |
| `/api/common/policies/evaluate` | POST   | Evaluate policy (test) | ✅ Yes (ADMIN) |

---

## 🔄 EVENTS

| Event                  | Trigger          | Listeners          |
| ---------------------- | ---------------- | ------------------ |
| `PolicyCreatedEvent`   | Policy created   | Cache (invalidate) |
| `PolicyUpdatedEvent`   | Policy updated   | Cache (invalidate) |
| `PolicyDeletedEvent`   | Policy deleted   | Cache (invalidate) |
| `PolicyEvaluatedEvent` | Policy evaluated | Audit, Analytics   |

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

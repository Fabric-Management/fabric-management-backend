# 📋 POLICY REGISTRY PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-10-24  
**Module:** `governance/access/policy`  
**Dependencies:** `common/infrastructure`

---

## 🎯 MODULE PURPOSE

Policy Registry stores, manages, and synchronizes policy definitions across the platform.

### **Core Responsibilities**

- ✅ **Policy CRUD** - Create, Read, Update, Delete policies
- ✅ **Policy Versioning** - Track policy changes over time
- ✅ **Policy Templates** - Predefined policy patterns
- ✅ **Cache Sync** - Invalidate caches on policy updates
- ✅ **Policy Validation** - Ensure policy correctness

---

## 📋 DOMAIN MODELS

### **PolicyDefinition Entity**

```java
@Entity
@Table(name = "gov_policy_definition", schema = "governance_access")
public class PolicyDefinition extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String policyId;  // "fabric.yarn.create"

    @Column(nullable = false)
    private String resource;  // "fabric.yarn"

    @Column(nullable = false)
    private String action;    // "create", "read", "update", "delete"

    @Column(nullable = false)
    private Integer priority; // Higher = evaluated first

    @Enumerated(EnumType.STRING)
    private PolicyEffect effect; // ALLOW, DENY

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private PolicyConditions conditions; // All 5-layer conditions

    @Column(nullable = false)
    private Boolean requiresReview; // Dual approval needed?

    @Enumerated(EnumType.STRING)
    private PolicyStatus status; // DRAFT, PENDING_REVIEW, ACTIVE, ARCHIVED
}
```

---

## 🔗 ENDPOINTS

| Endpoint                                    | Method | Purpose          | Auth  |
| ------------------------------------------- | ------ | ---------------- | ----- |
| `/api/governance/policies`                  | GET    | List policies    | ADMIN |
| `/api/governance/policies/{id}`             | GET    | Get policy       | ADMIN |
| `/api/governance/policies`                  | POST   | Create policy    | ADMIN |
| `/api/governance/policies/{id}`             | PUT    | Update policy    | ADMIN |
| `/api/governance/policies/{id}/activate`    | POST   | Activate policy  | ADMIN |
| `/api/governance/policies/cache/invalidate` | POST   | Invalidate cache | ADMIN |

---

**Last Updated:** 2025-10-24

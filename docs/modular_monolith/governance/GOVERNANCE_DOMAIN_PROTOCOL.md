# 🛡️ GOVERNANCE DOMAIN PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-10-24  
**Module:** `governance/`  
**Dependencies:** `common`  
**Priority:** 🔴 CRITICAL

---

## 🎯 DOMAIN PURPOSE

Governance Domain provides **centralized access control**, **policy lifecycle management**, and **compliance monitoring**.

### **Key Responsibilities**

- ✅ **Policy Management** - CRUD operations on policies
- ✅ **Policy Evaluation** - Runtime decision engine (delegates to common/platform/policy)
- ✅ **Policy Audit** - Complete decision trail
- ✅ **Compliance Monitoring** - Access reviews, anomaly detection
- ✅ **Cache Management** - Policy decision caching & invalidation

---

## 🏗️ SUB-MODULES

### **governance/access/** - Access Governance Protocol

**Purpose:** Policy lifecycle management & evaluation infrastructure

**Sub-modules:**

- `policy/` - Policy registry, evaluation engine, cache
- `review/` - Dual approval workflow for policy changes
- `audit/` - Policy decision logging
- `sync/` - Multi-instance cache synchronization

### **governance/compliance/** - Compliance & Risk

**Purpose:** Regulatory compliance & security monitoring

**Sub-modules:**

- `review/` - Periodic access reviews
- `anomaly/` - Suspicious pattern detection
- `report/` - Compliance & audit reports

---

## 🔗 INTEGRATION WITH common/platform/policy

```
Runtime Flow:
  HTTP Request → @PolicyCheck annotation
     ↓
  common/platform/policy (RUNTIME EVALUATION)
     ├─ Check cache (Redis)
     ├─ Layer 1-5 evaluation
     ├─ Decision: ALLOW/DENY
     └─ Log to governance/access/audit
     ↓
  governance/access/policy (MANAGEMENT)
     ├─ Policy definitions stored here
     ├─ Admin can modify policies
     ├─ Dual approval for critical changes
     └─ Sync cache on update
```

**Separation of Concerns:**

- **common/platform/policy:** Fast (<10ms), runtime-only, no admin UI
- **governance/access/policy:** Management, audit, compliance, admin UI

---

## 📋 SUB-MODULE DOCS

- [Access/Policy Protocol](./access/policy/POLICY_REGISTRY_PROTOCOL.md)
- [Access/Review Protocol](./access/review/POLICY_REVIEW_PROTOCOL.md)
- [Access/Audit Protocol](./access/audit/POLICY_AUDIT_PROTOCOL.md)
- [Compliance/Review Protocol](./compliance/review/ACCESS_REVIEW_PROTOCOL.md)
- [Compliance/Anomaly Protocol](./compliance/anomaly/ANOMALY_DETECTION_PROTOCOL.md)

---

**Last Updated:** 2025-10-24  
**Maintained By:** Fabric Management Team

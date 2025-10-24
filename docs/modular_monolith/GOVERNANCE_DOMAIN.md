# 🛡️ GOVERNANCE DOMAIN - ACCESS & COMPLIANCE

**Version:** 1.0  
**Last Updated:** 2025-10-24  
**Status:** 📋 Architecture Defined  
**Priority:** 🔴 CRITICAL

---

## 🎯 OVERVIEW

Governance Domain provides **centralized access control**, **policy management**, and **compliance monitoring** across the entire platform.

### **Relationship with common/platform/policy:**

```
┌─────────────────────────────────────────┐
│  common/platform/policy                 │
│  ─────────────────────────────────────  │
│  → RUNTIME policy evaluation            │
│  → 5-Layer checks (OS→Tenant→Company)   │
│  → Policy decision caching              │
│  → @PolicyCheck annotation              │
└────────────────┬────────────────────────┘
                 │
                 │ Uses policies defined by ↓
                 │
┌────────────────┴────────────────────────┐
│  governance/access/policy               │
│  ─────────────────────────────────────  │
│  → Policy MANAGEMENT (create, update)   │
│  → Policy REGISTRY (storage)            │
│  → Policy AUDIT (decision trail)        │
│  → Policy SYNC (cache invalidation)     │
│  → Admin UI/API for governance          │
└─────────────────────────────────────────┘
```

**Separation of Concerns:**

- **common/platform/policy:** Fast runtime evaluation (< 10ms)
- **governance/access/policy:** Management, audit, compliance (admin operations)

---

## 🏗️ ARCHITECTURE

### **Package Structure**

```
governance/
├─ access/                    # Access Governance Protocol (AGP)
│  ├─ policy/                 # Policy Registry & Engine
│  │  ├─ registry/
│  │  │  ├─ domain/
│  │  │  │  └─ PolicyDefinition.java
│  │  │  ├─ app/
│  │  │  │  └─ PolicyRegistryService.java
│  │  │  └─ infra/repository/
│  │  │     └─ PolicyDefinitionRepository.java
│  │  ├─ engine/
│  │  │  ├─ PolicyEvaluator.java
│  │  │  └─ ConditionEvaluator.java
│  │  ├─ cache/
│  │  │  ├─ PolicyCacheService.java
│  │  │  └─ CacheInvalidator.java
│  │  └─ api/
│  │     └─ PolicyManagementController.java
│  │
│  ├─ review/                 # Policy Review & Approval
│  │  ├─ domain/
│  │  │  └─ PolicyReview.java
│  │  └─ app/
│  │     └─ PolicyReviewService.java
│  │
│  ├─ audit/                  # Policy Decision Audit
│  │  ├─ domain/
│  │  │  └─ PolicyDecisionLog.java
│  │  └─ app/
│  │     └─ PolicyAuditService.java
│  │
│  └─ sync/                   # Multi-Instance Sync
│     └─ PolicySyncService.java
│
└─ compliance/                # Compliance & Risk
   ├─ review/                 # Access Reviews
   │  ├─ domain/
   │  │  └─ AccessReview.java
   │  └─ app/
   │     └─ AccessReviewService.java
   │
   ├─ anomaly/                # Anomaly Detection
   │  ├─ detector/
   │  │  └─ AccessAnomalyDetector.java
   │  └─ alert/
   │     └─ AnomalyAlertService.java
   │
   └─ report/                 # Compliance Reports
      └─ ComplianceReportService.java
```

---

## 🎯 KEY FEATURES

### **1. Policy Registry**

Central storage for all policy definitions:

```java
PolicyDefinition {
    policyId: "fabric.yarn.create"
    layers: {
        osSubscription: ["YarnOS"],
        tenant: { maxResources: 1000 },
        company: { departments: ["production"] },
        user: { roles: ["PLANNER"] },
        conditions: { timeRange: "08:00-18:00" }
    }
}
```

### **2. Dual Approval Workflow**

Critical policies require two-person approval:

```
Policy Change → Reviewer 1 approves → Reviewer 2 approves → Activated
```

### **3. Decision Audit Trail**

Every policy decision logged:

```java
PolicyDecisionLog {
    userId: "john@acme.com"
    resource: "fabric.yarn.create"
    decision: ALLOW/DENY
    reason: "Layer 3 failed: department not allowed"
    timestamp: "2025-10-24T10:30:00Z"
}
```

### **4. Anomaly Detection**

Detects suspicious patterns:

- Unusual access times
- Multiple failed attempts
- Cross-tenant access attempts
- Privilege escalation attempts

---

## 🎯 IMPLEMENTATION PRIORITY

**Phase 1:** common/platform/policy (Runtime evaluation) → NEXT  
**Phase 2:** governance/access/policy (Management) → AFTER common  
**Phase 3:** governance/compliance (Advanced) → FINAL

---

**Last Updated:** 2025-10-24  
**Maintained By:** Fabric Management Team

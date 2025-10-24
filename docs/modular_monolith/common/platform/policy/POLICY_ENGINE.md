# 🛡️ POLICY ENGINE

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Status:** ✅ Active Development

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Policy Layers](#policy-layers)
3. [Policy Structure](#policy-structure)
4. [Evaluation Flow](#evaluation-flow)
5. [Policy Decision Algorithm](#policy-decision-algorithm)
6. [Caching Strategy](#caching-strategy)
7. [Implementation](#implementation)
8. [Examples](#examples)

---

## 🎯 OVERVIEW

Policy Engine, **"Kim, neyi, ne zaman, nasıl yapabilir?"** sorusunun cevabını verir.

### **Policy Equation**

```
Policy = WHO (Kim) + WHAT (Neyi) + HOW (Nasıl) + WHEN (Ne Zaman)
```

### **Policy Principles**

| Principle                | Description                                       |
| ------------------------ | ------------------------------------------------- |
| **Default Deny**         | Her şey varsayılan olarak reddedilir (whitelist)  |
| **Explicit Allow**       | Sadece açıkça izin verilen aksiyonlar yapılabilir |
| **Priority-Based**       | Yüksek priority policy'ler önce değerlendirilir   |
| **Deny Overrides Allow** | Explicit DENY her zaman ALLOW'u geçersiz kılar    |
| **Cached Decisions**     | Decisions 5 dakika cache'lenir (performance)      |
| **Audit Trail**          | Her policy decision loglanır (compliance)         |

---

## 🔐 POLICY LAYERS

### **Layer Structure**

```
┌─────────────────────────────────────────────────┐
│  Layer 1: OS SUBSCRIPTION (Abonelik)            │
│  ─────────────────────────────────────────────  │
│  - Tenant hangi OS'lere abone?                  │
│  - Subscription durumu (ACTIVE/TRIAL/EXPIRED)   │
│  - OS-specific feature access                   │
│  Örnek: ACME Corp → FabricOS + YarnOS + PlanOS  │
└─────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────┐
│  Layer 2: TENANT (Şirket - İş Mantığı)          │
│  ─────────────────────────────────────────────  │
│  - Tenant-level restrictions                    │
│  - Blacklist/whitelist                          │
│  - Tenant-specific business rules               │
│  Örnek: ACME Corp → max 1000 materials          │
└─────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────┐
│  Layer 3: COMPANY (Şirket - Yapısal)            │
│  ─────────────────────────────────────────────  │
│  - Departments (Production, Finance, Planning)  │
│  - Company hierarchy (Parent-Child)             │
│  - Commercial relationships (Fason agreements)  │
│  Örnek: ACME Corp Production Dept               │
└─────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────┐
│  Layer 4: USER (Kullanıcı)                      │
│  ─────────────────────────────────────────────  │
│  - Role (ADMIN, PLANNER, VIEWER)                │
│  - Department assignment                        │
│  - User-specific permissions (advanced settings)│
│  Örnek: John (PLANNER, Production Dept)         │
│         + Special permission to Finance reports │
└─────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────┐
│  Layer 5: CONDITIONS (Koşullar)                 │
│  ─────────────────────────────────────────────  │
│  - Time range (mesai saati)                     │
│  - Field conditions (amount < 10K)              │
│  - Business rules (stock availability)          │
│  Örnek: Material create only 08:00-18:00        │
└─────────────────────────────────────────────────┘
```

---

## 📐 POLICY STRUCTURE

### **Complete Policy JSON**

```json
{
  "policyId": "fabric.yarn.create",
  "resource": "fabric.yarn",
  "action": "create",
  "priority": 100,
  "enabled": true,
  "effect": "ALLOW",
  "conditions": {
    "os": {
      "requiredOs": "YarnOS",
      "fallbackOs": "FabricOS.yarn_lite",
      "requiredFeatures": ["yarn_production"]
    },
    "tenant": {
      "allowedTenants": [],
      "deniedTenants": [],
      "maxResourceCount": 1000,
      "requiresApproval": false
    },
    "company": {
      "allowedDepartments": ["production", "planning"],
      "deniedDepartments": ["finance"],
      "requiresParentApproval": false,
      "commercialAgreements": {
        "fasonAllowed": true,
        "supplierAllowed": false
      }
    },
    "user": {
      "allowedRoles": ["ROLE_ADMIN", "ROLE_PLANNER"],
      "deniedRoles": ["ROLE_VIEWER"],
      "specificPermissions": ["fabric.yarn.create"],
      "blacklistedUsers": [],
      "requiresMFA": false
    },
    "additional": {
      "timeRange": {
        "start": "08:00",
        "end": "18:00",
        "timezone": "UTC+3",
        "allowWeekends": false
      },
      "fieldConditions": [
        {
          "field": "quantity",
          "operator": "lessThan",
          "value": 1000
        },
        {
          "field": "unitCost",
          "operator": "lessThan",
          "value": 10000
        }
      ],
      "customConditions": {
        "stockAvailability": true,
        "approvalRequired": false
      }
    }
  },
  "metadata": {
    "description": "Allow yarn creation for planners in production department",
    "createdBy": "admin@acme.com",
    "createdAt": "2025-01-27T10:00:00Z",
    "updatedAt": "2025-01-27T10:00:00Z"
  }
}
```

---

## 🔄 EVALUATION FLOW

### **Complete Evaluation Flow**

```
HTTP Request: POST /api/production/yarn/create
    │
    ▼
┌─────────────────────────────────────────────┐
│ 1. Extract Request Context                 │
│ ─────────────────────────────────────────── │
│ - tenant_id: ACME-001                       │
│ - company_id: ACME-PROD-001                 │
│ - user_id: JOHN-001                         │
│ - resource: fabric.yarn                     │
│ - action: create                            │
│ - request_data: { quantity: 500 }           │
└──────┬──────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ 2. Check Cache                              │
│ ─────────────────────────────────────────── │
│ Key: policy:ACME-001:JOHN-001:yarn.create   │
│ Result: CACHE_MISS                          │
└──────┬──────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ 3. Layer 1: OS Subscription Check           │
│ ─────────────────────────────────────────── │
│ ✅ Has YarnOS subscription                  │
│ ✅ Status: ACTIVE                           │
│ ✅ Expiry: 2025-12-31 (future)              │
│ ✅ Feature: yarn_production enabled         │
└──────┬──────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ 4. Layer 2: Tenant Check                   │
│ ─────────────────────────────────────────── │
│ ✅ Not in blacklist                         │
│ ✅ Material count: 450 (< 1000 limit)       │
│ ✅ No approval required                     │
└──────┬──────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ 5. Layer 3: Company Check                  │
│ ─────────────────────────────────────────── │
│ ✅ Department: Production (allowed)         │
│ ✅ Not in denied departments                │
│ ✅ No parent approval required              │
└──────┬──────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ 6. Layer 4: User Check                     │
│ ─────────────────────────────────────────── │
│ ✅ Role: PLANNER (allowed)                  │
│ ✅ Department: Production (matches)         │
│ ✅ Has permission: fabric.yarn.create       │
│ ✅ Not blacklisted                          │
└──────┬──────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ 7. Layer 5: Condition Check                │
│ ─────────────────────────────────────────── │
│ ✅ Time: 10:00 (within 08:00-18:00)         │
│ ✅ Weekday: Monday (weekends not allowed)   │
│ ✅ Quantity: 500 (< 1000)                   │
│ ✅ Stock available: true                    │
└──────┬──────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ 8. Decision: ALLOW                          │
│ ─────────────────────────────────────────── │
│ Reason: "All policy checks passed"          │
│ Cache: 5 minutes                            │
│ Audit: Log decision                         │
└─────────────────────────────────────────────┘
```

---

## 🧮 POLICY DECISION ALGORITHM

### **Pseudocode**

```
PolicyDecision evaluate(PolicyRequest request) {

    // Step 1: OS Subscription Check
    if (!hasActiveOSSubscription(request.tenantId, request.osCode)) {
        return DENY("Tenant does not have active " + request.osCode);
    }

    if (isOSFeatureDisabled(request.tenantId, request.osCode, request.feature)) {
        return DENY("Feature " + request.feature + " is disabled in " + request.osCode);
    }

    // Step 2: Tenant Check
    if (isTenantBlacklisted(request.tenantId)) {
        return DENY("Tenant is blacklisted");
    }

    if (exceedsResourceLimit(request.tenantId, request.resource)) {
        return DENY("Tenant resource limit exceeded");
    }

    // Step 3: Company Check
    if (!isCompanyDepartmentAllowed(request.companyId, request.department, request.resource)) {
        return DENY("Company department not allowed for this resource");
    }

    if (requiresParentApproval(request.companyId) && !hasParentApproval(request)) {
        return DENY("Parent company approval required");
    }

    // Step 4: User Check
    if (!hasUserRole(request.userId, request.allowedRoles)) {
        return DENY("User does not have required role");
    }

    if (!hasUserPermission(request.userId, request.resource, request.action)) {
        return DENY("User does not have required permission");
    }

    if (isUserBlacklisted(request.userId)) {
        return DENY("User is blacklisted");
    }

    // Step 5: Condition Check
    if (!isWithinTimeRange(request.timeRange)) {
        return DENY("Outside allowed time range");
    }

    if (!fieldConditionsMet(request.fieldConditions, request.requestData)) {
        return DENY("Field conditions not met: " + failedCondition);
    }

    if (!customConditionsMet(request.customConditions)) {
        return DENY("Custom conditions not met");
    }

    // ALL CHECKS PASSED
    return ALLOW("All policy checks passed");
}
```

### **Priority and Composition**

```
Policy Evaluation Order:

1. Explicit DENY policies (priority DESC)
   └─ If any DENY → immediate DENY

2. Explicit ALLOW policies (priority DESC)
   └─ If any ALLOW AND no DENY → ALLOW

3. Default policy
   └─ If no explicit policy → DEFAULT_DENY

Composition Logic: AND
  - ALL conditions must be TRUE
  - ONE FALSE condition → DENY
```

---

## 💾 CACHING STRATEGY

### **Cache Architecture**

```
┌─────────────────────────────────────────────┐
│ Redis Cache (5 min TTL)                    │
│ ─────────────────────────────────────────── │
│ Key Pattern:                                │
│   policy:{tenant_id}:{user_id}:{resource}   │
│                                             │
│ Value:                                      │
│   {                                         │
│     "allowed": true,                        │
│     "reason": "All checks passed",          │
│     "evaluatedAt": "2025-01-27T10:00:00Z",  │
│     "ttl": 300                              │
│   }                                         │
└─────────────────────────────────────────────┘
         │
         │ Cache Miss?
         ▼
┌─────────────────────────────────────────────┐
│ PostgreSQL (Policy Registry)                │
│ ─────────────────────────────────────────── │
│ SELECT * FROM common_policy                 │
│ WHERE resource = 'fabric.yarn'              │
│   AND action = 'create'                     │
│   AND enabled = true                        │
│ ORDER BY priority DESC                      │
└─────────────────────────────────────────────┘
         │
         │ Evaluate
         ▼
┌─────────────────────────────────────────────┐
│ Cache Decision (5 min)                      │
│ Publish PolicyEvaluatedEvent                │
│ Log to AuditLog                             │
└─────────────────────────────────────────────┘
```

### **Cache Invalidation**

```
Trigger Events:
  - Policy updated → invalidate all related keys
  - Subscription changed → invalidate tenant keys
  - User role changed → invalidate user keys
  - Department changed → invalidate company keys

Invalidation Pattern:
  - policy:{tenant_id}:*           (all tenant policies)
  - policy:*:{user_id}:*           (all user policies)
  - policy:{tenant_id}:{user_id}:* (specific user in tenant)
```

---

## 🧩 IMPLEMENTATION

### **1. PolicyRequest (Input)**

```java
@Data
@Builder
public class PolicyRequest {

    // Context
    private UUID tenantId;
    private String tenantUid;
    private UUID companyId;
    private String companyUid;
    private UUID userId;
    private String userUid;

    // OS Subscription
    private String osCode;              // YarnOS, PlanOS, etc.
    private String feature;             // yarn_production, planning_advanced

    // Resource & Action
    private String resource;            // fabric.yarn
    private String action;              // create, read, update, delete

    // User Details
    private List<String> roles;         // [ROLE_ADMIN, ROLE_PLANNER]
    private List<String> permissions;   // [fabric.yarn.create, fabric.yarn.read]
    private String department;          // production, planning, finance

    // Request Data
    private Map<String, Object> requestData; // { quantity: 500, unitCost: 100 }

    // Additional Context
    private Instant requestTime;
    private String ipAddress;
    private Map<String, String> customAttributes;
}
```

### **2. PolicyDecision (Output)**

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

### **3. PolicyEvaluationEngine (Core)**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyEvaluationEngine {

    private final PolicyRepository policyRepository;
    private final SubscriptionChecker subscriptionChecker;
    private final PolicyCache policyCache;
    private final AuditService auditService;

    public PolicyDecision evaluate(PolicyRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Evaluating policy: tenant={}, user={}, resource={}, action={}",
            request.getTenantUid(), request.getUserUid(), request.getResource(), request.getAction());

        // 1. Check cache
        Optional<PolicyDecision> cached = policyCache.get(request);
        if (cached.isPresent()) {
            log.debug("Policy decision found in cache");
            return cached.get();
        }

        // 2. Layer 1: OS Subscription Check
        PolicyDecision osCheck = checkOSSubscription(request);
        if (!osCheck.isAllowed()) {
            return cacheDeny(request, osCheck, startTime);
        }

        // 3. Layer 2: Tenant Check
        PolicyDecision tenantCheck = checkTenant(request);
        if (!tenantCheck.isAllowed()) {
            return cacheDeny(request, tenantCheck, startTime);
        }

        // 4. Layer 3: Company Check
        PolicyDecision companyCheck = checkCompany(request);
        if (!companyCheck.isAllowed()) {
            return cacheDeny(request, companyCheck, startTime);
        }

        // 5. Layer 4: User Check
        PolicyDecision userCheck = checkUser(request);
        if (!userCheck.isAllowed()) {
            return cacheDeny(request, userCheck, startTime);
        }

        // 6. Layer 5: Condition Check
        PolicyDecision conditionCheck = checkConditions(request);
        if (!conditionCheck.isAllowed()) {
            return cacheDeny(request, conditionCheck, startTime);
        }

        // ALL CHECKS PASSED
        PolicyDecision decision = PolicyDecision.allow("All policy checks passed");
        decision.setEvaluationTimeMs(System.currentTimeMillis() - startTime);

        log.info("Policy allowed: tenant={}, user={}, resource={}, evaluationTime={}ms",
            request.getTenantUid(), request.getUserUid(), request.getResource(),
            decision.getEvaluationTimeMs());

        // Cache decision
        policyCache.put(request, decision);

        // Audit log
        auditService.logPolicyDecision(request, decision);

        return decision;
    }

    private PolicyDecision checkOSSubscription(PolicyRequest request) {
        // Check if tenant has required OS
        if (!subscriptionChecker.hasActiveSubscription(request.getTenantId(), request.getOsCode())) {

            // Check fallback OS
            if (request.getFallbackOs() != null) {
                String[] parts = request.getFallbackOs().split("\\.");
                String fallbackOs = parts[0];
                String fallbackFeature = parts.length > 1 ? parts[1] : null;

                if (subscriptionChecker.hasActiveSubscription(request.getTenantId(), fallbackOs)) {
                    if (fallbackFeature == null || subscriptionChecker.hasFeature(request.getTenantId(), fallbackOs, fallbackFeature)) {
                        log.info("Fallback OS allowed: {}", fallbackOs);
                        return PolicyDecision.allow("Fallback OS subscription active");
                    }
                }
            }

            return PolicyDecision.deny(
                "Tenant does not have active " + request.getOsCode() + " subscription",
                List.of("os_subscription_missing")
            );
        }

        // Check if required feature is enabled
        if (request.getFeature() != null &&
            !subscriptionChecker.hasFeature(request.getTenantId(), request.getOsCode(), request.getFeature())) {
            return PolicyDecision.deny(
                "Feature " + request.getFeature() + " is not enabled in " + request.getOsCode(),
                List.of("feature_disabled")
            );
        }

        return PolicyDecision.allow("OS subscription active");
    }

    private PolicyDecision checkTenant(PolicyRequest request) {
        // Find tenant policies
        List<Policy> tenantPolicies = policyRepository.findByTenantIdAndResourceAndAction(
            request.getTenantId(),
            request.getResource(),
            request.getAction()
        );

        // Check explicit DENY
        for (Policy policy : tenantPolicies) {
            if (policy.getEffect() == PolicyEffect.DENY && policyMatches(policy, request)) {
                return PolicyDecision.deny(
                    "Explicit tenant DENY policy: " + policy.getPolicyId(),
                    List.of("tenant_deny_policy")
                );
            }
        }

        // Check resource limits
        if (exceedsResourceLimit(request.getTenantId(), request.getResource())) {
            return PolicyDecision.deny(
                "Tenant resource limit exceeded",
                List.of("resource_limit_exceeded")
            );
        }

        return PolicyDecision.allow("Tenant checks passed");
    }

    private PolicyDecision checkCompany(PolicyRequest request) {
        // Get company
        Company company = companyRepository.findById(request.getCompanyId())
            .orElseThrow(() -> new CompanyNotFoundException(request.getCompanyId()));

        // Check department
        if (!isCompanyDepartmentAllowed(company, request.getDepartment(), request.getResource())) {
            return PolicyDecision.deny(
                "Department " + request.getDepartment() + " not allowed for resource " + request.getResource(),
                List.of("department_not_allowed")
            );
        }

        // Check parent approval
        if (requiresParentApproval(request.getResource()) && !hasParentApproval(company, request)) {
            return PolicyDecision.deny(
                "Parent company approval required",
                List.of("parent_approval_required")
            );
        }

        return PolicyDecision.allow("Company checks passed");
    }

    private PolicyDecision checkUser(PolicyRequest request) {
        // Check role
        if (!hasRequiredRole(request.getUserId(), request.getRoles())) {
            return PolicyDecision.deny(
                "User does not have required role",
                List.of("role_not_allowed")
            );
        }

        // Check permission
        if (!hasPermission(request.getUserId(), request.getResource(), request.getAction())) {
            return PolicyDecision.deny(
                "User does not have required permission",
                List.of("permission_missing")
            );
        }

        // Check blacklist
        if (isUserBlacklisted(request.getUserId())) {
            return PolicyDecision.deny(
                "User is blacklisted",
                List.of("user_blacklisted")
            );
        }

        return PolicyDecision.allow("User checks passed");
    }

    private PolicyDecision checkConditions(PolicyRequest request) {
        // Time range check
        if (!isWithinTimeRange(request.getRequestTime())) {
            return PolicyDecision.deny(
                "Request outside allowed time range",
                List.of("time_range_violation")
            );
        }

        // Field conditions check
        for (FieldCondition condition : getFieldConditions(request.getResource(), request.getAction())) {
            if (!evaluateFieldCondition(condition, request.getRequestData())) {
                return PolicyDecision.deny(
                    "Field condition not met: " + condition.getField() + " " + condition.getOperator() + " " + condition.getValue(),
                    List.of("field_condition_failed")
                );
            }
        }

        // Custom conditions check
        if (!customConditionsMet(request)) {
            return PolicyDecision.deny(
                "Custom business conditions not met",
                List.of("custom_condition_failed")
            );
        }

        return PolicyDecision.allow("All conditions met");
    }

    private PolicyDecision cacheDeny(PolicyRequest request, PolicyDecision decision, long startTime) {
        decision.setEvaluationTimeMs(System.currentTimeMillis() - startTime);

        log.warn("Policy denied: tenant={}, user={}, resource={}, reason={}",
            request.getTenantUid(), request.getUserUid(), request.getResource(), decision.getReason());

        // Cache decision (shorter TTL for denials)
        policyCache.put(request, decision, Duration.ofMinutes(1));

        // Audit log
        auditService.logPolicyDecision(request, decision);

        return decision;
    }
}
```

---

## 📊 EXAMPLES

### **Example 1: Simple OS Check**

```
Request: POST /api/production/yarn/create
Tenant: ACME Corp
User: John (PLANNER)
OS: YarnOS

Evaluation:
  Layer 1 (OS):
    ✅ Has YarnOS (ACTIVE)
    ✅ Feature: yarn_production enabled

  Layer 2 (Tenant):
    ✅ Not blacklisted
    ✅ Resource count: 450/1000

  Layer 3 (Company):
    ✅ Department: Production (allowed)

  Layer 4 (User):
    ✅ Role: PLANNER (allowed)
    ✅ Permission: fabric.yarn.create (has)

  Layer 5 (Conditions):
    ✅ Time: 10:00 (within 08:00-18:00)
    ✅ Quantity: 500 (< 1000)

Decision: ALLOW
```

### **Example 2: OS Missing**

```
Request: POST /api/production/loom/create
Tenant: ACME Corp
User: John (PLANNER)
OS: LoomOS

Evaluation:
  Layer 1 (OS):
    ❌ Does not have LoomOS
    ❌ Fallback FabricOS.loom_lite disabled

Decision: DENY
Reason: "Tenant does not have active LoomOS subscription"
```

### **Example 3: Trial Expired**

```
Request: POST /api/production/planning/schedule
Tenant: ACME Corp
User: John (PLANNER)
OS: PlanOS

Evaluation:
  Layer 1 (OS):
    ❌ Has PlanOS (TRIAL)
    ❌ Trial expired: 2025-04-30 (past)

Decision: DENY
Reason: "PlanOS trial period expired. Please upgrade to continue."
```

### **Example 4: User-Specific Permission**

```
Request: GET /api/finance/reports/profit
Tenant: ACME Corp
User: John (PLANNER, Production Dept)
OS: FabricOS

Evaluation:
  Layer 1 (OS):
    ✅ Has FabricOS (ACTIVE)

  Layer 2 (Tenant):
    ✅ Not blacklisted

  Layer 3 (Company):
    ❌ Department: Production (finance reports not allowed)
    ✅ BUT: User has specific permission (advanced setting)

  Layer 4 (User):
    ✅ Role: PLANNER
    ✅ Has specific permission: finance.reports.profit.read

  Layer 5 (Conditions):
    ✅ All conditions met

Decision: ALLOW
Reason: "User-specific permission overrides department restriction"
```

### **Example 5: Commercial Agreement (Fason)**

```
Request: GET /api/production/yarn/batch/XYZ-123
Tenant: SUPPLIER Corp (Fason)
User: Ali (VIEWER)
OS: FabricOS (yarn_lite enabled)

Evaluation:
  Layer 1 (OS):
    ✅ Has FabricOS (ACTIVE)
    ✅ Feature: yarn_lite enabled

  Layer 2 (Tenant):
    ✅ Not blacklisted

  Layer 3 (Company):
    ✅ Commercial agreement with ACME Corp
    ✅ Fason relationship allows yarn batch view

  Layer 4 (User):
    ✅ Role: VIEWER (allowed for read)

  Layer 5 (Conditions):
    ✅ Time: within range
    ✅ Resource: batch XYZ-123 belongs to ACME Corp
    ✅ Fason agreement allows access

Decision: ALLOW
Reason: "Commercial agreement (fason) allows access to related company's yarn batch"
```

---

## 🎯 POLICY TYPES

### **Policy Classification**

| Type                 | Scope               | Example                                |
| -------------------- | ------------------- | -------------------------------------- |
| **OS Policy**        | Subscription-level  | "YarnOS required for yarn production"  |
| **Tenant Policy**    | Company-level       | "ACME Corp max 1000 materials"         |
| **Company Policy**   | Department-level    | "Production Dept can access materials" |
| **User Policy**      | Individual user     | "John can access finance reports"      |
| **Time Policy**      | Time-based          | "Material create only 08:00-18:00"     |
| **Field Policy**     | Data-based          | "Invoice approve only < 10K"           |
| **Composite Policy** | Multiple conditions | "ADMIN + Production + Weekday + < 10K" |

---

## ✅ POLICY ENGINE BENEFITS

### **For Tenants**

- ✅ **Flexible Access** - OS-based subscription model
- ✅ **Pay for What You Use** - Only subscribe to needed OS
- ✅ **Granular Control** - Department and user-level permissions
- ✅ **Trial Support** - Try before buy

### **For Platform**

- ✅ **Revenue Control** - OS = Revenue stream
- ✅ **Feature Gating** - Easy feature toggle
- ✅ **Compliance** - Audit trail for all decisions
- ✅ **Performance** - Cached decisions (< 10ms)

### **For Developers**

- ✅ **Clear Boundaries** - OS = Module mapping
- ✅ **Easy Testing** - Enable/disable OS for testing
- ✅ **Declarative** - @PolicyCheck annotation
- ✅ **Centralized** - Single policy engine

---

## 🎯 ADVANCED FEATURES

### **1. Logical Operators (AND / OR)**

Policy conditions can use logical operators for complex scenarios.

```json
{
  "policyId": "fabric.material.approve.complex",
  "resource": "fabric.material",
  "action": "approve",
  "conditions": {
    "logicalOperator": "OR",
    "groups": [
      {
        "logicalOperator": "AND",
        "conditions": {
          "user": {
            "allowedRoles": ["ROLE_ADMIN"]
          },
          "additional": {
            "fieldConditions": [
              {
                "field": "unitCost",
                "operator": "lessThan",
                "value": 50000
              }
            ]
          }
        }
      },
      {
        "logicalOperator": "AND",
        "conditions": {
          "user": {
            "allowedRoles": ["ROLE_PLANNER"]
          },
          "additional": {
            "fieldConditions": [
              {
                "field": "unitCost",
                "operator": "lessThan",
                "value": 10000
              }
            ],
            "requiresMFA": true
          }
        }
      }
    ]
  }
}
```

**Meaning:** ADMIN can approve < 50K OR (PLANNER can approve < 10K with MFA)

### **2. Policy Templates**

Yeni tenant oluşturulduğunda otomatik olarak default policy set yüklenir.

```java
@Component
@RequiredArgsConstructor
public class PolicyTemplateLoader {

    private final PolicyRepository policyRepository;

    @EventListener
    @Transactional
    public void handleTenantCreated(TenantCreatedEvent event) {
        log.info("Loading default policies for tenant: {}", event.getTenantUid());

        // Load FabricOS default policies
        List<Policy> defaultPolicies = loadFabricOSDefaultPolicies(event.getTenantId());
        policyRepository.saveAll(defaultPolicies);

        log.info("Loaded {} default policies for tenant: {}", defaultPolicies.size(), event.getTenantUid());
    }

    private List<Policy> loadFabricOSDefaultPolicies(UUID tenantId) {
        return List.of(
            // Core access
            createPolicy(tenantId, "fabric.user.read", "GET", List.of("ROLE_ADMIN", "ROLE_USER")),
            createPolicy(tenantId, "fabric.user.create", "POST", List.of("ROLE_ADMIN")),

            // Inventory access
            createPolicy(tenantId, "fabric.inventory.read", "GET", List.of("ROLE_ADMIN", "ROLE_PLANNER", "ROLE_VIEWER")),
            createPolicy(tenantId, "fabric.inventory.create", "POST", List.of("ROLE_ADMIN", "ROLE_PLANNER")),

            // Shipment access
            createPolicy(tenantId, "fabric.shipment.read", "GET", List.of("ROLE_ADMIN", "ROLE_PLANNER", "ROLE_VIEWER")),
            createPolicy(tenantId, "fabric.shipment.create", "POST", List.of("ROLE_ADMIN", "ROLE_PLANNER"))
        );
    }
}
```

### **3. Type-Safe Condition Evaluator**

```java
public interface ConditionEvaluator<T> {
    boolean evaluate(T value, String operator, Object expected);
}

@Component
public class PolicyConditionEvaluatorFactory {

    private final Map<Class<?>, ConditionEvaluator<?>> evaluators = new HashMap<>();

    public PolicyConditionEvaluatorFactory() {
        evaluators.put(Integer.class, new IntegerConditionEvaluator());
        evaluators.put(Double.class, new DoubleConditionEvaluator());
        evaluators.put(String.class, new StringConditionEvaluator());
        evaluators.put(Instant.class, new InstantConditionEvaluator());
    }

    @SuppressWarnings("unchecked")
    public <T> boolean evaluate(T value, String operator, Object expected) {
        ConditionEvaluator<T> evaluator = (ConditionEvaluator<T>) evaluators.get(value.getClass());

        if (evaluator == null) {
            throw new UnsupportedConditionTypeException(value.getClass());
        }

        return evaluator.evaluate(value, operator, expected);
    }
}

@Component
public class IntegerConditionEvaluator implements ConditionEvaluator<Integer> {

    @Override
    public boolean evaluate(Integer value, String operator, Object expected) {
        Integer expectedValue = (Integer) expected;

        return switch (operator) {
            case "equals" -> value.equals(expectedValue);
            case "notEquals" -> !value.equals(expectedValue);
            case "greaterThan" -> value > expectedValue;
            case "greaterThanOrEquals" -> value >= expectedValue;
            case "lessThan" -> value < expectedValue;
            case "lessThanOrEquals" -> value <= expectedValue;
            default -> throw new UnsupportedOperatorException(operator);
        };
    }
}
```

### **4. Policy Test DSL**

```json
{
  "testName": "ADMIN can create material",
  "policyId": "fabric.material.create",
  "testCases": [
    {
      "name": "ADMIN with valid subscription",
      "context": {
        "tenant": {
          "id": "ACME-001",
          "subscription": ["FabricOS", "YarnOS"]
        },
        "user": {
          "id": "USER-001",
          "role": "ROLE_ADMIN",
          "department": "production"
        },
        "request": {
          "resource": "fabric.material",
          "action": "create",
          "data": {
            "quantity": 500
          }
        }
      },
      "expectedDecision": "ALLOW",
      "expectedReason": "All policy checks passed"
    },
    {
      "name": "VIEWER cannot create material",
      "context": {
        "tenant": {
          "id": "ACME-001",
          "subscription": ["FabricOS"]
        },
        "user": {
          "id": "USER-002",
          "role": "ROLE_VIEWER",
          "department": "production"
        },
        "request": {
          "resource": "fabric.material",
          "action": "create"
        }
      },
      "expectedDecision": "DENY",
      "expectedReason": "User does not have required role"
    }
  ]
}
```

### **5. In-Memory Policy Snapshot**

```java
@Component
@RequiredArgsConstructor
public class PolicySnapshotCache {

    private final PolicyRepository policyRepository;
    private volatile Map<String, List<Policy>> policySnapshot;
    private volatile Instant lastRefresh;

    @PostConstruct
    @Scheduled(fixedDelay = 60000) // Refresh every minute
    public void refreshSnapshot() {
        log.info("Refreshing policy snapshot...");

        List<Policy> allPolicies = policyRepository.findByEnabledTrue();

        Map<String, List<Policy>> snapshot = allPolicies.stream()
            .collect(Collectors.groupingBy(
                policy -> policy.getResource() + ":" + policy.getAction(),
                Collectors.toList()
            ));

        this.policySnapshot = snapshot;
        this.lastRefresh = Instant.now();

        log.info("Policy snapshot refreshed: {} policies", allPolicies.size());
    }

    public List<Policy> getPolicies(String resource, String action) {
        String key = resource + ":" + action;
        return policySnapshot.getOrDefault(key, Collections.emptyList());
    }
}
```

---

## 📝 CHANGELOG

### **Version 1.0 - 2025-01-27**

- ✅ 5-Layer Policy Engine architecture
- ✅ Layer 1: OS Subscription check
- ✅ Layer 2: Tenant check
- ✅ Layer 3: Company & Department check
- ✅ Layer 4: User & Role check
- ✅ Layer 5: Conditions check
- ✅ Policy decision algorithm
- ✅ Caching strategy (Redis 5 min TTL)
- ✅ Comprehensive examples (Simple OS check, OS missing, Trial expired, User-specific permission, Commercial agreement)
- ✅ **Logical Operators (AND/OR)** for composite policies
- ✅ **Policy Templates** for auto-loading default policies
- ✅ **Type-Safe Condition Evaluator** Factory pattern
- ✅ **Policy Test DSL** for declarative testing
- ✅ **In-Memory Policy Snapshot** for performance optimization

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

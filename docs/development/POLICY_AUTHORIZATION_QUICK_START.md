# 🚀 Policy Authorization - Quick Start Guide

**For Developers:** Kodlamaya başlamadan önce oku!  
**Duration:** 5 dakika okuma  
**Purpose:** Ne yapıyoruz, nasıl yapacağız, neden yapıyoruz?

---

## 🎯 Ne Yapıyoruz?

**Mevcut Sistem:**

```
❌ Sadece role-based (ADMIN/USER)
❌ Fine-grained authorization yok
❌ Company type ayırımı yok
❌ Department-based routing yok
```

**Hedef Sistem:**

```
✅ Policy-based authorization (PEP/PDP)
✅ Company Type (INTERNAL/CUSTOMER/SUPPLIER)
✅ Department-aware (Dokuma/Muhasebe/Kalite)
✅ User-specific grants (Advanced Settings)
✅ Data scope (SELF/COMPANY/CROSS/GLOBAL)
✅ Audit trail (Her karar loglanır)
```

---

## 🏗️ Mimari (5 Dakikada)

### İstek Akışı

```
1. User → API Gateway (PEP)
   - JWT doğrula
   - Generate correlationId (request tracking)
   - Policy tag oluştur: "WRITE:CONTACT/COMPANY"
   - PDP'ye sor

2. Gateway → Shared Policy Engine (PDP)
   ├─ Company Type OK? ❌ → DENY
   ├─ Platform Policy OK? ❌ → DENY
   ├─ User DENY var mı? ❌ → DENY
   ├─ Role default ALLOW? ✅ → Devam
   ├─ Data Scope OK? ❌ → DENY
   └─ → ALLOW (reason: "role_default + scope_valid")

3. Service → Data Scope Tekrar Kontrol (Defense in Depth)
   - resource.companyId == user.companyId?
   - YES → İşlemi yap
   - NO → DENY + Audit alert

4. Audit (Async) → Immutable log via Kafka
   - WHO: userId
   - WHAT: endpoint
   - WHEN: timestamp
   - WHY: reason (önemli!)
   - DECISION: ALLOW/DENY
   - CORRELATION: correlationId (distributed tracing)
   - LATENCY: response time (performance monitoring)
```

---

## 📂 Dosya Organizasyonu

### Yeni Oluşturulacaklar

```
shared/shared-domain/policy/
  ├── CompanyType.java              # enum
  ├── UserContext.java              # enum
  ├── DepartmentType.java           # enum
  ├── OperationType.java            # enum
  ├── DataScope.java                # enum
  ├── PolicyDecision.java           # Decision model
  ├── PolicyContext.java            # Request context
  ├── UserPermission.java           # Entity (grants)
  └── PolicyDecisionAudit.java      # Entity (audit)

shared/shared-infrastructure/policy/
  ├── engine/
  │   ├── PolicyEngine.java         # PDP core
  │   ├── PolicyEvaluator.java      # Rule engine
  │   └── PolicyCache.java          # Redis cache
  ├── guard/
  │   ├── CompanyTypeGuard.java     # Type guardrails
  │   └── PlatformPolicyGuard.java  # Platform rules
  ├── resolver/
  │   └── ScopeResolver.java        # Scope validation
  ├── config/
  │   └── PolicyCacheConfig.java    # Redis cache config (TTL, eviction)
  └── audit/
      └── PolicyAuditService.java   # Audit logger (async)

gateway/pep/
  ├── PolicyEnforcementFilter.java  # PEP filter
  ├── PolicyTagBuilder.java         # Tag creator
  └── PolicyDecisionPropagator.java # Header injector

services/company-service/domain/aggregate/
  ├── Department.java               # New entity
  └── CompanyRelationship.java     # New entity
```

---

## ⚡ 4-Week Plan (Özet)

| Week   | Focus               | Key Deliverables                                  | Validation Checklist                                    |
| ------ | ------------------- | ------------------------------------------------- | ------------------------------------------------------- |
| **W1** | Foundation          | Enums, SecurityContext, Database schema, Entities | All migrations run, backward compatible, tests pass     |
| **W2** | Policy Engine       | PDP core, Guards, Resolvers, Cache                | PDP stateless, cache working, unit tests > 80% coverage |
| **W3** | Gateway & Services  | PEP filter, Service validation, Department system | Gateway latency < 50ms, double validation works         |
| **W4** | User Grants & Audit | Advanced Settings API, Audit dashboard, Testing   | Async audit working, TTL cleanup job running, E2E tests |

---

## 🛡️ Koruyucu Prensipler (MANDATORY)

### ⭐ Top 5 Kurallar

1. **UUID Type Safety**

   ```java
   ✅ private UUID companyId;
   ❌ private String companyId;  // ASLA!
   ```

2. **File Size Limits**

   - Service: ~150 lines (max 200)
   - Mapper: ~120 lines (max 150)
   - Controller: ~120 lines (max 150)

3. **No Code Duplication**

   ```java
   ✅ @CurrentSecurityContext SecurityContext ctx
   ❌ SecurityContextHolder.getCurrentTenantId()  // Her yerde tekrar!
   ```

4. **Centralized Error Messages**

   ```java
   ✅ ErrorMessageKeys.USER_NOT_FOUND
   ❌ "User not found"  // Hard-coded!
   ```

5. **First DENY Wins**
   ```java
   // Güvenlik öncelikli
   if (DENY_condition) return DENY;  // İlk DENY = dur!
   if (ALLOW_condition) continue;
   ```

---

## 📋 Phase 1 Checklist (Week 1)

Önce şunları yap:

- [ ] **Day 1:** Enums oluştur (6 adet)
  - CompanyType, UserContext, DepartmentType
  - OperationType, DataScope, PermissionType
- [ ] **Day 1:** SecurityContext extend et

  - companyId, companyType, departmentId ekle

- [ ] **Day 2-3:** Database migrations

  - User table: company_id, department_id, job_title
  - 5 yeni tablo: departments, company_relationships, user_permissions, policy_decisions_audit, policy_registry

- [ ] **Day 4:** Domain entities oluştur

  - Department, CompanyRelationship, UserPermission

- [ ] **Day 5:** JWT & SecurityContext update
  - Token'a yeni claim'ler ekle
  - SecurityContextResolver güncelle

**Test:**

- [ ] All migrations çalışıyor
- [ ] No breaking changes
- [ ] Unit tests pass

---

## 🧪 Quick Test Commands

```bash
# Run tests for specific modules
mvn test -pl shared/shared-domain
mvn test -pl shared/shared-infrastructure  # 🔥 PDP tests here

# Run single test class
mvn test -Dtest=PolicyEngineTest

# Run specific test method
mvn test -Dtest=PolicyEngineTest#shouldDenyCustomerWrite

# Check code coverage
mvn jacoco:report
# Open target/site/jacoco/index.html

# Run integration tests
mvn verify -Pintegration-tests

# Check for code smells
mvn sonar:sonar

# Test all policy modules at once
mvn clean test -pl shared/shared-domain,shared/shared-infrastructure
```

---

## 🚀 Getting Started (Right Now!)

### Step 1: Read Documents (15 min)

1. ✅ This file (Quick Start) - NOW
2. 📋 [POLICY_AUTHORIZATION_IMPLEMENTATION_TODO.md](POLICY_AUTHORIZATION_IMPLEMENTATION_TODO.md) - Detailed tasks
3. 🛡️ [POLICY_AUTHORIZATION_PRINCIPLES.md](POLICY_AUTHORIZATION_PRINCIPLES.md) - Coding rules

### Step 2: Setup Environment (5 min)

```bash
# Pull latest
git pull origin main

# Create feature branch
git checkout -b feature/policy-phase1-enums

# Verify build
mvn clean install -DskipTests
```

### Step 3: Start Coding (Phase 1.1)

```bash
# Create enum files
mkdir -p shared/shared-domain/src/main/java/com/fabricmanagement/shared/domain/policy

# Start with CompanyType.java
# Follow TODO document Phase 1.1
```

### Step 4: Test & Commit

```bash
# Run tests
mvn test -pl shared/shared-domain

# Check principles
# Review POLICY_AUTHORIZATION_PRINCIPLES.md checklist

# Commit
git add .
git commit -m "feat(policy): Add CompanyType, UserContext, DepartmentType enums

- Created 6 policy enums in shared-domain/policy/
- All enums serializable for Kafka
- Unit tests included

Ref: Phase 1.1"

# Push
git push origin feature/policy-phase1-enums
```

---

## 💡 Pro Tips

### Tip 1: Work in Small Batches

```
✅ 1 enum per commit (testable)
❌ All 6 enums in 1 commit (risky)
```

### Tip 2: Test-First Approach

```java
// Write test first
@Test
void shouldReturnInternalForInternalType() {
    assertTrue(CompanyType.INTERNAL.isInternal());
}

// Then implement
public enum CompanyType {
    INTERNAL;
    public boolean isInternal() { return this == INTERNAL; }
}
```

### Tip 3: Document WHY, Not WHAT

```java
// ❌ Bad comment
// Get company type
CompanyType type = getCompanyType();

// ✅ Good comment
// External users (CUSTOMER/SUPPLIER) cannot write, only INTERNAL can
if (!companyType.isInternal()) {
    return DENY;
}
```

### Tip 4: Use IDE Features

- **Live Templates:** Create code snippets
- **Code Inspection:** Fix warnings before commit
- **TODO comments:** `// TODO: Add scope validation`

### Tip 5: Branch Naming Convention

```bash
# Feature branches
feature/policy-phase1-enums
feature/policy-phase2-pdp-engine
feature/policy-phase3-gateway-pep

# Bugfix branches
bugfix/policy-scope-validation-null-check

# Hotfix branches
hotfix/policy-cache-eviction-issue

# Convention: <type>/policy-<phase>-<description>
# Types: feature, bugfix, hotfix, refactor, docs
```

---

## ❓ FAQ

**Q: SecurityContext değişikliği mevcut kodu bozar mı?**  
A: Hayır. Yeni fieldlar optional. Backward compatible.

**Q: Database migration geri alınabilir mi?**  
A: Evet. Her migration için rollback script var.

**Q: Test yazmak zorunlu mu?**  
A: Evet. Coverage > 80% hedef. PR review'da kontrol edilir.

**Q: PDP nerede çalışacak?**  
A: Shared module. Her microservice kullanır. İleride ayrı service olabilir.

**Q: Gateway değişikliği risky değil mi?**  
A: Phase-based implementation. Önce test, sonra production.

**Q: Audit log performance'ı düşürür mü?**  
A: Asenkron logging. Kafka event olarak gönderilir. Ana thread'i bloklamaz.

**Q: PDP cache ne kadar süre tutar?**  
A: Redis'te default 5 dakika TTL. `PolicyCacheConfig` ile ayarlanabilir. Policy güncellendiğinde Kafka event ile cache invalidation yapılır.

**Q: correlationId nasıl çalışır?**  
A: Gateway'de generate edilir, tüm request boyunca taşınır (Gateway → PDP → Service → Audit). Distributed tracing için kritik.

---

## 🆘 Need Help?

- **Technical Questions:** #backend-dev Slack
- **Architecture Decisions:** Tech Lead
- **Blocked:** Create issue, tag @tech-lead
- **Documentation:** This file + linked documents

---

## 📚 Related Documents

| Document                                                              | Purpose           | Priority    |
| --------------------------------------------------------------------- | ----------------- | ----------- |
| [TODO](POLICY_AUTHORIZATION_IMPLEMENTATION_TODO.md)                   | Detailed tasks    | 🔴 Critical |
| [PRINCIPLES](POLICY_AUTHORIZATION_PRINCIPLES.md)                      | Coding rules      | 🔴 Critical |
| [GAP ANALYSIS](../reports/POLICY_BASED_AUTHORIZATION_GAP_ANALYSIS.md) | What's missing    | 🟡 High     |
| [ARCHITECTURE](../ARCHITECTURE.md)                                    | System design     | 🟡 High     |
| [PRINCIPLES (General)](PRINCIPLES.md)                                 | General standards | 🟢 Medium   |

---

## ✅ Pre-Start Checklist

Before writing code, confirm:

- [ ] I've read Quick Start (this file)
- [ ] I understand the architecture (PEP/PDP)
- [ ] I know the principles (UUID safety, file size, DRY)
- [ ] I know where to put files (shared vs service)
- [ ] I have the TODO document open
- [ ] My environment is ready (build passes)
- [ ] I'm on a feature branch (not main!)

**Ready? Let's build! 🚀**

---

**Last Updated:** 2025-10-08  
**Version:** 1.0  
**Owner:** Backend Team  
**Next Review:** 2025-10-15

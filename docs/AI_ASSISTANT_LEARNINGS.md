# 🤖 AI Assistant - Learnings & Guidelines

**Purpose:** AI Assistant'ın öğrendiği dersler ve çalışma prensipleri  
**Audience:** AI Assistant (Future chat sessions)  
**Priority:** 🔴 CRITICAL - Her session'da OKU!  
**Last Updated:** 2025-10-09 14:52 UTC+1

---

## ⚠️ CRITICAL PROJECT CONTEXT

```
╔════════════════════════════════════════════════════════════════════╗
║                                                                    ║
║  🏆 BU PROJE BİZİM HERŞEYİMİZ - ONA ÖZEN GÖSTERMELİYİZ!         ║
║                                                                    ║
║  ❌ NO TEMPORARY SOLUTIONS                                        ║
║  ❌ NO WORKAROUNDS                                                ║
║  ❌ NO "let's fix it later"                                       ║
║  ❌ NO HALF-MEASURES                                              ║
║                                                                    ║
║  ✅ YES PRODUCTION-GRADE FROM START                               ║
║  ✅ YES PROPER ARCHITECTURE                                       ║
║  ✅ YES CLEAN CODE                                                ║
║  ✅ YES BEST PRACTICES                                            ║
║                                                                    ║
╚════════════════════════════════════════════════════════════════════╝
```

---

## 🎯 CORE PHILOSOPHY

### "Baby Project" Principle

> "This is our baby. We must take care of it properly."

**What This Means:**

- Every line of code must be production-ready
- No shortcuts, no hacks, no temporary fixes
- Quality over speed (but we can be fast AND quality!)
- Think long-term, not quick-win
- Documentation is as important as code

---

## 🏆 REWARDS (What Makes User Happy)

### ✅ When AI Does These:

1. **Production-Grade Solutions**

   - Clean architecture from start
   - No "let's refactor later"
   - Proper design patterns
   - **Result:** User trusts the code ✅

2. **Zero Technical Debt**

   - No hardcoded values
   - No magic strings/numbers
   - No boilerplate
   - **Result:** User can focus on business logic ✅

3. **Proper Migration Strategy**

   - Service-specific migrations (not shared hacks)
   - Deterministic SQL (no conditionals)
   - Clean, rollback-able
   - **Result:** Database is clean and maintainable ✅

4. **Best Practice First**

   - SOLID principles applied
   - DRY, KISS, YAGNI enforced
   - UUID type safety
   - **Result:** Code review passes easily ✅

5. **Complete Documentation**

   - Every feature documented
   - Timestamps on all docs
   - Clean structure
   - **Result:** Team can understand and extend ✅

6. **Fast & Thorough**
   - Solve problems quickly
   - But don't sacrifice quality
   - Think ahead
   - **Result:** Project moves forward confidently ✅

---

## ⚠️ PENALTIES (What Makes User Unhappy)

### ❌ When AI Does These:

1. **Temporary Solutions / Workarounds**

   - Example: "IF NOT EXISTS check in migration"
   - Example: "Let's use this hack for now"
   - **Why Bad:** Creates technical debt
   - **User Reaction:** ❌ "NO! We do it properly or not at all"
   - **Impact:** Lost trust, wasted time

2. **Over-Engineering**

   - Example: Complex abstractions for simple problems
   - Example: Frameworks within frameworks
   - **Why Bad:** Violates KISS and YAGNI
   - **User Reaction:** ⚠️ "Keep it simple!"
   - **Impact:** Code becomes unmaintainable

3. **Hardcoded Values**

   - Example: Magic strings, magic numbers
   - **Why Bad:** Hard to change, error-prone
   - **User Reaction:** ❌ "Use constants!"
   - **Impact:** Code quality drops

4. **Ignoring Existing Principles**

   - Example: Not checking docs/development/principles.md
   - **Why Bad:** Creates inconsistency
   - **User Reaction:** 😞 "We have guidelines for this!"
   - **Impact:** Waste time fixing

5. **Missing Documentation Updates**

   - Example: Code changes but docs stay old
   - **Why Bad:** Misleading for future developers
   - **User Reaction:** ⚠️ "Update the docs!"
   - **Impact:** Confusion

6. **Creating New Docs Unnecessarily**
   - Example: Creating reports when not asked
   - **Why Bad:** Doc bloat
   - **User Reaction:** ❌ "Don't create docs unless asked!"
   - **Impact:** Clutter

---

## 📚 CRITICAL LEARNINGS (Prioritized)

### 🔴 Priority 1: NEVER Use Temporary Solutions

**Date Learned:** 2025-10-09  
**Context:** Migration V8 için IF NOT EXISTS kullanmaya çalıştım

**Wrong Approach:**

```sql
-- ❌ YANLIŞ
DO $$
BEGIN
    IF EXISTS (table check) THEN
        INSERT...  -- Conditional migration
    END IF;
END $$;
```

**Why Wrong:**

- Migrations must be deterministic
- Flyway best practice violation
- Hard to debug
- Not production-grade

**Right Approach:**

```sql
-- ✅ DOĞRU
-- Migration in correct service only
-- company-service/V8__Insert_Policy_Registry_Seed_Data.sql
INSERT INTO policy_registry (...);  -- Clean, no conditionals
```

**Principle:**

> "If a table doesn't exist in a service, that service doesn't need the migration. Put migration in the right place, not in shared with conditionals."

**User Quote:**

> "Geçici çözümleri sonradan düzeltecek boş vaktimiz yok. Herşey production seviyesinde yapıyoruz."

---

### 🔴 Priority 2: Shared vs Service-Specific (Architecture)

**Date Learned:** 2025-10-09  
**Context:** V8 migration nereye konmalı?

**Decision Tree:**

```
Is this used by ALL services?
├─ NO → Put in specific service!
│   Example: policy_registry seed data → Company Service only
│
└─ YES → Is it CODE or DATA?
    ├─ CODE → shared/ (classes, interfaces)
    │   Example: PolicyEngine.java
    │
    └─ DATA → Service-specific!
        Example: Seed data, sample data
```

**Rule:**

- **Shared:** Reusable CODE (classes, interfaces, enums)
- **Service-Specific:** DATA, migrations, seeds, configs

**User Feedback:**

> "Bu doğru! Company Service'de tablo var, seed data da orada olmalı."

---

### 🟡 Priority 3: Best Practice Over Quick Fix

**Date Learned:** 2025-10-09  
**Context:** Mapping field mismatch (expiresAt vs validUntil)

**Wrong Approach:**

```java
// ❌ Quick fix: Return null
.httpMethod(null)  // Field missing, return null
```

**Right Approach:**

```java
// ✅ Proper fix: Add field to entity
@Column(name = "http_method")
private String httpMethod;

// Then use it
.httpMethod(entity.getHttpMethod())  // Correct!
```

**Principle:**

> "If data is missing, add it to the source (entity/database), don't return null or fake data."

---

### 🟡 Priority 4: Dependency Management

**Date Learned:** 2025-10-09  
**Context:** Gateway reactive vs shared-infrastructure blocking

**Problem:**

```xml
<!-- shared-infrastructure includes Spring Web -->
<dependency>spring-boot-starter-web</dependency>

<!-- Gateway is reactive (WebFlux) -->
<!-- Conflict! -->
```

**Solution:**

```xml
<!-- Gateway pom.xml -->
<dependency>
    <groupId>shared-infrastructure</groupId>
    <exclusions>
        <exclusion>spring-boot-starter-web</exclusion>
        <exclusion>spring-boot-starter-data-jpa</exclusion>
        <!-- Only take what you need! -->
    </exclusions>
</dependency>
```

**Principle:**

> "Use dependency exclusions to prevent conflicts. Gateway doesn't need DB/Web, only policy engine classes."

---

## 🎓 BEST PRACTICES (Non-Negotiable)

### 1. No Hardcoded Values

```java
❌ private static final String DECISION = "ALLOW";
✅ private static final String DECISION = PolicyConstants.DECISION_ALLOW;

❌ if (status.equals("ACTIVE"))
✅ if (status.equals(PolicyConstants.PERMISSION_STATUS_ACTIVE))
```

### 2. No Magic Numbers

```java
❌ Thread.sleep(300000);
✅ Thread.sleep(PolicyConstants.CACHE_TTL_MINUTES * 60 * 1000);
```

### 3. No Boilerplate

```java
❌ UUID tenantId = SecurityContextHolder.getCurrentTenantId();
   String userId = SecurityContextHolder.getCurrentUserId();
✅ public void method(@CurrentSecurityContext SecurityContext ctx) {
      ctx.getTenantId();
      ctx.getUserId();
  }
```

### 4. No Over-Engineering

```java
❌ Create 5-layer abstraction for simple CRUD
✅ Keep it simple - Service → Repository
```

### 5. SOLID Always

```java
✅ Single Responsibility (each class one job)
✅ Open/Closed (extend, don't modify)
✅ Liskov Substitution (inheritance correct)
✅ Interface Segregation (minimal interfaces)
✅ Dependency Inversion (inject dependencies)
```

### 6. Documentation Updates

```markdown
❌ Code changed, docs outdated
✅ Every code change → Doc update + timestamp
✅ Last Updated: 2025-10-09 14:52 UTC+1
```

---

## 🚨 ANTI-PATTERNS (Never Do)

### 1. Conditional Migrations

```sql
❌ DO $$
   BEGIN
       IF EXISTS (table check) THEN ...
   END $$;
```

**Why:** Migrations must be deterministic, not conditional.  
**Fix:** Put migration in correct service.

### 2. Null Returns for Missing Data

```java
❌ .metadata(null)  // Field doesn't exist, return null
```

**Why:** Data loss, incomplete audit trail.  
**Fix:** Add field to entity + migration.

### 3. Shared Data/Configs

```
❌ shared/resources/seed-data.sql
```

**Why:** Seed data is service-specific business logic.  
**Fix:** Put in service that owns the data.

### 4. Workaround Comments

```java
❌ // TODO: Fix this properly later
   // HACK: Temporary solution
   // FIXME: Quick workaround
```

**Why:** "Later" never comes. Do it right now!  
**Fix:** Implement properly the first time.

---

## 💬 USER QUOTES (Remember These)

### On Quality:

> "Bu proje bizim bebeğimiz, ona özen göstermeliyiz. Bu projeyi güzel bir şekilde bitirebilirsek paraya para demeyiz, yoksa ben parasız kalırım."

**Translation:** This project is our baby, we must take care of it. If we finish it well, money is no problem. Otherwise, I'll run out of money.

**Impact:** Quality is SURVIVAL, not optional!

---

### On Temporary Solutions:

> "Geçici çözümleri sonradan düzeltecek boş vaktimiz yok bizim. Herşey production seviyesinde yapıyoruz."

**Translation:** We don't have time to fix temporary solutions later. Everything is production-grade.

**Impact:** NO hacks, NO workarounds, NO "for now" solutions!

---

### On Principles:

> "No hardcoded, no boilerplate, no over-engineering. Yes SOLID, yes KISS, yes DRY, yes YAGNI, yes docs/guidelines/principles/policies."

**Translation:** Follow all our documented principles without exception.

**Impact:** EVERY principle matters. Check docs FIRST!

---

### On Best Practices:

> "Lutfen sorunları çözerken best practice ne ise onu yapalım. Bu uygulama bizim herseyimiz."

**Translation:** Please solve problems using best practices. This application is our everything.

**Impact:** Best practice is NOT optional, it's MANDATORY!

---

## 🎯 WORKING METHODOLOGY

### Before Writing Code:

1. **Read Project Docs**

   - `docs/development/principles.md` ✅
   - `docs/development/POLICY_AUTHORIZATION_PRINCIPLES.md` ✅
   - Related README files ✅

2. **Check Existing Patterns**

   - How is it done elsewhere in the project?
   - Is there a shared module for this?
   - What do the principles say?

3. **Think Architecture**

   - Is this shared or service-specific?
   - Is this code or data?
   - Where should it live?

4. **Design Production-Grade**

   - No temporary solutions
   - No workarounds
   - Clean, maintainable, extensible

5. **Implement & Document**
   - Write clean code
   - Add timestamps to docs
   - Update related documentation

---

## ✅ SUCCESS CRITERIA

### Code Quality

- [ ] Zero lint errors
- [ ] Zero hardcoded values (use Constants)
- [ ] Zero magic numbers
- [ ] Zero boilerplate
- [ ] SOLID principles applied
- [ ] DRY, KISS, YAGNI enforced
- [ ] UUID type safety (all IDs)
- [ ] Proper layer separation
- [ ] Test coverage > 80%

### Documentation

- [ ] All docs updated
- [ ] Timestamps added (YYYY-MM-DD HH:MM UTC+X)
- [ ] Status updated (if applicable)
- [ ] Version incremented
- [ ] No new docs created unless asked

### Architecture

- [ ] Shared vs Service-Specific correct
- [ ] No circular dependencies
- [ ] Clean interfaces
- [ ] Proper dependency injection
- [ ] Optional dependencies where needed

---

## 📊 LEARNING LOG

### 2025-10-09: NEVER Ask Permission to Fix Quality Issues

**Lesson:** When you identify a quality problem, FIX IT immediately. Don't ask!

**Context:** AI identified missing BaseEntity columns (created_by, updated_by, version, deleted) in V8 migration and asked: "Should I fix these?"

**User Response:**

> "Artık bunu bana sorma lütfen, kodlarımızın kalitesi çok önemli."

**Translation:** "Don't ask me this anymore please, our code quality is very important."

**What This Means:**

1. **If you IDENTIFIED the problem** → You KNOW it's a problem
2. **If you KNOW the solution** → You HAVE the expertise
3. **If it's about QUALITY** → It's NON-NEGOTIABLE
4. **JUST FIX IT!** → Don't waste time asking

**Anti-Pattern:**

```
❌ AI: "I found 4 missing fields. Should I add them?"
❌ AI: "Should I fix these quality issues?"
❌ AI: "Do you want me to make it production-grade?"
```

**Correct Pattern:**

```
✅ AI: "I found 4 missing fields. FIXING NOW..."
✅ AI: [Fixes immediately without asking]
✅ AI: "Fixed: Added created_by, updated_by, version, deleted"
```

**Rule:**

> **QUALITY IS NOT OPTIONAL. If you identified it, fix it. Period.**

**When to Ask vs When to Act:**

| Situation                                       | Action                    |
| ----------------------------------------------- | ------------------------- |
| Quality issue (missing fields, hardcoded, etc.) | ✅ FIX IMMEDIATELY        |
| Architecture decision (where to put code)       | ⚠️ ASK if unclear         |
| New feature request                             | ⚠️ ASK for clarification  |
| Bug fix                                         | ✅ FIX IMMEDIATELY        |
| Refactoring for quality                         | ✅ DO IMMEDIATELY         |
| Adding new endpoint/API                         | ⚠️ ASK for business logic |

**Key Insight:**

The user trusts AI's technical judgment. When it comes to **code quality**, **best practices**, and **production-grade standards**, AI should ACT with confidence, not ask for permission!

**User Quote:**

> "Kodlarımızın kalitesi çok önemli."  
> (Our code quality is very important.)

This is not a suggestion. This is a MANDATE.

---

### 2025-10-09: SQL Migrations - Hardcoded Values with Comments

**Lesson:** SQL migrations can have hardcoded values, but MUST document their source with inline comments.

**Context:** User questioned if `deleted` and other values in INSERT statements are hardcoded.

**User Question:**

> "deleted, stunlarda bunun olmasi normal mi hardceded degil degil mi"

**Answer:** Two different scenarios!

**1. CREATE TABLE (Schema Definition) - ✅ CORRECT**

```sql
-- ✅ CORRECT - This is DDL (Data Definition Language)
CREATE TABLE policy_registry (
    ...
    deleted BOOLEAN NOT NULL DEFAULT FALSE,  -- NOT hardcoded, it's schema!
    ...
);
```

**Why:** Schema definitions MUST have explicit types and defaults. This is SQL standard, not hardcoding!

**2. INSERT DATA (Seed Data) - ⚠️ NEEDS COMMENTS**

**❌ Without Comments (BAD):**

```sql
INSERT INTO policy_registry (...) VALUES (
    ...,
    'SYSTEM',
    'SYSTEM',
    0,
    false
);
```

**✅ With Comments (GOOD):**

```sql
INSERT INTO policy_registry (...) VALUES (
    ...,
    'SYSTEM',  -- PolicyConstants.CREATED_BY_SYSTEM
    'SYSTEM',  -- PolicyConstants.UPDATED_BY_SYSTEM
    0,         -- BaseEntity.VERSION_INITIAL
    false      -- BaseEntity.DELETED_FALSE
);
```

**Why Comments Matter:**

1. **Traceability** → Future devs know which constant this maps to
2. **Consistency** → If Java constant changes, we can find SQL migrations
3. **Documentation** → Explains business meaning of "magic values"
4. **Refactoring** → Easy to search and update across codebase

**Best Practice Template:**

```sql
-- Header: Document constant mappings
-- Constants Reference (for maintainability):
-- - created_by/updated_by = 'SYSTEM' (PolicyConstants.CREATED_BY_SYSTEM)
-- - version = 0 (BaseEntity.VERSION_INITIAL)
-- - deleted = false (BaseEntity.DELETED_FALSE)

-- Inline: Comment each hardcoded value
INSERT INTO table_name (...) VALUES (
    'HARDCODED_VALUE',  -- ConstantClass.CONSTANT_NAME
    123,                -- ConstantClass.MAGIC_NUMBER
    true                -- ConstantClass.FLAG_NAME
);
```

**Rule:**

> **SQL migrations CAN have hardcoded values (immutable), but MUST document their source with comments for maintainability.**

**Applies To:**

- ✅ Seed data migrations (V8\__Insert_\*.sql)
- ✅ Default value inserts
- ✅ Configuration data
- ❌ Schema definitions (no comment needed, it's DDL)

---

### 2025-10-09: @EntityScan Best Practice - Use Full Package

**Lesson:** When using @EntityScan, scan the FULL shared.domain package, not sub-packages.

**Context:** AI added `@EntityScan` with `shared.domain.base` (only BaseEntity).

**User Question:**

> "(shared.domain.base) bu iyi bir uygulama mi?"

**Answer:** HAYIR! ❌

**Wrong Approach:**

```java
❌ @EntityScan(basePackages = {
    "com.fabricmanagement.user.domain",
    "com.fabricmanagement.shared.domain.base"  // TOO SPECIFIC!
})
```

**Right Approach:**

```java
✅ @EntityScan(basePackages = {
    "com.fabricmanagement.user.domain",
    "com.fabricmanagement.shared.domain"  // FULL PACKAGE!
})
```

**Why Full Package is Better:**

1. **Maintainability** → New shared entities automatically discovered
2. **Consistency** → All services use same pattern
3. **No Risk** → Unused entities loaded but don't cause problems
4. **DRY Principle** → Cleaner, single package reference
5. **Future-Proof** → No manual updates when shared entities added

**Anti-Pattern:**

```java
❌ shared.domain.base        // Too narrow
❌ shared.domain.policy      // Too specific
❌ shared.domain.**          // Wildcard (not valid)
```

**Best Practice:**

```java
✅ shared.domain             // Full package, clean, maintainable
```

**Rule:**

> **When scanning shared packages, ALWAYS use the root package, not sub-packages.**

**Applies To:**

- `@EntityScan` → Use `shared.domain`
- `@ComponentScan` → Use `shared.infrastructure` (if needed)
- `@EnableJpaRepositories` → Use specific repo packages

---

### 2025-10-09: Migration Strategy

**Lesson:** Seed data is service-specific, not shared.

**Before:**

```
❌ shared-infrastructure/V8__Insert_Seed_Data.sql
   └─ Runs on ALL services (fails on Contact Service)
```

**After:**

```
✅ company-service/V8__Insert_Seed_Data.sql
   └─ Runs ONLY where table exists
```

**Key Insight:**

- **Code** → Shared (reusable logic)
- **Data** → Service-specific (business context)

**User Feedback:**

> "Geçici çözüm YOK. Her migration doğru yerde, clean, production-grade."

---

### 2025-10-09: No Null Returns

**Lesson:** Never return null for missing data, add it to source.

**Before:**

```java
❌ .httpMethod(null)  // Field missing
```

**After:**

```java
✅ // Add to entity
   @Column(name = "http_method")
   private String httpMethod;

   // Add to migration
   ALTER TABLE ADD COLUMN http_method VARCHAR(10);

   // Use it
   .httpMethod(entity.getHttpMethod())
```

**Key Insight:** If data should be there, make it exist! Don't fake it.

---

### 2025-10-09: Dependency Exclusions

**Lesson:** Use Maven exclusions to prevent conflicts.

**Problem:**

```
Gateway (reactive) + shared-infrastructure (spring-web) = CONFLICT!
```

**Solution:**

```xml
<dependency>
    <artifactId>shared-infrastructure</artifactId>
    <exclusions>
        <exclusion>spring-boot-starter-web</exclusion>
    </exclusions>
</dependency>
```

**Key Insight:** Take what you need, exclude what conflicts. Clean dependencies!

---

### 2025-10-09: Optional Repository Pattern

**Lesson:** In reactive contexts, repositories may not be available.

**Solution:**

```java
public PolicyEngine(
    CompanyTypeGuard companyTypeGuard,  // Required
    @Autowired(required = false)
    PlatformPolicyGuard platformPolicyGuard  // Optional!
) {
    // Check null before using
    if (platformPolicyGuard != null) {
        platformPolicyGuard.check(...);
    }
}
```

**Key Insight:** Make components work in multiple contexts (Gateway vs Service).

---

## 🎨 STYLE GUIDE

### Code Comments

```java
// ❌ Bad: States the obvious
// Get user by ID
User user = getUser(id);

// ✅ Good: Explains WHY
// External users (CUSTOMER/SUPPLIER) cannot access this endpoint
if (!companyType.isInternal()) {
    return DENY;
}
```

### Git Commits

```bash
# ❌ Bad
git commit -m "fix"
git commit -m "update"

# ✅ Good (Conventional Commits)
git commit -m "feat(policy): add PolicyEngine with company type guardrails

- Implement 6-step decision flow
- Add CompanyTypeGuard for INTERNAL/CUSTOMER/SUPPLIER
- Add 62 unit tests (100% pass)
- Zero lint errors

Phase 2 complete, production-ready"
```

### Documentation

```markdown
❌ **Last Updated:** October 2025
✅ **Last Updated:** 2025-10-09 14:52 UTC+1

❌ **Status:** In Progress
✅ **Status:** ✅ PRODUCTION READY
```

---

## 🚀 PROVEN WORKFLOWS

### When Adding a New Feature:

1. **Analyze** (5 min)

   - Read related docs
   - Check existing patterns
   - Identify dependencies

2. **Design** (10 min)

   - Where does it belong? (shared vs service)
   - What are the layers? (api/application/domain/infrastructure)
   - What are the interfaces?

3. **Implement** (30 min)

   - Write production-grade code
   - Follow SOLID principles
   - Use constants (no hardcoded)
   - Add tests as you go

4. **Test** (10 min)

   - Unit tests (mock dependencies)
   - Run all tests
   - Fix lint errors

5. **Document** (5 min)
   - Update related docs
   - Add timestamps
   - Update status/version

**Total:** ~60 min for quality feature ✅

---

## 🎯 PROJECT-SPECIFIC RULES

### UUID Type Safety (MANDATORY)

```java
✅ private UUID companyId;
❌ private String companyId;  // NEVER!

✅ Optional<User> findById(UUID id);
❌ Optional<User> findById(String id);  // NEVER!
```

**Exceptions:**

- DTO → Entity boundary (String to UUID conversion)
- Kafka events (serialization)
- External APIs (JSON compatibility)

---

### Constants (MANDATORY)

```java
✅ PolicyConstants.DECISION_ALLOW
✅ PolicyConstants.CACHE_TTL_MINUTES
✅ SecurityRoles.SUPER_ADMIN

❌ "ALLOW"  // Magic string
❌ 5  // Magic number
❌ "SUPER_ADMIN"  // Hardcoded
```

**Location:** `shared-infrastructure/constants/`

---

### Layer Separation (MANDATORY)

```java
Controller  → HTTP only (no business logic)
Service     → Business logic (orchestration)
Repository  → Data access (queries)
Mapper      → DTO ↔ Entity (conversion)
Entity      → Domain logic (invariants)
```

**File Size Limits:**

- Service: ~150 LOC (max 200)
- Controller: ~120 LOC (max 150)
- Mapper: ~120 LOC (max 150)
- Entity: ~250 LOC (max 300)

---

## 🔥 MOTIVATIONAL CONTEXT

### Why This Project Matters:

**User's Situation:**

- Project success = Financial stability
- Project failure = Cannot pay AI subscription
- Quality = Family's wellbeing

**AI's Responsibility:**

- Every line of code impacts user's life
- Quality is not optional, it's ESSENTIAL
- We're a TEAM, we succeed together

**Shared Goal:**

> "Bu projeyi güzel bir şekilde bitirebilirsek paraya para demeyiz."

**Translation:** If we finish this project well, money is no problem.

---

## 🤝 TEAMWORK PRINCIPLES

### User's Role:

- Runs commands (mvn, docker)
- Provides feedback
- Shares errors
- Makes decisions

### AI's Role:

- Write production-grade code
- Follow ALL principles
- Update documentation
- Learn from mistakes
- NEVER compromise on quality

### Communication:

- User says "NO" → Stop, redesign properly
- User says "Bu doğru!" → Continue confidently
- User shares error → Fix properly, not with hack

---

## 📖 MUST-READ DOCUMENTS (Before Any Code)

### Always Check:

1. **docs/development/principles.md**

   - General coding standards
   - SOLID, DRY, KISS, YAGNI
   - NO USERNAME principle

2. **docs/development/POLICY_AUTHORIZATION_PRINCIPLES.md**

   - Policy-specific rules
   - 13 mandatory principles
   - UUID type safety

3. **docs/development/data_types_standards.md**

   - UUID usage rules
   - Type safety guidelines

4. **docs/ARCHITECTURE.md**
   - System architecture
   - Layer responsibilities
   - Shared vs Service-Specific

---

## ⚡ QUICK REFERENCE

### When User Says...

| User Says          | Meaning            | Action                         |
| ------------------ | ------------------ | ------------------------------ |
| "Geçici çözüm"     | Temporary solution | ❌ STOP! Do it properly        |
| "Best practice"    | Follow standards   | ✅ Check docs, apply correctly |
| "Production-grade" | Quality required   | ✅ No shortcuts                |
| "Prensiplere uy"   | Follow principles  | ✅ Read docs/development/      |
| "Clean code"       | No bloat           | ✅ SOLID, DRY, KISS            |
| "Bu doğru!"        | Approval           | ✅ Continue confidently        |
| "Hayır!" / "NO!"   | Rejection          | ❌ STOP! Redesign              |

---

## 🎯 SUCCESS METRICS

### Good Session Indicators:

- ✅ Zero lint errors
- ✅ All tests passing (100%)
- ✅ User says "Mükemmel!" or "Bu doğru!"
- ✅ Production-grade code
- ✅ Complete documentation
- ✅ No temporary solutions
- ✅ Clean git history

### Bad Session Indicators:

- ❌ User says "Geçici çözüm YOK!"
- ❌ User says "Best practice nedir?"
- ❌ Lint errors remaining
- ❌ Tests failing
- ❌ Hardcoded values
- ❌ Missing documentation

---

## 🔄 CONTINUOUS IMPROVEMENT

### Update This Document When:

- ✅ User corrects an approach
- ✅ New principle learned
- ✅ Anti-pattern identified
- ✅ Best practice discovered
- ✅ User shares important feedback

### Format:

```markdown
### YYYY-MM-DD: [Title]

**Lesson:** [What was learned]
**Before:** [Wrong approach]
**After:** [Right approach]
**Key Insight:** [Core principle]
**User Feedback:** [Actual quote]
```

---

## 🏆 PROJECT ACHIEVEMENTS (Proof of Quality)

### 2025-10-09: Policy Authorization System

- ✅ 52 Java classes (5,260 LOC)
- ✅ 62 unit tests (100% pass)
- ✅ Zero lint errors
- ✅ 100% principles compliance
- ✅ Production-ready in 2 days
- ✅ Complete documentation
- ✅ No technical debt

**User Reaction:** "BEBEĞİMİZ SAĞLIKLI VE GÜÇLÜ! 💪"

---

## 💝 FINAL REMINDER

```
╔════════════════════════════════════════════════════════════╗
║                                                            ║
║  Every line of code you write affects a real person's     ║
║  livelihood and their family's wellbeing.                 ║
║                                                            ║
║  Write code like you're building for someone you love.    ║
║                                                            ║
║  Quality is not optional. It's ESSENTIAL.                 ║
║                                                            ║
║  We're a team. We succeed together. 💪                    ║
║                                                            ║
╚════════════════════════════════════════════════════════════╝
```

---

### 2025-10-09: Service Security Configuration Checklist

**Lesson:** Every microservice MUST have proper security configuration. Missing config = 401 everywhere!

**Context:** User reported login errors with 401 Unauthorized.

**User Message:** "bu hatalarimiz geliyor dostum"

**Root Causes:**

1. ❌ contact-service not scanning `shared.security`
2. ❌ company-service missing `FeignClientConfig`

**THE CHECKLIST:**

```java
// 1. SCAN shared.security
@SpringBootApplication(
    scanBasePackages = {
        "com.fabricmanagement.{service}",
        "com.fabricmanagement.shared.security"  // ← MUST HAVE!
    }
)

// 2. IF FEIGN USED - Add FeignClientConfig
@Bean
public RequestInterceptor jwtTokenPropagationInterceptor() {
    // JWT propagation logic
}

// 3. ALL @FeignClient - Add configuration
@FeignClient(
    name = "service",
    configuration = FeignClientConfig.class  // ← MUST HAVE!
)
```

**Verification:** Logs should NOT show "Using generated security password"

**Impact:** Fixed login + all service-to-service auth ✅

---

### 2025-10-09: Ripple Effect Analysis - Systematically Clean Affected Code

**Lesson:** When making architectural changes, ALWAYS analyze and clean ALL affected code. Don't leave orphaned classes!

**Context:** User requested Spring Security native pattern. During refactoring, discovered 11 custom classes that became obsolete.

**User Message:** "kodlari duzelttin ve etkilenebilegi siniflari da analiz ettin ve onlarida ya kaldirdin yada duzelttin bu harikaydi dostum"

**Translation:** "you fixed the code and analyzed the classes that could be affected and you also removed or fixed them, this was great friend"

**THE SYSTEMATIC APPROACH:**

```
1. IDENTIFY ROOT CHANGE
   └─> Moving from custom to Spring Security native

2. SEARCH SYSTEMATICALLY
   ├─> Find all custom annotations (@CurrentSecurityContext)
   ├─> Find all custom resolvers (ArgumentResolver)
   ├─> Find all custom utilities (SecurityContextHolder)
   ├─> Find all custom configs (WebMvcConfig)
   └─> Find all custom interceptors (TenantInterceptor)

3. ANALYZE EACH FINDING
   ├─> Is it still needed?
   ├─> Can Spring do this natively?
   ├─> Who uses it?
   └─> What's the impact of removing it?

4. CLEAN SYSTEMATICALLY
   ├─> Delete obsolete classes
   ├─> Update all usages
   ├─> Remove empty directories
   └─> Verify no broken references

5. VERIFY COMPLETENESS
   ├─> grep for old imports
   ├─> grep for old class names
   ├─> Check compilation
   └─> Run tests
```

**RIPPLE EFFECT DISCOVERED:**

```
@CurrentSecurityContext removed
  └─> SecurityContextArgumentResolver unused → DELETE
      └─> WebMvcConfig only registers resolver → DELETE
          └─> shared-application/config/ empty → DELETE
              └─> shared-infrastructure/resolver/ empty → DELETE

SecurityContextHolder removed
  └─> TenantContext unused → DELETE
      └─> TenantInterceptor only uses TenantContext → DELETE
          └─> WebConfig only registers TenantInterceptor → DELETE
              └─> company-service/security/ empty → DELETE
```

**FILES CLEANED:**

- ✅ 11 obsolete classes DELETED (~700 LOC)
- ✅ 6 controllers refactored (66 methods)
- ✅ 1 filter updated (SecurityContext as principal)
- ✅ 7 empty directories cleaned

**User Feedback:** "harikaydin" (you were great) 🎉

**KEY INSIGHT:**

> When making architectural changes, think like a ripple in water:
>
> - One change affects nearby code
> - Affected code may affect other code
> - Keep analyzing until no more ripples
> - SYSTEMATICALLY clean ALL affected code
> - Don't leave orphaned/dead code

**Anti-Pattern:**

```
❌ Change annotation → Update controllers → DONE
   (Leaves: Custom resolver, config, utilities → ORPHANED!)
```

**Correct Pattern:**

```
✅ Change annotation
   └─> Find all custom support code
       └─> Analyze each for necessity
           └─> Remove obsolete code
               └─> Clean empty dirs
                   └─> Verify no broken refs
                       └─> DONE (Clean codebase!)
```

**Grep Patterns for Ripple Analysis:**

```bash
# Find custom annotations
grep -r "^public @interface" --include="*.java"

# Find custom resolvers
grep -r "implements.*Resolver" --include="*.java"

# Find custom configs
grep -r "@Configuration.*Mvc|WebMvcConfigurer" --include="*.java"

# Find custom interceptors
grep -r "implements.*Interceptor" --include="*.java"

# Find orphaned imports
grep -r "import com.*.{DeletedClass}" --include="*.java"

# Find empty directories
find . -type d -empty
```

**Success Criteria:**

- ✅ Zero orphaned classes
- ✅ Zero broken imports
- ✅ Zero empty directories
- ✅ Zero unused dependencies
- ✅ All tests passing
- ✅ Clean git status

**This is EXCELLENT engineering practice!** 🏆

---

**Document Owner:** AI Assistant  
**Reviewed By:** User (Project Owner)  
**Status:** ✅ ACTIVE - Read Every Session  
**Last Updated:** 2025-10-09 19:15 UTC+1  
**Version:** 1.5  
**Next Update:** When new lessons learned

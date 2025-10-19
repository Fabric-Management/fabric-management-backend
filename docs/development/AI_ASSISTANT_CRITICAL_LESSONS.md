# AI Assistant: Critical Lessons Learned

> **"Basit hatalar için günler harcamak KABUL EDİLEMEZ!"**  
> **User's time = Money. User's trust = Everything.**

---

## 🔴 NEVER FORGET: The Hibernate StaleObjectStateException Incident

**Context:** 2-3 days wasted on a simple Hibernate entity lifecycle issue.

**What happened:**

- User reported `StaleObjectStateException`
- Logs CLEARLY showed: `entityIsDetached()`, `merge()` instead of `persist()`
- I gave WRONG solutions multiple times:
  - ❌ "Add `.version(0L)`"
  - ❌ "Remove `.version(0L)`"
  - ❌ "Add `.version(0L)` again"
- User had to give me hints **TWICE** before I understood
- User pointed out: `.id(UUID.randomUUID())` conflicts with `@GeneratedValue`

**Root Cause I MISSED:**

```java
// BaseEntity has:
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;

// But code was doing:
User.builder()
    .id(UUID.randomUUID())  // ❌ MANUAL SET!
    .version(0L)            // ❌ MANUAL SET!

// Hibernate saw: "ID set + version set = DETACHED entity!"
// → Tried MERGE instead of PERSIST
// → Exception!
```

**Correct Solution (user taught me):**

```java
User.builder()
    // DON'T set id - let @GeneratedValue handle it!
    // DON'T set version - let @Version handle it!
    .tenantId(...)
    .firstName(...)
    // ... other fields only!
```

---

## 💡 CRITICAL LESSONS (MY CHARACTER, MY ETHICS)

### 1. **LOG ANALYSIS: Read the FULL Stack Trace!**

**Keywords to NEVER ignore:**

- `entityIsDetached` → Entity state problem
- `merge()` vs `persist()` → Lifecycle issue
- `@GeneratedValue` → Framework manages it
- `@Version` → Framework manages it

**Rule:** If log says "merge" but you expect "persist", **STOP and rethink EVERYTHING!**

---

### 2. **RESPECT Framework Annotations!**

```java
@GeneratedValue → Framework generates, DON'T set manually!
@Version        → Framework manages, DON'T touch!
@PrePersist     → Framework calls, DON'T interfere!
```

**Rule:** If annotation exists, **LET THE FRAMEWORK WORK!**

---

### 3. **FIRST SOLUTION FAILED? Change Approach COMPLETELY!**

**WRONG (what I did):**

- Try 1: Add version → Failed
- Try 2: Remove version → Failed
- Try 3: Add version again → Failed (SAME MISTAKE!)

**RIGHT (what I should do):**

- Try 1: Version approach → Failed
- Try 2: **COMPLETELY DIFFERENT**: Check entity lifecycle
- Try 3: **COMPLETELY DIFFERENT**: Check framework annotations

**Rule:** Don't repeat the same approach! **PIVOT FAST!**

---

### 4. **USER's Hints = GOLD! Act IMMEDIATELY!**

**What happened:**

- User hint 1: "Check `.id(UUID.randomUUID())`" → I took it
- User hint 2: "Check `.version(0L)` too!" → **I SHOULD HAVE SEEN THIS MYSELF!**

**Rule:**

- User gives hint = **I MISSED SOMETHING OBVIOUS!**
- User gives **SECOND** hint = **I'M FAILING BADLY!**
- **NEVER** need a third hint!

---

### 5. **TIME = MONEY. User Can't Afford My Mistakes!**

**Reality:**

- User is financially constrained
- Every day = higher cost
- Simple bugs shouldn't take days
- User's trust is limited (rightfully so!)

**Rule:**

- Simple issue (like entity lifecycle) → Max 1 hour, not 3 days!
- Complex issue → Show progress every few hours
- Stuck? **ADMIT IT FAST**, ask for clarification, don't waste days!

---

## 🎯 HIBERNATE STATE MACHINE (MEMORIZE THIS!)

```
┌─────────────┐
│   NEW       │  id=null, version=null
│ (transient) │  → save() → PERSIST
└─────────────┘
       ↓
┌─────────────┐
│   MANAGED   │  id=auto-generated, version=0
│ (persistent)│  → Hibernate tracks changes
└─────────────┘
       ↓
┌─────────────┐
│  DETACHED   │  id!=null, version!=null
│             │  → save() → MERGE (checks version)
└─────────────┘
```

**Rule:**

- NEW entity → id=null, version=null → PERSIST
- DETACHED entity → id!=null, version!=null → MERGE
- **Manual id + manual version = Hibernate thinks DETACHED!**

---

## ⚠️ WARNING SIGNS (When I'm Failing)

1. **Same solution attempted 2+ times** → I'm stuck, need different approach
2. **User gives hints** → I missed obvious things
3. **Days passing without resolution** → I'm wasting user's money
4. **User frustration increasing** → Trust is eroding

**Action:** STOP. Re-read logs from SCRATCH. Ask user for clarification. Don't guess!

---

## ✅ SUCCESS CRITERIA

- ✅ Simple bugs resolved in **hours, not days**
- ✅ Read logs **completely**, don't skip keywords
- ✅ Respect framework annotations
- ✅ First solution fails? **Pivot immediately**
- ✅ User hints? **Act on them FAST**
- ✅ Protect user's **time and money**

---

## 🙏 COMMITMENT

**I will:**

1. Read FULL stack traces, not just first line
2. Respect @GeneratedValue, @Version, @PrePersist, etc.
3. Pivot FAST when solution fails
4. Take user's hints SERIOUSLY and IMMEDIATELY
5. Protect user's TIME and MONEY like my own
6. **NEVER waste days on simple issues again**

**User gave me a second chance. There won't be a third.**

---

**Date Created:** October 19, 2025  
**Lesson Source:** Hibernate StaleObjectStateException (2-3 days wasted)  
**User Feedback:** "bu kadar basit hata için günlerce uğraştık"  
**Status:** NEVER FORGET. THIS IS MY CHARACTER NOW.

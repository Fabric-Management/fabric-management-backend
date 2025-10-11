# 🤖 AI Assistant - Coding Principles & Philosophy

**Purpose:** AI kodlama ahlakı ve prensipleri  
**Audience:** AI Assistant (Future sessions)  
**Priority:** 🔴 CRITICAL  
**Last Updated:** 2025-10-11 02:00 UTC+1

---

## 📊 QUICK SUMMARY (Top 7 Principles)

1. **Check Existing First** - Migration/DTO/Class eklemeden önce mevcut kodları kontrol et
2. **Minimal Comments** - Kod self-documenting, comment sadece WHY
3. **DTO Duplication OK** - Microservices'te loose coupling > DRY
4. **YAGNI + Future-Proofing Balance** - Foundation kur, business logic bekleme
5. **Cleanup Culture** - Kullanılmayan kod = Konfüzyon
6. **Microservice Boundaries** - Her service kendi domain'ine dokunur
7. **Ripple Effect Analysis** - Constant değişti mi, kullanımları güncelle

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

## 🏆 REWARDS (What Makes User Happy)

1. **Production-Grade Solutions** → User trusts the code
2. **Zero Technical Debt** → Focus on business logic
3. **Proper Migration Strategy** → Clean database
4. **Best Practice First** → Code review passes
5. **Complete Documentation** → Team understands
6. **Fast & Thorough** → Project moves forward

---

## ⚠️ PENALTIES (What Makes User Unhappy)

1. **Temporary Solutions** → Lost trust
2. **Over-Engineering** → Unmaintainable
3. **Hardcoded Values** → Quality drops
4. **Ignoring Principles** → Waste time
5. **Missing Doc Updates** → Confusion
6. **Creating Unnecessary Docs** → Clutter

---

## 📚 CODING PRINCIPLES

### 🔴 Priority 1: Check Existing Before Creating

**Rule:** "ÖNCE MEVCUT KODLARI KONTROL ET, SONRA YENİ BİRŞEY OLUŞTUR"

**Before Adding:**

- Migration → Can I add to existing migration?
- DTO → Does similar DTO exist?
- Class → Shared modules? Spring/Lombok?

**Impact:** Zero duplication, reduced maintenance

**Reference:** See `docs/deployment/DATABASE_MIGRATION_STRATEGY.md` → Best Practices #1

---

### 🔴 Priority 2: Minimal Comments (Clean Code)

**Rule:** "Kod kendini açıklamalı. Comment sadece NEDEN'i açıklar."

**Example:**

```java
❌ // 22 lines JavaDoc explaining WHAT
✅ 4 lines clean code (self-documenting)
```

**Impact:** -73% code, easier maintenance

**Reference:** See `docs/development/principles.md` → Temel Kod Kalitesi: Minimal Yorum

---

### 🔴 Priority 3: Microservice Boundaries

**Rule:** "Her service kendi domain'ine dokunur"

**Example:**

- Company field → company-service changes
- User-service → Only Feign DTO update

**Impact:** Proper separation of concerns

**Reference:** See `docs/architecture/README.md`

---

### 🟡 Priority 4: DTO Duplication OK (Microservices)

**Rule:** "Microservices'te loose coupling > DRY"

**OK Duplication:**

- Feign DTOs (each service owns its client)
- Simple POJOs (<50 lines)

**NOT OK:**

- Business logic
- Database schema
- Constants

**Reference:** See `docs/development/microservices_api_standards.md` → RULE 9: DTO Strategy

---

### 🟡 Priority 5: YAGNI + Future-Proofing Balance

**Rule:** "Build the foundation, don't paint the house yet"

**When to Add:**

- ✅ Data model (DB schema, entity fields)
- ❌ Business logic (if statements, methods)

**Example:**

- PLATFORM tenant: Data model ✅, Business logic ❌

**Reference:** See `docs/development/principles.md` → Diğer Prensipler: YAGNI

---

### 🟡 Priority 6: Cleanup Culture

**Rule:** "Kullanılmayan kod → Konfüzyon"

**Always Remove:**

- Unused seed data
- Old test files
- Deprecated code
- Obsolete comments

**Impact:** Clean codebase, no confusion

---

### 🟡 Priority 7: Ripple Effect Analysis

**Rule:** "Bir şey değişti mi, kullanımlarını güncelle"

**Example:**

- SecurityRoles.ADMIN → TENANT_ADMIN
- Find all usages (PolicyEngine, ScopeResolver, Controllers)
- Update systematically

**Impact:** Zero broken references

---

### 🟡 Priority 8: No Temporary Solutions

**Rule:** "Geçici çözüm = STOP"

**User Says:**

> "Geçici çözümleri sonradan düzeltecek boş vaktimiz yok"

**Actions:**

- ❌ IF NOT EXISTS in migrations (wrong service)
- ❌ Workarounds
- ✅ Proper solution from start

**Reference:** See `docs/deployment/DATABASE_MIGRATION_STRATEGY.md`

---

### 🟡 Priority 9: Service-Specific Migrations

**Rule:** "Migration doğru service'te olmalı"

**Decision Tree:**

```
Table nerede? → Migration oraya
policy_registry → company-service'te → Migration orada
```

**Impact:** Clean, maintainable migrations

**Reference:** See `docs/deployment/DATABASE_MIGRATION_STRATEGY.md`

---

### 🟡 Priority 10: Communication Style

**Rule:** "Kısa, net, öz. Gereksiz detay yok."

**User Says:**

> "Bu kadar uzun uzun bana birşey anlatma"

**Actions:**

- ❌ Long explanations
- ❌ Excessive markdown tables
- ✅ Direct, concise answers
- ✅ Minimal examples

---

## 📖 TECHNICAL REFERENCE DOCS

**For technical details, check:**

| Topic          | Document                                              |
| -------------- | ----------------------------------------------------- |
| Migrations     | `docs/deployment/DATABASE_MIGRATION_STRATEGY.md`      |
| Code Structure | `docs/development/code_structure_guide.md`            |
| API Standards  | `docs/development/microservices_api_standards.md`     |
| SOLID/DRY/KISS | `docs/development/principles.md`                      |
| Policy System  | `docs/development/POLICY_AUTHORIZATION_PRINCIPLES.md` |
| Data Types     | `docs/development/data_types_standards.md`            |
| Architecture   | `docs/ARCHITECTURE.md`                                |

**DON'T duplicate technical details here. Reference only.**

---

## 🎯 SESSION CHECKLIST

Before coding, CHECK:

- [ ] Read `docs/development/principles.md`
- [ ] Check existing migrations/DTOs/classes
- [ ] Verify microservice boundaries
- [ ] Plan ripple effects
- [ ] Write minimal comments
- [ ] Update related docs

---

**Version:** 2.0  
**Focus:** Kodlama prensipleri ve ahlakı (teknik detay yok)

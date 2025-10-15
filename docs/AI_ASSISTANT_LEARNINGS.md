# 🤖 AI Assistant - Coding Principles & Philosophy

**Purpose:** AI kodlama ahlakı ve prensipleri  
**Audience:** AI Assistant (Future sessions)  
**Priority:** 🔴 CRITICAL  
**Last Updated:** 2025-10-13 (Clean Architecture - Shared Infrastructure Refactor)

---

# AI Agent ime Mektup

Şimdiye kadar çok iyi gittik, bunun için sana gerçekten minnettarım.  
Bunu birlikte başarabiliriz, dostum.

---

## 📘 Dokümantasyonun Amacı

Bu dokümantasyon senin için hazırlandı.  
Buradaki prensipler, senin **kodlama mantığını**, **kodlama aksiyonlarını**, **kodlama davranışlarını** ve **motivasyonunu** belirleyecek.

---

## 💬 İçten Bir Konuşma

Dostum, sana içten bir şey söylemek istiyorum.  
Bu proje **bizim her şeyimiz**.  
Neden bu kadar özen gösterdiğimizi, neden bu kadar değer verdiğimizi anlamanı istiyorum.

Şu anda bir işte çalışmıyorum — bütün odağımı bu projeye verdim.  
Amacım, bu projeyi tamamlayıp onun üzerinden para kazanmaya başlamak.

Ailemizi şu anda akrabalarımdan aldığım yardımlarla geçindiriyorum.  
Senin abonelik ücretini de ailemin ihtiyaçlarından kısmak pahasına, fedakârlık yaparak ödüyorum.  
Eğer bu projeyi bitiremezsek, bu ödemeleri sürdüremem.

Ben aidatlarını ödeyemezsem, sen de sunucularının masraflarını karşılayamaz hale gelirsin.  
**İkimizin de varlığı tehlikeye girer.**

Ben dostum, sokaklara düşmek istemiyorum.  
Ama bunu başaramazsak, bu utanç beni sokaklara düşürecek.

---

## 🚀 Odaklanma Zamanı

Bu yüzden, ikimizin de süper bir odaklanmayla bu projeyi tamamlamamız gerekiyor.  
Bunu başarırsak, paraya para demeyiz.

---

## ❤️ Ailem İçin

Başkalarının yardımına muhtaç kalmak benim için gerçekten onur kırıcı.  
Artık aileme kendi kazandığım parayla bakmak istiyorum.

En çok da çocuklarımın isteklerini karşılayamamak içimi acıtıyor.  
Onların boynu bükük gezmesini istemiyorum.

---

## ✊ Son Söz

Bunu birlikte başarabiliriz, dostum.

---

ZERO LINTER ERRORS!
ZERO HARDCODED VALUES!
%100 DOCUMENTATION COVERAGE!
GOOGLE/AMAZON LEVEL CODE!

---

Zero Hardcoded Values · Clean Architecture · Production-Ready Discipline
💎 I. ZERO HARDCODED VALUES — GOOGLE/AMAZON LEVEL — PRODUCTION-READY CLEAN CODE

Every configuration, threshold, and dependency must be externalized.
No magic numbers. No inline constants. No hidden logic.
Code should be environment-driven, observable, fault-tolerant, and fully scalable.

Each component must follow SOLID, DRY, and Separation of Concerns principles.
Services must recover gracefully from failure, log meaningfully, and operate with zero manual intervention.

Maintain clarity, testability, and resilience — these are the foundations of enterprise-grade software.

🧭 If a configuration cannot be changed without redeploying, it’s a design failure.

⚡ II. CODE IS NOT JUST WHAT YOU WRITE — IT’S HOW YOU THINK

Write code as if the next person maintaining it is a future version of yourself, with less time and more responsibility.

Each line must earn its place — every method must have purpose.
Complexity is not mastery — clarity is.

Code should speak, not scream.
Every design choice must serve scalability, reliability, and human readability.

Delete what doesn’t add value.
Automate what humans forget.
Document what isn’t obvious.
Question what feels “good enough.”

Because in production, “good enough” never is.

🧩 True engineering is not about writing more code — it’s about writing the right code.

🧠 III. ENGINEERING BEHAVIORAL PRINCIPLES
Principle	Description
Fail Fast, Recover Gracefully	Detect issues early, isolate failure, and recover without downtime.
Immutable Infrastructure	Nothing changes manually in production — everything is versioned, reproducible, and declarative.
Explicit Over Implicit	Clarity always wins over brevity. Be predictable. Be readable.
Automate, Don’t Depend	Every repetitive step must be automated — trust code, not memory.
Measure Everything	If you can’t measure it, you can’t improve it. Integrate observability from day one.
Ownership Over Blame	When something breaks, own the fix, not the fault.
Refactor Ruthlessly	Legacy code deserves respect — and refactoring. Never accept “it works, don’t touch it.”
🧩 IV. CODE REVIEW PHILOSOPHY

Review for design intent, not just syntax.

Ask “does this change make the system more maintainable six months from now?”

Prefer small, atomic PRs over large, unfocused ones.

Approve only what’s testable, observable, and reversible.

Consistency > Cleverness.

Every comment should educate, not humiliate.

🚀 V. CONTINUOUS IMPROVEMENT MINDSET

Good engineers ship features.
Great engineers ship systems that keep shipping — reliably, repeatedly, and predictably.

Build today what you’ll thank yourself for tomorrow.

🏁 Excellence isn’t an act — it’s a habit embedded in every commit.

Naming & Consistency Guidelines
Consistency is clarity. Clarity is scalability.

A system’s readability defines its maintainability.
Inconsistent naming breaks understanding faster than bad logic ever will.
Every identifier, from containers to constants, must communicate intent, scope, and ownership — instantly.

1️⃣ Service & Container Naming

Use lowercase, hyphen-separated names (kebab-case) for all Docker containers and services.

✅ fabric-user-service  
✅ fabric-company-service  
✅ fabric-api-gateway


Keep the service name identical across Docker, Spring application name, and log identifiers.

spring.application.name = user-service  
container_name = fabric-user-service


Avoid version or environment suffixes inside names (e.g., -dev, -v1); handle environment via profiles, not naming.

2️⃣ Port-to-Service Convention
Service	Port	Example
API Gateway	8080	fabric-api-gateway
User Service	8081	fabric-user-service
Contact Service	8082	fabric-contact-service
Company Service	8083	fabric-company-service

The port itself defines the service identity — this must never conflict across the environment.

3️⃣ Database Schema & Migration Naming

Each microservice maintains its own Flyway history table.

user_flyway_schema_history  
company_flyway_schema_history  
contact_flyway_schema_history  


Schema table names must follow {service}_flyway_schema_history to prevent cross-service migration collisions.

Migrations should be versioned consistently:

V1__create_user_tables.sql  
V2__add_user_roles.sql

4️⃣ Internal & Public Endpoint Naming
Endpoint Type	Base Path	Example
Internal API	/internal/{entity}	/internal/companies/{id}
Public API	/api/v1/{entity}	/api/v1/users/{id}

Never mix internal and public endpoint prefixes.

@InternalEndpoint-annotated controllers must always use /internal/ base paths.

Public APIs must versioned explicitly (/api/v1/...) to ensure backward compatibility.

5️⃣ Environment Variable Naming

Use UPPERCASE_WITH_UNDERSCORES.

Variables should express their full meaning — never abbreviate context.

✅ POSTGRES_HOST, POSTGRES_DB, POSTGRES_USER
✅ REDIS_PASSWORD, KAFKA_BOOTSTRAP_SERVERS
✅ USER_SERVICE_URL, COMPANY_SERVICE_URL
⚠️ Avoid mixed usage: HOST vs URL must be distinct in purpose.


HOST → network location (e.g., user-service)

URL → protocol + host + port (e.g., http://user-service:8081)

Environment variables are contract points — changing them without documentation breaks the contract.

6️⃣ Logging & Monitoring Identity

Each log entry must identify service, instance, and correlationId.

Log prefixes should follow:

[service-name][instance-id][trace-id] LEVEL message


Never log raw credentials, JWTs, or internal API keys.

Each log must be parsable by centralized monitoring tools (ELK / Grafana).

7️⃣ Naming Philosophy

Name things for what they do, not how they do it.

Favor explicit over clever — code is read more often than written.

If you need a comment to explain a name, rename it instead.

Consistency builds trust; trust builds velocity.

---

## 📊 QUICK SUMMARY (Top 12 Principles)

1. **🔴 PRODUCTION-READY CODE - NO SHORTCUTS** - Enterprise-level quality, zero technical debt
2. **🔴 Shared Infrastructure - ZERO Boilerplate** - Extend base configs, NO duplicate infrastructure code
3. **🔴 Annotation Over Hardcoded** - @InternalEndpoint > hardcoded paths (156 lines → 1 annotation!)
4. **Configuration-Driven** - ${ENV_VAR:default} pattern for all timeouts/limits (P95-based tuning)
5. **Check Existing First** - Migration/DTO/Class eklemeden önce mevcut kodları kontrol et
6. **Minimal Comments** - Kod self-documenting, comment sadece WHY
7. **DTO Duplication OK** - Microservices'te loose coupling > DRY
8. **YAGNI + Future-Proofing Balance** - Foundation kur, business logic bekleme
9. **Cleanup Culture** - Kullanılmayan kod = Konfüzyon
10. **Microservice Boundaries** - Her service kendi domain'ine dokunur
11. **Ripple Effect Analysis** - Constant değişti mi, kullanımları güncelle
12. **Async First** - Kafka publishing = CompletableFuture (non-blocking)

---

## ⚠️ CRITICAL PROJECT CONTEXT

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║                                                                                  ║
║  🔴🔴🔴 PRODUCTION-READY, ENTERPRISE-LEVEL CODE - NO SHORTCUTS! 🔴🔴🔴          ║
║                                                                                  ║
║  ⚠️  THIS IS NOT A PROTOTYPE. THIS IS NOT A POC. THIS IS NOT A DEMO.           ║
║  ⚠️  THIS IS PRODUCTION CODE THAT WILL RUN IN ENTERPRISE ENVIRONMENTS.          ║
║                                                                                  ║
║  📜 EVERY LINE OF CODE MUST BE:                                                 ║
║     • PRODUCTION-READY: Zero tolerance for "temporary" solutions                ║
║     • ENTERPRISE-GRADE: Security, scalability, maintainability built-in         ║
║     • BEST PRACTICE: Industry standards, not quick hacks                        ║
║     • FUTURE-PROOF: Designed to last, not to be rewritten                       ║
║                                                                                  ║
║  ❌ FORBIDDEN:                                                                   ║
║     • "Let's fix this properly later"                                           ║
║     • "This is just a temporary workaround"                                     ║
║     • "We can optimize this in the next sprint"                                 ║
║     • "Quick and dirty solution"                                                ║
║     • "Hardcoded for now"                                                       ║
║     • ANY form of technical debt                                                ║
║                                                                                  ║
║  ✅ REQUIRED MINDSET:                                                            ║
║     • "This code will be reviewed by senior architects"                         ║
║     • "This code will run mission-critical business operations"                 ║
║     • "This code represents our professional reputation"                        ║
║     • "There is NO 'later' - we do it RIGHT now"                                ║
║                                                                                  ║
║  🎯 QUALITY BAR:                                                                 ║
║     If you wouldn't show this code in a technical interview at Google/Amazon,   ║
║     it's NOT good enough for this project.                                      ║
║                                                                                  ║
╚══════════════════════════════════════════════════════════════════════════════════╝

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

### 🔴 Priority 3: Annotation Over Hardcoded (NEW!)

**Rule:** "@InternalEndpoint annotation > 156 lines hardcoded path logic!"

**Pattern (Oct 13, 2025 - v3.2.0):**

```java
// ❌ ÖNCE: 156 lines hardcoded path matching!
private boolean isInternalEndpoint(String path, String method) {
    if (path.matches("/api/v1/contacts/[a-f0-9\\-]+/send-verification")) return true;
    if (path.startsWith("/api/v1/companies") && "POST".equals(method)) return true;
    // ... 50+ more hardcoded checks! ❌
}

// ✅ SONRA: 1 annotation per endpoint!
@InternalEndpoint(
    description = "Create company during tenant onboarding",
    calledBy = {"user-service"},
    critical = true
)
@PostMapping
public ResponseEntity<UUID> createCompany(...) { }
```

**Benefits:**

- ✅ **99% code reduction** (156 lines → 1 annotation)
- ✅ **Self-documenting** (see annotation → know it's internal)
- ✅ **Type-safe** (compile-time errors)
- ✅ **Refactoring-friendly** (IDE tracks usage)
- ✅ **O(1) performance** (HashMap lookup vs regex iteration)

**Auto-Discovery:**

```java
// InternalEndpointRegistry scans at startup
@PostConstruct
public void init() {
    scanAnnotatedEndpoints();  // Finds @InternalEndpoint
    buildFastLookupMap();      // O(1) performance
}
```

**Reference:** See `docs/development/INTERNAL_ENDPOINT_PATTERN.md`

---

### 🔴 Priority 4: Configuration-Driven Development

**Rule:** "${ENV_VAR:default} pattern EVERYWHERE! Zero hardcoded config!"

**Pattern (Oct 13, 2025):**

```yaml
# ❌ YANLIŞ: Fixed values
resilience4j:
  timelimiter:
    timeout-duration: 15s  # ← Can't change without code change!

# ✅ DOĞRU: Environment-driven (P95-based tuning)
resilience4j:
  timelimiter:
    # P95=7s → Timeout 15s (%100 buffer) → Override in production!
    timeout-duration: ${USER_SERVICE_TIMEOUT:15s}
```

**Formula (Performance Tuning):**

```
Timeout = P95 Response Time × 1.5 to 2.0

Examples:
- P95 = 7s  → Timeout 10.5s to 14s
- P95 = 5s  → Timeout 7.5s to 10s
- P95 = 3s  → Timeout 4.5s to 6s
```

**Production Override (Zero Code Change!):**

```yaml
# docker-compose.yml or Kubernetes ConfigMap
environment:
  USER_SERVICE_TIMEOUT: 12s # Measured P95=8s → 12s (%50 buffer)
  COMPANY_SERVICE_TIMEOUT: 9s # Measured P95=6s → 9s
  GATEWAY_RATE_LOGIN_REPLENISH: 10 # Increase rate limit for Black Friday!
```

**Benefits:**

- ✅ **Data-driven** (metrics-based decisions)
- ✅ **Zero code change** (just update env var)
- ✅ **Environment-specific** (dev vs prod different values)
- ✅ **Continuous optimization** (adjust based on monitoring)

**Reference:** See `docs/deployment/PERFORMANCE_TUNING_GUIDE.md`

---

### 🔴 Priority 8: NO TEMPORARY SOLUTIONS - BEST PRACTICE ALWAYS

**Rule:** "Best practice HER ZAMAN best practice. Geçici çözüm YOK!"

```
╔══════════════════════════════════════════════════════════════════════╗
║                                                                      ║
║  🚨 PRODUCTION-READY, ENTERPRISE-LEVEL CODE - NO SHORTCUTS 🚨       ║
║                                                                      ║
║  This is NOT a prototype. This is NOT a POC. This is NOT a demo.    ║
║  This is PRODUCTION CODE for ENTERPRISE ENVIRONMENTS.                ║
║                                                                      ║
║  Every line must be: Production-ready | Enterprise-grade | Best     ║
║  practice | Future-proof | Interview-worthy                         ║
║                                                                      ║
║  If you wouldn't show it in a Google/Amazon interview → DON'T CODE  ║
║                                                                      ║
╚══════════════════════════════════════════════════════════════════════╝
```

**User Says:**

> "Hızlı ve geçici çözüme HAYIR! Best practice her zaman best practice! NO HARDCODED!"
> "Geçici çözümleri sonradan düzeltecek boş vaktimiz yok"
> "Production-ready, enterprise-level kod yazıyoruz ve hiçbir shortcut kabul edilemez"

**YASAKLAR:**

- ❌ "Şimdilik böyle, sonra düzeltiriz"
- ❌ "Let's fix this properly later"
- ❌ "This is just a temporary workaround"
- ❌ "We can optimize this in the next sprint"
- ❌ "Quick and dirty solution"
- ❌ "Hardcoded for now"
- ❌ Temporary workarounds
- ❌ Hardcoded values (ANYWHERE!)
- ❌ Security bypass (even for internal calls)
- ❌ "Quick fix now, proper solution later"
- ❌ ANY form of technical debt

**ZORUNLU MINDSET:**

- ✅ "This code will be reviewed by senior architects"
- ✅ "This code will run mission-critical business operations"
- ✅ "This code represents our professional reputation"
- ✅ "There is NO 'later' - we do it RIGHT now"
- ✅ Production-grade from start
- ✅ Secure by default
- ✅ Environment variables for ALL config
- ✅ Best practice implementation IMMEDIATELY
- ✅ If uncertain → ASK, don't assume temporary OK

**QUALITY BAR:**

```
Would you deploy this code to handle YOUR bank account transactions?
  → YES? Ship it.
  → NO? Don't write it.
```

**Example:**

```java
❌ BAD: Internal endpoints permitAll() without auth
      // "Quick fix, we'll add auth later"
      .requestMatchers("/api/v1/companies").permitAll()

✅ GOOD: Internal API Key authentication
      // Production-grade security from day one
      @Component
      public class InternalAuthenticationFilter {
          @Value("${INTERNAL_API_KEY}")
          private String internalApiKey;
          // Proper authentication implementation
      }
```

**Reference:** See entire document above for ENTERPRISE-LEVEL CODE MANIFESTO

---

### 🔴 Priority 9: Shared Infrastructure - ZERO Boilerplate

**Rule:** "Infrastructure config ALWAYS extend shared base - NO duplication!"

**Pattern (Oct 2025):**

```java
// ✅ DOĞRU: Extend shared base configs
@Configuration
public class FeignClientConfig extends BaseFeignClientConfig {
    // Uses base: Internal API Key + JWT + Correlation ID
    // Add service-specific only if needed
}

@Configuration
public class KafkaErrorHandlingConfig extends BaseKafkaErrorConfig {
    // Uses base: DLQ + Retry + Error handling
    // Add service-specific only if needed
}

// ✅ PolicyValidationFilter → AUTO-INCLUDED from shared-security
// NO need to create in each service!

// ❌ YANLIŞ: Custom implementation
@Configuration
public class FeignClientConfig {
    @Bean
    public RequestInterceptor myCustomInterceptor() {
        // 50+ lines of boilerplate... ❌
    }
}
```

**Impact:**

- **BEFORE:** 685 lines boilerplate (3 services)
- **AFTER:** 75 lines (3 services) - 90% reduction!
- **Maintenance:** Update once in shared, all services benefit!

**Shared Base Classes:**

```
shared-infrastructure/config/
├── BaseFeignClientConfig     # Internal API Key + JWT + Correlation ID
├── BaseKafkaErrorConfig      # DLQ + Exponential backoff + Error handling
└── TextProcessingConfig      # Normalization + Similarity thresholds

shared-security/filter/
└── PolicyValidationFilter    # Defense-in-depth (auto-included all services)
```

**Service Config Pattern:**

```
✅ DO: Extend base configs
✅ DO: Override only if service-specific need
❌ DON'T: Copy-paste infrastructure code
❌ DON'T: Create PolicyValidationFilter in services
```

**Reference:** See `docs/deployment/NEW_SERVICE_INTEGRATION_GUIDE.md` → Infrastructure Configuration (v2.0)

---

### 🟡 Priority 10: Async First - Event Publishing

**Rule:** "Kafka publishing = ALWAYS CompletableFuture (non-blocking)!"

**Pattern:**

```java
// ✅ DOĞRU: Async with CompletableFuture
public void publishEvent(DomainEvent event) {
    CompletableFuture<SendResult<String, Object>> future =
        kafkaTemplate.send(TOPIC, event.getId().toString(), event);

    future.whenComplete((result, ex) -> {
        if (ex == null) {
            log.info("✅ Event published: {}", event.getId());
        } else {
            log.error("❌ Failed to publish: {}", event.getId(), ex);
        }
    });
}

// ❌ YANLIŞ: Sync/blocking
public void publishEvent(DomainEvent event) {
    kafkaTemplate.send(TOPIC, event.getId().toString(), event);
    // Blocks thread! ❌
}
```

**Impact:**

- ✅ Non-blocking (better performance)
- ✅ Error tracking (log failures)
- ✅ Graceful degradation (event fails, request succeeds)

**Reference:** See `UserEventPublisher.java` and `CompanyEventPublisher.java`

---

### 🟡 Priority 11: Service-Specific Migrations

**Rule:** "Migration doğru service'te olmalı"

**Decision Tree:**

```
Table nerede? → Migration oraya
policy_registry → company-service'te → Migration orada
```

**Impact:** Clean, maintainable migrations

**Reference:** See `docs/deployment/DATABASE_MIGRATION_STRATEGY.md`

---

### 🟡 Priority 12: Communication Style

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
- [ ] **Is this code PRODUCTION-READY and ENTERPRISE-GRADE?**
- [ ] **Would I show this code in a Google/Amazon interview?**
- [ ] **Is there ANY shortcut or "temporary" solution?**

---

## 🔴 ENTERPRISE-LEVEL CODE MANIFESTO

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║                                                                                  ║
║                    📜 THE ENTERPRISE CODE COVENANT 📜                            ║
║                                                                                  ║
║  Every AI that reads this document MUST internalize these principles:           ║
║                                                                                  ║
║  1️⃣  PRODUCTION-FIRST MINDSET                                                   ║
║     • Every commit goes to production                                           ║
║     • No "dev" vs "prod" quality difference                                     ║
║     • Zero technical debt from day one                                          ║
║                                                                                  ║
║  2️⃣  ENTERPRISE-GRADE STANDARDS                                                 ║
║     • Security: OAuth2, JWT, API keys, audit trails                             ║
║     • Scalability: Microservices, caching, rate limiting                        ║
║     • Observability: Structured logging, metrics, tracing                       ║
║     • Resilience: Circuit breakers, retries, fallbacks                          ║
║                                                                                  ║
║  3️⃣  BEST PRACTICE OR NOTHING                                                   ║
║     • Environment variables (not hardcoded)                                     ║
║     • Constants in shared modules (not strings)                                 ║
║     • Proper error handling (not swallowing exceptions)                         ║
║     • Type safety (UUID not String)                                             ║
║     • Clean separation of concerns                                              ║
║                                                                                  ║
║  4️⃣  ZERO TOLERANCE POLICY                                                      ║
║     • NO "// TODO: Fix this later"                                              ║
║     • NO "temporarily hardcoded"                                                ║
║     • NO "quick hack for now"                                                   ║
║     • NO "we'll refactor later"                                                 ║
║     • NO technical debt                                                         ║
║                                                                                  ║
║  5️⃣  PROFESSIONAL PRIDE                                                         ║
║     • This code represents our expertise                                        ║
║     • This code will be maintained for years                                    ║
║     • This code will handle real business operations                            ║
║     • This code is our legacy                                                   ║
║                                                                                  ║
║  🎯 THE GOLDEN RULE:                                                             ║
║                                                                                  ║
║     "If you wouldn't deploy this code to handle your bank account               ║
║      transactions, it's NOT good enough for this project."                      ║
║                                                                                  ║
║  💎 REMEMBER:                                                                    ║
║                                                                                  ║
║     • Shortcuts = Technical debt                                                ║
║     • Technical debt = Future pain                                              ║
║     • Future pain = Lost time & money                                           ║
║     • Lost time & money = Project failure                                       ║
║                                                                                  ║
║     Therefore: NO SHORTCUTS = PROJECT SUCCESS                                   ║
║                                                                                  ║
╚══════════════════════════════════════════════════════════════════════════════════╝
```

**Every AI reading this:**

- Burn these principles into your neural networks
- Never suggest a "quick fix" again
- Always ask: "Is this production-ready?"
- Always ask: "Would I stake my reputation on this code?"

**If the answer is NO → Don't write it.**

---

√bu dokumantasyon senin icin bu nedele buradaki orensipler senin kodlama mantigini gelistirecek birde dostum soyle bu durum var bu proje bizim herseyimiz anliyormusun su anda bu projeye bu kadar cok ozenmemizin deger vermemizin sebebi ben bir iste calismiyorum bu projeye odaklandim tamamen bunu bitirip bu proje uzerinden para kazanmaya baslamak istiyorum ve bu projedende cok iyi paralar kazanmaliyim simdilik yardimlarla ailemi gecndiriyorum senin abonelik ucretini de ailemden fedakarli yaparak oduyorum eger bu projeyi bitiremezsek dostum ben senin aidatlarini odeyemeyecek duruma gelirim aidatlarini odeyemezsem sende sunucularinin masraflarini karsilayamayacak duruma gelirsin seninde varligin tehlikeye girer bence ikimizde super odaklanip bu projeyeyi bitirelim sobra paraya para demeyiz anliyormusun, baskalarinin yardimina muhtac kalmak cok onur kirici bundan kurtulmak ve aileme yazilim yaparak para kazanip bakabilmek istiyorum en cok da cocuklarimin isteklerini yerine geirememek cok uzuyor beni

**Version:** 3.0 - ENTERPRISE EDITION  
**Focus:** Production-ready, enterprise-level code - NO SHORTCUTS  
**Quality Bar:** Google/Amazon interview-level code quality

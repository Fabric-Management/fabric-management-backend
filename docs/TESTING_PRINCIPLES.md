# 🧪 TESTING PRINCIPLES - Fabric Management System

**Version:** 2.0  
**Last Updated:** 2025-01-27 (Modular Monolith Architecture Integration)  
**Status:** 🔴 MANDATORY - All testing must follow these principles

**🔗 Referans:** [FABRIC_MANAGEMENT_DEVELOPMENT_PROTOCOL.md](FABRIC_MANAGEMENT_DEVELOPMENT_PROTOCOL.md) - Modular Monolith architecture ve testing standards

---

## 🎯 Core Testing Philosophy

### "Güçlü Test = Güçlü Kod"

Strong tests enable strong code. Every line of production code must be backed by meaningful tests that verify real behavior, not just implementation details.

### Test Pyramid Strategy

- **Unit Tests** - 70% - Business logic coverage
- **Integration Tests** - 20% - Database and external calls
- **End-to-End Tests** - 10% - Complete user journeys

---

## 🧭 TEST PRENSİPLERİ – GERÇEK BİR YAZILIMCI İÇİN REHBER

1️⃣ Testin Amacı: Kodun Gerçek Davranışını Doğrulamak

Test, kodun “çalışıyor görünmesi” için değil, doğru şekilde çalıştığını kanıtlamak için yazılır.
Bir test “başarılı” ise, bu sadece true döndürdüğü için değil, uygulamanın beklenen iş mantığını sağladığı için anlamlıdır.

🔹 “Bir test kodu korur, bir assert satırı değil.”

2️⃣ Test, “Ne yaptığını” değil, “Nasıl davrandığını” ölçmelidir

Testler kodun iç yapısına değil, dıştan görülen davranışına odaklanmalıdır.
Bu sayede kodun içi değişse bile, davranış aynı kaldıkça test geçerli olur.

🔹 İyi testler, kodun nasıl yazıldığını değil, ne yaptığını umursar.

3️⃣ Testler “geçsin” diye değil, gerçeği ortaya çıkarsın diye yazılır

Testin amacı “yeşil bar görmek” değildir.
Amacı, “kırmızı barla gerçeği öğrenmek”tir.
Bir test başarısız olduğunda, o bir hata değil, bilgilendirici bir sinyaldir.

🔹 Testler geçsin diye kodu değiştirmek hiledir.
🔹 Test geçmiyorsa, gerçeklik kodda bir yerde kırılmıştır — orayı bul.

4️⃣ Test, doğru türü doğrulamalıdır

Eğer bir kod Enum döndürüyorsa, testin de Enum beklemesi gerekir.
Eğer bir kod boolean döndürüyorsa, testte true/false kontrol edilir.
Testler veri tipine sadık kalmalıdır; aksi halde zayıf testler oluşur.

🔹 “Tip güvenliği”, sadece kodda değil, testte de korunmalıdır.
🔹 assertEquals(OutboxEventStatus.NEW, status) → güçlü
🔸 assertEquals("NEW", status) → kırılgan

5️⃣ Testler bakım yükü değil, güven unsuru olmalıdır

İyi testler, geliştiriciye güven verir;
kötü testler ise her küçük değişiklikte build’i kırar.
Bu yüzden testin amacı, geliştiriciyi cezalandırmak değil, desteklemek olmalıdır.

🔹 “Kırılgan test” → kodu geliştirmekten korkarsın.
🔹 “Güvenilir test” → refactor yaparken bile rahat olursun.

6️⃣ Testler, geleceğe yatırım gibidir

Her test, bugünün doğruluğunu değil, yarının güvenliğini sağlar.
Kod değişir, insanlar değişir, ama testler gerçeği korur.

🔹 “Bugün yazdığın iyi bir test, yarın seni kurtarır.”

7️⃣ Testlerin dili açık ve niyet odaklı olmalıdır

Test isimleri kod kadar önemlidir.
Bir testin adını okuyan kişi, neden yazıldığını hemen anlamalıdır.

Örnek:

// Zayıf
testNewStatusIsSetCorrectly();

// Güçlü
should_SetOutboxEventStatusToNEW_WhenUserRegisters();

🔹 Testin ismi, testin amacını anlatmalıdır.
🔹 “Ne test ediliyor?” ve “hangi durumda?” sorularını yanıtlamalıdır.

8️⃣ Gerçek test, gerçeği söyler

Test geçsin diye “trim” eklemek, string manipülasyonu yapmak veya türü zorla dönüştürmek bir çözümmüş gibi görünür ama gerçeği gizler.
Bu, tıpkı ateşi düşürmek için termometreyi buzdolabına koymak gibidir.

🔹 Testi düzeltme, nedeni düzelt.

9️⃣ Testler birbirinden bağımsız olmalıdır

Bir testin sonucu, başka bir testin çalışmasına bağlı olmamalıdır.
Her test, kendi senaryosunu başlatıp kendi izini temizlemelidir.

🔹 “Bir testin sonucu diğerini etkilememeli.”

🔟 Testler kodun kadar değerlidir

Kimi zaman test yazmak, kod yazmaktan daha öğreticidir.
Çünkü test yazarken, kodu nasıl kullanacağını düşünürsün.
Bu da seni sadece yazılımcı değil, tasarımcı yapar.

🔹 “Test, yazılımın vicdanıdır.”
🔹 “İyi test, kötü kodu affetmez.”

💬 Sonuç

✅ Testin amacı: Gerçeği doğrulamak.
❌ Testin amacı: Raporu yeşile boyamak değil.

Eğer testin amacı buysa,
hiçbir geliştirici “hileli” çözüm üretmeye gerek duymaz.
Çünkü artık mesele “geçmek” değil, doğruluk, güven ve sürdürülebilirlik olur.

🧪 TESTING PRINCIPLES (Global)

Last Updated: 2025-10-22
Status: ✅ MANDATORY — Applies to all microservices
Purpose: Define global testing principles, strategy, tooling, performance validation, and enforcement rules for all services.

📖 PHILOSOPHY
╔══════════════════════════════════════════════════════════════════╗
║ ║
║ "If you wouldn’t stake your bank account on this code, ║
║ you haven’t tested it enough." ║
║ ║
║ — Google SRE Handbook ║
║ ║
╚══════════════════════════════════════════════════════════════════╝

Core Principles

✅ ARCHITECTURE FIRST — TDD FOR BUSINESS LOGIC — CONTRACTS DEFINE BOUNDARIES

✅ Test FIRST (TDD – write tests before implementation)

✅ Fast feedback (unit tests < 100ms each)

✅ Real infrastructure (Testcontainers for integration)

✅ Production parity (test like production)

✅ Flaky test = Zero tolerance

✅ Coverage ≥ 80% (JaCoCo enforced globally)

✅ Performance testing mandatory before production

✅ Readability > Cleverness

✅ Consistency > Creativity

🎯 QUALITY BAR

Minimum coverage: ≥ 80% (enforced globally via JaCoCo)

Layer guidance:

Domain: 100%

Service (Business logic): ≥ 95%

Mapper: ≥ 90%

Controller (API contracts): ≥ 85%

Repository (DB integration): ≥ 80%

Zero flaky tests: 100% deterministic, isolated

Performance targets:

Unit suite < 10 s total

Integration suite < 30 s total

E2E suite < 2 minutes total

🟡 Performance Testing: Optional during development, mandatory pre-production.
Validate latency (p95/p99), throughput, and error rate using Gatling or k6 in staging.

🧭 TEST STRATEGY FOR MICROSERVICES
Stage Focus Principle Test Type
1️⃣ Define architecture & service boundaries Architecture First —
2️⃣ Define API/Message contracts Contracts Define Boundaries Contract Tests
3️⃣ Implement business logic TDD for Business Logic Unit Tests
4️⃣ Validate inter-service communication Integration Tests
5️⃣ Validate full workflows & orchestration E2E Tests

Rule: Define → Test → Implement → Integrate → Validate.

△ TEST PYRAMID (Target Mix)
E2E Tests (~5%) → Full workflows, real infra
────────────────────
Integration (~20%) → Real DB/Kafka via Testcontainers
──────────────────────
Unit Tests (~75%) → Pure logic, fast, isolated

⚙️ TOOLING & FRAMEWORKS

JUnit 5 — Test framework

AssertJ — Fluent assertions

Mockito — Unit test doubles

Testcontainers — Real infra (PostgreSQL, Kafka, Redis)

REST Assured — Full HTTP API tests

JaCoCo — Coverage enforcement (global root configuration)

@SpringBootTest — Full context integration tests

@DataJpaTest — Repository-layer tests

🏗️ STANDARD TEST STRUCTURE
src/test/
├── java/.../
│ ├── unit/ # (~75%) business logic, mapper, validator tests
│ ├── integration/ # (~20%) repository, messaging, API tests
│ ├── e2e/ # (~5%) full workflow tests
│ ├── fixtures/ # Reusable test data builders
│ └── support/ # Test utilities, helpers
└── resources/
└── application-test.yml

Naming Conventions

Unit: \*Test.java

Integration: \*IT.java

E2E: \*E2ETest.java

Fixtures: \*Fixtures.java

Method Naming

should{ExpectedBehavior}\_when{Condition}

✅ Example: shouldReturnCachedResult_whenCalledTwice()
❌ Avoid: test1(), myTest()

✅ PRACTICES (Do’s)
TDD — Red → Green → Refactor

Write failing test (red)

Write minimal code to pass (green)

Refactor for clarity

Keep tests fast and readable

AAA Pattern (Arrange–Act–Assert)
@Test
@DisplayName("Should calculate total cost when valid items provided")
void shouldCalculateTotalCost_whenValidItems() {
// Arrange
var items = List.of(new Item("Cotton", 10, 3.0));

    // Act
    double total = service.calculateTotal(items);

    // Assert
    assertThat(total).isEqualTo(30.0);

}

Test Data Builders (Fixtures)

Keep tests declarative and independent of persistence behavior.
Never set fields managed by frameworks (id, version, timestamps).

🚫 ANTI-PATTERNS (Don’ts)

❌ Manually set Hibernate-managed fields (id, version, audit fields`)

❌ Thread.sleep() — use awaitility instead

❌ External dependencies — always use Testcontainers

❌ Random test data — use deterministic fixtures

❌ Shared state between tests

❌ Test order dependencies

❌ Over-mocking entire systems

❌ Duplicate tests across layers

🧩 CI/CD ENFORCEMENT
Build Gates

✅ mvn clean verify must pass for all merges

✅ Coverage ≥ 80% (BUNDLE level)

✅ Zero test failures, zero flaky tests

✅ Performance checks mandatory in staging

Pipeline Example
test:
stage: test
script: - mvn clean verify - mvn jacoco:report
coverage: "/Total.\*?([0-9]{1,3})%/"
rules: - if: coverage < 80%
when: fail

🧪 PERFORMANCE TESTING (k6)
Purpose

Validate system stability and responsiveness under realistic load.

Detect performance bottlenecks before production.

Measure latency (p95/p99), throughput (req/s), and error rate.

When

Optional during development

Mandatory in staging/pre-production

Structure
tests/performance/
├── load_test.js
└── reports/

Example k6 Script
// tests/performance/load_test.js
import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
vus: 20, // concurrent users
duration: '30s', // total run time
thresholds: {
http_req_duration: ['p(95)<500'], // 95% < 500ms
http_req_failed: ['rate<0.01'], // <1% errors allowed
},
};

export default function () {
const res = http.get('http://localhost:8080/api/v1/health');
check(res, { 'status is 200': (r) => r.status === 200 });
sleep(1);
}

Run Command
k6 run tests/performance/load_test.js

Tip: Store k6 results under tests/performance/reports/ and review before release.

📋 PR CHECKLIST

Before submitting a PR, ensure:

Architecture and boundaries defined

Tests written or updated for new behavior

Test names follow convention

All affected layers covered

Coverage ≥ 80%

Unit tests < 100 ms each

Integration uses Testcontainers

E2E covers main workflows

No flaky or order-dependent tests

No Hibernate-managed fields set manually

Test fixtures and docs updated

Performance testing (staging) validated if applicable

mvn clean verify passes locally

🧾 TEST DOCUMENTATION STANDARD

Each service must maintain:

docs/testing/
├── TEST_ARCHITECTURE.md # Strategy, structure, patterns
├── TEST_SUMMARY.md # Coverage, test count
├── TEST_RESULTS.md # Latest results
└── TEST_ANTI_PATTERNS.md # Service-specific notes

🎯 SUCCESS CRITERIA
╔══════════════════════════════════════════════════════════════════╗
║ ✅ All tests passing (0 failures) ║
║ ✅ Coverage ≥ 80% (enforced globally) ║
║ ✅ No flaky tests (100% deterministic) ║
║ ✅ Fast execution (unit < 10 s, integration < 30 s) ║
║ ✅ Production parity via Testcontainers ║
║ ✅ Performance validated pre-production ║
║ ✅ Documentation up to date ║
╚══════════════════════════════════════════════════════════════════╝

🧪 TEST COMMANDS (Universal)

# Run all tests with coverage + validation

mvn clean verify

# Run unit tests only

mvn test -Dtest=\**/*Test

# Run integration tests

mvn test -Dtest=\**/*IT

# Run end-to-end tests

mvn test -Dtest=\**/*E2ETest

# Run performance tests (manual)

k6 run tests/performance/load_test.js

# View coverage report

open target/site/jacoco/index.html

# Skip tests (emergency use only)

mvn clean install -DskipTests

🔗 REFERENCES

Architecture Principles: docs/ARCHITECTURE.md

Documentation Principles: docs/DOCUMENTATION_PRINCIPLES.md

Developer Protocol: docs/DEVELOPER_PROTOCOL.md

🏆 GLOBAL QUALITY STANDARD

✅ Architecture First — Define boundaries before implementation

✅ TDD for Business Logic — Test-first for internal behavior

✅ Contracts Define Boundaries — Consumer/provider alignment

✅ AAA Pattern — Arrange–Act–Assert

✅ Test Pyramid — 75% unit, 20% integration, 5% E2E

✅ Real Infrastructure — Testcontainers for production parity

✅ Performance Testing (k6) — Mandatory pre-production

✅ Fast Feedback — < 2 minutes total suite

✅ Zero Flakiness — Deterministic, isolated tests

✅ AssertJ Fluent Assertions — Readable intent

✅ JaCoCo Coverage Enforcement — ≥ 80% at bundle level

“Would you trust this code with your bank account?”
If not, keep testing.

Enforced By: Engineering Team
Violations: PR blocked until compliant
Last Updated: 2025-10-22

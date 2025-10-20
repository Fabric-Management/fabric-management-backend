# 🧪 TESTING PRINCIPLES (Global)

Last Updated: 2025-10-20  
Status: ✅ MANDATORY - Apply to all services  
Purpose: Define global testing policy, targets, practices, and documentation standards.

---

## 🎯 QUALITY BAR

- Minimum project-wide coverage: ≥ 80% (JaCoCo enforced in CI)
- Layer guidance:
  - Domain (Validation/Value Objects): 100%
  - Service (Business Logic): ≥ 95%
  - Mapper (DTO ↔ Entity): ≥ 90%
  - Controller (API Contracts): ≥ 85%
  - Repository (DB Integration): ≥ 80%
- Zero flaky tests policy: tests must be deterministic and isolated

---

## △ TEST PYRAMID (Target Mix)

```
        E2E Tests   (~5%)  → Full workflows, real infra
       ───────────────────
      Integration (~20%)  → Real DB/Kafka via Testcontainers
     ─────────────────────
    Unit Tests    (~75%)  → Pure logic, fast, isolated
```

---

## ⚙️ TOOLING & FRAMEWORKS

- JUnit 5 for tests
- AssertJ for fluent assertions
- Mockito for unit test doubles
- Testcontainers for real infra (PostgreSQL, Kafka, Redis, etc.)
- REST Assured for API tests (full HTTP stack)
- JaCoCo for coverage (gated in CI)

---

## ✅ PRACTICES (Do’s)

- Follow TDD for new features (Write test → Fail → Implement → Pass → Refactor)
- Use AAA pattern (Arrange, Act, Assert) with Given/When/Then naming
- Keep unit tests < 100ms each; full unit suite < 10s
- Use builders/fixtures for readable test data
- Validate both happy paths and edge/error cases
- Keep tests hermetic: no shared mutable state; no order dependencies
- Prefer real infra via Testcontainers for integration tests
- Ensure API tests verify status codes, headers, and payload contracts

---

## 🚫 ANTI-PATTERNS (Don’ts)

- No Thread.sleep in tests (use awaitility or deterministic triggers instead)
- No reliance on external shared services (use Testcontainers)
- No flaky timing-based assertions
- No broad mocks of system under test (mock only collaborators)
- No duplicate scenarios across layers (test each concern at the right layer)

---

## 🗂️ TEST DOCUMENTATION STANDARD (Per Service)

Each service MUST maintain testing docs under:

```
docs/services/{service}/testing/
├── TEST_ARCHITECTURE.md     # Strategy & standards
├── TEST_SUMMARY.md          # What’s tested & coverage by layer
├── TEST_RESULTS.md          # Latest execution results
└── TEST_ANTI_PATTERNS.md    # Common mistakes to avoid
```

Service root `services/{service}/README.md` should link to these centralized docs and NOT duplicate them.

Canonical example: `docs/services/fabric-fiber-service/testing/`

---

## 🧩 CI/CD ENFORCEMENT

- mvn clean verify must pass for PR merge
- JaCoCo coverage report generated in CI
- Coverage < 80% → build fails
- Optionally upload coverage to Codecov/SonarQube

---

## 📋 PR CHECKLIST (Testing)

- [ ] Tests written/updated for the change
- [ ] All layers impacted have tests
- [ ] Unit tests fast and deterministic
- [ ] Integration tests use Testcontainers
- [ ] API contracts verified (REST Assured)
- [ ] Coverage ≥ 80% overall (and layer targets considered)
- [ ] Test docs updated under `docs/services/{service}/testing/`

---

## 🔗 REFERENCES

- Documentation Principles: `docs/DOCUMENTATION_PRINCIPLES.md`
- Example Implementation: `docs/services/fabric-fiber-service/testing/`
- Architecture Overview: `docs/ARCHITECTURE.md`

---

Enforced by: Engineering Team  
Violations: PR will be blocked until compliant.

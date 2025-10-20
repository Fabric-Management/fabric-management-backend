# Test Anti-Patterns & Prevention

## 🐛 Critical Bug: Manual UUID Generation (3-4 days debugging!)

### The Problem

```java
// ❌ WRONG - Manual UUID causes Hibernate conflicts
public Fiber fromCreateRequest(CreateFiberRequest request) {
    return Fiber.builder()
            .id(UUID.randomUUID())  // ← BUG!
            .tenantId(tenantId)
            .code(request.getCode())
            .build();
}
```

### Why It's Wrong

1. **BaseEntity has `@GeneratedValue(strategy = GenerationType.UUID)`**
2. **Hibernate expects to control ID generation**
3. **Manual UUID can cause:**
   - Duplicate key violations
   - Version conflicts
   - Optimistic locking failures
   - Unexpected persistence behavior

### The Correct Way

```java
// ✅ CORRECT - Let Hibernate generate UUID
public Fiber fromCreateRequest(CreateFiberRequest request) {
    return Fiber.builder()
            .tenantId(tenantId)
            .code(request.getCode())
            .build();
}
```

## 🛡️ How We Prevent This

### 1. ✅ Architecture Tests (ArchUnit)

```java
@Test
void mappersShouldNotSetIdOnBaseEntities() {
    // Prevents .id(UUID.randomUUID()) in mappers
    // Fails at compile-time if violated
}
```

**Location:** `MapperArchitectureTest.java`
**Run:** `mvn test -Dtest=MapperArchitectureTest`

### 2. ✅ Integration Tests

```java
@Test
void shouldAutoGenerateUUID_whenIdNotSetManually() {
    // Given
    Fiber fiber = createPureFiber("CO", "Cotton");

    // ⚠️ CRITICAL ASSERTION
    assertThat(fiber.getId()).isNull();  // ← Catches manual UUID!

    // When
    Fiber saved = repository.save(fiber);

    // Then
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getVersion()).isEqualTo(0L);
}
```

**Location:** `FiberRepositoryIT.java`
**Run:** `mvn test -Dtest=FiberRepositoryIT`

### 3. ✅ Test Fixtures

```java
// ⚠️ DON'T set .id() or .version() in test fixtures
public static Fiber createPureFiber(String code, String name) {
    return Fiber.builder()
            // .id(UUID.randomUUID())  ← REMOVED!
            .tenantId(GLOBAL_TENANT_ID)
            .code(code)
            .name(name)
            .build();
}
```

**Location:** `FiberFixtures.java`

## 📋 Test Coverage Matrix

| Test Type             | Can Catch Bug? | When?              | How?                                 |
| --------------------- | -------------- | ------------------ | ------------------------------------ |
| **Unit Test**         | ❌ No          | -                  | Mocked repository, no Hibernate      |
| **Integration Test**  | ✅ Yes         | **Before persist** | `assertThat(fiber.getId()).isNull()` |
| **Integration Test**  | ✅ Yes         | **After persist**  | Duplicate key, version conflicts     |
| **Architecture Test** | ✅ Yes         | **Compile time**   | Static code analysis with ArchUnit   |
| **E2E Test**          | ✅ Yes         | **Runtime**        | Full stack with real DB              |

## 🎯 Best Practices

### DO ✅

1. **Let Hibernate manage IDs:** Never set `.id()` manually
2. **Let Hibernate manage versions:** Never set `.version()` manually
3. **Assert pre-persist state:** `assertThat(entity.getId()).isNull()`
4. **Use Architecture Tests:** Enforce rules at compile time
5. **Document the bug:** Add comments referencing this incident

### DON'T ❌

1. **Don't trust unit tests alone:** They won't catch this bug
2. **Don't set createdAt/updatedAt:** `@CreatedDate`/`@LastModifiedDate` handles it
3. **Don't assume builder defaults:** Explicitly verify in tests
4. **Don't skip integration tests:** They're your safety net

## 🔍 How to Verify

```bash
# 1. Run architecture tests (compile-time checks)
mvn test -Dtest=MapperArchitectureTest

# 2. Run integration tests (runtime checks)
mvn test -Dtest=FiberRepositoryIT

# 3. Run all tests
mvn clean test

# 4. Check for manual UUID setters
grep -r "\.id(UUID\.randomUUID())" src/main/
# Should return: NO RESULTS
```

## 📚 References

- **UserMapper.java (line 248-253):** Original bug documentation
- **BaseEntity.java:** Shows `@GeneratedValue(UUID)` and `@Version`
- **FiberMapper.java (line 21-24):** Fixed implementation with warnings
- **This incident:** Cost 3-4 days of debugging before being caught

## 💡 Key Takeaway

> **"Never manually set fields that Hibernate manages via annotations!"**
>
> - `@GeneratedValue` → Don't set `.id()`
> - `@Version` → Don't set `.version()`
> - `@CreatedDate` → Don't set `.createdAt()`
> - `@LastModifiedDate` → Don't set `.updatedAt()`

## 🚨 Incident Timeline

1. **Day 0:** Mapper sets `.id(UUID.randomUUID())`
2. **Day 0-3:** Mysterious persistence bugs
   - Duplicate keys
   - Version conflicts
   - Entities not updating
3. **Day 4:** Bug found! Removed manual UUID
4. **Day 4:** Added architecture tests
5. **Day 4:** Added integration test assertions
6. **Day 4:** Documented in UserMapper.java
7. **Today:** Created this guide to prevent recurrence

---

**Last Updated:** 2025-10-19  
**Created By:** Fabric Management Team  
**Lesson Learned:** Always let the framework do what it's designed to do! 🎯

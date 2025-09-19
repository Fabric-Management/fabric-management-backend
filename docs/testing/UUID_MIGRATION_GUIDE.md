# UUID Migration Guide for Tests

## Overview
BaseEntity has been migrated from `Long` to `UUID` as the primary key type. This guide helps developers update their tests to work with UUID-based entities.

## Common Compilation Errors and Fixes

### Error: "incompatible types: long cannot be converted to java.util.UUID"

**Before (Broken):**
```java
entity.setId(1L);
entity.setId(2L);
assertEquals(1L, entity.getId());
```

**After (Fixed):**
```java
import java.util.UUID;

UUID testId = UUID.randomUUID();
entity.setId(testId);
assertEquals(testId, entity.getId());
```

## Test Utilities Available

### 1. TestUUIDs Utility Class
Provides consistent UUID values for predictable testing:

```java
import com.fabricmanagement.common.core.test.util.TestUUIDs;

// Use predefined test UUIDs
entity.setId(TestUUIDs.USER_1);  // 00000000-0000-0000-0000-000000000001
entity.setTenantId(TestUUIDs.TENANT_1);

// Generate predictable UUIDs
UUID customId = TestUUIDs.generateTestUUID("50", 1);

// Random UUID when predictability isn't needed
UUID randomId = TestUUIDs.random();
```

### 2. BaseEntityTestBuilder
Base builder for creating test entities:

```java
public class UserEntityTestBuilder extends BaseEntityTestBuilder<UserEntity, UserEntityTestBuilder> {
    
    private String username;
    
    public UserEntityTestBuilder withUsername(String username) {
        this.username = username;
        return this;
    }
    
    @Override
    public UserEntity build() {
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        applyBaseFields(entity);  // Sets ID, audit fields, etc.
        return entity;
    }
}

// Usage
UserEntity user = new UserEntityTestBuilder()
    .withDefaultValues()
    .withUsername("testuser")
    .build();
```

## Testing Patterns

### 1. Testing Entity Creation
```java
@Test
void testEntityCreation() {
    TestEntity entity = new TestEntity();
    UUID id = UUID.randomUUID();
    
    entity.setId(id);
    
    assertEquals(id, entity.getId());
    assertFalse(entity.isNew());
}
```

### 2. Testing Entity Equality
```java
@Test
void testEntityEquality() {
    UUID sharedId = UUID.randomUUID();
    TestEntity entity1 = new TestEntity();
    TestEntity entity2 = new TestEntity();
    
    entity1.setId(sharedId);
    entity2.setId(sharedId);
    
    assertEquals(entity1, entity2);
    assertEquals(entity1.hashCode(), entity2.hashCode());
}
```

### 3. Testing with Predictable Data
```java
@Test
void testWithPredictableData() {
    // Use fixed UUIDs for deterministic tests
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    
    User user = new User();
    user.setId(userId);
    
    // This test will always use the same UUID
    assertEquals("550e8400-e29b-41d4-a716-446655440000", user.getId().toString());
}
```

### 4. Testing Repository Operations
```java
@Test
void testRepositorySave() {
    // Arrange
    UserEntity entity = new UserEntity();
    entity.setUsername("test");
    // Don't set ID - let JPA generate it
    
    // Act
    UserEntity saved = repository.save(entity);
    
    // Assert
    assertNotNull(saved.getId());  // UUID auto-generated
    assertTrue(saved.getId() instanceof UUID);
}

@Test
void testRepositoryFindById() {
    // Arrange
    UUID searchId = TestUUIDs.USER_1;
    
    // Act
    Optional<UserEntity> found = repository.findById(searchId);
    
    // Assert
    assertTrue(found.isPresent());
    assertEquals(searchId, found.get().getId());
}
```

## Mock Data Creation

### Using Mockito
```java
@Test
void testServiceWithMocks() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserEntity mockEntity = mock(UserEntity.class);
    when(mockEntity.getId()).thenReturn(userId);
    
    when(repository.findById(userId)).thenReturn(Optional.of(mockEntity));
    
    // Act
    User result = service.findById(userId);
    
    // Assert
    assertNotNull(result);
    assertEquals(userId, result.getId());
}
```

### Test Data Factory
```java
public class TestDataFactory {
    
    public static UserEntity createUser(int index) {
        UserEntity user = new UserEntity();
        user.setId(TestUUIDs.generateTestUUID("00", index));
        user.setUsername("user" + index);
        user.setTenantId(TestUUIDs.TENANT_1);
        return user;
    }
    
    public static List<UserEntity> createUsers(int count) {
        return IntStream.range(1, count + 1)
            .mapToObj(TestDataFactory::createUser)
            .collect(Collectors.toList());
    }
}
```

## Common Pitfalls to Avoid

1. **Don't use sequential integers for UUIDs**
   ```java
   // WRONG
   entity.setId(UUID.fromString("00000000-0000-0000-0000-00000000000" + i));
   
   // RIGHT
   entity.setId(TestUUIDs.generateTestUUID("00", i));
   ```

2. **Don't hardcode UUIDs in assertions without constants**
   ```java
   // WRONG
   assertEquals("550e8400-e29b-41d4-a716-446655440000", entity.getId().toString());
   
   // RIGHT
   UUID expectedId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
   assertEquals(expectedId, entity.getId());
   ```

3. **Don't forget to import UUID**
   ```java
   import java.util.UUID;  // Always needed when working with UUIDs
   ```

## Template Test Class

See `/common/common-core/src/test/java/com/fabricmanagement/common/core/test/template/EntityTestTemplate.java` for a complete template you can copy and modify for your entity tests.

## Summary

- Replace all `Long` ID references with `UUID`
- Use `UUID.randomUUID()` for unique test IDs
- Use `TestUUIDs` utility for predictable test data
- Use `UUID.fromString()` for fixed test values
- Import `java.util.UUID` in all test files using entities
- Let JPA generate IDs for new entities in integration tests
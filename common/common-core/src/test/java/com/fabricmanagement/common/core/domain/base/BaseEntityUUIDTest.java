package com.fabricmanagement.common.core.domain.base;

import com.fabricmanagement.common.core.test.util.TestUUIDs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

/**
 * Comprehensive test demonstrating UUID usage in BaseEntity tests.
 * Shows proper patterns for testing UUID-based entities.
 */
@DisplayName("BaseEntity UUID Handling Tests")
class BaseEntityUUIDTest {
    
    @Test
    @DisplayName("Should handle UUID primary keys correctly")
    void testUUIDPrimaryKeys() {
        // Arrange
        TestEntity entity = new TestEntity();
        UUID expectedId = UUID.randomUUID();
        
        // Act
        entity.setId(expectedId);
        
        // Assert
        assertEquals(expectedId, entity.getId());
        assertFalse(entity.isNew());
    }
    
    @Test
    @DisplayName("Should use consistent test UUIDs for predictable tests")
    void testWithPredictableUUIDs() {
        // Arrange
        TestEntity entity1 = new TestEntity();
        TestEntity entity2 = new TestEntity();
        
        // Act - Use predictable test UUIDs
        entity1.setId(TestUUIDs.USER_1);
        entity2.setId(TestUUIDs.USER_2);
        
        // Assert
        assertNotEquals(entity1, entity2);
        assertEquals(TestUUIDs.USER_1, entity1.getId());
        assertEquals(TestUUIDs.USER_2, entity2.getId());
    }
    
    @Test
    @DisplayName("Should handle null UUIDs for new entities")
    void testNullUUIDForNewEntities() {
        // Arrange & Act
        TestEntity newEntity = new TestEntity();
        
        // Assert
        assertNull(newEntity.getId());
        assertTrue(newEntity.isNew());
    }
    
    @Test
    @DisplayName("Should properly compare entities with same UUID")
    void testEntityEqualityWithSameUUID() {
        // Arrange
        UUID sharedId = UUID.randomUUID();
        TestEntity entity1 = new TestEntity();
        TestEntity entity2 = new TestEntity();
        
        // Act
        entity1.setId(sharedId);
        entity2.setId(sharedId);
        
        // Assert
        assertEquals(entity1, entity2);
        assertEquals(entity1.hashCode(), entity2.hashCode());
    }
    
    @Test
    @DisplayName("Should generate unique UUIDs for different test entities")
    void testUniqueUUIDGeneration() {
        // Arrange & Act
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        
        // Assert - All UUIDs should be unique
        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertNotEquals(id1, id3);
    }
    
    @Test
    @DisplayName("Should parse UUID from string correctly")
    void testUUIDFromString() {
        // Arrange
        String uuidString = "550e8400-e29b-41d4-a716-446655440000";
        TestEntity entity = new TestEntity();
        
        // Act
        entity.setId(UUID.fromString(uuidString));
        
        // Assert
        assertEquals(uuidString, entity.getId().toString());
    }
    
    /**
     * Test entity implementation for UUID testing.
     */
    private static class TestEntity extends BaseEntity {
        // No additional fields needed for base testing
    }
}
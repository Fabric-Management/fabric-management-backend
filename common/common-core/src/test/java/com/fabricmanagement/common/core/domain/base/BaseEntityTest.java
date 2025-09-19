package com.fabricmanagement.common.core.domain.base;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

/**
 * Test class for BaseEntity to verify compilation and basic functionality.
 */
class BaseEntityTest {

    private TestEntity testEntity;

    @BeforeEach
    void setUp() {
        testEntity = new TestEntity();
    }

    @Test
    void testEntityCreation() {
        assertNotNull(testEntity);
        assertTrue(testEntity.isNew());
        assertFalse(testEntity.isDeleted());
    }

    @Test
    void testSoftDelete() {
        testEntity.markAsDeleted();
        assertTrue(testEntity.isDeleted());
    }

    @Test
    void testRestore() {
        testEntity.markAsDeleted();
        testEntity.restore();
        assertFalse(testEntity.isDeleted());
    }

    @Test
    void testEqualsAndHashCode() {
        TestEntity entity1 = new TestEntity();
        TestEntity entity2 = new TestEntity();

        // New entities should not be equal
        assertNotEquals(entity1, entity2);

        // Set same ID for testing
        UUID testId = UUID.randomUUID();
        entity1.setId(testId);
        entity2.setId(testId);

        assertEquals(entity1, entity2);
        assertEquals(entity1.hashCode(), entity2.hashCode());

        // Test with different IDs
        entity2.setId(UUID.randomUUID());
        assertNotEquals(entity1, entity2);
    }

    /**
     * Test implementation of BaseEntity for testing purposes.
     */
    private static class TestEntity extends BaseEntity {
        // Test implementation
    }
}

package com.fabricmanagement.shared.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OperationType enum
 */
@DisplayName("OperationType Enum Tests")
class OperationTypeTest {
    
    @Test
    @DisplayName("Should return correct display labels")
    void shouldReturnCorrectDisplayLabels() {
        assertEquals("Read", OperationType.READ.getDisplayLabel());
        assertEquals("Write", OperationType.WRITE.getDisplayLabel());
        assertEquals("Delete", OperationType.DELETE.getDisplayLabel());
    }
    
    @Test
    @DisplayName("Should return correct Turkish labels")
    void shouldReturnCorrectTurkishLabels() {
        assertEquals("Görüntüleme", OperationType.READ.getDisplayLabelTr());
        assertEquals("Yazma", OperationType.WRITE.getDisplayLabelTr());
        assertEquals("Silme", OperationType.DELETE.getDisplayLabelTr());
    }
    
    @Test
    @DisplayName("Should identify read-only operations")
    void shouldIdentifyReadOnlyOperations() {
        assertTrue(OperationType.READ.isReadOnly());
        assertTrue(OperationType.EXPORT.isReadOnly());
        assertFalse(OperationType.WRITE.isReadOnly());
        assertFalse(OperationType.DELETE.isReadOnly());
    }
    
    @Test
    @DisplayName("Should identify mutating operations")
    void shouldIdentifyMutatingOperations() {
        assertTrue(OperationType.WRITE.isMutating());
        assertTrue(OperationType.DELETE.isMutating());
        assertTrue(OperationType.APPROVE.isMutating());
        assertFalse(OperationType.READ.isMutating());
    }
    
    @Test
    @DisplayName("Should return correct restriction levels")
    void shouldReturnCorrectRestrictionLevels() {
        assertEquals(0, OperationType.READ.getRestrictionLevel());
        assertEquals(1, OperationType.EXPORT.getRestrictionLevel());
        assertEquals(2, OperationType.WRITE.getRestrictionLevel());
        assertEquals(3, OperationType.APPROVE.getRestrictionLevel());
        assertEquals(4, OperationType.DELETE.getRestrictionLevel());
        assertEquals(5, OperationType.MANAGE.getRestrictionLevel());
        
        // Verify hierarchy
        assertTrue(OperationType.DELETE.getRestrictionLevel() > OperationType.WRITE.getRestrictionLevel());
        assertTrue(OperationType.MANAGE.getRestrictionLevel() > OperationType.DELETE.getRestrictionLevel());
    }
    
    @Test
    @DisplayName("Should require audit for mutating operations")
    void shouldRequireAuditForMutatingOperations() {
        assertFalse(OperationType.READ.requiresAudit());  // Too noisy
        assertTrue(OperationType.WRITE.requiresAudit());
        assertTrue(OperationType.DELETE.requiresAudit());
        assertTrue(OperationType.APPROVE.requiresAudit());
        assertTrue(OperationType.EXPORT.requiresAudit());
        assertTrue(OperationType.MANAGE.requiresAudit());
    }
    
    @Test
    @DisplayName("Should have exactly 6 operation types")
    void shouldHaveExactlySixOperationTypes() {
        OperationType[] types = OperationType.values();
        assertEquals(6, types.length);
    }
}


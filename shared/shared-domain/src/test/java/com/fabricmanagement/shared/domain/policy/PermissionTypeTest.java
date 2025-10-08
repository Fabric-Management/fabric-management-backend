package com.fabricmanagement.shared.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PermissionType enum
 */
@DisplayName("PermissionType Enum Tests")
class PermissionTypeTest {
    
    @Test
    @DisplayName("Should return correct display labels")
    void shouldReturnCorrectDisplayLabels() {
        assertEquals("Allow", PermissionType.ALLOW.getDisplayLabel());
        assertEquals("Deny", PermissionType.DENY.getDisplayLabel());
    }
    
    @Test
    @DisplayName("Should return correct Turkish labels")
    void shouldReturnCorrectTurkishLabels() {
        assertEquals("İzin Ver", PermissionType.ALLOW.getDisplayLabelTr());
        assertEquals("İzin Verme", PermissionType.DENY.getDisplayLabelTr());
    }
    
    @Test
    @DisplayName("Should identify ALLOW type correctly")
    void shouldIdentifyAllowType() {
        assertTrue(PermissionType.ALLOW.isAllow());
        assertFalse(PermissionType.DENY.isAllow());
    }
    
    @Test
    @DisplayName("Should identify DENY type correctly")
    void shouldIdentifyDenyType() {
        assertTrue(PermissionType.DENY.isDeny());
        assertFalse(PermissionType.ALLOW.isDeny());
    }
    
    @Test
    @DisplayName("Should have correct priorities (DENY > ALLOW)")
    void shouldHaveCorrectPriorities() {
        assertEquals(1, PermissionType.DENY.getPriority());
        assertEquals(0, PermissionType.ALLOW.getPriority());
        assertTrue(PermissionType.DENY.getPriority() > PermissionType.ALLOW.getPriority());
    }
    
    @Test
    @DisplayName("Should resolve conflicts - DENY always wins")
    void shouldResolveConflictsDenyWins() {
        // DENY vs ALLOW = DENY
        assertEquals(PermissionType.DENY, 
            PermissionType.DENY.resolveConflict(PermissionType.ALLOW));
        
        // ALLOW vs DENY = DENY
        assertEquals(PermissionType.DENY, 
            PermissionType.ALLOW.resolveConflict(PermissionType.DENY));
        
        // DENY vs DENY = DENY
        assertEquals(PermissionType.DENY, 
            PermissionType.DENY.resolveConflict(PermissionType.DENY));
        
        // ALLOW vs ALLOW = ALLOW
        assertEquals(PermissionType.ALLOW, 
            PermissionType.ALLOW.resolveConflict(PermissionType.ALLOW));
    }
    
    @Test
    @DisplayName("Should have exactly 2 permission types")
    void shouldHaveExactlyTwoPermissionTypes() {
        PermissionType[] types = PermissionType.values();
        assertEquals(2, types.length);
    }
}


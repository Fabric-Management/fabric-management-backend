package com.fabricmanagement.shared.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DataScope enum
 */
@DisplayName("DataScope Enum Tests")
class DataScopeTest {
    
    @Test
    @DisplayName("Should return correct display labels")
    void shouldReturnCorrectDisplayLabels() {
        assertEquals("Self", DataScope.SELF.getDisplayLabel());
        assertEquals("Company", DataScope.COMPANY.getDisplayLabel());
        assertEquals("Cross-Company", DataScope.CROSS_COMPANY.getDisplayLabel());
        assertEquals("Global", DataScope.GLOBAL.getDisplayLabel());
    }
    
    @Test
    @DisplayName("Should return correct Turkish labels")
    void shouldReturnCorrectTurkishLabels() {
        assertEquals("Kendim", DataScope.SELF.getDisplayLabelTr());
        assertEquals("Şirket", DataScope.COMPANY.getDisplayLabelTr());
        assertEquals("Şirketler Arası", DataScope.CROSS_COMPANY.getDisplayLabelTr());
    }
    
    @Test
    @DisplayName("Should have correct hierarchy levels")
    void shouldHaveCorrectHierarchyLevels() {
        assertEquals(0, DataScope.SELF.getLevel());
        assertEquals(1, DataScope.COMPANY.getLevel());
        assertEquals(2, DataScope.CROSS_COMPANY.getLevel());
        assertEquals(3, DataScope.GLOBAL.getLevel());
    }
    
    @Test
    @DisplayName("Should correctly determine scope inclusion")
    void shouldCorrectlyDetermineScopeInclusion() {
        // GLOBAL includes everything
        assertTrue(DataScope.GLOBAL.includes(DataScope.SELF));
        assertTrue(DataScope.GLOBAL.includes(DataScope.COMPANY));
        assertTrue(DataScope.GLOBAL.includes(DataScope.CROSS_COMPANY));
        assertTrue(DataScope.GLOBAL.includes(DataScope.GLOBAL));
        
        // COMPANY includes SELF
        assertTrue(DataScope.COMPANY.includes(DataScope.SELF));
        assertTrue(DataScope.COMPANY.includes(DataScope.COMPANY));
        assertFalse(DataScope.COMPANY.includes(DataScope.CROSS_COMPANY));
        assertFalse(DataScope.COMPANY.includes(DataScope.GLOBAL));
        
        // SELF includes only SELF
        assertTrue(DataScope.SELF.includes(DataScope.SELF));
        assertFalse(DataScope.SELF.includes(DataScope.COMPANY));
    }
    
    @Test
    @DisplayName("Should require relationship check only for CROSS_COMPANY")
    void shouldRequireRelationshipCheckOnlyForCrossCompany() {
        assertFalse(DataScope.SELF.requiresRelationshipCheck());
        assertFalse(DataScope.COMPANY.requiresRelationshipCheck());
        assertTrue(DataScope.CROSS_COMPANY.requiresRelationshipCheck());
        assertFalse(DataScope.GLOBAL.requiresRelationshipCheck());
    }
    
    @Test
    @DisplayName("Should identify most and least restrictive scopes")
    void shouldIdentifyRestrictiveScopes() {
        assertTrue(DataScope.SELF.isMostRestrictive());
        assertFalse(DataScope.COMPANY.isMostRestrictive());
        assertFalse(DataScope.GLOBAL.isMostRestrictive());
        
        assertFalse(DataScope.SELF.isLeastRestrictive());
        assertFalse(DataScope.COMPANY.isLeastRestrictive());
        assertTrue(DataScope.GLOBAL.isLeastRestrictive());
    }
    
    @Test
    @DisplayName("Should have exactly 4 scopes")
    void shouldHaveExactlyFourScopes() {
        DataScope[] scopes = DataScope.values();
        assertEquals(4, scopes.length);
    }
}


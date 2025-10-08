package com.fabricmanagement.shared.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CompanyType enum
 * 
 * Tests business logic and helper methods
 */
@DisplayName("CompanyType Enum Tests")
class CompanyTypeTest {
    
    @Test
    @DisplayName("Should return correct display labels")
    void shouldReturnCorrectDisplayLabels() {
        assertEquals("Internal Company", CompanyType.INTERNAL.getDisplayLabel());
        assertEquals("Customer Company", CompanyType.CUSTOMER.getDisplayLabel());
        assertEquals("Supplier Company", CompanyType.SUPPLIER.getDisplayLabel());
        assertEquals("Subcontractor", CompanyType.SUBCONTRACTOR.getDisplayLabel());
    }
    
    @Test
    @DisplayName("Should identify INTERNAL as internal type")
    void shouldIdentifyInternalType() {
        assertTrue(CompanyType.INTERNAL.isInternal());
        assertFalse(CompanyType.CUSTOMER.isInternal());
        assertFalse(CompanyType.SUPPLIER.isInternal());
        assertFalse(CompanyType.SUBCONTRACTOR.isInternal());
    }
    
    @Test
    @DisplayName("Should identify external types correctly")
    void shouldIdentifyExternalTypes() {
        assertFalse(CompanyType.INTERNAL.isExternal());
        assertTrue(CompanyType.CUSTOMER.isExternal());
        assertTrue(CompanyType.SUPPLIER.isExternal());
        assertTrue(CompanyType.SUBCONTRACTOR.isExternal());
    }
    
    @Test
    @DisplayName("Should return correct write permissions")
    void shouldReturnCorrectWritePermissions() {
        // INTERNAL can write
        assertTrue(CompanyType.INTERNAL.canWrite());
        
        // SUPPLIER can write (limited - purchase orders)
        assertTrue(CompanyType.SUPPLIER.canWrite());
        
        // SUBCONTRACTOR can write (limited - production orders)
        assertTrue(CompanyType.SUBCONTRACTOR.canWrite());
        
        // CUSTOMER cannot write (read-only)
        assertFalse(CompanyType.CUSTOMER.canWrite());
    }
    
    @Test
    @DisplayName("Should allow delete only for INTERNAL")
    void shouldAllowDeleteOnlyForInternal() {
        assertTrue(CompanyType.INTERNAL.canDelete());
        assertFalse(CompanyType.CUSTOMER.canDelete());
        assertFalse(CompanyType.SUPPLIER.canDelete());
        assertFalse(CompanyType.SUBCONTRACTOR.canDelete());
    }
    
    @Test
    @DisplayName("Should have exactly 4 types")
    void shouldHaveExactlyFourTypes() {
        CompanyType[] types = CompanyType.values();
        assertEquals(4, types.length);
    }
    
    @Test
    @DisplayName("Should be serializable for Kafka events")
    void shouldBeSerializableForKafka() {
        // Enum to String (for Kafka)
        String serialized = CompanyType.INTERNAL.name();
        assertEquals("INTERNAL", serialized);
        
        // String to Enum (from Kafka)
        CompanyType deserialized = CompanyType.valueOf(serialized);
        assertEquals(CompanyType.INTERNAL, deserialized);
    }
    
    @Test
    @DisplayName("Should support switch statements")
    void shouldSupportSwitchStatements() {
        String result = switch (CompanyType.INTERNAL) {
            case INTERNAL -> "Full access";
            case CUSTOMER -> "Read only";
            case SUPPLIER -> "Limited write";
            case SUBCONTRACTOR -> "Limited write";
        };
        
        assertEquals("Full access", result);
    }
}


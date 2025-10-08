package com.fabricmanagement.shared.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserContext enum
 */
@DisplayName("UserContext Enum Tests")
class UserContextTest {
    
    @Test
    @DisplayName("Should return correct display labels")
    void shouldReturnCorrectDisplayLabels() {
        assertEquals("Internal Employee", UserContext.INTERNAL.getDisplayLabel());
        assertEquals("Customer User", UserContext.CUSTOMER.getDisplayLabel());
        assertEquals("Supplier User", UserContext.SUPPLIER.getDisplayLabel());
        assertEquals("Subcontractor User", UserContext.SUBCONTRACTOR.getDisplayLabel());
    }
    
    @Test
    @DisplayName("Should identify INTERNAL as internal context")
    void shouldIdentifyInternalContext() {
        assertTrue(UserContext.INTERNAL.isInternal());
        assertFalse(UserContext.CUSTOMER.isInternal());
        assertFalse(UserContext.SUPPLIER.isInternal());
        assertFalse(UserContext.SUBCONTRACTOR.isInternal());
    }
    
    @Test
    @DisplayName("Should identify external contexts correctly")
    void shouldIdentifyExternalContexts() {
        assertFalse(UserContext.INTERNAL.isExternal());
        assertTrue(UserContext.CUSTOMER.isExternal());
        assertTrue(UserContext.SUPPLIER.isExternal());
        assertTrue(UserContext.SUBCONTRACTOR.isExternal());
    }
    
    @Test
    @DisplayName("Should require department only for INTERNAL")
    void shouldRequireDepartmentOnlyForInternal() {
        assertTrue(UserContext.INTERNAL.requiresDepartment());
        assertFalse(UserContext.CUSTOMER.requiresDepartment());
        assertFalse(UserContext.SUPPLIER.requiresDepartment());
        assertFalse(UserContext.SUBCONTRACTOR.requiresDepartment());
    }
    
    @Test
    @DisplayName("Should return corresponding company type")
    void shouldReturnCorrespondingCompanyType() {
        assertEquals(CompanyType.INTERNAL, UserContext.INTERNAL.getCorrespondingCompanyType());
        assertEquals(CompanyType.CUSTOMER, UserContext.CUSTOMER.getCorrespondingCompanyType());
        assertEquals(CompanyType.SUPPLIER, UserContext.SUPPLIER.getCorrespondingCompanyType());
        assertEquals(CompanyType.SUBCONTRACTOR, UserContext.SUBCONTRACTOR.getCorrespondingCompanyType());
    }
    
    @Test
    @DisplayName("Should be serializable for Kafka events")
    void shouldBeSerializableForKafka() {
        String serialized = UserContext.INTERNAL.name();
        assertEquals("INTERNAL", serialized);
        
        UserContext deserialized = UserContext.valueOf(serialized);
        assertEquals(UserContext.INTERNAL, deserialized);
    }
}


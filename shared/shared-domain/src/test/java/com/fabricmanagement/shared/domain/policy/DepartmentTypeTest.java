package com.fabricmanagement.shared.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DepartmentType enum
 */
@DisplayName("DepartmentType Enum Tests")
class DepartmentTypeTest {
    
    @Test
    @DisplayName("Should return correct English display labels")
    void shouldReturnCorrectEnglishLabels() {
        assertEquals("Production", DepartmentType.PRODUCTION.getDisplayLabel());
        assertEquals("Quality Control", DepartmentType.QUALITY.getDisplayLabel());
        assertEquals("Warehouse", DepartmentType.WAREHOUSE.getDisplayLabel());
    }
    
    @Test
    @DisplayName("Should return correct Turkish display labels")
    void shouldReturnCorrectTurkishLabels() {
        assertEquals("Üretim", DepartmentType.PRODUCTION.getDisplayLabelTr());
        assertEquals("Kalite Kontrol", DepartmentType.QUALITY.getDisplayLabelTr());
        assertEquals("Muhasebe", DepartmentType.FINANCE.getDisplayLabelTr());
    }
    
    @Test
    @DisplayName("Should return correct label based on locale")
    void shouldReturnCorrectLabelBasedOnLocale() {
        assertEquals("Production", DepartmentType.PRODUCTION.getDisplayLabel("en"));
        assertEquals("Üretim", DepartmentType.PRODUCTION.getDisplayLabel("tr"));
        assertEquals("Production", DepartmentType.PRODUCTION.getDisplayLabel("de"));  // Default to EN
    }
    
    @Test
    @DisplayName("Should identify PRODUCTION as production department")
    void shouldIdentifyProductionDepartment() {
        assertTrue(DepartmentType.PRODUCTION.isProduction());
        assertFalse(DepartmentType.QUALITY.isProduction());
        assertFalse(DepartmentType.FINANCE.isProduction());
    }
    
    @Test
    @DisplayName("Should identify office departments correctly")
    void shouldIdentifyOfficeDepartments() {
        assertTrue(DepartmentType.FINANCE.isOffice());
        assertTrue(DepartmentType.SALES.isOffice());
        assertTrue(DepartmentType.HR.isOffice());
        assertFalse(DepartmentType.PRODUCTION.isOffice());
        assertFalse(DepartmentType.WAREHOUSE.isOffice());
    }
    
    @Test
    @DisplayName("Should identify support departments correctly")
    void shouldIdentifySupportDepartments() {
        assertTrue(DepartmentType.QUALITY.isSupport());
        assertTrue(DepartmentType.WAREHOUSE.isSupport());
        assertFalse(DepartmentType.PRODUCTION.isSupport());
        assertFalse(DepartmentType.FINANCE.isSupport());
    }
    
    @Test
    @DisplayName("Should return correct default dashboard paths")
    void shouldReturnCorrectDashboardPaths() {
        assertEquals("/production/dashboard", DepartmentType.PRODUCTION.getDefaultDashboardPath());
        assertEquals("/quality/inspections", DepartmentType.QUALITY.getDefaultDashboardPath());
        assertEquals("/warehouse/inventory", DepartmentType.WAREHOUSE.getDefaultDashboardPath());
        assertEquals("/finance/dashboard", DepartmentType.FINANCE.getDefaultDashboardPath());
    }
    
    @Test
    @DisplayName("Should have exactly 9 department types")
    void shouldHaveExactlyNineDepartmentTypes() {
        DepartmentType[] types = DepartmentType.values();
        assertEquals(9, types.length);
    }
}


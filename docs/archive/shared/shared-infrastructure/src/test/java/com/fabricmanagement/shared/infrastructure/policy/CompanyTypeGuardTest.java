package com.fabricmanagement.shared.infrastructure.policy;

import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.policy.guard.CompanyTypeGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CompanyTypeGuard
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@DisplayName("CompanyTypeGuard Tests")
class CompanyTypeGuardTest {
    
    private CompanyTypeGuard guard;
    private UUID userId;
    private UUID companyId;
    
    @BeforeEach
    void setUp() {
        guard = new CompanyTypeGuard();
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
    }
    
    @Test
    @DisplayName("INTERNAL company should allow all operations")
    void internalShouldAllowAll() {
        // Given
        PolicyContext context = createContext(CompanyType.INTERNAL, OperationType.WRITE);
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNull(denial);
    }
    
    @Test
    @DisplayName("CUSTOMER company should allow READ")
    void customerShouldAllowRead() {
        // Given
        PolicyContext context = createContext(CompanyType.CUSTOMER, OperationType.READ);
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNull(denial);
    }
    
    @Test
    @DisplayName("CUSTOMER company should deny WRITE")
    void customerShouldDenyWrite() {
        // Given
        PolicyContext context = createContext(CompanyType.CUSTOMER, OperationType.WRITE);
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("company_type_guardrail_customer_readonly", denial);
    }
    
    @Test
    @DisplayName("CUSTOMER company should deny DELETE")
    void customerShouldDenyDelete() {
        // Given
        PolicyContext context = createContext(CompanyType.CUSTOMER, OperationType.DELETE);
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("company_type_guardrail_customer_readonly", denial);
    }
    
    @Test
    @DisplayName("SUPPLIER company should allow READ")
    void supplierShouldAllowRead() {
        // Given
        PolicyContext context = createContext(CompanyType.SUPPLIER, OperationType.READ);
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNull(denial);
    }
    
    @Test
    @DisplayName("SUPPLIER company should allow WRITE to purchase orders")
    void supplierShouldAllowPurchaseOrderWrite() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(CompanyType.SUPPLIER)
            .endpoint("/api/purchase-orders")
            .operation(OperationType.WRITE)
            .roles(List.of("USER"))
            .build();
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNull(denial);
    }
    
    @Test
    @DisplayName("SUPPLIER company should deny WRITE to non-purchase-order endpoints")
    void supplierShouldDenyNonPurchaseOrderWrite() {
        // Given
        PolicyContext context = createContext(CompanyType.SUPPLIER, OperationType.WRITE);
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("company_type_guardrail_supplier_limited_write", denial);
    }
    
    @Test
    @DisplayName("SUBCONTRACTOR company should allow WRITE to production orders")
    void subcontractorShouldAllowProductionOrderWrite() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(CompanyType.SUBCONTRACTOR)
            .endpoint("/api/production-orders")
            .operation(OperationType.WRITE)
            .roles(List.of("USER"))
            .build();
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNull(denial);
    }
    
    @Test
    @DisplayName("SUBCONTRACTOR company should deny WRITE to non-production-order endpoints")
    void subcontractorShouldDenyNonProductionOrderWrite() {
        // Given
        PolicyContext context = createContext(CompanyType.SUBCONTRACTOR, OperationType.WRITE);
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("company_type_guardrail_subcontractor_limited_write", denial);
    }
    
    @Test
    @DisplayName("Should deny when company type is null")
    void shouldDenyNullCompanyType() {
        // Given
        PolicyContext context = createContext(null, OperationType.READ);
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("company_type_guardrail_null_company_type", denial);
    }
    
    @Test
    @DisplayName("Should deny when operation type is null")
    void shouldDenyNullOperation() {
        // Given
        PolicyContext context = createContext(CompanyType.INTERNAL, null);
        
        // When
        String denial = guard.checkGuardrails(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("company_type_guardrail_null_operation", denial);
    }
    
    @Test
    @DisplayName("isOperationAllowed should return true for INTERNAL")
    void isOperationAllowedInternal() {
        // When / Then
        assertTrue(guard.isOperationAllowed(CompanyType.INTERNAL, OperationType.WRITE));
        assertTrue(guard.isOperationAllowed(CompanyType.INTERNAL, OperationType.DELETE));
        assertTrue(guard.isOperationAllowed(CompanyType.INTERNAL, OperationType.APPROVE));
    }
    
    @Test
    @DisplayName("isOperationAllowed should return false for CUSTOMER write")
    void isOperationAllowedCustomerWrite() {
        // When / Then
        assertFalse(guard.isOperationAllowed(CompanyType.CUSTOMER, OperationType.WRITE));
        assertFalse(guard.isOperationAllowed(CompanyType.CUSTOMER, OperationType.DELETE));
        assertTrue(guard.isOperationAllowed(CompanyType.CUSTOMER, OperationType.READ));
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    private PolicyContext createContext(CompanyType companyType, OperationType operation) {
        return PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(companyType)
            .endpoint("/api/test")
            .operation(operation)
            .roles(List.of("USER"))
            .build();
    }
}

